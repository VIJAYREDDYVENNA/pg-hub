package com.hostelmanagement.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileGuardTest {

    private static ProfileGuard guard(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return new ProfileGuard(env);
    }

    @Test
    void startup_isRefused_whenNoProfileWasChosen() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> guard().verify());
        assertTrue(e.getMessage().contains("SPRING_PROFILES_ACTIVE"), e.getMessage());
    }

    @Test
    void startup_isRefused_forUnknownProfiles() {
        assertThrows(IllegalStateException.class, () -> guard("staging").verify());
    }

    @Test
    void knownProfiles_areAccepted() {
        assertDoesNotThrow(() -> guard("dev").verify());
        assertDoesNotThrow(() -> guard("prod").verify());
        assertDoesNotThrow(() -> guard("test").verify());
    }
}
