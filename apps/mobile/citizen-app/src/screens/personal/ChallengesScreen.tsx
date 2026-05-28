import React, { useState, useEffect, useCallback, useRef } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, RefreshControl } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import { fetchChallenges, joinChallenge, fetchMyGoals } from "../../services/challengeService";
import type { WellnessChallenge, WellnessGoal } from "../../types";
import { USE_SEED_DATA, SEED_WELLNESS_CHALLENGES, SEED_WELLNESS_GOALS } from "../../data/seedHealthRecords";
import { useToast } from "../../components/Toast";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

type Tab = "challenges" | "goals";

const CHALLENGE_TYPE_CFG: Record<string, { icon: React.ComponentProps<typeof Ionicons>["name"]; color: string; bg: string }> = {
  STEPS:      { icon: "walk",           color: APP_GREEN,      bg: APP_GREEN_LIGHT },
  HYDRATION:  { icon: "water",          color: "#2563EB",      bg: "#EFF6FF"       },
  ACTIVITY:   { icon: "fitness",        color: APP_GOLD,       bg: APP_GOLD_LIGHT  },
  NUTRITION:  { icon: "nutrition",      color: APP_GREEN_DARK, bg: APP_GREEN_XLIGHT},
};

const STATUS_CFG: Record<string, { color: string; bg: string; label: string }> = {
  ACTIVE:    { color: APP_GREEN_DARK, bg: APP_GREEN_LIGHT, label: "Active"    },
  UPCOMING:  { color: APP_GOLD,       bg: APP_GOLD_LIGHT,  label: "Upcoming"  },
  COMPLETED: { color: APP_TEXT_2,     bg: "#EEF2F2",       label: "Completed" },
};

const PRIORITY_CFG: Record<string, { color: string; label: string }> = {
  HIGH:   { color: APP_RED,        label: "High"   },
  MEDIUM: { color: APP_GOLD,       label: "Medium" },
  LOW:    { color: APP_GREEN_DARK, label: "Low"    },
};

function GoalBar({ current, target }: { current: number; target: number }) {
  const pct = Math.min(100, (current / target) * 100);
  const color = pct >= 100 ? APP_GREEN : pct >= 70 ? APP_GREEN_DARK : APP_GOLD;
  return (
    <View style={gb.wrap}>
      <View style={gb.track}>
        <View style={[gb.fill, { width: `${pct}%` as never, backgroundColor: color }]} />
      </View>
      <Text style={[gb.label, { color }]}>{pct.toFixed(0)}%</Text>
    </View>
  );
}
const gb = StyleSheet.create({
  wrap: { flexDirection: "row", alignItems: "center", gap: 10, marginTop: 6 },
  track: { flex: 1, height: 6, backgroundColor: APP_GREEN_LIGHT, borderRadius: 3, overflow: "hidden" },
  fill: { height: "100%", borderRadius: 3 },
  label: { fontSize: 11, fontWeight: "700", width: 36, textAlign: "right" },
});

