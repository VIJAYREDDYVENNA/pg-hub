package com.hostelmanagement.owner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Business settings for an owner account (vacancy warning window, payment grace days, late fee). */
@Getter
@Setter
@Entity
@Table(name = "owner_settings")
public class OwnerSettings {

    @Id
    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "vacancy_warning_days", nullable = false)
    private int vacancyWarningDays = 7;

    @Column(name = "payment_grace_days", nullable = false)
    private int paymentGraceDays = 5;

    @Column(name = "late_fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal lateFeeAmount = BigDecimal.ZERO;

    @Column(name = "reminder_days_before", nullable = false)
    private int reminderDaysBefore = 2;

    /** When true the daily job sends WhatsApp rent reminders to residents who consented. Off until the owner turns it on. */
    @Column(name = "auto_reminders_enabled", nullable = false)
    private boolean autoRemindersEnabled = false;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public static OwnerSettings defaults(Long ownerId) {
        OwnerSettings s = new OwnerSettings();
        s.setOwnerId(ownerId);
        return s;
    }
}
