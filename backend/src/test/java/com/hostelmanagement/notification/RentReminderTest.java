package com.hostelmanagement.notification;

import com.hostelmanagement.notification.RentReminderService.Result;
import com.hostelmanagement.support.PaymentIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** The daily reminder job against real MySQL, the real WhatsApp service and the simulated provider. */
class RentReminderTest extends PaymentIntegrationTestBase {

    @Autowired RentReminderService reminders;
    @Autowired DataSource dataSource;

    private static final LocalDate DUE = LocalDate.now().plusDays(10);

    /** The test schema is shared by many tests: make this tenant the only one the job can act on. */
    @BeforeEach
    void onlyThisTenantIsEligible() {
        jdbc.update("UPDATE owner_settings SET auto_reminders_enabled = FALSE");
    }

    private long dueInvoice(String total, LocalDate due) {
        return invoice(t, t.hostelId(), t.residentId(), total, due);
    }

    private void enable(boolean consent, int daysBefore) {
        autoReminders(t.ownerId(), true, daysBefore);
        whatsAppConsent(t.residentId(), consent);
    }

    private List<Map<String, Object>> messages() {
        return jdbc.queryForList("SELECT * FROM whatsapp_messages WHERE resident_id = ? ORDER BY id", t.residentId());
    }

    private int reminderRows(long invoiceId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM rent_reminders WHERE invoice_id = ?", Integer.class, invoiceId);
    }

    private void pay(long invoiceId, String amount) throws Exception {
        send(post("/api/payments"), t.owner(), Map.of("residentId", t.residentId(), "amount", amount, "paymentDate", LocalDate.now().toString(),
                "method", "CASH", "allocations", List.of(Map.of("invoiceId", invoiceId, "amount", amount))));
    }

    // ------------------------------------------------------------------ the happy path

    @Test
    void sendsOneReminder_theConfiguredNumberOfDaysBefore_andNeverTwice() {
        enable(true, 2);
        long inv = dueInvoice("6000.00", DUE);

        Result first = reminders.run(DUE.minusDays(2));
        Result again = reminders.run(DUE.minusDays(2));
        Result nextDay = reminders.run(DUE.minusDays(1));

        assertEquals(1, first.sent());
        assertEquals(0, again.sent(), "a second run the same day sends nothing");
        assertEquals(0, nextDay.sent(), "the BEFORE stage is sent once");
        assertEquals(1, messages().size());
        Map<String, Object> m = messages().get(0);
        assertEquals("RENT_REMINDER", m.get("template_code"));
        assertEquals(true, m.get("simulated"), "WhatsApp stays in simulated mode");
        assertEquals(inv, ((Number) m.get("related_id")).longValue());
        assertEquals("INVOICE", m.get("related_type"));
        assertTrue(((String) m.get("body")).contains("6000.00"));
        assertTrue(((String) m.get("body")).contains("Asha Rao"));
        assertEquals(1, reminderRows(inv));
        assertEquals("BEFORE", jdbc.queryForObject("SELECT stage FROM rent_reminders WHERE invoice_id = ?", String.class, inv));
        assertEquals(((Number) m.get("id")).longValue(), jdbc.queryForObject("SELECT whatsapp_message_id FROM rent_reminders WHERE invoice_id = ?", Long.class, inv));
    }

    @Test
    void followsTheDueDateAndThenRepeatsWhileOverdue_withTheOverdueTemplate() {
        enable(true, 2);
        long inv = dueInvoice("6000.00", DUE);

        reminders.run(DUE.minusDays(2));
        reminders.run(DUE);
        reminders.run(DUE.plusDays(1));
        reminders.run(DUE.plusDays(2));
        reminders.run(DUE.plusDays(4));

        List<String> stages = jdbc.queryForList("SELECT stage FROM rent_reminders WHERE invoice_id = ? ORDER BY id", String.class, inv);
        assertEquals(List.of("BEFORE", "DUE", "OVERDUE_1", "OVERDUE_2"), stages);
        List<Object> templates = messages().stream().map(m -> m.get("template_code")).toList();
        assertEquals(List.of("RENT_REMINDER", "RENT_REMINDER", "OVERDUE_NOTICE", "OVERDUE_NOTICE"), templates);
    }

    @Test
    void zeroDaysBefore_remindsOnTheDueDateOnly() {
        enable(true, 0);
        dueInvoice("1000.00", DUE);

        assertEquals(0, reminders.run(DUE.minusDays(1)).sent());
        assertEquals(1, reminders.run(DUE).sent());
    }

    // ------------------------------------------------------------------ who is NOT messaged

    @Test
    void doesNothing_untilTheOwnerSwitchesAutomationOn() {
        autoReminders(t.ownerId(), false, 2);
        whatsAppConsent(t.residentId(), true);
        dueInvoice("6000.00", DUE);

        assertEquals(0, reminders.run(DUE.minusDays(2)).considered());
        assertTrue(messages().isEmpty());
    }

