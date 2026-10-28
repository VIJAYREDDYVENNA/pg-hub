package com.hostelmanagement.maintenance;

import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.common.files.FileStorageService.FileDto;
import com.hostelmanagement.maintenance.MaintenanceService.MaintenanceDto;
import com.hostelmanagement.maintenance.MaintenanceService.MaintenanceRequestDto;
import com.hostelmanagement.maintenance.MaintenanceService.StatusChangeRequest;
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

@Tag(name = "Maintenance")
@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_MAINTENANCE_MANAGE')")
public class MaintenanceController {

    private final MaintenanceService service;

    @Operation(summary = "List requests. status: OPEN, ASSIGNED, IN_PROGRESS, ON_HOLD, RESOLVED, CLOSED or ACTIVE (not resolved/closed)")
    @GetMapping
    public PageResponse<MaintenanceDto> list(@RequestParam(required = false) Long hostelId,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String priority,
                                             @RequestParam(required = false) Long roomId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.list(hostelId, status, priority, roomId, page, size);
    }

    @GetMapping("/{id}")
    public MaintenanceDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MaintenanceDto create(@Valid @RequestBody MaintenanceRequestDto req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public MaintenanceDto update(@PathVariable Long id, @Valid @RequestBody MaintenanceRequestDto req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/status")
    public MaintenanceDto status(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest req) {
        return service.changeStatus(id, req);
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileDto image(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return service.uploadImage(id, file);
    }
}
