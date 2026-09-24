import { describe, expect, it } from "vitest";
import { inMemoryDriver } from "../testing/node-sqlite-driver.js";
import { MIGRATIONS, currentVersion, migrate, type Migration } from "./migrations.js";

describe("migrate", () => {
  it("aplica todas las migraciones y es idempotente", () => {
    const db = inMemoryDriver();
    const latest = MIGRATIONS.at(-1)!.version;

    expect(migrate(db)).toBe(latest);
    expect(migrate(db)).toBe(latest);
    expect(currentVersion(db)).toBe(latest);
    expect(db.query("SELECT name FROM sqlite_master WHERE name = 'events'")).toHaveLength(1);
  });

  it("revierte una migración que falla y conserva la versión anterior", () => {
    const db = inMemoryDriver();
    const broken: Migration[] = [
      ...MIGRATIONS,
      {
        version: MIGRATIONS.length + 1,
        description: "broken",
        statements: ["CREATE TABLE t (a)", "NOT SQL"],
      },
    ];

    expect(() => migrate(db, broken)).toThrow();
    expect(currentVersion(db)).toBe(MIGRATIONS.length);
    expect(db.query("SELECT name FROM sqlite_master WHERE name = 't'")).toHaveLength(0);
  });

  it("rechaza huecos en la numeración", () => {
    const db = inMemoryDriver();
    const gap: Migration[] = [{ version: 2, description: "gap", statements: [] }];

    expect(() => migrate(db, gap)).toThrow(/Missing local migration 1/);
  });
});
