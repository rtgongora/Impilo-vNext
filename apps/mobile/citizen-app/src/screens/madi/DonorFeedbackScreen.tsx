import React, { useState } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Screen, Header, Button, TextField, Select, colors } from "@impilo/mobile-design-system";
import { submitDonorFeedback } from "../../services/madiService";

interface Props {
  onBack: () => void;
}

const RATINGS = [1, 2, 3, 4, 5].map((n) => ({ label: `${n} star${n > 1 ? "s" : ""}`, value: String(n) }));

export function DonorFeedbackScreen({ onBack }: Props) {
  const [rating, setRating] = useState("5");
  const [comments, setComments] = useState("");
  const [driveId, setDriveId] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function handleSubmit() {
    setSubmitting(true);
    setMessage(null);
    const ok = await submitDonorFeedback({
      rating: parseInt(rating, 10),
      comments: comments.trim() || undefined,
      drive_id: driveId.trim() || undefined,
    });
    setSubmitting(false);
    setMessage(ok ? "Thank you — your feedback helps improve donation experiences." : "Could not submit feedback. Try again later.");
  }

  return (
    <Screen>
      <Header title="Donor Feedback" onBack={onBack} />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        <Text style={styles.hint}>Share how your donation experience went. Feedback is audited and used for quality improvement.</Text>
        <TextField label="Drive ID (optional)" value={driveId} onChange={setDriveId} placeholder="UUID from your drive registration" />
        <Select label="Rating" value={rating} onChange={setRating} options={RATINGS} testID="madi-feedback-rating" />
        <TextField label="Comments" value={comments} onChange={setComments} placeholder="What went well? What could improve?" multiline />
        {message ? <Text style={message.includes("Thank") ? styles.success : styles.error}>{message}</Text> : null}
        <Button title={submitting ? "Submitting…" : "Submit feedback"} variant="primary" onPress={handleSubmit} disabled={submitting} testID="madi-feedback-submit" />
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
