package com.restio.sync.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.databind.JsonNode;
import com.restio.sync.domain.SyncResult;

/** Request and response bodies of {@code /sync}. Event fields match pos-core's DomainEvent. */
public final class SyncDtos {

    public static final int MAX_BATCH = 500;

    private SyncDtos() {}

    public record UploadEventsRequest(
            @NotEmpty @Size(max = MAX_BATCH) List<@Valid @NotNull EventDto> events) {}

    public record EventDto(
            @NotNull UUID eventId,
            @NotBlank @Size(max = 100) String type,
            @NotNull UUID aggregateId,
            @NotNull Long restaurantId,
            @NotNull UUID deviceId,
            Long userId,
            @NotNull @Positive Integer schemaVersion,
            @NotNull JsonNode payload,
            @NotBlank @Size(max = 64) String hlc,
            @NotNull @PositiveOrZero Long epoch,
            @Positive Long seq,
            @NotNull Instant createdAt) {}

    public record UploadEventsResponse(List<EventResultDto> results) {}

    public record EventResultDto(UUID eventId, SyncResult status) {}

    public record SnapshotResponse(Instant generatedAt, List<PosUserDto> users) {}

    public record PosUserDto(
            Long id,
            String displayName,
            String pinHash,
            Set<String> roles,
            Set<String> permissions) {}
}
