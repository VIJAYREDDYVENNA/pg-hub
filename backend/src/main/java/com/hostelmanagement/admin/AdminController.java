package com.hostelmanagement.admin;

import com.hostelmanagement.admin.AdminService.ActivityRow;
import com.hostelmanagement.admin.AdminService.CreateOwnerRequest;
import com.hostelmanagement.admin.AdminService.Dashboard;
import com.hostelmanagement.admin.AdminService.HostelRow;
import com.hostelmanagement.admin.AdminService.OwnerDetail;
import com.hostelmanagement.admin.AdminService.OwnerRow;
import com.hostelmanagement.admin.AdminService.ResetPasswordRequest;
import com.hostelmanagement.admin.AdminService.SettingsRequest;
import com.hostelmanagement.admin.AdminService.StatusRequest;
import com.hostelmanagement.admin.AdminService.UpdateOwnerRequest;
import com.hostelmanagement.common.PageResponse;
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
import java.util.Map;

@Tag(name = "Platform administration (super admin)")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private final AdminService service;

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        return service.dashboard();
    }

    @GetMapping("/owners")
    public List<OwnerRow> owners() {
        return service.owners();
    }

    @PostMapping("/owners")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerRow createOwner(@Valid @RequestBody CreateOwnerRequest req) {
        return service.createOwner(req);
    }

    @GetMapping("/owners/{id}")
    public OwnerDetail owner(@PathVariable Long id) {
        return service.owner(id);
    }

    @PutMapping("/owners/{id}")
    public OwnerRow updateOwner(@PathVariable Long id, @Valid @RequestBody UpdateOwnerRequest req) {
        return service.updateOwner(id, req);
    }

    @PutMapping("/owners/{id}/status")
    public OwnerRow setStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return service.setStatus(id, req);
    }

    @PostMapping("/owners/{id}/reset-password")
    public Map<String, String> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest req) {
        service.resetOwnerPassword(id, req.newPassword());
        return Map.of("message", "Password reset. The owner must sign in again.");
    }

    @GetMapping("/hostels")
    public List<HostelRow> hostels() {
        return service.hostels();
    }

    @GetMapping("/activity")
    public PageResponse<ActivityRow> activity(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "25") int size) {
        return service.activityPage(page, size);
    }

    @GetMapping("/settings")
    public Map<String, String> settings() {
        return service.settings();
    }

    @PutMapping("/settings")
    public Map<String, String> updateSettings(@Valid @RequestBody SettingsRequest req) {
        return service.updateSettings(req);
    }
}
