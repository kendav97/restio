import type { SqlDriver } from "../db/driver.js";
import { EVENT_SCHEMA_VERSION } from "../version.js";
import type { DomainEvent, StoredEvent } from "./event.js";
import type { EventStore } from "./event-store.js";
import { HlcClock } from "./hlc.js";

/** Who is recording: the linked device and the user logged in with their PIN. */
export interface RecordingContext {
  readonly restaurantId: number;
  readonly deviceId: string;
  readonly userId: number | null;
  /** Epoch of the current leader; 0 while there is none. */
  readonly epoch: number;
}

export interface NewEvent<P> {
  readonly type: string;
  readonly aggregateId: string;
  readonly payload: P;
}

const HLC_KEY = "hlc.last";

/**
 * Turns operational changes into events and puts them in the outbox.
 * The last HLC is persisted with every event so the clock survives app restarts.
 */
export class EventRecorder {
  private readonly clock: HlcClock;

  constructor(
    private readonly db: SqlDriver,
    private readonly store: EventStore,
    private readonly context: () => RecordingContext,
    private readonly newId: () => string = () => globalThis.crypto.randomUUID(),
    private readonly now: () => number = Date.now,
  ) {
    const saved = db.query<{ value: string }>("SELECT value FROM sync_state WHERE key = ?", [
      HLC_KEY,
    ])[0];
    this.clock = new HlcClock(context().deviceId, now, saved?.value);
  }

  record<P>(event: NewEvent<P>): DomainEvent<P> {
    const ctx = this.context();
    return this.db.transaction(() => {
      const hlc = this.clock.tick();
      const recorded: DomainEvent<P> = {
        eventId: this.newId(),
        type: event.type,
        aggregateId: event.aggregateId,
        restaurantId: ctx.restaurantId,
        deviceId: ctx.deviceId,
        userId: ctx.userId,
        schemaVersion: EVENT_SCHEMA_VERSION,
        payload: event.payload,
        hlc,
        epoch: ctx.epoch,
        seq: null,
        createdAt: new Date(this.now()).toISOString(),
      };
      this.store.append(recorded);
      this.db.execute("INSERT OR REPLACE INTO sync_state (key, value) VALUES (?, ?)", [
        HLC_KEY,
        hlc,
      ]);
      return recorded;
    });
  }
}

/**
 * Leader of itself: with a single device (stage 1) it confirms its own outbox, assigning
 * consecutive `seq` numbers. Same code path a real leader will follow in stage 2.
 */
export class SelfLeader {
  constructor(
    private readonly db: SqlDriver,
    private readonly store: EventStore,
    private readonly epoch: number,
  ) {}

  /** @returns the events confirmed in this call, in `seq` order */
  confirmPending(): StoredEvent[] {
    return this.db.transaction(() => {
      let seq = this.store.lastSeq();
      const confirmed: StoredEvent[] = [];
      for (const event of this.store.pending()) {
        seq += 1;
        this.store.confirm(event.eventId, seq, this.epoch);
        confirmed.push({ ...event, seq, epoch: this.epoch, status: "CONFIRMED" });
      }
      return confirmed;
    });
  }
}
