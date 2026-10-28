package com.hostelmanagement.payments;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOwnerIdAndIdempotencyKey(Long ownerId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM Payment p WHERE p.residentId = :residentId AND p.status = 'RECEIVED'
            AND p.unallocatedAmount > 0 ORDER BY p.paymentDate ASC, p.id ASC""")
    List<Payment> lockWithCredit(@Param("residentId") Long residentId);
}
