/**
 * AssuranceChoiceScreen — mirrors web /auth/register/assurance tier selection.
 */

import React, { useState } from "react";
import { View, Text, StyleSheet, Pressable, ScrollView } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Button, LoadingSpinner } from "@impilo/mobile-design-system";
import {
  requestAssuranceUpgrade,
  type AssuranceTier,
} from "../../services/citizenRegistrationService";
import { appStore } from "../../stores/appStore";

const GREEN = "#059669";

type IoniconName = React.ComponentProps<typeof Ionicons>["name"];

const TIERS: Array<{
  id: AssuranceTier;
  title: string;
  description: string;
  icon: IoniconName;
}> = [
  {
    id: "BASIC",
    title: "Basic Access",
    description: "Wellness, communities, marketplace — no Health ID required.",
    icon: "shield-outline",
  },
  {
    id: "TEMPORARY",
    title: "Temporary Health Access",
    description: "Provisional Health ID with supervised remote proofing.",
    icon: "time-outline",
  },
  {
    id: "FULL",
    title: "Full Activation",
    description: "In-person verification for full clinical services.",
    icon: "checkmark-circle-outline",
  },
];

export function AssuranceChoiceScreen() {
  const [selected, setSelected] = useState<AssuranceTier | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleContinue() {
    if (!selected || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      await requestAssuranceUpgrade(selected);
      appStore.getState().setOnboardingScreen(null);
    } catch {
      setError("Could not record your assurance choice. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <ScrollView testID="assurance-screen" contentContainerStyle={styles.container}>
      <Text style={styles.title}>Choose your access level</Text>
      <Text style={styles.subtitle}>
        You can upgrade later. This choice is recorded for audit and consent alignment.
      </Text>

      {TIERS.map((tier) => {
        const active = selected === tier.id;
        return (
          <Pressable
            key={tier.id}
            testID={`assurance-tier-${tier.id}`}
            onPress={() => setSelected(tier.id)}
            style={[styles.tierCard, active && styles.tierCardActive]}
          >
            <Ionicons name={tier.icon} size={22} color={active ? GREEN : "#6B7280"} />
            <View style={styles.tierBody}>
              <Text style={[styles.tierTitle, active && styles.tierTitleActive]}>{tier.title}</Text>
              <Text style={styles.tierDesc}>{tier.description}</Text>
            </View>
            {active ? <Ionicons name="checkmark-circle" size={22} color={GREEN} /> : null}
          </Pressable>
        );
      })}

      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      {submitting ? (
        <View style={styles.loadingRow}>
          <LoadingSpinner size="md" />
          <Text style={styles.loadingText}>Saving choice…</Text>
        </View>
      ) : (
        <Button
          title="Continue"
          onPress={handleContinue}
          variant="primary"
          size="lg"
          fullWidth
          disabled={!selected}
          testID="assurance-continue"
        />
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 24, paddingBottom: 48, backgroundColor: "#FFFFFF", flexGrow: 1 },
  title: { fontSize: 24, fontWeight: "800", color: "#111827" },
  subtitle: { marginTop: 8, fontSize: 14, color: "#6B7280", lineHeight: 20, marginBottom: 20 },
  tierCard: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 12,
    borderWidth: 1,
    borderColor: "#E5E7EB",
    borderRadius: 12,
    padding: 14,
    marginBottom: 10,
    backgroundColor: "#FAFAFA",
  },
  tierCardActive: { borderColor: GREEN, backgroundColor: "#ECFDF5" },
  tierBody: { flex: 1 },
  tierTitle: { fontSize: 16, fontWeight: "700", color: "#111827" },
  tierTitleActive: { color: GREEN },
  tierDesc: { marginTop: 4, fontSize: 13, color: "#6B7280", lineHeight: 18 },
  errorText: { color: "#DC2626", fontSize: 13, marginBottom: 12 },
  loadingRow: { flexDirection: "row", alignItems: "center", gap: 12, paddingVertical: 16, justifyContent: "center" },
  loadingText: { fontSize: 15, color: "#6B7280" },
});
