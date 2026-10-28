package com.hostelmanagement.security;

import com.hostelmanagement.auth.User;
import com.hostelmanagement.auth.UserRepository;
import com.hostelmanagement.owner.OwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Loads the current permissions of a user so that role/permission changes apply immediately. */
@Component
@RequiredArgsConstructor
public class PrincipalLoader {

    private final UserRepository userRepository;
    private final OwnerRepository ownerRepository;

    @Transactional(readOnly = true)
    public Optional<AuthUser> load(Long userId, Instant tokenIssuedAt) {
        return userRepository.findWithAccessById(userId)
                .filter(User::isEnabled)
                .filter(u -> u.getOwnerId() == null || ownerRepository.findById(u.getOwnerId()).map(o -> "ACTIVE".equals(o.getStatus())).orElse(false))
                .filter(u -> u.getPasswordChangedAt() == null || tokenIssuedAt == null
                        || !tokenIssuedAt.isBefore(u.getPasswordChangedAt().truncatedTo(ChronoUnit.SECONDS)))
                .map(PrincipalLoader::toAuthUser);
    }

    public static AuthUser toAuthUser(User u) {
        Set<String> roles = u.getRoles().stream().map(r -> r.getCode()).collect(Collectors.toSet());
        Set<String> perms = new HashSet<>();
        if (roles.contains("OWNER")) {
            perms.addAll(Permissions.ALL);
        } else {
            u.getPermissions().forEach(p -> perms.add(p.getCode()));
        }
        return new AuthUser(u.getId(), u.getOwnerId(), u.getEmail(), u.getFullName(), Set.copyOf(roles), Set.copyOf(perms));
    }
}
