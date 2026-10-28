package com.hostelmanagement.notification;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.scheduling.DbJobLock;
import com.hostelmanagement.support.PaymentIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 30-day notification retention against the real MySQL schema (migration V6 index included). */
class NotificationRetentionTest extends PaymentIntegrationTestBase {

    @Autowired NotificationRetentionService service;
    @Autowired NotificationRepository repository;
    @Autowired DbJobLock jobLock;
    @Autowired AppClock clock;
    @Autowired DataSource dataSource;

    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");

    private long notification(long ownerId, long userId, Instant createdAt, boolean read, String title) {
        return insert("""
                INSERT INTO notifications (owner_id, user_id, type, title, message, read_at, created_at)
                VALUES (?, ?, 'PAYMENT_DUE', ?, 'body', ?, ?)""", ownerId, userId, title,
                read ? Timestamp.from(createdAt.plusSeconds(60)) : null, Timestamp.from(createdAt));
    }

    private boolean exists(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE id = ?", Integer.class, id) > 0;
    }

    private long uid() {
        return t.owner().userId();
    }

    // ------------------------------------------------------------------ core retention rules

    @Test
    void notificationsOlderThanThirtyDays_areDeleted() {
        long old = notification(t.ownerId(), uid(), NOW.minus(31, ChronoUnit.DAYS), false, "old");

        service.purgeExpired(NOW);

        assertFalse(exists(old));
    }

    @Test
    void notificationExactlyAtTheCutoff_isEligible_butOneMicrosecondYoungerIsKept() {
        // Created Oct 1 10:00 -> eligible on Oct 31 10:00 (30 full days)
        Instant exactly = NOW.minus(30, ChronoUnit.DAYS);
        long atCutoff = notification(t.ownerId(), uid(), exactly, false, "boundary");
        long justYounger = notification(t.ownerId(), uid(), exactly.plus(1, ChronoUnit.MICROS), false, "younger");
        long justOlder = notification(t.ownerId(), uid(), exactly.minus(1, ChronoUnit.MICROS), false, "older");

        service.purgeExpired(NOW);

        assertFalse(exists(atCutoff), "exactly 30 days old is eligible");
        assertFalse(exists(justOlder));
        assertTrue(exists(justYounger), "younger than 30 days must remain");
    }

    @Test
    void youngerNotifications_remain() {
        long yesterday = notification(t.ownerId(), uid(), NOW.minus(1, ChronoUnit.DAYS), false, "y");
        long twentyNine = notification(t.ownerId(), uid(), NOW.minus(29, ChronoUnit.DAYS), true, "29");

        service.purgeExpired(NOW);

        assertTrue(exists(yesterday));
        assertTrue(exists(twentyNine));
    }

    @Test
    void readAndUnread_areDeletedAfterTheSameRetention_andAgeIgnoresReadTime() {
        Instant created = NOW.minus(45, ChronoUnit.DAYS);
        long unread = notification(t.ownerId(), uid(), created, false, "unread");
        long read = notification(t.ownerId(), uid(), created, true, "read");
        // Read yesterday, but created 45 days ago: still expired (age comes from creation, not read_at)
        jdbc.update("UPDATE notifications SET read_at = ? WHERE id = ?", Timestamp.from(NOW.minus(1, ChronoUnit.DAYS)), read);
        // Created recently but read long ago is impossible; a young unread one must stay
        long young = notification(t.ownerId(), uid(), NOW.minus(2, ChronoUnit.DAYS), false, "young");

        service.purgeExpired(NOW);

        assertFalse(exists(unread));
        assertFalse(exists(read));
        assertTrue(exists(young));
    }

    @Test
    void updatingARowDoesNotRestartItsRetentionClock() {
        long id = notification(t.ownerId(), uid(), NOW.minus(40, ChronoUnit.DAYS), false, "touched");
        jdbc.update("UPDATE notifications SET read_at = ?, title = ? WHERE id = ?", Timestamp.from(NOW), "touched-again", id);

        service.purgeExpired(NOW);

        assertFalse(exists(id));
    }

