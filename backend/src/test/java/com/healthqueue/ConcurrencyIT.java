package com.healthqueue;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthqueue.dto.Dtos.AppointmentDto;
import com.healthqueue.dto.Dtos.BookingRequest;
import com.healthqueue.security.AuthUser;
import com.healthqueue.service.AppointmentService;
import com.healthqueue.web.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Scenario 7: concurrent booking requests must never produce duplicate tokens or double-booked slots. */
class ConcurrencyIT extends AbstractIntegrationTest {

    @Autowired AppointmentService appointmentService;

    /** Runs all tasks at the same instant on separate threads/connections. */
    private <T> List<Object> raceAll(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<T> t : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return t.call();
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        ready.await();
        go.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> f : futures) results.add(f.get());
        pool.shutdown();
        return results;
    }

    @Test
    void parallelBookingsOfDifferentSlotsGetUniqueConsecutiveTokens() throws Exception {
        DoctorAccount doctor = newDoctor();   // brand-new doctor/date: also races the creation of the queue row
        int n = 16;
        List<Callable<AppointmentDto>> tasks = IntStream.range(0, n).<Callable<AppointmentDto>>mapToObj(i -> {
            Account p = newPatient();
            return () -> appointmentService.book(new AuthUser(p.id(), p.user().getEmail(), p.user().getRole()),
                    new BookingRequest(doctor.id(), tomorrow(), slot(i)));
        }).toList();

        List<Object> results = raceAll(tasks);

        List<Object> failures = results.stream().filter(r -> r instanceof Exception).toList();
        assertThat(failures).as("no booking should fail: %s", failures).isEmpty();
        var tokens = results.stream().map(r -> ((AppointmentDto) r).tokenNumber()).collect(Collectors.toSet());
        assertThat(tokens).hasSize(n);
        assertThat(tokens).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, n).boxed().toList());
    }

    @Test
    void parallelBookingsOfTheSameSlotLetExactlyOneWin() throws Exception {
        DoctorAccount doctor = newDoctor();
        int n = 8;
        List<Callable<AppointmentDto>> tasks = IntStream.range(0, n).<Callable<AppointmentDto>>mapToObj(i -> {
            Account p = newPatient();
            return () -> appointmentService.book(new AuthUser(p.id(), p.user().getEmail(), p.user().getRole()),
                    new BookingRequest(doctor.id(), tomorrow(), slot(0)));
        }).toList();

        List<Object> results = raceAll(tasks);

        long successes = results.stream().filter(r -> r instanceof AppointmentDto).count();
        long conflicts = results.stream().filter(r -> r instanceof ApiException).count();
        assertThat(successes).isEqualTo(1);
        assertThat(conflicts).as("every loser gets a clean 409, not a 500: %s", results).isEqualTo(n - 1);
    }

    @Test
    void oneBurstPatientCannotHoldTwoAppointmentsWithSameDoctorSameDay() throws Exception {
        DoctorAccount doctor = newDoctor();
        Account p = newPatient();
        AuthUser auth = new AuthUser(p.id(), p.user().getEmail(), p.user().getRole());
        List<Callable<AppointmentDto>> tasks = IntStream.range(0, 4).<Callable<AppointmentDto>>mapToObj(
                i -> () -> appointmentService.book(auth, new BookingRequest(doctor.id(), tomorrow(), slot(i)))).toList();

        List<Object> results = raceAll(tasks);

        assertThat(results.stream().filter(r -> r instanceof AppointmentDto).count()).isEqualTo(1);
        assertThat(results.stream().filter(r -> r instanceof ApiException).count()).isEqualTo(3);
    }
}
