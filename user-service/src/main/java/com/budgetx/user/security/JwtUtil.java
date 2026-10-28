package com.budgetx.user.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.budgetx.user.config.JwtProperties;
import com.budgetx.user.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and validates HMAC-signed JWTs. Access and refresh tokens share the key and
 * are told apart by the "type" claim, so a refresh token can never be used as an access token.
 */
@Component
public class JwtUtil {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_USER_ID = "uid";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtUtil(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(properties.accessTokenExpiryMinutes());
        this.refreshTtl = Duration.ofDays(properties.refreshTokenExpiryDays());
    }

    public String generateAccessToken(User user) {
        return generate(user, TYPE_ACCESS, accessTtl);
    }

    public String generateRefreshToken(User user) {
        return generate(user, TYPE_REFRESH, refreshTtl);
    }

    public long accessTokenTtlSeconds() {
        return accessTtl.toSeconds();
    }

    /** Returns the claims if the token is well-formed, correctly signed, unexpired and of the expected type. */
    public Optional<TokenClaims> validate(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(claims.get(CLAIM_USER_ID, Long.class), claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String generate(User user, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_USER_ID, user.getUserId())
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public record TokenClaims(Long userId, String email) {
    }
}
