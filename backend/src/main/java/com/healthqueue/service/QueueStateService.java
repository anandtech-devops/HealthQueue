package com.healthqueue.service;

import com.healthqueue.repository.QueueStateRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concurrency control for one doctor/date queue. Every operation that changes the queue (booking,
 * cancelling, calling, completing...) first takes the queue row lock here, so those operations run one at
 * a time per queue while different doctors/dates stay fully parallel. The lock is held until the caller's
 * transaction commits or rolls back, so a rolled-back booking never burns a token number.
 */
@Service
public class QueueStateService {

    private final QueueStateRepository repo;

    public QueueStateService(QueueStateRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(Long doctorId, LocalDate date) {
        repo.lock(doctorId, date);
    }

    /** Next token number for the queue (1, 2, 3, ...). Must be called inside the booking transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int issueToken(Long doctorId, LocalDate date) {
        repo.incrementCounter(doctorId, date);
        return repo.counter(doctorId, date);
    }
}
