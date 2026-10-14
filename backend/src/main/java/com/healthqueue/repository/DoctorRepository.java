package com.healthqueue.repository;

import com.healthqueue.domain.Doctor;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    @EntityGraph(attributePaths = {"user", "department"})
    @Query("select d from Doctor d where (:departmentId is null or d.department.id = :departmentId) "
            + "and (:includeInactive = true or d.active = true)")
    Page<Doctor> search(@Param("departmentId") Long departmentId,
                        @Param("includeInactive") boolean includeInactive, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "department"})
    Optional<Doctor> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"user", "department"})
    Optional<Doctor> findByUserId(Long userId);
}
