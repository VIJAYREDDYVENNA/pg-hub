package com.hostelmanagement.payments.online;

import com.hostelmanagement.payments.online.PaymentAttemptProcessor.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Receives gateway webhooks. The body is taken as raw bytes because signatures are computed over the exact bytes the
 * provider sent. Authentication is the provider signature, checked in the adapter before anything is parsed.
 */
@Tag(name = "Payment webhooks")
@RestController
@RequestMapping("/api/payments/webhook")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final OnlinePaymentService service;

    @Operation(summary = "Gateway webhook (signature verified, idempotent)")
    @PostMapping("/{provider}")
    public Map<String, String> receive(@PathVariable String provider, @RequestBody byte[] body, HttpServletRequest request) {
        Result result = service.handleWebhook(provider, body, headers(request));
        return Map.of("status", result.name());
    }

    private static Map<String, String> headers(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.put(name.toLowerCase(), request.getHeader(name));
        }
        return headers;
    }
}
