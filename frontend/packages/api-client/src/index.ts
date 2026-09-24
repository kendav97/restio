// api-client: typed client generated from the cloud OpenAPI document.
// Regenerate `schema.ts` with `pnpm openapi:fetch && pnpm generate` after changing the API.
export {
  ApiError,
  createApiClient,
  unwrap,
  type ApiClient,
  type ApiClientOptions,
  type Problem,
} from "./client.js";
export type { components, operations, paths } from "./schema.js";

import type { components } from "./schema.js";

export type TokenResponse = components["schemas"]["TokenResponse"];
export type MeResponse = components["schemas"]["MeResponse"];
export type PairingResponse = components["schemas"]["PairingResponse"];
export type PairResponse = components["schemas"]["PairResponse"];
export type DeviceResponse = components["schemas"]["DeviceResponse"];
export type SyncEventDto = components["schemas"]["EventDto"];
export type SyncEventResult = components["schemas"]["EventResultDto"];
export type SnapshotResponse = components["schemas"]["SnapshotResponse"];
export type PosUserDto = components["schemas"]["PosUserDto"];
