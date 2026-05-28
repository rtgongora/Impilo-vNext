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

export function TriageScreen({ embedded = false }: { embedded?: boolean }) {
  const { activeEncounter } = useEncounterStore();
  const [selectedLevel, setSelectedLevel] = useState("3");
  const [chiefComplaint, setChiefComplaint] = useState("");
  const [heartRate, setHeartRate] = useState("");
  const [respRate, setRespRate] = useState("");
  const [spo2, setSpo2] = useState("");
  const [mentalStatus, setMentalStatus] = useState("ALERT");

  const structuredAcuity = (() => {
    let score = 0;
    const hr = Number(heartRate || 0);
    const rr = Number(respRate || 0);
    const oxygen = Number(spo2 || 0);
    if (hr > 120 || hr < 40) score += 2;
    else if (hr > 100 || hr < 50) score += 1;
    if (rr > 30 || rr < 8) score += 2;
    else if (rr > 22) score += 1;
    if (oxygen > 0 && oxygen < 90) score += 2;
    else if (oxygen > 0 && oxygen < 94) score += 1;
    if (mentalStatus !== "ALERT") score += 2;
    return score;
  })();

  const mutation = useMutation({
    mutationFn: () => recordTriage({
      patientId: activeEncounter?.patientId ?? "", encounterId: activeEncounter?.journeyId || activeEncounter?.id || "",
      triageLevel: selectedLevel,
      chiefComplaint,
      acuityScore: Math.max(parseInt(selectedLevel), structuredAcuity),
      notes: `HR:${heartRate || "n/a"} RR:${respRate || "n/a"} SpO2:${spo2 || "n/a"} Mental:${mentalStatus}`,
    }),
    onSuccess: () => Alert.alert("Triage Saved", "Triage assessment recorded"),
  });

  const content = (
      <View testID="triage-screen" style={styles.container}>
        <Text style={styles.sectionTitle}>Triage Level</Text>
        {TRIAGE_LEVELS.map((t) => (
          <View key={t.level} style={[styles.levelRow, selectedLevel === t.level && { borderColor: t.color, borderWidth: 2 }]}>
            <View style={[styles.levelDot, { backgroundColor: t.color }]} />
            <View style={{ flex: 1 }}>
              <Text style={styles.levelLabel}>{t.label} (Level {t.level})</Text>
              <Text style={styles.levelDesc}>{t.description}</Text>
            </View>
            <Button title="Select" size="sm" onPress={() => setSelectedLevel(t.level)} testID={`triage-level-${t.level}`} />
          </View>
        ))}
        <Text style={styles.sectionTitle}>Chief Complaint</Text>
        <TextInput
          testID="triage-chief-complaint"
          style={styles.input}
          placeholder="Presenting complaint..."
          value={chiefComplaint}
          onChangeText={setChiefComplaint}
          multiline
          numberOfLines={3}
        />
        <Text style={styles.sectionTitle}>Structured triage scoring</Text>
        <View style={styles.metricsGrid}>
          <TextInput
            testID="triage-hr"
            style={styles.metricInput}
            placeholder="Heart rate"
            keyboardType="numeric"
            value={heartRate}
            onChangeText={setHeartRate}
          />
          <TextInput
            testID="triage-rr"
            style={styles.metricInput}
            placeholder="Respiratory rate"
            keyboardType="numeric"
            value={respRate}
            onChangeText={setRespRate}
          />
          <TextInput
            testID="triage-spo2"
            style={styles.metricInput}
            placeholder="SpO2 %"
            keyboardType="numeric"
            value={spo2}
            onChangeText={setSpo2}
          />
          <TextInput
            testID="triage-mental"
            style={styles.metricInput}
            placeholder="Mental status (ALERT/VOICE/PAIN/UNRESPONSIVE)"
            value={mentalStatus}
            onChangeText={setMentalStatus}
          />
        </View>
        <Text style={styles.helperText}>
          {`Computed acuity score: ${structuredAcuity} (final score uses max of selected level and structured score).`}
        </Text>
        <Button
          testID="triage-record"
          title={mutation.isPending ? "Saving..." : "Record Triage"}
          onPress={() => mutation.mutate()}
          disabled={!activeEncounter || !chiefComplaint || mutation.isPending}
        />
      </View>
  );

  if (embedded) {
    return content;
  }

  return (
    <Screen><Header title="Patient Triage" />
      {content}
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
  metricsGrid: { gap: 8 },
  metricInput: { borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 8, padding: 10, fontSize: 13 },
  helperText: { fontSize: 12, color: "#4B5563" },
});
