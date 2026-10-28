package com.hostelmanagement.payments;

import com.hostelmanagement.payments.gateway.MockPaymentGatewayService;
import com.hostelmanagement.payments.gateway.PaymentGatewayService;
import com.hostelmanagement.payments.online.PaymentReconciliationScheduler;
import com.hostelmanagement.support.PaymentIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Checkout, verification, webhook and reconciliation behaviour against the real service/database stack. */
class OnlinePaymentFlowTest extends PaymentIntegrationTestBase {

    @Autowired PaymentReconciliationScheduler scheduler;

    // ------------------------------------------------------------------ success / failure / pending

    @Test
    void fullPayment_isVerifiedAndMarksInvoicePaid() throws Exception {
        long inv = invoice("5000.00");
        Order o = order(t.owner(), inv, "5000.00");

        MvcResult r = confirm(t.owner(), o.orderId(), "SUCCESS");

        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals("SUCCEEDED", body(r).get("status").asText());
        assertNotNull(body(r).get("receiptNumber").asText());
        assertMoney("5000.00", amountPaid(inv));
        assertEquals("PAID", invoiceStatus(inv));
        assertEquals(1, onlinePaymentCount(t.residentId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payment_allocations WHERE invoice_id = ?", Integer.class, inv));
    }

    @Test
    void creatingAnOrder_doesNotChangeTheInvoice() throws Exception {
        long inv = invoice("5000.00");
        order(t.owner(), inv, "5000.00");

        assertMoney("0.00", amountPaid(inv));
        assertEquals("PENDING", invoiceStatus(inv));
        assertEquals(0, paymentCount(t.residentId()));
    }

    @Test
    void failedPayment_doesNotReduceBalance_andCanBeRetried() throws Exception {
        long inv = invoice("5000.00");
        Order first = order(t.owner(), inv, "5000.00");

        MvcResult failed = confirm(t.owner(), first.orderId(), "FAILED");

        assertEquals("FAILED", body(failed).get("status").asText());
        assertEquals("Simulated payment failure", body(failed).get("failureReason").asText());
        assertMoney("0.00", amountPaid(inv));
        assertEquals(0, paymentCount(t.residentId()));

        Order retry = order(t.owner(), inv, "5000.00");
        assertEquals("SUCCEEDED", body(confirm(t.owner(), retry.orderId(), "SUCCESS")).get("status").asText());
        assertEquals("PAID", invoiceStatus(inv));
        assertEquals(1, onlinePaymentCount(t.residentId()));
    }

    @Test
    void pendingPayment_isNotCountedUntilConfirmed_thenResolvesAsynchronously() throws Exception {
        long inv = invoice("5000.00");
        Order o = order(t.owner(), inv, "5000.00");

        MvcResult pending = confirm(t.owner(), o.orderId(), "PENDING");

        assertEquals("PENDING", body(pending).get("status").asText());
        assertMoney("0.00", amountPaid(inv));
        assertEquals(0, paymentCount(t.residentId()));
        // A second checkout is refused while the bank has not confirmed the first
        assertEquals(409, startOrder(t.owner(), inv, "5000.00").getResponse().getStatus());

        // The bank confirms later; the staff "check status" path (same as the reconciler) picks it up
        MvcResult settled = send(post("/api/online-payments/mock/attempts/{id}/settle", o.attemptId()).param("outcome", "SUCCEEDED"),
                t.owner(), null);
        assertEquals(200, settled.getResponse().getStatus(), settled.getResponse().getContentAsString());
        assertEquals("SUCCEEDED", body(settled).get("status").asText());
        assertMoney("5000.00", amountPaid(inv));
        assertEquals("PAID", invoiceStatus(inv));
    }

    @Test
    void pendingPayment_thatFails_isReleasedForRetry() throws Exception {
        long inv = invoice("5000.00");
        Order o = order(t.owner(), inv, "5000.00");
        confirm(t.owner(), o.orderId(), "PENDING");

        MvcResult failed = send(post("/api/online-payments/mock/attempts/{id}/settle", o.attemptId()).param("outcome", "FAILED"),
                t.owner(), null);

        assertEquals("FAILED", body(failed).get("status").asText());
        assertMoney("0.00", amountPaid(inv));
        assertEquals(201, startOrder(t.owner(), inv, "5000.00").getResponse().getStatus());
    }

    @Test
    void reconciler_appliesAConfirmedPaymentWhoseWebhookWasLost() throws Exception {
        long inv = invoice("3000.00");
        Order o = order(t.owner(), inv, "3000.00");
        confirm(t.owner(), o.orderId(), "PENDING");
        // Old enough for the reconciler to pick up
        jdbc.update("UPDATE payment_attempts SET created_at = ? WHERE id = ?",
                new java.sql.Timestamp(System.currentTimeMillis() - 600_000), o.attemptId());
        // The bank confirms on the provider side only; the app is not told (no webhook, no user action)
        gateway().settle(o.orderId(), PaymentGatewayService.State.SUCCEEDED);
        assertMoney("0.00", amountPaid(inv));

        int applied = scheduler.reconcileOpenAttempts();

        assertTrue(applied >= 1);
        assertEquals("SUCCEEDED", attemptStatus(o.attemptId()));
        assertMoney("3000.00", amountPaid(inv));
    }

    @Autowired PaymentGatewayService activeGateway;

    private MockPaymentGatewayService gateway() {
        return (MockPaymentGatewayService) activeGateway;
    }

    // ------------------------------------------------------------------ amounts

    @Test
    void partialPayments_reduceBalanceByConfirmedAmount_acrossMultiplePayments() throws Exception {
        long inv = invoice("5000.00");

        confirm(t.owner(), order(t.owner(), inv, "1500.00").orderId(), "SUCCESS");
        assertMoney("1500.00", amountPaid(inv));
        assertEquals("PARTIALLY_PAID", invoiceStatus(inv));

        // A failed attempt in between changes nothing
        confirm(t.owner(), order(t.owner(), inv, "1000.00").orderId(), "FAILED");
        assertMoney("1500.00", amountPaid(inv));

        confirm(t.owner(), order(t.owner(), inv, "3500.00").orderId(), "SUCCESS");
        assertMoney("5000.00", amountPaid(inv));
        assertEquals("PAID", invoiceStatus(inv));
        assertEquals(2, onlinePaymentCount(t.residentId()));
    }

    @Test
    void orderValidation_rejectsBadAmounts() throws Exception {
        long inv = invoice("5000.00");

        assertEquals(400, startOrder(t.owner(), inv, "5000.01").getResponse().getStatus(), "above balance");
        assertEquals(400, startOrder(t.owner(), inv, "99.99").getResponse().getStatus(), "below minimum partial");
        assertEquals(400, startOrder(t.owner(), inv, "0").getResponse().getStatus(), "zero");
        assertEquals(400, startOrder(t.owner(), inv, "-10").getResponse().getStatus(), "negative");
        assertEquals(400, startOrder(t.owner(), inv, "100.123").getResponse().getStatus(), "sub-paisa precision");
        assertEquals(201, startOrder(t.owner(), inv, "100.00").getResponse().getStatus(), "minimum partial is allowed");
    }

    @Test
    void smallRemainingBalance_canBePaidInFull() throws Exception {
        long inv = invoice("5000.00");
        confirm(t.owner(), order(t.owner(), inv, "4950.00").orderId(), "SUCCESS");

        // 50 left, below the 100 partial minimum, but equal to the whole balance
        assertEquals(201, startOrder(t.owner(), inv, "50.00").getResponse().getStatus());
    }

    @Test
    void paidInvoice_cannotBePaidAgain() throws Exception {
        long inv = invoice("1000.00");
        confirm(t.owner(), order(t.owner(), inv, "1000.00").orderId(), "SUCCESS");

        assertEquals(409, startOrder(t.owner(), inv, "100.00").getResponse().getStatus());
    }

    @Test
    void payment_isAppliedToTheChosenInvoice_notTheOldestOne() throws Exception {
        long older = invoice(t, t.hostelId(), t.residentId(), "4000.00", LocalDate.now().minusDays(10));
        long newer = invoice(t, t.hostelId(), t.residentId(), "6000.00", LocalDate.now().plusDays(10));

        confirm(t.owner(), order(t.owner(), newer, "6000.00").orderId(), "SUCCESS");

        assertEquals("PAID", invoiceStatus(newer));
        assertMoney("0.00", amountPaid(older));
        assertMoney("6000.00", amountPaid(newer));
    }

    @Test
    void onlineAndCashPayments_combineOnOneInvoice_andOnlineCanBeReversed() throws Exception {
        long inv = invoice("5000.00");
        MvcResult cash = send(post("/api/payments"), t.owner(), Map.of("residentId", t.residentId(), "amount", "1000.00",
                "paymentDate", LocalDate.now().toString(), "method", "CASH",
                "allocations", List.of(Map.of("invoiceId", inv, "amount", "1000.00"))));
        assertEquals(201, cash.getResponse().getStatus(), cash.getResponse().getContentAsString());

        MvcResult online = confirm(t.owner(), order(t.owner(), inv, "4000.00").orderId(), "SUCCESS");
        long paymentId = body(online).get("paymentId").asLong();

        assertEquals("PAID", invoiceStatus(inv));
        // Existing reversal workflow keeps working on online payments and restores the balance
        MvcResult reversed = send(post("/api/payments/{id}/reverse", paymentId), t.owner(), Map.of("reason", "test reversal"));
        assertEquals(200, reversed.getResponse().getStatus(), reversed.getResponse().getContentAsString());
        assertMoney("1000.00", amountPaid(inv));
        assertEquals("PARTIALLY_PAID", invoiceStatus(inv));
    }

    // ------------------------------------------------------------------ webhooks

    @Test
    void webhook_capturedPayment_isApplied() throws Exception {
        long inv = invoice("5000.00");
        Order o = order(t.owner(), inv, "5000.00");

        MvcResult r = webhook("evt_1", "payment.captured", o.orderId(), "pay_w1", "SUCCEEDED", 500000, "INR");

        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals("APPLIED", body(r).get("status").asText());
        assertEquals("PAID", invoiceStatus(inv));
        assertEquals("pay_w1", jdbc.queryForObject("SELECT provider_payment_id FROM payment_attempts WHERE id = ?",
                String.class, o.attemptId()));
    }

    @Test
    void webhook_duplicateDelivery_isAppliedOnce() throws Exception {
        long inv = invoice("5000.00");
        Order o = order(t.owner(), inv, "2000.00");

        MvcResult first = webhook("evt_dup", "payment.captured", o.orderId(), "pay_d1", "SUCCEEDED", 200000, "INR");
        MvcResult second = webhook("evt_dup", "payment.captured", o.orderId(), "pay_d1", "SUCCEEDED", 200000, "INR");

        assertEquals("APPLIED", body(first).get("status").asText());
        assertEquals(200, second.getResponse().getStatus());
        assertEquals("DUPLICATE_EVENT", body(second).get("status").asText());
        assertMoney("2000.00", amountPaid(inv));
        assertEquals(1, onlinePaymentCount(t.residentId()));
    }

    @Test
    void webhook_sameCaptureUnderNewEventId_isStillAppliedOnce() throws Exception {
        long inv = invoice("5000.00");
        Order o = order(t.owner(), inv, "2000.00");

        webhook("evt_a", "payment.captured", o.orderId(), "pay_n1", "SUCCEEDED", 200000, "INR");
        MvcResult again = webhook("evt_b", "order.paid", o.orderId(), "pay_n1", "SUCCEEDED", 200000, "INR");

        assertEquals("ALREADY_APPLIED", body(again).get("status").asText());
        assertMoney("2000.00", amountPaid(inv));
        assertEquals(1, onlinePaymentCount(t.residentId()));
    }

    @Test
    void webhook_concurrentRedelivery_appliesExactlyOnce() throws Exception {
        long inv = invoice("5000.00");
        Order o = order(t.owner(), inv, "5000.00");
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<String> call = () -> {
                go.await();
                return body(webhook("evt_race", "payment.captured", o.orderId(), "pay_race", "SUCCEEDED", 500000, "INR"))
                        .get("status").asText();
            };
            results.add(pool.submit(call));
        }
        go.countDown();
        List<String> statuses = new ArrayList<>();
        for (Future<String> f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertEquals(1, statuses.stream().filter("APPLIED"::equals).count(), statuses.toString());
        assertEquals(threads - 1, statuses.stream().filter("DUPLICATE_EVENT"::equals).count(), statuses.toString());
        assertEquals(1, onlinePaymentCount(t.residentId()));
        assertMoney("5000.00", amountPaid(inv));
    }

    @Test
    void webhookAndCheckoutConfirmation_racing_applyOnlyOnce() throws Exception {
        long inv = invoice("5000.00");
        Order o = order(t.owner(), inv, "5000.00");
        // Make the simulator know a captured payment so that both paths see the same truth
        confirm(t.owner(), o.orderId(), "PENDING");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<?> hook = pool.submit(() -> {
            go.await();
            return webhook("evt_x1", "payment.captured", o.orderId(), "pay_x", "SUCCEEDED", 500000, "INR");
        });
        Future<?> refresh = pool.submit(() -> {
            go.await();
            return send(post("/api/online-payments/attempts/{id}/refresh", o.attemptId()), t.owner(), null);
        });
        go.countDown();
        hook.get();
        refresh.get();
        pool.shutdown();

        assertEquals(1, onlinePaymentCount(t.residentId()));
        assertMoney("5000.00", amountPaid(inv));
    }

    @Test
    void webhook_invalidSignature_isRejectedAndChangesNothing() throws Exception {
        long inv = invoice("5000.00");
        Order o = order(t.owner(), inv, "5000.00");
        String payload = json.writeValueAsString(Map.of("id", "evt_bad", "type", "payment.captured", "orderId", o.orderId(),
                "paymentId", "pay_bad", "state", "SUCCEEDED", "amountMinor", 500000, "currency", "INR"));

        assertEquals(401, rawWebhook(payload, "deadbeef").getResponse().getStatus());
        assertEquals(401, rawWebhook(payload, null).getResponse().getStatus());
        // Signature is over the exact bytes: a re-serialised/modified body no longer verifies
        assertEquals(401, rawWebhook(payload + " ", signature(payload)).getResponse().getStatus());

        assertMoney("0.00", amountPaid(inv));
        assertEquals(0, paymentCount(t.residentId()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM payment_webhook_events WHERE event_id = 'evt_bad'", Integer.class));
    }

    @Test
    void webhook_forUnknownOrder_isAcknowledgedButIgnored() throws Exception {
        long inv = invoice("5000.00");

        MvcResult r = webhook("evt_unk", "payment.captured", "order_does_not_exist", "pay_u", "SUCCEEDED", 500000, "INR");

        assertEquals(200, r.getResponse().getStatus());
        assertEquals("UNKNOWN_ORDER", body(r).get("status").asText());
        assertMoney("0.00", amountPaid(inv));
    }

    @Test
    void webhook_withWrongAmount_isFlaggedNotApplied() throws Exception {
        long inv = invoice("5000.00");
        Order o = order(t.owner(), inv, "5000.00");

        MvcResult r = webhook("evt_amt", "payment.captured", o.orderId(), "pay_amt", "SUCCEEDED", 100, "INR");

        assertEquals("MISMATCH", body(r).get("status").asText());
        assertEquals("MISMATCH", attemptStatus(o.attemptId()));
        assertMoney("0.00", amountPaid(inv));
        assertEquals(0, paymentCount(t.residentId()));
        // A genuine later capture on a flagged attempt is not auto-applied either: it needs manual review
        MvcResult later = webhook("evt_amt2", "payment.captured", o.orderId(), "pay_amt2", "SUCCEEDED", 500000, "INR");
        assertEquals("ALREADY_APPLIED", body(later).get("status").asText());
        assertEquals(0, paymentCount(t.residentId()));
    }

    @Test
    void webhook_withWrongCurrency_isFlaggedNotApplied() throws Exception {
        long inv = invoice("5000.00");
        Order o = order(t.owner(), inv, "5000.00");

        MvcResult r = webhook("evt_cur", "payment.captured", o.orderId(), "pay_cur", "SUCCEEDED", 500000, "USD");

        assertEquals("MISMATCH", body(r).get("status").asText());
        assertMoney("0.00", amountPaid(inv));
    }

    @Test
    void webhook_failedThenCaptured_onTheSameOrder_endsPaid() throws Exception {
        long inv = invoice("5000.00");
        Order o = order(t.owner(), inv, "5000.00");

        webhook("evt_f", "payment.failed", o.orderId(), "pay_try1", "FAILED", 500000, "INR");
        assertEquals("FAILED", attemptStatus(o.attemptId()));
        assertMoney("0.00", amountPaid(inv));

        webhook("evt_s", "payment.captured", o.orderId(), "pay_try2", "SUCCEEDED", 500000, "INR");
        assertEquals("SUCCEEDED", attemptStatus(o.attemptId()));
        assertEquals("PAID", invoiceStatus(inv));
    }

    @Test
    void webhook_lateFailureEvent_doesNotUndoASuccessfulPayment() throws Exception {
        long inv = invoice("5000.00");
        Order o = order(t.owner(), inv, "5000.00");
        webhook("evt_ok", "payment.captured", o.orderId(), "pay_ok", "SUCCEEDED", 500000, "INR");

        webhook("evt_late_fail", "payment.failed", o.orderId(), "pay_earlier", "FAILED", 500000, "INR");

        assertEquals("SUCCEEDED", attemptStatus(o.attemptId()));
        assertEquals("PAID", invoiceStatus(inv));
    }

    @Test
    void webhook_afterInvoiceSettledByCash_keepsTheMoneyAsCredit() throws Exception {
        long inv = invoice("1000.00");
        Order o = order(t.owner(), inv, "1000.00");
        send(post("/api/payments"), t.owner(), Map.of("residentId", t.residentId(), "amount", "1000.00",
                "paymentDate", LocalDate.now().toString(), "method", "CASH",
                "allocations", List.of(Map.of("invoiceId", inv, "amount", "1000.00"))));

        MvcResult r = webhook("evt_after_cash", "payment.captured", o.orderId(), "pay_ac", "SUCCEEDED", 100000, "INR");

        assertEquals("APPLIED", body(r).get("status").asText());
        // Money was received, so it is recorded; with nothing left to settle it is held as the resident's credit
        assertMoney("1000.00", amountPaid(inv));
        assertMoney("1000.00", jdbc.queryForObject(
                "SELECT unallocated_amount FROM payments WHERE resident_id = ? AND method = 'ONLINE'", java.math.BigDecimal.class,
                t.residentId()));
    }

    @Test
    void confirm_withMissingPaymentId_isRejected_andChangesNothing() throws Exception {
        long inv = invoice("5000.00");
        Order o = order(t.owner(), inv, "5000.00");

        MvcResult r = send(post("/api/online-payments/confirm"), t.owner(),
                Map.of("providerOrderId", o.orderId(), "fields", Map.of("mock_outcome", "SUCCESS")));

        assertEquals(400, r.getResponse().getStatus());
        assertMoney("0.00", amountPaid(inv));
        assertEquals("CREATED", attemptStatus(o.attemptId()));
    }
}
