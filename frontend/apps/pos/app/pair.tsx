import { ApiError } from "@restio/api-client";
import { parsePairingCode } from "@restio/pos-sync";
import { CameraView, useCameraPermissions } from "expo-camera";
import { Redirect, router } from "expo-router";
import { useRef, useState } from "react";
import { ActivityIndicator, Button, StyleSheet, Text, TextInput, View } from "react-native";
import { useServices, useT } from "../src/app-context";

/** Links the device to a restaurant with the one-time code shown by the backoffice. */
export default function Pair() {
  const t = useT();
  const { transport, runtime, link } = useServices();
  const [permission, requestPermission] = useCameraPermissions();
  const [typed, setTyped] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // The camera fires many scans per second; only the first one counts.
  const handling = useRef(false);

  if (runtime) return <Redirect href="/" />;

  async function submit(text: string) {
    if (handling.current) return;
    const code = parsePairingCode(text);
    if (!code) {
      setError(t("device.pair.invalidCode"));
      return;
    }
    handling.current = true;
    setBusy(true);
    setError(null);
    try {
      await link(await transport.pair(code));
      router.replace("/login");
    } catch (e) {
      setError(e instanceof ApiError ? t(`errors.${e.code}`) : t("errors.NETWORK"));
    } finally {
      handling.current = false;
      setBusy(false);
    }
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>{t("device.pair.title")}</Text>
      <Text>{t("device.pair.instructions")}</Text>

      <View style={styles.camera}>
        {permission?.granted ? (
          <CameraView
            style={StyleSheet.absoluteFill}
            barcodeScannerSettings={{ barcodeTypes: ["qr"] }}
            onBarcodeScanned={busy ? undefined : ({ data }) => void submit(data)}
          />
        ) : (
          <Button title={t("device.pair.allowCamera")} onPress={() => void requestPermission()} />
        )}
      </View>

      <Text>{t("device.pair.orType")}</Text>
      <TextInput
        style={styles.input}
        value={typed}
        onChangeText={setTyped}
        autoCapitalize="characters"
        autoCorrect={false}
        placeholder="ABCD-EFGH"
        onSubmitEditing={() => void submit(typed)}
      />
      {busy ? (
        <ActivityIndicator />
      ) : (
        <Button title={t("device.pair.submit")} onPress={() => void submit(typed)} />
      )}
      {error ? <Text style={styles.error}>{error}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 24, gap: 12, justifyContent: "center" },
  title: { fontSize: 24, fontWeight: "600" },
  camera: {
    height: 260,
    borderRadius: 12,
    overflow: "hidden",
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#0001",
  },
  input: { borderWidth: 1, borderColor: "#999", borderRadius: 8, padding: 12, fontSize: 20 },
  error: { color: "#c62828" },
});
