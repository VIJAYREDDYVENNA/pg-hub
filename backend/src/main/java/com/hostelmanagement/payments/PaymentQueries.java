package com.hostelmanagement.payments;

import com.hostelmanagement.common.Money;
import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.payments.dto.FinanceDtos.AmountCount;
import com.hostelmanagement.payments.dto.FinanceDtos.FinanceSummary;
import com.hostelmanagement.payments.dto.FinanceDtos.HostelRevenue;
import com.hostelmanagement.payments.dto.FinanceDtos.InvoiceAllocationDto;
import com.hostelmanagement.payments.dto.FinanceDtos.InvoiceDto;
import com.hostelmanagement.payments.dto.FinanceDtos.InvoiceItemDto;
import com.hostelmanagement.payments.dto.FinanceDtos.MonthlyPoint;
import com.hostelmanagement.payments.dto.FinanceDtos.PaymentAllocationDto;
import com.hostelmanagement.payments.dto.FinanceDtos.PaymentDto;
import com.hostelmanagement.payments.dto.FinanceDtos.RefundDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read models and aggregates for billing. All queries are scoped by an authorized hostel ID list. */
@Repository
@RequiredArgsConstructor
public class PaymentQueries {

    /** Status as shown to users: open invoices past their due date are OVERDUE even before the nightly job. */
    static final String EFFECTIVE_STATUS = """
            CASE WHEN i.status IN ('PENDING','PARTIALLY_PAID','OVERDUE') AND i.due_date < :today THEN 'OVERDUE'
                 WHEN i.status = 'OVERDUE' THEN (CASE WHEN i.amount_paid > 0 THEN 'PARTIALLY_PAID' ELSE 'PENDING' END)
                 ELSE i.status END""";

    private static final String INVOICE_SELECT = """
            SELECT i.*, h.name hostel_name, res.full_name, res.mobile, %s eff_status,
                   (SELECT r.room_number FROM bed_allocations ba JOIN beds b ON b.id = ba.bed_id
                      JOIN rooms r ON r.id = b.room_id WHERE ba.admission_id = i.admission_id
                      ORDER BY ba.end_date IS NULL DESC, ba.start_date DESC LIMIT 1) room_number,
                   (SELECT b.bed_label FROM bed_allocations ba JOIN beds b ON b.id = ba.bed_id
                      WHERE ba.admission_id = i.admission_id
                      ORDER BY ba.end_date IS NULL DESC, ba.start_date DESC LIMIT 1) bed_label
            FROM invoices i JOIN hostels h ON h.id = i.hostel_id JOIN residents res ON res.id = i.resident_id
            """.formatted(EFFECTIVE_STATUS);

    private static final String PAYMENT_SELECT = """
            SELECT p.*, h.name hostel_name, res.full_name, res.mobile, u.full_name recorded_by_name,
                   (SELECT r.room_number FROM bed_allocations ba JOIN beds b ON b.id = ba.bed_id
                      JOIN rooms r ON r.id = b.room_id WHERE ba.admission_id = p.admission_id
                      ORDER BY ba.end_date IS NULL DESC, ba.start_date DESC LIMIT 1) room_number,
                   (SELECT b.bed_label FROM bed_allocations ba JOIN beds b ON b.id = ba.bed_id
                      WHERE ba.admission_id = p.admission_id
                      ORDER BY ba.end_date IS NULL DESC, ba.start_date DESC LIMIT 1) bed_label
            FROM payments p JOIN hostels h ON h.id = p.hostel_id JOIN residents res ON res.id = p.resident_id
            JOIN users u ON u.id = p.recorded_by
            """;

    private final NamedParameterJdbcTemplate jdbc;

    // ------------------------------------------------------------------ invoices

