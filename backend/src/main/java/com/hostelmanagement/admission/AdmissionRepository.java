package com.hostelmanagement.admission;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdmissionRepository extends JpaRepository<Admission, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Admission a WHERE a.id = :id")
    Optional<Admission> lockById(@Param("id") Long id);

    boolean existsByResidentIdAndStatus(Long residentId, String status);

    List<Admission> findByStatus(String status);

    List<Admission> findByStatusAndHostelIdIn(String status, java.util.Collection<Long> hostelIds);

    Optional<Admission> findFirstByResidentIdAndStatus(Long residentId, String status);
}
