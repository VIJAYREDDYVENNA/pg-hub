package com.hostelmanagement.payments.online;

import com.hostelmanagement.payments.online.OnlinePaymentDtos.AttemptDto;
import com.hostelmanagement.payments.online.OnlinePaymentDtos.ConfirmRequest;
import com.hostelmanagement.payments.online.OnlinePaymentDtos.CreateOrderRequest;
import com.hostelmanagement.payments.online.OnlinePaymentDtos.GatewayConfigDto;
import com.hostelmanagement.payments.online.OnlinePaymentDtos.OrderDto;
import com.hostelmanagement.payments.online.OnlinePaymentDtos.PaymentLinkDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Staff-side online collection. Owner/hostel scoping is enforced in the service layer on every call. */
@Tag(name = "Online payments")
@RestController
@RequestMapping("/api/online-payments")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_PAYMENT_VIEW')")
public class OnlinePaymentController {

    private final OnlinePaymentService service;
    private final PaymentLinkService linkService;

    @Operation(summary = "Active payment provider and what it supports")
    @GetMapping("/config")
    public GatewayConfigDto config() {
        return service.config();
    }

    @Operation(summary = "Start a gateway checkout for (part of) an invoice")
    @PostMapping("/invoices/{invoiceId}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_PAYMENT_COLLECT')")
    public OrderDto createOrder(@PathVariable Long invoiceId, @Valid @RequestBody CreateOrderRequest req) {
        return service.createOrderForStaff(invoiceId, req.amount());
    }

    @Operation(summary = "Create a shareable pay link for an invoice (the token is shown once)")
    @PostMapping("/invoices/{invoiceId}/links")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_PAYMENT_COLLECT')")
    public PaymentLinkDto createLink(@PathVariable Long invoiceId) {
        return linkService.create(invoiceId);
    }

    @Operation(summary = "Gateway attempts for an invoice, including failed and pending ones")
    @GetMapping("/invoices/{invoiceId}/attempts")
    public List<AttemptDto> attempts(@PathVariable Long invoiceId) {
        return service.attemptsForInvoice(invoiceId);
    }

    @Operation(summary = "Verify the checkout result returned by the browser; the backend confirms it with the provider")
    @PostMapping("/confirm")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_COLLECT')")
    public AttemptDto confirm(@Valid @RequestBody ConfirmRequest req) {
        return service.confirm(service.attemptForStaffByOrder(req.providerOrderId()), req.fields());
    }

    @Operation(summary = "Ask the provider for the latest status of an attempt")
    @PostMapping("/attempts/{attemptId}/refresh")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_COLLECT')")
    public AttemptDto refresh(@PathVariable Long attemptId) {
        return service.refresh(service.attemptForStaff(attemptId));
    }
}
