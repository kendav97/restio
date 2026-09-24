import type { PosUser } from "@restio/pos-core";
import { Redirect, router } from "expo-router";
import { useMemo, useState } from "react";
import {
  ActivityIndicator,
  Button,
  FlatList,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { useServices, useSyncStatus, useT } from "../src/app-context";

/**
 * Employee login, fully offline: the employee picks their name and types their PIN, which is
 * checked against the hash downloaded with the last snapshot.
 */
export default function Login() {
  const t = useT();
  const { runtime, logIn } = useServices();
  const status = useSyncStatus();
  const [selected, setSelected] = useState<PosUser | null>(null);
  const [pin, setPin] = useState("");
  const [error, setError] = useState(false);
  const [checking, setChecking] = useState(false);
  // Re-read after every sync round: a new snapshot may add or remove employees.
  const users = useMemo(() => runtime?.users.list() ?? [], [runtime, status.lastSyncAt]);

  if (!runtime) return <Redirect href="/pair" />;

  async function submit() {
    if (!selected || !runtime) return;
    setChecking(true);
    const user = await runtime.authenticator.authenticate(selected.id, pin);
    setChecking(false);
    setPin("");
    if (!user) {
      setError(true);
      return;
    }
    logIn(user);
    router.replace("/");
  }

  if (!selected) {
    return (
      <View style={styles.container}>
        <Text style={styles.title}>{t("auth.pin.chooseUser")}</Text>
        {users.length === 0 ? (
          <Text>{t(status.online === false ? "auth.pin.noUsersOffline" : "auth.pin.noUsers")}</Text>
        ) : (
          <FlatList
            data={users}
            keyExtractor={(u) => String(u.id)}
            renderItem={({ item }) => (
              <Pressable style={styles.user} onPress={() => setSelected(item)}>
                <Text style={styles.userName}>{item.displayName}</Text>
              </Pressable>
            )}
          />
        )}
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>{selected.displayName}</Text>
      <Text>{t("auth.pin.title")}</Text>
      <TextInput
        style={styles.input}
        value={pin}
        onChangeText={(text) => {
          setPin(text.replace(/\D/g, ""));
          setError(false);
        }}
        keyboardType="number-pad"
        secureTextEntry
        maxLength={6}
        autoFocus
        onSubmitEditing={() => void submit()}
      />
      {checking ? (
        <ActivityIndicator />
      ) : (
        <Button title={t("auth.pin.submit")} onPress={() => void submit()} />
      )}
      {error ? <Text style={styles.error}>{t("auth.pin.invalid")}</Text> : null}
      <Button
        title={t("auth.pin.back")}
        onPress={() => {
          setSelected(null);
          setPin("");
          setError(false);
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 24, gap: 12, justifyContent: "center" },
  title: { fontSize: 24, fontWeight: "600" },
  user: { padding: 16, borderBottomWidth: StyleSheet.hairlineWidth, borderColor: "#999" },
  userName: { fontSize: 20 },
  input: {
    borderWidth: 1,
    borderColor: "#999",
    borderRadius: 8,
    padding: 12,
    fontSize: 28,
    letterSpacing: 8,
    textAlign: "center",
  },
  error: { color: "#c62828" },
});
