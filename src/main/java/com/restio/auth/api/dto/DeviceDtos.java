package com.restio.auth.api.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.restio.auth.domain.Device;
import com.restio.auth.domain.DeviceType;

/** Request and response bodies of device linking. */
public final class DeviceDtos {

    private DeviceDtos() {}

    public record CreatePairingRequest(
            @NotBlank @Size(max = 100) String deviceName, @NotNull DeviceType deviceType) {}

    /** {@code code} is shown as a QR and can also be typed; it works once, until expiresAt. */
    public record PairingResponse(String code, Instant expiresAt) {}

    public record PairRequest(@NotBlank @Size(max = 20) String code) {}

    /** {@code deviceToken} is returned only once: the device must keep it in secure storage. */
    public record PairResponse(
            Long deviceId,
            UUID deviceUuid,
            String deviceCode,
            String name,
            DeviceType type,
            Long restaurantId,
            String deviceToken) {}

    public record DeviceLoginRequest(@NotNull Long deviceId, @NotBlank String deviceToken) {}

    public record DeviceResponse(
            Long id,
            UUID uuid,
            String code,
            String name,
            DeviceType type,
            boolean active,
            Instant lastSeenAt) {

        public static DeviceResponse of(Device device) {
            return new DeviceResponse(
                    device.getId(),
                    device.getUuid(),
                    device.getCode(),
                    device.getName(),
                    device.getType(),
                    device.isActive(),
                    device.getLastSeenAt());
        }
    }
}
