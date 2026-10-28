package com.hostelmanagement.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.hostelmanagement.support.PaymentIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Dashboard money definitions. Collections is unchanged (gross receipts, deposits included, refunds ignored). Net revenue =
 * Collections - deposit receipts - advance refunds. Deposit refunds are reported separately and are NOT deducted, because the
 * deposit receipt they return was never part of net revenue.
 */
class NetRevenueTest extends PaymentIntegrationTestBase {

    private JsonNode summary(Long hostelId) throws Exception {
        String url = "/api/payments/summary" + (hostelId == null ? "" : "?hostelId=" + hostelId);
        MvcResult r = getAs(t.owner(), url);
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return body(r);
    }

    private static void assertAmount(String expected, JsonNode actual, String what) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()), what + ": expected " + expected + " but was " + actual);
    }

    private long depositInvoice(String total) {
        long id = invoice(total);
        jdbc.update("UPDATE invoices SET invoice_type = 'DEPOSIT' WHERE id = ?", id);
        return id;
    }

    private long pay(long residentId, String amount, String method, LocalDate date, List<Map<String, Object>> allocations) throws Exception {
        Map<String, Object> req = new java.util.HashMap<>(Map.of("residentId", residentId, "amount", amount,
                "paymentDate", date.toString(), "method", method));
        if (allocations != null) {
            req.put("allocations", allocations);
        }
        MvcResult r = send(post("/api/payments"), t.owner(), req);
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return body(r).get("id").asLong();
    }

    private Map<String, Object> alloc(long invoiceId, String amount) {
        return Map.of("invoiceId", invoiceId, "amount", amount);
    }

    private void refund(String type, String amount, Long admissionId) throws Exception {
        Map<String, Object> req = new java.util.HashMap<>(Map.of("residentId", t.residentId(), "refundType", type, "amount", amount,
                "refundDate", LocalDate.now().toString(), "method", "CASH", "reason", "test"));
        if (admissionId != null) {
            req.put("admissionId", admissionId);
        }
        MvcResult r = send(post("/api/payments/refunds"), t.owner(), req);
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    }

    @Test
    void netRevenue_excludesDepositReceiptsAndAdvanceRefunds_butCollectionsKeepsItsMeaning() throws Exception {
        LocalDate today = LocalDate.now();
        long bed = bed(room(t.hostelId(), floor(building(t.hostelId())), "N1"), "A");
        long admission = admit(t.residentId(), t.hostelId(), bed, null);
        long rent = invoice("6000.00");
        long depositInv = depositInvoice("5000.00");
        jdbc.update("""
                INSERT INTO deposits (hostel_id, resident_id, admission_id, invoice_id, amount_required, amount_received, status,
                    created_at, updated_at) VALUES (?, ?, ?, ?, 5000, 0, 'PENDING', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))""",
                t.hostelId(), t.residentId(), admission, depositInv);

        JsonNode before = summary(t.hostelId());
        assertAmount("0", before.get("monthNetRevenue"), "empty month");

        // One payment of 11000 covering rent 6000 and the deposit 5000
        pay(t.residentId(), "11000.00", "UPI", today, List.of(alloc(rent, "6000.00"), alloc(depositInv, "5000.00")));
        JsonNode s1 = summary(t.hostelId());
        assertAmount("11000", s1.get("monthCollections"), "collections is gross receipts, deposits included");
        assertAmount("5000", s1.get("monthDepositReceipts"), "deposit receipts");
        assertAmount("6000", s1.get("monthNetRevenue"), "net revenue excludes the deposit");

        // Advance credit received, then partly handed back
        pay(t.residentId(), "2000.00", "CASH", today, null);
        refund("ADVANCE", "500.00", null);
        JsonNode s2 = summary(t.hostelId());
        assertAmount("13000", s2.get("monthCollections"), "advance counts as collected when it arrives");
        assertAmount("500", s2.get("monthAdvanceRefunds"), "advance refunds");
        assertAmount("7500", s2.get("monthNetRevenue"), "13000 - 5000 deposit - 500 advance refunded");

        // Returning part of the deposit changes neither Collections nor Net revenue; it is reported on its own
        refund("DEPOSIT", "3000.00", admission);
        JsonNode s3 = summary(t.hostelId());
        assertAmount("3000", s3.get("monthDepositRefunds"), "deposit refunds are reported");
        assertAmount("7500", s3.get("monthNetRevenue"), "deposit refunds are not deducted (deposit receipts were never revenue)");
        assertAmount("13000", s3.get("monthCollections"), "collections ignore refunds, as before");

        // Independent check straight from the raw tables
        BigDecimal rawCollected = jdbc.queryForObject("SELECT SUM(amount) FROM payments WHERE hostel_id = ? AND status = 'RECEIVED' AND method <> 'DEPOSIT_ADJUSTMENT'", BigDecimal.class, t.hostelId());
        BigDecimal rawDeposit = jdbc.queryForObject("""
                SELECT COALESCE(SUM(pa.amount), 0) FROM payment_allocations pa JOIN payments p ON p.id = pa.payment_id
                JOIN invoices i ON i.id = pa.invoice_id WHERE p.hostel_id = ? AND p.status = 'RECEIVED' AND i.invoice_type = 'DEPOSIT'""",
                BigDecimal.class, t.hostelId());
        BigDecimal rawAdvance = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM refunds WHERE hostel_id = ? AND refund_type = 'ADVANCE'", BigDecimal.class, t.hostelId());
        assertAmount(rawCollected.subtract(rawDeposit).subtract(rawAdvance).toPlainString(), s3.get("monthNetRevenue"), "matches the raw records");
    }

    @Test
    void depositAdjustments_doNotInflateCollections() throws Exception {
        long bed = bed(room(t.hostelId(), floor(building(t.hostelId())), "N2"), "A");
        long admission = admit(t.residentId(), t.hostelId(), bed, null);
        jdbc.update("""
                INSERT INTO deposits (hostel_id, resident_id, admission_id, amount_required, amount_received, status, created_at, updated_at)
                VALUES (?, ?, ?, 5000, 5000, 'HELD', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))""", t.hostelId(), t.residentId(), admission);
        invoice("3000.00");
        JsonNode before = summary(t.hostelId());

        MvcResult r = send(post("/api/payments/deposit-adjustments"), t.owner(), Map.of("admissionId", admission, "amount", "2000.00"));
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());

        JsonNode after = summary(t.hostelId());
        assertEquals(0, before.get("monthCollections").decimalValue().compareTo(after.get("monthCollections").decimalValue()),
                "moving money from the deposit to rent is not new cash");
        assertEquals(0, before.get("monthNetRevenue").decimalValue().compareTo(after.get("monthNetRevenue").decimalValue()));
    }

    @Test
    void reversedPayments_andOtherHostelsAndMonths_areLeftOut() throws Exception {
        LocalDate today = LocalDate.now();
        long rent = invoice("4000.00");
        long paymentId = pay(t.residentId(), "4000.00", "CASH", today, List.of(alloc(rent, "4000.00")));
        assertAmount("4000", summary(t.hostelId()).get("monthNetRevenue"), "one payment");

        send(post("/api/payments/{id}/reverse", paymentId), t.owner(), Map.of("reason", "bounced"));
        assertAmount("0", summary(t.hostelId()).get("monthNetRevenue"), "a reversed payment is not revenue");
        assertAmount("0", summary(t.hostelId()).get("monthCollections"), "nor a collection");

        // Another hostel of the same owner, and last month, must not leak into this hostel's month
        long other = invoice(t, t.hostel2Id(), t.resident2Id(), "900.00", today.plusDays(3));
        send(post("/api/payments"), t.owner(), Map.of("residentId", t.resident2Id(), "amount", "900.00", "paymentDate", today.toString(),
                "method", "CASH", "allocations", List.of(alloc(other, "900.00"))));
        LocalDate lastMonth = today.withDayOfMonth(1).minusDays(1);
        long old = invoice("700.00");
        pay(t.residentId(), "700.00", "CASH", lastMonth, List.of(alloc(old, "700.00")));

        JsonNode hostel1 = summary(t.hostelId());
        assertAmount("0", hostel1.get("monthNetRevenue"), "hostel 1 this month");
        assertAmount("900", summary(t.hostel2Id()).get("monthNetRevenue"), "hostel 2 this month");
        JsonNode trend = hostel1.get("trend");
        String lastMonthKey = YearMonth.from(lastMonth).toString();
        BigDecimal lastMonthNet = null;
        for (JsonNode p : trend) {
            if (lastMonthKey.equals(p.get("month").asText())) {
                lastMonthNet = p.get("netRevenue").decimalValue();
            }
        }
        assertEquals(0, new BigDecimal("700").compareTo(lastMonthNet), "the trend reports each month's own net revenue");
    }

    @Test
    void scopeFollowsHostelAccess() throws Exception {
        long rent = invoice("1000.00");
        pay(t.residentId(), "1000.00", "CASH", LocalDate.now(), List.of(alloc(rent, "1000.00")));

        // Manager of hostel 1 only sees hostel 1 figures
        MvcResult own = getAs(t.manager(), "/api/payments/summary");
        MvcResult foreign = getAs(t.manager(), "/api/payments/summary?hostelId=" + t.hostel2Id());

        assertEquals(200, own.getResponse().getStatus());
        assertAmount("1000", body(own).get("monthNetRevenue"), "manager sees their hostel");
        assertEquals(404, foreign.getResponse().getStatus(), "a hostel the manager cannot access is not found");
    }
}
