package com.hostelmanagement.owner;

import com.hostelmanagement.owner.dto.OwnerSettingsDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Owner settings")
@RestController
@RequestMapping("/api/owners/settings")
@RequiredArgsConstructor
public class OwnerSettingsController {

    private final OwnerSettingsService service;

    @GetMapping
    public OwnerSettingsDto get() {
        return service.current();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public OwnerSettingsDto update(@Valid @RequestBody OwnerSettingsDto dto) {
        return service.update(dto);
    }
}
