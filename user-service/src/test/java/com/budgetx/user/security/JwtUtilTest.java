package com.budgetx.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.budgetx.user.config.JwtProperties;
import com.budgetx.user.entity.User;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil(
            new JwtProperties("unit-test-secret-that-is-at-least-32-chars", 15, 7));

    private final User user = User.builder()
            .userId(42L)
            .email("asha@example.com")
            .fullName("Asha")
            .passwordHash("x")
            .monthlySalary(new BigDecimal("85000.00"))
            .build();

    @Test
    void accessTokenRoundTrips() {
        String token = jwtUtil.generateAccessToken(user);

        var claims = jwtUtil.validate(token, JwtUtil.TYPE_ACCESS);

        assertThat(claims).isPresent();
        assertThat(claims.get().userId()).isEqualTo(42L);
        assertThat(claims.get().email()).isEqualTo("asha@example.com");
    }

    @Test
    void refreshTokenIsNotAcceptedAsAccessToken() {
        String refresh = jwtUtil.generateRefreshToken(user);

        assertThat(jwtUtil.validate(refresh, JwtUtil.TYPE_ACCESS)).isEmpty();
        assertThat(jwtUtil.validate(refresh, JwtUtil.TYPE_REFRESH)).isPresent();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtUtil other = new JwtUtil(new JwtProperties("a-completely-different-secret-of-32+chars", 15, 7));

        assertThat(jwtUtil.validate(other.generateAccessToken(user), JwtUtil.TYPE_ACCESS)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        // A zero-minute TTL produces a token that is already expired.
        JwtUtil expired = new JwtUtil(new JwtProperties("unit-test-secret-that-is-at-least-32-chars", 0, 7));
        assertThat(jwtUtil.validate(expired.generateAccessToken(user), JwtUtil.TYPE_ACCESS)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(jwtUtil.validate("not.a.jwt", JwtUtil.TYPE_ACCESS)).isEmpty();
    }
}
