package com.healthqueue.service;

import static com.healthqueue.TestData.CLOCK;
import static com.healthqueue.TestData.TODAY;
import static com.healthqueue.TestData.appointment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthqueue.TestData;
import com.healthqueue.domain.Appointment;
import com.healthqueue.domain.AppointmentStatus;
import com.healthqueue.domain.Doctor;
import com.healthqueue.domain.Role;
import com.healthqueue.domain.User;
import com.healthqueue.dto.Dtos.QueueEntryDto;
import com.healthqueue.dto.Dtos.QueueStatusDto;
import com.healthqueue.repository.AppointmentRepository;
import com.healthqueue.repository.DoctorRepository;
import com.healthqueue.security.AuthUser;
import com.healthqueue.web.ApiException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueueServiceTest {

    @Mock AppointmentRepository appointments;
    @Mock DoctorRepository doctorRepo;
    @Mock QueueStateService queueStates;
    @Mock QueueEventBroadcaster events;
    @Mock EntityManager em;

    QueueService service;
    User patient = TestData.user(10, "Pat One", Role.PATIENT);
    User otherPatient = TestData.user(11, "Pat Two", Role.PATIENT);
    User docUser = TestData.user(20, "Dr One", Role.DOCTOR);
    User otherDocUser = TestData.user(21, "Dr Two", Role.DOCTOR);
    Doctor doctor = TestData.doctor(1, docUser);
    Doctor otherDoctor = TestData.doctor(2, otherDocUser);
    AuthUser patientAuth = new AuthUser(10L, patient.getEmail(), Role.PATIENT);
    AuthUser doctorAuth = new AuthUser(20L, docUser.getEmail(), Role.DOCTOR);
    AuthUser otherDoctorAuth = new AuthUser(21L, otherDocUser.getEmail(), Role.DOCTOR);
    AuthUser adminAuth = new AuthUser(1L, "admin@test.example.com", Role.ADMIN);

    @BeforeEach
    void setUp() {
        AccessPolicy policy = new AccessPolicy(doctorRepo);
        when(doctorRepo.findByUserId(20L)).thenReturn(Optional.of(doctor));
        when(doctorRepo.findByUserId(21L)).thenReturn(Optional.of(otherDoctor));
        when(doctorRepo.findWithDetailsById(1L)).thenReturn(Optional.of(doctor));
        service = new QueueService(appointments, doctorRepo, queueStates, events, policy, TestData.props(), CLOCK,
                em, new SimpleMeterRegistry());
    }

    private Appointment stub(long id, int token, AppointmentStatus status) {
        Appointment a = appointment(id, patient, doctor, token, status);
        when(appointments.findWithDetailsById(id)).thenReturn(Optional.of(a));
        return a;
    }

    // ---- patients ahead / estimate -----------------------------------------------------------

    @Test
    void waitingPatientSeesTokensAheadNowServingAndEstimate() {
        // token 8 is being served, token 12 is waiting, 9/10/11 are still waiting -> 3 ahead
        stub(12, 12, AppointmentStatus.WAITING);
        Appointment serving = appointment(8, otherPatient, doctor, 8, AppointmentStatus.IN_CONSULTATION);
        when(appointments.findFirstByDoctorIdAndAppointmentDateAndStatusInOrderByTokenNumberAsc(
                1L, TODAY, AppointmentStatus.ACTIVE)).thenReturn(Optional.of(serving));
        when(appointments.countAhead(1L, TODAY, 12, AppointmentStatus.PENDING)).thenReturn(3L);

        QueueStatusDto status = service.statusFor(12L, patientAuth);

        assertThat(status.yourToken()).isEqualTo("A-012");
        assertThat(status.nowServing()).isEqualTo("A-008");
        assertThat(status.patientsAhead()).isEqualTo(3);
        assertThat(status.estimatedWaitMinutes()).isEqualTo(15);
        assertThat(status.status()).isEqualTo(AppointmentStatus.WAITING);
        assertThat(status.estimateNote()).containsIgnoringCase("estimate");
    }

    @Test
    void onlyBookedAndWaitingCountAsAhead() {
        stub(12, 12, AppointmentStatus.BOOKED);
        service.statusFor(12L, patientAuth);
        // completed / cancelled / skipped / active statuses are never part of the "ahead" query
        verify(appointments).countAhead(eq(1L), eq(TODAY), eq(12), eq(AppointmentStatus.PENDING));
        assertThat(AppointmentStatus.PENDING).containsExactlyInAnyOrder(AppointmentStatus.BOOKED, AppointmentStatus.WAITING);
    }

    @Test
    void cancelledOrCompletedAppointmentHasNobodyAheadAndNoWait() {
        for (AppointmentStatus s : new AppointmentStatus[] {AppointmentStatus.CANCELLED, AppointmentStatus.COMPLETED,
                AppointmentStatus.SKIPPED, AppointmentStatus.CALLED}) {
            stub(12, 12, s);
            QueueStatusDto status = service.statusFor(12L, patientAuth);
            assertThat(status.patientsAhead()).isZero();
            assertThat(status.estimatedWaitMinutes()).isZero();
        }
        verify(appointments, never()).countAhead(any(), any(), anyInt(), any());
    }

    @Test
    void noOneBeingServedYieldsNullNowServing() {
        stub(12, 12, AppointmentStatus.WAITING);
        when(appointments.countAhead(1L, TODAY, 12, AppointmentStatus.PENDING)).thenReturn(0L);
        assertThat(service.statusFor(12L, patientAuth).nowServing()).isNull();
    }

    @Test
    void patientCannotSeeAnotherPatientsQueueStatus() {
        stub(12, 12, AppointmentStatus.WAITING);
        AuthUser stranger = new AuthUser(11L, otherPatient.getEmail(), Role.PATIENT);
        assertThatThrownBy(() -> service.statusFor(12L, stranger))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---- transitions -------------------------------------------------------------------------

    @Test
    void callNextCallsLowestWaitingTokenAndPublishesUpdate() {
        Appointment next = appointment(5, patient, doctor, 5, AppointmentStatus.WAITING);
        when(appointments.findFirstByDoctorIdAndAppointmentDateAndStatusInOrderByTokenNumberAsc(
                1L, TODAY, AppointmentStatus.ACTIVE)).thenReturn(Optional.empty());
        when(appointments.findFirstByDoctorIdAndAppointmentDateAndStatusInOrderByTokenNumberAsc(
                1L, TODAY, AppointmentStatus.PENDING)).thenReturn(Optional.of(next));

        QueueEntryDto called = service.callNext(1L, TODAY, doctorAuth);

        assertThat(called.status()).isEqualTo(AppointmentStatus.CALLED);
        assertThat(called.token()).isEqualTo("A-005");
        verify(queueStates).lock(1L, TODAY);
        verify(events).publishAfterCommit(1L, TODAY);
    }

    @Test
    void callNextRefusedWhileSomeoneIsStillActive() {
        when(appointments.findFirstByDoctorIdAndAppointmentDateAndStatusInOrderByTokenNumberAsc(
                1L, TODAY, AppointmentStatus.ACTIVE))
                .thenReturn(Optional.of(appointment(4, patient, doctor, 4, AppointmentStatus.CALLED)));
        assertThatThrownBy(() -> service.callNext(1L, TODAY, doctorAuth))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void callNextWithEmptyQueueIsConflict() {
        when(appointments.findFirstByDoctorIdAndAppointmentDateAndStatusInOrderByTokenNumberAsc(
                eq(1L), eq(TODAY), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.callNext(1L, TODAY, doctorAuth)).isInstanceOf(ApiException.class)
                .hasMessageContaining("no waiting patients");
    }

    @Test
    void startThenCompleteWalksTheStatusMachine() {
        Appointment a = stub(7, 7, AppointmentStatus.CALLED);
        assertThat(service.start(7L, doctorAuth).status()).isEqualTo(AppointmentStatus.IN_CONSULTATION);
        assertThat(service.complete(7L, doctorAuth).status()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(a.getCompletedAt()).isNotNull();
    }

    @Test
    void completingAPatientWhoWasNeverCalledIsRejected() {
        stub(7, 7, AppointmentStatus.WAITING);
        assertThatThrownBy(() -> service.complete(7L, doctorAuth)).isInstanceOf(ApiException.class)
                .hasMessageContaining("Cannot complete");
    }

    @Test
    void skipMarksCalledPatientSkippedButNotCompletedOnes() {
        stub(7, 7, AppointmentStatus.CALLED);
        assertThat(service.skip(7L, doctorAuth).status()).isEqualTo(AppointmentStatus.SKIPPED);
        stub(8, 8, AppointmentStatus.COMPLETED);
        assertThatThrownBy(() -> service.skip(8L, doctorAuth)).isInstanceOf(ApiException.class);
    }

    @Test
    void checkInMovesBookedToWaiting() {
        stub(7, 7, AppointmentStatus.BOOKED);
        assertThat(service.checkIn(7L, patientAuth).status()).isEqualTo(AppointmentStatus.WAITING);
    }

    // ---- cancellation ------------------------------------------------------------------------

    @Test
    void patientCanCancelOwnBookedAppointment() {
        stub(7, 7, AppointmentStatus.BOOKED);
        assertThat(service.cancel(7L, patientAuth).status()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(events).publishAfterCommit(1L, TODAY);
    }

    @Test
    void patientCannotCancelOnceCalledOrFinished() {
        for (AppointmentStatus s : new AppointmentStatus[] {AppointmentStatus.CALLED, AppointmentStatus.IN_CONSULTATION,
                AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED, AppointmentStatus.SKIPPED}) {
            stub(7, 7, s);
            assertThatThrownBy(() -> service.cancel(7L, patientAuth))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        }
    }

    @Test
    void patientCannotCancelAppointmentFromThePast() {
        Appointment past = appointment(7, patient, doctor, LocalDate.of(2030, 1, 9), 7, AppointmentStatus.BOOKED);
        when(appointments.findWithDetailsById(7L)).thenReturn(Optional.of(past));
        assertThatThrownBy(() -> service.cancel(7L, patientAuth)).hasMessageContaining("past");
    }

    @Test
    void patientCannotCancelSomeoneElsesAppointment() {
        stub(7, 7, AppointmentStatus.BOOKED);
        AuthUser stranger = new AuthUser(11L, otherPatient.getEmail(), Role.PATIENT);
        assertThatThrownBy(() -> service.cancel(7L, stranger)).isInstanceOf(ApiException.class);
    }

    @Test
    void adminCanCancelAnAlreadyCalledAppointment() {
        stub(7, 7, AppointmentStatus.CALLED);
        assertThat(service.cancel(7L, adminAuth).status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    // ---- authorization -----------------------------------------------------------------------

    @Test
    void doctorCannotManageAnotherDoctorsQueue() {
        assertThatThrownBy(() -> service.callNext(1L, TODAY, otherDoctorAuth))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        stub(7, 7, AppointmentStatus.CALLED);
        assertThatThrownBy(() -> service.complete(7L, otherDoctorAuth)).isInstanceOf(ApiException.class);
    }

    @Test
    void patientCannotRunQueueOperations() {
        assertThatThrownBy(() -> service.callNext(1L, TODAY, patientAuth))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void adminCanCallNextForAnyDoctor() {
        Appointment next = appointment(5, patient, doctor, 5, AppointmentStatus.BOOKED);
        when(appointments.findFirstByDoctorIdAndAppointmentDateAndStatusInOrderByTokenNumberAsc(
                1L, TODAY, AppointmentStatus.ACTIVE)).thenReturn(Optional.empty());
        when(appointments.findFirstByDoctorIdAndAppointmentDateAndStatusInOrderByTokenNumberAsc(
                1L, TODAY, AppointmentStatus.PENDING)).thenReturn(Optional.of(next));
        assertThat(service.callNext(1L, TODAY, adminAuth).status()).isEqualTo(AppointmentStatus.CALLED);
    }
}
