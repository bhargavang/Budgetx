package com.budgetx.gateway.filter;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;

import com.budgetx.gateway.config.GatewaySecurityProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

/**
 * Validates the Bearer access token on every request and forwards the caller's identity
 * to downstream services as trusted headers. Downstream services trust these headers,
 * so any client-supplied copies are stripped first.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";

    private final GatewaySecurityProperties properties;
    private final SecretKey key;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public JwtAuthenticationFilter(GatewaySecurityProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (matchesAny(path, properties.internalPaths())) {
            return reject(exchange, HttpStatus.FORBIDDEN);
        }

        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(USER_ID_HEADER);
                    h.remove(USER_EMAIL_HEADER);
                })
                .build();

        if (matchesAny(path, properties.openPaths())) {
            return chain.filter(exchange.mutate().request(stripped).build());
        }

        String header = stripped.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(header.substring(7))
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }
        if (!"access".equals(claims.get("type", String.class))) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        ServerHttpRequest authenticated = stripped.mutate()
                .header(USER_ID_HEADER, claims.get("uid").toString())
                .header(USER_EMAIL_HEADER, claims.getSubject())
                .build();
        return chain.filter(exchange.mutate().request(authenticated).build());
    }

    private boolean matchesAny(String path, Iterable<String> patterns) {
        for (String pattern : patterns) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        // Run before routing and before the rate limiter so it can key on the user id.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
