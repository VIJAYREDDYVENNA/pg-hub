package com.hostelmanagement.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.hostelmanagement.support.PaymentIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Tenant isolation, permissions and pay-link behaviour. */
class OnlinePaymentSecurityTest extends PaymentIntegrationTestBase {

    // ------------------------------------------------------------------ staff authorisation

    @Test
    void unauthenticatedCallers_cannotUseStaffEndpoints() throws Exception {
        long inv = invoice("1000.00");

        assertEquals(401, mvc.perform(post("/api/online-payments/invoices/{id}/orders", inv)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":500}")).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(get("/api/online-payments/config")).andReturn().getResponse().getStatus());
    }

    @Test
    void anotherOwner_cannotStartOrSeePaymentsForThisOwnersInvoice() throws Exception {
        long inv = invoice("5000.00");
        Order mine = order(t.owner(), inv, "1000.00");
        Tenant other = newTenant();

        assertEquals(404, startOrder(other.owner(), inv, "1000.00").getResponse().getStatus());
        assertEquals(404, getAs(other.owner(), "/api/online-payments/invoices/{id}/attempts", inv).getResponse().getStatus());
        assertEquals(404, confirm(other.owner(), mine.orderId(), "SUCCESS").getResponse().getStatus());
        assertEquals(404, send(post("/api/online-payments/attempts/{id}/refresh", mine.attemptId()), other.owner(), null)
                .getResponse().getStatus());
        assertEquals(404, send(post("/api/online-payments/invoices/{id}/links", inv), other.owner(), null)
                .getResponse().getStatus());
        assertMoney("0.00", amountPaid(inv));
    }

    @Test
    void manager_isLimitedToAssignedHostels() throws Exception {
        long assigned = invoice("2000.00");
        long notAssigned = invoice(t, t.hostel2Id(), t.resident2Id(), "2000.00", LocalDate.now().plusDays(3));

        assertEquals(201, startOrder(t.manager(), assigned, "500.00").getResponse().getStatus());
        assertEquals(404, startOrder(t.manager(), notAssigned, "500.00").getResponse().getStatus());
        assertEquals(404, getAs(t.manager(), "/api/online-payments/invoices/{id}/attempts", notAssigned).getResponse().getStatus());
        // An attempt the owner created in the other hostel is invisible to the manager
        Order ownersOrder = order(t.owner(), notAssigned, "500.00");
        assertEquals(404, confirm(t.manager(), ownersOrder.orderId(), "SUCCESS").getResponse().getStatus());
        assertEquals(404, send(post("/api/online-payments/attempts/{id}/refresh", ownersOrder.attemptId()), t.manager(), null)
                .getResponse().getStatus());
        assertMoney("0.00", amountPaid(notAssigned));
    }

    @Test
    void userWithoutCollectPermission_cannotStartOrConfirmPayments() throws Exception {
        long inv = invoice("2000.00");
        Order o = order(t.owner(), inv, "500.00");

        assertEquals(403, startOrder(t.viewer(), inv, "500.00").getResponse().getStatus());
        assertEquals(403, confirm(t.viewer(), o.orderId(), "SUCCESS").getResponse().getStatus());
        assertEquals(403, send(post("/api/online-payments/invoices/{id}/links", inv), t.viewer(), null).getResponse().getStatus());
        // ...but may read the attempt history of hostels they can see
        assertEquals(200, getAs(t.viewer(), "/api/online-payments/invoices/{id}/attempts", inv).getResponse().getStatus());
    }

    @Test
    void attemptFromOneInvoice_cannotBeConfirmedAsAnother() throws Exception {
        long invA = invoice("2000.00");
        long invB = invoice("3000.00");
        Order a = order(t.owner(), invA, "2000.00");

        confirm(t.owner(), a.orderId(), "SUCCESS");

        // Money lands on the invoice the order was created for, never on another one
        assertMoney("2000.00", amountPaid(invA));
        assertMoney("0.00", amountPaid(invB));
    }

    @Test
    void confirmWithUnknownOrderId_isNotFound() throws Exception {
        assertEquals(404, confirm(t.owner(), "order_mock_unknown", "SUCCESS").getResponse().getStatus());
    }

    // ------------------------------------------------------------------ pay links

    private String createLinkToken(long invoiceId) throws Exception {
        MvcResult r = send(post("/api/online-payments/invoices/{id}/links", invoiceId), t.owner(), null);
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        String url = body(r).get("url").asText();
        assertTrue(url.contains("/pay/"), url);
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private MvcResult publicGet(String token) throws Exception {
        return mvc.perform(get("/api/public/pay/{t}", token).with(freshIp())).andReturn();
    }

    private MvcResult publicPost(String token, String path, Object body, Object... vars) throws Exception {
        var req = post("/api/public/pay/{t}" + path, prepend(token, vars)).with(freshIp()).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            req.content(json.writeValueAsString(body));
        }
        return mvc.perform(req).andReturn();
    }

    private static Object[] prepend(Object first, Object[] rest) {
        Object[] all = new Object[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }

    @Test
    void payLink_letsAResidentPayWithoutAccount_andExposesMinimalData() throws Exception {
        long inv = invoice("5000.00");
        String token = createLinkToken(inv);

        MvcResult view = publicGet(token);
        assertEquals(200, view.getResponse().getStatus());
        JsonNode v = body(view);
        assertEquals("5000.00", v.get("balance").decimalValue().setScale(2).toPlainString());
        assertTrue(v.get("payable").asBoolean());
        assertTrue(v.get("gateway").get("simulated").asBoolean());
        String raw = view.getResponse().getContentAsString();
        assertFalse(raw.contains("9876500000"), "resident phone must not be exposed");
        assertFalse(raw.contains("ownerId"), "internal ids must not be exposed");

        MvcResult created = publicPost(token, "/orders", Map.of("amount", 2000));
        assertEquals(201, created.getResponse().getStatus(), created.getResponse().getContentAsString());
        String orderId = body(created).get("checkout").get("orderId").asText();
        MvcResult confirmed = publicPost(token, "/confirm", Map.of("providerOrderId", orderId, "fields",
                Map.of("mock_payment_id", "pay_link_" + UUID.randomUUID().toString().substring(0, 6), "mock_outcome", "SUCCESS")));

        assertEquals(200, confirmed.getResponse().getStatus(), confirmed.getResponse().getContentAsString());
        assertEquals("SUCCEEDED", body(confirmed).get("status").asText());
        assertMoney("2000.00", amountPaid(inv));
        assertEquals("LINK", jdbc.queryForObject("SELECT initiated_via FROM payment_attempts WHERE provider_order_id = ?",
                String.class, orderId));
        // The payment is attributed to the staff member who issued the link
        assertEquals(t.owner().userId(), jdbc.queryForObject("SELECT recorded_by FROM payments WHERE resident_id = ?",
                Long.class, t.residentId()));
    }

    @Test
    void payLink_forOneInvoice_cannotTouchAnotherInvoicesAttempts() throws Exception {
        long invA = invoice("5000.00");
        long invB = invoice("4000.00");
        String tokenA = createLinkToken(invA);
        String tokenB = createLinkToken(invB);
        MvcResult createdB = publicPost(tokenB, "/orders", Map.of("amount", 1000));
        String orderB = body(createdB).get("checkout").get("orderId").asText();
        long attemptB = body(createdB).get("attemptId").asLong();

        MvcResult cross = publicPost(tokenA, "/confirm", Map.of("providerOrderId", orderB, "fields",
                Map.of("mock_payment_id", "pay_x", "mock_outcome", "SUCCESS")));
        MvcResult crossRefresh = publicPost(tokenA, "/attempts/{id}/refresh", null, attemptB);

        assertEquals(404, cross.getResponse().getStatus());
        assertEquals(404, crossRefresh.getResponse().getStatus());
        assertMoney("0.00", amountPaid(invA));
        assertMoney("0.00", amountPaid(invB));
        // An attempt created by staff (no link) is also out of reach of any link
        Order staffOrder = order(t.owner(), invA, "500.00");
        assertEquals(404, publicPost(tokenA, "/confirm", Map.of("providerOrderId", staffOrder.orderId(), "fields",
                Map.of("mock_payment_id", "pay_y", "mock_outcome", "SUCCESS"))).getResponse().getStatus());
    }

    @Test
    void payLink_withBadExpiredOrRevokedToken_isNotFound() throws Exception {
        long inv = invoice("5000.00");
        String token = createLinkToken(inv);

        assertEquals(404, publicGet("not-a-real-token-not-a-real-token").getResponse().getStatus());
        assertEquals(404, publicGet("short").getResponse().getStatus());

        // Issuing a new link retires the previous one
        String newer = createLinkToken(inv);
        assertEquals(404, publicGet(token).getResponse().getStatus());
        assertEquals(200, publicGet(newer).getResponse().getStatus());

        jdbc.update("UPDATE payment_links SET expires_at = ? WHERE invoice_id = ?",
                new java.sql.Timestamp(System.currentTimeMillis() - 1000), inv);
        assertEquals(404, publicGet(newer).getResponse().getStatus());
        assertEquals(404, publicPost(newer, "/orders", Map.of("amount", 500)).getResponse().getStatus());
    }

    @Test
    void payLink_forSettledInvoice_isNotPayable() throws Exception {
        long inv = invoice("1000.00");
        String token = createLinkToken(inv);
        confirm(t.owner(), order(t.owner(), inv, "1000.00").orderId(), "SUCCESS");

        assertFalse(body(publicGet(token)).get("payable").asBoolean());
        assertEquals(409, publicPost(token, "/orders", Map.of("amount", 100)).getResponse().getStatus());
    }

    @Test
    void payLink_tokenIsStoredOnlyAsHash() throws Exception {
        long inv = invoice("1000.00");
        String token = createLinkToken(inv);

        Integer plain = jdbc.queryForObject("SELECT COUNT(*) FROM payment_links WHERE token_hash = ?", Integer.class, token);
        assertEquals(0, plain);
        assertEquals(64, jdbc.queryForObject("SELECT token_hash FROM payment_links WHERE invoice_id = ?", String.class, inv).length());
    }

    @Test
    void publicEndpoints_areRateLimitedPerClient() throws Exception {
        String ip = "203.0.113." + (UUID.randomUUID().hashCode() & 0x7f);
        int blocked = 0;
        for (int i = 0; i < 40; i++) {
            int status = mvc.perform(get("/api/public/pay/{t}", "x".repeat(30)).with(r -> {
                r.setRemoteAddr(ip);
                return r;
            })).andReturn().getResponse().getStatus();
            if (status == 429) {
                blocked++;
            }
        }
        assertTrue(blocked > 0, "expected the limiter to start rejecting after 30 requests/minute");
    }
}
