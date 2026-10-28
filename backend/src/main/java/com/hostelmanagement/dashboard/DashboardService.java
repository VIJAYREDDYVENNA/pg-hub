package com.hostelmanagement.dashboard;

import com.hostelmanagement.admission.AdmissionQueries;
import com.hostelmanagement.admission.dto.AdmissionDtos.AdmissionDto;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.hostel.dto.HostelDtos.OccupancyStats;
import com.hostelmanagement.maintenance.MaintenanceService;
import com.hostelmanagement.maintenance.MaintenanceService.MaintenanceDto;
import com.hostelmanagement.occupancy.OccupancyQueries;
import com.hostelmanagement.owner.OwnerSettingsService;
import com.hostelmanagement.payments.PaymentQueries;
import com.hostelmanagement.payments.dto.FinanceDtos.AmountCount;
import com.hostelmanagement.payments.dto.FinanceDtos.FinanceSummary;
import com.hostelmanagement.payments.dto.FinanceDtos.InvoiceDto;
import com.hostelmanagement.payments.dto.FinanceDtos.PaymentDto;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import com.hostelmanagement.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final HostelAccessService hostelAccess;
    private final OccupancyQueries occupancyQueries;
    private final VacancyForecastService vacancyService;
    private final PaymentQueries paymentQueries;
    private final AdmissionQueries admissionQueries;
    private final MaintenanceService maintenanceService;
    private final OwnerSettingsService settingsService;
    private final AppClock clock;

    public record DashboardSummary(LocalDate today, int vacancyWarningDays, long totalHostels, long totalRooms,
                                   long totalBeds, long occupiedBeds, long vacantBeds, long reservedBeds,
                                   long maintenanceBeds, BigDecimal occupancyPercent, long vacating7Days,
                                   long vacating15Days, long vacating30Days, long overdueCheckouts,
                                   /* Financial fields are null when the user lacks PAYMENT_VIEW. */
                                   BigDecimal todayCollections, AmountCount dueToday, AmountCount overdue,
                                   BigDecimal totalOutstanding, BigDecimal monthCollections) {
    }

    public record Activity(List<PaymentDto> recentPayments, List<AdmissionDto> recentAdmissions,
                           List<AdmissionDto> recentCheckouts, List<InvoiceDto> dueToday, List<InvoiceDto> overdue,
                           List<MaintenanceDto> openMaintenance) {
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary(Long hostelId) {
        AuthUser user = CurrentUser.get();
        List<Long> scope = hostelAccess.scope(hostelId);
        long hostels = scope.contains(-1L) ? 0 : scope.size();
        OccupancyStats t = VacancyForecastService.total(occupancyQueries.statsByHostel(scope));
        LocalDate today = clock.today();
        FinanceSummary f = user.has(Permissions.PAYMENT_VIEW) ? paymentQueries.summary(scope, today) : null;
        return new DashboardSummary(today, settingsService.forOwner(user.ownerId()).getVacancyWarningDays(), hostels,
                t.totalRooms(), t.totalBeds(), t.occupiedBeds(), t.vacantBeds(), t.reservedBeds(), t.maintenanceBeds(),
                t.occupancyPercent(), vacancyService.vacatingWithin(scope, 7), vacancyService.vacatingWithin(scope, 15),
                vacancyService.vacatingWithin(scope, 30), vacancyService.overdueCheckouts(scope),
                f == null ? null : f.todayCollections(), f == null ? null : f.dueToday(), f == null ? null : f.overdue(),
                f == null ? null : f.totalOutstanding(), f == null ? null : f.monthCollections());
    }

    @Transactional(readOnly = true)
    public Activity activity(Long hostelId) {
        AuthUser user = CurrentUser.get();
        List<Long> scope = hostelAccess.scope(hostelId);
        LocalDate today = clock.today();
        boolean finance = user.has(Permissions.PAYMENT_VIEW);
        boolean residents = user.has(Permissions.RESIDENT_VIEW);
        boolean maintenance = user.has(Permissions.MAINTENANCE_MANAGE);
        return new Activity(
                finance ? paymentQueries.searchPayments(scope, null, null, null, null, null, 0, 6).content() : List.of(),
                residents ? admissionQueries.search(scope, "ADMITTED", today.minusDays(60), null, 0, 6).content() : List.of(),
                residents ? admissionQueries.search(scope, "CHECKED_OUT", today.minusDays(60), null, 0, 6).content() : List.of(),
                finance ? paymentQueries.searchInvoices(scope, null, "DUE_TODAY", null, null, null, null, today, 0, 8).content() : List.of(),
                finance ? paymentQueries.searchInvoices(scope, null, "OVERDUE", null, null, null, null, today, 0, 8).content() : List.of(),
                maintenance ? maintenanceService.list(hostelId, "ACTIVE", null, null, 0, 6).content() : List.of());
    }
}
