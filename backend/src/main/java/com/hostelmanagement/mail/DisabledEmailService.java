package com.hostelmanagement.mail;

import lombok.extern.slf4j.Slf4j;

/**
 * Used when no mail delivery is configured outside development. Nothing is sent, and, because reset links are
 * credentials, nothing about the message is logged except that it could not be delivered.
 */
@Slf4j
public class DisabledEmailService implements EmailService {

    @Override
    public void send(EmailMessage m) {
        log.warn("E-mail \"{}\" was not sent because no mail server is configured (set MAIL_MODE=SMTP and the MAIL_* variables)", m.subject());
    }

    @Override
    public String mode() {
        return "DISABLED";
    }

    @Override
    public boolean deliversRealMail() {
        return false;
    }
}
