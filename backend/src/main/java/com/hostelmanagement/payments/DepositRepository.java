package com.hostelmanagement.payments;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DepositRepository extends JpaRepository<Deposit, Long> {

    Optional<Deposit> findByInvoiceId(Long invoiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Deposit d WHERE d.admissionId = :admissionId")
    Optional<Deposit> lockByAdmissionId(@Param("admissionId") Long admissionId);
}
