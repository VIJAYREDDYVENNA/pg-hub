package com.hostelmanagement.maintenance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceAssignmentRepository extends JpaRepository<MaintenanceAssignment, Long> {

    List<MaintenanceAssignment> findByRequestIdOrderByCreatedAtAsc(Long requestId);
}
