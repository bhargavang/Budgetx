package com.budgetx.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "JWT_SECRET must be at least 32 characters (256 bits)") String secret,
        @Positive long accessTokenExpiryMinutes,
        @Positive long refreshTokenExpiryDays) {
}
