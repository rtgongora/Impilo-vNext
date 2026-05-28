import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, RefreshControl } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import type { CarePlan, CarePlanStatus, CarePlanGoal } from "../../types";
import { USE_SEED_DATA, SEED_CARE_PLANS } from "../../data/seedHealthRecords";
// When USE_SEED_DATA is false: import { fetchCarePlans } from "../../services/carePlansService";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

const STATUS_CFG: Record<CarePlanStatus, { color: string; bg: string; label: string }> = {
  DRAFT:     { color: APP_TEXT_2,     bg: "#EEF2F2",        label: "Draft"     },
  ACTIVE:    { color: APP_GREEN_DARK, bg: APP_GREEN_LIGHT,  label: "Active"    },
  ON_HOLD:   { color: APP_GOLD,       bg: APP_GOLD_LIGHT,   label: "On Hold"   },
  COMPLETED: { color: APP_GREEN,      bg: APP_GREEN_XLIGHT, label: "Completed" },
  CANCELLED: { color: APP_RED,        bg: APP_RED_LIGHT,    label: "Cancelled" },
};

const GOAL_CFG: Record<CarePlanGoal["status"], { color: string; icon: React.ComponentProps<typeof Ionicons>["name"] }> = {
  PENDING:      { color: APP_TEXT_3,     icon: "ellipse-outline"    },
  IN_PROGRESS:  { color: APP_GOLD,       icon: "time-outline"       },
  ACHIEVED:     { color: APP_GREEN_DARK, icon: "checkmark-circle"   },
  NOT_ACHIEVED: { color: APP_RED,        icon: "close-circle"       },
};

function GoalProgressBar({ goals }: { goals: CarePlanGoal[] }) {
  const achieved = goals.filter((g) => g.status === "ACHIEVED").length;
  const pct      = goals.length > 0 ? (achieved / goals.length) * 100 : 0;
  return (
    <View style={pb.wrap}>
      <View style={pb.track}>
        <View style={[pb.fill, { width: `${pct}%` as never }]} />
      </View>
      <Text style={pb.label}>{achieved}/{goals.length}</Text>
    </View>
  );
}

const pb = StyleSheet.create({
  wrap: { flexDirection: "row", alignItems: "center", gap: 10, marginTop: 6 },
  track: { flex: 1, height: 6, backgroundColor: APP_GREEN_LIGHT, borderRadius: 3, overflow: "hidden" },
  fill: { height: "100%", backgroundColor: APP_GREEN, borderRadius: 3 },
  label: { fontSize: 11, fontWeight: "700", color: APP_GREEN_DARK, width: 32, textAlign: "right" },
});

