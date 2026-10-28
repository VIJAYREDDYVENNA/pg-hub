package com.hostelmanagement.payments.online;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class OnlinePaymentDtos {

    private OnlinePaymentDtos() {
    }

    public record CreateOrderRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount) {
    }

    /** What the browser sends back after checkout. {@code fields} are the provider's raw callback fields. */
    public record ConfirmRequest(@NotBlank String providerOrderId, @NotNull Map<String, String> fields) {
    }

    /** {@code checkout} contains only public checkout parameters, never secrets. */
    public record OrderDto(Long attemptId, String provider, boolean simulated, BigDecimal amount, String currency,
                           Map<String, Object> checkout) {
    }

    public record AttemptDto(Long id, Long invoiceId, String provider, String providerOrderId, String providerPaymentId,
                             BigDecimal amount, String currency, String status, String failureReason, String method,
                             boolean simulated, Long paymentId, String receiptNumber, String initiatedVia,
                             Instant createdAt, Instant completedAt) {
    }

    public record GatewayConfigDto(String provider, String mode, boolean simulated, String currency, BigDecimal minPartialAmount,
                                   boolean partialPayments, boolean webhooks, List<String> paymentMethods) {
    }

    public record PaymentLinkDto(String url, Instant expiresAt) {
    }

    /** Deliberately minimal view for the unauthenticated pay page: no phone numbers, no other residents' data. */
    public record PublicInvoiceDto(String invoiceNumber, String hostelName, String residentFirstName, String invoiceType,
                                   LocalDate dueDate, BigDecimal totalAmount, BigDecimal amountPaid, BigDecimal balance,
                                   String status, boolean payable, Instant linkExpiresAt, GatewayConfigDto gateway,
                                   com.hostelmanagement.paymentmethods.PaymentMethodDtos.ManualPaymentInfo manualPayment) {
    }
}
