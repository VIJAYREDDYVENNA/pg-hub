package com.hostelmanagement.notification;

import com.hostelmanagement.notification.RentReminderPolicy.Stage;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RentReminderPolicyTest {

    private static final LocalDate DUE = LocalDate.of(2026, 11, 10);

    private static Optional<Stage> on(LocalDate today, int daysBefore) {
        return RentReminderPolicy.currentStage(DUE, today, daysBefore, 3, 4);
    }

    private static String key(LocalDate today, int daysBefore) {
        return on(today, daysBefore).map(Stage::key).orElse("-");
    }

    @Test
    void stagesAreBeforeThenDueThenRepeatingOverdue() {
        List<Stage> stages = RentReminderPolicy.stages(DUE, 2, 3, 4);

        assertEquals(List.of("BEFORE", "DUE", "OVERDUE_1", "OVERDUE_2", "OVERDUE_3", "OVERDUE_4"), stages.stream().map(Stage::key).toList());
        assertEquals(DUE.minusDays(2), stages.get(0).scheduledFor());
        assertEquals(DUE, stages.get(1).scheduledFor());
        assertEquals(DUE.plusDays(1), stages.get(2).scheduledFor());
        assertEquals(DUE.plusDays(4), stages.get(3).scheduledFor());
        assertEquals(DUE.plusDays(7), stages.get(4).scheduledFor());
        assertEquals(DUE.plusDays(10), stages.get(5).scheduledFor());
    }

    @Test
    void daysBeforeSettingDrivesTheFirstReminder() {
        assertEquals("-", key(DUE.minusDays(3), 2), "too early");
        assertEquals("BEFORE", key(DUE.minusDays(2), 2));
        assertEquals("BEFORE", key(DUE.minusDays(1), 2), "still the BEFORE stage until the due date");
        assertEquals("BEFORE", key(DUE.minusDays(5), 5));
        assertEquals("BEFORE", key(DUE.minusDays(30), 30));
    }

    @Test
    void zeroDaysBeforeMeansNoAdvanceReminder() {
        assertEquals("-", key(DUE.minusDays(1), 0));
        assertEquals("DUE", key(DUE, 0));
        assertTrue(RentReminderPolicy.stages(DUE, 0, 3, 4).stream().noneMatch(s -> s.key().equals("BEFORE")));
    }

    @Test
    void theDueDateAndTheOverdueSequence() {
        assertEquals("DUE", key(DUE, 2));
        assertEquals("OVERDUE_1", key(DUE.plusDays(1), 2));
        assertEquals("OVERDUE_1", key(DUE.plusDays(2), 2), "inside the catch-up window");
        assertEquals("OVERDUE_1", key(DUE.plusDays(3), 2), "the last catch-up day for OVERDUE_1");
        assertEquals("OVERDUE_2", key(DUE.plusDays(4), 2));
        assertEquals("OVERDUE_3", key(DUE.plusDays(7), 2));
        assertEquals("OVERDUE_4", key(DUE.plusDays(10), 2));
    }

    @Test
    void afterTheLastReminderNothingMoreIsSent_andAncientInvoicesAreLeftAlone() {
        assertEquals("OVERDUE_4", key(DUE.plusDays(12), 2));
        assertEquals("-", key(DUE.plusDays(13), 2));
        assertEquals("-", key(DUE.plusDays(90), 2), "switching the feature on must not message very old invoices");
    }

    @Test
    void aMissedRunStillProducesOneReminder_butOnlyTheLatestStage() {
        // The job did not run on the due date or the day after; on due+2 only OVERDUE_1 is wanted, never DUE as well
        assertEquals("OVERDUE_1", key(DUE.plusDays(2), 2));
        assertEquals("OVERDUE_2", key(DUE.plusDays(5), 2), "the next stage takes over once its day has come");
    }

    @Test
    void anInvoiceCreatedLateGetsTheStageThatAppliesToday() {
        assertEquals("BEFORE", key(DUE.minusDays(1), 3), "created one day before the due date with a 3-day setting");
    }

    @Test
    void theOverdueIntervalAndCountAreConfigurable() {
        assertEquals("OVERDUE_2", RentReminderPolicy.currentStage(DUE, DUE.plusDays(8), 2, 7, 3).map(Stage::key).orElse("-"));
        assertEquals("-", RentReminderPolicy.currentStage(DUE, DUE.plusDays(5), 2, 7, 3).map(Stage::key).orElse("-"),
                "with a 7-day interval nothing is due on day 5");
        assertEquals(List.of("DUE"), RentReminderPolicy.stages(DUE, 0, 3, 0).stream().map(Stage::key).toList(), "overdue reminders can be turned off");
    }

    @Test
    void overdueStagesAreMarkedAsOverdue() {
        assertTrue(on(DUE.plusDays(1), 2).orElseThrow().isOverdue());
        assertTrue(!on(DUE, 2).orElseThrow().isOverdue());
        assertTrue(!on(DUE.minusDays(2), 2).orElseThrow().isOverdue());
    }

    @Test
    void candidateWindowCoversEveryStageThatCanStillFire() {
        assertEquals(1 + 3 * 3 + RentReminderPolicy.CATCH_UP_DAYS, RentReminderPolicy.maxDaysAfter(3, 4));
        assertEquals(30, RentReminderPolicy.maxDaysBefore());
    }
}
