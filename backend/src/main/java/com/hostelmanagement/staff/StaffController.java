package com.hostelmanagement.staff;

import com.hostelmanagement.staff.StaffService.StaffDto;
import com.hostelmanagement.staff.StaffService.StaffRequest;
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

import java.util.List;

@Tag(name = "Staff")
@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService service;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE') or hasAuthority('PERM_MAINTENANCE_MANAGE')")
    public List<StaffDto> list(@RequestParam(required = false) Long hostelId) {
        return service.list(hostelId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
    public StaffDto create(@Valid @RequestBody StaffRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
    public StaffDto update(@PathVariable Long id, @Valid @RequestBody StaffRequest req) {
        return service.update(id, req);
    }
}
