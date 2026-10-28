package com.hostelmanagement.paymentmethods;

import com.fasterxml.jackson.databind.JsonNode;
import com.hostelmanagement.common.crypto.FieldCipher;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.support.PaymentIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class PaymentMethodTest extends PaymentIntegrationTestBase {

    private static final String ACCOUNT = "123456789012";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired FieldCipher cipher;

    private Map<String, Object> request() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("acceptsCash", true);
        m.put("acceptsUpi", true);
        m.put("upiId", "sunrise@okhdfc");
        m.put("upiPayeeName", "Sunrise PG");
        m.put("acceptsBankTransfer", true);
        m.put("bankAccountHolder", "Sunrise PG");
        m.put("bankName", "HDFC Bank");
        m.put("bankBranch", "Indiranagar");
        m.put("bankIfsc", "HDFC0001234");
        m.put("bankAccountNumber", ACCOUNT);
        m.put("acceptsOnline", true);
        m.put("instructions", "Send the screenshot to the warden");
        return m;
    }

    private MvcResult save(AuthUser as, Map<String, Object> body, Long hostelId) throws Exception {
        var req = put("/api/payment-settings");
        if (hostelId != null) {
            req.param("hostelId", hostelId.toString());
        }
        return send(req, as, body);
    }

    private MvcResult view(AuthUser as, Long hostelId) throws Exception {
        var req = get("/api/payment-settings");
        if (hostelId != null) {
            req.param("hostelId", hostelId.toString());
        }
        return send(req, as, null);
    }

    private String payLink(long invoiceId) throws Exception {
        MvcResult r = send(post("/api/online-payments/invoices/{id}/links", invoiceId), t.owner(), null);
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        String url = body(r).get("url").asText();
        return url.substring(url.lastIndexOf('/') + 1);
    }

    // ------------------------------------------------------------------ validation

    @Test
    void defaults_whenNothingConfigured_areCashAndOnline() throws Exception {
        JsonNode n = body(view(t.owner(), null));
        assertTrue(n.get("acceptsCash").asBoolean());
        assertTrue(n.get("acceptsOnline").asBoolean());
        assertFalse(n.get("acceptsUpi").asBoolean());
        assertFalse(n.get("acceptsBankTransfer").asBoolean());
        assertTrue(n.get("note").asText().contains("not detected automatically"));
    }

    @Test
    void invalidInputs_areRejected() throws Exception {
        Map<String, Object> bad = request();
        bad.put("upiId", "not a upi id");
        assertEquals(400, save(t.owner(), bad, null).getResponse().getStatus());

        bad = request();
        bad.put("bankIfsc", "HDFC1234567");
        assertEquals(400, save(t.owner(), bad, null).getResponse().getStatus());

        bad = request();
        bad.put("bankAccountNumber", "12345");
        assertEquals(400, save(t.owner(), bad, null).getResponse().getStatus());

        bad = request();
        bad.put("bankAccountNumber", "1234-5678-9012");
        assertEquals(400, save(t.owner(), bad, null).getResponse().getStatus());
    }

    @Test
    void enabledMethods_needTheirDetails() throws Exception {
        Map<String, Object> m = request();
        m.put("upiId", null);
        assertEquals(400, save(t.owner(), m, null).getResponse().getStatus(), "UPI on without a UPI ID");

        m = request();
        m.put("bankAccountHolder", null);
        assertEquals(400, save(t.owner(), m, null).getResponse().getStatus(), "bank on without a holder");

        m = request();
        m.put("bankAccountNumber", null);
        assertEquals(400, save(t.owner(), m, null).getResponse().getStatus(), "bank on without any account number");
    }

    @Test
    void atLeastOneWayOfPayingMustRemain() throws Exception {
        Map<String, Object> m = request();
        m.put("acceptsCash", false);
        m.put("acceptsUpi", false);
        m.put("acceptsBankTransfer", false);
        m.put("acceptsOnline", false);
        assertEquals(400, save(t.owner(), m, null).getResponse().getStatus());
    }

    // ------------------------------------------------------------------ protecting the account number

    @Test
    void accountNumber_isEncryptedAtRest_andOnlyMaskedInTheSettingsResponse() throws Exception {
        MvcResult saved = save(t.owner(), request(), null);
        assertEquals(200, saved.getResponse().getStatus(), saved.getResponse().getContentAsString());
        String raw = saved.getResponse().getContentAsString();
        assertFalse(raw.contains(ACCOUNT), "settings response must never carry the account number");
        assertEquals("•••• 9012", body(saved).get("bankAccountMasked").asText());
        assertTrue(body(saved).get("hasBankAccount").asBoolean());

        assertFalse(view(t.owner(), null).getResponse().getContentAsString().contains(ACCOUNT));

        byte[] stored = jdbc.queryForObject("SELECT bank_account_number_enc FROM payment_method_settings WHERE owner_id = ? AND hostel_id IS NULL",
                byte[].class, t.ownerId());
        assertFalse(new String(stored, StandardCharsets.ISO_8859_1).contains(ACCOUNT), "ciphertext must not contain the digits");
        assertEquals(ACCOUNT, cipher.decrypt(stored, "bank-account:owner:" + t.ownerId()));
        assertEquals("9012", jdbc.queryForObject("SELECT bank_account_last4 FROM payment_method_settings WHERE owner_id = ?", String.class, t.ownerId()));
    }

    @Test
    void ciphertext_isBoundToTheOwner_andRandomised_andTamperProof() {
        byte[] a = cipher.encrypt(ACCOUNT, "ctx-1");
        byte[] b = cipher.encrypt(ACCOUNT, "ctx-1");
        assertNotEquals(java.util.HexFormat.of().formatHex(a), java.util.HexFormat.of().formatHex(b), "fresh IV each time");
        assertEquals(ACCOUNT, cipher.decrypt(a, "ctx-1"));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(a, "ctx-2"), "another owner's context must fail");
        byte[] tampered = a.clone();
        tampered[tampered.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered, "ctx-1"));
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(new byte[]{1, 2, 3}, "ctx-1"));
    }

    @Test
    void blankAccountNumberOnUpdate_keepsTheStoredOne_andEmptyRemovesIt() throws Exception {
        save(t.owner(), request(), null);
        Map<String, Object> m = request();
        m.put("bankAccountNumber", null);
        m.put("instructions", "Changed");
        assertEquals(200, save(t.owner(), m, null).getResponse().getStatus());
        assertEquals(ACCOUNT, body(send(get("/api/payment-settings/instructions"), t.manager(), null)).get("bank").get("accountNumber").asText());

        m = request();
        m.put("acceptsBankTransfer", false);
        m.put("bankAccountNumber", "");
        MvcResult cleared = save(t.owner(), m, null);
        assertEquals(200, cleared.getResponse().getStatus());
        assertFalse(body(cleared).get("hasBankAccount").asBoolean());
        assertNull(jdbc.queryForObject("SELECT bank_account_number_enc FROM payment_method_settings WHERE owner_id = ?", byte[].class, t.ownerId()));
    }

    @Test
    void audit_recordsWhatChanged_butNeverTheSecrets() throws Exception {
        save(t.owner(), request(), null);
        String details = jdbc.queryForObject("SELECT details FROM audit_logs WHERE owner_id = ? AND action = 'PAYMENT_SETTINGS_UPDATED' ORDER BY id DESC LIMIT 1",
                String.class, t.ownerId());
        assertTrue(details.contains("bank account number"), details);
        assertFalse(details.contains(ACCOUNT));
        assertFalse(details.contains("sunrise@okhdfc"));
    }

    // ------------------------------------------------------------------ permissions and isolation

    @Test
    void settingsNeedTheSettingsPermission() throws Exception {
        assertEquals(401, mvc.perform(get("/api/payment-settings")).andReturn().getResponse().getStatus());
        assertEquals(403, view(t.manager(), null).getResponse().getStatus());
        assertEquals(403, save(t.manager(), request(), null).getResponse().getStatus());
        AuthUser trusted = makeUser(t, "MANAGER", java.util.Set.of("SETTINGS_MANAGE"), t.hostelId());
        assertEquals(200, save(trusted, request(), null).getResponse().getStatus());
    }

    @Test
    void instructionsForSharing_needPaymentView_andShowFullDetails() throws Exception {
        save(t.owner(), request(), null);
        MvcResult ok = send(get("/api/payment-settings/instructions"), t.viewer(), null);
        assertEquals(200, ok.getResponse().getStatus());
        assertEquals(ACCOUNT, body(ok).get("bank").get("accountNumber").asText());
        assertEquals("sunrise@okhdfc", body(ok).get("upi").get("upiId").asText());
        assertTrue(body(ok).get("note").asText().contains("not detected automatically"));

        AuthUser noPayments = makeUser(t, "STAFF", java.util.Set.of("HOSTEL_VIEW"), t.hostelId());
        assertEquals(403, send(get("/api/payment-settings/instructions"), noPayments, null).getResponse().getStatus());
    }

    @Test
    void anotherOwner_neverSeesThesePaymentDetails() throws Exception {
        save(t.owner(), request(), null);
        Tenant other = newTenant();
        JsonNode theirs = body(view(other.owner(), null));
        assertFalse(theirs.get("acceptsUpi").asBoolean());
        assertEquals(404, view(other.owner(), t.hostelId()).getResponse().getStatus(), "someone else's hostel id");
        assertEquals(404, save(other.owner(), request(), t.hostelId()).getResponse().getStatus());
        assertEquals(404, send(get("/api/payment-settings/instructions").param("hostelId", "" + t.hostelId()), other.owner(), null).getResponse().getStatus());
    }

    @Test
    void managerCannotReadInstructionsForAnUnassignedHostel() throws Exception {
        assertEquals(200, send(get("/api/payment-settings/instructions").param("hostelId", "" + t.hostelId()), t.manager(), null).getResponse().getStatus());
        assertEquals(404, send(get("/api/payment-settings/instructions").param("hostelId", "" + t.hostel2Id()), t.manager(), null).getResponse().getStatus());
    }

    @Test
    void hostelOverride_winsOverDefault_andCanBeRemoved() throws Exception {
        save(t.owner(), request(), null);
        JsonNode inherited = body(view(t.owner(), t.hostel2Id()));
        assertTrue(inherited.get("inherited").asBoolean());
        assertEquals("sunrise@okhdfc", inherited.get("upiId").asText());

        Map<String, Object> own = request();
        own.put("upiId", "hostel2@okicici");
        own.put("acceptsBankTransfer", false);
        own.put("bankAccountNumber", null);
        assertEquals(200, save(t.owner(), own, t.hostel2Id()).getResponse().getStatus());

        JsonNode h2 = body(send(get("/api/payment-settings/instructions").param("hostelId", "" + t.hostel2Id()), t.owner(), null));
        assertEquals("hostel2@okicici", h2.get("upi").get("upiId").asText());
        assertTrue(h2.get("bank").isNull());
        JsonNode h1 = body(send(get("/api/payment-settings/instructions").param("hostelId", "" + t.hostelId()), t.owner(), null));
        assertEquals("sunrise@okhdfc", h1.get("upi").get("upiId").asText());
        assertEquals(ACCOUNT, h1.get("bank").get("accountNumber").asText());

        assertEquals(200, send(delete("/api/payment-settings").param("hostelId", "" + t.hostel2Id()), t.owner(), null).getResponse().getStatus());
        assertEquals("sunrise@okhdfc", body(view(t.owner(), t.hostel2Id())).get("upiId").asText());
    }

    // ------------------------------------------------------------------ QR

    private MvcResult uploadQr(AuthUser as, byte[] content, String name) throws Exception {
        return mvc.perform(multipart("/api/payment-settings/qr").file(new MockMultipartFile("file", name, "image/png", content)).with(as(as))).andReturn();
    }

    @Test
    void qr_acceptsImagesOnly_andReplacementRemovesTheOldFile() throws Exception {
        save(t.owner(), request(), null);
        assertEquals(400, uploadQr(t.owner(), "<html>not an image</html>".getBytes(StandardCharsets.UTF_8), "qr.png").getResponse().getStatus());
        assertEquals(400, uploadQr(t.owner(), "%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8), "qr.png").getResponse().getStatus(), "PDF is not an image");
        assertEquals(403, uploadQr(t.manager(), PNG, "qr.png").getResponse().getStatus());

        MvcResult first = uploadQr(t.owner(), PNG, "qr.png");
        assertEquals(200, first.getResponse().getStatus(), first.getResponse().getContentAsString());
        long firstId = body(first).get("qrFileId").asLong();

        MvcResult second = uploadQr(t.owner(), PNG, "qr2.png");
        long secondId = body(second).get("qrFileId").asLong();
        assertNotEquals(firstId, secondId);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM file_attachments WHERE id = ?", Integer.class, firstId), "old QR deleted");

        // Staff with PAYMENT_VIEW can load the picture to share it; others cannot
        assertEquals(200, getAs(t.viewer(), "/api/files/{id}", secondId).getResponse().getStatus());
        AuthUser noPayments = makeUser(t, "STAFF", java.util.Set.of("HOSTEL_VIEW"), t.hostelId());
        assertEquals(403, getAs(noPayments, "/api/files/{id}", secondId).getResponse().getStatus());
        assertEquals(404, getAs(newTenant().owner(), "/api/files/{id}", secondId).getResponse().getStatus());

        MvcResult removed = send(delete("/api/payment-settings/qr"), t.owner(), null);
        assertEquals(200, removed.getResponse().getStatus());
        assertTrue(body(removed).get("qrFileId").isNull());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM file_attachments WHERE id = ?", Integer.class, secondId));
    }

    // ------------------------------------------------------------------ the resident-facing pay page

    @Test
    void payPage_showsManualDetailsAndTheNoReconcileNote_only_whileThereIsABalance() throws Exception {
        save(t.owner(), request(), null);
        uploadQr(t.owner(), PNG, "qr.png");
        long inv = invoice("1500.00");
        String token = payLink(inv);

        MvcResult page = mvc.perform(get("/api/public/pay/{t}", token).with(freshIp())).andReturn();
        assertEquals(200, page.getResponse().getStatus());
        JsonNode m = body(page).get("manualPayment");
        assertEquals("sunrise@okhdfc", m.get("upi").get("upiId").asText());
        assertTrue(m.get("upi").get("hasQr").asBoolean());
        assertEquals(ACCOUNT, m.get("bank").get("accountNumber").asText());
        assertTrue(m.get("note").asText().contains("does not tell the hostel who paid"));

        MvcResult qr = mvc.perform(get("/api/public/pay/{t}/qr", token).with(freshIp())).andReturn();
        assertEquals(200, qr.getResponse().getStatus());
        assertEquals("image/png", qr.getResponse().getContentType());
        assertArrayEquals(PNG, qr.getResponse().getContentAsByteArray());
        assertEquals(404, mvc.perform(get("/api/public/pay/{t}/qr", "x".repeat(43)).with(freshIp())).andReturn().getResponse().getStatus());

        // Once the invoice is settled nothing about how to pay is exposed any more
        jdbc.update("UPDATE invoices SET status = 'PAID', amount_paid = total_amount WHERE id = ?", inv);
        JsonNode paid = body(mvc.perform(get("/api/public/pay/{t}", token).with(freshIp())).andReturn());
        assertTrue(paid.get("manualPayment").isNull());
        assertEquals(404, mvc.perform(get("/api/public/pay/{t}/qr", token).with(freshIp())).andReturn().getResponse().getStatus());
    }

    @Test
    void payPage_followsTheHostelsOwnSettings() throws Exception {
        save(t.owner(), request(), null);
        Map<String, Object> cashOnly = request();
        cashOnly.put("acceptsUpi", false);
        cashOnly.put("acceptsBankTransfer", false);
        cashOnly.put("bankAccountNumber", null);
        assertEquals(200, save(t.owner(), cashOnly, t.hostel2Id()).getResponse().getStatus());

        long inv = invoice(t, t.hostel2Id(), t.resident2Id(), "900.00", java.time.LocalDate.now().plusDays(5));
        JsonNode m = body(mvc.perform(get("/api/public/pay/{t}", payLink(inv)).with(freshIp())).andReturn()).get("manualPayment");
        assertTrue(m.get("upi").isNull());
        assertTrue(m.get("bank").isNull());
        assertTrue(m.get("cash").asBoolean());
    }

    // ------------------------------------------------------------------ the online switch

    @Test
    void switchingOnlineOff_blocksCheckoutAndLinks_butLeavesManualOptions() throws Exception {
        long inv = invoice("2000.00");
        String token = payLink(inv);
        Map<String, Object> m = request();
        m.put("acceptsOnline", false);
        assertEquals(200, save(t.owner(), m, null).getResponse().getStatus());

        MvcResult staff = startOrder(t.owner(), inv, "500.00");
        assertEquals(403, staff.getResponse().getStatus());
        assertEquals("ONLINE_PAYMENTS_OFF", body(staff).get("code").asText());
        assertEquals(403, send(post("/api/online-payments/invoices/{id}/links", inv), t.owner(), null).getResponse().getStatus());
        MvcResult resident = mvc.perform(post("/api/public/pay/{t}/orders", token).with(freshIp())
                .contentType("application/json").content("{\"amount\":500}")).andReturn();
        assertEquals(403, resident.getResponse().getStatus());

        JsonNode page = body(mvc.perform(get("/api/public/pay/{t}", token).with(freshIp())).andReturn()).get("manualPayment");
        assertFalse(page.get("online").asBoolean());
        assertEquals("sunrise@okhdfc", page.get("upi").get("upiId").asText());

        m.put("acceptsOnline", true);
        save(t.owner(), m, null);
        assertEquals(201, startOrder(t.owner(), inv, "500.00").getResponse().getStatus());
    }
}
