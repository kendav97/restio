import type { StoredEvent } from "@restio/pos-core";
import type { CloudTransport } from "./cloud-transport.js";
import type { DeviceSessions } from "./device-sessions.js";

/** The part of pos-core's `EventStore` the uploader needs. */
export interface UploadSource {
  unsynced(limit?: number): StoredEvent[];
  markSynced(eventIds: readonly string[], at: string): void;
}

export interface UploadReport {
  /** Events the cloud now holds (new or already there). */
  readonly synced: number;
  /** Events the cloud refused; they stay unsynced for a manager to look at. */
  readonly rejected: readonly string[];
}

const BATCH_SIZE = 500;

/**
 * Uploads the confirmed events the cloud does not have yet, in `seq` order and in batches.
 * Idempotent end to end: an event the cloud already stored comes back as `DUPLICATE` and is
 * simply marked synced. Without network it fails and the next call retries; nothing is lost.
 */
export class CloudUploader {
  constructor(
    private readonly transport: CloudTransport,
    private readonly source: UploadSource,
    private readonly sessions: DeviceSessions,
    private readonly now: () => number = Date.now,
  ) {}

  async uploadPending(): Promise<UploadReport> {
    let synced = 0;
    const rejected: string[] = [];
    for (;;) {
      const batch = this.source
        .unsynced(BATCH_SIZE + rejected.length)
        .filter((event) => !rejected.includes(event.eventId))
        .slice(0, BATCH_SIZE);
      if (batch.length === 0) break;

      const { restaurantId } = this.sessions.identity();
      const results = await this.sessions.call((session) =>
        this.transport.uploadEvents(session, restaurantId, batch),
      );
      const accepted = results
        .filter((r) => r.status === "APPLIED" || r.status === "DUPLICATE")
        .map((r) => r.eventId!);
      rejected.push(...results.filter((r) => r.status === "REJECTED").map((r) => r.eventId!));
      this.source.markSynced(accepted, new Date(this.now()).toISOString());
      synced += accepted.length;
      if (accepted.length === 0) break;
    }
    return { synced, rejected };
  }
}
