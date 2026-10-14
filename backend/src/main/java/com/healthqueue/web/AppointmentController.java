package com.healthqueue.web;

import com.healthqueue.dto.Dtos.AppointmentDto;
import com.healthqueue.dto.Dtos.BookingRequest;
import com.healthqueue.dto.Dtos.PageResponse;
import com.healthqueue.dto.Dtos.QueueEntryDto;
import com.healthqueue.dto.Dtos.QueueStatusDto;
import com.healthqueue.security.AuthUser;
import com.healthqueue.service.AppointmentService;
import com.healthqueue.service.QueueService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
@Tag(name = "Appointments")
public class AppointmentController {

    private final AppointmentService appointments;
    private final QueueService queue;

    public AppointmentController(AppointmentService appointments, QueueService queue) {
        this.appointments = appointments;
        this.queue = queue;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PATIENT')")
    public AppointmentDto book(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody BookingRequest req) {
        return appointments.book(user, req);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('PATIENT')")
    public PageResponse<AppointmentDto> mine(@AuthenticationPrincipal AuthUser user,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return appointments.myAppointments(user, page, size);
    }

    @GetMapping("/{id}")
    public AppointmentDto get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return appointments.get(user, id);
    }

    /** Queue position for one appointment. Patients can only query their own. */
    @GetMapping("/{id}/queue")
    public QueueStatusDto queueStatus(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return queue.statusFor(id, user);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('PATIENT','ADMIN')")
    public QueueEntryDto cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return queue.cancel(id, user);
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAnyRole('PATIENT','ADMIN')")
    public QueueEntryDto checkIn(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return queue.checkIn(id, user);
    }
}
