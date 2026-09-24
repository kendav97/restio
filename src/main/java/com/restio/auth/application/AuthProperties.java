package com.restio.auth.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.restio.auth.domain.LoginLock;

/** Settings under {@code restio.auth}. */
@ConfigurationProperties(prefix = "restio.auth")
public record AuthProperties(
        int maxFailedAttempts, Duration lockDuration, Duration pairingCodeTtl) {

    public LoginLock loginLock() {
        return new LoginLock(maxFailedAttempts, lockDuration);
    }
}
