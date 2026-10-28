package com.hostelmanagement.mail;

import lombok.extern.slf4j.Slf4j;

/** Development mode: shows the e-mail in the log so flows can be tried without any mail server. Never sends anything. */
@Slf4j
public class LogEmailService implements EmailService {

    @Override
    public void send(EmailMessage m) {
        log.info("[DEV E-MAIL - NOT SENT] to={} subject=\"{}\"\n{}", m.to(), m.subject(), m.body());
    }

    @Override
    public String mode() {
        return "LOG";
    }

    @Override
    public boolean deliversRealMail() {
        return false;
    }
}
