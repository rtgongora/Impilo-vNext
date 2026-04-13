/**
 * APGARScreen — 5-component APGAR scoring at 1, 5, and 10 minutes.
 */
import React, { useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Screen, Header, Button, Badge } from "@impilo/mobile-design-system";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { recordApgar, fetchApgar } from "../../services/inpatientService";
import { useEncounterStore } from "../../stores/encounterStore";

interface APGARComponent {
  id: string;
  label: string;
  scores: { value: number; label: string; description: string }[];
}

const COMPONENTS: APGARComponent[] = [
  { id: "appearance", label: "Appearance (Color)", scores: [
    { value: 0, label: "Blue/pale", description: "Blue or pale all over" },
    { value: 1, label: "Acrocyanosis", description: "Body pink, extremities blue" },
    { value: 2, label: "Pink", description: "Completely pink" },
  ]},
  { id: "pulse", label: "Pulse (Heart Rate)", scores: [
    { value: 0, label: "Absent", description: "No heart rate" },
    { value: 1, label: "<100", description: "Below 100 bpm" },
    { value: 2, label: "≥100", description: "100 bpm or above" },
  ]},
  { id: "grimace", label: "Grimace (Reflex)", scores: [
    { value: 0, label: "None", description: "No response to stimulation" },
    { value: 1, label: "Grimace", description: "Grimace/feeble cry on stimulation" },
    { value: 2, label: "Cry/cough", description: "Vigorous cry, cough, sneeze" },
  ]},
  { id: "activity", label: "Activity (Muscle Tone)", scores: [
    { value: 0, label: "Limp", description: "No movement, floppy" },
    { value: 1, label: "Some flexion", description: "Some flexion of extremities" },
    { value: 2, label: "Active", description: "Active motion, well flexed" },
  ]},
  { id: "respiration", label: "Respiration", scores: [
    { value: 0, label: "Absent", description: "Not breathing" },
    { value: 1, label: "Weak cry", description: "Slow, irregular, weak cry" },
    { value: 2, label: "Strong cry", description: "Good effort, strong cry" },
  ]},
];

const MINUTES = [1, 5, 10];

