package com.restio.auth.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.restio.shared.domain.AuditableEntity;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A long-lived token that obtains new access tokens. Only its SHA-256 hash is stored. Tokens
 * rotate: each use revokes the token and points it to its replacement, so presenting a revoked
 * token again reveals that it leaked.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends AuditableEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private Long replacedBy;

    @Column(name = "device_id", updatable = false)
    private Long deviceId;

    public RefreshToken(Long userId, String tokenHash, Instant expiresAt, Long deviceId) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.deviceId = deviceId;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public void replaceWith(RefreshToken next, Instant now) {
        revoke(now);
        replacedBy = next.getId();
    }
}
