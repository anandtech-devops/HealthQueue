package com.healthqueue.repository;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Native statements on the {@code queue_states} table (one row per doctor per date).
 * Each write is a single {@code INSERT ... ON DUPLICATE KEY UPDATE}: it creates the row if this is the
 * first booking of the day, otherwise updates it, and in both cases keeps an exclusive row lock until the
 * surrounding transaction ends. That is what serialises concurrent writers of one queue.
 */
public interface QueueStateRepository extends JpaRepository<QueueStateRepository.Marker, Long> {

    /** Take (and create if needed) the queue lock without changing the counter. */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO queue_states (doctor_id, queue_date, last_token_number) VALUES (:doctorId, :date, 0) "
            + "ON DUPLICATE KEY UPDATE last_token_number = last_token_number", nativeQuery = true)
    void lock(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);

    /** Atomically take the lock and add one to the token counter. */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO queue_states (doctor_id, queue_date, last_token_number) VALUES (:doctorId, :date, 1) "
            + "ON DUPLICATE KEY UPDATE last_token_number = last_token_number + 1", nativeQuery = true)
    void incrementCounter(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);

    @Query(value = "SELECT last_token_number FROM queue_states WHERE doctor_id = :doctorId AND queue_date = :date",
            nativeQuery = true)
    int counter(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);

    /** Spring Data needs a managed type to create a repository; the table itself is only touched natively. */
    @jakarta.persistence.Entity
    @jakarta.persistence.Table(name = "queue_states")
    class Marker {
        @jakarta.persistence.Id
        private Long id;
        @jakarta.persistence.Column(name = "doctor_id")
        private Long doctorId;
        @jakarta.persistence.Column(name = "queue_date")
        private LocalDate queueDate;
        @jakarta.persistence.Column(name = "last_token_number")
        private int lastTokenNumber;
    }
}
