/** A value that can be bound to a SQL parameter. */
export type SqlValue = string | number | null;

/** A result row, keyed by column name. */
export type SqlRow = Record<string, SqlValue>;

/**
 * Minimal synchronous SQLite access used by pos-core.
 *
 * The device implements it on top of op-sqlite (SQLCipher); tests use node:sqlite.
 * Keeping it synchronous makes event recording atomic without juggling promises.
 */
export interface SqlDriver {
  /** Runs a statement that returns no rows. */
  execute(sql: string, params?: readonly SqlValue[]): { changes: number };
  /** Runs a query and returns every row. */
  query<T extends SqlRow = SqlRow>(sql: string, params?: readonly SqlValue[]): T[];
  /** Runs `work` inside a transaction; rolls back if it throws. */
  transaction<T>(work: () => T): T;
}
