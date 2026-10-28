package com.hostelmanagement.config;

import com.hostelmanagement.auth.Role;
import com.hostelmanagement.auth.RoleRepository;
import com.hostelmanagement.auth.User;
import com.hostelmanagement.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Creates the platform super admin from configuration (SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD) when no
 * SUPER_ADMIN user exists. Never modifies an existing account. The super admin belongs to no owner account.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class SuperAdminSeeder implements ApplicationRunner {

    private final AppProperties props;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var cfg = props.superAdmin();
        if (cfg == null || isBlank(cfg.email()) || isBlank(cfg.password())) {
            return;
        }
        Role role = roleRepository.findByCode("SUPER_ADMIN").orElse(null);
        if (role == null || userRepository.existsByRoles_Code("SUPER_ADMIN")) {
            return;
        }
        String email = cfg.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.warn("Super admin e-mail {} is already used by another account; no super admin was created", email);
            return;
        }
        User u = new User();
        u.setOwnerId(null);
        u.setFullName(cfg.fullName());
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(cfg.password()));
        u.setPasswordChangedAt(Instant.now().minusSeconds(5));
        u.setRoles(new HashSet<>(Set.of(role)));
        userRepository.save(u);
        log.info("Created super admin account {}", email);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
