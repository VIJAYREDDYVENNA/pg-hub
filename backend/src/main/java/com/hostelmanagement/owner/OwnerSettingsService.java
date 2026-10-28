package com.hostelmanagement.owner;

import com.hostelmanagement.common.Money;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.owner.dto.OwnerSettingsDto;
import com.hostelmanagement.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OwnerSettingsService {

    private final OwnerSettingsRepository repository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public OwnerSettings forOwner(Long ownerId) {
        return repository.findById(ownerId).orElseGet(() -> OwnerSettings.defaults(ownerId));
    }

    @Transactional(readOnly = true)
    public OwnerSettingsDto current() {
        return OwnerSettingsDto.from(forOwner(CurrentUser.get().ownerId()));
    }

    @Transactional
    public OwnerSettingsDto update(OwnerSettingsDto dto) {
        Long ownerId = CurrentUser.get().ownerId();
        OwnerSettings s = repository.findById(ownerId).orElseGet(() -> OwnerSettings.defaults(ownerId));
        s.setVacancyWarningDays(dto.vacancyWarningDays());
        s.setPaymentGraceDays(dto.paymentGraceDays());
        s.setLateFeeAmount(Money.of(dto.lateFeeAmount()));
        s.setReminderDaysBefore(dto.reminderDaysBefore());
        s.setAutoRemindersEnabled(dto.autoRemindersEnabled());
        repository.save(s);
        auditService.log("SETTINGS_UPDATED", "OWNER_SETTINGS", ownerId, dto.toString());
        return OwnerSettingsDto.from(s);
    }
}
