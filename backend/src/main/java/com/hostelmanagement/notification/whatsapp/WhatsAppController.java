package com.hostelmanagement.notification.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.config.AppProperties;
import com.hostelmanagement.enquiry.EnquiryRepository;
import com.hostelmanagement.notification.whatsapp.WhatsAppService.MessageDto;
import com.hostelmanagement.notification.whatsapp.WhatsAppService.SendCommand;
import com.hostelmanagement.notification.whatsapp.WhatsAppService.StatusDto;
import com.hostelmanagement.notification.whatsapp.WhatsAppService.TemplateDto;
import com.hostelmanagement.resident.ResidentService;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "WhatsApp")
@RestController
@RequestMapping("/api/whatsapp")
@RequiredArgsConstructor
public class WhatsAppController {

    private final WhatsAppService service;
    private final ResidentService residentService;
    private final EnquiryRepository enquiryRepository;
    private final HostelAccessService hostelAccess;
    private final AppProperties props;
    private final ObjectMapper objectMapper;

    public record SendRequest(Long residentId, Long enquiryId, @NotBlank String templateCode,
                              Map<String, String> variables, @Size(max = 1000) String customMessage) {
    }

    public record TemplateRequest(@NotBlank @Size(max = 1500) String body, @Size(max = 100) String providerTemplate,
                                  @Size(max = 10) String languageCode) {
    }

    @GetMapping("/status")
    public StatusDto status() {
        return service.status();
    }

    @GetMapping("/messages")
    @PreAuthorize("hasAuthority('PERM_NOTIFICATION_SEND') or hasAuthority('PERM_RESIDENT_VIEW')")
    public PageResponse<MessageDto> messages(@RequestParam(required = false) Long hostelId,
                                             @RequestParam(required = false) Long residentId,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.list(hostelId, residentId, status, page, size);
    }

    @Operation(summary = "Send a template or custom message to a resident (or an enquiry contact)")
    @PostMapping("/send")
    @PreAuthorize("hasAuthority('PERM_NOTIFICATION_SEND')")
    @Transactional
    public MessageDto send(@Valid @RequestBody SendRequest req) {
        AuthUser user = CurrentUser.get();
        String custom = "CUSTOM".equals(req.templateCode()) ? req.customMessage() : null;
        if ("CUSTOM".equals(req.templateCode()) && (custom == null || custom.isBlank())) {
            throw ApiException.badRequest("Enter the message to send");
        }
        WhatsAppMessage m;
        if (req.residentId() != null) {
            var r = residentService.getEntity(req.residentId());
            m = service.send(new SendCommand(user.ownerId(), r.getHostelId(), r.getId(), null, req.templateCode(),
                    req.variables(), custom, "RESIDENT", r.getId(), user.userId()));
        } else if (req.enquiryId() != null) {
            var e = enquiryRepository.findById(req.enquiryId())
                    .filter(x -> x.getOwnerId().equals(user.ownerId()))
                    .orElseThrow(() -> ApiException.notFound("Enquiry"));
            if (e.getHostelId() != null) {
                hostelAccess.checkEntity(e.getHostelId(), "Enquiry");
            }
            if (!e.isWhatsappOptIn()) {
                throw ApiException.conflict("This contact has not agreed to receive WhatsApp messages");
            }
            var vars = new java.util.HashMap<String, String>();
            vars.put("residentName", e.getFullName());
            if (req.variables() != null) {
                vars.putAll(req.variables());
            }
            m = service.send(new SendCommand(user.ownerId(), e.getHostelId(), null, e.getMobile(), req.templateCode(),
                    vars, custom, "ENQUIRY", e.getId(), user.userId()));
        } else {
            throw ApiException.badRequest("Select a resident or an enquiry");
        }
        return toDto(m);
    }

    @Operation(summary = "Send a payment reminder (or overdue notice) for an invoice")
    @PostMapping("/invoice-reminders/{invoiceId}")
    @PreAuthorize("hasAuthority('PERM_NOTIFICATION_SEND')")
    public MessageDto invoiceReminder(@PathVariable Long invoiceId) {
        return toDto(service.sendInvoiceReminder(invoiceId));
    }

    @PostMapping("/bulk-reminders")
    @PreAuthorize("hasAuthority('PERM_NOTIFICATION_SEND')")
    public Map<String, Object> bulk(@RequestParam(required = false) Long hostelId,
                                    @RequestParam(defaultValue = "false") boolean includeDueToday) {
        int[] r = service.bulkReminders(hostelId, includeDueToday);
        return Map.of("attempted", r[0], "skippedNoConsent", r[1], "simulated", service.status().simulated());
    }

    @GetMapping("/templates")
    public List<TemplateDto> templates() {
        return service.templates();
    }

    @PutMapping("/templates/{code}")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public TemplateDto saveTemplate(@PathVariable String code, @Valid @RequestBody TemplateRequest req) {
        return service.saveTemplate(code, req.body(), req.providerTemplate(), req.languageCode());
    }

    // ------------------------------------------------------------- Meta webhook (public, signature-verified)

    @GetMapping("/webhook")
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
                                         @RequestParam(name = "hub.verify_token", required = false) String token,
                                         @RequestParam(name = "hub.challenge", required = false) String challenge) {
        String expected = props.whatsapp().webhookVerifyToken();
        if ("subscribe".equals(mode) && expected != null && !expected.isBlank() && expected.equals(token)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(403).body("Forbidden");
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@RequestBody String payload,
                                          @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
        String secret = props.whatsapp().appSecret();
        if (secret == null || secret.isBlank() || !validSignature(payload, signature, secret)) {
            return ResponseEntity.status(403).body("Invalid signature");
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            for (JsonNode entry : root.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    for (JsonNode st : change.path("value").path("statuses")) {
                        String error = st.path("errors").isArray() && !st.path("errors").isEmpty()
                                ? st.path("errors").get(0).path("title").asText(null) : null;
                        service.applyStatus(st.path("id").asText(), st.path("status").asText(), error);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not process WhatsApp webhook payload", e);
        }
        return ResponseEntity.ok("ok");
    }

    static boolean validSignature(String payload, String header, String secret) {
        if (header == null || !header.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    header.substring(7).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private MessageDto toDto(WhatsAppMessage m) {
        return new MessageDto(m.getId(), m.getHostelId(), m.getResidentId(), null, m.getToPhone(), m.getTemplateCode(),
                m.getBody(), m.getStatus(), m.isSimulated(), m.getErrorMessage(), m.getAttempts(), m.getRelatedType(),
                m.getRelatedId(), m.getCreatedAt(), m.getSentAt(), m.getDeliveredAt());
    }
}
