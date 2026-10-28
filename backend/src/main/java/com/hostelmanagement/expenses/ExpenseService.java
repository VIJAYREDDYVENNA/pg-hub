package com.hostelmanagement.expenses;

import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.Money;
import com.hostelmanagement.common.PageRequests;
import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Expenses are editable (with audit) but never deleted, to keep the financial history intact. */
@Service
@RequiredArgsConstructor
public class ExpenseService {

    public static final String CATEGORIES = "ELECTRICITY|WATER|SALARY|MAINTENANCE|FOOD|INTERNET|RENT|SUPPLIES|TAXES|OTHER";

    private final ExpenseRepository repository;
    private final HostelAccessService hostelAccess;
    private final AuditService auditService;
    private final NamedParameterJdbcTemplate jdbc;
    private final AppClock clock;

    public record ExpenseRequest(@NotNull Long hostelId,
                                 @NotNull @Pattern(regexp = CATEGORIES) String category,
                                 @NotBlank @Size(max = 500) String description,
                                 @NotNull @DecimalMin("0.01") BigDecimal amount,
                                 @NotNull LocalDate expenseDate,
                                 @NotNull @Pattern(regexp = "CASH|UPI|BANK_TRANSFER|CARD|OTHER") String paymentMethod,
                                 @Size(max = 150) String vendor,
                                 @Size(max = 100) String reference,
                                 Long maintenanceRequestId) {
    }

    public record ExpenseDto(Long id, Long hostelId, String category, String description, BigDecimal amount,
                             LocalDate expenseDate, String paymentMethod, String vendor, String reference,
                             Long maintenanceRequestId, Instant createdAt) {
        static ExpenseDto from(Expense e) {
            return new ExpenseDto(e.getId(), e.getHostelId(), e.getCategory(), e.getDescription(), e.getAmount(),
                    e.getExpenseDate(), e.getPaymentMethod(), e.getVendor(), e.getReference(),
                    e.getMaintenanceRequestId(), e.getCreatedAt());
        }
    }

    public record CategoryTotal(String category, BigDecimal amount) {
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseDto> list(Long hostelId, String category, LocalDate from, LocalDate to, int page, int size) {
        var pageable = PageRequests.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "expenseDate", "id"));
        return PageResponse.of(repository.search(hostelAccess.scope(hostelId),
                category == null || category.isBlank() || "ALL".equals(category) ? null : category, from, to, pageable),
                ExpenseDto::from);
    }

    @Transactional(readOnly = true)
    public List<CategoryTotal> byCategory(Long hostelId, LocalDate from, LocalDate to) {
        MapSqlParameterSource p = new MapSqlParameterSource("ids", hostelAccess.scope(hostelId))
                .addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(to));
        return jdbc.query("""
                SELECT category, SUM(amount) total FROM expenses WHERE hostel_id IN (:ids)
                AND expense_date BETWEEN :from AND :to GROUP BY category ORDER BY total DESC""", p,
                (rs, n) -> new CategoryTotal(rs.getString("category"), Money.of(rs.getBigDecimal("total"))));
    }

    @Transactional
    public ExpenseDto create(ExpenseRequest req) {
        hostelAccess.checkHostel(req.hostelId());
        validateDate(req.expenseDate());
        Expense e = new Expense();
        e.setRecordedBy(CurrentUser.get().userId());
        apply(e, req);
        repository.save(e);
        auditService.log("EXPENSE_RECORDED", "EXPENSE", e.getId(), e.getCategory() + " " + e.getAmount());
        return ExpenseDto.from(e);
    }

    @Transactional
    public ExpenseDto update(Long id, ExpenseRequest req) {
        Expense e = repository.findById(id).orElseThrow(() -> ApiException.notFound("Expense"));
        hostelAccess.checkEntity(e.getHostelId(), "Expense");
        hostelAccess.checkHostel(req.hostelId());
        validateDate(req.expenseDate());
        String before = e.getCategory() + " " + e.getAmount() + " " + e.getExpenseDate();
        apply(e, req);
        auditService.log("EXPENSE_UPDATED", "EXPENSE", id, before + " -> " + e.getCategory() + " " + e.getAmount()
                + " " + e.getExpenseDate());
        return ExpenseDto.from(e);
    }

    private void validateDate(LocalDate d) {
        if (d.isAfter(clock.today())) {
            throw ApiException.badRequest("Expense date cannot be in the future");
        }
    }

    private void apply(Expense e, ExpenseRequest req) {
        e.setHostelId(req.hostelId());
        e.setCategory(req.category());
        e.setDescription(req.description().trim());
        e.setAmount(Money.of(req.amount()));
        e.setExpenseDate(req.expenseDate());
        e.setPaymentMethod(req.paymentMethod());
        e.setVendor(req.vendor());
        e.setReference(req.reference());
        if (req.maintenanceRequestId() != null) {
            // The linked request must exist in the same hostel as the expense
            Integer ok = jdbc.queryForObject("SELECT COUNT(*) FROM maintenance_requests WHERE id = :id AND hostel_id = :h",
                    new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("id", req.maintenanceRequestId())
                            .addValue("h", req.hostelId()), Integer.class);
            if (ok == null || ok == 0) {
                throw ApiException.badRequest("The linked maintenance request does not belong to this hostel");
            }
        }
        e.setMaintenanceRequestId(req.maintenanceRequestId());
    }
}
