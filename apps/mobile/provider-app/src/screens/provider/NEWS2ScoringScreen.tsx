/**
 * NEWS2ScoringScreen — Component-by-component National Early Warning Score 2.
 * Each of the 7 parameters scored individually (0-3), auto-summed with risk classification.
 */
import React, { useState, useMemo } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Screen, Header, Button, Badge, colors } from "@impilo/mobile-design-system";
import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@impilo/mobile-api-client";
import { useEncounterStore } from "../../stores/encounterStore";

interface NEWS2Param {
  id: string;
  label: string;
  unit: string;
  ranges: { score: number; label: string; color: string }[];
}

const PARAMS: NEWS2Param[] = [
  { id: "respiratoryRate", label: "Respiratory Rate", unit: "breaths/min",
    ranges: [{ score: 0, label: "12-20", color: colors.ui.success.main }, { score: 1, label: "9-11", color: colors.ui.warning.main }, { score: 2, label: "21-24", color: "#F97316" }, { score: 3, label: "≤8 or ≥25", color: colors.ui.error.main }] },
  { id: "spo2", label: "SpO₂ (Scale 1)", unit: "%",
    ranges: [{ score: 0, label: "≥96", color: colors.ui.success.main }, { score: 1, label: "94-95", color: colors.ui.warning.main }, { score: 2, label: "92-93", color: "#F97316" }, { score: 3, label: "≤91", color: colors.ui.error.main }] },
  { id: "airOrOxygen", label: "Air or Oxygen", unit: "",
    ranges: [{ score: 0, label: "Air", color: colors.ui.success.main }, { score: 2, label: "Oxygen", color: "#F97316" }] },
  { id: "systolicBP", label: "Systolic BP", unit: "mmHg",
    ranges: [{ score: 0, label: "111-219", color: colors.ui.success.main }, { score: 1, label: "101-110", color: colors.ui.warning.main }, { score: 2, label: "91-100", color: "#F97316" }, { score: 3, label: "≤90 or ≥220", color: colors.ui.error.main }] },
  { id: "heartRate", label: "Heart Rate", unit: "bpm",
    ranges: [{ score: 0, label: "51-90", color: colors.ui.success.main }, { score: 1, label: "41-50 or 91-110", color: colors.ui.warning.main }, { score: 2, label: "111-130", color: "#F97316" }, { score: 3, label: "≤40 or ≥131", color: colors.ui.error.main }] },
  { id: "consciousness", label: "Consciousness", unit: "ACVPU",
    ranges: [{ score: 0, label: "Alert", color: colors.ui.success.main }, { score: 3, label: "C, V, P, or U", color: colors.ui.error.main }] },
  { id: "temperature", label: "Temperature", unit: "°C",
    ranges: [{ score: 0, label: "36.1-38.0", color: colors.ui.success.main }, { score: 1, label: "35.1-36.0 or 38.1-39.0", color: colors.ui.warning.main }, { score: 2, label: "≥39.1", color: "#F97316" }, { score: 3, label: "≤35.0", color: colors.ui.error.main }] },
];