    @Test
    void differentUsersAndTenants_areAllCleaned_andOnlyExpiredRowsGo() {
        Tenant other = newTenant();
        long mineOld = notification(t.ownerId(), uid(), NOW.minus(50, ChronoUnit.DAYS), false, "mine-old");
        long mineNew = notification(t.ownerId(), uid(), NOW.minus(5, ChronoUnit.DAYS), false, "mine-new");
        long managerOld = notification(t.ownerId(), t.manager().userId(), NOW.minus(31, ChronoUnit.DAYS), true, "mgr-old");
        long managerNew = notification(t.ownerId(), t.manager().userId(), NOW.minus(1, ChronoUnit.DAYS), false, "mgr-new");
        long otherOld = notification(other.ownerId(), other.owner().userId(), NOW.minus(60, ChronoUnit.DAYS), false, "o-old");
        long otherNew = notification(other.ownerId(), other.owner().userId(), NOW.minus(10, ChronoUnit.DAYS), true, "o-new");

        service.purgeExpired(NOW);

        assertFalse(exists(mineOld));
        assertFalse(exists(managerOld));
        assertFalse(exists(otherOld));
        assertTrue(exists(mineNew));
        assertTrue(exists(managerNew));
        assertTrue(exists(otherNew));
        // Ownership of the survivors is intact
        assertEquals(t.ownerId(), jdbc.queryForObject("SELECT owner_id FROM notifications WHERE id = ?", Long.class, mineNew));
        assertEquals(other.ownerId(), jdbc.queryForObject("SELECT owner_id FROM notifications WHERE id = ?", Long.class, otherNew));
    }

