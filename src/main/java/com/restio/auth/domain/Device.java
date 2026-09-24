package com.restio.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.restio.shared.domain.RestaurantScopedEntity;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A registered POS, KDS or admin device. PIN login and synchronisation are only accepted from an
 * active device that proves itself with its device token. Failed PINs are counted per device, since
 * a wrong PIN does not identify any user.
 *
 * <p>{@code uuid} identifies the device in its events; {@code code} (D01, D02...) names its ticket
 * series and is never reused within the restaurant, not even after the device is deactivated.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "devices")
public class Device extends RestaurantScopedEntity {

    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private DeviceType type;

    @Column(name = "device_token_hash", nullable = false, unique = true)
    private String deviceTokenHash;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "failed_pin_attempts", nullable = false)
    private int failedPinAttempts;

    @Column(name = "pin_locked_until")
    private Instant pinLockedUntil;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    public Device(
            Long restaurantId, String code, String name, DeviceType type, String deviceTokenHash) {
        super(restaurantId);
        this.uuid = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.type = type;
        this.deviceTokenHash = deviceTokenHash;
    }

    /** Code of the {@code sequence}-th device of a restaurant: D01, D02... D100. */
    public static String codeFor(long sequence) {
        return "D%02d".formatted(sequence);
    }

    public boolean isPinLocked(Instant now) {
        return pinLockedUntil != null && now.isBefore(pinLockedUntil);
    }

    public void registerFailedPin(Instant now, LoginLock policy) {
        failedPinAttempts++;
        if (failedPinAttempts >= policy.maxFailedAttempts()) {
            pinLockedUntil = now.plus(policy.lockDuration());
            failedPinAttempts = 0;
        }
    }

    public void registerSuccessfulPin(Instant now) {
        failedPinAttempts = 0;
        pinLockedUntil = null;
        lastSeenAt = now;
    }

    public void registerSeen(Instant now) {
        lastSeenAt = now;
    }

    /** Unlinks the device for good: its token stops working and it cannot come back. */
    public void deactivate() {
        active = false;
    }
}
