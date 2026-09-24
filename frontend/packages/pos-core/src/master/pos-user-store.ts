import type { SqlDriver, SqlRow } from "../db/driver.js";

/** An employee who can log in on the device with their PIN. Master data, downloaded from the cloud. */
export interface PosUser {
  readonly id: number;
  readonly displayName: string;
  /** BCrypt hash, the same the cloud stores. The PIN itself never reaches the device. */
  readonly pinHash: string;
  readonly roles: readonly string[];
  readonly permissions: readonly string[];
}

interface PosUserRow extends SqlRow {
  id: number;
  display_name: string;
  pin_hash: string;
  roles: string;
  permissions: string;
}

const SNAPSHOT_KEY = "snapshot.generatedAt";

/** Local copy of the restaurant's PIN users, so employees can log in without internet. */
export class PosUserStore {
  constructor(private readonly db: SqlDriver) {}

  /** Replaces every user with the ones of a cloud snapshot (users removed there disappear). */
  replaceAll(users: readonly PosUser[], generatedAt: string): void {
    this.db.transaction(() => {
      this.db.execute("DELETE FROM pos_users");
      for (const user of users) {
        this.db.execute(
          "INSERT INTO pos_users (id, display_name, pin_hash, roles, permissions) VALUES (?, ?, ?, ?, ?)",
          [
            user.id,
            user.displayName,
            user.pinHash,
            JSON.stringify(user.roles),
            JSON.stringify(user.permissions),
          ],
        );
      }
      this.db.execute("INSERT OR REPLACE INTO sync_state (key, value) VALUES (?, ?)", [
        SNAPSHOT_KEY,
        generatedAt,
      ]);
    });
  }

  list(): PosUser[] {
    return this.db
      .query<PosUserRow>("SELECT * FROM pos_users ORDER BY display_name, id")
      .map(toUser);
  }

  get(id: number): PosUser | undefined {
    const row = this.db.query<PosUserRow>("SELECT * FROM pos_users WHERE id = ?", [id])[0];
    return row && toUser(row);
  }

  /** When the data was generated in the cloud, or `null` before the first download. */
  snapshotAt(): string | null {
    const row = this.db.query<{ value: string }>("SELECT value FROM sync_state WHERE key = ?", [
      SNAPSHOT_KEY,
    ])[0];
    return row?.value ?? null;
  }
}

function toUser(row: PosUserRow): PosUser {
  return {
    id: row.id,
    displayName: row.display_name,
    pinHash: row.pin_hash,
    roles: JSON.parse(row.roles) as string[],
    permissions: JSON.parse(row.permissions) as string[],
  };
}
