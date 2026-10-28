package com.hostelmanagement.bed;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BedRepository extends JpaRepository<Bed, Long> {

    long countByRoomIdAndDeletedAtIsNull(Long roomId);

    List<Bed> findByRoomIdAndDeletedAtIsNullOrderByBedLabelAsc(Long roomId);

    Optional<Bed> findByIdAndDeletedAtIsNull(Long id);

    /** Row lock used while allocating or reserving a bed so concurrent requests are serialized. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bed b WHERE b.id = :id AND b.deletedAt IS NULL")
    Optional<Bed> lockById(@Param("id") Long id);

    boolean existsByRoomIdAndBedLabelIgnoreCaseAndDeletedAtIsNull(Long roomId, String bedLabel);
}
