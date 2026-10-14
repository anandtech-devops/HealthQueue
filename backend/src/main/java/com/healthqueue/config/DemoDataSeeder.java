package com.healthqueue.config;

import com.healthqueue.domain.Department;
import com.healthqueue.domain.Doctor;
import com.healthqueue.domain.Role;
import com.healthqueue.domain.User;
import com.healthqueue.repository.DepartmentRepository;
import com.healthqueue.repository.DoctorRepository;
import com.healthqueue.repository.UserRepository;
import java.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DEMO DATA ONLY. Runs when SEED_DEMO_DATA=true and DEMO_PASSWORD is set. Accounts use the reserved
 * example.com domain (no real people). The password comes from the environment, never from source code.
 * Idempotent: users that already exist are left alone.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppProperties props;
    private final UserRepository users;
    private final DoctorRepository doctors;
    private final DepartmentRepository departments;
    private final PasswordEncoder encoder;

    public DemoDataSeeder(AppProperties props, UserRepository users, DoctorRepository doctors,
                          DepartmentRepository departments, PasswordEncoder encoder) {
        this.props = props;
        this.users = users;
        this.doctors = doctors;
        this.departments = departments;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.demo().seedEnabled()) return;
        String password = props.demo().password();
        if (password == null || password.length() < 8) {
            log.warn("SEED_DEMO_DATA is true but DEMO_PASSWORD is missing/too short; skipping demo data");
            return;
        }
        user("Demo Receptionist", "admin@demo.example.com", password, Role.ADMIN);
        user("Demo Patient", "patient@demo.example.com", password, Role.PATIENT);
        user("Demo Patient Two", "patient2@demo.example.com", password, Role.PATIENT);
        doctor("Dr. Demo Physician", "doctor.general@demo.example.com", password, "General Medicine",
                "General Physician", "A");
        doctor("Dr. Demo Pediatrician", "doctor.peds@demo.example.com", password, "Pediatrics",
                "Pediatrician", "B");
        log.warn("DEMO DATA seeded (accounts @demo.example.com). Do not enable SEED_DEMO_DATA in production.");
    }

    private User user(String name, String email, String password, Role role) {
        return users.findByEmailIgnoreCase(email)
                .orElseGet(() -> users.save(new User(name, email, encoder.encode(password), role)));
    }

    private void doctor(String name, String email, String password, String dept, String title, String prefix) {
        User u = user(name, email, password, Role.DOCTOR);
        if (doctors.findByUserId(u.getId()).isPresent()) return;
        Department d = departments.findByName(dept).orElseThrow();
        doctors.save(new Doctor(u, d, title, prefix, 15, LocalTime.of(9, 0), LocalTime.of(17, 0),
                "MON,TUE,WED,THU,FRI,SAT,SUN"));
    }
}
