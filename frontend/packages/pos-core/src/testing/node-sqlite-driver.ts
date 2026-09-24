import { createRequire } from "node:module";
import type * as NodeSqlite from "node:sqlite";
import type { SqlDriver, SqlRow, SqlValue } from "../db/driver.js";

// Vite 5 does not know the `node:sqlite` builtin, so it is loaded through require.
const { DatabaseSync } = createRequire(import.meta.url)("node:sqlite") as typeof NodeSqlite;

/** In-memory SqlDriver for tests, backed by Node's built-in SQLite. */
export function inMemoryDriver(): SqlDriver & { close(): void } {
  const db = new DatabaseSync(":memory:");
  let depth = 0;
  return {
    execute(sql: string, params: readonly SqlValue[] = []) {
      const result = db.prepare(sql).run(...params);
      return { changes: Number(result.changes) };
    },
    query<T extends SqlRow = SqlRow>(sql: string, params: readonly SqlValue[] = []): T[] {
      return db.prepare(sql).all(...params) as T[];
    },
    transaction<T>(work: () => T): T {
      const savepoint = `sp${depth++}`;
      db.exec(`SAVEPOINT ${savepoint}`);
      try {
        const result = work();
        db.exec(`RELEASE ${savepoint}`);
        return result;
      } catch (error) {
        db.exec(`ROLLBACK TO ${savepoint}`);
        db.exec(`RELEASE ${savepoint}`);
        throw error;
      } finally {
        depth--;
      }
    },
    close: () => db.close(),
  };
}
