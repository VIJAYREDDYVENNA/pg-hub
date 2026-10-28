package com.hostelmanagement.notification;

import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.owner.OwnerSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Daily in-app reminders for staff: payments due / overdue, upcoming checkouts and enquiry follow-ups.
 * WhatsApp messages to residents are never sent automatically; staff trigger them explicitly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM");

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final OwnerSettingsService settingsService;
    private final AppClock clock;

    @Scheduled(cron = "0 0 8 * * *", zone = "${app.time-zone:Asia/Kolkata}")
    @Transactional
    public void daily() {
        runReminders();
    }

    @Transactional
    public int runReminders() {
        LocalDate today = clock.today();
        int[] count = {0};
        jdbc.query("""
                SELECT i.id, i.owner_id, i.hostel_id, i.invoice_number, i.total_amount - i.amount_paid bal, i.due_date,
                       res.full_name, res.id rid
                FROM invoices i JOIN residents res ON res.id = i.resident_id
                WHERE i.status IN ('PENDING','PARTIALLY_PAID','OVERDUE') AND i.due_date <= ?""", rs -> {
            LocalDate due = rs.getDate("due_date").toLocalDate();
            boolean overdue = due.isBefore(today);
            BigDecimal bal = rs.getBigDecimal("bal");
            String title = overdue ? "Overdue payment" : "Payment due today";
            String msg = rs.getString("full_name") + " - Rs. " + bal.toPlainString() + " (" + rs.getString("invoice_number")
                    + (overdue ? ", due " + DATE.format(due) : "") + ")";
            // Overdue reminders repeat weekly; due-today reminders once.
            String key = overdue ? "overdue-" + rs.getLong("id") + "-w" + (today.toEpochDay() / 7) : "due-" + rs.getLong("id");
            count[0] += notifications.notifyHostel(rs.getLong("owner_id"), rs.getLong("hostel_id"),
                    overdue ? NotificationTypes.PAYMENT_OVERDUE : NotificationTypes.PAYMENT_DUE, title, msg,
                    "/residents/" + rs.getLong("rid"), key);
        }, Date.valueOf(today));

        jdbc.query("""
                SELECT a.id, h.owner_id, a.hostel_id, a.expected_checkout_date, a.checkout_confirmed, res.full_name, res.id rid
                FROM admissions a JOIN hostels h ON h.id = a.hostel_id JOIN residents res ON res.id = a.resident_id
                WHERE a.status = 'ACTIVE' AND a.expected_checkout_date IS NOT NULL
                AND a.expected_checkout_date <= ?""", rs -> {
            long ownerId = rs.getLong("owner_id");
            int days = settingsService.forOwner(ownerId).getVacancyWarningDays();
            LocalDate date = rs.getDate("expected_checkout_date").toLocalDate();
            if (date.isAfter(today.plusDays(days))) {
                return;
            }
            String when = date.isBefore(today) ? "was expected on " + DATE.format(date) : "on " + DATE.format(date);
            count[0] += notifications.notifyHostel(ownerId, rs.getLong("hostel_id"), NotificationTypes.UPCOMING_CHECKOUT,
                    "Upcoming checkout", rs.getString("full_name") + " checkout " + when
                            + (rs.getBoolean("checkout_confirmed") ? " (confirmed)" : " (estimated)"),
                    "/residents/" + rs.getLong("rid"), "checkout-soon-" + rs.getLong("id") + "-" + date);
        }, Date.valueOf(today.plusDays(90)));

        jdbc.query("""
                SELECT id, owner_id, hostel_id, full_name, next_follow_up_date FROM enquiries
                WHERE status IN ('NEW','CONTACTED','FOLLOW_UP') AND next_follow_up_date <= ?""", rs -> {
            long hostelId = rs.getLong("hostel_id");
            Long hid = rs.wasNull() ? null : hostelId;
            count[0] += notifications.notifyHostel(rs.getLong("owner_id"), hid, NotificationTypes.ENQUIRY_FOLLOW_UP,
                    "Enquiry follow-up", "Follow up with " + rs.getString("full_name"),
                    "/enquiries?id=" + rs.getLong("id"), "followup-" + rs.getLong("id") + "-" + rs.getDate("next_follow_up_date"));
        }, Date.valueOf(today));
        log.info("Daily reminders: {} notification(s) created", count[0]);
        return count[0];
    }
}
