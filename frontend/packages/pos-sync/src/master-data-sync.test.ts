import type { PosUser } from "@restio/pos-core";
import { describe, expect, it } from "vitest";
import type { CloudTransport } from "./cloud-transport.js";
import type { DeviceIdentity } from "./device-identity.js";
import { DeviceSessions } from "./device-sessions.js";
import { MasterDataSync } from "./master-data-sync.js";

const IDENTITY: DeviceIdentity = {
  deviceId: 7,
  deviceUuid: "00000000-0000-4000-8000-000000000007",
  deviceCode: "D01",
  restaurantId: 1,
  name: "TPV",
  deviceToken: "secret",
};

const cloud: CloudTransport = {
  pair: () => Promise.resolve(IDENTITY),
  deviceLogin: () => Promise.resolve({ accessToken: "t", expiresAt: Number.MAX_SAFE_INTEGER }),
  uploadEvents: () => Promise.resolve([]),
  snapshot: () =>
    Promise.resolve({
      generatedAt: "2026-09-24T10:00:00Z",
      users: [
        { id: 101, displayName: "Camarero Demo", pinHash: "$2a$10$x", roles: ["WAITER"] },
        { id: 102, displayName: "Sin PIN" },
      ],
    }),
};

describe("MasterDataSync", () => {
  it("reemplaza los usuarios locales por los de la instantánea con PIN", async () => {
    let saved: { users: readonly PosUser[]; at: string } | null = null;
    const target = {
      replaceAll: (users: readonly PosUser[], at: string) => (saved = { users, at }),
    };

    const count = await new MasterDataSync(
      cloud,
      target,
      new DeviceSessions(cloud, () => IDENTITY),
    ).refresh();

    expect(count).toBe(1);
    expect(saved).toEqual({
      at: "2026-09-24T10:00:00Z",
      users: [
        {
          id: 101,
          displayName: "Camarero Demo",
          pinHash: "$2a$10$x",
          roles: ["WAITER"],
          permissions: [],
        },
      ],
    });
  });
});