    @Test
    void residentsWhoHaveNotConsented_areNeverMessaged_andNoSkippedRowsPileUp() {
        enable(false, 2);
        dueInvoice("6000.00", DUE);

        Result r = reminders.run(DUE.minusDays(2));

        assertEquals(0, r.sent());
        assertTrue(messages().isEmpty(), "not even a SKIPPED_NO_CONSENT row is created every day");
    }

    @Test
    void consentWithdrawnBetweenRuns_stopsFurtherReminders() {
        enable(true, 2);
        dueInvoice("6000.00", DUE);
        reminders.run(DUE.minusDays(2));

        whatsAppConsent(t.residentId(), false);
        reminders.run(DUE);
        reminders.run(DUE.plusDays(1));

        assertEquals(1, messages().size());
    }

    @Test
    void formerResidents_areNotMessaged() {
        enable(true, 2);
        dueInvoice("6000.00", DUE);
        jdbc.update("UPDATE residents SET status = 'CHECKED_OUT' WHERE id = ?", t.residentId());

        assertEquals(0, reminders.run(DUE.minusDays(2)).sent());
    }

    @Test
    void settledOrClosedInvoices_getNoReminder() {
        enable(true, 2);
        long paid = dueInvoice("1000.00", DUE);
        long cancelled = dueInvoice("1100.00", DUE);
        long waived = dueInvoice("1200.00", DUE);
        jdbc.update("UPDATE invoices SET status = 'PAID', amount_paid = total_amount WHERE id = ?", paid);
        jdbc.update("UPDATE invoices SET status = 'CANCELLED' WHERE id = ?", cancelled);
        jdbc.update("UPDATE invoices SET status = 'WAIVED' WHERE id = ?", waived);

        assertEquals(0, reminders.run(DUE.minusDays(2)).sent());
        assertTrue(messages().isEmpty());
    }

    @Test
    void tooEarlyAndVeryOldInvoices_areLeftAlone() {
        enable(true, 2);
        dueInvoice("1000.00", DUE.plusDays(20));
        dueInvoice("1000.00", DUE.minusDays(120));

        Result r = reminders.run(DUE);

        assertEquals(0, r.sent());
        assertTrue(messages().isEmpty());
    }

    @Test
    void ownersWhosePlanLacksWhatsApp_areSkipped() {
        enable(true, 2);
        dueInvoice("1000.00", DUE);
        long enterprise = jdbc.queryForObject("SELECT id FROM subscription_plans WHERE code = 'ENTERPRISE'", Long.class);
        jdbc.update("UPDATE plan_entitlements SET enabled = FALSE WHERE plan_id = ? AND feature_code = 'WHATSAPP_MESSAGING'", enterprise);
        try {
            assertEquals(0, reminders.run(DUE.minusDays(2)).sent());
            assertTrue(messages().isEmpty());
        } finally {
            jdbc.update("UPDATE plan_entitlements SET enabled = TRUE WHERE plan_id = ? AND feature_code = 'WHATSAPP_MESSAGING'", enterprise);
        }
        assertEquals(1, reminders.run(DUE.minusDays(2)).sent(), "and they resume when the feature is back");
    }

    // ------------------------------------------------------------------ payments stop reminders

    @Test
    void aPartPayment_changesTheAmountAsked_andFullPaymentStopsEverything() throws Exception {
        enable(true, 2);
        long inv = dueInvoice("6000.00", DUE);
        reminders.run(DUE.minusDays(2));

        pay(inv, "2500.00");
        reminders.run(DUE);

        List<Map<String, Object>> sent = messages();
        assertEquals(2, sent.size());
        assertTrue(((String) sent.get(0).get("body")).contains("6000.00"));
        assertTrue(((String) sent.get(1).get("body")).contains("3500.00"), "the second reminder asks only for what is left");
        assertFalse(((String) sent.get(1).get("body")).contains("6000.00"));

        pay(inv, "3500.00");
        assertEquals("PAID", invoiceStatus(inv));
        reminders.run(DUE.plusDays(1));
        reminders.run(DUE.plusDays(4));

        assertEquals(2, messages().size(), "no reminder after the invoice is fully paid");
    }

    @Test
    void anInvoicePaidJustBeforeTheRun_isNotRemindedAbout() throws Exception {
        enable(true, 2);
        long inv = dueInvoice("1500.00", DUE);
        pay(inv, "1500.00");

        assertEquals(0, reminders.run(DUE.minusDays(2)).sent());
        assertEquals(0, reminderRows(inv));
    }

    // ------------------------------------------------------------------ robustness

