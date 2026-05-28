/**
 * IdRecoverySection — Recover lost Health ID.
 * Maps to web: /citizen/id-recovery
 * Priority: MEDIUM
 */

import React from "react";
import { View, Text, TextInput, StyleSheet, ScrollView } from "react-native";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Screen, Header, Card, CardBody, Button } from "@impilo/mobile-design-system";
import {
  resolvePatientIdentity,
  searchIdentities,
  startPatientRecovery,
  verifyPatientRecovery,
  type IdentityRegistryRecord,
} from "../../services/identityRegistryService";

export function IdRecoverySection() {
  const [search, setSearch] = React.useState("");
  const [submittedSearch, setSubmittedSearch] = React.useState("");
  const [healthId, setHealthId] = React.useState("");
  const [recoveryPayload, setRecoveryPayload] = React.useState(
    JSON.stringify({ method: "phone_otp", contact: "+263000000000", fullName: "Example Client" }, null, 2),
  );
  const [verifyPayload, setVerifyPayload] = React.useState(JSON.stringify({ recoveryId: "RECOVERY-ID", otp: "000000" }, null, 2));
  const [feedback, setFeedback] = React.useState<string | null>(null);

  const searchQ = useQuery({
    queryKey: ["citizen-identity-search", submittedSearch],
    queryFn: () => searchIdentities(submittedSearch),
    enabled: submittedSearch.trim().length >= 2,
  });
  const resolveM = useMutation({ mutationFn: resolvePatientIdentity, onError: () => setFeedback("Health ID resolution failed.") });
  const startM = useMutation({ mutationFn: startPatientRecovery, onError: () => setFeedback("Recovery start failed.") });
  const verifyM = useMutation({ mutationFn: verifyPatientRecovery, onError: () => setFeedback("Recovery verification failed.") });

  function parsePayload(text: string): IdentityRegistryRecord | null {
    try {
      const parsed = JSON.parse(text);
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        throw new Error("Payload must be an object.");
      }
      setFeedback(null);
      return parsed as IdentityRegistryRecord;
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : "Invalid JSON payload.");
      return null;
    }
  }

  const response = resolveM.data ?? startM.data ?? verifyM.data;

  return (
    <Screen>
      <Header title="Recover Health ID" />
      <ScrollView style={styles.content} contentContainerStyle={styles.contentContainer}>
        <Card>
          <CardBody>
            <View testID="citizen-id-recovery-operations" style={styles.panel}>
              <Text style={styles.title}>Live Identity Recovery</Text>
              <Text style={styles.hint}>
                Search, resolve, start recovery, and verify recovery through the Registry plane identity BFF.
              </Text>
              <TextInput
                style={styles.input}
                placeholder="Search name, PHID, phone"
                value={search}
                onChangeText={setSearch}
              />
              <Button title="Search identity" onPress={() => setSubmittedSearch(search)} disabled={search.trim().length < 2} />
              {searchQ.data?.slice(0, 3).map((item, index) => (
                <View key={`${String(item.id ?? item.healthId ?? index)}`} style={styles.resultRow}>
                  <Text style={styles.resultTitle}>{String(item.healthId ?? item.id ?? item.cpid ?? `identity-${index + 1}`)}</Text>
                  <Text style={styles.resultMeta}>{String(item.displayName ?? item.name ?? item.fullName ?? "Registry identity")}</Text>
                </View>
              ))}
              <TextInput
                style={styles.input}
                placeholder="Health ID / PHID to resolve"
                value={healthId}
                onChangeText={setHealthId}
              />
              <Button
                title={resolveM.isPending ? "Resolving..." : "Resolve Health ID"}
                onPress={() => resolveM.mutate(healthId)}
                disabled={!healthId.trim() || resolveM.isPending}
              />
              <Text style={styles.label}>Start recovery payload</Text>
              <TextInput style={styles.textArea} value={recoveryPayload} onChangeText={setRecoveryPayload} multiline />
              <Button
                title={startM.isPending ? "Starting..." : "Start recovery"}
                onPress={() => {
                  const payload = parsePayload(recoveryPayload);
                  if (payload) startM.mutate(payload);
                }}
                disabled={startM.isPending}
              />
              <Text style={styles.label}>Verify recovery payload</Text>
              <TextInput style={styles.textArea} value={verifyPayload} onChangeText={setVerifyPayload} multiline />
              <Button
                title={verifyM.isPending ? "Verifying..." : "Verify recovery"}
                onPress={() => {
                  const payload = parsePayload(verifyPayload);
                  if (payload) verifyM.mutate(payload);
                }}
                disabled={verifyM.isPending}
              />
              {feedback ? <Text style={styles.error}>{feedback}</Text> : null}
              {response ? <Text style={styles.preview}>{JSON.stringify(response, null, 2).slice(0, 500)}</Text> : null}
            </View>
          </CardBody>
        </Card>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { flex: 1 },
  contentContainer: { padding: 16, gap: 16 },
  panel: { gap: 10 },
  title: { fontSize: 18, fontWeight: "800", color: "#111827" },
  hint: { fontSize: 13, color: "#6B7280", lineHeight: 18 },
  input: { borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 8, padding: 10, fontSize: 14 },
  textArea: {
    minHeight: 90,
    borderWidth: 1,
    borderColor: "#D1D5DB",
    borderRadius: 8,
    padding: 10,
    fontSize: 12,
    fontFamily: "monospace",
    textAlignVertical: "top",
  },
  label: { fontSize: 12, fontWeight: "700", color: "#374151" },
  resultRow: { borderWidth: 1, borderColor: "#E5E7EB", borderRadius: 8, padding: 8, backgroundColor: "#F9FAFB" },
  resultTitle: { fontSize: 12, fontWeight: "700", color: "#111827" },
  resultMeta: { fontSize: 11, color: "#6B7280", marginTop: 2 },
  error: { fontSize: 12, color: "#B91C1C" },
  preview: { fontSize: 11, color: "#374151", backgroundColor: "#F3F4F6", borderRadius: 8, padding: 8 },
});