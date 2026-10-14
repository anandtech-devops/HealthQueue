package com.healthqueue.repository;

import com.healthqueue.domain.Appointment;
import com.healthqueue.domain.AppointmentStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @EntityGraph(attributePaths = {"doctor", "doctor.user", "doctor.department", "patient"})
    Optional<Appointment> findWithDetailsById(Long id);

    boolean existsByPatientIdAndDoctorIdAndAppointmentDateAndStatusNot(
            Long patientId, Long doctorId, LocalDate date, AppointmentStatus status);

    @EntityGraph(attributePaths = {"doctor", "doctor.user", "doctor.department"})
    Page<Appointment> findByPatientIdOrderByAppointmentDateDescSlotTimeDesc(Long patientId, Pageable pageable);

    @Query("select a.slotTime from Appointment a where a.doctor.id = :doctorId and a.appointmentDate = :date "
            + "and a.status <> com.healthqueue.domain.AppointmentStatus.CANCELLED")
    List<LocalTime> findTakenSlots(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);

    /** Number of appointments with a smaller token that are in one of the given statuses. */
    @Query("select count(a) from Appointment a where a.doctor.id = :doctorId and a.appointmentDate = :date "
            + "and a.tokenNumber < :token and a.status in :statuses")
    long countAhead(@Param("doctorId") Long doctorId, @Param("date") LocalDate date,
                    @Param("token") int token, @Param("statuses") Collection<AppointmentStatus> statuses);

    @Query("select count(a) from Appointment a where a.doctor.id = :doctorId and a.appointmentDate = :date "
            + "and a.status in :statuses")
    long countByStatuses(@Param("doctorId") Long doctorId, @Param("date") LocalDate date,
                         @Param("statuses") Collection<AppointmentStatus> statuses);

    @EntityGraph(attributePaths = {"patient", "doctor"})
    Optional<Appointment> findFirstByDoctorIdAndAppointmentDateAndStatusInOrderByTokenNumberAsc(
            Long doctorId, LocalDate date, Collection<AppointmentStatus> statuses);

    @EntityGraph(attributePaths = {"patient", "doctor"})
    Optional<Appointment> findFirstByDoctorIdAndAppointmentDateAndStatusInOrderByTokenNumberDesc(
            Long doctorId, LocalDate date, Collection<AppointmentStatus> statuses);

    @EntityGraph(attributePaths = {"patient", "doctor"})
    List<Appointment> findByDoctorIdAndAppointmentDateOrderByTokenNumberAsc(Long doctorId, LocalDate date);

    @EntityGraph(attributePaths = {"patient", "doctor", "doctor.user", "doctor.department"})
    @Query("select a from Appointment a where a.appointmentDate = :date "
            + "and (:doctorId is null or a.doctor.id = :doctorId) "
            + "and (:status is null or a.status = :status) order by a.doctor.id, a.tokenNumber")
    Page<Appointment> searchDaily(@Param("date") LocalDate date, @Param("doctorId") Long doctorId,
                                  @Param("status") AppointmentStatus status, Pageable pageable);

    @Query("select a.doctor.id, a.status, count(a) from Appointment a where a.appointmentDate = :date "
            + "group by a.doctor.id, a.status")
    List<Object[]> countByDoctorAndStatus(@Param("date") LocalDate date);
}
