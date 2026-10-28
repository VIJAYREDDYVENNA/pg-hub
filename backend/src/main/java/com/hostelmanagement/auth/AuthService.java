package com.hostelmanagement.auth;

import com.hostelmanagement.auth.dto.AuthDtos.ChangePasswordRequest;
import com.hostelmanagement.auth.dto.AuthDtos.LoginResponse;
import com.hostelmanagement.auth.dto.AuthDtos.MeResponse;
import com.hostelmanagement.auth.dto.AuthDtos.UpdateProfileRequest;
import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.config.AppProperties;
import com.hostelmanagement.mail.PasswordResetEmail;
import com.hostelmanagement.owner.OwnerRepository;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import com.hostelmanagement.security.JwtService;
import com.hostelmanagement.security.PrincipalLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final OwnerRepository ownerRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final com.hostelmanagement.mail.MailDispatcher mail;
    private final HostelAccessService hostelAccessService;
    private final AppProperties props;

    public record LoginResult(LoginResponse response, String refreshToken) {
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult login(String email, String password) {
        Optional<User> found = userRepository.findByEmailIgnoreCase(email.trim());
        Instant now = Instant.now();
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash());
            throw invalidCredentials();
        }
        User user = found.get();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_LOCKED",
                    "Too many failed attempts. Your account is locked for a few minutes.");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            if (user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(now.plus(LOCK_DURATION));
                user.setFailedLoginAttempts(0);
                auditService.log(user.getOwnerId(), user.getId(), "ACCOUNT_LOCKED", "USER", user.getId(), null);
            }
            throw invalidCredentials();
        }
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "This account has been disabled");
        }
        requireOwnerActive(user);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        auditService.log(user.getOwnerId(), user.getId(), "LOGIN", "USER", user.getId(), null);
        return issueTokens(user);
    }

    @Transactional
    public LoginResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("Session expired");
        }
        RefreshToken stored = refreshTokenRepository.findByTokenHash(TokenHashing.sha256(rawRefreshToken))
                .orElseThrow(() -> ApiException.unauthorized("Session expired"));
        Instant now = Instant.now();
        if (stored.getRevokedAt() != null) {
            // Re-use of a rotated token: revoke the whole family for safety.
            refreshTokenRepository.revokeAllForUser(stored.getUserId(), now);
            throw ApiException.unauthorized("Session expired");
        }
        if (stored.getExpiresAt().isBefore(now)) {
            throw ApiException.unauthorized("Session expired");
        }
        User user = userRepository.findWithAccessById(stored.getUserId())
                .filter(User::isEnabled)
                .orElseThrow(() -> ApiException.unauthorized("Session expired"));
        requireOwnerActive(user);
        stored.setRevokedAt(now);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(TokenHashing.sha256(rawRefreshToken))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> t.setRevokedAt(Instant.now()));
    }

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmailIgnoreCase(email.trim()).filter(User::isEnabled).ifPresent(user -> {
            String raw = TokenHashing.newToken();
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(user.getId());
            token.setTokenHash(TokenHashing.sha256(raw));
            token.setCreatedAt(Instant.now());
            token.setExpiresAt(Instant.now().plus(RESET_TOKEN_TTL));
            resetTokenRepository.save(token);
            auditService.log(user.getOwnerId(), user.getId(), "PASSWORD_RESET_REQUESTED", "USER", user.getId(), null);
            String link = props.frontendBaseUrl().replaceAll("/+$", "") + "/reset-password?token=" + raw;
            // Queued for after the token is committed; delivery never delays or alters the response.
            mail.sendAfterCommit(PasswordResetEmail.build(user.getEmail(), user.getFullName(), link, RESET_TOKEN_TTL));
        });
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = resetTokenRepository.findByTokenHash(TokenHashing.sha256(rawToken))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> ApiException.badRequest("This reset link is invalid or has expired"));
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> ApiException.badRequest("This reset link is invalid or has expired"));
        token.setUsedAt(Instant.now());
        applyNewPassword(user, newPassword);
        auditService.log(user.getOwnerId(), user.getId(), "PASSWORD_RESET", "USER", user.getId(), null);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest req) {
        AuthUser current = CurrentUser.get();
        User user = userRepository.findById(current.userId()).orElseThrow(() -> ApiException.notFound("User"));
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "Current password is incorrect");
        }
        if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("New password must be different from the current password");
        }
        applyNewPassword(user, req.newPassword());
        auditService.log("PASSWORD_CHANGED", "USER", user.getId(), null);
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        AuthUser current = CurrentUser.get();
        User user = userRepository.findWithAccessById(current.userId()).orElseThrow(() -> ApiException.notFound("User"));
        return toMe(user, current);
    }

    @Transactional
    public MeResponse updateProfile(UpdateProfileRequest req) {
        AuthUser current = CurrentUser.get();
        User user = userRepository.findWithAccessById(current.userId()).orElseThrow(() -> ApiException.notFound("User"));
        user.setFullName(req.fullName().trim());
        user.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        auditService.log("PROFILE_UPDATED", "USER", user.getId(), null);
        return toMe(user, current);
    }

    private void applyNewPassword(User user, String newPassword) {
        Instant now = Instant.now();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(now);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        refreshTokenRepository.revokeAllForUser(user.getId(), now);
    }

    private LoginResult issueTokens(User user) {
        var access = jwtService.issueAccessToken(user.getId(), user.getOwnerId());
        String raw = TokenHashing.newToken();
        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(TokenHashing.sha256(raw));
        rt.setCreatedAt(Instant.now());
        rt.setExpiresAt(Instant.now().plus(Duration.ofDays(props.security().refreshTokenDays())));
        refreshTokenRepository.save(rt);

        AuthUser principal = PrincipalLoader.toAuthUser(user);
        // Make the freshly authenticated user available for hostel scope resolution within this request.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        hostelAccessService.evict();
        return new LoginResult(new LoginResponse(access.token(), access.expiresAt(), toMe(user, principal)), raw);
    }

    private MeResponse toMe(User user, AuthUser principal) {
        String ownerName = user.getOwnerId() == null ? null
                : ownerRepository.findById(user.getOwnerId()).map(o -> o.getName()).orElse(null);
        List<Long> hostelIds = List.copyOf(hostelAccessService.accessibleHostelIds());
        return new MeResponse(user.getId(), user.getFullName(), user.getEmail().toLowerCase(Locale.ROOT), user.getPhone(),
                user.getOwnerId(), ownerName, principal.roles(), principal.permissions(), hostelIds, user.getLastLoginAt());
    }

    /** Users of a suspended or deactivated owner account cannot sign in. */
    private void requireOwnerActive(User user) {
        if (user.getOwnerId() != null
                && !ownerRepository.findById(user.getOwnerId()).map(o -> "ACTIVE".equals(o.getStatus())).orElse(false)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "OWNER_SUSPENDED",
                    "This organisation account is suspended. Contact the platform administrator.");
        }
    }

    private volatile String dummyHash;

    /** Hash used to keep response timing similar when the e-mail does not exist. */
    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("timing-equalizer-password-1");
        }
        return dummyHash;
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Incorrect email or password");
    }
}
