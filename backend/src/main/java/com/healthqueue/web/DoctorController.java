package com.healthqueue.web;

import com.healthqueue.dto.Dtos.DepartmentDto;
import com.healthqueue.dto.Dtos.DoctorDto;
import com.healthqueue.dto.Dtos.PageResponse;
import com.healthqueue.dto.Dtos.SlotDto;
import com.healthqueue.security.AuthUser;
import com.healthqueue.service.DoctorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Doctors")
public class DoctorController {

    private final DoctorService doctors;
    private final com.healthqueue.service.AccessPolicy policy;

    public DoctorController(DoctorService doctors, com.healthqueue.service.AccessPolicy policy) {
        this.doctors = doctors;
        this.policy = policy;
    }

    @GetMapping("/departments")
    public List<DepartmentDto> departments() {
        return doctors.departments();
    }

    @GetMapping("/doctors")
    public PageResponse<DoctorDto> list(@RequestParam(required = false) Long departmentId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return doctors.list(departmentId, false, page, size);
    }

    @GetMapping("/doctors/{id}")
    public DoctorDto get(@PathVariable Long id) {
        return doctors.get(id);
    }

    @GetMapping("/doctors/{id}/slots")
    public List<SlotDto> slots(@PathVariable Long id,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return doctors.slots(id, date);
    }

    /** The logged-in doctor's own profile (used by the doctor dashboard). */
    @GetMapping("/doctor/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public DoctorDto myProfile(@AuthenticationPrincipal AuthUser user) {
        Long id = policy.doctorIdOf(user).orElseThrow(() -> ApiException.notFound("No doctor profile for this account"));
        return doctors.get(id);
    }
}