    @Test
    void aMissedRun_sendsOneReminderForTheCurrentStage_notABurst() {
        enable(true, 2);
        long inv = dueInvoice("1000.00", DUE);

        reminders.run(DUE.plusDays(2));

        assertEquals(List.of("OVERDUE_1"), jdbc.queryForList("SELECT stage FROM rent_reminders WHERE invoice_id = ?", String.class, inv));
        assertEquals(1, messages().size());
    }

    @Test
    void concurrentRuns_sendExactlyOneMessage() throws Exception {
        enable(true, 2);
        long inv = dueInvoice("1000.00", DUE);
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Result>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return reminders.run(DUE.minusDays(2));
            }));
        }
        go.countDown();
        int sent = 0;
        for (Future<Result> f : results) {
            sent += f.get().sent();
        }
        pool.shutdown();

        assertEquals(1, sent);
        assertEquals(1, messages().size());
        assertEquals(1, reminderRows(inv));
    }

    @Test
    void eachOwnerIsHandledIndependently() {
        enable(true, 2);
        dueInvoice("1000.00", DUE);
        Tenant other = newTenant();
        autoReminders(other.ownerId(), true, 5);
        whatsAppConsent(other.residentId(), true);
        long otherInvoice = invoice(other, other.hostelId(), other.residentId(), "2000.00", DUE);

        reminders.run(DUE.minusDays(2));

        assertEquals(1, messages().size(), "owner A: 2 days before");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM whatsapp_messages WHERE resident_id = ?", Integer.class, other.residentId()),
                "owner B asked for 5 days, so nothing is due yet");
        reminders.run(DUE.minusDays(5));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM whatsapp_messages WHERE resident_id = ?", Integer.class, other.residentId()));
        assertEquals(other.ownerId(), jdbc.queryForObject("SELECT owner_id FROM whatsapp_messages WHERE resident_id = ?", Long.class, other.residentId()));
        assertEquals(1, reminderRows(otherInvoice));
    }

    @Test
    void theScheduledEntryPoint_runsOnce_andIsSkippedWhileAnotherInstanceHoldsTheLock() throws Exception {
        enable(true, 2);
        dueInvoice("1000.00", LocalDate.now().plusDays(2));

        try (Connection other = dataSource.getConnection()) {
            try (var ps = other.prepareStatement("SELECT GET_LOCK('hostelhub:rent-reminders', 0)")) {
                ps.executeQuery().close();
            }
            reminders.scheduledRun();
            assertTrue(messages().isEmpty(), "another instance is running: this one must not");
            try (var ps = other.prepareStatement("SELECT RELEASE_LOCK('hostelhub:rent-reminders')")) {
                ps.executeQuery().close();
            }
        }

        reminders.scheduledRun();
        reminders.scheduledRun();
        assertEquals(1, messages().size());
    }

    @Test
    void theDedupeKeyIsEnforcedByTheDatabaseItself() {
        long inv = dueInvoice("1000.00", DUE);
        jdbc.update("INSERT INTO rent_reminders (owner_id, invoice_id, stage, scheduled_for, created_at) VALUES (?, ?, 'DUE', ?, UTC_TIMESTAMP(6))",
                t.ownerId(), inv, java.sql.Date.valueOf(DUE));

        int second = jdbc.update("INSERT IGNORE INTO rent_reminders (owner_id, invoice_id, stage, scheduled_for, created_at) VALUES (?, ?, 'DUE', ?, UTC_TIMESTAMP(6))",
                t.ownerId(), inv, java.sql.Date.valueOf(DUE));

        assertEquals(0, second);
        assertEquals(1, reminderRows(inv));
    }

    @Test
    void theOwnerSettingIsSavedAndReadBack() throws Exception {
        MvcResultHolder saved = new MvcResultHolder();
        saved.status = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/owners/settings"), t.owner(),
                Map.of("vacancyWarningDays", 7, "paymentGraceDays", 5, "lateFeeAmount", 0, "reminderDaysBefore", 3, "autoRemindersEnabled", true,
                        "currencyCode", "INR")).getResponse().getStatus();

        assertEquals(200, saved.status);
        assertEquals(true, jdbc.queryForObject("SELECT auto_reminders_enabled FROM owner_settings WHERE owner_id = ?", Boolean.class, t.ownerId()));
        assertEquals(3, jdbc.queryForObject("SELECT reminder_days_before FROM owner_settings WHERE owner_id = ?", Integer.class, t.ownerId()));
        // A manager without the settings permission cannot switch it
        assertEquals(403, send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/owners/settings"), t.manager(),
                Map.of("vacancyWarningDays", 7, "paymentGraceDays", 5, "lateFeeAmount", 0, "reminderDaysBefore", 3, "autoRemindersEnabled", false,
                        "currencyCode", "INR")).getResponse().getStatus());
    }

    private static final class MvcResultHolder {
        int status;
    }
}
