package com.hostelmanagement.payments.online;

import com.hostelmanagement.payments.online.OnlinePaymentDtos.AttemptDto;
import com.hostelmanagement.payments.online.OnlinePaymentDtos.ConfirmRequest;
import com.hostelmanagement.payments.online.OnlinePaymentDtos.CreateOrderRequest;
import com.hostelmanagement.payments.online.OnlinePaymentDtos.OrderDto;
import com.hostelmanagement.payments.online.OnlinePaymentDtos.PublicInvoiceDto;
import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.files.FileAttachment;
import com.hostelmanagement.common.files.FileStorageService;
import com.hostelmanagement.payments.Invoice;
import com.hostelmanagement.payments.InvoiceRepository;
import com.hostelmanagement.security.LoginRateLimiter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated endpoints behind a pay link. The link token is the only credential: it selects the invoice, and
 * every attempt touched here must have been created through that same link.
 */
@Tag(name = "Public payment links")
@RestController
@RequestMapping("/api/public/pay/{token}")
@RequiredArgsConstructor
public class PublicPayController {

    private static final int REQUESTS_PER_MINUTE = 30;

    private final PaymentLinkService linkService;
    private final OnlinePaymentService service;
    private final LoginRateLimiter rateLimiter;
    private final InvoiceRepository invoiceRepository;
    private final com.hostelmanagement.paymentmethods.PaymentMethodService paymentMethods;
    private final FileStorageService files;

    @Operation(summary = "Invoice summary for the pay page")
    @GetMapping
    public PublicInvoiceDto invoice(@PathVariable String token, HttpServletRequest http) {
        return linkService.describe(link(token, http));
    }

    @Operation(summary = "The owner's UPI QR picture, if the invoice can still be paid manually")
    @GetMapping("/qr")
    public ResponseEntity<Resource> qr(@PathVariable String token, HttpServletRequest http) {
        PaymentLink link = link(token, http);
        Invoice invoice = invoiceRepository.findById(link.getInvoiceId()).orElseThrow(() -> ApiException.notFound("Payment link"));
        if (!invoice.isOpen() || invoice.balance().signum() <= 0) {
            throw ApiException.notFound("QR code");
        }
        FileAttachment f = paymentMethods.qrFile(invoice.getOwnerId(), invoice.getHostelId())
                .orElseThrow(() -> ApiException.notFound("QR code"));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.getContentType()))
                .header("X-Content-Type-Options", "nosniff").header("Cache-Control", "no-store")
                .body(files.load(f));
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto createOrder(@PathVariable String token, @Valid @RequestBody CreateOrderRequest req, HttpServletRequest http) {
        return service.createOrderForLink(link(token, http), req.amount());
    }

    @PostMapping("/confirm")
    public AttemptDto confirm(@PathVariable String token, @Valid @RequestBody ConfirmRequest req, HttpServletRequest http) {
        PaymentLink link = link(token, http);
        return service.confirm(service.attemptForLinkByOrder(link, req.providerOrderId()), req.fields());
    }

    @PostMapping("/attempts/{attemptId}/refresh")
    public AttemptDto refresh(@PathVariable String token, @PathVariable Long attemptId, HttpServletRequest http) {
        PaymentLink link = link(token, http);
        return service.refresh(service.attemptForLink(link, attemptId));
    }

    private PaymentLink link(String token, HttpServletRequest http) {
        rateLimiter.check("pay:" + http.getRemoteAddr(), REQUESTS_PER_MINUTE);
        return linkService.resolve(token);
    }
}
