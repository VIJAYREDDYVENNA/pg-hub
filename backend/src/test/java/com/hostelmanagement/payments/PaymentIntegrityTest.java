package com.hostelmanagement.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.hostelmanagement.support.PaymentIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Reversals, deposit accounting and duplicate-submission protection for manually recorded money. */
class PaymentIntegrityTest extends PaymentIntegrationTestBase {

    private long admission() {
        long bed = bed(room(t.hostelId(), floor(building(t.hostelId())), "T" + UUID.randomUUID().toString().substring(0, 4)), "A");
        return admit(t.residentId(), t.hostelId(), bed, null);
    }

    private long deposit(long admissionId, Long invoiceId, String received) {
        return insert("""
                INSERT INTO deposits (hostel_id, resident_id, admission_id, invoice_id, amount_required, amount_received, status,
                    created_at, updated_at) VALUES (?, ?, ?, ?, 5000, ?, 'HELD', ?, ?)""", t.hostelId(), t.residentId(), admissionId,
                invoiceId, new BigDecimal(received), new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()));
    }

    private BigDecimal col(String table, String column, long id) {
        return jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE id = ?", BigDecimal.class, id);
    }

    private MvcResult pay(String amount, long invoiceId, String key) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("residentId", t.residentId(), "amount", amount,
                "paymentDate", LocalDate.now().toString(), "method", "CASH",
                "allocations", List.of(Map.of("invoiceId", invoiceId, "amount", amount))));
        if (key != null) {
            body.put("idempotencyKey", key);
        }
        return send(post("/api/payments"), t.owner(), body);
    }

    // ------------------------------------------------------------------ deposit adjustments

    @Test
    void reversingADepositAdjustment_putsTheMoneyBackIntoTheDeposit() throws Exception {
        long admission = admission();
        long deposit = deposit(admission, null, "5000.00");
        long rent = invoice("3000.00");

        MvcResult adjusted = send(post("/api/payments/deposit-adjustments"), t.owner(), Map.of("admissionId", admission, "amount", "2000.00"));
        assertEquals(201, adjusted.getResponse().getStatus(), adjusted.getResponse().getContentAsString());
        long paymentId = body(adjusted).get("id").asLong();
        assertMoney("2000.00", amountPaid(rent));
        assertMoney("2000.00", col("deposits", "amount_adjusted", deposit));

        MvcResult reversed = send(post("/api/payments/{id}/reverse", paymentId), t.owner(), Map.of("reason", "adjusted by mistake"));

        assertEquals(200, reversed.getResponse().getStatus(), reversed.getResponse().getContentAsString());
        assertMoney("0.00", amountPaid(rent));
        assertMoney("0.00", col("deposits", "amount_adjusted", deposit));
        assertEquals("HELD", jdbc.queryForObject("SELECT status FROM deposits WHERE id = ?", String.class, deposit));
        assertEquals("REVERSED", jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, paymentId));
        assertEquals(409, send(post("/api/payments/{id}/reverse", paymentId), t.owner(), Map.of("reason", "again")).getResponse().getStatus(),
                "a payment can only be reversed once");
    }

    @Test
    void reversingADepositPayment_isRefused_afterPartOfTheDepositWasRefunded() throws Exception {
        long admission = admission();
        long depositInvoice = invoice("5000.00");
        jdbc.update("UPDATE invoices SET invoice_type = 'DEPOSIT' WHERE id = ?", depositInvoice);
        long deposit = deposit(admission, depositInvoice, "0.00");
        MvcResult paid = pay("5000.00", depositInvoice, null);
        assertEquals(201, paid.getResponse().getStatus(), paid.getResponse().getContentAsString());
        long paymentId = body(paid).get("id").asLong();
        assertMoney("5000.00", col("deposits", "amount_received", deposit));
        MvcResult refund = send(post("/api/payments/refunds"), t.owner(), Map.of("residentId", t.residentId(), "refundType", "DEPOSIT",
                "admissionId", admission, "amount", "3000.00", "refundDate", LocalDate.now().toString(), "method", "CASH", "reason", "partial"));
        assertEquals(201, refund.getResponse().getStatus(), refund.getResponse().getContentAsString());

        MvcResult reversed = send(post("/api/payments/{id}/reverse", paymentId), t.owner(), Map.of("reason", "bounced"));

        assertEquals(409, reversed.getResponse().getStatus());
        assertMoney("5000.00", col("deposits", "amount_received", deposit), "nothing may change when the reversal is refused");
        assertMoney("5000.00", amountPaid(depositInvoice));
        assertEquals("RECEIVED", jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, paymentId));
    }

    private static void assertMoney(String expected, BigDecimal actual, String message) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), message + ": expected " + expected + " but was " + actual);
    }

    // ------------------------------------------------------------------ advance credit

    @Test
    void reversingAPayment_isRefused_whenPartOfItsAdvanceWasAlreadyRefunded() throws Exception {
        // No open invoices, so the whole payment is held as advance credit
        MvcResult paid = send(post("/api/payments"), t.owner(), Map.of("residentId", t.residentId(), "amount", "5000.00",
                "paymentDate", LocalDate.now().toString(), "method", "UPI"));
        assertEquals(201, paid.getResponse().getStatus(), paid.getResponse().getContentAsString());
        long paymentId = body(paid).get("id").asLong();
        MvcResult refund = send(post("/api/payments/refunds"), t.owner(), Map.of("residentId", t.residentId(), "refundType", "ADVANCE",
                "amount", "2000.00", "refundDate", LocalDate.now().toString(), "method", "UPI", "reason", "advance returned"));
        assertEquals(201, refund.getResponse().getStatus(), refund.getResponse().getContentAsString());

        MvcResult reversed = send(post("/api/payments/{id}/reverse", paymentId), t.owner(), Map.of("reason", "bounced"));

        assertEquals(409, reversed.getResponse().getStatus());
        assertTrue(body(reversed).get("message").asText().contains("already refunded"), body(reversed).get("message").asText());
        assertMoney("3000.00", col("payments", "unallocated_amount", paymentId), "remaining credit untouched");
        assertEquals("RECEIVED", jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, paymentId));
    }

    @Test
    void reversingAnOrdinaryPayment_stillRestoresTheInvoice() throws Exception {
        long rent = invoice("4000.00");
        long paymentId = body(pay("4000.00", rent, null)).get("id").asLong();
        assertEquals("PAID", invoiceStatus(rent));

        MvcResult reversed = send(post("/api/payments/{id}/reverse", paymentId), t.owner(), Map.of("reason", "bounced"));

        assertEquals(200, reversed.getResponse().getStatus());
        assertMoney("0.00", amountPaid(rent));
        assertEquals("PENDING", invoiceStatus(rent));
    }

    @Test
    void reversalRequiresTheAdjustPermission_andOwnership() throws Exception {
        long rent = invoice("1000.00");
        long paymentId = body(pay("1000.00", rent, null)).get("id").asLong();

        assertEquals(403, send(post("/api/payments/{id}/reverse", paymentId), t.manager(), Map.of("reason", "x")).getResponse().getStatus(),
                "managers without PAYMENT_ADJUST cannot reverse");
        assertEquals(404, send(post("/api/payments/{id}/reverse", paymentId), newTenant().owner(), Map.of("reason", "x")).getResponse().getStatus(),
                "another owner cannot reach it");
        assertMoney("1000.00", amountPaid(rent), "unchanged");
    }

    // ------------------------------------------------------------------ duplicate submissions

    @Test
    void sameIdempotencyKey_recordsOnePayment_andReturnsTheOriginalOnRetry() throws Exception {
        long rent = invoice("5000.00");
        String key = "pay-" + UUID.randomUUID();

        MvcResult first = pay("1500.00", rent, key);
        MvcResult retry = pay("1500.00", rent, key);

        assertEquals(201, first.getResponse().getStatus(), first.getResponse().getContentAsString());
        assertEquals(201, retry.getResponse().getStatus(), retry.getResponse().getContentAsString());
        assertEquals(body(first).get("id").asLong(), body(retry).get("id").asLong());
        assertEquals(1, paymentCount(t.residentId()));
        assertMoney("1500.00", amountPaid(rent));
    }

    @Test
    void reusingAKeyForADifferentPayment_isRejected() throws Exception {
        long rent = invoice("5000.00");
        String key = "pay-" + UUID.randomUUID();
        pay("1500.00", rent, key);

        MvcResult different = pay("2500.00", rent, key);

        assertEquals(409, different.getResponse().getStatus());
        assertMoney("1500.00", amountPaid(rent));
        assertEquals(1, paymentCount(t.residentId()));
    }

    @Test
    void keysAreScopedPerOwner_andWithoutAKeyEverySubmissionIsANewPayment() throws Exception {
        long rent = invoice("5000.00");
        String key = "shared-" + UUID.randomUUID();
        pay("1000.00", rent, key);
        Tenant other = newTenant();
        long otherInvoice = invoice(other, other.hostelId(), other.residentId(), "5000.00", LocalDate.now().plusDays(3));

        MvcResult otherOwner = send(post("/api/payments"), other.owner(), Map.of("residentId", other.residentId(), "amount", "1000.00",
                "paymentDate", LocalDate.now().toString(), "method", "CASH", "idempotencyKey", key,
                "allocations", List.of(Map.of("invoiceId", otherInvoice, "amount", "1000.00"))));
        assertEquals(201, otherOwner.getResponse().getStatus(), otherOwner.getResponse().getContentAsString());
        assertEquals(1, paymentCount(other.residentId()));

        pay("500.00", rent, null);
        pay("500.00", rent, null);
        assertEquals(3, paymentCount(t.residentId()), "legacy clients without a key behave as before");
    }

    @Test
    void invalidKeys_areRejected() throws Exception {
        long rent = invoice("1000.00");

        assertEquals(400, pay("100.00", rent, "has spaces!").getResponse().getStatus());
        assertEquals(400, pay("100.00", rent, "x".repeat(65)).getResponse().getStatus());
        assertEquals(0, paymentCount(t.residentId()));
    }

    @Test
    void concurrentIdenticalSubmissions_recordExactlyOnePayment() throws Exception {
        long rent = invoice("5000.00");
        String key = "race-" + UUID.randomUUID();
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcResult>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return pay("1200.00", rent, key);
            }));
        }
        go.countDown();
        List<Long> ids = new ArrayList<>();
        for (Future<MvcResult> f : results) {
            MvcResult r = f.get();
            assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
            ids.add(body(r).get("id").asLong());
        }
        pool.shutdown();

        assertEquals(1, ids.stream().distinct().count(), "every caller is told about the same payment");
        assertEquals(1, paymentCount(t.residentId()));
        assertMoney("1200.00", amountPaid(rent));
        JsonNode first = body(getAs(t.owner(), "/api/payments/{id}", ids.get(0)));
        assertEquals(t.residentId(), first.get("residentId").asLong());
    }
}
