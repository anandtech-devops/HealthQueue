package com.healthqueue;

import com.healthqueue.config.AppProperties;
import com.healthqueue.domain.Appointment;
import com.healthqueue.domain.AppointmentStatus;
import com.healthqueue.domain.Department;
import com.healthqueue.domain.Doctor;
import com.healthqueue.domain.Role;
import com.healthqueue.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

/** Builders for plain (non-persisted) objects used by unit tests. */
public final class TestData {
    private TestData() {}

    public static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-10T08:00:00Z"), ZoneOffset.UTC);
    public static final LocalDate TODAY = LocalDate.of(2030, 1, 10);

    public static AppProperties props() {
        return new AppProperties(new AppProperties.Jwt("unit-test-secret-unit-test-secret-123", 60),
                new AppProperties.Cors(List.of("http://localhost")), new AppProperties.Queue(5),
                new AppProperties.Booking(30, false), "UTC", new AppProperties.Demo(false, ""));
    }

    public static User user(long id, String name, Role role) {
        User u = new User(name, name.toLowerCase().replace(' ', '.') + "@test.example.com", "hash", role);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    public static Doctor doctor(long id, User user) {
        Department dept = BeanUtilsHelper.department();
        Doctor d = new Doctor(user, dept, "General Physician", "A", 15, LocalTime.of(9, 0), LocalTime.of(17, 0),
                "MON,TUE,WED,THU,FRI,SAT,SUN");
        ReflectionTestUtils.setField(d, "id", id);
        return d;
    }

    public static Appointment appointment(long id, User patient, Doctor doctor, int token, AppointmentStatus status) {
        return appointment(id, patient, doctor, TODAY, token, status);
    }

    public static Appointment appointment(long id, User patient, Doctor doctor, LocalDate date, int token,
                                          AppointmentStatus status) {
        Appointment a = new Appointment(patient, doctor, date, LocalTime.of(9, 0).plusMinutes(15L * token),
                token, CLOCK.instant());
        ReflectionTestUtils.setField(a, "id", id);
        ReflectionTestUtils.setField(a, "status", status);
        return a;
    }

    /** Department has no public constructor (JPA entity); create it reflectively for tests. */
    static final class BeanUtilsHelper {
        static Department department() {
            try {
                var c = Department.class.getDeclaredConstructor();
                c.setAccessible(true);
                Department d = c.newInstance();
                ReflectionTestUtils.setField(d, "id", 1L);
                ReflectionTestUtils.setField(d, "name", "General Medicine");
                return d;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