export function ChallengesScreen() {
  const toast    = useToast();
  const toastRef = useRef(toast);
  toastRef.current = toast;

  const [tab, setTab]                   = useState<Tab>("challenges");
  const [challenges, setChallenges]     = useState<WellnessChallenge[]>([]);
  const [goals, setGoals]               = useState<WellnessGoal[]>([]);
  const [isLoading, setIsLoading]       = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [joiningId, setJoiningId]       = useState<string | null>(null);

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setChallenges(SEED_WELLNESS_CHALLENGES);
        setGoals(SEED_WELLNESS_GOALS);
      } else {
        const [ch, gl] = await Promise.all([fetchChallenges(), fetchMyGoals()]);
        setChallenges(ch);
        setGoals(gl);
      }
    } catch {
      toastRef.current.error("Could not load challenges.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  const onRefresh = useCallback(() => { setIsRefreshing(true); void load(true); }, [load]);

  const handleJoin = useCallback(async (id: string) => {
    if (USE_SEED_DATA) { toastRef.current.success("Challenge joined!"); return; }
    setJoiningId(id);
    try {
      await joinChallenge(id);
      toastRef.current.success("Challenge joined!");
      void load(true);
    } catch {
      toastRef.current.error("Could not join challenge.");
    } finally {
      setJoiningId(null);
    }
  }, [load]);

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  return (
    <View style={s.root}>
      <View style={s.tabBar}>
        {(["challenges", "goals"] as Tab[]).map((t) => (
          <Pressable key={t} onPress={() => setTab(t)} style={[s.tab, tab === t && s.tabActive]}>
            <Ionicons name={t === "challenges" ? "trophy-outline" : "flag-outline"} size={14} color={tab === t ? APP_GREEN : APP_TEXT_2} />
            <Text style={[s.tabText, tab === t && s.tabTextActive]}>
              {t === "challenges" ? "Challenges" : "My Goals"}
            </Text>
          </Pressable>
        ))}
      </View>

      <ScrollView
        style={s.scroll}
        contentContainerStyle={s.content}
        showsVerticalScrollIndicator={false}
        refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor={APP_GREEN} colors={[APP_GREEN]} />}
      >
        {tab === "challenges" ? (
          challenges.length === 0 ? (
            <View style={s.empty}>
              <Ionicons name="trophy-outline" size={52} color={APP_GREEN_LIGHT} />
              <Text style={s.emptyTitle}>No challenges available</Text>
              <Text style={s.emptySub}>Check back soon for new wellness challenges.</Text>
            </View>
          ) : (
            challenges.map((ch) => {
              const cfg     = CHALLENGE_TYPE_CFG[ch.challengeType] ?? CHALLENGE_TYPE_CFG.ACTIVITY;
              const statCfg = STATUS_CFG[ch.status] ?? STATUS_CFG.ACTIVE;
              const daysLeft = Math.max(0, Math.ceil((new Date(ch.endDate).getTime() - Date.now()) / 86_400_000));
              return (
                <View key={ch.id} style={s.card}>
                  <View style={s.cardTop}>
                    <View style={[s.cardIcon, { backgroundColor: cfg.bg }]}>
                      <Ionicons name={cfg.icon} size={22} color={cfg.color} />
                    </View>
                    <View style={s.cardMain}>
                      <Text style={s.cardTitle}>{ch.title}</Text>
                      {ch.description ? <Text style={s.cardDesc} numberOfLines={2}>{ch.description}</Text> : null}
                      <View style={s.cardMeta}>
                        <View style={[s.statusChip, { backgroundColor: statCfg.bg }]}>
                          <Text style={[s.statusText, { color: statCfg.color }]}>{statCfg.label}</Text>
                        </View>
                        {daysLeft > 0 ? (
                          <Text style={s.daysLeft}>{daysLeft}d left</Text>
                        ) : null}
                        <View style={s.participantRow}>
                          <Ionicons name="people-outline" size={11} color={APP_TEXT_3} />
                          <Text style={s.participantText}>{ch.participantCount.toLocaleString()}</Text>
                        </View>
                      </View>
                      <Text style={s.targetText}>Target: {ch.targetValue.toLocaleString()} {ch.targetUnit}</Text>
                    </View>
                  </View>
                  <Pressable
                    onPress={() => handleJoin(ch.id)}
                    disabled={joiningId === ch.id}
                    style={[s.joinBtn, joiningId === ch.id && s.joinBtnDisabled]}
                  >
                    {joiningId === ch.id ? <LoadingSpinner size="sm" /> : (
                      <>
                        <Ionicons name="add-circle-outline" size={15} color="#FFFFFF" />
                        <Text style={s.joinBtnText}>Join Challenge</Text>
                      </>
                    )}
                  </Pressable>
                </View>
              );
            })
          )
        ) : (
          goals.length === 0 ? (
            <View style={s.empty}>
              <Ionicons name="flag-outline" size={52} color={APP_GREEN_LIGHT} />
              <Text style={s.emptyTitle}>No goals yet</Text>
              <Text style={s.emptySub}>Your wellness goals will appear here.</Text>
            </View>
          ) : (
            goals.map((g) => {
              const pri = PRIORITY_CFG[g.priority] ?? PRIORITY_CFG.MEDIUM;
              const isAchieved = g.status === "ACHIEVED";
              return (
                <View key={g.id} style={[s.card, isAchieved && { borderLeftWidth: 4, borderLeftColor: APP_GREEN }]}>
                  <View style={s.goalTop}>
                    <View style={s.goalMain}>
                      <Text style={s.cardTitle}>{g.title}</Text>
                      {g.description ? <Text style={s.cardDesc}>{g.description}</Text> : null}
                    </View>
                    <View style={[s.priPill, { backgroundColor: pri.color + "22" }]}>
                      <Text style={[s.priText, { color: pri.color }]}>{pri.label}</Text>
                    </View>
                  </View>
                  <GoalBar current={g.currentValue} target={g.targetValue} />
                  <View style={s.goalMeta}>
                    <Text style={s.goalProgress}>{g.currentValue} / {g.targetValue} {g.unit ?? ""}</Text>
                    {g.targetDate ? (
                      <Text style={s.goalDate}>Target: {new Date(g.targetDate).toLocaleDateString()}</Text>
                    ) : null}
                  </View>
                  {isAchieved ? (
                    <View style={s.achievedBadge}>
                      <Ionicons name="checkmark-circle" size={14} color={APP_GREEN} />
                      <Text style={s.achievedText}>Achieved!</Text>
                    </View>
                  ) : null}
                </View>
              );
            })
          )
        )}
      </ScrollView>
    </View>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  centred: { flex: 1, alignItems: "center", justifyContent: "center" },
  tabBar: { flexDirection: "row", backgroundColor: APP_SURFACE, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: APP_BORDER },
  tab: { flex: 1, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 6, paddingVertical: 11 },
  tabActive: { borderBottomWidth: 2, borderBottomColor: APP_GREEN },
  tabText: { fontSize: 13, fontWeight: "500", color: APP_TEXT_2 },
  tabTextActive: { color: APP_GREEN, fontWeight: "700" },
  scroll: { flex: 1 },
  content: { padding: 16, gap: 12, paddingBottom: 40 },
  card: {
    backgroundColor: APP_SURFACE, borderRadius: 18, padding: 16, gap: 12,
    shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3,
  },
  cardTop: { flexDirection: "row", alignItems: "flex-start", gap: 12 },
  cardIcon: { width: 48, height: 48, borderRadius: 14, alignItems: "center", justifyContent: "center" },
  cardMain: { flex: 1, gap: 5 },
  cardTitle: { fontSize: 15, fontWeight: "700", color: APP_TEXT },
  cardDesc: { fontSize: 12, color: APP_TEXT_2, lineHeight: 17 },
  cardMeta: { flexDirection: "row", alignItems: "center", gap: 8, flexWrap: "wrap" },
  statusChip: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 10 },
  statusText: { fontSize: 11, fontWeight: "700" },
  daysLeft: { fontSize: 11, color: APP_GOLD, fontWeight: "600" },
  participantRow: { flexDirection: "row", alignItems: "center", gap: 4 },
  participantText: { fontSize: 11, color: APP_TEXT_3 },
  targetText: { fontSize: 12, color: APP_GREEN_DARK, fontWeight: "600" },
  joinBtn: {
    flexDirection: "row", alignItems: "center", justifyContent: "center",
    gap: 6, backgroundColor: APP_GREEN, paddingVertical: 11, borderRadius: 12,
  },
  joinBtnDisabled: { opacity: 0.5 },
  joinBtnText: { fontSize: 13, fontWeight: "700", color: "#FFFFFF" },
  goalTop: { flexDirection: "row", alignItems: "flex-start", gap: 12 },
  goalMain: { flex: 1 },
  priPill: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 10 },
  priText: { fontSize: 11, fontWeight: "700" },
  goalMeta: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  goalProgress: { fontSize: 13, color: APP_TEXT_2 },
  goalDate: { fontSize: 12, color: APP_TEXT_3 },
  achievedBadge: { flexDirection: "row", alignItems: "center", gap: 6, backgroundColor: APP_GREEN_XLIGHT, borderRadius: 10, paddingHorizontal: 10, paddingVertical: 6, alignSelf: "flex-start" },
  achievedText: { fontSize: 12, fontWeight: "700", color: APP_GREEN },
  empty: { alignItems: "center", paddingVertical: 60, gap: 12 },
  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT },
  emptySub: { fontSize: 13, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 32, lineHeight: 19 },
});
