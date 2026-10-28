package com.budgetx.gateway.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param jwtSecret     HMAC secret shared with user-service (JWT_SECRET)
 * @param openPaths     paths reachable without a token (login, register, docs)
 * @param internalPaths service-to-service endpoints that must never be reachable from outside
 */
@ConfigurationProperties(prefix = "budgetx.security")
public record GatewaySecurityProperties(String jwtSecret, List<String> openPaths, List<String> internalPaths) {

    public GatewaySecurityProperties {
        openPaths = openPaths == null ? List.of() : openPaths;
        internalPaths = internalPaths == null ? List.of() : internalPaths;
    }
}
