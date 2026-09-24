import type { SqlDriver, SqlRow } from "../db/driver.js";
import type { DomainEvent, EventStatus, StoredEvent } from "./event.js";

interface EventRow extends SqlRow {
  event_id: string;
  type: string;
  aggregate_id: string;
  restaurant_id: number;
  device_id: string;
  user_id: number | null;
  schema_version: number;
  payload: string;
  hlc: string;
  epoch: number;
  seq: number | null;
  status: string;
  created_at: string;
  synced_at: string | null;
}

const COLUMNS =
  "event_id, type, aggregate_id, restaurant_id, device_id, user_id, schema_version, payload, hlc, epoch, seq, status, created_at, synced_at";

/**
 * Local event log. Pending events form the outbox; confirmed ones carry the leader's `seq`.
 * Appending is idempotent by `eventId`, so replays from the leader or the cloud are harmless.
 */
export class EventStore {
  constructor(private readonly db: SqlDriver) {}

  /**
   * Stores an event unless one with the same `eventId` already exists.
   *
   * @returns `true` if it was new
   */
  append(
    event: DomainEvent,
    status: EventStatus = event.seq === null ? "PENDING" : "CONFIRMED",
  ): boolean {
    const { changes } = this.db.execute(
      `INSERT OR IGNORE INTO events (${COLUMNS}) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)`,
      [
        event.eventId,
        event.type,
        event.aggregateId,
        event.restaurantId,
        event.deviceId,
        event.userId,
        event.schemaVersion,
        JSON.stringify(event.payload),
        event.hlc,
        event.epoch,
        event.seq,
        status,
        event.createdAt,
      ],
    );
    return changes > 0;
  }

  get(eventId: string): StoredEvent | undefined {
    const row = this.db.query<EventRow>(`SELECT ${COLUMNS} FROM events WHERE event_id = ?`, [
      eventId,
    ])[0];
    return row && toEvent(row);
  }

  /** The outbox: events not yet confirmed by the leader, oldest first. */
  pending(limit = 500): StoredEvent[] {
    return this.db
      .query<EventRow>(
        `SELECT ${COLUMNS} FROM events WHERE status = 'PENDING' ORDER BY hlc LIMIT ?`,
        [limit],
      )
      .map(toEvent);
  }

  /** Confirmed events not yet acknowledged by the cloud, in `seq` order. */
  unsynced(limit = 500): StoredEvent[] {
    return this.db
      .query<EventRow>(
        `SELECT ${COLUMNS} FROM events WHERE status = 'CONFIRMED' AND synced_at IS NULL ORDER BY seq LIMIT ?`,
        [limit],
      )
      .map(toEvent);
  }

  /** How many confirmed events the cloud has not acknowledged yet. */
  countUnsynced(): number {
    const row = this.db.query<{ n: number }>(
      "SELECT COUNT(*) AS n FROM events WHERE status = 'CONFIRMED' AND synced_at IS NULL",
    )[0];
    return row?.n ?? 0;
  }

  /** Every event of an aggregate in causal order. */
  forAggregate(aggregateId: string): StoredEvent[] {
    return this.db
      .query<EventRow>(`SELECT ${COLUMNS} FROM events WHERE aggregate_id = ? ORDER BY hlc`, [
        aggregateId,
      ])
      .map(toEvent);
  }

  /** Highest `seq` confirmed so far (0 if none): tells whether this log is up to date. */
  lastSeq(): number {
    const row = this.db.query<{ last: number | null }>("SELECT MAX(seq) AS last FROM events")[0];
    return row?.last ?? 0;
  }

  /** Records the leader's confirmation. Only pending events can be confirmed. */
  confirm(eventId: string, seq: number, epoch: number): void {
    const { changes } = this.db.execute(
      "UPDATE events SET status = 'CONFIRMED', seq = ?, epoch = ? WHERE event_id = ? AND status = 'PENDING'",
      [seq, epoch, eventId],
    );
    if (changes === 0) throw new Error(`Event ${eventId} is not pending`);
  }

  reject(eventId: string): void {
    this.db.execute(
      "UPDATE events SET status = 'REJECTED' WHERE event_id = ? AND status = 'PENDING'",
      [eventId],
    );
  }

  /** Marks events as accepted by the cloud. */
  markSynced(eventIds: readonly string[], at: string): void {
    this.db.transaction(() => {
      for (const id of eventIds) {
        this.db.execute(
          "UPDATE events SET synced_at = ? WHERE event_id = ? AND synced_at IS NULL",
          [at, id],
        );
      }
    });
  }
}

function toEvent(row: EventRow): StoredEvent {
  return {
    eventId: row.event_id,
    type: row.type,
    aggregateId: row.aggregate_id,
    restaurantId: row.restaurant_id,
    deviceId: row.device_id,
    userId: row.user_id,
    schemaVersion: row.schema_version,
    payload: JSON.parse(row.payload) as unknown,
    hlc: row.hlc,
    epoch: row.epoch,
    seq: row.seq,
    status: row.status as EventStatus,
    createdAt: row.created_at,
    syncedAt: row.synced_at,
  };
}
