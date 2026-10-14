package com.healthqueue.service;

import com.healthqueue.config.AppProperties;
import com.healthqueue.domain.Appointment;
import com.healthqueue.domain.AppointmentStatus;
import com.healthqueue.domain.Doctor;
import com.healthqueue.domain.Role;
import com.healthqueue.dto.Dtos.AdminAppointmentDto;
import com.healthqueue.dto.Dtos.DailyQueueDto;
import com.healthqueue.dto.Dtos.DoctorOverviewDto;
import com.healthqueue.dto.Dtos.PageResponse;
import com.healthqueue.dto.Dtos.QueueEntryDto;
import com.healthqueue.dto.Dtos.QueueStatusDto;
import com.healthqueue.dto.Dtos.QueueSummaryDto;
import com.healthqueue.repository.AppointmentRepository;
import com.healthqueue.repository.DoctorRepository;
import com.healthqueue.security.AuthUser;
import com.healthqueue.web.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queue reads and all queue state transitions.
 * <pre>
 * BOOKED --check-in--> WAITING --call--> CALLED --start--> IN_CONSULTATION --complete--> COMPLETED
 * BOOKED/WAITING --cancel--> CANCELLED         BOOKED/WAITING/CALLED --skip--> SKIPPED
 * </pre>
 * "Patients ahead" = appointments of the same doctor + date with a smaller token that are still BOOKED
 * or WAITING. Completed, cancelled and skipped appointments never count; the patient currently being
 * served is shown separately as "now serving".
 */
