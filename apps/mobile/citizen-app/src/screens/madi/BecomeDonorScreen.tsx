import React, { useState } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Screen, Header, Button, TextField, Select, LoadingSpinner } from "@impilo/mobile-design-system";
import { registerDonor } from "../../services/madiService";

interface Props {
  onBack: () => void;
}

const BLOOD_GROUPS = ["A", "B", "AB", "O"].map((g) => ({ label: g, value: g }));
const RH_FACTORS = [
  { label: "Positive (+)", value: "POSITIVE" },
  { label: "Negative (−)", value: "NEGATIVE" },
];

export function BecomeDonorScreen({ onBack }: Props) {
  const [bloodGroup, setBloodGroup] = useState("O");
  const [rhFactor, setRhFactor] = useState("POSITIVE");
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function handleRegister() {
    setSubmitting(true);
    setMessage(null);
    const profile = await registerDonor({ blood_group: bloodGroup, rh_factor: rhFactor });
    setSubmitting(false);
    if (profile) {
      setMessage(`Registered as donor ${profile.donor_id?.slice(0, 8) ?? ""}. You can now find drives and complete screening.`);
    } else {
      setMessage("Registration could not be completed. Check connectivity or try again later.");
    }
  }

  return (
    <Screen>
      <Header title="Become a Donor" onBack={onBack} />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        {submitting ? <LoadingSpinner label="Registering..." /> : null}
        <Text style={styles.hint}>
          Register once with your blood group. Your Health ID anchors your donor record — no duplicate profiles.
        </Text>
        <Select label="Blood group" value={bloodGroup} onChange={setBloodGroup} options={BLOOD_GROUPS} testID="madi-blood-group" />
        <Select label="Rh factor" value={rhFactor} onChange={setRhFactor} options={RH_FACTORS} testID="madi-rh-factor" />
        {message ? <Text style={styles.message}>{message}</Text> : null}
        <Button title="Register as donor" variant="primary" onPress={handleRegister} disabled={submitting} testID="madi-register-submit" />
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 16, gap: 14 },
  hint: { fontSize: 13, color: "#6B7280", lineHeight: 18 },
  message: { fontSize: 13, color: "#059669", lineHeight: 18 },
});
