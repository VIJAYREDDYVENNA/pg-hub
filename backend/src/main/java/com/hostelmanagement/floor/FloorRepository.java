package com.hostelmanagement.floor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FloorRepository extends JpaRepository<Floor, Long> {

    List<Floor> findByBuildingIdAndDeletedAtIsNullOrderBySortOrderAscLevelNumberAsc(Long buildingId);

    Optional<Floor> findByIdAndDeletedAtIsNull(Long id);

    long countByBuildingIdAndDeletedAtIsNull(Long buildingId);

    boolean existsByBuildingIdAndNameIgnoreCaseAndDeletedAtIsNull(Long buildingId, String name);
}
