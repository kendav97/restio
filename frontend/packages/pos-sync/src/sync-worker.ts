import type { UploadReport } from "./cloud-uploader.js";

export interface SyncStatus {
  /** Whether the last attempt reached the cloud. `null` before the first attempt. */
  readonly online: boolean | null;
  readonly running: boolean;
  /** ISO time of the last successful round. */
  readonly lastSyncAt: string | null;
  /** Events the cloud refused in the last round; they need a manager's attention. */
  readonly rejected: readonly string[];
}

export interface SyncTasks {
  uploadPending(): Promise<UploadReport>;
  refreshMasterData(): Promise<unknown>;
}

export interface SyncWorkerOptions {
  /** Pause between rounds while everything goes well. */
  readonly intervalMs?: number;
  /** Pause after a failed round (no network, cloud down). */
  readonly retryMs?: number;
  readonly now?: () => number;
}

/**
 * Runs sync rounds in the background while the app is open: upload the outbox, then refresh the
 * master data. Never runs two rounds at once; a failed round is simply retried later, so an event
 * recorded offline reaches the cloud on the first round after the connection comes back.
 */
export class SyncWorker {
  private status: SyncStatus = { online: null, running: false, lastSyncAt: null, rejected: [] };
  private timer: ReturnType<typeof setTimeout> | null = null;
  private current: Promise<SyncStatus> | null = null;
  private started = false;
  private readonly listeners = new Set<(status: SyncStatus) => void>();
  private readonly intervalMs: number;
  private readonly retryMs: number;
  private readonly now: () => number;

  constructor(
    private readonly tasks: SyncTasks,
    options: SyncWorkerOptions = {},
  ) {
    this.intervalMs = options.intervalMs ?? 60_000;
    this.retryMs = options.retryMs ?? 15_000;
    this.now = options.now ?? Date.now;
  }

  start(): void {
    if (this.started) return;
    this.started = true;
    void this.syncNow();
  }

  stop(): void {
    this.started = false;
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
  }

  /** Runs a round now (or joins the one in progress), e.g. right after recording an event. */
  syncNow(): Promise<SyncStatus> {
    this.current ??= this.round().finally(() => {
      this.current = null;
    });
    return this.current;
  }

  getStatus(): SyncStatus {
    return this.status;
  }

  subscribe(listener: (status: SyncStatus) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private async round(): Promise<SyncStatus> {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    this.update({ running: true });
    let ok = false;
    try {
      const report = await this.tasks.uploadPending();
      await this.tasks.refreshMasterData();
      ok = true;
      this.update({
        online: true,
        running: false,
        lastSyncAt: new Date(this.now()).toISOString(),
        rejected: report.rejected,
      });
    } catch {
      this.update({ online: false, running: false });
    }
    if (this.started) {
      this.timer = setTimeout(() => void this.syncNow(), ok ? this.intervalMs : this.retryMs);
    }
    return this.status;
  }

  private update(change: Partial<SyncStatus>): void {
    this.status = { ...this.status, ...change };
    for (const listener of this.listeners) listener(this.status);
  }
}
