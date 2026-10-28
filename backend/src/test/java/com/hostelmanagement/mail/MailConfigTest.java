package com.hostelmanagement.mail;

import com.hostelmanagement.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mode selection and fail-fast validation of the e-mail settings. */
class MailConfigTest {

    private final MailConfig config = new MailConfig();

    private EmailService build(AppProperties.Mail mail, String... profiles) {
        AppProperties props = new AppProperties(null, null, null, null, null, null, null, null, null, null, mail, null);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return config.emailService(props, env);
    }

    private static AppProperties.Mail smtp(String host, int port, String user, String pass, String from, String tls, boolean auth) {
        return new AppProperties.Mail("SMTP", host, port, user, pass, from, "HostelHub", tls, auth, 10000);
    }

    private static AppProperties.Mail log() {
        return new AppProperties.Mail("LOG", null, 587, null, null, null, "HostelHub", "STARTTLS", true, 10000);
    }

    @Test
    void logMode_inDevelopment_writesToTheLog_andSendsNothing() {
        EmailService s = build(log(), "dev");
        assertInstanceOf(LogEmailService.class, s);
        assertEquals("LOG", s.mode());
        assertFalse(s.deliversRealMail());
    }

    @Test
    void logMode_inProduction_isRefusedAsAChannel_theAppStartsButSendsNothing() {
        EmailService s = build(log(), "prod");
        assertInstanceOf(DisabledEmailService.class, s);
        assertEquals("DISABLED", s.mode());
        assertFalse(s.deliversRealMail());
    }

    @Test
    void validSmtp_isAccepted_forAnyProvider() {
        EmailService s = build(smtp("smtp.example.org", 587, "user", "secret", "no-reply@example.org", "STARTTLS", true), "prod");
        assertInstanceOf(SmtpEmailService.class, s);
        assertTrue(s.deliversRealMail());
        assertEquals("SMTP", s.mode());
        assertInstanceOf(SmtpEmailService.class, build(smtp("smtp.example.org", 465, "u", "p", "a@example.org", "ssl", true), "prod"),
                "the TLS mode is case-insensitive");
    }

    @Test
    void localDevelopmentSink_needsNoTlsAndNoLogin() {
        assertInstanceOf(SmtpEmailService.class, build(smtp("localhost", 1025, null, null, "dev@example.org", "NONE", false), "dev"));
    }

    @Test
    void unknownMode_failsStartup() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> build(new AppProperties.Mail("SENDGRID", null, 587, null, null, null, "x", "STARTTLS", true, 10000), "dev"));
        assertTrue(e.getMessage().contains("LOG or SMTP"), e.getMessage());
    }

    @Test
    void smtpWithoutHost_failsWithAClearMessage() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> build(smtp("", 587, "u", "p", "a@example.org", "STARTTLS", true), "prod"));
        assertTrue(e.getMessage().contains("MAIL_HOST"), e.getMessage());
    }

    @Test
    void invalidSenderAddress_isRejected() {
        for (String from : new String[]{"", null, "not-an-address", "a b@example.org", "<a@example.org>"}) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> build(smtp("smtp.example.org", 587, "u", "p", from, "STARTTLS", true), "prod"), String.valueOf(from));
            assertTrue(e.getMessage().contains("MAIL_FROM"), e.getMessage());
        }
    }

    @Test
    void invalidPortOrTlsModeOrTimeout_isRejected() {
        assertThrows(IllegalStateException.class, () -> build(smtp("h.example.org", 0, "u", "p", "a@example.org", "STARTTLS", true), "prod"));
        assertThrows(IllegalStateException.class, () -> build(smtp("h.example.org", 70000, "u", "p", "a@example.org", "STARTTLS", true), "prod"));
        IllegalStateException tls = assertThrows(IllegalStateException.class,
                () -> build(smtp("h.example.org", 587, "u", "p", "a@example.org", "TLS1.3", true), "prod"));
        assertTrue(tls.getMessage().contains("MAIL_TLS"));
        assertThrows(IllegalStateException.class, () -> build(new AppProperties.Mail("SMTP", "h.example.org", 587, "u", "p", "a@example.org",
                "HostelHub", "STARTTLS", true, 10), "prod"));
    }

    @Test
    void loginRequiredButMissing_isRejected_withoutEchoingAnyPassword() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> build(smtp("smtp.example.org", 587, "user", "", "a@example.org", "STARTTLS", true), "prod"));
        assertTrue(e.getMessage().contains("MAIL_USERNAME and MAIL_PASSWORD"), e.getMessage());
        assertThrows(IllegalStateException.class, () -> build(smtp("smtp.example.org", 587, "", "pw", "a@example.org", "STARTTLS", true), "prod"));
    }

    @Test
    void credentialsAreNeverSentOverAnUnencryptedConnectionToARemoteServer() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> build(smtp("mail.example.org", 25, "user", "secret-pw", "a@example.org", "NONE", true), "prod"));
        assertTrue(e.getMessage().contains("unencrypted"), e.getMessage());
        assertFalse(e.getMessage().contains("secret-pw"));
    }
}
