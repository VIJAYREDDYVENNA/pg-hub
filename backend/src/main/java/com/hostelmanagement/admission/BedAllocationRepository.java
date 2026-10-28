package com.hostelmanagement.admission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BedAllocationRepository extends JpaRepository<BedAllocation, Long> {

    Optional<BedAllocation> findByAdmissionIdAndEndDateIsNull(Long admissionId);

    Optional<BedAllocation> findByBedIdAndEndDateIsNull(Long bedId);
}
