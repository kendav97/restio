package com.restio.sync.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.restio.sync.domain.SyncEvent;

/**
 * Append-only store of uploaded events. Plain SQL: the insert must be idempotent by {@code
 * event_id} ({@code ON CONFLICT DO NOTHING}), which JPA cannot express. Native SQL bypasses the
 * restaurant filter, so the caller has already checked the restaurant of every event.
 */
@Repository
public class SyncEventRepository {

    private final JdbcTemplate jdbc;

    public SyncEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @return {@code true} if the event was new, {@code false} if it was already stored
     */
    public boolean insertIfAbsent(SyncEvent event, Long uploadedBy, Instant receivedAt) {
        int inserted =
                jdbc.update(
                        """
                        INSERT INTO sync_events (event_id, restaurant_id, type, aggregate_id,
                            device_uuid, user_id, schema_version, payload, hlc, epoch, seq,
                            created_at, received_at, uploaded_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (event_id) DO NOTHING
                        """,
                        event.eventId(),
                        event.restaurantId(),
                        event.type(),
                        event.aggregateId(),
                        event.deviceUuid(),
                        event.userId(),
                        event.schemaVersion(),
                        event.payload(),
                        event.hlc(),
                        event.epoch(),
                        event.seq(),
                        Timestamp.from(event.createdAt()),
                        Timestamp.from(receivedAt),
                        uploadedBy);
        return inserted > 0;
    }
}
