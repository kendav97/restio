// pos-sync: device linking, cloud upload and master data download (stage 0.5); leader election
// and LAN replication arrive in stage 2.
export { pairingQrContent, parsePairingCode, type DeviceIdentity } from "./device-identity.js";
export { apiCloudTransport, type CloudTransport, type DeviceSession } from "./cloud-transport.js";
export { DeviceSessions } from "./device-sessions.js";
export { CloudUploader, type UploadReport, type UploadSource } from "./cloud-uploader.js";
export { MasterDataSync, type MasterDataTarget } from "./master-data-sync.js";
export { PinAuthenticator, type PosUserLookup } from "./pin-authenticator.js";
export {
  SyncWorker,
  type SyncStatus,
  type SyncTasks,
  type SyncWorkerOptions,
} from "./sync-worker.js";
