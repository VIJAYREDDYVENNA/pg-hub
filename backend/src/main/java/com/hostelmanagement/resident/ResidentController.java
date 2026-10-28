package com.hostelmanagement.resident;

import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.common.files.FileStorageService.FileDto;
import com.hostelmanagement.resident.dto.ResidentDtos.ConsentRequest;
import com.hostelmanagement.resident.dto.ResidentDtos.ResidentProfile;
import com.hostelmanagement.resident.dto.ResidentDtos.ResidentRequest;
import com.hostelmanagement.resident.dto.ResidentDtos.ResidentSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Residents")
@RestController
@RequestMapping("/api/residents")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_RESIDENT_VIEW')")
public class ResidentController {

    private final ResidentService service;

    @Operation(summary = "Search residents. status: REGISTERED, ACTIVE, CHECKED_OUT, INACTIVE, VACATING_SOON")
    @GetMapping
    public PageResponse<ResidentSummary> list(@RequestParam(required = false) Long hostelId,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String sort) {
        return service.list(hostelId, status, q, page, size, sort);
    }

    @GetMapping("/{id}")
    public ResidentProfile get(@PathVariable Long id) {
        return service.profile(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_RESIDENT_MANAGE')")
    public ResidentProfile create(@Valid @RequestBody ResidentRequest req) {
        return service.profile(service.create(req).getId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_RESIDENT_MANAGE')")
    public ResidentProfile update(@PathVariable Long id, @Valid @RequestBody ResidentRequest req) {
        service.update(id, req);
        return service.profile(id);
    }

    @PutMapping("/{id}/whatsapp-consent")
    @PreAuthorize("hasAuthority('PERM_RESIDENT_MANAGE')")
    public ResidentProfile consent(@PathVariable Long id, @RequestBody ConsentRequest req) {
        service.setConsent(id, req.whatsappOptIn());
        return service.profile(id);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PERM_RESIDENT_MANAGE')")
    public ResidentProfile deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return service.profile(id);
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_RESIDENT_MANAGE')")
    public ResidentProfile photo(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        service.uploadPhoto(id, file);
        return service.profile(id);
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_RESIDENT_MANAGE')")
    public FileDto document(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                            @RequestParam(required = false) String category) {
        return service.uploadDocument(id, file, category);
    }
}
