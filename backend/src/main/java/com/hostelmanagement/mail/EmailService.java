package com.hostelmanagement.mail;

/**
 * The only thing the rest of the application knows about e-mail. Implementations: {@link SmtpEmailService} (real delivery
 * through any SMTP server), {@link LogEmailService} (development: written to the log, nothing leaves the machine) and
 * {@link DisabledEmailService} (production without mail settings: nothing is sent and nothing sensitive is logged).
 */
public interface EmailService {

    /** Sends (or records) the message. Throws {@link MailDeliveryException} if delivery fails. */
    void send(EmailMessage message);

    /** LOG, SMTP or DISABLED. */
    String mode();

    /** True only when messages really leave the system. */
    boolean deliversRealMail();
}
