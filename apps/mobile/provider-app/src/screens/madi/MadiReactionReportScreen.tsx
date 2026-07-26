import React, { useState } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Screen, Header, Button, TextField, Select, colors } from "@impilo/mobile-design-system";
import { reportTransfusionReaction } from "../../services/madiService";

const REACTION_TYPES = [
  { label: "Febrile non-haemolytic", value: "FEVER" },
  { label: "Allergic / anaphylactic", value: "ALLERGIC" },
  { label: "Acute haemolytic", value: "HAEMOLYTIC" },
  { label: "TRALI suspect", value: "TRALI" },
  { label: "TACO suspect", value: "TACO" },
  { label: "Other", value: "OTHER" },
];

const SEVERITIES = [
  { label: "Mild", value: "MILD" },
  { label: "Moderate", value: "MODERATE" },
  { label: "Severe", value: "SEVERE" },
  { label: "Life-threatening", value: "LIFE_THREATENING" },
];

export function MadiReactionReportScreen() {
  const [episodeId, setEpisodeId] = useState("");
  const [reactionType, setReactionType] = useState("FEVER");
  const [severity, setSeverity] = useState("MODERATE");
  const [description, setDescription] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function handleSubmit() {
    if (!episodeId.trim()) return;
    setSubmitting(true);
    setMessage(null);
    const reaction = await reportTransfusionReaction({
      episode_id: episodeId.trim(),
      reaction_type: reactionType,
      severity,
      description: description.trim() || undefined,
    });
    setSubmitting(false);
    if (reaction) {
      setMessage(`Reaction reported (${reaction.reaction_id?.slice(0, 8) ?? "submitted"}). Haemovigilance case workflow initiated.`);
      setEpisodeId("");
      setDescription("");
    } else {
      setMessage("Report could not be submitted. Verify episode ID and try again.");
    }
  }

  return (
    <Screen>
      <Header title="Haemovigilance Report" />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        <Text style={styles.hint}>
          Report adverse transfusion reactions immediately. Reports feed national haemovigilance and are fully audited.
        </Text>
        <TextField label="Transfusion episode ID" value={episodeId} onChange={setEpisodeId} placeholder="Episode UUID" testID="madi-reaction-episode" />
        <Select label="Reaction type" value={reactionType} onChange={setReactionType} options={REACTION_TYPES} />
        <Select label="Severity" value={severity} onChange={setSeverity} options={SEVERITIES} />
        <TextField label="Clinical description" value={description} onChange={setDescription} placeholder="Signs, timing, interventions" multiline />
        {message ? <Text style={message.includes("Reported") || message.includes("submitted") ? styles.success : styles.error}>{message}</Text> : null}
        <Button
          title={submitting ? "Submitting…" : "Submit reaction report"}
          variant="primary"
          onPress={handleSubmit}
          disabled={submitting || !episodeId.trim()}
          testID="madi-reaction-submit"
        />
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 16, gap: 14 },
  hint: { fontSize: 13, color: colors.gray[500], lineHeight: 18 },
  success: { fontSize: 13, color: "#059669" },
  error: { fontSize: 13, color: colors.ui.error.main },
});
