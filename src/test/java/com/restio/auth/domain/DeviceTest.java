package com.restio.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeviceTest {

    private static final LoginLock POLICY = new LoginLock(5, Duration.ofMinutes(15));
    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    @Test
    @DisplayName("cinco PIN erróneos bloquean el PIN en el dispositivo")
    void registerFailedPin_reachingTheLimit_locksPinLogin() {
        Device device = new Device(1L, "D01", "TPV", DeviceType.POS, "hash");

        for (int i = 0; i < 5; i++) {
            device.registerFailedPin(NOW, POLICY);
        }

        assertThat(device.isPinLocked(NOW)).isTrue();
        assertThat(device.isPinLocked(NOW.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    @DisplayName("un PIN correcto desbloquea y registra la última actividad")
    void registerSuccessfulPin_resetsCounterAndTouchesLastSeen() {
        Device device = new Device(1L, "D01", "TPV", DeviceType.POS, "hash");
        device.registerFailedPin(NOW, POLICY);

        device.registerSuccessfulPin(NOW);

        assertThat(device.getFailedPinAttempts()).isZero();
        assertThat(device.getLastSeenAt()).isEqualTo(NOW);
    }
}
