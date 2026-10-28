package com.hostelmanagement.notification.whatsapp;

import com.hostelmanagement.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WhatsApp Business Cloud API (Meta Graph API). Business-initiated messages must use templates that have been
 * approved in WhatsApp Manager; when a template has a provider template name, the message is sent as a template
 * with body parameters in placeholder order, otherwise as a plain text message (only delivered inside the 24-hour
 * customer service window).
 */
@Slf4j
public class MetaCloudWhatsAppProvider implements WhatsAppProvider {

    private final AppProperties.WhatsApp cfg;
    private final RestClient client;

    public MetaCloudWhatsAppProvider(AppProperties.WhatsApp cfg) {
        this.cfg = cfg;
        this.client = RestClient.builder().baseUrl("https://graph.facebook.com").build();
    }

    @Override
    public boolean isSimulated() {
        return false;
    }

    @Override
    public boolean isConfigured() {
        return notBlank(cfg.phoneNumberId()) && notBlank(cfg.accessToken());
    }

    @Override
    public SendResult send(OutboundMessage m) {
        if (!isConfigured()) {
            return SendResult.failed("WhatsApp Cloud API credentials are not configured", false);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", m.toPhone());
        if (notBlank(m.providerTemplate())) {
            List<Map<String, Object>> params = new ArrayList<>();
            m.templateParameters().forEach(v -> params.add(Map.of("type", "text", "text", v)));
            Map<String, Object> template = new LinkedHashMap<>();
            template.put("name", m.providerTemplate());
            template.put("language", Map.of("code", m.languageCode() == null ? "en" : m.languageCode()));
            if (!params.isEmpty()) {
                template.put("components", List.of(Map.of("type", "body", "parameters", params)));
            }
            payload.put("type", "template");
            payload.put("template", template);
        } else {
            payload.put("type", "text");
            payload.put("text", Map.of("body", m.body()));
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = client.post()
                    .uri("/{version}/{phoneId}/messages", cfg.apiVersion(), cfg.phoneNumberId())
                    .header("Authorization", "Bearer " + cfg.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            Object messages = resp == null ? null : resp.get("messages");
            if (messages instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
                return SendResult.ok(String.valueOf(first.get("id")));
            }
            return SendResult.failed("Unexpected response from WhatsApp API", true);
        } catch (HttpStatusCodeException e) {
            int code = e.getStatusCode().value();
            boolean transientFailure = code == 429 || code >= 500;
            log.warn("WhatsApp API error {}: {}", code, e.getResponseBodyAsString());
            return SendResult.failed("WhatsApp API error " + code, transientFailure);
        } catch (ResourceAccessException e) {
            return SendResult.failed("Could not reach WhatsApp API: " + e.getMessage(), true);
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
