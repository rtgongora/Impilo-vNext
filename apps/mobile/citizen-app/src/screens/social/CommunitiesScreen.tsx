/**
 * CommunitiesScreen — Community groups from BFF.
 */
import React from "react";
import { View, Text, TouchableOpacity, StyleSheet } from "react-native";
import { Badge, LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@impilo/mobile-api-client";

interface CommunityGroup {
  id: string;
  type: string;
  attributes: { name: string; description: string; groupType: string; category: string; memberCount: number; isPublic: boolean };
}

export function CommunitiesScreen() {
  const { data, isLoading } = useQuery({
    queryKey: ["community-groups-mobile"],
    queryFn: async () => {
      const res = await apiClient.get<{ data: CommunityGroup[] }>("/internal/v1/community/groups");
      return res.data.data;
    },
  });
  const groups = data ?? [];

  if (isLoading) return <LoadingSpinner />;

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Community Groups</Text>
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
            <TouchableOpacity style={styles.joinButton}><Text style={styles.joinText}>Join Group</Text></TouchableOpacity>
          </View>
        ))
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 12 },
  title: { fontSize: 18, fontWeight: "700", color: "#111827" },
  empty: { color: "#9CA3AF", fontSize: 14, textAlign: "center", paddingVertical: 20 },
  card: { backgroundColor: "#F9FAFB", borderRadius: 12, padding: 16, gap: 6 },
  header: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  name: { fontSize: 15, fontWeight: "600", color: "#111827", flex: 1 },
  desc: { fontSize: 13, color: "#4B5563" },
  meta: { fontSize: 12, color: "#9CA3AF" },
  joinButton: { backgroundColor: "#7C3AED", borderRadius: 8, paddingVertical: 10, alignItems: "center", marginTop: 4 },
  joinText: { color: "#FFF", fontSize: 14, fontWeight: "600" },
});
