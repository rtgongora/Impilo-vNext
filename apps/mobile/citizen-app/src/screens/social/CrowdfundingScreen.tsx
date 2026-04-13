/**
 * CrowdfundingScreen — Medical fundraising campaigns.
 */
import React, { useState } from "react";
import { View, Text, TouchableOpacity, TextInput, StyleSheet, Alert } from "react-native";
import { Badge, Button, LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchCampaigns, donate } from "../../services/crowdfundingService";
import { useAppStore } from "../../stores/appStore";

export function CrowdfundingScreen() {
  const profile = useAppStore((s) => s.profile);
  const queryClient = useQueryClient();
  const [donating, setDonating] = useState<string | null>(null);
  const [amount, setAmount] = useState("");

  const { data: campaigns = [], isLoading } = useQuery({ queryKey: ["crowdfunding"], queryFn: () => fetchCampaigns() });

  const donateMutation = useMutation({
    mutationFn: (campaignId: string) => donate(campaignId, { donorId: profile?.cpid ?? "", amount: Number(amount) }),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["crowdfunding"] }); setDonating(null); setAmount(""); Alert.alert("Thank you!", "Your donation has been processed."); },
  });

  if (isLoading) return <LoadingSpinner />;

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Fundraising Campaigns</Text>
      {campaigns.map((c) => {
        const pct = c.goalAmount > 0 ? Math.min((c.raisedAmount / c.goalAmount) * 100, 100) : 0;
        return (
          <View key={c.id} style={styles.card}>
            <View style={styles.cardHeader}>
              <Text style={styles.campaignTitle}>{c.title}</Text>
              {c.verified && <Badge label="Verified" variant="success" />}
            </View>
            <Text style={styles.story} numberOfLines={3}>{c.story}</Text>
            <View style={styles.progressSection}>
              <View style={styles.progressBar}><View style={[styles.progressFill, { width: `${pct}%` }]} /></View>
              <View style={styles.progressMeta}>
                <Text style={styles.raised}>{c.currency} {c.raisedAmount.toLocaleString()}</Text>
                <Text style={styles.goal}>of {c.currency} {c.goalAmount.toLocaleString()}</Text>
              </View>
              <Text style={styles.donors}>{c.donorCount} donors · {Math.round(pct)}% funded</Text>
            </View>
            {c.endsAt && <Text style={styles.deadline}>Ends: {new Date(c.endsAt).toLocaleDateString()}</Text>}
            {donating === c.id ? (
              <View style={styles.donateForm}>
                <TextInput style={styles.input} placeholder={`Amount (${c.currency})`} value={amount} onChangeText={setAmount} keyboardType="numeric" />
                <View style={styles.donateActions}>
                  <TouchableOpacity onPress={() => setDonating(null)}><Text style={styles.cancelText}>Cancel</Text></TouchableOpacity>
                  <Button title={donateMutation.isPending ? "Processing..." : "Donate"} onPress={() => donateMutation.mutate(c.id)} disabled={!amount || Number(amount) <= 0 || donateMutation.isPending} size="sm" />
                </View>
              </View>
            ) : (
              <TouchableOpacity style={styles.donateButton} onPress={() => setDonating(c.id)}>
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
  donateActions: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  cancelText: { color: "#6B7280", fontSize: 14 },
  donateButton: { backgroundColor: "#DC2626", borderRadius: 8, paddingVertical: 12, alignItems: "center" },
  donateButtonText: { color: "#FFF", fontSize: 14, fontWeight: "600" },
});
