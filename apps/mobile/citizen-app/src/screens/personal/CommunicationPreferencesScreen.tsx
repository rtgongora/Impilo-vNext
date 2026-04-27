/**
 * Citizen view of Mvumo communication / reminder / follow-up preferences (read via BFF).
 * Wire to navigation as needed; CPID can be taken from session when available.
 */

import React, { useCallback, useState } from "react";
import { View, Text, TextInput, Pressable, StyleSheet, ScrollView, ActivityIndicator, Alert } from "react-native";
import { getCommunicationPreferences } from "@impilo/mobile-api-client";

export function CommunicationPreferencesScreen() {
  const [cpid, setCpid] = useState("");
  const [loading, setLoading] = useState(false);
  const [body, setBody] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setBody(null);
    try {
      const ref = cpid.trim() || "demo-cpid";
      const res = await getCommunicationPreferences(ref);
      setBody(JSON.stringify(res, null, 2));
    } catch (e) {
      Alert.alert("Error", e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [cpid]);

  return (
    <ScrollView style={styles.wrap} contentContainerStyle={styles.inner}>
      <Text style={styles.title}>Communication preferences</Text>
      <Text style={styles.hint}>
        Loads the versioned Mvumo bundle for the CPID (or demo). Update flows use PUT/PATCH on the same API as the
        portal and EHR.
      </Text>
      <TextInput
        placeholder="CPID (optional)"
        value={cpid}
        onChangeText={setCpid}
        autoCapitalize="none"
        style={styles.input}
      />
      <Pressable onPress={load} style={styles.btn} disabled={loading}>
        {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.btnText}>Load current bundle</Text>}
      </Pressable>
      {body ? <Text style={styles.mono}>{body}</Text> : null}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  wrap: { flex: 1, backgroundColor: "#fff" },
  inner: { padding: 16, paddingBottom: 40 },
  title: { fontSize: 18, fontWeight: "600", marginBottom: 8 },
  hint: { fontSize: 12, color: "#666", marginBottom: 12 },
  input: {
    borderWidth: 1,
    borderColor: "#ddd",
    borderRadius: 8,
    padding: 10,
    marginBottom: 12,
  },
  btn: {
    backgroundColor: "#059669",
    padding: 12,
    borderRadius: 8,
    alignItems: "center",
    marginBottom: 16,
  },
  btnText: { color: "#fff", fontWeight: "600" },
  mono: { fontFamily: "monospace", fontSize: 11, color: "#111" },
});
