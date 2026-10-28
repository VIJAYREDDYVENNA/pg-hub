package com.hostelmanagement.payments.online;

import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.payments.gateway.PaymentGatewayService;
import com.hostelmanagement.payments.online.PaymentAttempt.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Safety net for lost webhooks and abandoned checkouts: asks the provider about attempts that are still open, applies
 * any confirmed payment, and expires checkouts nobody completed. Uses the same idempotent path as webhooks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private static final Duration MIN_AGE = Duration.ofMinutes(2);
    private static final Duration MAX_AGE = Duration.ofHours(48);
    private static final Duration ABANDON_AFTER = Duration.ofHours(24);

    private final PaymentAttemptRepository attempts;
    private final OnlinePaymentService service;
    private final PaymentGatewayService gateway;
    private final AppClock clock;

    @Scheduled(fixedDelayString = "${app.payment.reconcile-interval-ms:300000}", initialDelay = 60_000)
    public void run() {
        reconcileOpenAttempts();
    }

    /** @return number of attempts whose state changed to a final outcome. */
    public int reconcileOpenAttempts() {
        Instant now = clock.now();
        expireAbandoned(now);
        List<PaymentAttempt> open = attempts.findOpenBetween(List.of(Status.CREATED, Status.PENDING),
                now.minus(MIN_AGE), now.minus(MAX_AGE));
        int applied = 0;
        for (PaymentAttempt attempt : open) {
            if (!attempt.getProvider().equals(gateway.providerCode())) {
                continue; // belongs to a previous provider; its adapter is no longer active
            }
            try {
                if (service.reconcile(attempt.getId()) == PaymentAttemptProcessor.Result.APPLIED) {
                    applied++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not reconcile payment attempt {}: {}", attempt.getId(), e.getMessage());
            }
        }
        if (!open.isEmpty()) {
            log.info("Payment reconciliation: {} open attempt(s) checked, {} payment(s) applied", open.size(), applied);
        }
        return applied;
    }

    private void expireAbandoned(Instant now) {
        int expired = attempts.expireCreatedBefore(now.minus(ABANDON_AFTER), now);
        if (expired > 0) {
            log.info("Expired {} abandoned payment attempt(s)", expired);
        }
    }
}
