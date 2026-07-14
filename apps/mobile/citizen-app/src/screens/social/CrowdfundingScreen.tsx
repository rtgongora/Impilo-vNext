/**
 * CrowdfundingScreen — facility-verified medical fundraisers (Daidzai case + mushe money
 * via the BFF). The Verified badge appears ONLY for provider-attested cases
 * (verificationStatus ACTIVE/FULFILLED) and opens the independent-verification link.
 * Donation success is only ever a 2xx from the BFF — rejections (409 not-open,
 * insufficient funds) are rendered honestly, never swallowed.
 */
import React, { useState } from "react";
import { View, Text, TouchableOpacity, TextInput, StyleSheet, Alert, Switch, Linking } from "react-native";
import { Badge, Button, LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchCampaigns, donate } from "../../services/crowdfundingService";
import type { CrowdfundingCampaign } from "../../types";

function isVerified(c: CrowdfundingCampaign): boolean {
  return c.verificationStatus === "ACTIVE" || c.verificationStatus === "FULFILLED";
}

function errorMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const anyErr = e as { message?: string; error?: { message?: string } };
    if (anyErr.error?.message) return anyErr.error.message;
    if (anyErr.message) return anyErr.message;
  }
  return "Your donation could not be captured — nothing was charged.";
}

export function CrowdfundingScreen() {
  const queryClient = useQueryClient();
  const [donating, setDonating] = useState<string | null>(null);
  const [amount, setAmount] = useState("");
  const [message, setMessage] = useState("");
  const [anonymous, setAnonymous] = useState(false);
  const [donateError, setDonateError] = useState<string | null>(null);

  const { data: campaigns = [], isLoading, isError } = useQuery({
    queryKey: ["fundraisers"],
    queryFn: () => fetchCampaigns(),
  });

  const donateMutation = useMutation({
    mutationFn: (fundraiserId: string) =>
      donate(fundraiserId, {
        amount: Number(amount),
        message: message.trim() || undefined,
        isAnonymous: anonymous,
      }),
    onSuccess: () => {
      // Only reached on a 2xx from the BFF — real recorded contribution.
      queryClient.invalidateQueries({ queryKey: ["fundraisers"] });
      setDonating(null);
      setAmount("");
      setMessage("");
      setAnonymous(false);
      setDonateError(null);
      Alert.alert("Thank you!", "Your donation was recorded.");
    },
    onError: (e: unknown) => {
      setDonateError(errorMessage(e));
    },
  });

  const openAttestation = (c: CrowdfundingCampaign) => {
    if (c.attestationPublicToken) {
      Linking.openURL(c.attestationPublicToken).catch(() => {
        Alert.alert("Could not open", "The verification link could not be opened on this device.");
      });
    }
  };

  if (isLoading) return <LoadingSpinner />;

  if (isError) {
    return (
      <View style={styles.container}>
        <Text style={styles.title}>Fundraisers</Text>
        <Text style={styles.errorText}>We couldn't load fundraisers right now. Pull to retry.</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Fundraisers</Text>
      {campaigns.length === 0 && (
        <Text style={styles.emptyText}>No verified fundraisers are open right now.</Text>
      )}
      {campaigns.map((c) => {
        const pct = c.targetAmount > 0 ? Math.min((c.raisedAmount / c.targetAmount) * 100, 100) : 0;
        return (
          <View key={c.id} style={styles.card}>
            <View style={styles.cardHeader}>
              <Text style={styles.campaignTitle}>{c.title}</Text>
              {isVerified(c) && (
                <TouchableOpacity
                  onPress={() => openAttestation(c)}
                  disabled={!c.attestationPublicToken}
                  accessibilityLabel="Verified — tap to check independently"
                >
                  <Badge label="Verified ✓" variant="success" />
                </TouchableOpacity>
              )}
            </View>
            {!!c.story && <Text style={styles.story} numberOfLines={3}>{c.story}</Text>}
            <View style={styles.progressSection}>
              <View style={styles.progressBar}><View style={[styles.progressFill, { width: `${pct}%` }]} /></View>
              <View style={styles.progressMeta}>
                <Text style={styles.raised}>{c.currency} {c.raisedAmount.toLocaleString()}</Text>
                <Text style={styles.goal}>of {c.currency} {c.targetAmount.toLocaleString()}</Text>
              </View>
              <Text style={styles.donors}>{c.donorCount} donors · {Math.round(pct)}% funded</Text>
            </View>
            {c.endsAt && <Text style={styles.deadline}>Ends: {new Date(c.endsAt).toLocaleDateString()}</Text>}
            {donating === c.id ? (
              <View style={styles.donateForm}>
                {donateError && <Text style={styles.errorText}>{donateError}</Text>}
                <TextInput
                  style={styles.input}
                  placeholder={`Amount (${c.currency})`}
                  value={amount}
                  onChangeText={setAmount}
                  keyboardType="numeric"
                />
                <TextInput
                  style={styles.input}
                  placeholder="Message (optional)"
                  value={message}
                  onChangeText={setMessage}
                />
                <View style={styles.anonymousRow}>
                  <Text style={styles.anonymousLabel}>Donate anonymously</Text>
                  <Switch value={anonymous} onValueChange={setAnonymous} />
                </View>
                <View style={styles.donateActions}>
                  <TouchableOpacity onPress={() => { setDonating(null); setDonateError(null); }}>
                    <Text style={styles.cancelText}>Cancel</Text>
                  </TouchableOpacity>
                  <Button
                    title={donateMutation.isPending ? "Processing..." : "Donate"}
                    onPress={() => donateMutation.mutate(c.id)}
                    disabled={!amount || Number(amount) <= 0 || donateMutation.isPending}
                    size="sm"
                  />
                </View>
              </View>
            ) : (
              <TouchableOpacity
                style={styles.donateButton}
                onPress={() => { setDonating(c.id); setDonateError(null); }}
              >
                <Text style={styles.donateButtonText}>❤️ Donate</Text>
              </TouchableOpacity>
            )}
          </View>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 12 },
  title: { fontSize: 18, fontWeight: "700", color: "#111827" },
  emptyText: { fontSize: 13, color: "#6B7280" },
  errorText: { fontSize: 13, color: "#DC2626" },
  card: { backgroundColor: "#F9FAFB", borderRadius: 12, padding: 16, gap: 8 },
  cardHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  campaignTitle: { fontSize: 16, fontWeight: "700", color: "#111827", flex: 1 },
  story: { fontSize: 13, color: "#4B5563", lineHeight: 18 },
  progressSection: { gap: 4 },
  progressBar: { height: 8, backgroundColor: "#E5E7EB", borderRadius: 4 },
  progressFill: { height: 8, backgroundColor: "#22C55E", borderRadius: 4 },
  progressMeta: { flexDirection: "row", gap: 4, alignItems: "baseline" },
  raised: { fontSize: 16, fontWeight: "700", color: "#22C55E" },
  goal: { fontSize: 13, color: "#6B7280" },
  donors: { fontSize: 12, color: "#9CA3AF" },
  deadline: { fontSize: 12, color: "#F59E0B" },
  donateForm: { gap: 8 },
  input: { borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 8, padding: 10, fontSize: 14 },
  anonymousRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  anonymousLabel: { fontSize: 13, color: "#4B5563" },
  donateActions: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  cancelText: { color: "#6B7280", fontSize: 14 },
  donateButton: { backgroundColor: "#DC2626", borderRadius: 8, paddingVertical: 12, alignItems: "center" },
  donateButtonText: { color: "#FFF", fontSize: 14, fontWeight: "600" },
});
