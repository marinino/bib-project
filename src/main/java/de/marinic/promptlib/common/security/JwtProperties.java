package de.marinic.promptlib.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "promptlib.jwt")
public record JwtProperties(String secret, long expirationMs) {}
