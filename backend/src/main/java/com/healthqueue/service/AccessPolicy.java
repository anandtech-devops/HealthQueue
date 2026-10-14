package com.healthqueue.service;

import com.healthqueue.domain.Appointment;
import com.healthqueue.domain.Doctor;
import com.healthqueue.domain.Role;
import com.healthqueue.repository.DoctorRepository;
import com.healthqueue.security.AuthUser;
import com.healthqueue.web.ApiException;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Ownership / assignment checks (enforced server-side; the UI only mirrors them). */
@Component
public class AccessPolicy {

    private final DoctorRepository doctors;

    public AccessPolicy(DoctorRepository doctors) {
        this.doctors = doctors;
    }

    public Optional<Long> doctorIdOf(AuthUser user) {
        if (user.role() != Role.DOCTOR) return Optional.empty();
        return doctors.findByUserId(user.id()).map(Doctor::getId);
    }

    /** Admin may manage any queue; a doctor only their own. */
    public void assertCanManageQueue(AuthUser user, Long doctorId) {
        if (user.isAdmin()) return;
        if (user.role() == Role.DOCTOR && doctorIdOf(user).filter(doctorId::equals).isPresent()) return;
        throw ApiException.forbidden("You can only manage your own queue");
    }

    /** Patients: only their own appointment. Doctors: only appointments assigned to them. Admin: any. */
    public void assertCanView(AuthUser user, Appointment appt) {
        switch (user.role()) {
            case ADMIN -> { }
            case PATIENT -> {
                if (!appt.getPatient().getId().equals(user.id())) throw notYours();
            }
            case DOCTOR -> assertCanManageQueue(user, appt.getDoctor().getId());
        }
    }

    private static ApiException notYours() {
        // 404 rather than 403 so that appointment ids of other patients cannot be probed.
        return ApiException.notFound("Appointment not found");
    }
}