@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);
    static final String ESTIMATE_NOTE = "Estimate only - actual waiting time depends on how long consultations take.";

    private final AppointmentRepository appointments;
    private final DoctorRepository doctors;
    private final QueueStateService queueStates;
    private final QueueEventBroadcaster events;
    private final AccessPolicy policy;
    private final AppProperties props;
    private final Clock clock;
    private final EntityManager em;
    private final MeterRegistry metrics;

    public QueueService(AppointmentRepository appointments, DoctorRepository doctors, QueueStateService queueStates,
                        QueueEventBroadcaster events, AccessPolicy policy, AppProperties props, Clock clock,
                        EntityManager em, MeterRegistry metrics) {
        this.appointments = appointments;
        this.doctors = doctors;
        this.queueStates = queueStates;
        this.events = events;
        this.policy = policy;
        this.props = props;
        this.clock = clock;
        this.em = em;
        this.metrics = metrics;
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public QueueStatusDto statusFor(Long appointmentId, AuthUser caller) {
        Appointment a = load(appointmentId);
        policy.assertCanView(caller, a);
        Long doctorId = a.getDoctor().getId();
        String nowServing = activeAppointment(doctorId, a.getAppointmentDate()).map(Appointment::displayToken).orElse(null);
        long ahead = AppointmentStatus.PENDING.contains(a.getStatus())
                ? appointments.countAhead(doctorId, a.getAppointmentDate(), a.getTokenNumber(), AppointmentStatus.PENDING)
                : 0;
        int avg = props.queue().avgConsultationMinutes();
        return new QueueStatusDto(a.getId(), a.getPatient().getFullName(), a.getDoctor().getUser().getFullName(),
                a.getDoctor().getTitle(), a.getAppointmentDate(), a.getSlotTime(), a.displayToken(), nowServing,
                ahead, QueueMath.estimateWaitMinutes(ahead, avg), avg, a.getStatus(), ESTIMATE_NOTE);
    }

    /** Anyone logged in may see the public summary; it contains no patient data. */
    @Transactional(readOnly = true)
    public QueueSummaryDto summary(Long doctorId, LocalDate date) {
        requireDoctor(doctorId);
        return new QueueSummaryDto(doctorId, date,
                activeAppointment(doctorId, date).map(Appointment::displayToken).orElse(null),
                appointments.countByStatuses(doctorId, date, AppointmentStatus.PENDING),
                props.queue().avgConsultationMinutes());
    }

    @Transactional(readOnly = true)
    public DailyQueueDto dailyQueue(Long doctorId, LocalDate date, AuthUser caller) {
        policy.assertCanManageQueue(caller, doctorId);
        Doctor d = requireDoctor(doctorId);
        List<QueueEntryDto> entries = appointments.findByDoctorIdAndAppointmentDateOrderByTokenNumberAsc(doctorId, date)
                .stream().map(Mappers::toEntry).toList();
        return new DailyQueueDto(doctorId, d.getUser().getFullName(), d.getTitle(), date,
                activeAppointment(doctorId, date).map(Appointment::displayToken).orElse(null),
                appointments.countByStatuses(doctorId, date, AppointmentStatus.PENDING),
                props.queue().avgConsultationMinutes(), entries);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAppointmentDto> adminAppointments(LocalDate date, Long doctorId, AppointmentStatus status,
                                                               int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return PageResponse.of(appointments.searchDaily(date, doctorId, status, pageable).map(Mappers::toAdminDto));
    }

    @Transactional(readOnly = true)
    public List<DoctorOverviewDto> overview(LocalDate date) {
        Map<Long, Map<AppointmentStatus, Long>> counts = new java.util.HashMap<>();
        for (Object[] row : appointments.countByDoctorAndStatus(date)) {
            counts.computeIfAbsent((Long) row[0], k -> new EnumMap<>(AppointmentStatus.class))
                    .put((AppointmentStatus) row[1], (Long) row[2]);
        }
        return doctors.search(null, false, PageRequest.of(0, 100)).stream()
                .map(d -> new DoctorOverviewDto(d.getId(), d.getUser().getFullName(), d.getTitle(),
                        counts.getOrDefault(d.getId(), Map.of()),
                        activeAppointment(d.getId(), date).map(Appointment::displayToken).orElse(null)))
                .toList();
    }

    // ------------------------------------------------------------------ transitions

    /** Calls the lowest-numbered BOOKED/WAITING appointment. Only one patient can be active at a time. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QueueEntryDto callNext(Long doctorId, LocalDate date, AuthUser caller) {
        policy.assertCanManageQueue(caller, doctorId);
        requireDoctor(doctorId);
        queueStates.lock(doctorId, date);
        if (activeAppointment(doctorId, date).isPresent()) {
            throw ApiException.conflict("Complete or skip the patient who is currently called first");
        }
        Appointment next = appointments
                .findFirstByDoctorIdAndAppointmentDateAndStatusInOrderByTokenNumberAsc(doctorId, date, AppointmentStatus.PENDING)
                .orElseThrow(() -> ApiException.conflict("There are no waiting patients"));
        next.changeStatus(AppointmentStatus.CALLED, Instant.now(clock));
        afterChange(next, "called");
        metrics.counter("healthqueue.queue.calls").increment();
        return Mappers.toEntry(next);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QueueEntryDto start(Long appointmentId, AuthUser caller) {
        return manage(appointmentId, caller, EnumSet.of(AppointmentStatus.CALLED), AppointmentStatus.IN_CONSULTATION, "start");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QueueEntryDto complete(Long appointmentId, AuthUser caller) {
        return manage(appointmentId, caller, AppointmentStatus.ACTIVE, AppointmentStatus.COMPLETED, "complete");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QueueEntryDto skip(Long appointmentId, AuthUser caller) {
        return manage(appointmentId, caller,
                EnumSet.of(AppointmentStatus.BOOKED, AppointmentStatus.WAITING, AppointmentStatus.CALLED),
                AppointmentStatus.SKIPPED, "skip");
    }

    /** Patient (own appointment) or admin marks the patient as arrived: BOOKED -> WAITING. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QueueEntryDto checkIn(Long appointmentId, AuthUser caller) {
        Appointment a = load(appointmentId);
        policy.assertCanView(caller, a);
        if (caller.role() == Role.DOCTOR) throw ApiException.forbidden("Doctors cannot check patients in");
        a = lockFresh(a);
        requireStatus(a, EnumSet.of(AppointmentStatus.BOOKED), "check in");
        a.changeStatus(AppointmentStatus.WAITING, Instant.now(clock));
        afterChange(a, "checked in");
        return Mappers.toEntry(a);
    }

    /**
     * Patients may cancel their own BOOKED/WAITING appointments (not in the past). Admins may also
     * cancel an appointment that is already called. Cancelled appointments no longer count as "ahead".
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QueueEntryDto cancel(Long appointmentId, AuthUser caller) {
        Appointment a = load(appointmentId);
        policy.assertCanView(caller, a);
        if (caller.role() == Role.DOCTOR) throw ApiException.forbidden("Doctors cannot cancel appointments");
        a = lockFresh(a);
        if (caller.isAdmin()) {
            requireStatus(a, EnumSet.of(AppointmentStatus.BOOKED, AppointmentStatus.WAITING, AppointmentStatus.CALLED), "cancel");
        } else {
            requireStatus(a, AppointmentStatus.CANCELLABLE, "cancel");
            if (a.getAppointmentDate().isBefore(LocalDate.now(clock))) {
                throw ApiException.conflict("Appointments in the past cannot be cancelled");
            }
        }
        a.changeStatus(AppointmentStatus.CANCELLED, Instant.now(clock));
        afterChange(a, "cancelled");
        metrics.counter("healthqueue.appointments.cancelled").increment();
        return Mappers.toEntry(a);
    }

    // ------------------------------------------------------------------ helpers

    private QueueEntryDto manage(Long id, AuthUser caller, Set<AppointmentStatus> allowedFrom, AppointmentStatus to,
                                 String action) {
        Appointment a = load(id);
        policy.assertCanManageQueue(caller, a.getDoctor().getId());
        a = lockFresh(a);
        requireStatus(a, allowedFrom, action);
        a.changeStatus(to, Instant.now(clock));
        afterChange(a, action);
        return Mappers.toEntry(a);
    }

    /** Take the queue lock, then re-read the row so we decide on the latest committed status. */
    private Appointment lockFresh(Appointment a) {
        queueStates.lock(a.getDoctor().getId(), a.getAppointmentDate());
        em.refresh(a, LockModeType.PESSIMISTIC_WRITE);
        return a;
    }

    private static void requireStatus(Appointment a, Set<AppointmentStatus> allowed, String action) {
        if (!allowed.contains(a.getStatus())) {
            throw ApiException.conflict("Cannot %s an appointment that is %s".formatted(action, a.getStatus()));
        }
    }

    private void afterChange(Appointment a, String what) {
        events.publishAfterCommit(a.getDoctor().getId(), a.getAppointmentDate());
        log.info("Appointment {} {} (doctorId={}, token={})", a.getId(), what, a.getDoctor().getId(), a.displayToken());
    }

    private java.util.Optional<Appointment> activeAppointment(Long doctorId, LocalDate date) {
        return appointments.findFirstByDoctorIdAndAppointmentDateAndStatusInOrderByTokenNumberAsc(
                doctorId, date, AppointmentStatus.ACTIVE);
    }

    private Appointment load(Long id) {
        return appointments.findWithDetailsById(id).orElseThrow(() -> ApiException.notFound("Appointment not found"));
    }

    private Doctor requireDoctor(Long id) {
        return doctors.findWithDetailsById(id).orElseThrow(() -> ApiException.notFound("Doctor not found"));
    }
}
