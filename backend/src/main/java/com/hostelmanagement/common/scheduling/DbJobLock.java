package com.hostelmanagement.common.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cross-instance mutual exclusion for scheduled jobs, built on MySQL named locks ({@code GET_LOCK}). The lock is held
 * by a dedicated connection for the duration of the job and is released automatically by MySQL if that connection or
 * the whole instance dies, so a crashed node can never leave a job locked forever. The same mechanism also stops two
 * runs of a job overlapping inside a single instance.
 */
@Slf4j
@Component
public class DbJobLock {

    private static final int MAX_NAME = 64;

    private final DataSource dataSource;

    public DbJobLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Runs {@code job} only if no other instance (or earlier run) currently holds the lock {@code name}.
     *
     * @return the job result, or empty when the lock was held elsewhere and the job was skipped.
     */
    public <T> Optional<T> runExclusive(String name, Supplier<T> job) {
        String lockName = ("hostelhub:" + name);
        if (lockName.length() > MAX_NAME) {
            throw new IllegalArgumentException("Lock name too long: " + name);
        }
        try (Connection lockConnection = dataSource.getConnection()) {
            if (!acquire(lockConnection, lockName)) {
                log.info("Job '{}' is already running elsewhere; skipping this run", name);
                return Optional.empty();
            }
            try {
                return Optional.ofNullable(job.get());
            } finally {
                release(lockConnection, lockName);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not use the job lock '" + name + "'", e);
        }
    }

    private static boolean acquire(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT GET_LOCK(?, 0)")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    private void release(Connection c, String name) {
        try (PreparedStatement ps = c.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            ps.setString(1, name);
            ps.executeQuery().close();
        } catch (SQLException e) {
            // The lock disappears with the connection anyway; just make the problem visible
            log.warn("Could not release job lock '{}': {}", name, e.getMessage());
        }
    }
}
