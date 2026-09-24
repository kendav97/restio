import {
  unwrap,
  type ApiClient,
  type SnapshotResponse,
  type SyncEventResult,
} from "@restio/api-client";
import type { DomainEvent } from "@restio/pos-core";
import type { DeviceIdentity } from "./device-identity.js";

/** A device session: short-lived access token for `/sync`. */
export interface DeviceSession {
  readonly accessToken: string;
  /** Epoch milliseconds after which the token must not be used. */
  readonly expiresAt: number;
}

/** The cloud endpoints a device uses. A port, so the sync logic is tested without HTTP. */
export interface CloudTransport {
  pair(code: string): Promise<DeviceIdentity>;
  deviceLogin(identity: DeviceIdentity): Promise<DeviceSession>;
  uploadEvents(
    session: DeviceSession,
    restaurantId: number,
    events: readonly DomainEvent[],
  ): Promise<SyncEventResult[]>;
  snapshot(session: DeviceSession, restaurantId: number): Promise<SnapshotResponse>;
}

/** Margin so a token is renewed before it expires in flight. */
const EXPIRY_MARGIN_MS = 60_000;

/** {@link CloudTransport} over the generated API client. */
export function apiCloudTransport(api: ApiClient, now: () => number = Date.now): CloudTransport {
  const auth = (session: DeviceSession) => ({
    headers: { Authorization: `Bearer ${session.accessToken}` },
  });

  return {
    async pair(code) {
      const paired = await unwrap(api.POST("/api/v1/devices/pair", { body: { code } }));
      return {
        deviceId: paired.deviceId!,
        deviceUuid: paired.deviceUuid!,
        deviceCode: paired.deviceCode!,
        restaurantId: paired.restaurantId!,
        name: paired.name!,
        deviceToken: paired.deviceToken!,
      };
    },

    async deviceLogin(identity) {
      const tokens = await unwrap(
        api.POST("/api/v1/auth/device-login", {
          body: { deviceId: identity.deviceId, deviceToken: identity.deviceToken },
        }),
      );
      return {
        accessToken: tokens.accessToken!,
        expiresAt: now() + (tokens.expiresIn ?? 0) * 1000 - EXPIRY_MARGIN_MS,
      };
    },

    async uploadEvents(session, restaurantId, events) {
      const response = await unwrap(
        api.POST("/api/v1/restaurants/{restaurantId}/sync/events", {
          params: { path: { restaurantId } },
          body: { events: events.map(toDto) },
          ...auth(session),
        }),
      );
      return response.results ?? [];
    },

    snapshot(session, restaurantId) {
      return unwrap(
        api.GET("/api/v1/restaurants/{restaurantId}/sync/snapshot", {
          params: { path: { restaurantId } },
          ...auth(session),
        }),
      );
    },
  };
}

function toDto(event: DomainEvent) {
  return {
    eventId: event.eventId,
    type: event.type,
    aggregateId: event.aggregateId,
    restaurantId: event.restaurantId,
    deviceId: event.deviceId,
    ...(event.userId === null ? {} : { userId: event.userId }),
    schemaVersion: event.schemaVersion,
    payload: event.payload as Record<string, never>,
    hlc: event.hlc,
    epoch: event.epoch,
    ...(event.seq === null ? {} : { seq: event.seq }),
    createdAt: event.createdAt,
  };
}
