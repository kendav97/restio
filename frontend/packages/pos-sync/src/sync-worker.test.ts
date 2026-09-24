import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SyncWorker, type SyncTasks } from "./sync-worker.js";

function tasks(): SyncTasks & { offline: boolean; uploads: number } {
  return {
    offline: false,
    uploads: 0,
    uploadPending() {
      this.uploads += 1;
      if (this.offline) return Promise.reject(new TypeError("Network request failed"));
      return Promise.resolve({ synced: 1, rejected: [] });
    },
    refreshMasterData() {
      return Promise.resolve(0);
    },
  };
}

describe("SyncWorker", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("sin red queda offline y reintenta hasta que vuelve la conexión", async () => {
    const cloud = tasks();
    cloud.offline = true;
    const worker = new SyncWorker(cloud, { intervalMs: 60_000, retryMs: 5_000 });

    worker.start();
    await vi.advanceTimersByTimeAsync(0);
    expect(worker.getStatus().online).toBe(false);

    cloud.offline = false;
    await vi.advanceTimersByTimeAsync(5_000);

    expect(cloud.uploads).toBe(2);
    expect(worker.getStatus()).toMatchObject({ online: true, running: false });
    expect(worker.getStatus().lastSyncAt).not.toBeNull();
    worker.stop();
  });

  it("no solapa rondas: syncNow durante una ronda se une a ella", async () => {
    const cloud = tasks();
    const worker = new SyncWorker(cloud);

    await Promise.all([worker.syncNow(), worker.syncNow()]);

    expect(cloud.uploads).toBe(1);
  });

  it("detenido no programa más rondas", async () => {
    const cloud = tasks();
    const worker = new SyncWorker(cloud, { intervalMs: 1_000 });

    worker.start();
    await vi.advanceTimersByTimeAsync(0);
    worker.stop();
    await vi.advanceTimersByTimeAsync(10_000);

    expect(cloud.uploads).toBe(1);
  });

  it("avisa a los suscriptores de cada cambio", async () => {
    const worker = new SyncWorker(tasks());
    const seen: boolean[] = [];
    worker.subscribe((s) => seen.push(s.running));

    await worker.syncNow();

    expect(seen).toEqual([true, false]);
  });
});
