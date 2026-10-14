package com.healthqueue.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle: BOOKED -> WAITING (checked in) -> CALLED -> IN_CONSULTATION -> COMPLETED.
 * BOOKED/WAITING can also end as CANCELLED; CALLED/IN_CONSULTATION patients can be SKIPPED (no-show).
 */
public enum AppointmentStatus {
    BOOKED, WAITING, CALLED, IN_CONSULTATION, COMPLETED, CANCELLED, SKIPPED;

    /** Patients that are still in line and count as "ahead" of later tokens. */
    public static final Set<AppointmentStatus> PENDING = EnumSet.of(BOOKED, WAITING);
    /** Patient currently being called / with the doctor. */
    public static final Set<AppointmentStatus> ACTIVE = EnumSet.of(CALLED, IN_CONSULTATION);
    /** Statuses a patient may still cancel. */
    public static final Set<AppointmentStatus> CANCELLABLE = PENDING;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == SKIPPED;
    }
}
