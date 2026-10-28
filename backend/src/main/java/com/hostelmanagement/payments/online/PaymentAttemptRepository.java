package com.hostelmanagement.payments.online;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    Optional<PaymentAttempt> findByProviderAndProviderOrderId(String provider, String providerOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PaymentAttempt a WHERE a.provider = :provider AND a.providerOrderId = :orderId")
    Optional<PaymentAttempt> lockByOrder(@Param("provider") String provider, @Param("orderId") String orderId);

    List<PaymentAttempt> findByInvoiceIdOrderByIdDesc(Long invoiceId);

    boolean existsByInvoiceIdAndStatusAndCreatedAtAfter(Long invoiceId, PaymentAttempt.Status status, Instant after);

    @Query("""
            SELECT a FROM PaymentAttempt a WHERE a.status IN :statuses AND a.createdAt < :olderThan
            AND a.createdAt > :newerThan ORDER BY a.id""")
    List<PaymentAttempt> findOpenBetween(@Param("statuses") Collection<PaymentAttempt.Status> statuses,
                                         @Param("olderThan") Instant olderThan, @Param("newerThan") Instant newerThan);

    @Modifying
    @Transactional
    @Query("UPDATE PaymentAttempt a SET a.status = 'EXPIRED', a.updatedAt = :now WHERE a.status = 'CREATED' AND a.createdAt < :before")
    int expireCreatedBefore(@Param("before") Instant before, @Param("now") Instant now);
}
