package com.restio.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restio.auth.domain.AuthErrorCode;
import com.restio.auth.domain.Device;
import com.restio.auth.domain.DevicePairing;
import com.restio.auth.domain.DeviceType;
import com.restio.auth.infrastructure.DevicePairingRepository;
import com.restio.auth.infrastructure.DeviceRepository;
import com.restio.shared.error.NotFoundException;
import com.restio.shared.error.UnauthorizedException;
import com.restio.shared.tenant.SystemContext;

/**
 * Links devices to a restaurant with one-time pairing codes, and unlinks them. See
 * restio-architecture-sync, "Dispositivos".
 */
@Service
public class DeviceService {

    private final DeviceRepository devices;
    private final DevicePairingRepository pairings;
    private final AuthProperties properties;
    private final Clock clock;

    public DeviceService(
            DeviceRepository devices,
            DevicePairingRepository pairings,
            AuthProperties properties,
            Clock clock) {
        this.devices = devices;
        this.pairings = pairings;
        this.properties = properties;
        this.clock = clock;
    }

    /** A pairing code as shown to the manager; the raw code is never stored. */
    public record PairingCode(String code, Instant expiresAt) {}

    /** A freshly linked device and its token, which is handed out only this once. */
    public record PairedDevice(Device device, String deviceToken) {}

    @Transactional
    public PairingCode createPairing(Long restaurantId, String deviceName, DeviceType type) {
        String code = PairingCodes.generate();
        Instant expiresAt = clock.instant().plus(properties.pairingCodeTtl());
        pairings.save(
                new DevicePairing(
                        restaurantId, PairingCodes.hash(code), deviceName.trim(), type, expiresAt));
        return new PairingCode(code, expiresAt);
    }

    /**
     * Redeems a pairing code and registers the device. The caller is anonymous, so the code is
     * looked up across restaurants; only a valid, unexpired and unused code reveals anything.
     */
    @SystemContext
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public PairedDevice pair(String pairingCode) {
        Instant now = clock.instant();
        DevicePairing pairing =
                pairings.findByCodeHash(PairingCodes.hash(pairingCode))
                        .filter(p -> p.isRedeemable(now))
                        .orElseThrow(
                                () ->
                                        new UnauthorizedException(
                                                AuthErrorCode.AUTH_INVALID_PAIRING_CODE,
                                                "The pairing code is not valid or has expired"));

        String token = SecretTokens.generate();
        String code = Device.codeFor(devices.countEverLinked(pairing.getRestaurantId()) + 1);
        Device device =
                devices.save(
                        new Device(
                                pairing.getRestaurantId(),
                                code,
                                pairing.getDeviceName(),
                                pairing.getDeviceType(),
                                SecretTokens.hash(token)));
        device.registerSeen(now);
        pairing.redeem(device, now);
        return new PairedDevice(device, token);
    }

    @Transactional(readOnly = true)
    public List<Device> list(Long restaurantId) {
        return devices.findByRestaurantIdOrderByCode(restaurantId);
    }

    /** Unlinks a device for good. Deactivating an inactive device changes nothing. */
    @Transactional
    public Device deactivate(Long restaurantId, Long deviceId) {
        Device device =
                devices.findById(deviceId)
                        .filter(d -> d.getRestaurantId().equals(restaurantId))
                        .orElseThrow(() -> new NotFoundException("Device", deviceId));
        device.deactivate();
        return device;
    }
}
