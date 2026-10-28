package com.hostelmanagement.subscription;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An owner's subscription: which plan, for how long, and any owner-specific limit overrides. Subscription status,
 * billing status and resource usage are deliberately separate things; "expired" and "in grace" are derived from the
 * dates by {@link #accessState(LocalDate)} and never stored.
 */
@Getter
@Setter
@Entity
@Table(name = "owner_subscriptions")
public class OwnerSubscription extends BaseEntity {

    public static final String TRIAL = "TRIAL";
    public static final String ACTIVE = "ACTIVE";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String CANCELLED = "CANCELLED";

    public static final String BILLING_NOT_REQUIRED = "NOT_REQUIRED";
    public static final String BILLING_UNPAID = "UNPAID";
    public static final String BILLING_PAID = "PAID";

    /** What the owner may currently do. Only RESTRICTED blocks creating new billable resources. */
    public enum AccessState { TRIAL, ACTIVE, GRACE, RESTRICTED }

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "trial_ends_on")
    private LocalDate trialEndsOn;

    @Column(name = "period_ends_on")
    private LocalDate periodEndsOn;

    @Column(name = "grace_days", nullable = false)
    private int graceDays;

    @Column(name = "custom_max_hostels")
    private Integer customMaxHostels;

    @Column(name = "custom_max_beds_per_hostel")
    private Integer customMaxBedsPerHostel;

    @Column(name = "custom_max_managers")
    private Integer customMaxManagers;

    @Column(name = "custom_monthly_price", precision = 12, scale = 2)
    private BigDecimal customMonthlyPrice;

    @Column(name = "billing_status", nullable = false, length = 14)
    private String billingStatus;

    @Version
    private Long version;

    /** The date after which the subscription is past due (end of trial or of the paid period); null = open-ended. */
    public LocalDate endsOn() {
        return TRIAL.equals(status) ? trialEndsOn : periodEndsOn;
    }

    public LocalDate graceEndsOn() {
        LocalDate end = endsOn();
        return end == null ? null : end.plusDays(graceDays);
    }

    public AccessState accessState(LocalDate today) {
        if (SUSPENDED.equals(status) || CANCELLED.equals(status)) {
            return AccessState.RESTRICTED;
        }
        LocalDate end = endsOn();
        AccessState current = TRIAL.equals(status) ? AccessState.TRIAL : AccessState.ACTIVE;
        if (end == null || !today.isAfter(end)) {
            return current;
        }
        return today.isAfter(end.plusDays(graceDays)) ? AccessState.RESTRICTED : AccessState.GRACE;
    }
}
