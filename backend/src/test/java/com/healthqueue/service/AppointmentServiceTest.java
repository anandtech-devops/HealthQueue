package com.healthqueue.service;

import static com.healthqueue.TestData.CLOCK;
import static com.healthqueue.TestData.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthqueue.TestData;
import com.healthqueue.domain.Appointment;
import com.healthqueue.domain.AppointmentStatus;
import com.healthqueue.domain.Doctor;
import com.healthqueue.domain.Role;
import com.healthqueue.domain.User;
import com.healthqueue.dto.Dtos.AppointmentDto;
import com.healthqueue.dto.Dtos.BookingRequest;
import com.healthqueue.dto.Dtos.SlotDto;
import com.healthqueue.repository.AppointmentRepository;
import com.healthqueue.repository.UserRepository;
import com.healthqueue.security.AuthUser;
import com.healthqueue.web.ApiException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentServiceTest {

    static final LocalDate DAY = TODAY.plusDays(1);
    static final LocalTime SLOT = LocalTime.of(9, 30);

    @Mock AppointmentRepository appointments;
    @Mock UserRepository users;
    @Mock DoctorService doctorService;
    @Mock QueueStateService queueStates;
    @Mock QueueEventBroadcaster events;
    @Mock AccessPolicy policy;

    AppointmentService service;
    User patient = TestData.user(10, "Pat One", Role.PATIENT);
    Doctor doctor = TestData.doctor(1, TestData.user(20, "Dr One", Role.DOCTOR));
    AuthUser caller = new AuthUser(10L, patient.getEmail(), Role.PATIENT);
    BookingRequest request = new BookingRequest(1L, DAY, SLOT);

    @BeforeEach
    void setUp() {
        when(doctorService.find(1L)).thenReturn(doctor);
        when(doctorService.slots(1L, DAY)).thenReturn(List.of(new SlotDto(LocalTime.of(9, 15), true), new SlotDto(SLOT, true)));
        when(users.getReferenceById(10L)).thenReturn(patient);
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));
        service = new AppointmentService(appointments, users, doctorService, queueStates, events, policy,
                TestData.props(), CLOCK, new SimpleMeterRegistry());
    }

    @Test
    void bookingAssignsTheNextTokenAndStartsAsBooked() {
        when(queueStates.issueToken(1L, DAY)).thenReturn(12);

        AppointmentDto dto = service.book(caller, request);

        assertThat(dto.token()).isEqualTo("A-012");
        assertThat(dto.tokenNumber()).isEqualTo(12);
        assertThat(dto.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(dto.date()).isEqualTo(DAY);
        verify(events).publishAfterCommit(1L, DAY);
    }

    @Test
    void tokensAreFormattedWithPrefixAndThreeDigits() {
        assertThat(Appointment.tokenLabel("B", 7)).isEqualTo("B-007");
        assertThat(Appointment.tokenLabel("A", 1234)).isEqualTo("A-1234");
    }

    @Test
    void slotThatIsNotOnTheDoctorsGridIsRejected() {
        BookingRequest odd = new BookingRequest(1L, DAY, LocalTime.of(9, 31));
        assertThatThrownBy(() -> service.book(caller, odd)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void takenSlotIsConflict() {
        when(doctorService.slots(1L, DAY)).thenReturn(List.of(new SlotDto(SLOT, false)));
        assertThatThrownBy(() -> service.book(caller, request)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void secondActiveAppointmentWithSameDoctorSameDayIsConflict() {
        when(appointments.existsByPatientIdAndDoctorIdAndAppointmentDateAndStatusNot(10L, 1L, DAY, AppointmentStatus.CANCELLED))
                .thenReturn(true);
        assertThatThrownBy(() -> service.book(caller, request)).hasMessageContaining("already have");
    }

    @Test
    void databaseUniqueViolationIsReportedAsConflictNotServerError() {
        when(queueStates.issueToken(1L, DAY)).thenReturn(3);
        when(appointments.saveAndFlush(any(Appointment.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> service.book(caller, request)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void inactiveDoctorCannotBeBooked() {
        Doctor inactive = TestData.doctor(1, TestData.user(20, "Dr One", Role.DOCTOR));
        inactive.update(inactive.getDepartment(), "x", 15, LocalTime.of(9, 0), LocalTime.of(17, 0), "MON", false);
        when(doctorService.find(1L)).thenReturn(inactive);
        assertThatThrownBy(() -> service.book(caller, request)).isInstanceOf(ApiException.class);
    }
}
