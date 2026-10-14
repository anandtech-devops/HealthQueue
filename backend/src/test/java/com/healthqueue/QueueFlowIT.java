package com.healthqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** End-to-end behaviour of booking and the live queue, through the real REST API and MySQL. */
class QueueFlowIT extends AbstractIntegrationTest {

    @Test
    void patientBooksAndGetsTokenAndQueuePosition() throws Exception {
        DoctorAccount doctor = newDoctor();
        Account p1 = newPatient(), p2 = newPatient(), p3 = newPatient();

        long a1 = book(p1, doctor, 0);
        long a2 = book(p2, doctor, 1);
        long a3 = book(p3, doctor, 2);

        // scenario 1+2: booking succeeded and tokens are sequential for this doctor/date
        JsonNode s1 = queueStatus(p1, a1), s3 = queueStatus(p3, a3);
        assertThat(s1.get("yourToken").asText()).isEqualTo("A-001");
        assertThat(queueStatus(p2, a2).get("yourToken").asText()).isEqualTo("A-002");
        assertThat(s3.get("yourToken").asText()).isEqualTo("A-003");

        // scenario 3: correct queue position (nobody served yet; 2 patients ahead of token 3)
        assertThat(s3.get("patientsAhead").asInt()).isEqualTo(2);
        assertThat(s3.get("estimatedWaitMinutes").asInt()).isEqualTo(10);   // 2 x 5 min default
        assertThat(s3.hasNonNull("nowServing")).isFalse();
        assertThat(s3.get("status").asText()).isEqualTo("BOOKED");
        assertThat(s3.get("estimateNote").asText()).containsIgnoringCase("estimate");
    }

    @Test
    void callingAndCompletingAdvancesTheQueue() throws Exception {
        DoctorAccount doctor = newDoctor();
        Account p1 = newPatient(), p2 = newPatient(), p3 = newPatient();
        long a1 = book(p1, doctor, 0), a2 = book(p2, doctor, 1), a3 = book(p3, doctor, 2);

        // scenario 4: calling next updates the serving token and shrinks the line for everyone behind
        callNext(doctor).andExpect(status().isOk()).andExpect(jsonPath("$.token").value("A-001"))
                .andExpect(jsonPath("$.status").value("CALLED"));
        JsonNode s3 = queueStatus(p3, a3);
        assertThat(s3.get("nowServing").asText()).isEqualTo("A-001");
        assertThat(s3.get("patientsAhead").asInt()).isEqualTo(1);           // only token 2 is still ahead
        assertThat(queueStatus(p1, a1).get("status").asText()).isEqualTo("CALLED");

        // only one patient may be active at a time
        callNext(doctor).andExpect(status().isConflict());

        // scenario 5: completing frees the doctor; the next call moves to token 2
        send(post("/api/queue/appointments/" + a1 + "/start"), doctor.token()).andExpect(status().isOk());
        send(post("/api/queue/appointments/" + a1 + "/complete"), doctor.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(queueStatus(p1, a1).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(queueStatus(p1, a1).get("patientsAhead").asInt()).isZero();

        callNext(doctor).andExpect(jsonPath("$.token").value("A-002"));
        JsonNode after = queueStatus(p3, a3);
        assertThat(after.get("nowServing").asText()).isEqualTo("A-002");
        assertThat(after.get("patientsAhead").asInt()).isZero();
        assertThat(after.get("estimatedWaitMinutes").asInt()).isZero();

        // skipping the called patient lets the line move on to token 3
        send(post("/api/queue/appointments/" + a2 + "/skip"), doctor.token()).andExpect(status().isOk());
        callNext(doctor).andExpect(jsonPath("$.token").value("A-003"));
    }

    @Test
    void cancelledAppointmentsAreExcludedFromTheCount() throws Exception {
        DoctorAccount doctor = newDoctor();
        Account p1 = newPatient(), p2 = newPatient(), p3 = newPatient();
        book(p1, doctor, 0);
        long a2 = book(p2, doctor, 1);
        long a3 = book(p3, doctor, 2);
        assertThat(queueStatus(p3, a3).get("patientsAhead").asInt()).isEqualTo(2);

        // scenario 8
        send(post("/api/appointments/" + a2 + "/cancel"), p2.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        JsonNode s3 = queueStatus(p3, a3);
        assertThat(s3.get("patientsAhead").asInt()).isEqualTo(1);
        assertThat(s3.get("estimatedWaitMinutes").asInt()).isEqualTo(5);

        // the cancelled token is never handed out again, and the freed slot can be re-booked
        Account p4 = newPatient();
        long a4 = book(p4, doctor, 1);
        assertThat(queueStatus(p4, a4).get("yourToken").asText()).isEqualTo("A-004");
        // call-next skips the cancelled appointment: 1 then 3
        callNext(doctor).andExpect(jsonPath("$.token").value("A-001"));
    }

    @Test
    void queuesAreIndependentPerDoctorAndPerDate() throws Exception {
        DoctorAccount d1 = newDoctor(), d2 = newDoctor();
        Account p = newPatient(), q = newPatient();
        long onD1 = book(p, d1, 0);
        long onD2 = book(q, d2, 0);
        assertThat(queueStatus(p, onD1).get("yourToken").asText()).isEqualTo("A-001");
        assertThat(queueStatus(q, onD2).get("yourToken").asText()).isEqualTo("A-001");

        // a different date for the same doctor starts at 001 again
        String body = bookRequest(p.token(), d1.id(), tomorrow().plusDays(1), slot(0)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).get("token").asText()).isEqualTo("A-001");
    }

    @Test
    void tokenIsStableAcrossLogout_andPatientHistoryIsPaged() throws Exception {
        DoctorAccount doctor = newDoctor();
        Account p = newPatient();
        long id = book(p, doctor, 3);
        // a fresh token for the same user (a new login) sees the same appointment and token
        String freshToken = jwt.generate(p.user());
        send(get("/api/appointments/my?page=0&size=5"), freshToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].token").value("A-001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void doubleBookingRulesAreEnforced() throws Exception {
        DoctorAccount doctor = newDoctor();
        Account p = newPatient(), q = newPatient();
        book(p, doctor, 0);
        // same slot, other patient
        bookRequest(q.token(), doctor.id(), tomorrow(), slot(0)).andExpect(status().isConflict());
        // same doctor and day, same patient, other slot
        bookRequest(p.token(), doctor.id(), tomorrow(), slot(5)).andExpect(status().isConflict());
        // invalid slot time and past date
        bookRequest(q.token(), doctor.id(), tomorrow(), java.time.LocalTime.of(9, 7)).andExpect(status().isBadRequest());
        bookRequest(q.token(), doctor.id(), tomorrow().minusDays(5), slot(0)).andExpect(status().isBadRequest());
        // slots endpoint reflects the taken slot
        send(get("/api/doctors/" + doctor.id() + "/slots?date=" + tomorrow()), null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(false)).andExpect(jsonPath("$[1].available").value(true));
    }
}
