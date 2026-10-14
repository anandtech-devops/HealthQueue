package com.healthqueue.web;

import com.healthqueue.domain.AppointmentStatus;
import com.healthqueue.dto.Dtos.AdminAppointmentDto;
import com.healthqueue.dto.Dtos.DoctorCreateRequest;
import com.healthqueue.dto.Dtos.DoctorDto;
import com.healthqueue.dto.Dtos.DoctorOverviewDto;
import com.healthqueue.dto.Dtos.DoctorUpdateRequest;
import com.healthqueue.dto.Dtos.PageResponse;
import com.healthqueue.service.DoctorService;
import com.healthqueue.service.QueueService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin / Receptionist")
public class AdminController {

    private final DoctorService doctors;
    private final QueueService queue;
    private final Clock clock;

    public AdminController(DoctorService doctors, QueueService queue, Clock clock) {
        this.doctors = doctors;
        this.queue = queue;
        this.clock = clock;
    }

    @GetMapping("/doctors")
    public PageResponse<DoctorDto> listDoctors(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        return doctors.list(null, true, page, size);
    }

    @PostMapping("/doctors")
    @ResponseStatus(HttpStatus.CREATED)
    public DoctorDto createDoctor(@Valid @RequestBody DoctorCreateRequest req) {
        return doctors.create(req);
    }

    @PutMapping("/doctors/{id}")
    public DoctorDto updateDoctor(@PathVariable Long id, @Valid @RequestBody DoctorUpdateRequest req) {
        return doctors.update(id, req);
    }

    @GetMapping("/appointments")
    public PageResponse<AdminAppointmentDto> appointments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return queue.adminAppointments(date != null ? date : LocalDate.now(clock), doctorId, status, page, size);
    }

    @GetMapping("/overview")
    public List<DoctorOverviewDto> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queue.overview(date != null ? date : LocalDate.now(clock));
    }
}
