package com.restio.sync.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * An operational event as uploaded by a device (see restio-architecture-sync, "Eventos"). The
 * payload is kept as raw JSON: each module reads the event types it owns.
 */
public record SyncEvent(
        UUID eventId,
        String type,
        UUID aggregateId,
        Long restaurantId,
        UUID deviceUuid,
        Long userId,
        int schemaVersion,
        String payload,
        String hlc,
        long epoch,
        Long seq,
        Instant createdAt) {}
