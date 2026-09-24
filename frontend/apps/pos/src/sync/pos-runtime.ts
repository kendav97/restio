import {
  EventRecorder,
  PosUserStore,
  SelfLeader,
  type NewEvent,
  type PosUser,
} from "@restio/pos-core";
import {
  CloudUploader,
  DeviceSessions,
  MasterDataSync,
  PinAuthenticator,
  SyncWorker,
  type CloudTransport,
  type DeviceIdentity,
} from "@restio/pos-sync";
import type { LocalDatabase } from "../db/local-database";

/** Epoch of the self-leader: a single device (stage 1) never changes leader. */
const SELF_EPOCH = 1;

/** Everything a linked device needs to operate and sync, wired once per identity. */
export interface PosRuntime {
  readonly identity: DeviceIdentity;
  readonly users: PosUserStore;
  readonly authenticator: PinAuthenticator;
  readonly worker: SyncWorker;
  /** Records an event as the given user, confirms it (self-leader) and schedules its upload. */
  record<P>(event: NewEvent<P>, user: PosUser | null): void;
  /** Confirmed events the cloud does not hold yet. */
  unsyncedCount(): number;
}

export function createPosRuntime(
  db: LocalDatabase,
  transport: CloudTransport,
  identity: DeviceIdentity,
): PosRuntime {
  const users = new PosUserStore(db.driver);
  const sessions = new DeviceSessions(transport, () => identity);
  const uploader = new CloudUploader(transport, db.events, sessions);
  const masterData = new MasterDataSync(transport, users, sessions);
  const worker = new SyncWorker({
    uploadPending: () => uploader.uploadPending(),
    refreshMasterData: () => masterData.refresh(),
  });

  let actingUser: PosUser | null = null;
  const recorder = new EventRecorder(db.driver, db.events, () => ({
    restaurantId: identity.restaurantId,
    deviceId: identity.deviceUuid,
    userId: actingUser?.id ?? null,
    epoch: SELF_EPOCH,
  }));
  const leader = new SelfLeader(db.driver, db.events, SELF_EPOCH);

  return {
    identity,
    users,
    authenticator: new PinAuthenticator(users),
    worker,
    record(event, user) {
      actingUser = user;
      recorder.record(event);
      leader.confirmPending();
      void worker.syncNow();
    },
    unsyncedCount: () => db.events.countUnsynced(),
  };
}
