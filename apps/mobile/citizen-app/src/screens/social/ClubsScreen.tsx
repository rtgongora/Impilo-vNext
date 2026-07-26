/**
 * ClubsScreen — Wellness and fitness club membership.
 */
import React from "react";
import { View, Text, TouchableOpacity, StyleSheet } from "react-native";
import { Badge, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchClubs, joinClub } from "../../services/clubsService";
import { useAppStore } from "../../stores/appStore";

const CLUB_TYPE_ICONS: Record<string, string> = { FITNESS: "🏃", SUPPORT: "🤝", WELLNESS: "🧘", SOCIAL: "👥" };

export function ClubsScreen() {
  const profile = useAppStore((s) => s.profile);
  const queryClient = useQueryClient();

  const { data: clubs = [], isLoading } = useQuery({ queryKey: ["clubs"], queryFn: () => fetchClubs() });

  const joinMutation = useMutation({
    mutationFn: (clubId: string) => joinClub(clubId, profile?.cpid ?? ""),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["clubs"] }),
  });

  if (isLoading) return <LoadingSpinner />;

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Wellness Clubs</Text>
      {clubs.map((c) => (
        <View key={c.id} style={styles.card}>
          <View style={styles.cardHeader}>
            <Text style={styles.icon}>{CLUB_TYPE_ICONS[c.clubType] ?? "👥"}</Text>
            <View style={{ flex: 1 }}>
              <Text style={styles.clubName}>{c.name}</Text>
              <Text style={styles.clubMeta}>{c.category} · {c.memberCount} members</Text>
            </View>
            <Badge label={c.clubType} variant="info" />
          </View>
          {c.description ? <Text style={styles.clubDesc}>{c.description}</Text> : null}
          {c.meetingSchedule ? <Text style={styles.schedule}>📅 {c.meetingSchedule}</Text> : null}
          {c.location ? <Text style={styles.location}>📍 {c.location}</Text> : null}
          <TouchableOpacity style={styles.joinButton} onPress={() => joinMutation.mutate(c.id)}>
            <Text style={styles.joinText}>Join Club</Text>
          </TouchableOpacity>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 12 },
  title: { fontSize: 18, fontWeight: "700", color: colors.gray[900] },
  card: { backgroundColor: colors.gray[50], borderRadius: 12, padding: 16, gap: 8 },
  cardHeader: { flexDirection: "row", alignItems: "center", gap: 12 },
  icon: { fontSize: 28 },
  clubName: { fontSize: 15, fontWeight: "600", color: colors.gray[900] },
  clubMeta: { fontSize: 12, color: colors.gray[500] },
  clubDesc: { fontSize: 13, color: colors.gray[600] },
  schedule: { fontSize: 12, color: colors.gray[500] },
  location: { fontSize: 12, color: colors.gray[500] },
  joinButton: { backgroundColor: "#2563EB", borderRadius: 8, paddingVertical: 10, alignItems: "center" },
  joinText: { color: "#FFF", fontSize: 14, fontWeight: "600" },
});
