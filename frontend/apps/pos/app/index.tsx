import { Redirect } from "expo-router";
import { Button, StyleSheet, Text, View } from "react-native";
import { useServices, useSyncStatus, useT } from "../src/app-context";

/** Home of a linked device with an employee logged in. Placeholder until the sales screens (1.x). */
export default function Home() {
  const t = useT();
  const { runtime, user, logOut } = useServices();
  const status = useSyncStatus();

  if (!runtime) return <Redirect href="/pair" />;
  if (!user) return <Redirect href="/login" />;

  // `status` changes after every round, so the count is re-read when uploads finish.
  const unsynced = runtime.unsyncedCount();

  return (
    <View style={styles.container}>
      <Text style={styles.title}>{t("app.name")}</Text>
      <Text>{t("home.greeting", { name: user.displayName })}</Text>
      <Text>
        {t("home.device", { name: runtime.identity.name, code: runtime.identity.deviceCode })}
      </Text>
      <Text>{status.online === false ? t("sync.offline") : t("sync.online")}</Text>
      <Text>{t("sync.pendingEvents", { count: unsynced })}</Text>
      {status.rejected.length > 0 ? (
        <Text style={styles.warning}>
          {t("sync.rejectedEvents", { count: status.rejected.length })}
        </Text>
      ) : null}
      <Button title={t("sync.syncNow")} onPress={() => void runtime.worker.syncNow()} />
      <Button title={t("auth.logout")} onPress={logOut} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: "center", justifyContent: "center", gap: 8, padding: 24 },
  title: { fontSize: 32, fontWeight: "600" },
  warning: { color: "#c62828" },
});
