package com.hostelmanagement.notification.whatsapp;

import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.PageRequests;
import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.config.AppProperties;
import com.hostelmanagement.hostel.Hostel;
import com.hostelmanagement.hostel.HostelRepository;
import com.hostelmanagement.notification.NotificationTemplate;
import com.hostelmanagement.notification.NotificationTemplateRepository;
import com.hostelmanagement.resident.Resident;
import com.hostelmanagement.resident.ResidentRepository;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends WhatsApp messages through the configured provider, enforcing resident consent, recording every message
 * (including simulated ones and those skipped for lack of consent) and retrying temporary failures.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppService {

    private static final String PARAM_SEP = "";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");

    private final WhatsAppProvider provider;
    private final WhatsAppMessageRepository repository;
    private final NotificationTemplateRepository templateRepository;
    private final ResidentRepository residentRepository;
    private final HostelRepository hostelRepository;
    private final HostelAccessService hostelAccess;
    private final AuditService auditService;
    private final NamedParameterJdbcTemplate jdbc;
    private final AppProperties props;
    private final AppClock clock;

    /**
     * @param residentId when set, the resident's mobile and consent are used; otherwise {@code toPhone} must be given
     *                   (e.g. enquiries, which carry their own consent flag checked by the caller).
     */
    public record SendCommand(Long ownerId, Long hostelId, Long residentId, String toPhone, String templateCode,
                              Map<String, String> variables, String customBody, String relatedType, Long relatedId,
                              Long userId) {
    }

    public record MessageDto(Long id, Long hostelId, Long residentId, String residentName, String toPhone,
                             String templateCode, String body, String status, boolean simulated, String errorMessage,
                             int attempts, String relatedType, Long relatedId, Instant createdAt, Instant sentAt,
                             Instant deliveredAt) {
    }

    public record TemplateDto(Long id, String code, String name, String body, String providerTemplate,
                              String languageCode, boolean active, boolean customized) {
    }

    public record StatusDto(String mode, boolean simulated, boolean configured, String note) {
    }

    public StatusDto status() {
        return provider.isSimulated()
                ? new StatusDto("MOCK", true, true,
                "Development mock mode: messages are recorded and shown as SIMULATED. Nothing is sent to WhatsApp.")
                : new StatusDto("META", false, provider.isConfigured(), provider.isConfigured()
                ? "WhatsApp Cloud API configured. Business-initiated messages require approved templates."
                : "WhatsApp Cloud API selected but credentials are missing.");
    }

    @Transactional
    public WhatsAppMessage send(SendCommand cmd) {
        Hostel hostel = cmd.hostelId() == null ? null : hostelRepository.findById(cmd.hostelId()).orElse(null);
        Resident resident = null;
        String phone = cmd.toPhone();
        if (cmd.residentId() != null) {
            resident = residentRepository.findById(cmd.residentId())
                    .filter(r -> r.getOwnerId().equals(cmd.ownerId()))
                    .orElseThrow(() -> ApiException.notFound("Resident"));
            phone = resident.getMobile();
        }
        if (phone == null || phone.isBlank()) {
            throw ApiException.badRequest("No mobile number available for this message");
        }
        NotificationTemplate template = cmd.templateCode() == null ? null : template(cmd.ownerId(), cmd.templateCode());
        Map<String, String> vars = new HashMap<>();
        if (resident != null) {
            vars.put("residentName", resident.getFullName());
        }
        if (hostel != null) {
            vars.put("hostelName", hostel.getName());
        }
        if (cmd.variables() != null) {
            vars.putAll(cmd.variables());
        }
        String body;
        List<String> params = new ArrayList<>();
        if (cmd.customBody() != null && !cmd.customBody().isBlank()) {
            body = cmd.customBody().trim();
            params.add(body);
        } else if (template != null) {
            body = render(template.getBody(), vars, params);
        } else {
            throw ApiException.badRequest("Choose a template or enter a message");
        }

        WhatsAppMessage m = new WhatsAppMessage();
        m.setOwnerId(cmd.ownerId());
        m.setHostelId(cmd.hostelId());
        m.setResidentId(cmd.residentId());
        m.setToPhone(normalize(phone));
        m.setTemplateCode(cmd.templateCode() == null ? "CUSTOM" : cmd.templateCode());
        m.setBody(body.length() > 2000 ? body.substring(0, 2000) : body);
        m.setSimulated(provider.isSimulated());
        m.setRelatedType(cmd.relatedType());
        m.setRelatedId(cmd.relatedId());
        m.setSentBy(cmd.userId());
        if (resident != null && !resident.isWhatsappOptIn()) {
            m.setStatus("SKIPPED_NO_CONSENT");
            m.setErrorMessage("Resident has not opted in to WhatsApp messages");
            return repository.save(m);
        }
        m.setStatus("QUEUED");
        repository.save(m);
        String providerTemplate = template == null || cmd.customBody() != null ? null : template.getProviderTemplate();
        if (providerTemplate != null) {
            m.setTemplateParams(String.join(PARAM_SEP, params));
        }
        attempt(m, new WhatsAppProvider.OutboundMessage(m.getToPhone(), m.getBody(), providerTemplate,
                template == null ? "en" : template.getLanguageCode(), params));
        return m;
    }

    /** Sends a reminder for an invoice (payment reminder or overdue notice based on its due date). */
    @Transactional
    public WhatsAppMessage sendInvoiceReminder(Long invoiceId) {
        AuthUser user = CurrentUser.get();
        Map<String, Object> inv = invoiceRow(invoiceId);
        if (!user.ownerId().equals(((Number) inv.get("owner_id")).longValue())) {
            throw ApiException.notFound("Invoice");
        }
        Long hostelId = ((Number) inv.get("hostel_id")).longValue();
        hostelAccess.checkEntity(hostelId, "Invoice");
        String status = (String) inv.get("status");
        if (!List.of("PENDING", "PARTIALLY_PAID", "OVERDUE").contains(status)) {
            throw ApiException.conflict("This invoice has no outstanding balance");
        }
        java.time.LocalDate due = ((java.sql.Date) inv.get("due_date")).toLocalDate();
        String code = due.isBefore(clock.today()) ? "OVERDUE_NOTICE" : "RENT_REMINDER";
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("amount", ((java.math.BigDecimal) inv.get("balance")).toPlainString());
        vars.put("dueDate", due.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")));
        vars.put("invoiceNumber", (String) inv.get("invoice_number"));
        vars.put("roomNumber", inv.get("room_number") == null ? "-" : (String) inv.get("room_number"));
        WhatsAppMessage m = send(new SendCommand(user.ownerId(), hostelId, ((Number) inv.get("resident_id")).longValue(),
                null, code, vars, null, "INVOICE", invoiceId, user.userId()));
        auditService.log("WHATSAPP_REMINDER", "INVOICE", invoiceId, m.getStatus() + (m.isSimulated() ? " (simulated)" : ""));
        return m;
    }

/** The invoice facts a reminder needs (balance, due date, room), without any access check: callers authorise. */
    private Map<String, Object> invoiceRow(Long invoiceId) {
        return jdbc.queryForMap("""
                SELECT i.id, i.owner_id, i.hostel_id, i.resident_id, i.invoice_number, i.due_date,
                       i.total_amount - i.amount_paid balance, i.status,
                       (SELECT r.room_number FROM bed_allocations ba JOIN beds b ON b.id = ba.bed_id
                          JOIN rooms r ON r.id = b.room_id WHERE ba.admission_id = i.admission_id
                          ORDER BY ba.end_date IS NULL DESC, ba.start_date DESC LIMIT 1) room_number
                FROM invoices i WHERE i.id = :id""", new MapSqlParameterSource("id", invoiceId));
    }

    /**
     * Reminder sent by the daily job rather than by a person. Uses the same templates as the manual reminder: the amount is
     * the <em>remaining</em> balance, so a part-paid invoice asks only for what is left. The caller has already checked the
     * invoice is open, the owner's switch and the plan; consent is enforced again by {@link #send}.
     */
    @Transactional
    public WhatsAppMessage sendAutomaticInvoiceReminder(Long invoiceId, boolean overdue) {
        Map<String, Object> inv = invoiceRow(invoiceId);
        java.time.LocalDate due = ((java.sql.Date) inv.get("due_date")).toLocalDate();
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("amount", ((java.math.BigDecimal) inv.get("balance")).toPlainString());
        vars.put("dueDate", due.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")));
        vars.put("invoiceNumber", (String) inv.get("invoice_number"));
        vars.put("roomNumber", inv.get("room_number") == null ? "-" : (String) inv.get("room_number"));
        return send(new SendCommand(((Number) inv.get("owner_id")).longValue(), ((Number) inv.get("hostel_id")).longValue(),
                ((Number) inv.get("resident_id")).longValue(), null, overdue ? "OVERDUE_NOTICE" : "RENT_REMINDER", vars, null,
                "INVOICE", invoiceId, null));
    }

    /** Sends reminders for all overdue (or due-today) invoices in scope. Returns [attempted, skippedNoConsent]. */
    @Transactional
    public int[] bulkReminders(Long hostelId, boolean includeDueToday) {
        List<Long> scope = hostelAccess.scope(hostelId);
        List<Long> ids = jdbc.queryForList("""
                SELECT i.id FROM invoices i WHERE i.hostel_id IN (:ids)
                AND i.status IN ('PENDING','PARTIALLY_PAID','OVERDUE') AND i.due_date """
                        + (includeDueToday ? "<=" : "<") + " :today ORDER BY i.due_date",
                new MapSqlParameterSource("ids", scope).addValue("today", java.sql.Date.valueOf(clock.today())),
                Long.class);
        int sent = 0, skipped = 0;
        for (Long id : ids) {
            WhatsAppMessage m = sendInvoiceReminder(id);
            if ("SKIPPED_NO_CONSENT".equals(m.getStatus())) {
                skipped++;
            } else {
                sent++;
            }
        }
        return new int[]{sent, skipped};
    }

    /** Announcement to all active residents with consent in the given hostels. Returns [attempted, skipped]. */
    @Transactional
    public int[] broadcastAnnouncement(AuthUser user, List<Long> hostelIds, String title, String message,
                                       Long announcementId) {
        int sent = 0, skipped = 0;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, hostel_id, whatsapp_opt_in FROM residents WHERE hostel_id IN (:ids) AND status = 'ACTIVE'""",
                new MapSqlParameterSource("ids", hostelIds));
        for (Map<String, Object> r : rows) {
            if (!Boolean.TRUE.equals(r.get("whatsapp_opt_in"))) {
                skipped++;
                continue;
            }
            send(new SendCommand(user.ownerId(), ((Number) r.get("hostel_id")).longValue(),
                    ((Number) r.get("id")).longValue(), null, "ANNOUNCEMENT", Map.of("title", title, "message", message),
                    null, "ANNOUNCEMENT", announcementId, user.userId()));
            sent++;
        }
        return new int[]{sent, skipped};
    }

    /** Retries temporary failures with exponential back-off. */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    @Transactional
    public void retryFailed() {
        for (WhatsAppMessage m : repository.dueForRetry(Instant.now())) {
            NotificationTemplate template = templateOrNull(m.getOwnerId(), m.getTemplateCode());
            boolean useTemplate = template != null && m.getTemplateParams() != null && template.getProviderTemplate() != null;
            attempt(m, new WhatsAppProvider.OutboundMessage(m.getToPhone(), m.getBody(),
                    useTemplate ? template.getProviderTemplate() : null,
                    template == null ? "en" : template.getLanguageCode(),
                    useTemplate ? List.of(m.getTemplateParams().split(PARAM_SEP, -1)) : List.of()));
        }
    }

    private void attempt(WhatsAppMessage m, WhatsAppProvider.OutboundMessage out) {
        m.setAttempts(m.getAttempts() + 1);
        WhatsAppProvider.SendResult result;
        try {
            result = provider.send(out);
        } catch (RuntimeException e) {
            log.error("WhatsApp provider error", e);
            result = WhatsAppProvider.SendResult.failed("Provider error: " + e.getClass().getSimpleName(), true);
        }
        if (result.success()) {
            m.setStatus(provider.isSimulated() ? "DELIVERED" : "SENT");
            m.setProviderMessageId(result.providerMessageId());
            m.setSentAt(Instant.now());
            if (provider.isSimulated()) {
                m.setDeliveredAt(Instant.now());
            }
            m.setErrorMessage(null);
            m.setNextAttemptAt(null);
        } else {
            m.setStatus("FAILED");
            m.setErrorMessage(result.error());
            int max = props.whatsapp().maxAttempts();
            m.setNextAttemptAt(result.transientFailure() && m.getAttempts() < max
                    ? Instant.now().plus(Duration.ofMinutes(2L << (m.getAttempts() - 1))) : null);
        }
    }

    /** Applies a delivery status update received from the provider webhook. */
    @Transactional
    public void applyStatus(String providerMessageId, String status, String error) {
        repository.findFirstByProviderMessageId(providerMessageId).ifPresent(m -> {
            switch (status) {
                case "sent" -> {
                    if ("QUEUED".equals(m.getStatus())) {
                        m.setStatus("SENT");
                    }
                }
                case "delivered" -> {
                    if (!"READ".equals(m.getStatus())) {
                        m.setStatus("DELIVERED");
                        m.setDeliveredAt(Instant.now());
                    }
                }
                case "read" -> m.setStatus("READ");
                case "failed" -> {
                    m.setStatus("FAILED");
                    m.setErrorMessage(error == null ? "Delivery failed" : error);
                }
                default -> log.debug("Ignoring WhatsApp status {}", status);
            }
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageDto> list(Long hostelId, Long residentId, String status, int page, int size) {
        AuthUser user = CurrentUser.get();
        if (hostelId != null) {
            hostelAccess.checkHostel(hostelId);
        }
        var pageable = PageRequests.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var result = repository.search(user.ownerId(), hostelAccess.scope((Long) null), hostelId, residentId,
                status == null || status.isBlank() || "ALL".equals(status) ? null : status, pageable);
        Map<Long, String> names = new HashMap<>();
        var rids = result.getContent().stream().map(WhatsAppMessage::getResidentId).filter(java.util.Objects::nonNull)
                .distinct().toList();
        residentRepository.findAllById(rids).forEach(r -> names.put(r.getId(), r.getFullName()));
        return PageResponse.of(result, m -> new MessageDto(m.getId(), m.getHostelId(), m.getResidentId(),
                names.get(m.getResidentId()), m.getToPhone(), m.getTemplateCode(), m.getBody(), m.getStatus(),
                m.isSimulated(), m.getErrorMessage(), m.getAttempts(), m.getRelatedType(), m.getRelatedId(),
                m.getCreatedAt(), m.getSentAt(), m.getDeliveredAt()));
    }

    @Transactional(readOnly = true)
    public List<TemplateDto> templates() {
        Long ownerId = CurrentUser.get().ownerId();
        Map<String, TemplateDto> byCode = new LinkedHashMap<>();
        for (NotificationTemplate t : templateRepository.visible(ownerId)) {
            boolean custom = t.getOwnerId() != null;
            if (custom || !byCode.containsKey(t.getCode())) {
                byCode.put(t.getCode(), new TemplateDto(t.getId(), t.getCode(), t.getName(), t.getBody(),
                        t.getProviderTemplate(), t.getLanguageCode(), t.isActive(), custom));
            }
        }
        return List.copyOf(byCode.values());
    }

    /** Creates or updates the owner's own version of a template code. */
    @Transactional
    public TemplateDto saveTemplate(String code, String body, String providerTemplate, String languageCode) {
        Long ownerId = CurrentUser.get().ownerId();
        NotificationTemplate base = template(ownerId, code);
        NotificationTemplate t = templateRepository.findByOwnerIdAndCodeAndChannel(ownerId, code, "WHATSAPP")
                .orElseGet(() -> {
                    NotificationTemplate n = new NotificationTemplate();
                    n.setOwnerId(ownerId);
                    n.setCode(code);
                    n.setName(base.getName());
                    n.setChannel("WHATSAPP");
                    return n;
                });
        t.setBody(body.trim());
        t.setProviderTemplate(providerTemplate == null || providerTemplate.isBlank() ? null : providerTemplate.trim());
        t.setLanguageCode(languageCode == null || languageCode.isBlank() ? "en" : languageCode.trim());
        templateRepository.save(t);
        auditService.log("TEMPLATE_UPDATED", "TEMPLATE", t.getId(), code);
        return new TemplateDto(t.getId(), t.getCode(), t.getName(), t.getBody(), t.getProviderTemplate(),
                t.getLanguageCode(), t.isActive(), true);
    }

    private NotificationTemplate template(Long ownerId, String code) {
        NotificationTemplate t = templateOrNull(ownerId, code);
        if (t == null || !t.isActive()) {
            throw ApiException.badRequest("Unknown or inactive message template: " + code);
        }
        return t;
    }

    private NotificationTemplate templateOrNull(Long ownerId, String code) {
        return templateRepository.findByOwnerIdAndCodeAndChannel(ownerId, code, "WHATSAPP")
                .or(() -> templateRepository.findByOwnerIdAndCodeAndChannel(null, code, "WHATSAPP"))
                .orElse(null);
    }

    /** Replaces {{name}} placeholders; collects values in order for provider template parameters. */
    static String render(String body, Map<String, String> vars, List<String> paramsOut) {
        Matcher m = PLACEHOLDER.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = vars.getOrDefault(m.group(1), "");
            paramsOut.add(value.isEmpty() ? "-" : value);
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Converts local numbers to international format without "+" (e.g. 9876543210 -> 919876543210). */
    String normalize(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 10) {
            digits = props.whatsapp().defaultCountryCode() + digits;
        }
        if (digits.startsWith("0") && digits.length() == 11) {
            digits = props.whatsapp().defaultCountryCode() + digits.substring(1);
        }
        return digits;
    }
}
