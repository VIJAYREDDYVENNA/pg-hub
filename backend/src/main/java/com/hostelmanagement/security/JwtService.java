package com.hostelmanagement.security;

import com.hostelmanagement.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

/** Issues and validates short-lived HS256 access tokens. */
@Service
public class JwtService {

    private static final String ISSUER = "hostel-management";

    private final SecretKey key;
    private final int accessMinutes;

    public JwtService(AppProperties props) {
        String secret = props.security().jwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be configured with at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessMinutes = props.security().accessTokenMinutes();
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public record TokenClaims(Long userId, Long ownerId, Instant issuedAt) {
    }

    public IssuedToken issueAccessToken(Long userId, Long ownerId) {
        Instant now = Instant.now();
        Instant exp = now.plus(accessMinutes, ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .claim("oid", ownerId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, exp);
    }

    public Optional<TokenClaims> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).requireIssuer(ISSUER).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(new TokenClaims(Long.valueOf(c.getSubject()),
                    c.get("oid", Long.class), c.getIssuedAt().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
