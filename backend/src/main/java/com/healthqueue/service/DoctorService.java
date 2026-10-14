package com.healthqueue.service;

import com.healthqueue.config.AppProperties;
import com.healthqueue.domain.Department;
import com.healthqueue.domain.Doctor;
import com.healthqueue.domain.Role;
import com.healthqueue.domain.User;
import com.healthqueue.dto.Dtos.DepartmentDto;
import com.healthqueue.dto.Dtos.DoctorCreateRequest;
import com.healthqueue.dto.Dtos.DoctorDto;
import com.healthqueue.dto.Dtos.DoctorUpdateRequest;
import com.healthqueue.dto.Dtos.PageResponse;
import com.healthqueue.dto.Dtos.SlotDto;
import com.healthqueue.repository.AppointmentRepository;
import com.healthqueue.repository.DepartmentRepository;
import com.healthqueue.repository.DoctorRepository;
import com.healthqueue.repository.UserRepository;
import com.healthqueue.web.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorService {

    private final DoctorRepository doctors;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final AppointmentRepository appointments;
    private final PasswordEncoder encoder;
    private final AppProperties props;
    private final Clock clock;

    public DoctorService(DoctorRepository doctors, DepartmentRepository departments, UserRepository users,
                         AppointmentRepository appointments, PasswordEncoder encoder, AppProperties props, Clock clock) {
        this.doctors = doctors;
        this.departments = departments;
        this.users = users;
        this.appointments = appointments;
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DepartmentDto> departments() {
        return departments.findAll(Sort.by("name")).stream().map(d -> new DepartmentDto(d.getId(), d.getName())).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<DoctorDto> list(Long departmentId, boolean includeInactive, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100), Sort.by("user.fullName"));
        return PageResponse.of(doctors.search(departmentId, includeInactive, pageable).map(Mappers::toDto));
    }

    @Transactional(readOnly = true)
    public DoctorDto get(Long id) {
        return Mappers.toDto(find(id));
    }

    @Transactional
    public DoctorDto create(DoctorCreateRequest req) {
        validateHours(req.startTime(), req.endTime(), req.slotMinutes());
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("An account with this e-mail already exists");
        Department dept = department(req.departmentId());
        User user = users.save(new User(req.fullName().trim(), email, encoder.encode(req.password()), Role.DOCTOR));
        String prefix = String.valueOf((char) ('A' + (int) (doctors.count() % 26)));
        Doctor d = doctors.save(new Doctor(user, dept, req.title().trim(), prefix, req.slotMinutes(),
                req.startTime(), req.endTime(), req.workingDays()));
        return Mappers.toDto(d);
    }

    @Transactional
    public DoctorDto update(Long id, DoctorUpdateRequest req) {
        validateHours(req.startTime(), req.endTime(), req.slotMinutes());
        Doctor d = find(id);
        d.update(department(req.departmentId()), req.title().trim(), req.slotMinutes(), req.startTime(),
                req.endTime(), req.workingDays(), req.active());
        return Mappers.toDto(d);
    }

    /** Bookable slots for one doctor on one date. A slot is unavailable if taken (or already past today). */
    @Transactional(readOnly = true)
    public List<SlotDto> slots(Long doctorId, LocalDate date) {
        Doctor d = find(doctorId);
        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today)) throw ApiException.badRequest("Date is in the past");
        if (date.isAfter(today.plusDays(props.booking().maxDaysAhead()))) {
            throw ApiException.badRequest("Bookings open up to %d days ahead".formatted(props.booking().maxDaysAhead()));
        }
        if (!d.isActive() || !d.worksOn(date.getDayOfWeek())) return List.of();
        Set<LocalTime> taken = new HashSet<>(appointments.findTakenSlots(doctorId, date));
        LocalTime now = LocalTime.now(clock);
        boolean hidePast = date.equals(today) && !props.booking().allowPastSlotsToday();
        List<SlotDto> result = new ArrayList<>();
        for (LocalTime t = d.getStartTime(); !t.plusMinutes(d.getSlotMinutes()).isAfter(d.getEndTime());
             t = t.plusMinutes(d.getSlotMinutes())) {
            result.add(new SlotDto(t, !taken.contains(t) && !(hidePast && t.isBefore(now))));
            if (t.plusMinutes(d.getSlotMinutes()).isBefore(t)) break;   // guard against midnight wrap-around
        }
        return result;
    }

    Doctor find(Long id) {
        return doctors.findWithDetailsById(id).orElseThrow(() -> ApiException.notFound("Doctor not found"));
    }

    private Department department(Long id) {
        return departments.findById(id).orElseThrow(() -> ApiException.badRequest("Unknown department"));
    }

    private static void validateHours(LocalTime start, LocalTime end, int slotMinutes) {
        if (!start.isBefore(end)) throw ApiException.badRequest("Start time must be before end time");
        if (start.plusMinutes(slotMinutes).isAfter(end)) throw ApiException.badRequest("Working hours must fit at least one slot");
    }
}
