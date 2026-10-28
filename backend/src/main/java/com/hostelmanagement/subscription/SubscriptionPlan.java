package com.hostelmanagement.subscription;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** A purchasable tier. All numbers are data edited by the Super Admin; nothing about a plan is hardcoded. */
@Getter
@Setter
@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan extends BaseEntity {

    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";
    public static final String ARCHIVED = "ARCHIVED";

    @Column(nullable = false, length = 30, updatable = false)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "monthly_price", precision = 12, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(name = "annual_price", precision = 12, scale = 2)
    private BigDecimal annualPrice;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "billing_interval", nullable = false, length = 10)
    private String billingInterval = "MONTHLY";

    @Column(name = "max_hostels", nullable = false)
    private int maxHostels;

    @Column(name = "max_beds_per_hostel", nullable = false)
    private int maxBedsPerHostel;

    @Column(name = "max_managers", nullable = false)
    private int maxManagers;

    @Column(name = "trial_days", nullable = false)
    private int trialDays = 14;

    @Column(name = "grace_days", nullable = false)
    private int graceDays = 7;

    @Column(name = "custom_pricing", nullable = false)
    private boolean customPricing;

    @Column(nullable = false, length = 10)
    private String status = ACTIVE;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Version
    private Long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_entitlements", joinColumns = @JoinColumn(name = "plan_id"))
    @MapKeyColumn(name = "feature_code", length = 40)
    @Column(name = "enabled", nullable = false)
    private Map<String, Boolean> entitlements = new HashMap<>();

    public boolean allows(String feature) {
        return Boolean.TRUE.equals(entitlements.get(feature));
    }
}
