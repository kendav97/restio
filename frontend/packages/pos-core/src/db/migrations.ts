import type { SqlDriver } from "./driver.js";

/** One schema change of the local database. Versions are consecutive and never edited once shipped. */
export interface Migration {
  readonly version: number;
  readonly description: string;
  readonly statements: readonly string[];
}

export const MIGRATIONS: readonly Migration[] = [
  {
    version: 1,
    description: "event log and outbox",
    statements: [
      `CREATE TABLE events (
        event_id       TEXT    PRIMARY KEY,
        type           TEXT    NOT NULL,
        aggregate_id   TEXT    NOT NULL,
        restaurant_id  INTEGER NOT NULL,
        device_id      TEXT    NOT NULL,
        user_id        INTEGER,
        schema_version INTEGER NOT NULL,
        payload        TEXT    NOT NULL,
        hlc            TEXT    NOT NULL,
        epoch          INTEGER NOT NULL,
        seq            INTEGER UNIQUE,
        status         TEXT    NOT NULL,
        created_at     TEXT    NOT NULL,
        synced_at      TEXT
      )`,
      "CREATE INDEX idx_events_aggregate ON events (aggregate_id, hlc)",
      "CREATE INDEX idx_events_status ON events (status, hlc)",
      `CREATE TABLE sync_state (
        key   TEXT PRIMARY KEY,
        value TEXT NOT NULL
      )`,
    ],
  },
  {
    version: 2,
    description: "users with PIN from the cloud snapshot",
    statements: [
      `CREATE TABLE pos_users (
        id           INTEGER PRIMARY KEY,
        display_name TEXT    NOT NULL,
        pin_hash     TEXT    NOT NULL,
        roles        TEXT    NOT NULL,
        permissions  TEXT    NOT NULL
      )`,
    ],
  },
];

/**
 * Brings the local database up to the latest schema. The applied version lives in
 * `PRAGMA user_version`; each migration runs in its own transaction.
 *
 * @returns the schema version after migrating
 */
export function migrate(db: SqlDriver, migrations: readonly Migration[] = MIGRATIONS): number {
  let current = currentVersion(db);
  for (const migration of migrations) {
    if (migration.version <= current) continue;
    if (migration.version !== current + 1) {
      throw new Error(`Missing local migration ${current + 1} before ${migration.version}`);
    }
    db.transaction(() => {
      for (const statement of migration.statements) db.execute(statement);
      db.execute(`PRAGMA user_version = ${migration.version}`);
    });
    current = migration.version;
  }
  return current;
}

export function currentVersion(db: SqlDriver): number {
  const row = db.query<{ user_version: number }>("PRAGMA user_version")[0];
  return row?.user_version ?? 0;
}
