package com.restio.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-bytes-length";
    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    private final JwtProperties properties =
            new JwtProperties(
                    SECRET,
                    Duration.ofMinutes(15),
                    Duration.ofDays(7),
                    Duration.ofHours(8),
                    Duration.ofHours(1));

    private JwtService at(Instant instant) {
        return new JwtService(properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("un token emitido se verifica con los mismos claims")
    void issueAndVerify_roundTripsClaims() {
        AuthenticatedUser user =
                new AuthenticatedUser(
                        7L, Set.of(1L, 2L), Set.of("WAITER"), Set.of("ORDER_CREATE"), 100L);

        String token = at(NOW).issue(user, Duration.ofMinutes(15));

        assertThat(at(NOW).verify(token)).isEqualTo(user);
    }

    @Test
    @DisplayName("un token caducado se rechaza")
    void verify_expiredToken_throws() {
        AuthenticatedUser user = new AuthenticatedUser(7L, Set.of(1L), Set.of(), Set.of(), null);
        String token = at(NOW).issue(user, Duration.ofMinutes(15));

        assertThatThrownBy(() -> at(NOW.plus(Duration.ofMinutes(16))).verify(token))
                .isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    @DisplayName("un token firmado con otra clave se rechaza")
    void verify_tokenSignedWithAnotherKey_throws() {
        JwtService other =
                new JwtService(
                        new JwtProperties(
                                "another-secret-key-with-at-least-32-bytes",
                                Duration.ofMinutes(15),
                                Duration.ofDays(7),
                                Duration.ofHours(8),
                                Duration.ofHours(1)),
                        Clock.fixed(NOW, ZoneOffset.UTC));
        String token =
                other.issue(
                        new AuthenticatedUser(7L, Set.of(1L), Set.of(), Set.of(), null),
                        Duration.ofMinutes(15));

        assertThatThrownBy(() -> at(NOW).verify(token))
                .isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    @DisplayName("un secreto de menos de 32 bytes impide arrancar")
    void properties_shortSecret_isRejected() {
        assertThatThrownBy(() -> new JwtProperties("short", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("una sesión de dispositivo se verifica sin usuario y con el rol DEVICE")
    void issueAndVerify_deviceSession_hasNoUser() {
        AuthenticatedUser device = AuthenticatedUser.device(100L, 1L);

        AuthenticatedUser verified = at(NOW).verify(at(NOW).issue(device, Duration.ofHours(1)));

        assertThat(verified).isEqualTo(device);
        assertThat(verified.isDevice()).isTrue();
        assertThat(verified.getName()).isEqualTo("device:100");
    }
}
