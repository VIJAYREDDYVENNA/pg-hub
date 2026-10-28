package com.hostelmanagement.payments.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class FinanceDtos {

    private FinanceDtos() {
    }

    public record InvoiceItemDto(Long id, String itemType, String description, BigDecimal amount) {
    }

    public record InvoiceAllocationDto(Long paymentId, String receiptNumber, LocalDate paymentDate, String method,
                                       BigDecimal amount, String paymentStatus) {
    }

    public record InvoiceDto(Long id, String invoiceNumber, String invoiceType, Long hostelId, String hostelName,
                             Long residentId, String residentName, String residentMobile, Long admissionId,
                             String roomNumber, String bedLabel, LocalDate periodStart, LocalDate periodEnd,
                             LocalDate issueDate, LocalDate dueDate, BigDecimal subtotal, BigDecimal discountAmount,
                             BigDecimal lateFeeAmount, BigDecimal totalAmount, BigDecimal amountPaid,
                             BigDecimal balance, String status, Integer daysOverdue, String notes,
                             List<InvoiceItemDto> items, List<InvoiceAllocationDto> payments) {
    }

    public record PaymentAllocationDto(Long invoiceId, String invoiceNumber, String invoiceType, LocalDate periodStart,
                                       LocalDate periodEnd, BigDecimal amount) {
    }

    public record PaymentDto(Long id, String receiptNumber, Long hostelId, String hostelName, Long residentId,
                             String residentName, String residentMobile, String roomNumber, String bedLabel,
                             BigDecimal amount, BigDecimal unallocatedAmount, LocalDate paymentDate, String method,
                             String transactionReference, String status, String notes, String recordedByName,
                             Instant createdAt, String reversalReason, List<PaymentAllocationDto> allocations) {
    }

    public record RefundDto(Long id, Long hostelId, Long residentId, String residentName, String refundType,
                            BigDecimal amount, LocalDate refundDate, String method, String transactionReference,
                            String reason, String recordedByName, Instant createdAt) {
    }

    public record HostelRevenue(Long hostelId, String hostelName, BigDecimal collected, BigDecimal outstanding) {
    }

    /** {@code netRevenue} = collected - deposit receipts - advance refunds (see PaymentQueries.summary). */
    public record MonthlyPoint(String month, BigDecimal collected, BigDecimal invoiced, BigDecimal expenses,
                               BigDecimal netRevenue) {
    }

    public record AmountCount(long count, BigDecimal amount) {
    }

    public record FinanceSummary(LocalDate today, BigDecimal todayCollections, AmountCount dueToday,
                                 AmountCount overdue, BigDecimal totalOutstanding, BigDecimal monthCollections,
                                 BigDecimal monthInvoiced, BigDecimal monthExpenses, BigDecimal depositsHeld,
                                 List<HostelRevenue> hostelRevenue, List<MonthlyPoint> trend,
                                 BigDecimal monthDepositReceipts, BigDecimal monthAdvanceRefunds,
                                 BigDecimal monthDepositRefunds, BigDecimal monthNetRevenue) {
    }

    public record ReceiptDto(PaymentDto payment, String hostelAddress, String hostelContact, String ownerName) {
    }
}
