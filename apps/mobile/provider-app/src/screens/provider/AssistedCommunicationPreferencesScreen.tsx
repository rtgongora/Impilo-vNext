/**
 * Provider-assisted view for Mvumo communication preferences (read bundle; updates use the same API with
 * X-Actor-Ref and a documented client confirmation step in a full workflow). Navigation can register this screen.
 */
import React, { useCallback, useState } from "react";
import { View, Text, TextInput, Pressable, StyleSheet, ScrollView, ActivityIndicator, Alert } from "react-native";
import { Screen, Header } from "@impilo/mobile-design-system";
import { getCommunicationPreferences, putCommunicationPreferences } from "@impilo/mobile-api-client";

export function AssistedCommunicationPreferencesScreen() {
  const [cpid, setCpid] = useState("");
  const [rawJson, setRawJson] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setRawJson(null);
    try {
      const ref = cpid.trim() || "demo-cpid";
      const res = await getCommunicationPreferences(ref);
      setRawJson(JSON.stringify(res, null, 2));
    } catch (e) {
      Alert.alert("Error", e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [cpid]);

  const saveStub = useCallback(() => {
    void (async () => {
      if (!cpid.trim()) {
        Alert.alert("CPID", "Enter a patient CPID to save.");
        return;
      }
      try {
        const body = { channels: { sms: true, email: true, app_push: true }, schemaVersion: 1, actorRelationship: "clinician_assisted" };
        await putCommunicationPreferences(cpid, body, { actorRefHeader: "provider-app-assisted" });
        Alert.alert("Saved", "Preference bundle update queued — see Mvumo history for revision.");
        await load();
      } catch (e) {
        Alert.alert("Error", e instanceof Error ? e.message : String(e));
      }
    })();
  }, [cpid, load]);

  return (
    <Screen>
      <Header title="Comms preferences (assisted)" />
      <ScrollView contentContainerStyle={styles.inner}>
        <Text style={styles.p}>
          Document verbal consent in the local workflow; this screen is a technical stub for loading/saving the Mvumo
          bundle.
        </Text>
        <TextInput placeholder="Patient CPID" value={cpid} onChangeText={setCpid} style={styles.input} autoCapitalize="none" />
        <Pressable style={styles.btn} onPress={load} disabled={loading}>
          {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.btnText}>Load</Text>}
        </Pressable>
        <Pressable style={[styles.btn, styles.btnSecondary]} onPress={saveStub}>
          <Text style={styles.btnTextDark}>Save stub bundle (opt-in all channels)</Text>
        </Pressable>
        {rawJson ? <Text style={styles.mono}>{rawJson}</Text> : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  inner: { padding: 16 },
  p: { fontSize: 12, color: "#444", marginBottom: 12 },
  input: { borderWidth: 1, borderColor: "#ddd", borderRadius: 8, padding: 10, marginBottom: 8 },
  btn: { backgroundColor: "#0f766e", padding: 12, borderRadius: 8, alignItems: "center", marginBottom: 8 },
  btnSecondary: { backgroundColor: "#e0f2f1" },
  btnText: { color: "#fff", fontWeight: "600" },
  btnTextDark: { color: "#0f172a", fontWeight: "600" },
  mono: { fontFamily: "monospace", fontSize: 10, color: "#111" },
});
