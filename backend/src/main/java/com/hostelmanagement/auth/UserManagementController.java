package com.hostelmanagement.auth;

import com.hostelmanagement.auth.UserManagementService.CreateUserRequest;
import com.hostelmanagement.auth.UserManagementService.PermissionDto;
import com.hostelmanagement.auth.UserManagementService.ResetUserPasswordRequest;
import com.hostelmanagement.auth.UserManagementService.RoleDto;
import com.hostelmanagement.auth.UserManagementService.UpdateUserRequest;
import com.hostelmanagement.auth.UserManagementService.UserDto;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Users, roles and permissions")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
public class UserManagementController {

    private final UserManagementService service;

    @GetMapping
    public List<UserDto> list() {
        return service.list();
    }

    @GetMapping("/roles")
    public List<RoleDto> roles() {
        return service.roles();
    }

    @GetMapping("/permissions")
    public List<PermissionDto> permissions() {
        return service.permissions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody CreateUserRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/reset-password")
    public Map<String, String> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetUserPasswordRequest req) {
        service.resetPassword(id, req.newPassword());
        return Map.of("message", "Password reset. The user must sign in again.");
    }
}