export function CarePlansSection() {
  const [plans, setPlans]               = useState<CarePlan[]>([]);
  const [isLoading, setIsLoading]       = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [expanded, setExpanded]         = useState<string | null>(null);

  const load = useCallback(async (silent = false) => {
    if (!silent) setIsLoading(true);
    try {
      if (USE_SEED_DATA) {
        await new Promise((r) => setTimeout(r, 350));
        setPlans(SEED_CARE_PLANS);
      } else {
        // const r = await fetchCarePlans(); setPlans(r.items);
        setPlans([]);
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

  const active    = plans.filter((p) => p.status === "ACTIVE").length;
  const completed = plans.filter((p) => p.status === "COMPLETED").length;
  const totalGoals    = plans.flatMap((p) => p.goals).length;
  const achievedGoals = plans.flatMap((p) => p.goals).filter((g) => g.status === "ACHIEVED").length;

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  return (
    <ScrollView
      style={s.root}
      contentContainerStyle={s.content}
      showsVerticalScrollIndicator={false}
      refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} tintColor={APP_GREEN} colors={[APP_GREEN]} />}
    >
      {/* Summary */}
      <View style={s.summaryStrip}>
        <View style={[s.summaryCard, { backgroundColor: APP_GREEN_LIGHT }]}>
          <Text style={[s.summaryNum, { color: APP_GREEN_DARK }]}>{active}</Text>
          <Text style={s.summaryLbl}>Active Plans</Text>
        </View>
        <View style={[s.summaryCard, { backgroundColor: APP_GREEN_XLIGHT }]}>
          <Text style={[s.summaryNum, { color: APP_GREEN }]}>{achievedGoals}/{totalGoals}</Text>
          <Text style={s.summaryLbl}>Goals Met</Text>
        </View>
        <View style={[s.summaryCard, { backgroundColor: "#EEF2F2" }]}>
          <Text style={[s.summaryNum, { color: APP_TEXT_2 }]}>{completed}</Text>
          <Text style={s.summaryLbl}>Completed</Text>
        </View>
      </View>

      {plans.length === 0 ? (
        <View style={s.empty}>
          <Ionicons name="clipboard-outline" size={52} color={APP_GREEN_LIGHT} />
          <Text style={s.emptyTitle}>No care plans</Text>
          <Text style={s.emptySub}>Your personalised care plans will appear here when created by your care team.</Text>
        </View>
      ) : (
        plans.map((plan) => {
          const isOpen = expanded === plan.id;
          const cfg    = STATUS_CFG[plan.status] ?? STATUS_CFG.ACTIVE;
          return (
            <Pressable
              key={plan.id}
              onPress={() => setExpanded(isOpen ? null : plan.id)}
              style={[s.card, { borderLeftColor: cfg.color }]}
            >
              <View style={s.cardTop}>
                <View style={[s.cardIcon, { backgroundColor: cfg.bg }]}>
                  <Ionicons name="clipboard" size={20} color={cfg.color} />
                </View>
                <View style={s.cardMain}>
                  <Text style={s.planName}>{plan.name}</Text>
                  {plan.description ? <Text style={s.planDesc} numberOfLines={2}>{plan.description}</Text> : null}
                  {plan.createdBy ? (
                    <View style={s.metaRow}>
                      <Ionicons name="person-circle-outline" size={12} color={APP_TEXT_3} />
                      <Text style={s.metaText}>{plan.createdBy}</Text>
                    </View>
                  ) : null}
                  {plan.goals.length > 0 ? (
                    <GoalProgressBar goals={plan.goals} />
                  ) : null}
                </View>
                <View style={s.cardRight}>
                  <View style={[s.statusChip, { backgroundColor: cfg.bg }]}>
                    <Text style={[s.statusText, { color: cfg.color }]}>{cfg.label}</Text>
                  </View>
                  <Ionicons name={isOpen ? "chevron-up" : "chevron-down"} size={14} color={APP_TEXT_3} style={{ marginTop: 6 }} />
                </View>
              </View>

              {isOpen ? (
                <View style={s.expanded}>
                  <Text style={s.expandedSectionLabel}>Goals</Text>
                  {plan.goals.map((goal) => {
                    const gcfg = GOAL_CFG[goal.status];
                    return (
                      <View key={goal.id} style={s.goalRow}>
                        <Ionicons name={gcfg.icon} size={16} color={gcfg.color} />
                        <View style={s.goalInfo}>
                          <Text style={s.goalDesc}>{goal.description}</Text>
                          {goal.targetDate ? (
                            <Text style={s.goalDate}>
                              Target: {new Date(goal.targetDate).toLocaleDateString()}
                            </Text>
                          ) : null}
                        </View>
                      </View>
                    );
                  })}

                  {plan.interventions.length > 0 ? (
                    <>
                      <View style={s.divider} />
                      <Text style={s.expandedSectionLabel}>Interventions</Text>
                      {plan.interventions.map((inv) => (
                        <View key={inv.id} style={s.interventionRow}>
                          <View style={[s.invDot, { backgroundColor: inv.status === "ACTIVE" ? APP_GREEN : APP_TEXT_3 }]} />
                          <View style={s.goalInfo}>
                            <Text style={s.goalDesc}>{inv.description}</Text>
                            {inv.frequency ? <Text style={s.goalDate}>{inv.frequency}</Text> : null}
                          </View>
                        </View>
                      ))}
                    </>
                  ) : null}
                </View>
              ) : null}
            </Pressable>
          );
        })
      )}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  content: { padding: 16, gap: 12, paddingBottom: 40 },
  centred: { flex: 1, alignItems: "center", justifyContent: "center" },
  summaryStrip: { flexDirection: "row", gap: 10 },
  summaryCard: { flex: 1, borderRadius: 14, padding: 14, alignItems: "center", gap: 4 },
  summaryNum: { fontSize: 20, fontWeight: "800" },
  summaryLbl: { fontSize: 10, fontWeight: "600", color: APP_TEXT_2, textAlign: "center" },
  card: {
    backgroundColor: APP_SURFACE, borderRadius: 16, borderLeftWidth: 4,
    overflow: "hidden", shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 6, elevation: 3,
  },
  cardTop: { flexDirection: "row", alignItems: "flex-start", gap: 12, padding: 14 },
  cardIcon: { width: 42, height: 42, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  cardMain: { flex: 1 },
  cardRight: { alignItems: "flex-end" },
  planName: { fontSize: 15, fontWeight: "700", color: APP_TEXT, marginBottom: 3 },
  planDesc: { fontSize: 12, color: APP_TEXT_2, lineHeight: 17, marginBottom: 5 },
  metaRow: { flexDirection: "row", alignItems: "center", gap: 4 },
  metaText: { fontSize: 12, color: APP_TEXT_3 },
  statusChip: { paddingHorizontal: 8, paddingVertical: 4, borderRadius: 10 },
  statusText: { fontSize: 11, fontWeight: "700" },
  expanded: {
    paddingHorizontal: 14, paddingBottom: 14, gap: 10,
    borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER,
  },
  expandedSectionLabel: { fontSize: 11, fontWeight: "700", color: APP_TEXT_2, textTransform: "uppercase", letterSpacing: 0.8, marginTop: 4 },
  goalRow: { flexDirection: "row", alignItems: "flex-start", gap: 10 },
  goalInfo: { flex: 1 },
  goalDesc: { fontSize: 13, color: APP_TEXT, lineHeight: 18 },
  goalDate: { fontSize: 11, color: APP_TEXT_3, marginTop: 2 },
  divider: { height: StyleSheet.hairlineWidth, backgroundColor: APP_BORDER },
  interventionRow: { flexDirection: "row", alignItems: "flex-start", gap: 10 },
  invDot: { width: 8, height: 8, borderRadius: 4, marginTop: 5 },
  empty: { alignItems: "center", paddingVertical: 60, gap: 12 },
  emptyTitle: { fontSize: 17, fontWeight: "700", color: APP_TEXT },
  emptySub: { fontSize: 13, color: APP_TEXT_3, textAlign: "center", paddingHorizontal: 32, lineHeight: 19 },
});
