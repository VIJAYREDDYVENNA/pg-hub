package com.hostelmanagement.mail;

import com.hostelmanagement.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Chooses the e-mail implementation from {@code app.mail.*}. Misconfiguration fails start-up with a precise message
 * rather than silently dropping password-reset mail.
 *
 * <ul>
 *   <li>{@code SMTP}: validated (host, from address, TLS mode, credentials) and used as configured.</li>
 *   <li>{@code LOG} in dev/test: e-mails are logged, never sent.</li>
 *   <li>{@code LOG} under {@code prod}: refused as a delivery channel; the application starts but sends nothing (a reset
 *       link in a log file would be a credential leak) and says so on start-up.</li>
 * </ul>
 */
@Slf4j
@Configuration
public class MailConfig {

    private static final Pattern ADDRESS = Pattern.compile("^[^@\\s<>]+@[^@\\s<>]+\\.[^@\\s<>]+$");
    private static final Set<String> TLS_MODES = Set.of("NONE", "STARTTLS", "SSL");
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    @Bean
    public EmailService emailService(AppProperties props, Environment env) {
        AppProperties.Mail cfg = props.mail();
        String mode = cfg.mode() == null ? "" : cfg.mode().trim().toUpperCase(Locale.ROOT);
        boolean prod = env.acceptsProfiles(Profiles.of("prod"));
        return switch (mode) {
            case "SMTP" -> smtp(cfg);
            case "LOG" -> {
                if (prod) {
                    log.warn("MAIL_MODE=LOG is not allowed with the 'prod' profile: password-reset e-mails will NOT be sent. "
                            + "Set MAIL_MODE=SMTP and the MAIL_* variables (see docs/EMAIL.md).");
                    yield new DisabledEmailService();
                }
                log.info("E-mail is in LOG mode: messages are written to the log and nothing is sent");
                yield new LogEmailService();
            }
            default -> throw new IllegalStateException("Unsupported app.mail.mode '" + cfg.mode() + "'. Use LOG or SMTP.");
        };
    }

    static EmailService smtp(AppProperties.Mail cfg) {
        if (blank(cfg.host())) {
            throw new IllegalStateException("MAIL_MODE=SMTP requires MAIL_HOST (your SMTP server).");
        }
        if (cfg.port() < 1 || cfg.port() > 65535) {
            throw new IllegalStateException("MAIL_PORT must be between 1 and 65535 but was " + cfg.port());
        }
        if (blank(cfg.from()) || !ADDRESS.matcher(cfg.from().trim()).matches()) {
            throw new IllegalStateException("MAIL_FROM must be a valid sender address, e.g. no-reply@yourdomain.in");
        }
        String tls = cfg.tls() == null ? "" : cfg.tls().trim().toUpperCase(Locale.ROOT);
        if (!TLS_MODES.contains(tls)) {
            throw new IllegalStateException("MAIL_TLS must be NONE, STARTTLS or SSL but was '" + cfg.tls() + "'");
        }
        if (cfg.auth() && (blank(cfg.username()) || blank(cfg.password()))) {
            throw new IllegalStateException("MAIL_AUTH is on, so MAIL_USERNAME and MAIL_PASSWORD are both required "
                    + "(set MAIL_AUTH=false for a server that needs no login).");
        }
        if (cfg.auth() && "NONE".equals(tls) && !LOCAL_HOSTS.contains(cfg.host().trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Refusing to send the SMTP password over an unencrypted connection to "
                    + cfg.host() + ". Use MAIL_TLS=STARTTLS (port 587) or SSL (port 465).");
        }
        if (cfg.timeoutMs() < 1000) {
            throw new IllegalStateException("MAIL_TIMEOUT_MS must be at least 1000");
        }
        log.info("E-mail will be sent through SMTP {}:{} (TLS: {}, login: {})", cfg.host(), cfg.port(), tls, cfg.auth() ? "yes" : "no");
        return new SmtpEmailService(new AppProperties.Mail("SMTP", cfg.host().trim(), cfg.port(), cfg.username(), cfg.password(),
                cfg.from().trim(), blank(cfg.fromName()) ? "HostelHub" : cfg.fromName().trim(), tls, cfg.auth(), cfg.timeoutMs()));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
