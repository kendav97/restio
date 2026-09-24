import type { DeviceIdentity } from "@restio/pos-sync";
import * as SecureStore from "expo-secure-store";

const IDENTITY_ENTRY = "restio.device.identity";

/**
 * The device identity lives in the platform keystore, not in SQLite: it carries `deviceToken`,
 * the secret the device logs in to the cloud with.
 */
export async function loadIdentity(): Promise<DeviceIdentity | null> {
  const stored = await SecureStore.getItemAsync(IDENTITY_ENTRY);
  return stored ? (JSON.parse(stored) as DeviceIdentity) : null;
}

export async function saveIdentity(identity: DeviceIdentity): Promise<void> {
  await SecureStore.setItemAsync(IDENTITY_ENTRY, JSON.stringify(identity));
}
