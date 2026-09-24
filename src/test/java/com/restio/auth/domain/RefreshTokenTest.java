package com.restio.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    @Test
    @DisplayName("un token caduca exactamente en su fecha de expiración")
    void isExpired_atExpiryInstant_isTrue() {
        RefreshToken token = new RefreshToken(1L, "hash", NOW.plus(Duration.ofDays(7)), null);

        assertThat(token.isExpired(NOW)).isFalse();
        assertThat(token.isExpired(NOW.plus(Duration.ofDays(7)))).isTrue();
    }

    @Test
    @DisplayName("revocar dos veces conserva la primera fecha")
    void revoke_twice_keepsFirstInstant() {
        RefreshToken token = new RefreshToken(1L, "hash", NOW.plus(Duration.ofDays(7)), null);

        token.revoke(NOW);
        token.revoke(NOW.plusSeconds(60));

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isEqualTo(NOW);
    }
}
