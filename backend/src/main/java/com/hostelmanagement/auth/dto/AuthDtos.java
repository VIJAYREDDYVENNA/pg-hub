package com.hostelmanagement.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class AuthDtos {

    /** At least 8 characters with at least one letter and one digit. */
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MESSAGE = "Password must be 8-72 characters and contain a letter and a number";

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 72) String password) {
    }

    public record LoginResponse(String accessToken, Instant expiresAt, MeResponse user) {
    }

    public record MeResponse(Long id, String fullName, String email, String phone, Long ownerId, String ownerName,
                             Set<String> roles, Set<String> permissions, List<Long> hostelIds, Instant lastLoginAt) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(@NotBlank String token,
                                       @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String newPassword) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String newPassword) {
    }

    public record UpdateProfileRequest(@NotBlank @Size(max = 150) String fullName,
                                       @Pattern(regexp = "^$|^[+0-9 ]{7,20}$", message = "Enter a valid phone number") String phone) {
    }

    public record MessageResponse(String message) {
    }
}
