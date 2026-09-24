import { createApiClient } from "@restio/api-client";
import { createI18n, type I18nInstance } from "@restio/i18n";
import type { PosUser } from "@restio/pos-core";
import {
  apiCloudTransport,
  type CloudTransport,
  type DeviceIdentity,
  type SyncStatus,
} from "@restio/pos-sync";
import { getLocales } from "expo-localization";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from "react";
import { openLocalDatabase, type LocalDatabase } from "./db/local-database";
import { loadIdentity, saveIdentity } from "./device/identity-storage";
import { createPosRuntime, type PosRuntime } from "./sync/pos-runtime";

/** Cloud URL. On the Android emulator, 10.0.2.2 is the host machine. */
const API_URL = process.env.EXPO_PUBLIC_API_URL ?? "http://10.0.2.2:8080";

export interface AppServices {
  readonly i18n: I18nInstance;
  readonly db: LocalDatabase;
  readonly transport: CloudTransport;
  /** `null` until the device is linked to a restaurant. */
  readonly runtime: PosRuntime | null;
  /** Employee logged in with their PIN, or `null`. */
  readonly user: PosUser | null;
  link(identity: DeviceIdentity): Promise<void>;
  logIn(user: PosUser): void;
  logOut(): void;
}

const AppContext = createContext<AppServices | null>(null);

interface Boot {
  readonly i18n: I18nInstance;
  readonly db: LocalDatabase;
  readonly identity: DeviceIdentity | null;
}

/** Opens the local database, loads translations and the device identity before rendering. */
export function AppProvider({ children, fallback }: { children: ReactNode; fallback?: ReactNode }) {
  const [boot, setBoot] = useState<Boot | null>(null);
  const [runtime, setRuntime] = useState<PosRuntime | null>(null);
  const [user, setUser] = useState<PosUser | null>(null);
  const transport = useMemo(
    () =>
      apiCloudTransport(
        createApiClient({
          baseUrl: API_URL,
          getLocale: () => getLocales()[0]?.languageTag ?? "es",
        }),
      ),
    [],
  );

  useEffect(() => {
    let cancelled = false;
    void Promise.all([
      createI18n(getLocales()[0]?.languageTag),
      openLocalDatabase(),
      loadIdentity(),
    ]).then(([i18n, db, identity]) => {
      if (cancelled) return;
      setBoot({ i18n, db, identity });
      if (identity) setRuntime(createPosRuntime(db, transport, identity));
    });
    return () => {
      cancelled = true;
    };
  }, [transport]);

  // Sync runs while the app is open; each round retries whatever failed offline.
  useEffect(() => {
    runtime?.worker.start();
    return () => runtime?.worker.stop();
  }, [runtime]);

  const link = useCallback(
    async (identity: DeviceIdentity) => {
      if (!boot) return;
      await saveIdentity(identity);
      const linked = createPosRuntime(boot.db, transport, identity);
      await linked.worker.syncNow();
      setRuntime(linked);
    },
    [boot, transport],
  );

  const logIn = useCallback(
    (loggedIn: PosUser) => {
      runtime?.record(
        {
          type: "PosSessionStarted",
          aggregateId: runtime.identity.deviceUuid,
          payload: { userId: loggedIn.id },
        },
        loggedIn,
      );
      setUser(loggedIn);
    },
    [runtime],
  );

  const logOut = useCallback(() => {
    if (runtime && user) {
      runtime.record(
        {
          type: "PosSessionEnded",
          aggregateId: runtime.identity.deviceUuid,
          payload: { userId: user.id },
        },
        user,
      );
    }
    setUser(null);
  }, [runtime, user]);

  if (!boot) return fallback ?? null;
  const services: AppServices = {
    i18n: boot.i18n,
    db: boot.db,
    transport,
    runtime,
    user,
    link,
    logIn,
    logOut,
  };
  return <AppContext.Provider value={services}>{children}</AppContext.Provider>;
}

export function useServices(): AppServices {
  const services = useContext(AppContext);
  if (!services) throw new Error("useServices must be used inside AppProvider");
  return services;
}

/** Translation function of the active locale. */
export function useT(): I18nInstance["t"] {
  return useServices().i18n.t;
}

const IDLE: SyncStatus = { online: null, running: false, lastSyncAt: null, rejected: [] };

/** Live sync status of the linked device. */
export function useSyncStatus(): SyncStatus {
  const { runtime } = useServices();
  return useSyncExternalStore(
    (onChange) => runtime?.worker.subscribe(onChange) ?? (() => undefined),
    () => runtime?.worker.getStatus() ?? IDLE,
  );
}
