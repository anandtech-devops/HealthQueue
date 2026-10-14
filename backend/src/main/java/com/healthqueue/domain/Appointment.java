package com.healthqueue.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "appointments")
public class Appointment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "patient_id")
    private User patient;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "doctor_id")
    private Doctor doctor;
    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;
    @Column(name = "slot_time", nullable = false)
    private LocalTime slotTime;
    @Column(name = "token_number", nullable = false)
    private int tokenNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private AppointmentStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "called_at")
    private Instant calledAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected Appointment() {}

    public Appointment(User patient, Doctor doctor, LocalDate date, LocalTime slot, int tokenNumber, Instant now) {
        this.patient = patient;
        this.doctor = doctor;
        this.appointmentDate = date;
        this.slotTime = slot;
        this.tokenNumber = tokenNumber;
        this.status = AppointmentStatus.BOOKED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void changeStatus(AppointmentStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
        if (next == AppointmentStatus.CALLED) this.calledAt = now;
        if (next == AppointmentStatus.COMPLETED) this.completedAt = now;
    }

    public String displayToken() { return tokenLabel(doctor.getTokenPrefix(), tokenNumber); }

    public static String tokenLabel(String prefix, int number) { return "%s-%03d".formatted(prefix, number); }

    public Long getId() { return id; }
    public User getPatient() { return patient; }
    public Doctor getDoctor() { return doctor; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public LocalTime getSlotTime() { return slotTime; }
    public int getTokenNumber() { return tokenNumber; }
    public AppointmentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCalledAt() { return calledAt; }
    public Instant getCompletedAt() { return completedAt; }
}
