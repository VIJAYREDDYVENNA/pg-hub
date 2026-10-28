package com.hostelmanagement.admission;

import com.hostelmanagement.admission.dto.AdmissionDtos.AdmissionDto;
import com.hostelmanagement.admission.dto.AdmissionDtos.AdmitRequest;
import com.hostelmanagement.admission.dto.AdmissionDtos.CheckoutRequest;
import com.hostelmanagement.admission.dto.AdmissionDtos.TransferRequest;
import com.hostelmanagement.admission.dto.AdmissionDtos.UpdateAdmissionRequest;
import com.hostelmanagement.common.PageResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Admissions & bed allocation")
@RestController
@RequestMapping("/api/admissions")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_RESIDENT_VIEW')")
public class AdmissionController {

    private final AdmissionService service;
    private final AdmissionQueries queries;
    private final HostelAccessService hostelAccess;

    @Operation(summary = "List admissions. type: ADMITTED (by admission date), CHECKED_OUT, ACTIVE")
    @GetMapping
    public PageResponse<AdmissionDto> list(@RequestParam(required = false) Long hostelId,
                                           @RequestParam(defaultValue = "ADMITTED") String type,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return queries.search(hostelAccess.scope(hostelId), type, from, to, page, size);
    }

    @GetMapping("/{id}")
    public AdmissionDto get(@PathVariable Long id) {
        service.getEntity(id);
        return queries.get(id);
    }

    @Operation(summary = "Admit a resident to a bed (creates allocation, deposit and first rent invoice)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_RESIDENT_MANAGE')")
    public AdmissionDto admit(@Valid @RequestBody AdmitRequest req) {
        return queries.get(service.admit(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_RESIDENT_MANAGE')")
    public AdmissionDto update(@PathVariable Long id, @Valid @RequestBody UpdateAdmissionRequest req) {
        service.update(id, req);
        return queries.get(id);
    }

    @Operation(summary = "Record a checkout; frees the bed and cancels unpaid rent invoices for later periods")
    @PostMapping("/{id}/checkout")
    @PreAuthorize("hasAuthority('PERM_RESIDENT_MANAGE')")
    public AdmissionDto checkout(@PathVariable Long id, @Valid @RequestBody CheckoutRequest req) {
        service.checkout(id, req);
        return queries.get(id);
    }

    @Operation(summary = "Move the resident to another bed (history is kept)")
    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasAuthority('PERM_RESIDENT_MANAGE')")
    public AdmissionDto transfer(@PathVariable Long id, @Valid @RequestBody TransferRequest req) {
        service.transfer(id, req);
        return queries.get(id);
    }
}
