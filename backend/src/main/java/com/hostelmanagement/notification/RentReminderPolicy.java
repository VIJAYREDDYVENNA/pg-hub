package com.hostelmanagement.notification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * When is an automatic rent reminder due? Pure date logic, no I/O, so every rule is unit-tested.
 *
 * <p>Stages for an invoice due on D: {@code BEFORE} at D - reminderDaysBefore (skipped when that setting is 0),
 * {@code DUE} on D, then {@code OVERDUE_1..n} on D+1, D+1+interval, D+1+2*interval ... (at most {@code overdueMax}).
 *
 * <p>The job asks for the <em>latest</em> stage whose date has arrived, and only if that date is recent
 * ({@value #CATCH_UP_DAYS} days). So a missed run (server down on the due date) still produces one reminder, but never a
 * burst of every stage that was missed, and old overdue invoices are not messaged when a feature is first switched on.
 */
public final class RentReminderPolicy {

    /** How late a stage may still be sent after its scheduled day. */
    public static final int CATCH_UP_DAYS = 2;

    public record Stage(String key, LocalDate scheduledFor) {
        public boolean isOverdue() {
            return key.startsWith("OVERDUE");
        }
    }

    private RentReminderPolicy() {
    }

    public static List<Stage> stages(LocalDate dueDate, int daysBefore, int overdueIntervalDays, int overdueMax) {
        List<Stage> stages = new ArrayList<>();
        if (daysBefore > 0) {
            stages.add(new Stage("BEFORE", dueDate.minusDays(daysBefore)));
        }
        stages.add(new Stage("DUE", dueDate));
        int interval = Math.max(1, overdueIntervalDays);
        for (int k = 1; k <= overdueMax; k++) {
            stages.add(new Stage("OVERDUE_" + k, dueDate.plusDays(1L + (long) (k - 1) * interval)));
        }
        return stages;
    }

    public static Optional<Stage> currentStage(LocalDate dueDate, LocalDate today, int daysBefore, int overdueIntervalDays,
                                               int overdueMax) {
        Stage latest = null;
        for (Stage s : stages(dueDate, daysBefore, overdueIntervalDays, overdueMax)) {
            if (!s.scheduledFor().isAfter(today)) {
                latest = s;
            }
        }
        if (latest == null || today.isAfter(latest.scheduledFor().plusDays(CATCH_UP_DAYS))) {
            return Optional.empty();
        }
        return Optional.of(latest);
    }

    /** The earliest date any stage can fall on relative to the due date, for narrowing the candidate query. */
    public static int maxDaysBefore() {
        return 30;
    }

    public static int maxDaysAfter(int overdueIntervalDays, int overdueMax) {
        return 1 + Math.max(1, overdueIntervalDays) * Math.max(0, overdueMax - 1) + CATCH_UP_DAYS;
    }
}
