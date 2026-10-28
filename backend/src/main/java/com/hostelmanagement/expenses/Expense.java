package com.hostelmanagement.expenses;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "expenses")
public class Expense extends BaseEntity {

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    @Column(length = 150)
    private String vendor;

    @Column(length = 100)
    private String reference;

    @Column(name = "maintenance_request_id")
    private Long maintenanceRequestId;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;
}
