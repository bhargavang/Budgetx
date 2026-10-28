package com.budgetx.gateway.config;

import java.util.Objects;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.budgetx.gateway.filter.JwtAuthenticationFilter;

import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    /** Rate-limit per authenticated user, falling back to client IP for anonymous calls. */
    @Bean
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(JwtAuthenticationFilter.USER_ID_HEADER);
            if (userId != null) {
                return Mono.just("user:" + userId);
            }
            var address = exchange.getRequest().getRemoteAddress();
            String ip = address != null && address.getAddress() != null
                    ? address.getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + Objects.requireNonNull(ip));
        };
    }
}
