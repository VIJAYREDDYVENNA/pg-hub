package com.hostelmanagement.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {
    List<SubscriptionPayment> findTop50ByOwnerIdOrderByIdDesc(Long ownerId);
}
