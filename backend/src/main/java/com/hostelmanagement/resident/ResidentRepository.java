package com.hostelmanagement.resident;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResidentRepository extends JpaRepository<Resident, Long> {
}
