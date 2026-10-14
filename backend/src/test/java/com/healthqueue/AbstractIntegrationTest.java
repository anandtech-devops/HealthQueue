package com.healthqueue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthqueue.domain.Doctor;
import com.healthqueue.domain.Role;
import com.healthqueue.domain.User;
import com.healthqueue.repository.DepartmentRepository;
import com.healthqueue.repository.DoctorRepository;
import com.healthqueue.repository.UserRepository;
import com.healthqueue.security.JwtService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Boots the whole application against a throw-away MySQL 8 container (Testcontainers), applying the real
 * Flyway migrations. Requires a running Docker daemon. One container is shared by all integration tests;
 * each test creates its own doctor so queues never interfere.
 */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
public abstract class AbstractIntegrationTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("healthqueue").withUsername("hq").withPassword("hq-test-only");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("healthqueue.jwt.secret", () -> "integration-test-secret-integration-test-secret");
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UserRepository users;
    @Autowired protected DoctorRepository doctors;
    @Autowired protected DepartmentRepository departments;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected JwtService jwt;
    @Autowired protected Clock clock;

    public record Account(User user, String token) {
        public Long id() { return user.getId(); }
    }

    public record DoctorAccount(Doctor doctor, String token) {
        public Long id() { return doctor.getId(); }
    }

    protected LocalDate tomorrow() {
        return LocalDate.now(clock).plusDays(1);
    }

    protected Account newPatient() {
        return account("Patient " + UUID.randomUUID().toString().substring(0, 6), Role.PATIENT);
    }

    protected Account newAdmin() {
        return account("Admin " + UUID.randomUUID().toString().substring(0, 6), Role.ADMIN);
    }

    protected Account account(String name, Role role) {
        User u = users.save(new User(name, UUID.randomUUID() + "@it.example.com", encoder.encode("Password-123"), role));
        return new Account(u, jwt.generate(u));
    }

    /** Doctor working 09:00-17:00 every day with 15-minute slots (32 slots). */
    protected DoctorAccount newDoctor() {
        Account acct = account("Dr " + UUID.randomUUID().toString().substring(0, 6), Role.DOCTOR);
        Doctor d = doctors.save(new Doctor(acct.user(), departments.findByName("General Medicine").orElseThrow(),
                "General Physician", "A", 15, LocalTime.of(9, 0), LocalTime.of(17, 0), "MON,TUE,WED,THU,FRI,SAT,SUN"));
        return new DoctorAccount(d, acct.token());
    }

    protected static LocalTime slot(int index) {
        return LocalTime.of(9, 0).plusMinutes(15L * index);
    }

    // ---- HTTP helpers ---------------------------------------------------------------------------

    protected ResultActions send(MockHttpServletRequestBuilder req, String token) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        return mvc.perform(req);
    }

    protected ResultActions bookRequest(String token, Long doctorId, LocalDate date, LocalTime slot) throws Exception {
        return send(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"doctorId\":%d,\"date\":\"%s\",\"slotTime\":\"%s\"}".formatted(doctorId, date, slot)), token);
    }

    /** Books and returns the appointment id (asserting HTTP 201). */
    protected long book(Account patient, DoctorAccount doctor, int slotIndex) throws Exception {
        String body = bookRequest(patient.token(), doctor.id(), tomorrow(), slot(slotIndex))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    protected JsonNode queueStatus(Account patient, long appointmentId) throws Exception {
        String body = send(get("/api/appointments/" + appointmentId + "/queue"), patient.token())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    protected ResultActions callNext(DoctorAccount doctor) throws Exception {
        return send(post("/api/queue/doctors/" + doctor.id() + "/call-next?date=" + tomorrow()), doctor.token());
    }
}
