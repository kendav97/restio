package com.restio.auth.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.restio.shared.domain.RestaurantScopedEntity;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A one-time code, shown as a QR, that links a new device to a restaurant. A manager creates it
 * (authenticated), the new device redeems it (anonymous) before it expires. Only the hash of the
 * code is stored.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "device_pairings")
public class DevicePairing extends RestaurantScopedEntity {

    @Column(name = "code_hash", nullable = false, unique = true, updatable = false)
    private String codeHash;

    @Column(name = "device_name", nullable = false, updatable = false)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, updatable = false)
    private DeviceType deviceType;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    @Column(name = "device_id")
    private Long deviceId;

    public DevicePairing(
            Long restaurantId,
            String codeHash,
            String deviceName,
            DeviceType deviceType,
            Instant expiresAt) {
        super(restaurantId);
        this.codeHash = codeHash;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.expiresAt = expiresAt;
    }

    public boolean isRedeemable(Instant now) {
        return redeemedAt == null && now.isBefore(expiresAt);
    }

    public void redeem(Device device, Instant now) {
        if (!isRedeemable(now)) {
            throw new IllegalStateException("Pairing " + getId() + " is not redeemable");
        }
        this.redeemedAt = now;
        this.deviceId = device.getId();
    }
}
