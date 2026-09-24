package com.restio.sync.api;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.restio.shared.security.AuthenticatedUser;
import com.restio.sync.api.dto.SyncDtos.EventDto;
import com.restio.sync.api.dto.SyncDtos.EventResultDto;
import com.restio.sync.api.dto.SyncDtos.PosUserDto;
import com.restio.sync.api.dto.SyncDtos.SnapshotResponse;
import com.restio.sync.api.dto.SyncDtos.UploadEventsRequest;
import com.restio.sync.api.dto.SyncDtos.UploadEventsResponse;
import com.restio.sync.application.SyncService;
import com.restio.sync.application.SyncService.Snapshot;
import com.restio.sync.domain.SyncEvent;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Only device sessions ({@code POST /auth/device-login}) synchronise. */
@Tag(name = "sync")
@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/sync")
public class SyncController {

    private static final String DEVICE_OF_RESTAURANT =
            "@restaurantAccess.canAccess(#restaurantId) and hasRole('DEVICE')";

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @Operation(summary = "Upload a batch of events; idempotent by eventId, one result per event")
    @PostMapping("/events")
    @PreAuthorize(DEVICE_OF_RESTAURANT)
    public UploadEventsResponse upload(
            @PathVariable Long restaurantId,
            @AuthenticationPrincipal AuthenticatedUser device,
            @Valid @RequestBody UploadEventsRequest request) {
        var outcomes =
                syncService.upload(
                        restaurantId,
                        device.deviceId(),
                        request.events().stream().map(SyncController::toEvent).toList());
        return new UploadEventsResponse(
                outcomes.stream().map(o -> new EventResultDto(o.eventId(), o.result())).toList());
    }

    @Operation(summary = "Master data a device needs to work offline")
    @GetMapping("/snapshot")
    @PreAuthorize(DEVICE_OF_RESTAURANT)
    public SnapshotResponse snapshot(@PathVariable Long restaurantId) {
        Snapshot snapshot = syncService.snapshot(restaurantId);
        return new SnapshotResponse(
                snapshot.generatedAt(),
                snapshot.users().stream()
                        .map(
                                u ->
                                        new PosUserDto(
                                                u.id(),
                                                u.displayName(),
                                                u.pinHash(),
                                                u.roles(),
                                                u.permissions()))
                        .toList());
    }

    private static SyncEvent toEvent(EventDto dto) {
        return new SyncEvent(
                dto.eventId(),
                dto.type(),
                dto.aggregateId(),
                dto.restaurantId(),
                dto.deviceId(),
                dto.userId(),
                dto.schemaVersion(),
                dto.payload().toString(),
                dto.hlc(),
                dto.epoch(),
                dto.seq(),
                dto.createdAt());
    }
}
