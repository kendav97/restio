/** Lifecycle of an event in the local log. */
export type EventStatus =
  /** Recorded on this device, waiting for the leader to confirm it (outbox). */
  | "PENDING"
  /** Confirmed by the leader, which assigned its `seq`. */
  | "CONFIRMED"
  /** Rejected by the leader; its effect must be reverted. */
  | "REJECTED"
  /** Kept aside for a manager to resolve (see restio-architecture-sync). */
  | "CONFLICT";

/** Immutable record of one operational change, shared with the leader and the cloud. */
export interface DomainEvent<P = unknown> {
  readonly eventId: string;
  readonly type: string;
  readonly aggregateId: string;
  readonly restaurantId: number;
  readonly deviceId: string;
  readonly userId: number | null;
  readonly schemaVersion: number;
  readonly payload: P;
  readonly hlc: string;
  readonly epoch: number;
  readonly seq: number | null;
  readonly createdAt: string;
}

/** An event as stored locally, with its sync bookkeeping. */
export interface StoredEvent<P = unknown> extends DomainEvent<P> {
  readonly status: EventStatus;
  readonly syncedAt: string | null;
}
