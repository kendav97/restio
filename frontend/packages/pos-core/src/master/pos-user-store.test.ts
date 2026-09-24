import { describe, expect, it } from "vitest";
import { migrate } from "../db/migrations.js";
import { inMemoryDriver } from "../testing/node-sqlite-driver.js";
import { PosUserStore, type PosUser } from "./pos-user-store.js";

const ANA: PosUser = {
  id: 2,
  displayName: "Ana",
  pinHash: "$2a$10$hash",
  roles: ["WAITER"],
  permissions: ["ORDER_CREATE"],
};

function store(): PosUserStore {
  const db = inMemoryDriver();
  migrate(db);
  return new PosUserStore(db);
}

describe("PosUserStore", () => {
  it("guarda la instantánea y la lee por id", () => {
    const users = store();

    users.replaceAll([ANA], "2026-09-24T10:00:00Z");

    expect(users.get(2)).toEqual(ANA);
    expect(users.get(3)).toBeUndefined();
    expect(users.snapshotAt()).toBe("2026-09-24T10:00:00Z");
  });

  it("una instantánea nueva reemplaza a la anterior", () => {
    const users = store();
    users.replaceAll([ANA, { ...ANA, id: 3, displayName: "Beto" }], "t1");

    users.replaceAll([{ ...ANA, displayName: "Ana María" }], "t2");

    expect(users.list().map((u) => u.displayName)).toEqual(["Ana María"]);
  });

  it("sin descarga previa no hay usuarios ni fecha", () => {
    const users = store();

    expect(users.list()).toEqual([]);
    expect(users.snapshotAt()).toBeNull();
  });
});
