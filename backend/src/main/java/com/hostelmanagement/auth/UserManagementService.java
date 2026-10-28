package com.hostelmanagement.auth;

import com.hostelmanagement.auth.dto.AuthDtos;
import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Owner-side management of staff logins, their roles, fine-grained permissions and hostel access.
 * A non-owner administrator can never grant permissions or hostels they do not have themselves.
 */
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserHostelAccessRepository accessRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final HostelAccessService hostelAccess;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final com.hostelmanagement.subscription.SubscriptionLimitService limits;

    public record CreateUserRequest(@NotBlank @Size(max = 150) String fullName,
                                    @NotBlank @Email @Size(max = 150) String email,
                                    @Pattern(regexp = "^$|^[+0-9 ]{7,20}$", message = "Enter a valid phone number") String phone,
                                    @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_REGEX, message = AuthDtos.PASSWORD_MESSAGE) String password,
                                    @NotNull @Pattern(regexp = "MANAGER|WARDEN|STAFF") String roleCode,
                                    Set<String> permissions,
                                    @NotNull Set<Long> hostelIds) {
    }

    public record UpdateUserRequest(@NotBlank @Size(max = 150) String fullName,
                                    @Pattern(regexp = "^$|^[+0-9 ]{7,20}$", message = "Enter a valid phone number") String phone,
                                    @NotNull @Pattern(regexp = "MANAGER|WARDEN|STAFF") String roleCode,
                                    @NotNull Set<String> permissions,
                                    @NotNull Set<Long> hostelIds,
                                    boolean enabled) {
    }

    public record ResetUserPasswordRequest(
            @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_REGEX, message = AuthDtos.PASSWORD_MESSAGE) String newPassword) {
    }

    public record UserDto(Long id, String fullName, String email, String phone, boolean enabled, Set<String> roles,
                          Set<String> permissions, List<Long> hostelIds, Instant lastLoginAt, Instant createdAt) {
    }

    public record RoleDto(String code, String name, String description, Set<String> defaultPermissions) {
    }

    public record PermissionDto(String code, String name, String category) {
    }

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        AuthUser me = CurrentUser.get();
        List<User> users = userRepository.findByOwnerIdOrderByFullName(me.ownerId());
        Map<Long, List<Long>> access = new HashMap<>();
        accessRepository.findByUserIdIn(users.stream().map(User::getId).toList())
                .forEach(a -> access.computeIfAbsent(a.getUserId(), k -> new java.util.ArrayList<>()).add(a.getHostelId()));
        Set<Long> visibleHostels = hostelAccess.accessibleHostelIds();
        return users.stream()
                // Non-owners only see users that share at least one of their hostels (and never owners' details beyond name)
                .filter(u -> me.isOwner() || u.getId().equals(me.userId())
                        || access.getOrDefault(u.getId(), List.of()).stream().anyMatch(visibleHostels::contains))
                .map(u -> toDto(userRepository.findWithAccessById(u.getId()).orElseThrow(),
                        access.getOrDefault(u.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleDto> roles() {
        return roleRepository.findAllByOrderByIdAsc().stream()
                .map(r -> new RoleDto(r.getCode(), r.getName(), r.getDescription(),
                        r.getDefaultPermissions().stream().map(Permission::getCode).collect(Collectors.toSet())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionDto> permissions() {
        return permissionRepository.findAllByOrderByCategoryAscIdAsc().stream()
                .map(p -> new PermissionDto(p.getCode(), p.getName(), p.getCategory())).toList();
    }

    @Transactional
    public UserDto create(CreateUserRequest req) {
        AuthUser me = CurrentUser.get();
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("A user with this email already exists");
        }
        Role role = roleRepository.findByCode(req.roleCode()).orElseThrow(() -> ApiException.badRequest("Unknown role"));
        Set<String> perms = req.permissions() == null || req.permissions().isEmpty()
                ? role.getDefaultPermissions().stream().map(Permission::getCode).collect(Collectors.toSet())
                : req.permissions();
        checkGrantable(me, perms, req.hostelIds());
        limits.assertCanEnableManager(me.ownerId());
        User u = new User();
        u.setOwnerId(me.ownerId());
        u.setFullName(req.fullName().trim());
        u.setEmail(email);
        u.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setPasswordChangedAt(Instant.now().minusSeconds(5));
        u.setRoles(new HashSet<>(Set.of(role)));
        u.setPermissions(new HashSet<>(permissionRepository.findByCodeIn(perms)));
        userRepository.save(u);
        req.hostelIds().forEach(h -> accessRepository.save(new UserHostelAccess(u.getId(), h)));
        auditService.log("USER_CREATED", "USER", u.getId(), email + " as " + role.getCode() + " perms=" + perms
                + " hostels=" + req.hostelIds());
        return toDto(u, List.copyOf(req.hostelIds()));
    }

    @Transactional
    public UserDto update(Long id, UpdateUserRequest req) {
        AuthUser me = CurrentUser.get();
        User u = editable(id, me);
        Role role = roleRepository.findByCode(req.roleCode()).orElseThrow(() -> ApiException.badRequest("Unknown role"));
        checkGrantable(me, req.permissions(), req.hostelIds());
        u.setFullName(req.fullName().trim());
        u.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        u.setRoles(new HashSet<>(Set.of(role)));
        u.setPermissions(new HashSet<>(permissionRepository.findByCodeIn(req.permissions())));
        boolean wasEnabled = u.isEnabled();
        if (!wasEnabled && req.enabled()) {
            limits.assertCanEnableManager(me.ownerId());
        }
        u.setEnabled(req.enabled());
        if (wasEnabled && !req.enabled()) {
            refreshTokenRepository.revokeAllForUser(u.getId(), Instant.now());
        }
        accessRepository.deleteByUserId(u.getId());
        accessRepository.flush();
        req.hostelIds().forEach(h -> accessRepository.save(new UserHostelAccess(u.getId(), h)));
        auditService.log("USER_UPDATED", "USER", u.getId(), "role=" + role.getCode() + " perms=" + req.permissions()
                + " hostels=" + req.hostelIds() + " enabled=" + req.enabled());
        return toDto(u, List.copyOf(req.hostelIds()));
    }

    /**
     * Setting someone else's password is the strongest form of impersonation, so only the account owner may do it.
     * Delegated managers cannot reset a peer's password even if the peer's access is a subset of their own.
     */
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        AuthUser me = CurrentUser.get();
        if (!me.isOwner()) {
            throw ApiException.forbidden("Only the account owner can reset another user's password");
        }
        User u = editable(id, me);
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        u.setPasswordChangedAt(Instant.now());
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        refreshTokenRepository.revokeAllForUser(u.getId(), Instant.now());
        auditService.log("USER_PASSWORD_RESET", "USER", u.getId(), "by " + me.userId());
    }

    private User editable(Long id, AuthUser me) {
        User u = userRepository.findWithAccessById(id).filter(x -> me.ownerId() != null && me.ownerId().equals(x.getOwnerId()))
                .orElseThrow(() -> ApiException.notFound("User"));
        if (u.hasRole("OWNER")) {
            throw ApiException.forbidden("Owner accounts cannot be changed here");
        }
        if (u.getId().equals(me.userId())) {
            throw ApiException.forbidden("You cannot change your own role or permissions");
        }
        if (!me.isOwner()) {
            Set<Long> mine = hostelAccess.accessibleHostelIds();
            Set<Long> theirs = accessRepository.findByUserId(u.getId()).stream()
                    .map(UserHostelAccess::getHostelId).collect(Collectors.toSet());
            if (theirs.stream().noneMatch(mine::contains)) {
                throw ApiException.notFound("User");
            }
            // A delegated manager may only administer users whose access lies entirely within their own. Otherwise
            // they could take over, or silently strip hostels from, someone with wider rights than themselves.
            boolean permissionsWithin = u.getPermissions().stream().map(Permission::getCode).allMatch(me::has);
            if (!mine.containsAll(theirs) || !permissionsWithin) {
                throw ApiException.forbidden("You can only manage users whose hostels and permissions are within your own");
            }
        }
        return u;
    }

    private void checkGrantable(AuthUser me, Set<String> perms, Set<Long> hostelIds) {
        if (perms.contains("SETTINGS_MANAGE") && !me.isOwner()) {
            throw ApiException.forbidden("Only the owner can grant settings management");
        }
        for (String p : perms) {
            if (!com.hostelmanagement.security.Permissions.ALL.contains(p)) {
                throw ApiException.badRequest("Unknown permission " + p);
            }
            if (!me.has(p)) {
                throw ApiException.forbidden("You cannot grant a permission you do not have: " + p);
            }
        }
        if (hostelIds.isEmpty()) {
            throw ApiException.badRequest("Give the user access to at least one hostel");
        }
        hostelIds.forEach(hostelAccess::checkHostel);
    }

    private static UserDto toDto(User u, List<Long> hostelIds) {
        return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.isEnabled(),
                u.getRoles().stream().map(Role::getCode).collect(Collectors.toSet()),
                u.hasRole("OWNER") ? com.hostelmanagement.security.Permissions.ALL
                        : u.getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet()),
                hostelIds, u.getLastLoginAt(), u.getCreatedAt());
    }
}
