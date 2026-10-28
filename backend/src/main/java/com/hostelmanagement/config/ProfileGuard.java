package com.hostelmanagement.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Fails start-up when no deployment profile was chosen. The previous default silently fell back to the development
 * profile, which seeds demo data and a well-known administrator password.
 */
@Component
public class ProfileGuard {

    private static final Set<String> KNOWN = Set.of("dev", "prod", "test");

    private final Environment env;

    public ProfileGuard(Environment env) {
        this.env = env;
    }

    @PostConstruct
    void verify() {
        boolean known = Arrays.stream(env.getActiveProfiles()).anyMatch(KNOWN::contains);
        if (!known) {
            throw new IllegalStateException("No deployment profile is active. Set SPRING_PROFILES_ACTIVE=dev for local "
                    + "development or SPRING_PROFILES_ACTIVE=prod for production (backend/.env or the environment).");
        }
    }
}
