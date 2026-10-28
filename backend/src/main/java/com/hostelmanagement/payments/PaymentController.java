package com.hostelmanagement.payments;

import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.hostel.Hostel;
import com.hostelmanagement.hostel.HostelRepository;
import com.hostelmanagement.owner.OwnerRepository;
import com.hostelmanagement.payments.PaymentService.DepositAdjustRequest;
import com.hostelmanagement.payments.PaymentService.RecordPaymentRequest;
import com.hostelmanagement.payments.PaymentService.RefundRequest;
import com.hostelmanagement.payments.dto.FinanceDtos.FinanceSummary;
import com.hostelmanagement.payments.dto.FinanceDtos.PaymentDto;
import com.hostelmanagement.payments.dto.FinanceDtos.ReceiptDto;
import com.hostelmanagement.payments.dto.FinanceDtos.RefundDto;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Payments")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_PAYMENT_VIEW')")
public class PaymentController {

    private final PaymentService service;
    private final PaymentQueries queries;
    private final ReceiptPdfService pdfService;
    private final HostelAccessService hostelAccess;
    private final HostelRepository hostelRepository;
    private final OwnerRepository ownerRepository;
    private final AppClock clock;

    @GetMapping
    public PageResponse<PaymentDto> list(@RequestParam(required = false) Long hostelId,
                                         @RequestParam(required = false) Long residentId,
                                         @RequestParam(required = false) String method,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return queries.searchPayments(hostelAccess.scope(hostelId), residentId, method, from, to, q, page, size);
    }

    @Operation(summary = "Collection dashboard: today's collections, due today, overdue, outstanding, trends")
    @GetMapping("/summary")
    public FinanceSummary summary(@RequestParam(required = false) Long hostelId) {
        return queries.summary(hostelAccess.scope(hostelId), clock.today());
    }

    @GetMapping("/{id}")
    public PaymentDto get(@PathVariable Long id) {
        service.getAccessible(id);
        return queries.payment(id);
    }

    @Operation(summary = "Record money received from a resident (applied oldest invoice first unless allocations given)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_PAYMENT_COLLECT')")
    public PaymentDto collect(@Valid @RequestBody RecordPaymentRequest req) {
        try {
            return queries.payment(service.collect(req).getId());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Two identical submissions raced and the unique idempotency key rejected the second: return the first payment
            return service.findByIdempotencyKey(req.idempotencyKey()).map(p -> queries.payment(p.getId())).orElseThrow(() -> e);
        }
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_ADJUST')")
    public PaymentDto reverse(@PathVariable Long id, @RequestBody Map<String, String> body) {
        service.reverse(id, InvoiceController.reason(body));
        return queries.payment(id);
    }

    @GetMapping("/{id}/receipt")
    public ReceiptDto receipt(@PathVariable Long id) {
        service.getAccessible(id);
        return buildReceipt(id);
    }

    @GetMapping(value = "/{id}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> receiptPdf(@PathVariable Long id) {
        service.getAccessible(id);
        ReceiptDto r = buildReceipt(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("receipt-" + r.payment().receiptNumber() + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfService.render(r));
    }

    @GetMapping("/refunds")
    public List<RefundDto> refunds(@RequestParam(required = false) Long hostelId,
                                   @RequestParam(required = false) Long residentId) {
        return queries.refunds(hostelAccess.scope(hostelId), residentId);
    }

    @PostMapping("/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_PAYMENT_ADJUST')")
    public Map<String, Long> refund(@Valid @RequestBody RefundRequest req) {
        return Map.of("refundId", service.refund(req).getId());
    }

    @Operation(summary = "Settle outstanding dues from the held security deposit")
    @PostMapping("/deposit-adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_PAYMENT_ADJUST')")
    public PaymentDto adjustDeposit(@Valid @RequestBody DepositAdjustRequest req) {
        return queries.payment(service.adjustDeposit(req).getId());
    }

    private ReceiptDto buildReceipt(Long id) {
        PaymentDto p = queries.payment(id);
        Hostel h = hostelRepository.findById(p.hostelId()).orElseThrow();
        String address = h.getAddress() + ", " + h.getCity() + ", " + h.getState() + " - " + h.getPinCode();
        String contact = h.getContactNumber() == null ? null : "Phone: " + h.getContactNumber();
        String owner = ownerRepository.findById(CurrentUser.get().ownerId()).map(o -> o.getName()).orElse(null);
        return new ReceiptDto(p, address, contact, owner);
    }
}
