package com.hostelmanagement.payments.online;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface PaymentLinkRepository extends JpaRepository<PaymentLink, Long> {

    Optional<PaymentLink> findByTokenHash(String tokenHash);

    @Modifying
    @Transactional
    @Query("UPDATE PaymentLink l SET l.revokedAt = :now WHERE l.invoiceId = :invoiceId AND l.revokedAt IS NULL")
    int revokeActiveForInvoice(@Param("invoiceId") Long invoiceId, @Param("now") Instant now);
}
