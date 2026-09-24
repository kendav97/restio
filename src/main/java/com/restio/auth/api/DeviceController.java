package com.restio.auth.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.restio.auth.api.dto.DeviceDtos.CreatePairingRequest;
import com.restio.auth.api.dto.DeviceDtos.DeviceResponse;
import com.restio.auth.api.dto.DeviceDtos.PairRequest;
import com.restio.auth.api.dto.DeviceDtos.PairResponse;
import com.restio.auth.api.dto.DeviceDtos.PairingResponse;
import com.restio.auth.application.DeviceService;
import com.restio.auth.application.DeviceService.PairedDevice;
import com.restio.auth.application.DeviceService.PairingCode;
import com.restio.auth.domain.Device;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "devices")
@RestController
@RequestMapping("/api/v1")
public class DeviceController {

    private static final String CAN_MANAGE =
            "@restaurantAccess.canAccess(#restaurantId) and hasAuthority('DEVICE_MANAGE')";

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Operation(summary = "Create a one-time pairing code (QR) to link a new device")
    @PostMapping("/restaurants/{restaurantId}/devices/pairings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_MANAGE)
    public PairingResponse createPairing(
            @PathVariable Long restaurantId, @Valid @RequestBody CreatePairingRequest request) {
        PairingCode pairing =
                deviceService.createPairing(
                        restaurantId, request.deviceName(), request.deviceType());
        return new PairingResponse(pairing.code(), pairing.expiresAt());
    }

    @Operation(summary = "Redeem a pairing code from the new device; returns its token once")
    @PostMapping("/devices/pair")
    @ResponseStatus(HttpStatus.CREATED)
    public PairResponse pair(@Valid @RequestBody PairRequest request) {
        PairedDevice paired = deviceService.pair(request.code());
        Device device = paired.device();
        return new PairResponse(
                device.getId(),
                device.getUuid(),
                device.getCode(),
                device.getName(),
                device.getType(),
                device.getRestaurantId(),
                paired.deviceToken());
    }

    @Operation(summary = "Devices of the restaurant, active and unlinked")
    @GetMapping("/restaurants/{restaurantId}/devices")
    @PreAuthorize(CAN_MANAGE)
    public List<DeviceResponse> list(@PathVariable Long restaurantId) {
        return deviceService.list(restaurantId).stream().map(DeviceResponse::of).toList();
    }

    @Operation(summary = "Unlink a device for good; its token and PIN logins stop working")
    @PostMapping("/restaurants/{restaurantId}/devices/{deviceId}/deactivate")
    @PreAuthorize(CAN_MANAGE)
    public DeviceResponse deactivate(@PathVariable Long restaurantId, @PathVariable Long deviceId) {
        return DeviceResponse.of(deviceService.deactivate(restaurantId, deviceId));
    }
}
