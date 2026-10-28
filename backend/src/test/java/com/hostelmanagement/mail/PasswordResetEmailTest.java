package com.hostelmanagement.mail;

import com.hostelmanagement.support.PaymentIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Forgot-password through the real API with the mail channel captured instead of sent. */
@Import(PasswordResetEmailTest.CapturingMail.class)
class PasswordResetEmailTest extends PaymentIntegrationTestBase {

    static class CapturingEmail implements EmailService {
        final List<EmailMessage> sent = new CopyOnWriteArrayList<>();
        volatile boolean failing;

        @Override
        public void send(EmailMessage message) {
            if (failing) {
                throw new MailDeliveryException("simulated SMTP outage", null);
            }
            sent.add(message);
        }

        @Override
        public String mode() {
            return "TEST";
        }

        @Override
        public boolean deliversRealMail() {
            return false;
        }
    }

    @TestConfiguration
    static class CapturingMail {
        @Bean
        @Primary
        CapturingEmail capturingEmail() {
            return new CapturingEmail();
        }
    }

    @Autowired CapturingEmail mail;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void reset() {
        mail.sent.clear();
        mail.failing = false;
    }

    private String createUser(boolean enabled) {
        String email = "reset-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
        insert("""
                INSERT INTO users (owner_id, full_name, email, password_hash, enabled, created_at, updated_at)
                VALUES (?, 'Reset Tester', ?, ?, ?, ?, ?)""", t.ownerId(), email, passwordEncoder.encode("Old@pass1"), enabled,
                new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()));
        return email;
    }

    private MvcResult forgot(String email) throws Exception {
        return mvc.perform(post("/api/auth/forgot-password").with(freshIp()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email)))).andReturn();
    }

    private MvcResult resetWith(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/reset-password").with(freshIp()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token, "newPassword", password)))).andReturn();
    }

    private int loginStatus(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(freshIp()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password)))).andReturn().getResponse().getStatus();
    }

    private String awaitToken() {
        await().atMost(5, TimeUnit.SECONDS).until(() -> !mail.sent.isEmpty());
        Matcher m = Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)").matcher(mail.sent.get(0).body());
        assertTrue(m.find(), mail.sent.get(0).body());
        return m.group(1);
    }

    @Test
    void aRegisteredUser_receivesOneEmailWithAWorkingSingleUseLink() throws Exception {
        String email = createUser(true);

        MvcResult r = forgot(email);

        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        String token = awaitToken();
        EmailMessage m = mail.sent.get(0);
        assertEquals(email, m.to());
        assertEquals("Reset your HostelHub password", m.subject());
        assertTrue(m.body().contains("Hello Reset Tester,"));
        assertTrue(m.body().contains("expires in 30 minutes"));
        assertTrue(m.body().contains("http://localhost:3000/reset-password?token=" + token));
        assertEquals(1, mail.sent.size());

        assertEquals(200, resetWith(token, "New@pass2x").getResponse().getStatus());
        assertEquals(200, loginStatus(email, "New@pass2x"), "the new password works");
        assertEquals(401, loginStatus(email, "Old@pass1"), "the old password no longer works");
        assertEquals(400, resetWith(token, "Third@pass3").getResponse().getStatus(), "the link works only once");
    }

    @Test
    void unknownAndDisabledAccounts_getTheSameResponse_andNoEmail() throws Exception {
        String known = createUser(true);
        MvcResult knownResponse = forgot(known);
        await().atMost(5, TimeUnit.SECONDS).until(() -> !mail.sent.isEmpty());
        mail.sent.clear();

        MvcResult unknown = forgot("nobody-" + UUID.randomUUID() + "@test.dev");
        MvcResult disabled = forgot(createUser(false));
        Thread.sleep(800);

        assertEquals(knownResponse.getResponse().getStatus(), unknown.getResponse().getStatus());
        assertEquals(knownResponse.getResponse().getContentAsString(), unknown.getResponse().getContentAsString(),
                "the response must not reveal whether an account exists");
        assertEquals(knownResponse.getResponse().getContentAsString(), disabled.getResponse().getContentAsString());
        assertTrue(mail.sent.isEmpty(), "no e-mail for unknown or disabled accounts");
    }

    @Test
    void aMailOutage_doesNotChangeTheResponse_orLeakTheLinkIntoAnError() throws Exception {
        mail.failing = true;
        String email = createUser(true);

        MvcResult r = forgot(email);

        assertEquals(200, r.getResponse().getStatus());
        assertFalse(r.getResponse().getContentAsString().contains("token"));
        Thread.sleep(500);
        assertTrue(mail.sent.isEmpty());
        // The token row exists, so once mail recovers a new request simply issues another
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM password_reset_tokens t JOIN users u ON u.id = t.user_id WHERE u.email = ?""", Integer.class, email));
    }

    @Test
    void anExpiredOrForgedLink_isRejected() throws Exception {
        String email = createUser(true);
        forgot(email);
        String token = awaitToken();
        jdbc.update("UPDATE password_reset_tokens SET expires_at = ? WHERE user_id = (SELECT id FROM users WHERE email = ?)",
                new Timestamp(System.currentTimeMillis() - 60_000), email);

        assertEquals(400, resetWith(token, "New@pass2x").getResponse().getStatus(), "expired");
        assertEquals(400, resetWith("forged-token-forged-token-forged", "New@pass2x").getResponse().getStatus(), "forged");
        assertEquals(200, loginStatus(email, "Old@pass1"), "the password did not change");
    }

    @Test
    void theTokenIsStoredOnlyAsAHash() throws Exception {
        String email = createUser(true);
        forgot(email);
        String token = awaitToken();

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE token_hash = ?", Integer.class, token));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE LENGTH(token_hash) = 64 AND user_id = (SELECT id FROM users WHERE email = ?)",
                Integer.class, email));
    }
}
