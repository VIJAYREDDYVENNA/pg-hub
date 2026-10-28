package com.hostelmanagement.mail;

import com.hostelmanagement.config.AppProperties;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Delivers through any standard SMTP server (host, port, TLS mode and credentials come from {@code app.mail.*}). */
@Slf4j
public class SmtpEmailService implements EmailService {

    private final JavaMailSenderImpl sender;
    private final String from;
    private final String fromName;

    public SmtpEmailService(AppProperties.Mail cfg) {
        this.sender = new JavaMailSenderImpl();
        this.sender.setHost(cfg.host());
        this.sender.setPort(cfg.port());
        this.sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        if (cfg.auth()) {
            this.sender.setUsername(cfg.username());
            this.sender.setPassword(cfg.password());
        }
        Properties p = this.sender.getJavaMailProperties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.auth", String.valueOf(cfg.auth()));
        String timeout = String.valueOf(cfg.timeoutMs());
        p.put("mail.smtp.connectiontimeout", timeout);
        p.put("mail.smtp.timeout", timeout);
        p.put("mail.smtp.writetimeout", timeout);
        switch (cfg.tls().toUpperCase()) {
            case "STARTTLS" -> {
                p.put("mail.smtp.starttls.enable", "true");
                // Never fall back to an unencrypted session if the server does not offer TLS
                p.put("mail.smtp.starttls.required", "true");
                p.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            case "SSL" -> {
                p.put("mail.smtp.ssl.enable", "true");
                p.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            default -> { /* NONE: only for a local development sink */ }
        }
        this.from = cfg.from();
        this.fromName = cfg.fromName();
    }

    @Override
    public void send(EmailMessage m) {
        try {
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(from, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(m.to());
            helper.setSubject(m.subject());
            helper.setText(m.body(), false);
            sender.send(mime);
        } catch (MailException | jakarta.mail.MessagingException | java.io.UnsupportedEncodingException e) {
            // The cause chain can name the server but never contains the body or the password
            throw new MailDeliveryException("SMTP delivery failed: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String mode() {
        return "SMTP";
    }

    @Override
    public boolean deliversRealMail() {
        return true;
    }
}
