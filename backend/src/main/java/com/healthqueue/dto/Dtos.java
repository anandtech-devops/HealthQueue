package com.healthqueue.dto;

import com.healthqueue.domain.AppointmentStatus;
import com.healthqueue.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

/** All request/response payloads in one place to keep the project easy to navigate. */
public final class Dtos {
    private Dtos() {}

    // ---- auth ----
    public record RegisterRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters") String password) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record UserDto(Long id, String fullName, String email, Role role, Long doctorId) {}

    public record AuthResponse(String token, UserDto user) {}

    // ---- doctors ----
    public record DepartmentDto(Long id, String name) {}

    public record DoctorDto(Long id, String fullName, String title, Long departmentId, String department,
                            String tokenPrefix, int slotMinutes, LocalTime startTime, LocalTime endTime,
                            String workingDays, boolean active) {}

    public record DoctorCreateRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull Long departmentId,
            @NotBlank @Size(max = 100) String title,
            @NotNull @Min(5) @Max(120) Integer slotMinutes,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotBlank @Pattern(regexp = "^(MON|TUE|WED|THU|FRI|SAT|SUN)(,(MON|TUE|WED|THU|FRI|SAT|SUN))*$",
                    message = "must be a comma separated list such as MON,TUE,WED") String workingDays) {}

    public record DoctorUpdateRequest(
            @NotNull Long departmentId,
            @NotBlank @Size(max = 100) String title,
            @NotNull @Min(5) @Max(120) Integer slotMinutes,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotBlank @Pattern(regexp = "^(MON|TUE|WED|THU|FRI|SAT|SUN)(,(MON|TUE|WED|THU|FRI|SAT|SUN))*$",
                    message = "must be a comma separated list such as MON,TUE,WED") String workingDays,
            @NotNull Boolean active) {}

    public record SlotDto(LocalTime time, boolean available) {}

    // ---- appointments ----
    public record BookingRequest(@NotNull Long doctorId, @NotNull LocalDate date, @NotNull LocalTime slotTime) {}

    public record AppointmentDto(Long id, Long doctorId, String doctorName, String doctorTitle, String department,
                                 LocalDate date, LocalTime slotTime, String token, int tokenNumber,
                                 AppointmentStatus status, Instant createdAt) {}

    // ---- queue ----
    /** What a patient sees: only their own data plus the public "now serving" token. */
    public record QueueStatusDto(Long appointmentId, String patientName, String doctorName, String doctorTitle,
                                 LocalDate date, LocalTime slotTime, String yourToken, String nowServing,
                                 long patientsAhead, int estimatedWaitMinutes, int avgConsultationMinutes,
                                 AppointmentStatus status, String estimateNote) {}

    /** Doctor / admin view of one queue row. */
    public record QueueEntryDto(Long appointmentId, String token, int tokenNumber, String patientName,
                                LocalTime slotTime, AppointmentStatus status) {}

    public record DailyQueueDto(Long doctorId, String doctorName, String doctorTitle, LocalDate date,
                                String nowServing, long waitingCount, int avgConsultationMinutes,
                                List<QueueEntryDto> entries) {}

    /** Public, anonymous-safe queue summary (no patient information). */
    public record QueueSummaryDto(Long doctorId, LocalDate date, String nowServing, long waitingCount,
                                  int avgConsultationMinutes) {}

    // ---- admin ----
    public record AdminAppointmentDto(Long id, Long doctorId, String doctorName, String patientName, LocalDate date,
                                      LocalTime slotTime, String token, AppointmentStatus status) {}

    public record DoctorOverviewDto(Long doctorId, String doctorName, String title, Map<AppointmentStatus, Long> counts,
                                    String nowServing) {}

    // ---- generic ----
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> of(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }

    public record ApiError(Instant timestamp, int status, String error, String message, String path,
                           Map<String, String> fieldErrors) {}
}
