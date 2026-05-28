import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, RefreshControl, Linking } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import type { CareTeamMember } from "../../types";
import { USE_SEED_DATA, SEED_CARE_TEAM } from "../../data/seedHealthRecords";
// When USE_SEED_DATA is false: import { fetchCareTeam } from "../../services/careTeamService";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

const ROLE_ICON: Record<string, React.ComponentProps<typeof Ionicons>["name"]> = {
  "Primary Care Physician": "person-circle",
  "Primary Care Nurse":     "medkit",
  "Specialist":             "ribbon",
  "General Practitioner":   "business",
};

function initials(name: string): string {
  return name.split(" ").map((w) => w[0] ?? "").slice(0, 2).join("").toUpperCase();
}

const AVATAR_COLORS = [APP_GREEN, APP_GREEN_DARK, "#00695C", "#26A69A"];

export function CareTeamSection() {
  const [team, setTeam]                 = useState<CareTeamMember[]>([]);
  const [isLoading, setIsLoading]       = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setTeam(SEED_CARE_TEAM);
      } else {
        // const r = await fetchCareTeam(); setTeam(r.items);
        setTeam([]);
      }
    } catch {
      // silent
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  const onRefresh = useCallback(() => { setIsRefreshing(true); void load(true); }, [load]);

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  const primary  = team.find((m) => m.isPrimary);
  const others   = team.filter((m) => !m.isPrimary);

  return (
    <ScrollView
      style={s.root}
      contentContainerStyle={s.content}
      showsVerticalScrollIndicator={false}
      refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor={APP_GREEN} colors={[APP_GREEN]} />}
    >
      {team.length === 0 ? (
        <View style={s.empty}>
          <Ionicons name="people-outline" size={52} color={APP_GREEN_LIGHT} />
          <Text style={s.emptyTitle}>No care team assigned</Text>
          <Text style={s.emptySub}>Your healthcare providers will appear here once assigned.</Text>
        </View>
      ) : (
        <>
          {/* Primary provider hero card */}
          {primary ? (
            <View style={s.primaryCard}>
              <View style={s.primaryCardDecor1} />
              <View style={s.primaryCardDecor2} />
              <View style={s.primaryAvatar}>
                <Text style={s.primaryAvatarText}>{initials(primary.name)}</Text>
              </View>
              <View style={s.primaryBadge}>
                <Ionicons name="star" size={10} color="#FFFFFF" />
                <Text style={s.primaryBadgeText}>Primary</Text>
              </View>
              <Text style={s.primaryName}>{primary.name}</Text>
              <Text style={s.primaryRole}>{primary.role}</Text>
              {primary.specialty ? <Text style={s.primarySpecialty}>{primary.specialty}</Text> : null}
              {primary.facilityName ? (
                <View style={s.primaryFacilityRow}>
                  <Ionicons name="location-outline" size={12} color="rgba(255,255,255,0.75)" />
                  <Text style={s.primaryFacility}>{primary.facilityName}</Text>
                </View>
              ) : null}
              {primary.phone ? (
                <Pressable
                  onPress={() => Linking.openURL(`tel:${primary.phone}`)}
                  style={s.callBtn}
                >
                  <Ionicons name="call-outline" size={15} color={APP_GREEN} />
                  <Text style={s.callBtnText}>{primary.phone}</Text>
                </Pressable>
              ) : null}
            </View>
          ) : null}

          {/* Other team members */}
          {others.length > 0 ? (
            <>
              <Text style={s.sectionLabel}>Care Team Members</Text>
              {others.map((member, idx) => (
                <View key={member.id} style={s.memberCard}>
                  <View style={[s.memberAvatar, { backgroundColor: AVATAR_COLORS[idx % AVATAR_COLORS.length] }]}>
                    <Text style={s.memberAvatarText}>{initials(member.name)}</Text>
                  </View>
                  <View style={s.memberInfo}>
                    <Text style={s.memberName}>{member.name}</Text>
                    <Text style={s.memberRole}>{member.role}</Text>
                    {member.specialty ? <Text style={s.memberSpecialty}>{member.specialty}</Text> : null}
                    {member.facilityName ? (
                      <View style={s.memberMetaRow}>
                        <Ionicons name="business-outline" size={12} color={APP_TEXT_3} />
                        <Text style={s.memberMeta}>{member.facilityName}</Text>
                      </View>
                    ) : null}
                  </View>
                  {member.phone ? (
                    <Pressable
                      onPress={() => Linking.openURL(`tel:${member.phone}`)}
                      style={s.memberCallBtn}
                      hitSlop={8}
                    >
                      <Ionicons name="call-outline" size={18} color={APP_GREEN} />
                    </Pressable>
                  ) : null}
                </View>
              ))}
            </>
          ) : null}
        </>
      )}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  content: { padding: 16, gap: 12, paddingBottom: 40 },
  centred: { flex: 1, alignItems: "center", justifyContent: "center" },

  primaryCard: {
    backgroundColor: APP_GREEN, borderRadius: 24, padding: 22,
    alignItems: "center", overflow: "hidden",
    shadowColor: APP_GREEN_DARK, shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.3, shadowRadius: 16, elevation: 10,
  },
  primaryCardDecor1: {
    position: "absolute", width: 160, height: 160, borderRadius: 80,
    borderWidth: 24, borderColor: "rgba(255,255,255,0.07)", top: -50, right: -50,
  },
  primaryCardDecor2: {
    position: "absolute", width: 90, height: 90, borderRadius: 45,
    borderWidth: 16, borderColor: "rgba(255,255,255,0.05)", bottom: -20, left: -20,
  },
  primaryAvatar: {
    width: 72, height: 72, borderRadius: 36,
    backgroundColor: "rgba(255,255,255,0.2)", alignItems: "center", justifyContent: "center",
    borderWidth: 2, borderColor: "rgba(255,255,255,0.35)", marginBottom: 10,
  },
  primaryAvatarText: { fontSize: 24, fontWeight: "800", color: "#FFFFFF" },
  primaryBadge: {
    flexDirection: "row", alignItems: "center", gap: 4,
    backgroundColor: "rgba(255,255,255,0.15)", paddingHorizontal: 10, paddingVertical: 4,
    borderRadius: 20, marginBottom: 10,
  },
  primaryBadgeText: { fontSize: 11, fontWeight: "700", color: "#FFFFFF" },
  primaryName: { fontSize: 20, fontWeight: "800", color: "#FFFFFF", textAlign: "center" },
  primaryRole: { fontSize: 13, color: "rgba(255,255,255,0.8)", marginTop: 3, textAlign: "center" },
  primarySpecialty: { fontSize: 12, color: "rgba(255,255,255,0.65)", marginTop: 2, textAlign: "center" },
  primaryFacilityRow: { flexDirection: "row", alignItems: "center", gap: 4, marginTop: 8 },
  primaryFacility: { fontSize: 12, color: "rgba(255,255,255,0.75)" },
  callBtn: {
    flexDirection: "row", alignItems: "center", gap: 8,
    backgroundColor: "rgba(255,255,255,0.92)", paddingHorizontal: 18, paddingVertical: 10,
    borderRadius: 20, marginTop: 16,
  },
  callBtnText: { fontSize: 13, fontWeight: "700", color: APP_GREEN },

  sectionLabel: {
    fontSize: 12, fontWeight: "700", color: APP_TEXT_2,
    textTransform: "uppercase", letterSpacing: 0.8, paddingHorizontal: 4,
  },
  memberCard: {
    flexDirection: "row", alignItems: "center", gap: 14,
    backgroundColor: APP_SURFACE, borderRadius: 16, padding: 14,
    shadowColor: "#000", shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05, shadowRadius: 4, elevation: 2,
  },
  memberAvatar: {
    width: 46, height: 46, borderRadius: 14, alignItems: "center", justifyContent: "center",
  },
  memberAvatarText: { fontSize: 16, fontWeight: "800", color: "#FFFFFF" },
  memberInfo: { flex: 1 },
  memberName: { fontSize: 15, fontWeight: "700", color: APP_TEXT },
  memberRole: { fontSize: 12, color: APP_TEXT_2, marginTop: 2 },
  memberSpecialty: { fontSize: 12, color: APP_GREEN_DARK, fontWeight: "600", marginTop: 1 },
  memberMetaRow: { flexDirection: "row", alignItems: "center", gap: 4, marginTop: 4 },
  memberMeta: { fontSize: 11, color: APP_TEXT_3 },
  memberCallBtn: {
    width: 38, height: 38, borderRadius: 10,
    backgroundColor: APP_GREEN_XLIGHT, alignItems: "center", justifyContent: "center",
  },

  empty: { alignItems: "center", paddingVertical: 60, gap: 12 },
  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT },
  emptySub: { fontSize: 13, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 32, lineHeight: 19 },
});
