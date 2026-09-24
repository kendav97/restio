package com.restio.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token settings under {@code restio.jwt}. The secret signs access tokens with HMAC-SHA and must be
 * at least 32 bytes long.
 */
@ConfigurationProperties(prefix = "restio.jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration pinTokenTtl,
        Duration deviceTokenTtl) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "restio.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
    }
}
