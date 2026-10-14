package com.healthqueue.domain;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "doctors")
public class Doctor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "department_id")
    private Department department;
    @Column(nullable = false)
    private String title;
    @Column(name = "token_prefix", nullable = false, length = 1)
    private String tokenPrefix;
    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes;
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
    @Column(name = "working_days", nullable = false)
    private String workingDays;
    @Column(nullable = false)
    private boolean active = true;

    protected Doctor() {}

    public Doctor(User user, Department department, String title, String tokenPrefix, int slotMinutes,
                  LocalTime startTime, LocalTime endTime, String workingDays) {
        this.user = user;
        this.department = department;
        this.title = title;
        this.tokenPrefix = tokenPrefix;
        this.slotMinutes = slotMinutes;
        this.startTime = startTime;
        this.endTime = endTime;
        this.workingDays = workingDays;
    }

    public void update(Department department, String title, int slotMinutes, LocalTime startTime,
                       LocalTime endTime, String workingDays, boolean active) {
        this.department = department;
        this.title = title;
        this.slotMinutes = slotMinutes;
        this.startTime = startTime;
        this.endTime = endTime;
        this.workingDays = workingDays;
        this.active = active;
    }

    public boolean worksOn(DayOfWeek day) {
        return workingDaySet().contains(day.name().substring(0, 3));
    }

    public Set<String> workingDaySet() {
        return Arrays.stream(workingDays.split(",")).map(String::trim).map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Department getDepartment() { return department; }
    public String getTitle() { return title; }
    public String getTokenPrefix() { return tokenPrefix; }
    public int getSlotMinutes() { return slotMinutes; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getWorkingDays() { return workingDays; }
    public boolean isActive() { return active; }
}