    /**
     * status filter: OPEN, DUE_TODAY, OVERDUE, PENDING, PARTIALLY_PAID, PAID, WAIVED, CANCELLED or null/ALL.
     */
    public PageResponse<InvoiceDto> searchInvoices(Collection<Long> hostelIds, Long residentId, String status,
                                                   String type, LocalDate dueFrom, LocalDate dueTo, String q,
                                                   LocalDate today, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE i.hostel_id IN (:ids)");
        MapSqlParameterSource p = new MapSqlParameterSource("ids", hostelIds).addValue("today", Date.valueOf(today));
        if (residentId != null) {
            where.append(" AND i.resident_id = :rid");
            p.addValue("rid", residentId);
        }
        if (type != null && !type.isBlank()) {
            where.append(" AND i.invoice_type = :type");
            p.addValue("type", type);
        }
        if (status != null && !status.isBlank() && !"ALL".equals(status)) {
            switch (status) {
                case "OPEN" -> where.append(" AND i.status IN ('PENDING','PARTIALLY_PAID','OVERDUE')");
                case "DUE_TODAY" -> where.append(" AND i.status IN ('PENDING','PARTIALLY_PAID','OVERDUE') AND i.due_date = :today");
                default -> {
                    where.append(" AND (").append(EFFECTIVE_STATUS).append(") = :status");
                    p.addValue("status", status);
                }
            }
        }
        if (dueFrom != null) {
            where.append(" AND i.due_date >= :dueFrom");
            p.addValue("dueFrom", Date.valueOf(dueFrom));
        }
        if (dueTo != null) {
            where.append(" AND i.due_date <= :dueTo");
            p.addValue("dueTo", Date.valueOf(dueTo));
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (res.full_name LIKE :q OR res.mobile LIKE :q OR i.invoice_number LIKE :q)");
            p.addValue("q", "%" + q.trim() + "%");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM invoices i JOIN residents res ON res.id = i.resident_id"
                + where, p, Long.class);
        int safeSize = Math.min(Math.max(size, 1), 100);
        p.addValue("limit", safeSize).addValue("offset", Math.max(page, 0) * safeSize);
        List<InvoiceDto> content = jdbc.query(INVOICE_SELECT + where
                        + " ORDER BY i.due_date ASC, i.id ASC LIMIT :limit OFFSET :offset", p,
                (rs, n) -> mapInvoice(rs, today, List.of(), List.of()));
        long t = total == null ? 0 : total;
        return new PageResponse<>(content, Math.max(page, 0), safeSize, t, (int) Math.ceil(t / (double) safeSize));
    }

