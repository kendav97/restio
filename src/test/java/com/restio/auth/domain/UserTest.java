package com.restio.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final LoginLock POLICY = new LoginLock(5, Duration.ofMinutes(15));
    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    @Test
    @DisplayName("el quinto intento fallido bloquea la cuenta 15 minutos")
    void registerFailedAttempt_reachingTheLimit_locksForTheConfiguredDuration() {
        User user = new User("ana@restio.local", "Ana");

        for (int i = 0; i < 4; i++) {
            user.registerFailedAttempt(NOW, POLICY);
        }
        assertThat(user.isLocked(NOW)).isFalse();

        user.registerFailedAttempt(NOW, POLICY);

        assertThat(user.isLocked(NOW)).isTrue();
        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    @DisplayName("un login correcto reinicia los intentos fallidos")
    void registerSuccessfulLogin_resetsFailedAttempts() {
        User user = new User("ana@restio.local", "Ana");
        user.registerFailedAttempt(NOW, POLICY);

        user.registerSuccessfulLogin(NOW);

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
    }
}