    @Test
    void otherBusinessRecords_areUntouched() {
        long invoice = invoice("5000.00");
        jdbc.update("UPDATE invoices SET created_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(NOW.minus(90, ChronoUnit.DAYS)), Timestamp.from(NOW.minus(90, ChronoUnit.DAYS)), invoice);
        jdbc.update("INSERT INTO audit_logs (owner_id, user_id, action, entity_type, details, created_at) VALUES (?, ?, 'X', 'Y', 'd', ?)",
                t.ownerId(), uid(), Timestamp.from(NOW.minus(90, ChronoUnit.DAYS)));
        notification(t.ownerId(), uid(), NOW.minus(90, ChronoUnit.DAYS), false, "old");
        int users = count("users WHERE owner_id = " + t.ownerId());
        int residents = count("residents WHERE owner_id = " + t.ownerId());
        int invoices = count("invoices WHERE owner_id = " + t.ownerId());
        int audits = count("audit_logs WHERE owner_id = " + t.ownerId());
        int hostels = count("hostels WHERE owner_id = " + t.ownerId());

        service.purgeExpired(NOW);

        assertEquals(users, count("users WHERE owner_id = " + t.ownerId()));
        assertEquals(residents, count("residents WHERE owner_id = " + t.ownerId()));
        assertEquals(invoices, count("invoices WHERE owner_id = " + t.ownerId()));
        assertEquals(audits, count("audit_logs WHERE owner_id = " + t.ownerId()));
        assertEquals(hostels, count("hostels WHERE owner_id = " + t.ownerId()));
        assertEquals(0, count("notifications WHERE owner_id = " + t.ownerId() + " AND title = 'old'"));
    }

    private int count(String fromWhere) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + fromWhere, Integer.class);
    }

    @Test
    void runningTwice_isSafe_andTheSecondRunDeletesNothing() {
        notification(t.ownerId(), uid(), NOW.minus(40, ChronoUnit.DAYS), false, "a");
        notification(t.ownerId(), uid(), NOW.minus(41, ChronoUnit.DAYS), true, "b");
        long keep = notification(t.ownerId(), uid(), NOW.minus(3, ChronoUnit.DAYS), false, "keep");

        int first = service.purgeExpired(NOW);
        int second = service.purgeExpired(NOW);

        assertTrue(first >= 2);
        assertEquals(0, second);
        assertTrue(exists(keep));
    }

    @Test
    void configuredRetention_isRespected() {
        NotificationRetentionService sevenDays = new NotificationRetentionService(repository, jobLock, clock, 7, 100);
        long eightDays = notification(t.ownerId(), uid(), NOW.minus(8, ChronoUnit.DAYS), false, "8d");
        long sixDays = notification(t.ownerId(), uid(), NOW.minus(6, ChronoUnit.DAYS), false, "6d");

        sevenDays.purgeExpired(NOW);

        assertFalse(exists(eightDays));
        assertTrue(exists(sixDays));
        assertThrows(IllegalStateException.class, () -> new NotificationRetentionService(repository, jobLock, clock, 0, 100));
    }

    @Test
    void largeBacklogs_areDeletedInBatches() {
        NotificationRetentionService small = new NotificationRetentionService(repository, jobLock, clock, 30, 2);
        List<Long> ids = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i -> notification(t.ownerId(), uid(), NOW.minus(40 + i, ChronoUnit.DAYS), i % 2 == 0, "b" + i)).toList();

        int deleted = small.purgeExpired(NOW);

        assertTrue(deleted >= 5);
        assertTrue(ids.stream().noneMatch(this::exists));
    }

    @Test
    void thePropertyDefault_isThirtyDays() {
        // The wired bean (default configuration) keeps 29-day-old rows and removes 31-day-old rows
        long keep = notification(t.ownerId(), uid(), NOW.minus(29, ChronoUnit.DAYS), false, "29");
        long drop = notification(t.ownerId(), uid(), NOW.minus(31, ChronoUnit.DAYS), false, "31");

        service.purgeExpired(NOW);

        assertTrue(exists(keep));
        assertFalse(exists(drop));
    }

    // ------------------------------------------------------------------ scheduler behaviour

    @Test
    void scheduledRun_deletesExpiredNotifications_withoutAnyUserAction() {
        long old = notification(t.ownerId(), uid(), Instant.now().minus(Duration.ofDays(35)), false, "scheduled-old");
        long fresh = notification(t.ownerId(), uid(), Instant.now().minus(Duration.ofDays(1)), false, "scheduled-new");

        service.scheduledCleanup();

        assertFalse(exists(old));
        assertTrue(exists(fresh));
    }

    @Test
    void scheduledRun_isSkipped_whileAnotherInstanceHoldsTheLock() throws Exception {
        long old = notification(t.ownerId(), uid(), Instant.now().minus(Duration.ofDays(35)), false, "locked-old");
        try (Connection other = dataSource.getConnection()) {
            try (var ps = other.prepareStatement("SELECT GET_LOCK('hostelhub:notification-cleanup', 0)");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }

            service.scheduledCleanup();

            assertTrue(exists(old), "a run that cannot get the lock must not delete");
            try (var ps = other.prepareStatement("SELECT RELEASE_LOCK('hostelhub:notification-cleanup')")) {
                ps.executeQuery().close();
            }
        }

        service.scheduledCleanup();

        assertFalse(exists(old), "the next run succeeds once the lock is free");
    }

    @Test
    void scheduledRun_handlesDatabaseFailures_andLogsWithoutContents() {
        NotificationRepository broken = mock(NotificationRepository.class);
        when(broken.deleteExpiredBatch(any(), anyInt())).thenThrow(new IllegalStateException("db down"));
        NotificationRetentionService failing = new NotificationRetentionService(broken, jobLock, clock, 30, 10);
        Logger logger = (Logger) LoggerFactory.getLogger(NotificationRetentionService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertDoesNotThrow(failing::scheduledCleanup);
        } finally {
            logger.detachAppender(appender);
        }

        ILoggingEvent error = appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).findFirst().orElseThrow();
        assertTrue(error.getFormattedMessage().contains("Notification cleanup failed"));
        assertTrue(error.getFormattedMessage().contains("db down"));
        // The lock was released even though the job failed: a normal run still works
        long old = notification(t.ownerId(), uid(), Instant.now().minus(Duration.ofDays(35)), false, "after-failure");
        service.scheduledCleanup();
        assertFalse(exists(old));
    }

    @Test
    void successLog_reportsCountAndTime_butNotContents() {
        notification(t.ownerId(), uid(), Instant.now().minus(Duration.ofDays(40)), false, "SENSITIVE-RESIDENT-NAME");
        Logger logger = (Logger) LoggerFactory.getLogger(NotificationRetentionService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.scheduledCleanup();
        } finally {
            logger.detachAppender(appender);
        }

        String all = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(all.contains("Notification cleanup removed"), all);
        assertTrue(all.contains(" ms"), all);
        assertFalse(all.contains("SENSITIVE-RESIDENT-NAME"));
    }

    @Test
    void creationTimestampIndexExists() {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'notifications' AND index_name = 'idx_notifications_created'""",
                Integer.class);
        assertEquals(1, n);
    }
}
