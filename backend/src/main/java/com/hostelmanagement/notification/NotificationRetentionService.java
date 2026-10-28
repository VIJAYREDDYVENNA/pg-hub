package com.hostelmanagement.notification;

import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.scheduling.DbJobLock;
import com.hostelmanagement.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Deletes notifications once they are {@code app.notifications.retention-days} old (default 30), counted from the
 * original creation time and regardless of read state, owner or tenant. Runs daily without any user action.
 *
 * <p>Only rows of the {@code notifications} table are touched. Deletion happens in bounded batches so no long lock is
 * held, a database lock keeps two instances (or two runs) from cleaning at once, and failures are logged without
 * ever including notification contents.
 */
@Slf4j
@Component
public class NotificationRetentionService {

    static final String LOCK_NAME = "notification-cleanup";

    private final NotificationRepository repository;
    private final DbJobLock jobLock;
    private final AppClock clock;
    private final int retentionDays;
    private final int batchSize;

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationRetentionService(NotificationRepository repository, DbJobLock jobLock, AppClock clock,
                                        AppProperties props) {
        this(repository, jobLock, clock, props.notifications().retentionDays(), props.notifications().deleteBatchSize());
    }

    NotificationRetentionService(NotificationRepository repository, DbJobLock jobLock, AppClock clock,
                                 int retentionDays, int batchSize) {
        if (retentionDays < 1) {
            throw new IllegalStateException("app.notifications.retention-days must be at least 1 but was " + retentionDays);
        }
        if (batchSize < 1) {
            throw new IllegalStateException("app.notifications.delete-batch-size must be at least 1");
        }
        this.repository = repository;
        this.jobLock = jobLock;
        this.clock = clock;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
    }

    /** Scheduled entry point. Never throws: a failed run is logged and retried by the next day's run. */
    @Scheduled(cron = "${app.notifications.cleanup-cron:0 30 3 * * *}", zone = "${app.time-zone:Asia/Kolkata}")
    public void scheduledCleanup() {
        long started = System.nanoTime();
        try {
            var result = jobLock.runExclusive(LOCK_NAME, () -> purgeExpired(clock.now()));
            if (result.isPresent()) {
                log.info("Notification cleanup removed {} notification(s) older than {} day(s) in {} ms",
                        result.get(), retentionDays, (System.nanoTime() - started) / 1_000_000);
            }
        } catch (Exception e) {
            // Only the exception type and message are logged: they never contain notification contents
            log.error("Notification cleanup failed after {} ms; it will be retried at the next scheduled run: {}: {}",
                    (System.nanoTime() - started) / 1_000_000, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * Deletes every notification created at or before {@code now - retention}. Safe to call repeatedly.
     *
     * @return the number of notifications deleted.
     */
    public int purgeExpired(Instant now) {
        Instant cutoff = now.minus(Duration.ofDays(retentionDays));
        int total = 0;
        int deleted;
        do {
            deleted = repository.deleteExpiredBatch(cutoff, batchSize);
            total += deleted;
        } while (deleted == batchSize);
        return total;
    }
}
