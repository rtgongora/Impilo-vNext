/**
 * MentalHealthQueueScreen — PENDING | ACCEPTED referral queue with accept/decline.
 */
import React, { useState } from "react";
import { View, Text, ScrollView, TextInput, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Button, Badge, LoadingSpinner, ErrorState, colors } from "@impilo/mobile-design-system";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  listMentalHealthReferrals,
  acceptMentalHealthReferral,
  declineMentalHealthReferral,
  type MentalHealthReferral,
} from "../../services/mentalHealthService";
import { MentalHealthReferralScreen } from "./MentalHealthReferralScreen";

export function MentalHealthQueueScreen({
  selectedReferralId,
  onSelectReferral,
}: {
  selectedReferralId: string | null;
  onSelectReferral: (id: string | null) => void;
}) {
  const [tab, setTab] = useState<"PENDING" | "ACCEPTED">("PENDING");
  const [declineReason, setDeclineReason] = useState("");
  const [decliningId, setDecliningId] = useState<string | null>(null);
  const qc = useQueryClient();

  const { data: referrals, isLoading, isError, refetch } = useQuery({
    queryKey: ["mental-health-referrals", tab],
    queryFn: () => listMentalHealthReferrals(tab),
    refetchInterval: 20_000,
  });

  const acceptMut = useMutation({
    mutationFn: (id: string) => acceptMentalHealthReferral(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["mental-health-referrals"] });
      Alert.alert("Accepted", "Referral accepted — open clinical record to continue.");
    },
    onError: () => Alert.alert("Error", "Accept failed — service may be unavailable in preview."),
  });

  const declineMut = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => declineMentalHealthReferral(id, reason),
    onSuccess: () => {
      setDecliningId(null);
      setDeclineReason("");
      void qc.invalidateQueries({ queryKey: ["mental-health-referrals"] });
    },
    onError: () => Alert.alert("Error", "Decline failed"),
  });

  if (selectedReferralId) {
    return <MentalHealthReferralScreen referralId={selectedReferralId} onBack={() => onSelectReferral(null)} />;
  }

  return (
    <ScrollView style={s.scroll} contentContainerStyle={s.pad}>
      <View style={s.tabRow}>
        {(["PENDING", "ACCEPTED"] as const).map((status) => (
          <TouchableOpacity key={status} onPress={() => setTab(status)} style={[s.tab, tab === status && s.activeTab]}>
            <Text style={[s.tabText, tab === status && s.activeTabText]}>{status === "PENDING" ? "Pending" : "Accepted"}</Text>
          </TouchableOpacity>
        ))}
      </View>

      {isLoading ? <LoadingSpinner /> : null}
      {isError ? <ErrorState message="Could not load referrals — mental-health-service may be undeployed in preview." onRetry={() => void refetch()} /> : null}

      {(referrals ?? []).map((r: MentalHealthReferral) => (
        <View key={String(r.id)} style={s.card}>
          <TouchableOpacity onPress={() => r.id && onSelectReferral(String(r.id))}>
            <Text style={s.cardTitle}>Referral {String(r.id)}</Text>
            <Text style={s.muted}>CPID {String(r.subject_cpid ?? "unknown")}</Text>
            <Text style={s.muted}>{String(r.request_reason ?? "")}</Text>
            <Badge variant="outline">{String(r.status)}</Badge>
          </TouchableOpacity>
          {tab === "PENDING" && r.id ? (
            <View style={s.actions}>
              <Button title="Accept" size="sm" onPress={() => acceptMut.mutate(String(r.id))} loading={acceptMut.isPending} />
              <Button title="Decline" size="sm" variant="outline" onPress={() => setDecliningId(String(r.id))} />
            </View>
          ) : null}
          {decliningId === r.id ? (
            <>
              <TextInput value={declineReason} onChangeText={setDeclineReason} style={s.input} placeholder="Decline reason (required)" />
              <Button title="Confirm decline" size="sm" variant="outline" onPress={() => declineMut.mutate({ id: String(r.id), reason: declineReason })} disabled={!declineReason.trim()} />
            </>
          ) : null}
        </View>
      ))}

      {!isLoading && !isError && (referrals ?? []).length === 0 ? (
        <Text style={s.muted}>{tab === "PENDING" ? "No pending referrals." : "No accepted referrals."}</Text>
      ) : null}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  scroll: { flex: 1 },
  pad: { padding: 16, gap: 10, paddingBottom: 32 },
  tabRow: { flexDirection: "row", gap: 8 },
  tab: { paddingHorizontal: 14, paddingVertical: 8, borderRadius: 16, backgroundColor: colors.gray[100] },
  activeTab: { backgroundColor: "#7C3AED" },
  tabText: { fontSize: 13, color: colors.gray[600] },
  activeTabText: { color: "#FFF", fontWeight: "600" },
  card: { borderWidth: 1, borderColor: colors.gray[200], borderRadius: 8, padding: 12, gap: 8 },
  cardTitle: { fontSize: 14, fontWeight: "600" },
  muted: { fontSize: 12, color: colors.gray[500] },
  input: { borderWidth: 1, borderColor: colors.gray[300], borderRadius: 8, padding: 8, fontSize: 13 },
  actions: { flexDirection: "row", gap: 8 },
});
