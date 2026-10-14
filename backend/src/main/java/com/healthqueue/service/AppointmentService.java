package com.healthqueue.service;

import com.healthqueue.config.AppProperties;
import com.healthqueue.domain.Appointment;
import com.healthqueue.domain.AppointmentStatus;
import com.healthqueue.domain.Doctor;
import com.healthqueue.domain.User;
import com.healthqueue.dto.Dtos.AppointmentDto;
import com.healthqueue.dto.Dtos.BookingRequest;
import com.healthqueue.dto.Dtos.PageResponse;
import com.healthqueue.dto.Dtos.SlotDto;
import com.healthqueue.repository.AppointmentRepository;
import com.healthqueue.repository.UserRepository;
import com.healthqueue.security.AuthUser;
import com.healthqueue.web.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointments;
    private final UserRepository users;
    private final DoctorService doctorService;
    private final QueueStateService queueStates;
    private final QueueEventBroadcaster events;
    private final AccessPolicy policy;
    private final AppProperties props;
    private final Clock clock;
    private final MeterRegistry metrics;

    public AppointmentService(AppointmentRepository appointments, UserRepository users, DoctorService doctorService,
                              QueueStateService queueStates, QueueEventBroadcaster events, AccessPolicy policy,
                              AppProperties props, Clock clock, MeterRegistry metrics) {
        this.appointments = appointments;
        this.users = users;
        this.doctorService = doctorService;
        this.queueStates = queueStates;
        this.events = events;
        this.policy = policy;
        this.props = props;
        this.clock = clock;
        this.metrics = metrics;
    }

    /**
     * Books a slot and issues the next token for that doctor/date. The queue row is locked
     * (SELECT ... FOR UPDATE) while the token is taken, and unique constraints on token, slot and
     * (patient, doctor, date) act as the final safety net.
     */
    // READ_COMMITTED: reads after we take the queue lock must see rows committed by the previous lock holder.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentDto book(AuthUser caller, BookingRequest req) {
        Doctor doctor = doctorService.find(req.doctorId());
        if (!doctor.isActive()) throw ApiException.badRequest("This doctor is not accepting appointments");

        // Validates date window, working day and slot grid (throws 400 on bad dates).
        SlotDto slot = doctorService.slots(doctor.getId(), req.date()).stream()
                .filter(s -> s.time().equals(req.slotTime())).findFirst()
                .orElseThrow(() -> ApiException.badRequest("That is not a valid slot for this doctor on that date"));
        if (!slot.available()) throw ApiException.conflict("That slot is no longer available");

        if (appointments.existsByPatientIdAndDoctorIdAndAppointmentDateAndStatusNot(
                caller.id(), doctor.getId(), req.date(), AppointmentStatus.CANCELLED)) {
            throw ApiException.conflict("You already have an appointment with this doctor on that date");
        }

        User patient = users.getReferenceById(caller.id());
        try {
            int token = queueStates.issueToken(doctor.getId(), req.date());
            Appointment saved = appointments.saveAndFlush(
                    new Appointment(patient, doctor, req.date(), req.slotTime(), token, Instant.now(clock)));
            events.publishAfterCommit(doctor.getId(), req.date());
            metrics.counter("healthqueue.appointments.booked").increment();
            log.info("Booked appointmentId={} doctorId={} date={} token={}", saved.getId(), doctor.getId(),
                    req.date(), saved.displayToken());
            return Mappers.toDto(saved);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("That slot was just taken, or you already have an appointment that day");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentDto> myAppointments(AuthUser caller, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        return PageResponse.of(appointments
                .findByPatientIdOrderByAppointmentDateDescSlotTimeDesc(caller.id(), pageable).map(Mappers::toDto));
    }

    @Transactional(readOnly = true)
    public AppointmentDto get(AuthUser caller, Long id) {
        Appointment a = appointments.findWithDetailsById(id).orElseThrow(() -> ApiException.notFound("Appointment not found"));
        policy.assertCanView(caller, a);
        return Mappers.toDto(a);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    int maxDaysAhead() {
        return props.booking().maxDaysAhead();
    }
}
