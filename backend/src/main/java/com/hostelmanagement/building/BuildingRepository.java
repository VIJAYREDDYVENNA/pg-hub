package com.hostelmanagement.building;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BuildingRepository extends JpaRepository<Building, Long> {

    List<Building> findByHostelIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(Long hostelId);

    Optional<Building> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByHostelIdAndNameIgnoreCaseAndDeletedAtIsNull(Long hostelId, String name);
}
