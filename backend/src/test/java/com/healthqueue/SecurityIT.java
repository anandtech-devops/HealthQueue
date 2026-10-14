package com.healthqueue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthqueue.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Authentication, role-based access and ownership checks, enforced by the backend. */
class SecurityIT extends AbstractIntegrationTest {

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        send(get("/api/appointments/my"), null).andExpect(status().isUnauthorized());
        send(post("/api/queue/doctors/1/call-next"), null).andExpect(status().isUnauthorized());
        send(get("/api/appointments/my"), "not-a-jwt").andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsNeedNoLogin() throws Exception {
        send(get("/api/departments"), null).andExpect(status().isOk());
        send(get("/api/doctors"), null).andExpect(status().isOk());
        send(get("/actuator/health"), null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        send(get("/actuator/prometheus"), null).andExpect(status().isOk());
        send(get("/v3/api-docs"), null).andExpect(status().isOk());
    }

    @Test
    void patientCannotReadOrCancelAnotherPatientsAppointment() throws Exception {
        DoctorAccount doctor = newDoctor();
        Account owner = newPatient(), intruder = newPatient();
        long id = book(owner, doctor, 0);

        // scenario 6 (404 so that ids cannot be probed)
        send(get("/api/appointments/" + id), intruder.token()).andExpect(status().isNotFound());
        send(get("/api/appointments/" + id + "/queue"), intruder.token()).andExpect(status().isNotFound());
        send(post("/api/appointments/" + id + "/cancel"), intruder.token()).andExpect(status().isNotFound());
        send(post("/api/appointments/" + id + "/check-in"), intruder.token()).andExpect(status().isNotFound());
        // the intruder's own list is empty, the owner's is not
        send(get("/api/appointments/my"), intruder.token()).andExpect(jsonPath("$.totalElements").value(0));
        send(get("/api/appointments/my"), owner.token()).andExpect(jsonPath("$.totalElements").value(1));
        // owner still fine
        send(get("/api/appointments/" + id + "/queue"), owner.token()).andExpect(status().isOk());
    }

    @Test
    void patientCannotOperateTheQueue() throws Exception {
        DoctorAccount doctor = newDoctor();
        Account patient = newPatient();
        long id = book(patient, doctor, 0);
        send(post("/api/queue/doctors/" + doctor.id() + "/call-next"), patient.token()).andExpect(status().isForbidden());
        send(post("/api/queue/appointments/" + id + "/complete"), patient.token()).andExpect(status().isForbidden());
        send(post("/api/queue/appointments/" + id + "/skip"), patient.token()).andExpect(status().isForbidden());
        send(get("/api/queue/doctors/" + doctor.id()), patient.token()).andExpect(status().isForbidden());
    }

    @Test
    void doctorCanOnlyManageTheirOwnQueue() throws Exception {
        DoctorAccount mine = newDoctor(), other = newDoctor();
        Account patient = newPatient();
        long id = book(patient, mine, 0);

        send(post("/api/queue/doctors/" + mine.id() + "/call-next?date=" + tomorrow()), other.token())
                .andExpect(status().isForbidden());
        send(get("/api/queue/doctors/" + mine.id() + "?date=" + tomorrow()), other.token()).andExpect(status().isForbidden());
        send(post("/api/queue/appointments/" + id + "/skip"), other.token()).andExpect(status().isForbidden());
        // the assigned doctor can, and sees the patient's name
        send(get("/api/queue/doctors/" + mine.id() + "?date=" + tomorrow()), mine.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].token").value("A-001"))
                .andExpect(jsonPath("$.entries[0].patientName").value(patient.user().getFullName()));
    }

    @Test
    void adminEndpointsAreAdminOnly() throws Exception {
        Account patient = newPatient();
        DoctorAccount doctor = newDoctor();
        Account admin = newAdmin();
        send(get("/api/admin/appointments"), patient.token()).andExpect(status().isForbidden());
        send(get("/api/admin/overview"), doctor.token()).andExpect(status().isForbidden());
        send(get("/api/admin/appointments"), admin.token()).andExpect(status().isOk());
        send(get("/api/admin/overview"), admin.token()).andExpect(status().isOk());
    }

    @Test
    void adminCanCreateDoctorWhoCanThenLogIn() throws Exception {
        Account admin = newAdmin();
        Long dept = departments.findByName("Pediatrics").orElseThrow().getId();
        String email = "new.doctor." + System.nanoTime() + "@it.example.com";
        String body = """
                {"fullName":"Dr New","email":"%s","password":"Password-123","departmentId":%d,"title":"Pediatrician",
                 "slotMinutes":20,"startTime":"10:00","endTime":"14:00","workingDays":"MON,TUE,WED,THU,FRI,SAT,SUN"}
                """.formatted(email, dept);
        send(post("/api/admin/doctors").contentType(MediaType.APPLICATION_JSON).content(body), admin.token())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Pediatrician"));
        send(post("/api/admin/doctors").contentType(MediaType.APPLICATION_JSON).content(body), admin.token())
                .andExpect(status().isConflict());
        send(post("/api/admin/doctors").contentType(MediaType.APPLICATION_JSON).content(body), newPatient().token())
                .andExpect(status().isForbidden());

        String login = send(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"Password-123\"}".formatted(email)), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("DOCTOR"))
                .andExpect(jsonPath("$.user.doctorId").isNumber()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(login).get("token").asText();
        send(get("/api/doctor/me"), token).andExpect(status().isOk());
    }

    @Test
    void registrationAndLoginValidation() throws Exception {
        String email = "reg." + System.nanoTime() + "@it.example.com";
        String reg = "{\"fullName\":\"Reg User\",\"email\":\"%s\",\"password\":\"Password-123\"}".formatted(email);
        send(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(reg), null)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value(Role.PATIENT.name()))
                .andExpect(jsonPath("$.token").isNotEmpty());
        send(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(reg), null)
                .andExpect(status().isConflict());
        send(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"\",\"email\":\"bad\",\"password\":\"x\"}"), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        send(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"wrong-password\"}".formatted(email)), null)
                .andExpect(status().isUnauthorized());
        send(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"Password-123\"}".formatted(email)), null)
                .andExpect(status().isOk());
        // passwords are stored as BCrypt hashes
        org.assertj.core.api.Assertions.assertThat(users.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash())
                .startsWith("$2");
    }
}