    public InvoiceDto invoice(Long id, LocalDate today) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", id).addValue("today", Date.valueOf(today));
        List<InvoiceItemDto> items = jdbc.query("""
                SELECT id, item_type, description, amount FROM invoice_items WHERE invoice_id = :id ORDER BY id""", p,
                (rs, n) -> new InvoiceItemDto(rs.getLong("id"), rs.getString("item_type"), rs.getString("description"),
                        rs.getBigDecimal("amount")));
        List<InvoiceAllocationDto> pays = jdbc.query("""
                SELECT p.id, p.receipt_number, p.payment_date, p.method, pa.amount, p.status
                FROM payment_allocations pa JOIN payments p ON p.id = pa.payment_id
                WHERE pa.invoice_id = :id ORDER BY p.payment_date, p.id""", p,
                (rs, n) -> new InvoiceAllocationDto(rs.getLong("id"), rs.getString("receipt_number"),
                        date(rs, "payment_date"), rs.getString("method"), rs.getBigDecimal("amount"),
                        rs.getString("status")));
        List<InvoiceDto> list = jdbc.query(INVOICE_SELECT + " WHERE i.id = :id", p,
                (rs, n) -> mapInvoice(rs, today, items, pays));
        return list.isEmpty() ? null : list.get(0);
    }

    private InvoiceDto mapInvoice(ResultSet rs, LocalDate today, List<InvoiceItemDto> items,
                                  List<InvoiceAllocationDto> pays) throws SQLException {
        BigDecimal total = rs.getBigDecimal("total_amount");
        BigDecimal paid = rs.getBigDecimal("amount_paid");
        String status = rs.getString("eff_status");
        LocalDate due = date(rs, "due_date");
        Integer daysOverdue = "OVERDUE".equals(status) ? (int) ChronoUnit.DAYS.between(due, today) : null;
        long adm = rs.getLong("admission_id");
        boolean noAdm = rs.wasNull();
        BigDecimal balance = ("WAIVED".equals(status) || "CANCELLED".equals(status)) ? Money.ZERO : total.subtract(paid);
        return new InvoiceDto(rs.getLong("id"), rs.getString("invoice_number"), rs.getString("invoice_type"),
                rs.getLong("hostel_id"), rs.getString("hostel_name"), rs.getLong("resident_id"),
                rs.getString("full_name"), rs.getString("mobile"), noAdm ? null : adm, rs.getString("room_number"),
                rs.getString("bed_label"), date(rs, "period_start"), date(rs, "period_end"), date(rs, "issue_date"),
                due, rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("late_fee_amount"), total, paid, balance, status, daysOverdue,
                rs.getString("notes"), items, pays);
    }

    // ------------------------------------------------------------------ payments

    public PageResponse<PaymentDto> searchPayments(Collection<Long> hostelIds, Long residentId, String method,
                                                   LocalDate from, LocalDate to, String q, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE p.hostel_id IN (:ids)");
        MapSqlParameterSource p = new MapSqlParameterSource("ids", hostelIds);
        if (residentId != null) {
            where.append(" AND p.resident_id = :rid");
            p.addValue("rid", residentId);
        }
        if (method != null && !method.isBlank() && !"ALL".equals(method)) {
            where.append(" AND p.method = :method");
            p.addValue("method", method);
        }
        if (from != null) {
            where.append(" AND p.payment_date >= :from");
            p.addValue("from", Date.valueOf(from));
        }
        if (to != null) {
            where.append(" AND p.payment_date <= :to");
            p.addValue("to", Date.valueOf(to));
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (res.full_name LIKE :q OR res.mobile LIKE :q OR p.receipt_number LIKE :q OR p.transaction_reference LIKE :q)");
            p.addValue("q", "%" + q.trim() + "%");
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM payments p JOIN residents res ON res.id = p.resident_id"
                + where, p, Long.class);
        int safeSize = Math.min(Math.max(size, 1), 100);
        p.addValue("limit", safeSize).addValue("offset", Math.max(page, 0) * safeSize);
        List<PaymentDto> content = jdbc.query(PAYMENT_SELECT + where
                + " ORDER BY p.payment_date DESC, p.id DESC LIMIT :limit OFFSET :offset", p, (rs, n) -> mapPayment(rs, List.of()));
        long t = total == null ? 0 : total;
        return new PageResponse<>(content, Math.max(page, 0), safeSize, t, (int) Math.ceil(t / (double) safeSize));
    }

    public PaymentDto payment(Long id) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", id);
        List<PaymentAllocationDto> allocs = jdbc.query("""
                SELECT i.id, i.invoice_number, i.invoice_type, i.period_start, i.period_end, pa.amount
                FROM payment_allocations pa JOIN invoices i ON i.id = pa.invoice_id
                WHERE pa.payment_id = :id ORDER BY i.due_date, i.id""", p,
                (rs, n) -> new PaymentAllocationDto(rs.getLong("id"), rs.getString("invoice_number"),
                        rs.getString("invoice_type"), date(rs, "period_start"), date(rs, "period_end"),
                        rs.getBigDecimal("amount")));
        List<PaymentDto> list = jdbc.query(PAYMENT_SELECT + " WHERE p.id = :id", p, (rs, n) -> mapPayment(rs, allocs));
        return list.isEmpty() ? null : list.get(0);
    }

    private PaymentDto mapPayment(ResultSet rs, List<PaymentAllocationDto> allocs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        return new PaymentDto(rs.getLong("id"), rs.getString("receipt_number"), rs.getLong("hostel_id"),
                rs.getString("hostel_name"), rs.getLong("resident_id"), rs.getString("full_name"),
                rs.getString("mobile"), rs.getString("room_number"), rs.getString("bed_label"),
                rs.getBigDecimal("amount"), rs.getBigDecimal("unallocated_amount"), date(rs, "payment_date"),
                rs.getString("method"), rs.getString("transaction_reference"), rs.getString("status"),
                rs.getString("notes"), rs.getString("recorded_by_name"),
                created == null ? null : created.toInstant(), rs.getString("reversal_reason"), allocs);
    }

    public List<RefundDto> refunds(Collection<Long> hostelIds, Long residentId) {
        StringBuilder sql = new StringBuilder("""
                SELECT rf.*, res.full_name, u.full_name recorded_by_name FROM refunds rf
                JOIN residents res ON res.id = rf.resident_id JOIN users u ON u.id = rf.recorded_by
                WHERE rf.hostel_id IN (:ids)""");
        MapSqlParameterSource p = new MapSqlParameterSource("ids", hostelIds);
        if (residentId != null) {
            sql.append(" AND rf.resident_id = :rid");
            p.addValue("rid", residentId);
        }
        sql.append(" ORDER BY rf.refund_date DESC, rf.id DESC LIMIT 200");
        return jdbc.query(sql.toString(), p, (rs, n) -> new RefundDto(rs.getLong("id"), rs.getLong("hostel_id"),
                rs.getLong("resident_id"), rs.getString("full_name"), rs.getString("refund_type"),
                rs.getBigDecimal("amount"), date(rs, "refund_date"), rs.getString("method"),
                rs.getString("transaction_reference"), rs.getString("reason"), rs.getString("recorded_by_name"),
                rs.getTimestamp("created_at").toInstant()));
    }

    // ------------------------------------------------------------------ balances

    public BigDecimal residentCredit(Long residentId) {
        BigDecimal v = jdbc.queryForObject("""
                SELECT COALESCE(SUM(unallocated_amount), 0) FROM payments WHERE resident_id = :id AND status = 'RECEIVED'""",
                new MapSqlParameterSource("id", residentId), BigDecimal.class);
        return Money.of(v);
    }

    public BigDecimal depositHeld(Long residentId) {
        BigDecimal v = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount_received - amount_refunded - amount_adjusted), 0) FROM deposits
                WHERE resident_id = :id""", new MapSqlParameterSource("id", residentId), BigDecimal.class);
        return Money.of(v);
    }

    // ------------------------------------------------------------------ summary

    private static final String DEPOSIT_RECEIPTS_SQL = """
            SELECT COALESCE(SUM(pa.amount), 0) FROM payment_allocations pa
            JOIN payments p ON p.id = pa.payment_id JOIN invoices i ON i.id = pa.invoice_id
            WHERE p.hostel_id IN (:ids) AND p.status = 'RECEIVED' AND p.method <> 'DEPOSIT_ADJUSTMENT'
            AND i.invoice_type = 'DEPOSIT'""";

    private static final String REFUNDS_SQL = "SELECT COALESCE(SUM(amount), 0) FROM refunds WHERE hostel_id IN (:ids)";

    public FinanceSummary summary(Collection<Long> hostelIds, LocalDate today) {
        MapSqlParameterSource p = new MapSqlParameterSource("ids", hostelIds).addValue("today", Date.valueOf(today));
        YearMonth month = YearMonth.from(today);
        p.addValue("monthStart", Date.valueOf(month.atDay(1))).addValue("monthEnd", Date.valueOf(month.atEndOfMonth()));

        BigDecimal todayCollections = scalar("""
                SELECT COALESCE(SUM(amount), 0) FROM payments WHERE hostel_id IN (:ids) AND status = 'RECEIVED'
                AND method <> 'DEPOSIT_ADJUSTMENT' AND payment_date = :today""", p);
        AmountCount dueToday = amountCount("""
                SELECT COUNT(*) c, COALESCE(SUM(total_amount - amount_paid), 0) a FROM invoices
                WHERE hostel_id IN (:ids) AND status IN ('PENDING','PARTIALLY_PAID','OVERDUE') AND due_date = :today""", p);
        AmountCount overdue = amountCount("""
                SELECT COUNT(*) c, COALESCE(SUM(total_amount - amount_paid), 0) a FROM invoices
                WHERE hostel_id IN (:ids) AND status IN ('PENDING','PARTIALLY_PAID','OVERDUE') AND due_date < :today""", p);
        BigDecimal outstanding = scalar("""
                SELECT COALESCE(SUM(total_amount - amount_paid), 0) FROM invoices
                WHERE hostel_id IN (:ids) AND status IN ('PENDING','PARTIALLY_PAID','OVERDUE')""", p);
        BigDecimal monthCollections = scalar("""
                SELECT COALESCE(SUM(amount), 0) FROM payments WHERE hostel_id IN (:ids) AND status = 'RECEIVED'
                AND method <> 'DEPOSIT_ADJUSTMENT' AND payment_date BETWEEN :monthStart AND :monthEnd""", p);
        // Net revenue = Collections - deposit receipts - advance refunds, all for the same calendar month:
        //  * Collections (unchanged): RECEIVED payments except deposit adjustments, by payment date, gross.
        //  * Deposit receipts: the part of those payments allocated to DEPOSIT invoices. A deposit is a liability to the
        //    resident, not income, so it is removed.
        //  * Advance refunds: advance credit handed back (it was counted in Collections when it arrived).
        //  * Deposit refunds are shown but NOT deducted: the deposit was already excluded, so deducting its refund would
        //    subtract the same money twice.
        BigDecimal monthDepositReceipts = scalar(DEPOSIT_RECEIPTS_SQL + " AND p.payment_date BETWEEN :monthStart AND :monthEnd", p);
        BigDecimal monthAdvanceRefunds = scalar(REFUNDS_SQL + " AND refund_type = 'ADVANCE' AND refund_date BETWEEN :monthStart AND :monthEnd", p);
        BigDecimal monthDepositRefunds = scalar(REFUNDS_SQL + " AND refund_type = 'DEPOSIT' AND refund_date BETWEEN :monthStart AND :monthEnd", p);
        BigDecimal monthNetRevenue = monthCollections.subtract(monthDepositReceipts).subtract(monthAdvanceRefunds);
        BigDecimal monthInvoiced = scalar("""
                SELECT COALESCE(SUM(total_amount), 0) FROM invoices WHERE hostel_id IN (:ids)
                AND status NOT IN ('CANCELLED') AND issue_date BETWEEN :monthStart AND :monthEnd""", p);
        BigDecimal monthExpenses = scalar("""
                SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE hostel_id IN (:ids)
                AND expense_date BETWEEN :monthStart AND :monthEnd""", p);
        BigDecimal deposits = scalar("""
                SELECT COALESCE(SUM(amount_received - amount_refunded - amount_adjusted), 0) FROM deposits
                WHERE hostel_id IN (:ids)""", p);

        List<HostelRevenue> perHostel = jdbc.query("""
                SELECT h.id, h.name,
                  (SELECT COALESCE(SUM(p.amount), 0) FROM payments p WHERE p.hostel_id = h.id AND p.status = 'RECEIVED'
                     AND p.method <> 'DEPOSIT_ADJUSTMENT' AND p.payment_date BETWEEN :monthStart AND :monthEnd) collected,
                  (SELECT COALESCE(SUM(i.total_amount - i.amount_paid), 0) FROM invoices i WHERE i.hostel_id = h.id
                     AND i.status IN ('PENDING','PARTIALLY_PAID','OVERDUE')) outstanding
                FROM hostels h WHERE h.id IN (:ids) AND h.deleted_at IS NULL ORDER BY h.name""", p,
                (rs, n) -> new HostelRevenue(rs.getLong("id"), rs.getString("name"),
                        Money.of(rs.getBigDecimal("collected")), Money.of(rs.getBigDecimal("outstanding"))));

        return new FinanceSummary(today, todayCollections, dueToday, overdue, outstanding, monthCollections,
                monthInvoiced, monthExpenses, deposits, perHostel, trend(hostelIds, today, 6),
                monthDepositReceipts, monthAdvanceRefunds, monthDepositRefunds, monthNetRevenue);
    }

    /** Collected vs invoiced vs expenses for the last {@code months} calendar months (oldest first). */
    public List<MonthlyPoint> trend(Collection<Long> hostelIds, LocalDate today, int months) {
        YearMonth end = YearMonth.from(today);
        YearMonth start = end.minusMonths(months - 1L);
        MapSqlParameterSource p = new MapSqlParameterSource("ids", hostelIds)
                .addValue("from", Date.valueOf(start.atDay(1))).addValue("to", Date.valueOf(end.atEndOfMonth()));
        Map<String, BigDecimal[]> map = new LinkedHashMap<>();
        for (YearMonth m = start; !m.isAfter(end); m = m.plusMonths(1)) {
            map.put(m.toString(), new BigDecimal[]{Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO});
        }
        jdbc.query("""
                SELECT DATE_FORMAT(payment_date, '%Y-%m') m, SUM(amount) s FROM payments WHERE hostel_id IN (:ids)
                AND status = 'RECEIVED' AND method <> 'DEPOSIT_ADJUSTMENT' AND payment_date BETWEEN :from AND :to
                GROUP BY m""", p, rs -> {
            BigDecimal[] v = map.get(rs.getString("m"));
            if (v != null) {
                v[0] = Money.of(rs.getBigDecimal("s"));
            }
        });
        jdbc.query("""
                SELECT DATE_FORMAT(issue_date, '%Y-%m') m, SUM(total_amount) s FROM invoices WHERE hostel_id IN (:ids)
                AND status <> 'CANCELLED' AND issue_date BETWEEN :from AND :to GROUP BY m""", p, rs -> {
            BigDecimal[] v = map.get(rs.getString("m"));
            if (v != null) {
                v[1] = Money.of(rs.getBigDecimal("s"));
            }
        });
        jdbc.query("""
                SELECT DATE_FORMAT(expense_date, '%Y-%m') m, SUM(amount) s FROM expenses WHERE hostel_id IN (:ids)
                AND expense_date BETWEEN :from AND :to GROUP BY m""", p, rs -> {
            BigDecimal[] v = map.get(rs.getString("m"));
            if (v != null) {
                v[2] = Money.of(rs.getBigDecimal("s"));
            }
        });
        jdbc.query(DEPOSIT_RECEIPTS_SQL.replace("SELECT COALESCE(SUM(pa.amount), 0)", "SELECT DATE_FORMAT(p.payment_date, '%Y-%m') m, SUM(pa.amount) s")
                + " AND p.payment_date BETWEEN :from AND :to GROUP BY m", p, rs -> {
            BigDecimal[] v = map.get(rs.getString("m"));
            if (v != null) {
                v[3] = Money.of(rs.getBigDecimal("s"));
            }
        });
        jdbc.query("SELECT DATE_FORMAT(refund_date, '%Y-%m') m, SUM(amount) s FROM refunds WHERE hostel_id IN (:ids) "
                + "AND refund_type = 'ADVANCE' AND refund_date BETWEEN :from AND :to GROUP BY m", p, rs -> {
            BigDecimal[] v = map.get(rs.getString("m"));
            if (v != null) {
                v[4] = Money.of(rs.getBigDecimal("s"));
            }
        });
        List<MonthlyPoint> points = new ArrayList<>();
        map.forEach((m, v) -> points.add(new MonthlyPoint(m, v[0], v[1], v[2], v[0].subtract(v[3]).subtract(v[4]))));
        return points;
    }

    private BigDecimal scalar(String sql, MapSqlParameterSource p) {
        return Money.of(jdbc.queryForObject(sql, p, BigDecimal.class));
    }

    private AmountCount amountCount(String sql, MapSqlParameterSource p) {
        return jdbc.queryForObject(sql, p, (rs, n) -> new AmountCount(rs.getLong("c"), Money.of(rs.getBigDecimal("a"))));
    }

    private static LocalDate date(ResultSet rs, String col) throws SQLException {
        Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }
}
