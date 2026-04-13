/**
 * TriageScreen — Patient sorting and triage assessment.
 */
import React, { useState } from "react";
import { View, Text, TextInput, StyleSheet, Alert } from "react-native";
import { Screen, Header, Button, LoadingSpinner } from "@impilo/mobile-design-system";
import { useMutation } from "@tanstack/react-query";
import { recordTriage } from "../../services/queueService";
import { useEncounterStore } from "../../stores/encounterStore";

const TRIAGE_LEVELS = [
  { level: "1", label: "Resuscitation", color: "#DC2626", description: "Immediate life-threatening" },
  { level: "2", label: "Emergency", color: "#F97316", description: "Very urgent, potential threat to life" },
  { level: "3", label: "Urgent", color: "#EAB308", description: "Requires timely care" },
  { level: "4", label: "Standard", color: "#22C55E", description: "Can wait safely" },
  { level: "5", label: "Non-urgent", color: "#3B82F6", description: "Minor complaint" },
];

export function TriageScreen() {
  const { activeEncounter } = useEncounterStore();
  const [selectedLevel, setSelectedLevel] = useState("3");
  const [chiefComplaint, setChiefComplaint] = useState("");

  const mutation = useMutation({
    mutationFn: () => recordTriage({
      patientId: activeEncounter?.patientId ?? "", encounterId: activeEncounter?.id ?? "",
      triageLevel: selectedLevel, chiefComplaint, acuityScore: parseInt(selectedLevel),
    }),
    onSuccess: () => Alert.alert("Triage Saved", "Triage assessment recorded"),
  });

  return (
    <Screen><Header title="Patient Triage" />
      <View style={styles.container}>
        <Text style={styles.sectionTitle}>Triage Level</Text>
        {TRIAGE_LEVELS.map((t) => (
          <View key={t.level} style={[styles.levelRow, selectedLevel === t.level && { borderColor: t.color, borderWidth: 2 }]}>
            <View style={[styles.levelDot, { backgroundColor: t.color }]} />
            <View style={{ flex: 1 }}>
              <Text style={styles.levelLabel}>{t.label} (Level {t.level})</Text>
              <Text style={styles.levelDesc}>{t.description}</Text>
            </View>
            <Button label="Select" size="small" onPress={() => setSelectedLevel(t.level)} />
          </View>
        ))}
        <Text style={styles.sectionTitle}>Chief Complaint</Text>
        <TextInput
          style={styles.input}
          placeholder="Presenting complaint..."
          value={chiefComplaint}
          onChangeText={setChiefComplaint}
          multiline
          numberOfLines={3}
        />
        <Button label={mutation.isPending ? "Saving..." : "Record Triage"} onPress={() => mutation.mutate()} disabled={!chiefComplaint || mutation.isPending} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16, gap: 12 },
  sectionTitle: { fontSize: 16, fontWeight: "700", color: "#111827" },
  levelRow: { flexDirection: "row", alignItems: "center", gap: 12, backgroundColor: "#F9FAFB", borderRadius: 12, padding: 12, borderWidth: 1, borderColor: "#E5E7EB" },
  levelDot: { width: 16, height: 16, borderRadius: 8 },
  levelLabel: { fontSize: 14, fontWeight: "600", color: "#111827" },
  levelDesc: { fontSize: 12, color: "#6B7280" },
  input: { borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 8, padding: 12, fontSize: 14, minHeight: 80, textAlignVertical: "top" },
});