export function NEWS2ScoringScreen() {
  const { activeEncounter } = useEncounterStore();
  const [scores, setScores] = useState<Record<string, number>>({});

  // Every parameter must be scored before a NEWS2 total means anything. The screen used to send
  // `scores.x ?? 0` for anything the clinician had not touched — and 0 is the NORMAL band, so an
  // unmeasured respiratory rate was submitted as a normal respiratory rate. A partially observed
  // patient scored as a well one.
  const missing = PARAMS.filter((p) => scores[p.id] === undefined);
  const complete = missing.length === 0;

  const total = useMemo(() => Object.values(scores).reduce((a, b) => a + b, 0), [scores]);

  // NEWS2's escalation rule is that ANY single parameter scoring 3 triggers it — not consciousness
  // alone, which is all this checked. A patient with a respiratory rate of 3 and nothing else was
  // shown as LOW risk with no escalation.
  //
  // This banding is a preview so the clinician sees the consequence as they score. The record's
  // risk level and escalation flag come from inpatient-service, which owns the calculation.
  const anyParamAtThree = Object.values(scores).some((v) => v === 3);
  const riskLevel = total >= 7 ? "HIGH" : total >= 5 || anyParamAtThree ? "MEDIUM" : total >= 1 ? "LOW" : "NONE";
  const escalation = total >= 7 || anyParamAtThree;

  const riskColors: Record<string, string> = { NONE: colors.ui.success.main, LOW: "#3B82F6", MEDIUM: colors.ui.warning.main, HIGH: colors.ui.error.main };

  const mutation = useMutation({
    mutationFn: () => {
      if (!complete) {
        // Belt and braces: the button is disabled, but a partial NEWS2 must never reach the record.
        throw new Error(`Score every parameter first — missing: ${missing.map((p) => p.label).join(", ")}`);
      }
      return apiClient.post<{ data: { riskLevel?: string; escalationRequired?: boolean; totalScore?: number } }>(
        "/internal/v1/ews/news2",
        {
          patientId: activeEncounter?.patientId ?? "",
          encounterId: activeEncounter?.id ?? "",
          respiratoryRateScore: scores.respiratoryRate,
          spo2Score: scores.spo2,
          // The chart on this screen is SpO2 Scale 1 (see the parameter label). Scale 2, for
          // patients with hypercapnic respiratory failure, is a different chart and is not offered
          // here — so this is a stated choice, not an unscored parameter defaulted to zero.
          spo2ScaleScore: 0,
          airOrOxygenScore: scores.airOrOxygen,
          systolicBPScore: scores.systolicBP,
          heartRateScore: scores.heartRate,
          consciousnessScore: scores.consciousness,
          temperatureScore: scores.temperature,
        },
      );
    },
    // Report what inpatient-service banded and recorded, not what this screen previewed. If they
    // disagree, the record is what the next clinician will act on.
    onSuccess: (response) => {
      const recorded = response?.data?.data ?? {};
      const recordedRisk = recorded.riskLevel ?? riskLevel;
      const recordedEscalation = recorded.escalationRequired ?? escalation;
      Alert.alert(
        "NEWS2 recorded",
        `Total: ${recorded.totalScore ?? total} — Risk: ${recordedRisk}${recordedEscalation ? "\n⚠️ ESCALATION REQUIRED" : ""}`,
      );
    },
    onError: (error: unknown) => {
      const detail = error instanceof Error ? error.message : "The score was not recorded.";
      Alert.alert("NEWS2 NOT recorded", `${detail}\n\nThis patient has no NEWS2 entry for this observation.`);
    },
  });

  return (
    <Screen><Header title="NEWS2 Scoring" />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        {/* Score summary */}
        <View style={[styles.scoreCard, { borderColor: riskColors[riskLevel] }]}>
          <Text style={[styles.totalScore, { color: riskColors[riskLevel] }]}>{total}</Text>
          <Text style={styles.riskLabel}>{riskLevel} Risk</Text>
          {escalation && <Badge label="ESCALATION REQUIRED" variant="destructive" />}
        </View>

        {/* Parameter scoring */}
        {PARAMS.map((param) => (
          <View key={param.id} style={styles.paramCard}>
            <Text style={styles.paramLabel}>{param.label} {param.unit ? `(${param.unit})` : ""}</Text>
            <View style={styles.scoreRow}>
              {param.ranges.map((range) => (
                <TouchableOpacity
                  key={`${param.id}-${range.score}`}
                  onPress={() => setScores({ ...scores, [param.id]: range.score })}
                  style={[
                    styles.scoreBtn,
                    { borderColor: range.color },
                    scores[param.id] === range.score && { backgroundColor: range.color },
                  ]}
                >
                  <Text style={[styles.scoreBtnScore, scores[param.id] === range.score && { color: "#FFF" }]}>{range.score}</Text>
                  <Text style={[styles.scoreBtnLabel, scores[param.id] === range.score && { color: "#FFF" }]} numberOfLines={1}>{range.label}</Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>
        ))}

        {!complete && (
          <Text testID="news2-incomplete" style={styles.riskLabel}>
            {`Score every parameter before recording — missing: ${missing.map((p) => p.label).join(", ")}`}
          </Text>
        )}
        <Button
          testID="news2-record"
          title={mutation.isPending ? "Recording..." : "Record NEWS2"}
          onPress={() => mutation.mutate()}
          disabled={!complete || mutation.isPending}
        />
        <Text style={styles.hint}>Score at least 5 of 7 parameters to record</Text>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 16, gap: 12, paddingBottom: 32 },
  scoreCard: { alignItems: "center", padding: 20, borderRadius: 16, borderWidth: 3, backgroundColor: "#FFF" },
  totalScore: { fontSize: 56, fontWeight: "900" },
  riskLabel: { fontSize: 16, fontWeight: "700", color: colors.gray[700] },
  paramCard: { backgroundColor: "#FFF", borderRadius: 12, padding: 12, gap: 8 },
  paramLabel: { fontSize: 14, fontWeight: "600", color: colors.gray[900] },
  scoreRow: { flexDirection: "row", gap: 6, flexWrap: "wrap" },
  scoreBtn: { flex: 1, minWidth: 70, borderWidth: 2, borderRadius: 8, padding: 8, alignItems: "center" },
  scoreBtnScore: { fontSize: 18, fontWeight: "700", color: colors.gray[700] },
  scoreBtnLabel: { fontSize: 9, color: colors.gray[500], textAlign: "center" },
  hint: { textAlign: "center", fontSize: 12, color: colors.gray[400] },
});
