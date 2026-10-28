package com.hostelmanagement.mail;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hostelmanagement.config.AppProperties;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The real SMTP code path (JavaMail, AUTH, TLS policy) against an in-process SMTP server. No external service. */
class SmtpEmailServiceTest {

    @RegisterExtension
    static GreenMailExtension mailServer = new GreenMailExtension(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP))
            .withPerMethodLifecycle(true);

    private AppProperties.Mail cfg(String tls, boolean auth, String user, String pass) {
        return new AppProperties.Mail("SMTP", "127.0.0.1", mailServer.getSmtp().getPort(), user, pass, "no-reply@hostelhub.test",
                "HostelHub Support", tls, auth, 5000);
    }

    private static final EmailMessage MESSAGE = new EmailMessage("resident@example.org", "Reset your HostelHub password",
            "Open http://localhost:3000/reset-password?token=abc123 to continue.", true);

    @Test
    void deliversTheMessage_withSenderNameSubjectAndBody() throws Exception {
        mailServer.setUser("mailer", "mailer-secret");
        new SmtpEmailService(cfg("NONE", true, "mailer", "mailer-secret")).send(MESSAGE);

        MimeMessage[] received = mailServer.getReceivedMessages();
        assertEquals(1, received.length);
        assertEquals("Reset your HostelHub password", received[0].getSubject());
        assertEquals("resident@example.org", received[0].getAllRecipients()[0].toString());
        assertTrue(received[0].getFrom()[0].toString().contains("no-reply@hostelhub.test"));
        assertTrue(received[0].getFrom()[0].toString().contains("HostelHub Support"));
        assertTrue(GreenMailUtil.getBody(received[0]).contains("reset-password?token=abc123"));
    }

    @Test
    void worksWithoutLogin_forServersThatDoNotNeedIt() {
        new SmtpEmailService(cfg("NONE", false, null, null)).send(MESSAGE);
        assertEquals(1, mailServer.getReceivedMessages().length);
    }

    @Test
    void wrongPassword_failsCleanly_andNothingIsDelivered_andThePasswordIsNotInTheError() {
        mailServer.setUser("mailer", "right-password");

        MailDeliveryException e = assertThrows(MailDeliveryException.class,
                () -> new SmtpEmailService(cfg("NONE", true, "mailer", "wrong-password")).send(MESSAGE));

        assertEquals(0, mailServer.getReceivedMessages().length);
        assertFalse(e.getMessage().contains("wrong-password"));
        assertFalse(e.getMessage().contains("abc123"), "the reset token must not appear in the error");
    }

    @Test
    void whenTlsIsRequiredButTheServerOffersNone_nothingIsSentInClearText() {
        mailServer.setUser("mailer", "mailer-secret");

        assertThrows(MailDeliveryException.class, () -> new SmtpEmailService(cfg("STARTTLS", true, "mailer", "mailer-secret")).send(MESSAGE));

        assertEquals(0, mailServer.getReceivedMessages().length, "no silent downgrade to an unencrypted session");
    }

    @Test
    void anUnreachableServer_failsFastWithoutHangingTheCaller() {
        AppProperties.Mail dead = new AppProperties.Mail("SMTP", "127.0.0.1", 1, null, null, "no-reply@hostelhub.test", "HostelHub", "NONE", false, 2000);
        long started = System.currentTimeMillis();

        assertThrows(MailDeliveryException.class, () -> new SmtpEmailService(dead).send(MESSAGE));

        assertTrue(System.currentTimeMillis() - started < 6000, "bounded by the configured timeout");
    }

    @Test
    void anInvalidRecipient_isReportedAsADeliveryFailure() {
        assertThrows(MailDeliveryException.class,
                () -> new SmtpEmailService(cfg("NONE", false, null, null)).send(new EmailMessage("not an address", "x", "y", false)));
    }

    @Test
    void disabledMode_logsThatNothingWasSent_butNeverTheLinkOrRecipient() {
        Logger logger = (Logger) LoggerFactory.getLogger(DisabledEmailService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new DisabledEmailService().send(MESSAGE);
        } finally {
            logger.detachAppender(appender);
        }

        String logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
        assertTrue(logged.contains("not sent"));
        assertFalse(logged.contains("abc123"));
        assertFalse(logged.contains("resident@example.org"));
    }
}
