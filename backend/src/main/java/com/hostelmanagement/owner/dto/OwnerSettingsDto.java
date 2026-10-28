package com.hostelmanagement.owner.dto;

import com.hostelmanagement.owner.OwnerSettings;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OwnerSettingsDto(
        @Min(1) @Max(90) int vacancyWarningDays,
        @Min(0) @Max(60) int paymentGraceDays,
        @NotNull @DecimalMin("0.00") BigDecimal lateFeeAmount,
        @Min(0) @Max(30) int reminderDaysBefore,
        boolean autoRemindersEnabled,
        String currencyCode) {

    public static OwnerSettingsDto from(OwnerSettings s) {
        return new OwnerSettingsDto(s.getVacancyWarningDays(), s.getPaymentGraceDays(), s.getLateFeeAmount(),
                s.getReminderDaysBefore(), s.isAutoRemindersEnabled(), s.getCurrencyCode());
    }
}
