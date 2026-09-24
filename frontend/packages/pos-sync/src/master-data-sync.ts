import type { PosUser } from "@restio/pos-core";
import type { CloudTransport } from "./cloud-transport.js";
import type { DeviceSessions } from "./device-sessions.js";

/** The part of pos-core's `PosUserStore` the download needs. */
export interface MasterDataTarget {
  replaceAll(users: readonly PosUser[], generatedAt: string): void;
}

/**
 * Downloads the master data (for now, the PIN users) and replaces the local copy.
 * Full snapshot every time: the API has no `since` yet and the data is small.
 */
export class MasterDataSync {
  constructor(
    private readonly transport: CloudTransport,
    private readonly target: MasterDataTarget,
    private readonly sessions: DeviceSessions,
  ) {}

  async refresh(): Promise<number> {
    const { restaurantId } = this.sessions.identity();
    const snapshot = await this.sessions.call((session) =>
      this.transport.snapshot(session, restaurantId),
    );
    const users: PosUser[] = (snapshot.users ?? [])
      .filter((u) => u.pinHash)
      .map((u) => ({
        id: u.id!,
        displayName: u.displayName ?? "",
        pinHash: u.pinHash!,
        roles: u.roles ?? [],
        permissions: u.permissions ?? [],
      }));
    this.target.replaceAll(users, snapshot.generatedAt ?? new Date().toISOString());
    return users.length;
  }
}