export function APGARScreen() {
  const { activeEncounter } = useEncounterStore();
  const pid = activeEncounter?.patientId ?? "";
  const qc = useQueryClient();

  const { data: existing = [] } = useQuery({
    queryKey: ["apgar", pid],
    queryFn: () => fetchApgar(pid),
    enabled: !!pid,
  });

  const [selectedMinute, setSelectedMinute] = useState(1);
  const [scores, setScores] = useState<Record<string, number>>({});
  const total = Object.values(scores).reduce((a, b) => a + b, 0);

  const mutation = useMutation({
    mutationFn: () => recordApgar({
      patientId: pid, encounterId: activeEncounter?.id, minute: selectedMinute,
      ...scores,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["apgar"] });
      Alert.alert("APGAR Recorded", `${selectedMinute}-minute score: ${total}/10`);
      setScores({});
    },
  });

  const interpretation = total >= 7 ? { label: "Normal", color: "#22C55E" } : total >= 4 ? { label: "Moderate Depression", color: "#F59E0B" } : { label: "Severe Depression", color: "#DC2626" };

  return (
    <Screen><Header title="APGAR Score" />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        {/* Minute selector */}
        <View style={styles.minuteRow}>
          {MINUTES.map((m) => {
            const done = (existing as Array<Record<string, unknown>>).some((e) => e.minute === m);
            return (
              <TouchableOpacity key={m} onPress={() => setSelectedMinute(m)}
                style={[styles.minuteBtn, selectedMinute === m && styles.minuteActive, done && styles.minuteDone]}>
                <Text style={[styles.minuteText, selectedMinute === m && styles.minuteTextActive]}>{m} min</Text>
                {done && <Text style={styles.minuteCheck}>✓</Text>}
              </TouchableOpacity>
            );
          })}
        </View>

        {/* Total score display */}
        <View style={[styles.totalCard, { borderColor: interpretation.color }]}>
          <Text style={[styles.totalScore, { color: interpretation.color }]}>{total}/10</Text>
          <Text style={styles.totalLabel}>{interpretation.label}</Text>
        </View>

        {/* Component scoring */}
        {COMPONENTS.map((comp) => (
          <View key={comp.id} style={styles.compCard}>
            <Text style={styles.compLabel}>{comp.label}</Text>
            <View style={styles.scoreOptions}>
              {comp.scores.map((opt) => (
                <TouchableOpacity
                  key={`${comp.id}-${opt.value}`}
                  onPress={() => setScores({ ...scores, [comp.id]: opt.value })}
                  style={[
                    styles.scoreOption,
                    scores[comp.id] === opt.value && styles.scoreOptionActive,
                  ]}
                >
                  <View style={styles.scoreHeader}>
                    <Text style={[styles.scoreValue, scores[comp.id] === opt.value && { color: "#FFF" }]}>{opt.value}</Text>
                    <Text style={[styles.scoreLabel, scores[comp.id] === opt.value && { color: "#FFF" }]}>{opt.label}</Text>
                  </View>
                  <Text style={[styles.scoreDesc, scores[comp.id] === opt.value && { color: "#DBEAFE" }]}>{opt.description}</Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>
        ))}

        <Button title={mutation.isPending ? "Recording..." : `Record ${selectedMinute}-Minute APGAR`}
          onPress={() => mutation.mutate()} disabled={Object.keys(scores).length < 5 || mutation.isPending} />

        {/* Previous scores */}
        {(existing as Array<Record<string, unknown>>).length > 0 && (
          <View style={styles.historySection}>
            <Text style={styles.historyTitle}>Recorded Scores</Text>
            {(existing as Array<Record<string, unknown>>).map((e, i) => (
              <View key={i} style={styles.historyRow}>
                <Text style={styles.historyMinute}>{String(e.minute)} min</Text>
                <Text style={styles.historyTotal}>{String(e.total)}/10</Text>
                <Text style={styles.historyDetail}>A:{String(e.appearance)} P:{String(e.pulse)} G:{String(e.grimace)} A:{String(e.activity)} R:{String(e.respiration)}</Text>
              </View>
            ))}
          </View>
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 16, gap: 12, paddingBottom: 32 },
  minuteRow: { flexDirection: "row", gap: 8 },
  minuteBtn: { flex: 1, padding: 12, borderRadius: 12, borderWidth: 2, borderColor: "#E5E7EB", alignItems: "center" },
  minuteActive: { borderColor: "#2563EB", backgroundColor: "#EFF6FF" },
  minuteDone: { borderColor: "#22C55E" },
  minuteText: { fontSize: 14, fontWeight: "600", color: "#6B7280" },
  minuteTextActive: { color: "#2563EB" },
  minuteCheck: { color: "#22C55E", fontSize: 16, fontWeight: "700" },
  totalCard: { alignItems: "center", padding: 16, borderRadius: 16, borderWidth: 3, backgroundColor: "#FFF" },
  totalScore: { fontSize: 48, fontWeight: "900" },
  totalLabel: { fontSize: 14, fontWeight: "600", color: "#374151" },
  compCard: { backgroundColor: "#FFF", borderRadius: 12, padding: 12, gap: 8 },
  compLabel: { fontSize: 14, fontWeight: "700", color: "#111827" },
  scoreOptions: { gap: 6 },
  scoreOption: { borderWidth: 1, borderColor: "#E5E7EB", borderRadius: 8, padding: 10 },
  scoreOptionActive: { backgroundColor: "#2563EB", borderColor: "#2563EB" },
  scoreHeader: { flexDirection: "row", alignItems: "center", gap: 8 },
  scoreValue: { fontSize: 20, fontWeight: "900", color: "#374151", width: 28 },
  scoreLabel: { fontSize: 14, fontWeight: "600", color: "#374151" },
  scoreDesc: { fontSize: 12, color: "#6B7280", marginLeft: 36 },
  historySection: { gap: 6 },
  historyTitle: { fontSize: 14, fontWeight: "700", color: "#111827" },
  historyRow: { flexDirection: "row", alignItems: "center", gap: 8, backgroundColor: "#F9FAFB", borderRadius: 8, padding: 10 },
  historyMinute: { fontSize: 13, fontWeight: "700", color: "#2563EB", width: 50 },
  historyTotal: { fontSize: 16, fontWeight: "900", color: "#111827", width: 50 },
  historyDetail: { fontSize: 11, color: "#6B7280", fontFamily: "monospace" },
});
