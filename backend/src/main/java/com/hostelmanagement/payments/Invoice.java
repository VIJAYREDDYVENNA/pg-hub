package com.hostelmanagement.payments;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "invoices")
public class Invoice extends BaseEntity {

    public static final List<String> OPEN_STATUSES = List.of("PENDING", "PARTIALLY_PAID", "OVERDUE");

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "resident_id", nullable = false)
    private Long residentId;

    @Column(name = "admission_id")
    private Long admissionId;

    @Column(name = "invoice_number", nullable = false, length = 40)
    private String invoiceNumber;

    /** RENT, DEPOSIT or OTHER. */
    @Column(name = "invoice_type", nullable = false, length = 20)
    private String invoiceType;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "late_fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal lateFeeAmount;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid;

    /** PENDING, PARTIALLY_PAID, PAID, OVERDUE, WAIVED, CANCELLED. */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Version
    private Long version;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<InvoiceItem> items = new ArrayList<>();

    public BigDecimal balance() {
        return totalAmount.subtract(amountPaid);
    }

    public boolean isOpen() {
        return OPEN_STATUSES.contains(status);
    }

    public void addItem(String type, String description, BigDecimal amount) {
        InvoiceItem item = new InvoiceItem();
        item.setInvoice(this);
        item.setItemType(type);
        item.setDescription(description);
        item.setAmount(amount);
        items.add(item);
    }
}
