package com.hostelmanagement.subscription;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class SubscriptionDtos {

    /** Sanity ceilings that keep a typo (e.g. 5000000 beds) from being saved as a "limit". */
    public static final int MAX_HOSTELS = 1000;
    public static final int MAX_BEDS_PER_HOSTEL = 10_000;
    public static final int MAX_MANAGERS = 1000;

    private SubscriptionDtos() {
    }

    // ------------------------------------------------------------------ plans

    public record PlanRequest(
            @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$", message = "Code must be 2-30 characters: capital letters, digits or _") String code,
            @NotBlank @Size(max = 80) String name,
            @Size(max = 500) String description,
            @DecimalMin("0.00") @DecimalMax("10000000") BigDecimal monthlyPrice,
            @DecimalMin("0.00") @DecimalMax("10000000") BigDecimal annualPrice,
            @Pattern(regexp = "^[A-Z]{3}$", message = "Use a 3-letter currency code") String currency,
            @NotNull @Min(1) @Max(MAX_HOSTELS) Integer maxHostels,
            @NotNull @Min(1) @Max(MAX_BEDS_PER_HOSTEL) Integer maxBedsPerHostel,
            @NotNull @Min(0) @Max(MAX_MANAGERS) Integer maxManagers,
            @NotNull @Min(0) @Max(365) Integer trialDays,
            @NotNull @Min(0) @Max(90) Integer graceDays,
            boolean customPricing,
            @Min(0) @Max(10_000) Integer sortOrder,
            Map<String, Boolean> entitlements) {
    }

    public record PlanDto(Long id, String code, String name, String description, BigDecimal monthlyPrice,
                          BigDecimal annualPrice, String currency, String billingInterval, int maxHostels,
                          int maxBedsPerHostel, int maxManagers, int trialDays, int graceDays, boolean customPricing,
                          String status, int sortOrder, Map<String, Boolean> entitlements, long subscribers) {
    }

    public record PlanStatusRequest(@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE|ARCHIVED") String status) {
    }

    public record FeatureDto(String code, String label, String description) {
    }

    // ------------------------------------------------------------------ owner subscription input

    /** Used when an owner is created: everything except the plan is optional and defaults from the plan. */
    public record SubscriptionInput(
            @NotNull Long planId,
            @Min(0) @Max(365) Integer trialDays,
            LocalDate startsOn,
            LocalDate expiresOn,
            @Min(0) @Max(90) Integer graceDays,
            @Min(1) @Max(MAX_HOSTELS) Integer customMaxHostels,
            @Min(1) @Max(MAX_BEDS_PER_HOSTEL) Integer customMaxBedsPerHostel,
            @Min(0) @Max(MAX_MANAGERS) Integer customMaxManagers,
            @DecimalMin("0.00") @DecimalMax("10000000") BigDecimal customMonthlyPrice) {
    }

    /** Full replacement of an owner's subscription settings (what the edit form submits). */
    public record UpdateSubscriptionRequest(
            @NotNull Long planId,
            @NotBlank @Pattern(regexp = "TRIAL|ACTIVE|SUSPENDED|CANCELLED") String status,
            @NotNull LocalDate startsOn,
            LocalDate trialEndsOn,
            LocalDate periodEndsOn,
            @NotNull @Min(0) @Max(90) Integer graceDays,
            @Min(1) @Max(MAX_HOSTELS) Integer customMaxHostels,
            @Min(1) @Max(MAX_BEDS_PER_HOSTEL) Integer customMaxBedsPerHostel,
            @Min(0) @Max(MAX_MANAGERS) Integer customMaxManagers,
            @DecimalMin("0.00") @DecimalMax("10000000") BigDecimal customMonthlyPrice,
            @Size(max = 300) String reason) {
    }

    public record RecordPaymentRequest(
            @NotNull @DecimalMin("0.01") @DecimalMax("100000000") BigDecimal amount,
            @NotNull LocalDate paidOn,
            @NotBlank @Pattern(regexp = "CASH|UPI|BANK_TRANSFER|CARD|OTHER") String method,
            @Size(max = 100) String reference,
            @Size(max = 500) String notes,
            @NotNull @Min(0) @Max(36) Integer extendMonths) {
    }

    // ------------------------------------------------------------------ views

    public record Limits(int maxHostels, int maxBedsPerHostel, int maxManagers) {
    }

    public record HostelUsage(Long hostelId, String hostelName, int beds) {
    }

    public record Usage(int hostels, int managers, List<HostelUsage> hostelBeds) {
    }

    public record PaymentDto(Long id, BigDecimal amount, LocalDate paidOn, String method, String reference,
                             String notes, LocalDate extendedTo, Long recordedBy, Instant createdAt) {
    }

    public record EventDto(Long id, String eventType, String details, Long actorUserId, String actorName,
                           Instant createdAt) {
    }

    /** Everything the Super Admin sees about one owner's subscription. */
    public record OwnerSubscriptionDto(
            Long ownerId, PlanDto plan, String status, String accessState, LocalDate startsOn,
            LocalDate trialEndsOn, LocalDate periodEndsOn, LocalDate graceEndsOn, int graceDays, String billingStatus,
            Integer customMaxHostels, Integer customMaxBedsPerHostel, Integer customMaxManagers,
            BigDecimal customMonthlyPrice, BigDecimal effectiveMonthlyPrice, Limits planLimits, Limits effectiveLimits,
            boolean overridesPlan, Usage usage, List<String> overLimit, Map<String, Boolean> entitlements,
            List<EventDto> events, List<PaymentDto> payments) {
    }

    /** What an owner/manager sees: plan, dates, limits, usage and warnings. No billing detail. */
    public record SubscriptionSummary(
            String planCode, String planName, String status, String accessState, LocalDate trialEndsOn,
            LocalDate periodEndsOn, LocalDate graceEndsOn, Long daysRemaining, Limits limits, Usage usage,
            boolean canAddResources, int warningPercent, List<String> warnings, Map<String, Boolean> entitlements) {
    }
}
