/**
 * pos-core: the local (device-side) domain of Restio.
 *
 * Domain rules are pure functions: `decide(state, command) -> events | error` and
 * `apply(state, event) -> state`. Persistence goes through the `SqlDriver` port, so nothing
 * here depends on React, the network or a specific SQLite binding.
 */
export { EVENT_SCHEMA_VERSION } from "./version.js";
export type { SqlDriver, SqlRow, SqlValue } from "./db/driver.js";
export { MIGRATIONS, currentVersion, migrate, type Migration } from "./db/migrations.js";
export type { DomainEvent, EventStatus, StoredEvent } from "./events/event.js";
export { EventStore } from "./events/event-store.js";
export {
  EventRecorder,
  SelfLeader,
  type NewEvent,
  type RecordingContext,
} from "./events/event-recorder.js";
export { PosUserStore, type PosUser } from "./master/pos-user-store.js";
export { HlcClock, decodeHlc, encodeHlc, type Hlc } from "./events/hlc.js";
