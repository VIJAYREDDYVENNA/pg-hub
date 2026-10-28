package com.hostelmanagement.auth;

import com.hostelmanagement.auth.dto.AuthDtos.ChangePasswordRequest;
import com.hostelmanagement.auth.dto.AuthDtos.ForgotPasswordRequest;
import com.hostelmanagement.auth.dto.AuthDtos.LoginRequest;
import com.hostelmanagement.auth.dto.AuthDtos.LoginResponse;
import com.hostelmanagement.auth.dto.AuthDtos.MeResponse;
import com.hostelmanagement.auth.dto.AuthDtos.MessageResponse;
import com.hostelmanagement.auth.dto.AuthDtos.ResetPasswordRequest;
import com.hostelmanagement.auth.dto.AuthDtos.UpdateProfileRequest;
import com.hostelmanagement.config.AppProperties;
import com.hostelmanagement.security.LoginRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Locale;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    static final String REFRESH_COOKIE = "hms_refresh";
    static final int REFRESH_LIMIT_PER_MINUTE = 120;

    private final AuthService authService;
    private final LoginRateLimiter rateLimiter;
    private final AppProperties props;

    @Operation(summary = "Sign in with email and password")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        rateLimiter.check("login:" + http.getRemoteAddr());
        rateLimiter.check("login-email:" + req.email().toLowerCase(Locale.ROOT));
        var result = authService.login(req.email(), req.password());
        return withRefreshCookie(result.response(), result.refreshToken());
    }

    @Operation(summary = "Exchange the refresh cookie for a new access token")
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token,
                                                 HttpServletRequest http) {
        // Every page load refreshes the session, so this limit is far higher than for password logins.
        rateLimiter.check("refresh:" + http.getRemoteAddr(), REFRESH_LIMIT_PER_MINUTE);
        var result = authService.refresh(token);
        return withRefreshCookie(result.response(), result.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
        authService.logout(token);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .body(new MessageResponse("Signed out"));
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest req, HttpServletRequest http) {
        rateLimiter.check("forgot:" + http.getRemoteAddr());
        authService.forgotPassword(req.email());
        return new MessageResponse("If an account exists for this email, a password reset link has been issued.");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req, HttpServletRequest http) {
        rateLimiter.check("reset:" + http.getRemoteAddr());
        authService.resetPassword(req.token(), req.newPassword());
        return new MessageResponse("Your password has been reset. You can now sign in.");
    }

    @PostMapping("/change-password")
    public MessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(req);
        return new MessageResponse("Password changed. Other sessions have been signed out.");
    }

    @GetMapping("/me")
    public MeResponse me() {
        return authService.me();
    }

    @PutMapping("/me")
    public MeResponse updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        return authService.updateProfile(req);
    }

    private ResponseEntity<LoginResponse> withRefreshCookie(LoginResponse body, String refreshToken) {
        ResponseCookie c = cookie(refreshToken, Duration.ofDays(props.security().refreshTokenDays()));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, c.toString()).body(body);
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(props.security().refreshCookieSecure())
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
