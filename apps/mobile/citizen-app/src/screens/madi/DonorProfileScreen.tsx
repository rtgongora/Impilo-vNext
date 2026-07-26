import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Screen, Header, Button, Select, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import {
  fetchDonorProfile,
  fetchNextEligibility,
  updateDonorPreferences,
  type DonorProfile,
  type DonorEligibility,
} from "../../services/madiService";

interface Props {
  onBack: () => void;
}

const CHANNELS = [
  { label: "SMS", value: "SMS" },
  { label: "Push notification", value: "PUSH" },
  { label: "Email", value: "EMAIL" },
  { label: "In-app only", value: "IN_APP" },
];

export function DonorProfileScreen({ onBack }: Props) {
  const [profile, setProfile] = useState<DonorProfile | null>(null);
  const [eligibility, setEligibility] = useState<DonorEligibility | null>(null);
  const [channel, setChannel] = useState("PUSH");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [p, e] = await Promise.all([fetchDonorProfile(), fetchNextEligibility()]);
      setProfile(p);
      setEligibility(e);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function handleSavePrefs() {
    setSaving(true);
    await updateDonorPreferences({ channel, enabled: true });
    setSaving(false);
  }

  if (loading) return <Screen><Header title="Donor Profile" onBack={onBack} /><LoadingSpinner label="Loading profile..." /></Screen>;
  if (error) return <Screen><Header title="Donor Profile" onBack={onBack} /><ErrorState message={error.message} onRetry={load} /></Screen>;

  if (!profile) {
    return (
      <Screen>
        <Header title="Donor Profile" onBack={onBack} />
        <EmptyState icon="heart-outline" title="Not registered" description="Register as a donor first to view your profile." />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="Donor Profile" onBack={onBack} />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        <View style={styles.card}>
          <Text style={styles.label}>Blood group</Text>
          <Text style={styles.value}>{profile.blood_group}{profile.rh_factor ? ` (${profile.rh_factor})` : ""}</Text>
          <Text style={styles.label}>Status</Text>
          <Text style={styles.value}>{profile.status ?? "ACTIVE"}</Text>
          <Text style={styles.label}>Donor ID</Text>
          <Text style={styles.valueMono}>{profile.donor_id}</Text>
        </View>
        {eligibility ? (
          <View style={styles.card}>
            <Text style={styles.sectionTitle}>Next eligibility</Text>
            <Text style={styles.value}>
              {eligibility.eligible ? "Eligible to donate now" : `Next eligible: ${eligibility.next_eligible_date ?? "—"}`}
            </Text>
            {eligibility.reason ? <Text style={styles.hint}>{eligibility.reason}</Text> : null}
          </View>
        ) : null}
        <View style={styles.card}>
          <Text style={styles.sectionTitle}>Communication preferences</Text>
          <Select label="Preferred channel" value={channel} onChange={setChannel} options={CHANNELS} />
          <Button title={saving ? "Saving…" : "Save preferences"} variant="outline" onPress={handleSavePrefs} disabled={saving} />
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 16, gap: 12 },
  card: { backgroundColor: "#FFFFFF", borderRadius: 12, padding: 14, borderWidth: 1, borderColor: colors.gray[200], gap: 6 },
  label: { fontSize: 12, color: colors.gray[500], marginTop: 4 },
  value: { fontSize: 15, fontWeight: "600", color: colors.gray[900] },
  valueMono: { fontSize: 12, fontFamily: "monospace", color: colors.gray[700] },
  sectionTitle: { fontSize: 14, fontWeight: "600", marginBottom: 4 },
  hint: { fontSize: 12, color: "#B45309" },
});
