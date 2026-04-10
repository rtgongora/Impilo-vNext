/**
 * CommunitiesScreen — Community groups from BFF; join calls real POST /community/groups/{id}/join.
 */
import React, { useState } from "react";
import { View, Text, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Badge, LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@impilo/mobile-api-client";
import { useAppStore } from "../../stores/appStore";

interface CommunityGroup {
  id: string;
  type: string;
  attributes: { name: string; description: string; groupType: string; category: string; memberCount: number; isPublic: boolean };
}

export function CommunitiesScreen() {
  const queryClient = useQueryClient();
  const profile = useAppStore().profile;
  const memberId = profile?.cpid ?? "";
  const [joiningId, setJoiningId] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ["community-groups-mobile"],
    queryFn: async () => {
      const res = await apiClient.get<{ data: CommunityGroup[] }>("/internal/v1/community/groups");
      return res.data.data;
    },
  });
  const groups = data ?? [];

  const joinMutation = useMutation({
    mutationFn: async (groupId: string) => {
      if (!memberId) throw new Error("missing_member");
      await apiClient.post(`/internal/v1/community/groups/${groupId}/join`, { memberId });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["community-groups-mobile"] });
    },
  });

  async function onJoin(groupId: string) {
    if (!memberId) {
      Alert.alert("Profile required", "Sign in with a citizen profile (CPID) before joining a group.");
      return;
    }
    setJoiningId(groupId);
    try {
      await joinMutation.mutateAsync(groupId);
      Alert.alert("Joined", "Your membership was recorded in Experience BFF.");
    } catch (e) {
      Alert.alert("Join failed", "The server could not record membership. Check your session and try again.");
    } finally {
      setJoiningId(null);
    }
  }

  if (isLoading) return <LoadingSpinner />;

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Community Groups</Text>
      {!memberId ? (
        <Text style={styles.hint}>Set a citizen profile (CPID) to enable Join — the BFF requires memberId.</Text>
      ) : null}
      {groups.length === 0 ? (
        <Text style={styles.empty}>No community groups available</Text>
      ) : (
        groups.map((g) => (
          <View key={g.id} style={styles.card}>
            <View style={styles.header}>
              <Text style={styles.name}>{g.attributes.name}</Text>
              <Badge label={g.attributes.groupType} variant="info" />
            </View>
            <Text style={styles.desc}>{g.attributes.description}</Text>
            <Text style={styles.meta}>{g.attributes.category} · {g.attributes.memberCount} members · {g.attributes.isPublic ? "Public" : "Private"}</Text>
            <TouchableOpacity
              style={[styles.joinButton, (joiningId === g.id || joinMutation.isPending) && styles.joinButtonDisabled]}
              disabled={joiningId === g.id || joinMutation.isPending}
              onPress={() => onJoin(g.id)}
            >
              <Text style={styles.joinText}>{joiningId === g.id ? "Joining…" : "Join Group"}</Text>
            </TouchableOpacity>
          </View>
        ))
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 12 },
  title: { fontSize: 18, fontWeight: "700", color: "#111827" },
  hint: { fontSize: 13, color: "#B45309", backgroundColor: "#FEF3C7", padding: 10, borderRadius: 8 },
  empty: { color: "#9CA3AF", fontSize: 14, textAlign: "center", paddingVertical: 20 },
  card: { backgroundColor: "#F9FAFB", borderRadius: 12, padding: 16, gap: 6 },
  header: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  name: { fontSize: 15, fontWeight: "600", color: "#111827", flex: 1 },
  desc: { fontSize: 13, color: "#4B5563" },
  meta: { fontSize: 12, color: "#9CA3AF" },
  joinButton: { backgroundColor: "#7C3AED", borderRadius: 8, paddingVertical: 10, alignItems: "center", marginTop: 4 },
  joinButtonDisabled: { opacity: 0.6 },
  joinText: { color: "#FFF", fontSize: 14, fontWeight: "600" },
});
