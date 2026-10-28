package com.hostelmanagement.payments;

import com.hostelmanagement.admission.Admission;
import com.hostelmanagement.admission.AdmissionRepository;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.Money;
import com.hostelmanagement.hostel.HostelRepository;
import com.hostelmanagement.owner.OwnerSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** Nightly billing: generate rent for started periods, mark overdue invoices and apply configured late fees. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingScheduler {

    private final AdmissionRepository admissionRepository;
    private final HostelRepository hostelRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final OwnerSettingsService settingsService;
    private final AppClock clock;

    @Scheduled(cron = "0 15 1 * * *", zone = "${app.time-zone:Asia/Kolkata}")
    @Transactional
    public void nightly() {
        runBilling();
    }

    @Transactional
    public void runBilling() {
        LocalDate today = clock.today();
        Map<Long, Long> ownerByHostel = new HashMap<>();
        int created = 0;
        for (Admission a : admissionRepository.findByStatus("ACTIVE")) {
            Long ownerId = ownerByHostel.computeIfAbsent(a.getHostelId(),
                    id -> hostelRepository.findById(id).map(h -> h.getOwnerId()).orElse(null));
            if (ownerId != null) {
                created += invoiceService.generateRentInvoices(a, ownerId, today, null).size();
            }
        }
        int overdue = 0;
        for (Invoice inv : invoiceRepository.findNewlyOverdue(today)) {
            var settings = settingsService.forOwner(inv.getOwnerId());
            boolean hasLateFee = inv.getItems().stream().anyMatch(i -> "LATE_FEE".equals(i.getItemType()));
            if (Money.isPositive(settings.getLateFeeAmount()) && !hasLateFee && "RENT".equals(inv.getInvoiceType())) {
                inv.addItem("LATE_FEE", "Late payment fee", Money.of(settings.getLateFeeAmount()));
                InvoiceService.recalculate(inv);
            }
            InvoiceService.refreshStatus(inv, today);
            overdue++;
        }
        log.info("Nightly billing: {} rent invoice(s) created, {} invoice(s) marked overdue", created, overdue);
    }
}
