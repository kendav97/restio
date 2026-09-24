import { open } from "@op-engineering/op-sqlite";
import { EventStore, migrate, type SqlDriver } from "@restio/pos-core";
import * as Crypto from "expo-crypto";
import * as SecureStore from "expo-secure-store";
import { opSqliteDriver } from "./op-sqlite-driver";

const DATABASE_NAME = "restio.db";
const KEY_ENTRY = "restio.db.key";

export interface LocalDatabase {
  readonly driver: SqlDriver;
  readonly events: EventStore;
  readonly schemaVersion: number;
}

/**
 * Opens the encrypted local database (SQLCipher) and migrates it.
 * The key is generated on first launch and kept in the platform keystore.
 */
export async function openLocalDatabase(): Promise<LocalDatabase> {
  const driver = opSqliteDriver(
    open({ name: DATABASE_NAME, encryptionKey: await encryptionKey() }),
  );
  const schemaVersion = migrate(driver);
  return { driver, events: new EventStore(driver), schemaVersion };
}

async function encryptionKey(): Promise<string> {
  const existing = await SecureStore.getItemAsync(KEY_ENTRY);
  if (existing) return existing;
  const bytes = await Crypto.getRandomBytesAsync(32);
  const key = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
  await SecureStore.setItemAsync(KEY_ENTRY, key);
  return key;
}
