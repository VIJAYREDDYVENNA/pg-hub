package com.hostelmanagement.payments.online;

import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.config.AppProperties;
import com.hostelmanagement.hostel.HostelRepository;
import com.hostelmanagement.payments.Invoice;
import com.hostelmanagement.payments.InvoiceRepository;
import com.hostelmanagement.payments.InvoiceService;
import com.hostelmanagement.payments.online.OnlinePaymentDtos.PaymentLinkDto;
import com.hostelmanagement.payments.online.OnlinePaymentDtos.PublicInvoiceDto;
import com.hostelmanagement.resident.ResidentRepository;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Invoice-specific pay links. The token is 256 random bits, shown once, and only its SHA-256 is stored, so a database
 * leak does not leak usable links. A link authorises exactly one thing: paying that one invoice.
 */
@Service
@RequiredArgsConstructor
public class PaymentLinkService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentLinkRepository links;
    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final HostelRepository hostelRepository;
    private final ResidentRepository residentRepository;
    private final OnlinePaymentService onlinePayments;
    private final AuditService auditService;
    private final AppProperties props;
    private final com.hostelmanagement.subscription.EntitlementService entitlements;
    private final AppClock clock;
    private final com.hostelmanagement.paymentmethods.PaymentMethodService paymentMethods;

    /** Creates a fresh link for an invoice the caller may access; earlier links for it stop working. */
    @Transactional
    public PaymentLinkDto create(Long invoiceId) {
        AuthUser user = CurrentUser.get();
        Invoice invoice = invoiceService.getAccessible(invoiceId);
        entitlements.require(invoice.getOwnerId(), com.hostelmanagement.subscription.Features.ONLINE_PAYMENTS);
        paymentMethods.requireOnlineAccepted(invoice.getOwnerId(), invoice.getHostelId());
        if (!invoice.isOpen() || invoice.balance().signum() <= 0) {
            throw ApiException.conflict("This invoice has no outstanding balance");
        }
        Instant now = clock.now();
        links.revokeActiveForInvoice(invoiceId, now);
        String token = newToken();
        PaymentLink link = new PaymentLink();
        link.setOwnerId(invoice.getOwnerId());
        link.setHostelId(invoice.getHostelId());
        link.setInvoiceId(invoice.getId());
        link.setTokenHash(sha256(token));
        link.setExpiresAt(now.plus(Duration.ofDays(Math.max(1, props.payment().linkExpiryDays()))));
        link.setCreatedBy(user.userId());
        links.save(link);
        auditService.log("PAYMENT_LINK_CREATED", "INVOICE", invoiceId, "expires " + link.getExpiresAt());
        String base = props.frontendBaseUrl().replaceAll("/+$", "");
        return new PaymentLinkDto(base + "/pay/" + token, link.getExpiresAt());
    }

    /** Resolves a token to a usable link. Unknown, expired and revoked tokens are indistinguishable to the caller. */
    public PaymentLink resolve(String token) {
        if (token == null || token.length() < 20 || token.length() > 100) {
            throw invalid();
        }
        PaymentLink link = links.findByTokenHash(sha256(token)).orElseThrow(PaymentLinkService::invalid);
        if (!link.isUsable(clock.now())) {
            throw invalid();
        }
        return link;
    }

    public PublicInvoiceDto describe(PaymentLink link) {
        Invoice invoice = invoiceRepository.findById(link.getInvoiceId()).orElseThrow(PaymentLinkService::invalid);
        String hostel = hostelRepository.findById(invoice.getHostelId()).map(h -> h.getName()).orElse("");
        String first = residentRepository.findById(invoice.getResidentId())
                .map(r -> r.getFullName().trim().split("\\s+")[0]).orElse("");
        boolean payable = invoice.isOpen() && invoice.balance().signum() > 0;
        return new PublicInvoiceDto(invoice.getInvoiceNumber(), hostel, first, invoice.getInvoiceType(),
                invoice.getDueDate(), invoice.getTotalAmount(), invoice.getAmountPaid(), invoice.balance(),
                invoice.getStatus(), payable, link.getExpiresAt(), onlinePayments.config(),
                payable ? paymentMethods.manualInfo(invoice.getOwnerId(), invoice.getHostelId()) : null);
    }

    private static ApiException invalid() {
        return ApiException.notFound("Payment link");
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
