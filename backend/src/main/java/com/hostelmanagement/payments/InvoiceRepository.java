package com.hostelmanagement.payments;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @Query("SELECT i.periodStart FROM Invoice i WHERE i.admissionId = :admissionId AND i.invoiceType = 'RENT'")
    List<LocalDate> findRentPeriodStarts(@Param("admissionId") Long admissionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id")
    Optional<Invoice> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i FROM Invoice i WHERE i.residentId = :residentId AND i.status IN :statuses
            AND i.invoiceType IN :types ORDER BY i.dueDate ASC, i.id ASC""")
    List<Invoice> lockOpenForResident(@Param("residentId") Long residentId,
                                      @Param("statuses") Collection<String> statuses,
                                      @Param("types") Collection<String> types);

    @Query("""
            SELECT i FROM Invoice i WHERE i.admissionId = :admissionId AND i.invoiceType = 'RENT'
            AND i.periodStart > :after AND i.status IN :statuses""")
    List<Invoice> findRentAfter(@Param("admissionId") Long admissionId, @Param("after") LocalDate after,
                                @Param("statuses") Collection<String> statuses);

    @Query("SELECT i FROM Invoice i WHERE i.status IN ('PENDING','PARTIALLY_PAID') AND i.dueDate < :today")
    List<Invoice> findNewlyOverdue(@Param("today") LocalDate today);
}
