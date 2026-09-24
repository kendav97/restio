import { ApiError, type SyncEventResult } from "@restio/api-client";
import type { DomainEvent, StoredEvent } from "@restio/pos-core";
import { describe, expect, it } from "vitest";
import type { CloudTransport, DeviceSession } from "./cloud-transport.js";
import { CloudUploader, type UploadSource } from "./cloud-uploader.js";
import { DeviceSessions } from "./device-sessions.js";
import type { DeviceIdentity } from "./device-identity.js";

const IDENTITY: DeviceIdentity = {
  deviceId: 7,
  deviceUuid: "00000000-0000-4000-8000-000000000007",
  deviceCode: "D01",
  restaurantId: 1,
  name: "TPV",
  deviceToken: "secret",
};

function event(seq: number): StoredEvent {
  return {
    eventId: `e${seq}`,
    type: "TableOpened",
    aggregateId: "a",
    restaurantId: 1,
    deviceId: IDENTITY.deviceUuid,
    userId: null,
    schemaVersion: 1,
    payload: {},
    hlc: `h${seq}`,
    epoch: 0,
    seq,
    createdAt: "2026-09-24T10:00:00.000Z",
    status: "CONFIRMED",
    syncedAt: null,
  };
}

/** Outbox in memory with the same contract as EventStore.unsynced / markSynced. */
function source(count: number): UploadSource & { synced: Set<string> } {
  const events = Array.from({ length: count }, (_, i) => event(i + 1));
  const synced = new Set<string>();
  return {
    synced,
    unsynced: (limit = 500) => events.filter((e) => !synced.has(e.eventId)).slice(0, limit),
    markSynced: (ids) => ids.forEach((id) => synced.add(id)),
  };
}

/** Cloud that stores events by id, like the backend. */
class FakeCloud implements CloudTransport {
  readonly stored = new Set<string>();
  logins = 0;
  offline = false;
  failNextWith401 = false;
  rejectIds = new Set<string>();

  pair(): Promise<DeviceIdentity> {
    return Promise.resolve(IDENTITY);
  }

  deviceLogin(): Promise<DeviceSession> {
    this.logins += 1;
    return Promise.resolve({ accessToken: `t${this.logins}`, expiresAt: Number.MAX_SAFE_INTEGER });
  }

  uploadEvents(_s: DeviceSession, _r: number, events: readonly DomainEvent[]) {
    if (this.offline) return Promise.reject(new TypeError("Network request failed"));
    if (this.failNextWith401) {
      this.failNextWith401 = false;
      return Promise.reject(new ApiError({ status: 401, code: "UNAUTHORIZED" }));
    }
    return Promise.resolve(
      events.map((e): SyncEventResult => {
        if (this.rejectIds.has(e.eventId)) return { eventId: e.eventId, status: "REJECTED" };
        const status = this.stored.has(e.eventId) ? "DUPLICATE" : "APPLIED";
        this.stored.add(e.eventId);
        return { eventId: e.eventId, status };
      }),
    );
  }

  snapshot() {
    return Promise.resolve({ users: [] });
  }
}

describe("CloudUploader", () => {
  it("sube todos los eventos confirmados en lotes y los marca sincronizados", async () => {
    const cloud = new FakeCloud();
    const outbox = source(1200);

    const report = await new CloudUploader(
      cloud,
      outbox,
      new DeviceSessions(cloud, () => IDENTITY),
    ).uploadPending();

    expect(report).toEqual({ synced: 1200, rejected: [] });
    expect(cloud.stored.size).toBe(1200);
    expect(outbox.unsynced()).toEqual([]);
    expect(cloud.logins).toBe(1);
  });

  it("sin red falla sin marcar nada y el reintento sube lo pendiente", async () => {
    const cloud = new FakeCloud();
    const outbox = source(3);
    const uploader = new CloudUploader(cloud, outbox, new DeviceSessions(cloud, () => IDENTITY));
    cloud.offline = true;

    await expect(uploader.uploadPending()).rejects.toThrow();
    expect(outbox.synced.size).toBe(0);

    cloud.offline = false;
    await expect(uploader.uploadPending()).resolves.toEqual({ synced: 3, rejected: [] });
  });

  it("un evento que la nube ya tenía cuenta como sincronizado", async () => {
    const cloud = new FakeCloud();
    cloud.stored.add("e1");
    const outbox = source(2);

    await new CloudUploader(
      cloud,
      outbox,
      new DeviceSessions(cloud, () => IDENTITY),
    ).uploadPending();

    expect(outbox.synced).toEqual(new Set(["e1", "e2"]));
  });

  it("ante un 401 vuelve a iniciar sesión una vez y reintenta", async () => {
    const cloud = new FakeCloud();
    const uploader = new CloudUploader(cloud, source(1), new DeviceSessions(cloud, () => IDENTITY));
    cloud.failNextWith401 = true;

    await expect(uploader.uploadPending()).resolves.toEqual({ synced: 1, rejected: [] });
    expect(cloud.logins).toBe(2);
  });

  it("los rechazados quedan sin sincronizar y no bloquean al resto", async () => {
    const cloud = new FakeCloud();
    cloud.rejectIds.add("e1");
    const outbox = source(3);

    const report = await new CloudUploader(
      cloud,
      outbox,
      new DeviceSessions(cloud, () => IDENTITY),
    ).uploadPending();

    expect(report).toEqual({ synced: 2, rejected: ["e1"] });
    expect(outbox.unsynced().map((e) => e.eventId)).toEqual(["e1"]);
  });
});
