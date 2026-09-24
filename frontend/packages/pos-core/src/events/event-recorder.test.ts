import { beforeEach, describe, expect, it } from "vitest";
import type { SqlDriver } from "../db/driver.js";
import { migrate } from "../db/migrations.js";
import { inMemoryDriver } from "../testing/node-sqlite-driver.js";
import { EventRecorder, SelfLeader, type RecordingContext } from "./event-recorder.js";
import { EventStore } from "./event-store.js";

const context: RecordingContext = { restaurantId: 1, deviceId: "dev-a", userId: 1001, epoch: 0 };

describe("registro de eventos local", () => {
  let db: SqlDriver;
  let store: EventStore;
  let now: number;
  let ids: number;

  const recorder = () =>
    new EventRecorder(
      db,
      store,
      () => context,
      () => `00000000-0000-4000-8000-${String(++ids).padStart(12, "0")}`,
      () => now,
    );

  beforeEach(() => {
    db = inMemoryDriver();
    migrate(db);
    store = new EventStore(db);
    now = Date.UTC(2026, 8, 24, 12);
    ids = 0;
  });

  it("guarda el evento en la outbox con su sobre completo", () => {
    const event = recorder().record({
      type: "TableOpened",
      aggregateId: "t-1",
      payload: { guests: 2 },
    });

    expect(store.pending()).toEqual([
      {
        ...event,
        payload: { guests: 2 },
        restaurantId: 1,
        deviceId: "dev-a",
        userId: 1001,
        schemaVersion: 1,
        seq: null,
        status: "PENDING",
        syncedAt: null,
        createdAt: "2026-09-24T12:00:00.000Z",
      },
    ]);
  });

  it("es idempotente por eventId", () => {
    const event = recorder().record({ type: "TableOpened", aggregateId: "t-1", payload: {} });

    expect(store.append(event)).toBe(false);
    expect(store.pending()).toHaveLength(1);
  });

  it("mantiene el orden causal aunque la app se reinicie con el reloj atrasado", () => {
    const first = recorder().record({ type: "TableOpened", aggregateId: "t-1", payload: {} });
    now -= 60_000;
    const second = recorder().record({ type: "TableClosed", aggregateId: "t-1", payload: {} });

    expect(second.hlc > first.hlc).toBe(true);
    expect(store.forAggregate("t-1").map((e) => e.type)).toEqual(["TableOpened", "TableClosed"]);
  });

  it("como líder de sí mismo confirma la outbox con seq consecutivos", () => {
    const rec = recorder();
    rec.record({ type: "A", aggregateId: "x", payload: {} });
    rec.record({ type: "B", aggregateId: "x", payload: {} });
    const leader = new SelfLeader(db, store, 1);

    expect(leader.confirmPending().map((e) => [e.type, e.seq])).toEqual([
      ["A", 1],
      ["B", 2],
    ]);
    rec.record({ type: "C", aggregateId: "x", payload: {} });
    expect(leader.confirmPending().map((e) => e.seq)).toEqual([3]);
    expect(store.pending()).toHaveLength(0);
    expect(store.lastSeq()).toBe(3);
  });

  it("lista los confirmados pendientes de subir y los marca como sincronizados", () => {
    const rec = recorder();
    const a = rec.record({ type: "A", aggregateId: "x", payload: {} });
    rec.record({ type: "B", aggregateId: "x", payload: {} });
    new SelfLeader(db, store, 1).confirmPending();

    store.markSynced([a.eventId], "2026-09-24T12:05:00.000Z");

    expect(store.unsynced().map((e) => e.type)).toEqual(["B"]);
    expect(store.countUnsynced()).toBe(1);
    expect(store.get(a.eventId)?.syncedAt).toBe("2026-09-24T12:05:00.000Z");
  });

  it("solo confirma eventos pendientes", () => {
    const event = recorder().record({ type: "A", aggregateId: "x", payload: {} });
    store.reject(event.eventId);

    expect(() => store.confirm(event.eventId, 1, 1)).toThrow(/not pending/);
    expect(store.get(event.eventId)?.status).toBe("REJECTED");
  });
});
