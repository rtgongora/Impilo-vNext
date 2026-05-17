/**
 * PagesScreen — Browse facility/programme/campaign pages and follow them.
 * Backed by /internal/v1/mobile/citizen/social/pages.
 */
import React, { useMemo, useState } from "react";
import { View, Text, TouchableOpacity, StyleSheet, Alert, ScrollView } from "react-native";
import { Badge, Button, LoadingSpinner, EmptyState } from "@impilo/mobile-design-system";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchSocialPages, followSocialPage, type SocialPage } from "../../services/socialService";

const KINDS = [
  { value: "", label: "All" },
  { value: "facility", label: "Facilities" },
  { value: "organisation", label: "Organisations" },
  { value: "programme", label: "Programmes" },
  { value: "campaign", label: "Campaigns" },
  { value: "service", label: "Services" },
];

export function PagesScreen() {
  const queryClient = useQueryClient();
  const [kind, setKind] = useState("");
  const [followingId, setFollowingId] = useState<string | null>(null);

  const { data, isLoading, refetch } = useQuery({
    queryKey: ["social-pages-mobile", kind],
    queryFn: () => fetchSocialPages(kind || undefined),
  });
  const pages: SocialPage[] = useMemo(() => data ?? [], [data]);

  const follow = useMutation({
    mutationFn: (id: string) => followSocialPage(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["social-pages-mobile"] });
    },
  });

  async function onFollow(p: SocialPage) {
    setFollowingId(p.id);
    try {
      await follow.mutateAsync(p.id);
      refetch();
    } catch {
      Alert.alert("Couldn’t follow", "Try again in a moment.");
    } finally {
      setFollowingId(null);
    }
  }

  if (isLoading) return <LoadingSpinner />;

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Pages</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filterRow}>
        {KINDS.map((k) => (
          <Button
            key={k.value || "all"}
            title={k.label}
            size="sm"
            variant={kind === k.value ? "primary" : "ghost"}
            onPress={() => setKind(k.value)}
            testID={`pages-filter-${k.value || "all"}`}
          />
        ))}
      </ScrollView>

      {pages.length === 0 ? (
        <EmptyState title="No pages" message="Pages will appear as facilities and programmes publish content." />
      ) : (
        pages.map((p) => (
          <View key={p.id} style={styles.card}>
            <View style={styles.avatar}><Text style={styles.avatarText}>{p.name.charAt(0).toUpperCase()}</Text></View>
            <View style={{ flex: 1, gap: 4 }}>
              <View style={{ flexDirection: "row", alignItems: "center", gap: 6 }}>
                <Text style={styles.name}>{p.name}</Text>
                {p.verified ? <Badge label="Verified" variant="info" /> : null}
              </View>
              <Text style={styles.meta}>{p.kind} · {p.followerCount} followers</Text>
              {p.bio ? <Text style={styles.bio} numberOfLines={2}>{p.bio}</Text> : null}
              <TouchableOpacity
                onPress={() => onFollow(p)}
                disabled={followingId === p.id}
                style={[styles.followButton, p.viewerFollows && styles.followingButton]}
              >
                <Text style={[styles.followText, p.viewerFollows && styles.followingText]}>
                  {p.viewerFollows ? "Following" : followingId === p.id ? "…" : "Follow"}
                </Text>
              </TouchableOpacity>
            </View>
          </View>
        ))
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 12 },
  title: { fontSize: 18, fontWeight: "700", color: "#111827" },
  filterRow: { gap: 6, paddingVertical: 4 },
  card: { flexDirection: "row", backgroundColor: "#F9FAFB", borderRadius: 12, padding: 16, gap: 12 },
  avatar: { width: 48, height: 48, borderRadius: 12, backgroundColor: "#EEF2FF", alignItems: "center", justifyContent: "center" },
  avatarText: { fontSize: 18, fontWeight: "700", color: "#3730A3" },
  name: { fontSize: 15, fontWeight: "700", color: "#111827" },
  meta: { fontSize: 12, color: "#6B7280" },
  bio: { fontSize: 12, color: "#4B5563" },
  followButton: { backgroundColor: "#7C3AED", borderRadius: 8, paddingVertical: 8, paddingHorizontal: 14, alignSelf: "flex-start", marginTop: 4 },
  followingButton: { backgroundColor: "#E5E7EB" },
  followText: { color: "#FFF", fontSize: 13, fontWeight: "600" },
  followingText: { color: "#374151" },
});
