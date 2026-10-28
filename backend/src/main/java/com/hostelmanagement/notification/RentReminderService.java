package com.hostelmanagement.notification;

import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.scheduling.DbJobLock;
import com.hostelmanagement.config.AppProperties;
import com.hostelmanagement.notification.RentReminderPolicy.Stage;
import com.hostelmanagement.notification.whatsapp.WhatsAppMessage;
import com.hostelmanagement.notification.whatsapp.WhatsAppService;
import com.hostelmanagement.subscription.EntitlementService;
import com.hostelmanagement.subscription.Features;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Daily automatic WhatsApp rent reminders.
 *
 * <ul>
 *   <li>Only owners who switched "automatic reminders" on, with the WhatsApp feature in their plan.</li>
 *   <li>Only residents who agreed to WhatsApp messages and are currently ACTIVE.</li>
 *   <li>Only invoices that still have a balance. The invoice is locked and re-checked immediately before sending, so a
 *       payment made a second earlier stops the reminder; a part payment makes it ask for the remaining balance only.</li>
 *   <li>Timing from the owner's {@code reminder_days_before} (see {@link RentReminderPolicy}).</li>
 *   <li>At most one message per invoice and stage, enforced by a unique key in {@code rent_reminders}; two instances or a
 *       re-run cannot send twice. A lock also stops overlapping runs.</li>
 *   <li>Messages go through the configured WhatsApp provider, which is the simulator until real credentials are set.</li>
 * </ul>
 */
@Slf4j
@Component
public class RentReminderService {

    static final String LOCK_NAME = "rent-reminders";

    public record Result(int considered, int sent, int skipped, int failed) {
    }

    private record Candidate(long invoiceId, long ownerId, LocalDate dueDate, int daysBefore) {
    }

    private final JdbcTemplate jdbc;
    private final AppProperties props;
    private final WhatsAppService whatsApp;
    private final EntitlementService entitlements;
    private final DbJobLock jobLock;
    private final AppClock clock;
    private final TransactionTemplate tx;

    public RentReminderService(JdbcTemplate jdbc, AppProperties props, WhatsAppService whatsApp, EntitlementService entitlements,
                               DbJobLock jobLock, AppClock clock, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.props = props;
        this.whatsApp = whatsApp;
        this.entitlements = entitlements;
        this.jobLock = jobLock;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Scheduled entry point. Never throws; a failed run is logged and the next day's run starts afresh. */
    @Scheduled(cron = "${app.reminders.cron:0 0 9 * * *}", zone = "${app.time-zone:Asia/Kolkata}")
    public void scheduledRun() {
        if (!props.reminders().enabled()) {
            return;
        }
        long started = System.nanoTime();
        try {
            var result = jobLock.runExclusive(LOCK_NAME, () -> run(clock.today()));
            result.ifPresent(r -> log.info("Rent reminders: {} candidate invoice(s), {} sent, {} not due or already sent, {} failed in {} ms",
                    r.considered(), r.sent(), r.skipped(), r.failed(), (System.nanoTime() - started) / 1_000_000));
        } catch (Exception e) {
            log.error("Rent reminder run failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /** Runs the reminders as if today were {@code today}. Safe to call repeatedly. */
    public Result run(LocalDate today) {
        int overdueInterval = props.reminders().overdueIntervalDays();
        int overdueMax = props.reminders().overdueMax();
        LocalDate from = today.minusDays(RentReminderPolicy.maxDaysAfter(overdueInterval, overdueMax));
        LocalDate to = today.plusDays(RentReminderPolicy.maxDaysBefore());
        List<Candidate> candidates = jdbc.query("""
                SELECT i.id, i.owner_id, i.due_date, s.reminder_days_before
                FROM invoices i
                JOIN residents r ON r.id = i.resident_id
                JOIN owner_settings s ON s.owner_id = i.owner_id
                WHERE s.auto_reminders_enabled = TRUE
                  AND i.status IN ('PENDING', 'PARTIALLY_PAID', 'OVERDUE') AND i.total_amount > i.amount_paid
                  AND r.whatsapp_opt_in = TRUE AND r.status = 'ACTIVE'
                  AND i.due_date BETWEEN ? AND ?
                ORDER BY i.due_date, i.id""",
                (rs, n) -> new Candidate(rs.getLong("id"), rs.getLong("owner_id"), rs.getDate("due_date").toLocalDate(),
                        rs.getInt("reminder_days_before")),
                Date.valueOf(from), Date.valueOf(to));

        Map<Long, Boolean> entitled = new HashMap<>();
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        for (Candidate c : candidates) {
            Optional<Stage> stage = RentReminderPolicy.currentStage(c.dueDate(), today, c.daysBefore(), overdueInterval, overdueMax);
            boolean allowed = entitled.computeIfAbsent(c.ownerId(), id -> entitlements.isEnabled(id, Features.WHATSAPP_MESSAGING));
            if (stage.isEmpty() || !allowed) {
                skipped++;
                continue;
            }
            try {
                Boolean done = tx.execute(status -> remind(c, stage.get()));
                if (Boolean.TRUE.equals(done)) {
                    sent++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                failed++;
                // Invoice id only: no names, phone numbers or message text in the log
                log.warn("Could not send the automatic reminder for invoice {}: {}", c.invoiceId(), e.getMessage());
            }
        }
        return new Result(candidates.size(), sent, skipped, failed);
    }

    /** @return true if a message was created; false if the invoice is settled or this stage was already sent. */
    private boolean remind(Candidate c, Stage stage) {
        // Lock first: a payment being recorded right now either finishes before this read or waits for us
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT status, total_amount - amount_paid AS balance FROM invoices WHERE id = ? FOR UPDATE""", c.invoiceId());
        if (rows.isEmpty()) {
            return false;
        }
        String status = (String) rows.get(0).get("status");
        java.math.BigDecimal balance = (java.math.BigDecimal) rows.get(0).get("balance");
        if (!List.of("PENDING", "PARTIALLY_PAID", "OVERDUE").contains(status) || balance.signum() <= 0) {
            return false;
        }
        int inserted = jdbc.update("""
                INSERT IGNORE INTO rent_reminders (owner_id, invoice_id, stage, scheduled_for, created_at) VALUES (?, ?, ?, ?, ?)""",
                c.ownerId(), c.invoiceId(), stage.key(), Date.valueOf(stage.scheduledFor()), Timestamp.from(clock.now()));
        if (inserted == 0) {
            return false;
        }
        WhatsAppMessage message = whatsApp.sendAutomaticInvoiceReminder(c.invoiceId(), stage.isOverdue());
        jdbc.update("UPDATE rent_reminders SET whatsapp_message_id = ? WHERE invoice_id = ? AND stage = ?",
                message.getId(), c.invoiceId(), stage.key());
        return true;
    }
}
