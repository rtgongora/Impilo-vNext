/**
 * CommunitiesScreen — Browse and join health communities. Backed by the
 * social timeline via experience-bff /internal/v1/mobile/citizen/social/communities.
 */
import React, { useMemo, useState } from "react";
import { View, Text, TouchableOpacity, StyleSheet, Alert, ScrollView } from "react-native";
import { Badge, Button, LoadingSpinner, EmptyState } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchSocialCommunities, joinSocialCommunity, type SocialCommunity } from "../../services/socialService";

const CATEGORIES = [
  { value: "", label: "All" },
  { value: "maternal_health", label: "Maternal" },
  { value: "diabetes", label: "Diabetes" },
  { value: "hypertension", label: "Hypertension" },
  { value: "youth_wellness", label: "Youth" },
  { value: "mental_wellness", label: "Mental" },
  { value: "fitness", label: "Fitness" },
  { value: "nutrition", label: "Nutrition" },
  { value: "community_of_practice", label: "Practice" },
];

export function CommunitiesScreen() {
  const queryClient = useQueryClient();
  const [category, setCategory] = useState("");
  const [joiningId, setJoiningId] = useState<string | null>(null);

  const { data, isLoading, refetch } = useQuery({
    queryKey: ["social-communities-mobile", category],
    queryFn: () => fetchSocialCommunities(category || undefined),
  });
  const communities: SocialCommunity[] = useMemo(() => data ?? [], [data]);

  const join = useMutation({
    mutationFn: (id: string) => joinSocialCommunity(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["social-communities-mobile"] });
    },
  });

  async function onJoin(c: SocialCommunity) {
    setJoiningId(c.id);
    try {
      await join.mutateAsync(c.id);
      Alert.alert("Joined", `You're now a member of ${c.name}.`);
      refetch();
    } catch {
      Alert.alert("Couldn’t join", "The service is unavailable right now. Please try again later.");
    } finally {
      setJoiningId(null);
    }
  }

  if (isLoading) return <LoadingSpinner />;

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Communities</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filterRow}>
        {CATEGORIES.map((c) => (
          <Button
            key={c.value || "all"}
            title={c.label}
            size="sm"
            variant={category === c.value ? "primary" : "ghost"}
            onPress={() => setCategory(c.value)}
            testID={`comm-filter-${c.value || "all"}`}
          />
        ))}
      </ScrollView>

      {communities.length === 0 ? (
        <EmptyState title="No communities" message="Try a different category or check back later." />
      ) : (
        communities.map((c) => {
          const isMember = c.viewerMembership && c.viewerMembership !== "none";
          return (
            <View key={c.id} style={styles.card}>
              <View style={styles.header}>
                <View style={[styles.avatar, { backgroundColor: c.coverColor || "#4F46E5" }]}>
                  <Text style={styles.avatarText}>{c.name.charAt(0).toUpperCase()}</Text>
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.name}>{c.name}</Text>
                  <Text style={styles.meta}>{c.category} · {c.memberCount} members · {c.kind}</Text>
                </View>
                <Badge label={c.kind === "verified" ? "Verified" : c.kind} variant={c.kind === "verified" ? "info" : "default"} />
              </View>
              {c.description ? <Text style={styles.desc}>{c.description}</Text> : null}
              <TouchableOpacity
                style={[styles.joinButton, (joiningId === c.id || isMember) && styles.joinButtonDisabled]}
                disabled={joiningId === c.id || !!isMember}
                onPress={() => onJoin(c)}
              >
                <Text style={styles.joinText}>
                  {isMember ? "Joined" : joiningId === c.id ? "Joining…" : "Join community"}
                </Text>
              </TouchableOpacity>
            </View>
          );
        })
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 12 },
  title: { fontSize: 18, fontWeight: "700", color: "#111827" },
  filterRow: { gap: 6, paddingVertical: 4 },
  card: { backgroundColor: "#F9FAFB", borderRadius: 12, padding: 16, gap: 8 },
  header: { flexDirection: "row", alignItems: "center", gap: 10 },
  avatar: { width: 40, height: 40, borderRadius: 10, alignItems: "center", justifyContent: "center" },
  avatarText: { color: "#fff", fontWeight: "700" },
  name: { fontSize: 15, fontWeight: "600", color: "#111827" },
  desc: { fontSize: 13, color: "#4B5563" },
  meta: { fontSize: 12, color: "#9CA3AF" },
  joinButton: { backgroundColor: "#7C3AED", borderRadius: 8, paddingVertical: 10, alignItems: "center", marginTop: 4 },
  joinButtonDisabled: { opacity: 0.6 },
  joinText: { color: "#FFF", fontSize: 14, fontWeight: "600" },
});
