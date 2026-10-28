package com.hostelmanagement.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Owners that predate subscriptions keep working exactly as before: at start-up each one without a subscription is
 * grandfathered on an open-ended ENTERPRISE subscription whose limits are never below what the owner already uses.
 * Nothing is deleted or reduced; a Super Admin can then move the owner to a real plan.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class SubscriptionBackfill implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final OwnerSubscriptionRepository subscriptions;
    private final SubscriptionService service;

    @Override
    public void run(ApplicationArguments args) {
        List<Long> owners = jdbc.queryForList("SELECT id FROM owners ORDER BY id", Long.class);
        int created = 0;
        for (Long id : owners) {
            if (subscriptions.findByOwnerId(id).isEmpty()) {
                try {
                    service.grandfather(id);
                    created++;
                } catch (RuntimeException e) {
                    log.error("Could not grandfather owner {}: {}", id, e.getMessage());
                }
            }
        }
        if (created > 0) {
            log.info("Grandfathered {} existing owner(s) onto open-ended subscriptions", created);
        }
    }
}
