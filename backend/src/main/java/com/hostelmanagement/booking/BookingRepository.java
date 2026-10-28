package com.hostelmanagement.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBedIdAndStatus(Long bedId, String status);

    @Query("""
            SELECT b FROM Booking b WHERE b.hostelId IN :hostelIds AND (:status IS NULL OR b.status = :status)""")
    Page<Booking> search(@Param("hostelIds") Collection<Long> hostelIds, @Param("status") String status, Pageable pageable);

    List<Booking> findByStatusAndHoldUntilBefore(String status, LocalDate date);
}
