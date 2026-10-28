package com.hostelmanagement.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    Optional<SubscriptionPlan> findByCode(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<SubscriptionPlan> findAllByOrderBySortOrderAscIdAsc();
}
