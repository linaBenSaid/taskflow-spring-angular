package com.taskflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Bound from app.jwt.* in application.yaml (values come from JWT_SECRET and JWT_EXPIRATION_MINUTES).
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationMinutes) {
}
