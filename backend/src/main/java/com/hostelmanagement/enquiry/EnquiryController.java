package com.hostelmanagement.enquiry;

import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.enquiry.EnquiryService.ConvertRequest;
import com.hostelmanagement.enquiry.EnquiryService.EnquiryDto;
import com.hostelmanagement.enquiry.EnquiryService.EnquiryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.Map;

@Tag(name = "Enquiries")
@RestController
@RequestMapping("/api/enquiries")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_ENQUIRY_MANAGE')")
public class EnquiryController {

    private final EnquiryService service;

    @GetMapping
    public PageResponse<EnquiryDto> list(@RequestParam(required = false) Long hostelId,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.list(hostelId, status, q, page, size);
    }

    @GetMapping("/{id}")
    public EnquiryDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnquiryDto create(@Valid @RequestBody EnquiryRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public EnquiryDto update(@PathVariable Long id, @Valid @RequestBody EnquiryRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Create a registered resident from the enquiry (then admit via /api/admissions)")
    @PostMapping("/{id}/convert")
    @PreAuthorize("hasAuthority('PERM_RESIDENT_MANAGE')")
    public Map<String, Long> convert(@PathVariable Long id, @Valid @RequestBody ConvertRequest req) {
        return Map.of("residentId", service.convert(id, req));
    }
}
