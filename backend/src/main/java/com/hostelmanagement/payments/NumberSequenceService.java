package com.hostelmanagement.payments;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Gap-tolerant, per-owner, per-year document numbers (INV-2026-00001, RCP-2026-00001). */
@Service
@RequiredArgsConstructor
public class NumberSequenceService {

    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Long ownerId, String prefix, int year) {
        // The no-op ON DUPLICATE KEY UPDATE takes an exclusive lock on the row straight away. The previous INSERT IGNORE
        // took a shared lock on a duplicate, and two transactions then both waited to upgrade it: a deadlock whenever two
        // documents of a new owner/year were numbered at the same moment.
        jdbc.update("""
                INSERT INTO number_sequences (owner_id, seq_name, seq_year, next_value) VALUES (?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE next_value = next_value""", ownerId, prefix, year);
        Long value = jdbc.queryForObject("""
                SELECT next_value FROM number_sequences WHERE owner_id = ? AND seq_name = ? AND seq_year = ? FOR UPDATE""",
                Long.class, ownerId, prefix, year);
        jdbc.update("UPDATE number_sequences SET next_value = next_value + 1 WHERE owner_id = ? AND seq_name = ? AND seq_year = ?",
                ownerId, prefix, year);
        return "%s-%d-%05d".formatted(prefix, year, value);
    }
}
