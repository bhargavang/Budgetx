package com.budgetx.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import com.budgetx.gateway.config.GatewaySecurityProperties;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "gateway-test-secret-that-is-32-chars-plus";

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(new GatewaySecurityProperties(
            SECRET, List.of("/api/auth/**"), List.of("/api/users/*/salary")));

    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    @Test
    void openPathPassesWithoutToken() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/login"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    void missingTokenIsUnauthorized() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/emis"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void validAccessTokenForwardsUserHeadersAndDropsSpoofedOnes() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/emis")
                .header("Authorization", "Bearer " + token("access"))
                .header(JwtAuthenticationFilter.USER_ID_HEADER, "999"));

        filter.filter(exchange, chain).block();

        var headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.get(JwtAuthenticationFilter.USER_ID_HEADER)).containsExactly("7");
        assertThat(headers.getFirst(JwtAuthenticationFilter.USER_EMAIL_HEADER)).isEqualTo("asha@example.com");
    }

    @Test
    void refreshTokenIsRejected() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/emis")
                .header("Authorization", "Bearer " + token("refresh")));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void internalPathIsForbiddenEvenWithToken() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users/7/salary")
                .header("Authorization", "Bearer " + token("access")));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forwarded.get()).isNull();
    }

    private String token(String type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("asha@example.com")
                .claim("uid", 7L)
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
