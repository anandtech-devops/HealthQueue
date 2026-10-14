package com.healthqueue.service;

import com.healthqueue.domain.Appointment;
import com.healthqueue.domain.Doctor;
import com.healthqueue.domain.User;
import com.healthqueue.dto.Dtos.AdminAppointmentDto;
import com.healthqueue.dto.Dtos.AppointmentDto;
import com.healthqueue.dto.Dtos.DoctorDto;
import com.healthqueue.dto.Dtos.QueueEntryDto;
import com.healthqueue.dto.Dtos.UserDto;

final class Mappers {
    private Mappers() {}

    static DoctorDto toDto(Doctor d) {
        return new DoctorDto(d.getId(), d.getUser().getFullName(), d.getTitle(), d.getDepartment().getId(),
                d.getDepartment().getName(), d.getTokenPrefix(), d.getSlotMinutes(), d.getStartTime(),
                d.getEndTime(), d.getWorkingDays(), d.isActive());
    }

    static AppointmentDto toDto(Appointment a) {
        Doctor d = a.getDoctor();
        return new AppointmentDto(a.getId(), d.getId(), d.getUser().getFullName(), d.getTitle(),
                d.getDepartment().getName(), a.getAppointmentDate(), a.getSlotTime(), a.displayToken(),
                a.getTokenNumber(), a.getStatus(), a.getCreatedAt());
    }

    static QueueEntryDto toEntry(Appointment a) {
        return new QueueEntryDto(a.getId(), a.displayToken(), a.getTokenNumber(), a.getPatient().getFullName(),
                a.getSlotTime(), a.getStatus());
    }

    static AdminAppointmentDto toAdminDto(Appointment a) {
        return new AdminAppointmentDto(a.getId(), a.getDoctor().getId(), a.getDoctor().getUser().getFullName(),
                a.getPatient().getFullName(), a.getAppointmentDate(), a.getSlotTime(), a.displayToken(),
                a.getStatus());
    }

    static UserDto toDto(User u, Long doctorId) {
        return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), doctorId);
    }
}
