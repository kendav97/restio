package com.restio.auth.application;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.restio.auth.domain.Device;
import com.restio.auth.infrastructure.DeviceRepository;
import com.restio.shared.tenant.SystemContext;

/**
 * Finds the device behind a PIN login. The caller is still anonymous, so its restaurant is unknown
 * until the device is found: this is the one lookup of the login flow that must cross restaurants.
 * It only returns an active device whose token matches, so nothing leaks to an unproven caller.
 */
@Component
public class DeviceCredentials {

    private final DeviceRepository devices;

    public DeviceCredentials(DeviceRepository devices) {
        this.devices = devices;
    }

    @SystemContext
    public Optional<Device> findAuthenticated(Long deviceId, String deviceToken) {
        return devices.findById(deviceId)
                .filter(Device::isActive)
                .filter(device -> SecretTokens.matches(deviceToken, device.getDeviceTokenHash()));
    }
}
