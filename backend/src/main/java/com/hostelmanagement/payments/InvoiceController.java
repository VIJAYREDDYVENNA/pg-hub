package com.hostelmanagement.payments;

import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.payments.InvoiceService.AdjustmentRequest;
import com.hostelmanagement.payments.InvoiceService.ManualInvoiceRequest;
import com.hostelmanagement.payments.dto.FinanceDtos.InvoiceDto;
import com.hostelmanagement.security.HostelAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
import java.util.Map;

@Tag(name = "Invoices")
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_PAYMENT_VIEW')")
public class InvoiceController {

    private final InvoiceService service;
    private final PaymentQueries queries;
    private final HostelAccessService hostelAccess;
    private final AppClock clock;

    @Operation(summary = "Search invoices. status: OPEN, DUE_TODAY, OVERDUE, PENDING, PARTIALLY_PAID, PAID, WAIVED, CANCELLED")
    @GetMapping
    public PageResponse<InvoiceDto> list(@RequestParam(required = false) Long hostelId,
                                         @RequestParam(required = false) Long residentId,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return queries.searchInvoices(hostelAccess.scope(hostelId), residentId, status, type, dueFrom, dueTo, q,
                clock.today(), page, size);
    }

    @GetMapping("/{id}")
    public InvoiceDto get(@PathVariable Long id) {
        service.getAccessible(id);
        return dto(id);
    }

    @Operation(summary = "Create an ad-hoc charge invoice (e.g. electricity, laundry)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_PAYMENT_COLLECT')")
    public InvoiceDto create(@Valid @RequestBody ManualInvoiceRequest req) {
        return dto(service.createManual(req).getId());
    }

    @Operation(summary = "Generate all due rent invoices now (also runs automatically every night)")
    @PostMapping("/generate-rent")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_COLLECT')")
    public Map<String, Integer> generateRent(@RequestParam(required = false) Long hostelId) {
        return Map.of("created", service.generateDueRentForCurrentUser(hostelId));
    }

    @PostMapping("/{id}/adjustments")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_ADJUST')")
    public InvoiceDto adjust(@PathVariable Long id, @Valid @RequestBody AdjustmentRequest req) {
        service.addAdjustment(id, req);
        return dto(id);
    }

    @PostMapping("/{id}/waive")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_ADJUST')")
    public InvoiceDto waive(@PathVariable Long id, @RequestBody Map<String, String> body) {
        service.waive(id, reason(body));
        return dto(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PERM_PAYMENT_ADJUST')")
    public InvoiceDto cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        service.cancel(id, reason(body));
        return dto(id);
    }

    private InvoiceDto dto(Long id) {
        return queries.invoice(id, clock.today());
    }

    static String reason(Map<String, String> body) {
        String r = body == null ? null : body.get("reason");
        if (r == null || r.isBlank()) {
            throw ApiException.badRequest("Please enter a reason");
        }
        return r.length() > 400 ? r.substring(0, 400) : r.trim();
    }
}
