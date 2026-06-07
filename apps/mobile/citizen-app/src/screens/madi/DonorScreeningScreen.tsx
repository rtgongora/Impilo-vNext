import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from "react-native";
import { Screen, Header, Button, LoadingSpinner, ErrorState } from "@impilo/mobile-design-system";
import {
  fetchDonorProfile,
  requestDonorAssist,
  submitDonorPreScreening,
  type DonorAssistResponse,
  type DonorPreScreeningResult,
  type DonorProfile,
} from "../../services/madiService";

const QUESTIONS = [
  { id: "recent_illness", label: "Have you felt unwell in the last 7 days?" },
  { id: "recent_travel_malaria", label: "Travelled to a malaria area recently?" },
  { id: "low_hemoglobin_symptoms", label: "Dizziness, unusual tiredness, or breathlessness?" },
  { id: "recent_tattoo", label: "Tattoo or piercing in the last 3 months?" },
  { id: "on_antibiotics", label: "Taking antibiotics now?" },
  { id: "pregnant_or_recent_birth", label: "Pregnant or gave birth in the last 6 months?" },
] as const;

interface Props {
  onBack: () => void;
}

export function DonorScreeningScreen({ onBack }: Props) {
  const [profile, setProfile] = useState<DonorProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [answers, setAnswers] = useState<Record<string, boolean>>({});
  const [outcome, setOutcome] = useState<DonorPreScreeningResult | null>(null);
  const [assist, setAssist] = useState<DonorAssistResponse | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setProfile(await fetchDonorProfile());
      setAssist(await requestDonorAssist({ op: "prescreening_guide" }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  function setAnswer(id: string, value: boolean) {
    setAnswers((prev) => ({ ...prev, [id]: value }));
  }

  async function handleSubmit() {
    if (!profile?.donor_id) return;
    if (QUESTIONS.some((q) => answers[q.id] === undefined)) return;
    setBusy(true);
    const payload: Record<string, boolean> = {};
    QUESTIONS.forEach((q) => {
      payload[q.id] = !!answers[q.id];
    });
    const result = await submitDonorPreScreening(payload);
    setOutcome(result);
    if (result?.outcome) {
      setAssist(
        await requestDonorAssist({
          op: "interpret_prescreening_outcome",
          context: { prescreening_outcome: result.outcome },
        }),
      );
    }
    setBusy(false);
  }

  if (loading) {
    return (
      <Screen>
        <Header title="Wellness check" onBack={onBack} />
        <LoadingSpinner label="Loading..." />
      </Screen>
    );
  }

  if (error) {
    return (
      <Screen>
        <Header title="Wellness check" onBack={onBack} />
        <ErrorState message={error} onRetry={load} />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="Wellness check" onBack={onBack} />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        {assist ? (
          <View style={styles.assistBox}>
            <Text style={styles.assistTitle}>Nompilo guide</Text>
            {assist.disclaimer ? <Text style={styles.disclaimer}>{assist.disclaimer}</Text> : null}
            {assist.steps?.map((step) => (
              <Text key={step} style={styles.assistLine}>• {step}</Text>
            ))}
            {typeof assist.content === "string" ? <Text style={styles.assistLine}>{assist.content}</Text> : null}
          </View>
        ) : null}

        {!profile ? (
          <Text style={styles.hint}>Register as a donor first to complete your wellness check.</Text>
        ) : (
          <>
            {QUESTIONS.map((q) => (
              <View key={q.id} style={styles.question}>
                <Text style={styles.questionLabel}>{q.label}</Text>
                <View style={styles.row}>
                  <TouchableOpacity style={styles.choice} onPress={() => setAnswer(q.id, true)}>
                    <Text style={answers[q.id] === true ? styles.choiceOn : styles.choiceOff}>Yes</Text>
                  </TouchableOpacity>
                  <TouchableOpacity style={styles.choice} onPress={() => setAnswer(q.id, false)}>
                    <Text style={answers[q.id] === false ? styles.choiceOn : styles.choiceOff}>No</Text>
                  </TouchableOpacity>
                </View>
              </View>
            ))}
            <Button title="Submit wellness check" variant="primary" onPress={handleSubmit} disabled={busy} />
          </>
        )}

        {outcome ? (
          <View style={styles.outcomeBox}>
            <Text style={styles.outcomeTitle}>
              {outcome.outcome === "PROCEED_TO_DRIVE"
                ? "You may be ready to visit a drive"
                : outcome.outcome === "DEFER_SAFELY"
                  ? "It may be better to wait"
                  : "Speak with blood bank staff"}
            </Text>
            {outcome.safe_message ? <Text style={styles.outcomeMsg}>{outcome.safe_message}</Text> : null}
          </View>
        ) : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 16, gap: 12 },
  assistBox: { backgroundColor: "#F5F3FF", borderRadius: 12, padding: 12, borderWidth: 1, borderColor: "#DDD6FE" },
  assistTitle: { fontSize: 13, fontWeight: "700", color: "#5B21B6", marginBottom: 6 },
  disclaimer: { fontSize: 11, color: "#6D28D9", marginBottom: 4 },
  assistLine: { fontSize: 12, color: "#374151", marginBottom: 2 },
  hint: { fontSize: 13, color: "#6B7280" },
  question: { backgroundColor: "#FFF", borderRadius: 10, padding: 12, borderWidth: 1, borderColor: "#E5E7EB" },
  questionLabel: { fontSize: 13, fontWeight: "600", marginBottom: 8 },
  row: { flexDirection: "row", gap: 12 },
  choice: { paddingVertical: 4, paddingHorizontal: 8 },
  choiceOn: { color: "#DC2626", fontWeight: "700" },
  choiceOff: { color: "#6B7280" },
  outcomeBox: { backgroundColor: "#ECFDF5", borderRadius: 10, padding: 12, borderWidth: 1, borderColor: "#A7F3D0" },
  outcomeTitle: { fontSize: 14, fontWeight: "600", color: "#065F46" },
  outcomeMsg: { fontSize: 12, color: "#047857", marginTop: 4 },
});
