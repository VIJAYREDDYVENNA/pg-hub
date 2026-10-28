package com.hostelmanagement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("Asia/Kolkata") String timeZone,
        @DefaultValue Cors cors,
        @DefaultValue Security security,
        @DefaultValue("http://localhost:3000") String frontendBaseUrl,
        @DefaultValue("./data/uploads") String uploadDir,
        @DefaultValue Seed seed,
        @DefaultValue SuperAdmin superAdmin,
        @DefaultValue WhatsApp whatsapp,
        @DefaultValue Payment payment,
        @DefaultValue Notifications notifications,
        @DefaultValue Mail mail,
        @DefaultValue Reminders reminders) {

    /**
     * Automated rent reminders. Each owner must also switch them on in Settings. After the due date a reminder repeats every
     * {@code overdueIntervalDays} days, at most {@code overdueMax} times.
     */
    public record Reminders(@DefaultValue("true") boolean enabled, @DefaultValue("3") int overdueIntervalDays,
                            @DefaultValue("4") int overdueMax) {
    }

    /**
     * Outgoing e-mail (password reset). {@code mode} LOG writes the e-mail to the log instead of sending it (development
     * only); SMTP sends through any standard SMTP server. Nothing is provider specific: host, port, TLS and credentials
     * are all settings.
     */
    public record Mail(@DefaultValue("LOG") String mode, String host, @DefaultValue("587") int port, String username,
                       String password, String from, @DefaultValue("HostelHub") String fromName,
                       @DefaultValue("STARTTLS") String tls, @DefaultValue("true") boolean auth,
                       @DefaultValue("10000") int timeoutMs) {
    }

    /** Notification housekeeping. Notifications are deleted once they are {@code retentionDays} old, read or not. */
    public record Notifications(@DefaultValue("30") int retentionDays, @DefaultValue("5000") int deleteBatchSize) {
    }

    public record Cors(@DefaultValue("http://localhost:3000") List<String> allowedOrigins) {
    }

    public record Security(String jwtSecret,
                           @DefaultValue("30") int accessTokenMinutes,
                           @DefaultValue("7") int refreshTokenDays,
                           @DefaultValue("false") boolean refreshCookieSecure,
                           @DefaultValue("10") int loginRateLimitPerMinute,
                           /** Base64 of 32 random bytes; encrypts bank account numbers at rest. Optional in dev, required in prod to store them. */
                           String dataKey) {
    }

    public record Seed(@DefaultValue("false") boolean enabled) {
    }

    /** Bootstrap platform administrator, created on start-up when no SUPER_ADMIN exists yet. */
    public record SuperAdmin(String email, String password, @DefaultValue("Platform Administrator") String fullName) {
    }

    public record WhatsApp(@DefaultValue("MOCK") String mode,
                           @DefaultValue("v21.0") String apiVersion,
                           String phoneNumberId,
                           String accessToken,
                           String appSecret,
                           String webhookVerifyToken,
                           @DefaultValue("91") String defaultCountryCode,
                           @DefaultValue("3") int maxAttempts) {

        public boolean isMock() {
            return !"META".equalsIgnoreCase(mode);
        }
    }

    /**
     * Online payment collection. {@code provider} selects the gateway adapter (see PaymentGatewayConfig);
     * only the settings of the selected provider are used.
     */
    public record Payment(@DefaultValue("mock") String provider,
                          @DefaultValue("INR") String currency,
                          @DefaultValue("100") BigDecimal minPartialAmount,
                          @DefaultValue("7") int linkExpiryDays,
                          @DefaultValue Razorpay razorpay) {

        public record Razorpay(String keyId, String keySecret, String webhookSecret,
                               @DefaultValue("https://api.razorpay.com/v1") String baseUrl) {
        }
    }
}
