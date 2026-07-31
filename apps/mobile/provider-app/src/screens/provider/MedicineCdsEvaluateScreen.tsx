/**
 * Governed adult-medicine CDS evaluate — POST /internal/v1/medicine/cds/{topic}/evaluate.
 *
 * Evaluation is an act, not a cacheable read. A failed call must not look like "no alerts".
 */

import React, { useState } from "react";
import { View, Text, TouchableOpacity, ScrollView, StyleSheet } from "react-native";
import { useMutation } from "@tanstack/react-query";
import { ApiError } from "@impilo/mobile-api-client";
import { Badge, Button, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import {
  evaluateMedicineCds,
  MEDICINE_CDS_TOPICS,
  type ProgrammeAlert,
} from "../../services/medicineService";

type Props = {
  patientId?: string | null;
  embedded?: boolean;
};

function AlertCard({ alert }: { alert: ProgrammeAlert }) {
  const variant =
    alert.severity === "HIGH" || alert.severity === "CRITICAL" ? "warning" : "info";
  return (
    <View style={styles.alertCard} testID={`cds-alert-${alert.code}`}>
      <Badge label={alert.severity} variant={variant} />
      <Text style={styles.alertTitle}>{alert.name}</Text>
      <Text style={styles.alertBody}>{alert.message}</Text>
      {alert.required_action ? (
        <Text style={styles.alertAction}>Action: {alert.required_action}</Text>
      ) : null}
    </View>
  );
}

export function MedicineCdsEvaluateScreen({ patientId, embedded }: Props) {
  const [topic, setTopic] = useState<string>(MEDICINE_CDS_TOPICS[0].topic);

  const mutation = useMutation({
    mutationFn: () =>
      evaluateMedicineCds(topic, {}, patientId ?? undefined),
  });

  const guidance = mutation.data;
  const unavailable =
    mutation.isError &&
    mutation.error instanceof ApiError &&
    (mutation.error.code === "medicine_cds_unavailable" || mutation.error.status === 502);

  return (
    <ScrollView style={styles.wrap} testID="medicine-cds-evaluate">
      {!embedded && <Text style={styles.title}>Medicine decision support</Text>}
      <Text style={styles.subtitle}>
        Web spine: /ehr/…/medicine/cds · Eight governed CKP topics (partial mobile mirror)
      </Text>

      {!patientId && (
        <Text style={styles.hint} testID="medicine-cds-needs-patient">
          Open from an active encounter so subject_cpid is sent for medication fact composition.
        </Text>
      )}

      <Text style={styles.label}>Topic</Text>
      <View style={styles.topicRow}>
        {MEDICINE_CDS_TOPICS.map((t) => (
          <TouchableOpacity
            key={t.topic}
            testID={`cds-topic-${t.topic}`}
            onPress={() => setTopic(t.topic)}
            style={[styles.topicChip, topic === t.topic && styles.topicChipActive]}
          >
            <Text style={[styles.topicChipText, topic === t.topic && styles.topicChipTextActive]}>
              {t.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      <Button
        title={mutation.isPending ? "Evaluating…" : "Evaluate guidance"}
        onPress={() => mutation.mutate()}
        disabled={mutation.isPending}
        testID="cds-evaluate-button"
      />

      {mutation.isPending && <LoadingSpinner />}

      {unavailable && (
        <View style={styles.errorBox} testID="cds-unavailable">
          <Text style={styles.errorTitle}>Decision support could not be evaluated</Text>
          <Text style={styles.errorBody}>
            Do not treat the absence of alerts as an all-clear. Retry or use the web CDS surface.
          </Text>
        </View>
      )}

      {mutation.isError && !unavailable && (
        <View style={styles.errorBox} testID="cds-error">
          <Text style={styles.errorTitle}>Evaluation failed</Text>
          <Text style={styles.errorBody}>
            {mutation.error instanceof Error ? mutation.error.message : "Unknown error"}
          </Text>
        </View>
      )}

      {guidance && (
        <View style={styles.results} testID="cds-results">
          {guidance.incomplete && (
            <Text style={styles.incomplete} testID="cds-incomplete">
              Assessment incomplete — {guidance.not_assessed.join(", ") || "facts undetermined"}
            </Text>
          )}
          {guidance.derivation_note ? (
            <Text style={styles.derivation}>{guidance.derivation_note}</Text>
          ) : null}
          {guidance.alerts.length === 0 && guidance.assessed && !guidance.incomplete ? (
            <Text style={styles.empty} testID="cds-no-alerts">
              No alerts fired for this topic with the supplied facts.
            </Text>
          ) : (
            guidance.alerts.map((alert) => <AlertCard key={alert.code} alert={alert} />)
          )}
          {guidance.referral_required && (
            <Badge label="Referral required" variant="warning" />
          )}
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  wrap: { flex: 1, gap: 10 },
  title: { fontSize: 17, fontWeight: "700", color: colors.gray[900] },
  subtitle: { fontSize: 11, color: colors.gray[500], marginBottom: 8 },
  hint: { fontSize: 13, color: "#B45309", marginBottom: 8 },
  label: { fontSize: 13, fontWeight: "600", color: colors.gray[700], marginTop: 4 },
  topicRow: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginVertical: 8 },
  topicChip: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: colors.gray[100],
    borderWidth: 1,
    borderColor: colors.gray[200],
  },
  topicChipActive: { backgroundColor: "#DBEAFE", borderColor: "#2563EB" },
  topicChipText: { fontSize: 11, color: colors.gray[700] },
  topicChipTextActive: { color: "#1D4ED8", fontWeight: "600" },
  errorBox: {
    backgroundColor: "#FEF2F2",
    borderRadius: 10,
    padding: 12,
    marginTop: 10,
    borderWidth: 1,
    borderColor: "#FECACA",
  },
  errorTitle: { fontSize: 13, fontWeight: "700", color: "#B91C1C" },
  errorBody: { fontSize: 12, color: colors.gray[600], marginTop: 4, lineHeight: 18 },
  results: { marginTop: 12, gap: 10 },
  incomplete: { fontSize: 13, color: "#B45309", fontWeight: "600" },
  derivation: { fontSize: 12, color: colors.gray[500], fontStyle: "italic" },
  empty: { fontSize: 13, color: colors.gray[500] },
  alertCard: {
    backgroundColor: "#fff",
    borderRadius: 10,
    padding: 12,
    borderWidth: 1,
    borderColor: colors.gray[200],
    gap: 4,
  },
  alertTitle: { fontSize: 14, fontWeight: "600", color: colors.gray[900] },
  alertBody: { fontSize: 13, color: colors.gray[700], lineHeight: 18 },
  alertAction: { fontSize: 12, color: colors.gray[500], marginTop: 4 },
});
