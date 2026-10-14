package com.healthqueue.service;

/** Pure helpers for queue numbers, kept free of persistence so they are trivially testable. */
public final class QueueMath {
    private QueueMath() {}

    /** Approximate wait = patients still waiting ahead x configured average consultation time. */
    public static int estimateWaitMinutes(long patientsAhead, int avgConsultationMinutes) {
        if (patientsAhead <= 0 || avgConsultationMinutes <= 0) return 0;
        return Math.toIntExact(patientsAhead * avgConsultationMinutes);
    }
}
