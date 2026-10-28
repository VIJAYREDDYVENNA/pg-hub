package com.hostelmanagement.payments;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    List<PaymentAllocation> findByPaymentId(Long paymentId);
}
