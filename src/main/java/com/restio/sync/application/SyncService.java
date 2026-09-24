package com.restio.sync.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restio.auth.PosUsers;
import com.restio.auth.PosUsers.PosUser;
import com.restio.sync.domain.SyncEvent;
import com.restio.sync.domain.SyncResult;
import com.restio.sync.infrastructure.SyncEventRepository;

/**
 * Cloud side of the synchronisation: stores the events uploaded by the devices (idempotent by
 * {@code eventId}) and serves the master data the devices need to work offline.
 *
 * <p>Minimal version (stage 0.5): no leader check yet, and events are stored but not projected into
 * the domain modules; both arrive with the modules that own the events.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final SyncEventRepository events;
    private final PosUsers posUsers;
    private final Clock clock;

    public SyncService(SyncEventRepository events, PosUsers posUsers, Clock clock) {
        this.events = events;
        this.posUsers = posUsers;
        this.clock = clock;
    }

    public record EventOutcome(UUID eventId, SyncResult result) {}

    public record Snapshot(Instant generatedAt, List<PosUser> users) {}

    @Transactional
    public List<EventOutcome> upload(Long restaurantId, Long uploadedBy, List<SyncEvent> batch) {
        Instant now = clock.instant();
        return batch.stream()
                .map(
                        event ->
                                new EventOutcome(
                                        event.eventId(),
                                        store(restaurantId, uploadedBy, event, now)))
                .toList();
    }

    private SyncResult store(Long restaurantId, Long uploadedBy, SyncEvent event, Instant now) {
        if (!restaurantId.equals(event.restaurantId())) {
            log.warn(
                    "Device {} uploaded event {} of restaurant {} to restaurant {}",
                    uploadedBy,
                    event.eventId(),
                    event.restaurantId(),
                    restaurantId);
            return SyncResult.REJECTED;
        }
        return events.insertIfAbsent(event, uploadedBy, now)
                ? SyncResult.APPLIED
                : SyncResult.DUPLICATE;
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot(Long restaurantId) {
        return new Snapshot(clock.instant(), posUsers.ofRestaurant(restaurantId));
    }
}
