import type { DB } from "@op-engineering/op-sqlite";
import type { SqlDriver, SqlRow, SqlValue } from "@restio/pos-core";

/** pos-core SqlDriver on top of op-sqlite's synchronous API. */
export function opSqliteDriver(db: DB): SqlDriver {
  let depth = 0;
  return {
    execute(sql: string, params: readonly SqlValue[] = []) {
      return { changes: db.executeSync(sql, [...params]).rowsAffected };
    },
    query<T extends SqlRow = SqlRow>(sql: string, params: readonly SqlValue[] = []): T[] {
      return db.executeSync(sql, [...params]).rows as T[];
    },
    transaction<T>(work: () => T): T {
      const savepoint = `sp${depth++}`;
      db.executeSync(`SAVEPOINT ${savepoint}`);
      try {
        const result = work();
        db.executeSync(`RELEASE ${savepoint}`);
        return result;
      } catch (error) {
        db.executeSync(`ROLLBACK TO ${savepoint}`);
        db.executeSync(`RELEASE ${savepoint}`);
        throw error;
      } finally {
        depth--;
      }
    },
  };
}
