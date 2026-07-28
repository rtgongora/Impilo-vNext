/**
 * TriageScreen — Patient sorting and triage assessment.
 */
import React, { useState } from "react";
import { View, Text, TextInput, StyleSheet, Alert } from "react-native";
import { Screen, Header, Button, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import { useMutation } from "@tanstack/react-query";
import { recordTriage } from "../../services/queueService";
import { useEncounterStore } from "../../stores/encounterStore";

const TRIAGE_LEVELS = [
  { level: "1", label: "Resuscitation", color: colors.ui.error.main, description: "Immediate life-threatening" },
  { level: "2", label: "Emergency", color: "#F97316", description: "Very urgent, potential threat to life" },
  { level: "3", label: "Urgent", color: "#EAB308", description: "Requires timely care" },
  { level: "4", label: "Standard", color: colors.ui.success.main, description: "Can wait safely" },
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

  // There used to be a client-side acuity score here. It combined an ad-hoc points scale (invented
  // here, matching no published triage instrument) with the clinician's selected level via
  // Math.max — and the two scales ran in OPPOSITE directions. PCT's acuity is 1=resuscitation …
  // 5=non-urgent, while the points scale counted upward with severity. So abnormal vitals RAISED
  // the number and therefore DEMOTED the patient, and a sufficiently sick patient scored above 5,
  // failed the server's @Max(5), and was rejected with a 400 that this screen never displayed —
  // leaving no triage record at all for the sickest arrivals.
  //
  // Acuity is a clinical judgement. It is the clinician's selected level, sent as-is. The vitals go
  // to the server as structured data in the field that exists for them, so the server can reason
  // about them; they are not evidence this screen is entitled to score.
  const acuity = parseInt(selectedLevel, 10);

  const mutation = useMutation({
    mutationFn: () => recordTriage({
      patientId: activeEncounter?.patientId ?? "",
      encounterId: activeEncounter?.journeyId || activeEncounter?.id || "",
      triageLevel: selectedLevel,
      chiefComplaint,
      acuityScore: acuity,
      vitals: {
        heart_rate: heartRate ? Number(heartRate) : null,
        respiratory_rate: respRate ? Number(respRate) : null,
        spo2: spo2 ? Number(spo2) : null,
        mental_status: mentalStatus,
      },
    }),
    onSuccess: (result) => {
      // Report the acuity the server recorded, not the one this screen sent. If they ever diverge,
      // the clinician must see the one that is now in the record.
      const recorded = result?.acuity ?? acuity;
      Alert.alert("Triage saved", `Triage recorded at acuity ${recorded}.`);
    },
    onError: (error: unknown) => {
      // A silent failure here is the dangerous case: the clinician walks away believing the patient
      // is in the queue at the acuity they set, and no triage record exists.
      const detail = error instanceof Error ? error.message : "The triage assessment was not saved.";
      Alert.alert(
        "Triage NOT saved",
        `${detail}\n\nThis patient has no triage record. Record the assessment again or escalate.`,
      );
    },
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
          {`Recording acuity ${acuity} — ${TRIAGE_LEVELS.find((t) => t.level === selectedLevel)?.label ?? ""}. `}
          {"Vitals are sent with the assessment; they do not change the acuity you selected."}
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
  sectionTitle: { fontSize: 16, fontWeight: "700", color: colors.gray[900] },
  levelRow: { flexDirection: "row", alignItems: "center", gap: 12, backgroundColor: colors.gray[50], borderRadius: 12, padding: 12, borderWidth: 1, borderColor: colors.gray[200] },
  levelDot: { width: 16, height: 16, borderRadius: 8 },
  levelLabel: { fontSize: 14, fontWeight: "600", color: colors.gray[900] },
  levelDesc: { fontSize: 12, color: colors.gray[500] },
  input: { borderWidth: 1, borderColor: colors.gray[300], borderRadius: 8, padding: 12, fontSize: 14, minHeight: 80, textAlignVertical: "top" },
  metricsGrid: { gap: 8 },
  metricInput: { borderWidth: 1, borderColor: colors.gray[300], borderRadius: 8, padding: 10, fontSize: 13 },
  helperText: { fontSize: 12, color: colors.gray[600] },
});
