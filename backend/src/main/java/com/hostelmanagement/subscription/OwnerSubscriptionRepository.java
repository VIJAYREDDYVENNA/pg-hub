package com.hostelmanagement.subscription;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OwnerSubscriptionRepository extends JpaRepository<OwnerSubscription, Long> {
    Optional<OwnerSubscription> findByOwnerId(Long ownerId);

    /** Row lock that serialises every limit check for one owner (taken inside the caller's transaction). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM OwnerSubscription s WHERE s.ownerId = :ownerId")
    Optional<OwnerSubscription> lockByOwnerId(@Param("ownerId") Long ownerId);

    long countByPlanId(Long planId);

    @Query("SELECT s.planId, COUNT(s) FROM OwnerSubscription s GROUP BY s.planId")
    List<Object[]> countPerPlan();
}
