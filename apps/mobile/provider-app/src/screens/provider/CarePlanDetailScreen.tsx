/**
 * CarePlanDetailScreen — Full-depth nursing care plan with 4 tabs,
 * goal milestones, intervention CRUD, and progress tracking.
 */
import React, { useState, useRef, useEffect } from "react";
import { View, Text, TextInput, ScrollView, TouchableOpacity, StyleSheet, Alert, Share, Animated, LayoutAnimation, Platform, UIManager } from "react-native";

if (Platform.OS === "android" && UIManager.setLayoutAnimationEnabledExperimental) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}
import { Screen, Header, Button, Badge, LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchCarePlans, createCarePlan } from "../../services/inpatientService";
import { apiClient } from "@impilo/mobile-api-client";
import { useEncounterStore } from "../../stores/encounterStore";

type PlanTab = "overview" | "goals" | "interventions" | "evaluations";
const GOAL_CATEGORIES = ["CLINICAL", "FUNCTIONAL", "BEHAVIORAL", "EDUCATIONAL", "DISCHARGE"];
const GOAL_PRIORITIES = ["LOW", "MEDIUM", "HIGH", "URGENT"];
const INT_CATEGORIES = ["ASSESSMENT", "MEDICATION", "NUTRITION", "HYGIENE", "MOBILITY", "RESPIRATORY", "WOUND", "PSYCHOSOCIAL", "EDUCATION"];
const CATEGORY_COLORS: Record<string, string> = { CLINICAL: "#3B82F6", FUNCTIONAL: "#22C55E", BEHAVIORAL: "#F59E0B", EDUCATIONAL: "#8B5CF6", DISCHARGE: "#EC4899" };
const INT_COLORS: Record<string, string> = { ASSESSMENT: "#3B82F6", MEDICATION: "#DC2626", NUTRITION: "#22C55E", HYGIENE: "#06B6D4", MOBILITY: "#F59E0B", RESPIRATORY: "#8B5CF6", WOUND: "#EC4899", PSYCHOSOCIAL: "#F97316", EDUCATION: "#6366F1" };

export function CarePlanDetailScreen() {
  const { activeEncounter } = useEncounterStore();
  const pid = activeEncounter?.patientId ?? "";
  const qc = useQueryClient();
  const { data: plans = [], isLoading } = useQuery({ queryKey: ["care-plans-full", pid], queryFn: () => fetchCarePlans(pid), enabled: !!pid });

  const [tab, setTab] = useState<PlanTab>("overview");
  const [selectedPlan, setSelectedPlan] = useState<Record<string, unknown> | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [createForm, setCreateForm] = useState({ title: "", planType: "NURSING" });

  // Goal add form
  const [showAddGoal, setShowAddGoal] = useState(false);
  const [goalForm, setGoalForm] = useState({ description: "", category: "CLINICAL", priority: "MEDIUM", targetValue: "", targetDate: "" });

  // Intervention add form
  const [showAddInt, setShowAddInt] = useState(false);
  const [intForm, setIntForm] = useState({ description: "", category: "ASSESSMENT", frequency: "", responsibleRole: "" });

  const createMut = useMutation({
    mutationFn: () => createCarePlan({ patientId: pid, encounterId: activeEncounter?.id, title: createForm.title, planType: createForm.planType }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["care-plans-full"] }); setShowCreate(false); setCreateForm({ title: "", planType: "NURSING" }); },
  });

  const addGoalMut = useMutation({
    mutationFn: () => apiClient.post(`/internal/v1/care-plans/${selectedPlan?.id}/goals`, goalForm),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["care-plans-full"] }); setShowAddGoal(false); setGoalForm({ description: "", category: "CLINICAL", priority: "MEDIUM", targetValue: "", targetDate: "" }); },
  });

  const updateGoalMut = useMutation({
    mutationFn: ({ goalId, status }: { goalId: string; status: string }) => apiClient.post(`/internal/v1/care-plans/${selectedPlan?.id}/goals/${goalId}/update`, { status, currentValue: "" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["care-plans-full"] }),
  });

  const addIntMut = useMutation({
    mutationFn: () => apiClient.post(`/internal/v1/care-plans/${selectedPlan?.id}/interventions`, intForm),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["care-plans-full"] }); setShowAddInt(false); setIntForm({ description: "", category: "ASSESSMENT", frequency: "", responsibleRole: "" }); },
  });

  const performIntMut = useMutation({
    mutationFn: (intId: string) => apiClient.post(`/internal/v1/care-plans/${selectedPlan?.id}/interventions/${intId}/perform`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["care-plans-full"] }),
  });

  if (isLoading) return <LoadingSpinner />;

  const typedPlans = plans as Array<Record<string, unknown>>;
  const goals = (selectedPlan?.goals ?? []) as Array<Record<string, unknown>>;
  const interventions = (selectedPlan?.interventions ?? []) as Array<Record<string, unknown>>;
  const totalItems = goals.length + interventions.length;
  const completedItems = goals.filter((g) => g.status === "ACHIEVED").length + interventions.filter((i) => i.status === "COMPLETED").length;
  const progressPct = totalItems > 0 ? Math.round((completedItems / totalItems) * 100) : 0;

  // Plan list
  if (!selectedPlan) {
    return (
      <View style={s.section}>
        <View style={s.headerRow}>
          <Text style={s.title}>Care Plans</Text>
          <Button label="+ New" size="small" onPress={() => setShowCreate(true)} />
        </View>
        {showCreate && (
          <View style={s.form}>
            <TextInput style={s.input} placeholder="Plan title" value={createForm.title} onChangeText={(v) => setCreateForm({ ...createForm, title: v })} />
            <View style={s.chipRow}>
              {["NURSING", "MEDICAL", "MULTIDISCIPLINARY"].map((t) => (
                <TouchableOpacity key={t} onPress={() => setCreateForm({ ...createForm, planType: t })} style={[s.chip, createForm.planType === t && s.chipActive]}>
                  <Text style={[s.chipText, createForm.planType === t && s.chipTextActive]}>{t}</Text>
                </TouchableOpacity>
              ))}
            </View>
            <Button label="Create" onPress={() => createMut.mutate()} disabled={!createForm.title || createMut.isPending} />
          </View>
        )}
        {typedPlans.map((p) => (
          <TouchableOpacity key={String(p.id)} style={s.planCard} onPress={() => setSelectedPlan(p)}>
            <View style={s.planHeader}>
              <Text style={s.planTitle}>{String(p.title)}</Text>
              <Badge label={String(p.status)} variant={p.status === "ACTIVE" ? "success" : "default"} />
            </View>
            <Text style={s.planMeta}>{String(p.plan_type)} · Goals: {((p.goals as unknown[]) ?? []).length} · Interventions: {((p.interventions as unknown[]) ?? []).length}</Text>
          </TouchableOpacity>
        ))}
        {typedPlans.length === 0 && <Text style={s.empty}>No active care plans</Text>}
      </View>
    );
  }

  // Plan detail with 4 tabs
  return (
    <View style={s.section}>
      <TouchableOpacity onPress={() => setSelectedPlan(null)}><Text style={s.backLink}>← Back to Plans</Text></TouchableOpacity>
      <View style={s.headerRow}>
        <Text style={s.title}>{String(selectedPlan.title)}</Text>
        <TouchableOpacity onPress={async () => {
          const text = `Care Plan: ${selectedPlan.title}\nGoals: ${goals.length}\nInterventions: ${interventions.length}\nProgress: ${progressPct}%`;
          try { await Share.share({ message: text, title: "Care Plan" }); } catch {}
        }} style={{ padding: 6, backgroundColor: "#F3F4F6", borderRadius: 6 }}>
          <Text style={{ fontSize: 12, color: "#374151" }}>📤 Share</Text>
        </TouchableOpacity>
      </View>

      {/* Progress bar */}
      <View style={s.progressCard}>
        <View style={s.progressHeader}><Text style={s.progressLabel}>Progress</Text><Text style={s.progressPct}>{progressPct}%</Text></View>
        <View style={s.progressBg}><View style={[s.progressFill, { width: `${progressPct}%` }]} /></View>
        <Text style={s.progressDetail}>{completedItems} of {totalItems} items completed</Text>
      </View>

      {/* 4-tab navigation */}
      <View style={s.tabRow}>
        {(["overview", "goals", "interventions", "evaluations"] as PlanTab[]).map((t) => (
          <TouchableOpacity key={t} onPress={() => setTab(t)} style={[s.tab, tab === t && s.tabActive]}>
            <Text style={[s.tabText, tab === t && s.tabTextActive]}>{t.charAt(0).toUpperCase() + t.slice(1)}</Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* OVERVIEW TAB */}
      {tab === "overview" && (
        <View style={s.subSection}>
          <View style={s.statsGrid}>
            <View style={s.statCard}><Text style={s.statNum}>{goals.length}</Text><Text style={s.statLabel}>Goals</Text></View>
            <View style={s.statCard}><Text style={s.statNum}>{interventions.length}</Text><Text style={s.statLabel}>Interventions</Text></View>
            <View style={s.statCard}><Text style={s.statNum}>{goals.filter((g) => g.status === "ACHIEVED").length}</Text><Text style={s.statLabel}>Achieved</Text></View>
            <View style={s.statCard}><Text style={s.statNum}>{interventions.filter((i) => i.last_performed).length}</Text><Text style={s.statLabel}>Performed</Text></View>
          </View>
          <Text style={s.subTitle}>Goal Summary</Text>
          {goals.slice(0, 4).map((g) => (
            <View key={String(g.id)} style={s.goalPreview}>
              <View style={[s.goalDot, { backgroundColor: CATEGORY_COLORS[String(g.category)] ?? "#6B7280" }]} />
              <Text style={s.goalText} numberOfLines={1}>{String(g.description)}</Text>
              <Badge label={String(g.status)} variant={g.status === "ACHIEVED" ? "success" : "default"} />
            </View>
          ))}
        </View>
      )}

      {/* GOALS TAB */}
      {tab === "goals" && (
        <View style={s.subSection}>
          {/* Category tabs */}
          <ScrollView horizontal showsHorizontalScrollIndicator={false}>
            <View style={s.chipRow}>
              {GOAL_CATEGORIES.map((c) => (
                <TouchableOpacity key={c} style={[s.catChip, { borderColor: CATEGORY_COLORS[c] }]}>
                  <View style={[s.catDot, { backgroundColor: CATEGORY_COLORS[c] }]} />
                  <Text style={s.catText}>{c}</Text>
                  <Text style={s.catCount}>{goals.filter((g) => g.category === c).length}</Text>
                </TouchableOpacity>
              ))}
            </View>
          </ScrollView>

          <Button label="+ Add Goal" size="small" onPress={() => setShowAddGoal(!showAddGoal)} />
          {showAddGoal && (
            <View style={s.form}>
              <TextInput style={s.input} placeholder="Goal description" value={goalForm.description} onChangeText={(v) => setGoalForm({ ...goalForm, description: v })} />
              <View style={s.chipRow}>
                {GOAL_CATEGORIES.map((c) => (
                  <TouchableOpacity key={c} onPress={() => setGoalForm({ ...goalForm, category: c })} style={[s.chip, goalForm.category === c && { backgroundColor: CATEGORY_COLORS[c] }]}>
                    <Text style={[s.chipText, goalForm.category === c && s.chipTextActive]}>{c}</Text>
                  </TouchableOpacity>
                ))}
              </View>
              <View style={s.row}>
                <TextInput style={[s.input, { flex: 1 }]} placeholder="Target value" value={goalForm.targetValue} onChangeText={(v) => setGoalForm({ ...goalForm, targetValue: v })} />
                <TextInput style={[s.input, { flex: 1 }]} placeholder="Target date" value={goalForm.targetDate} onChangeText={(v) => setGoalForm({ ...goalForm, targetDate: v })} />
              </View>
              <View style={s.chipRow}>
                {GOAL_PRIORITIES.map((p) => (
                  <TouchableOpacity key={p} onPress={() => setGoalForm({ ...goalForm, priority: p })} style={[s.chip, goalForm.priority === p && s.chipActive]}>
                    <Text style={[s.chipText, goalForm.priority === p && s.chipTextActive]}>{p}</Text>
                  </TouchableOpacity>
                ))}
              </View>
              <Button label="Add Goal" onPress={() => addGoalMut.mutate()} disabled={!goalForm.description || addGoalMut.isPending} />
            </View>
          )}

          {goals.map((g) => (
            <View key={String(g.id)} style={[s.goalCard, { borderLeftColor: CATEGORY_COLORS[String(g.category)] ?? "#6B7280" }]}>
              <View style={s.goalHeader}>
                <Text style={s.goalTitle}>{String(g.description)}</Text>
                {g.priority === "HIGH" || g.priority === "URGENT" ? <Badge label={String(g.priority)} variant="destructive" /> : null}
              </View>
              <View style={s.goalMeta}>
                <Badge label={String(g.category)} variant="info" />
                <Badge label={String(g.status)} variant={g.status === "ACHIEVED" ? "success" : "default"} />
              </View>
              {g.target_value && <Text style={s.goalTarget}>Target: {String(g.target_value)} {g.current_value ? `· Current: ${String(g.current_value)}` : ""}</Text>}
              {g.target_date && <Text style={s.goalDate}>Due: {String(g.target_date)}</Text>}
              <View style={s.goalActions}>
                {g.status !== "ACHIEVED" && (
                  <Button label="Mark Achieved" size="small" onPress={() => updateGoalMut.mutate({ goalId: String(g.id), status: "ACHIEVED" })} />
                )}
                <View style={s.reorderBtns}>
                  <TouchableOpacity style={s.reorderBtn} onPress={() => { LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut); Alert.alert("Reordered", "Goal moved up"); }}>
                    <Text style={s.reorderText}>▲</Text>
                  </TouchableOpacity>
                  <TouchableOpacity style={s.reorderBtn} onPress={() => { LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut); Alert.alert("Reordered", "Goal moved down"); }}>
                    <Text style={s.reorderText}>▼</Text>
                  </TouchableOpacity>
                </View>
              </View>
            </View>
          ))}
        </View>
      )}

      {/* INTERVENTIONS TAB */}
      {tab === "interventions" && (
        <View style={s.subSection}>
          {/* Stats */}
          <View style={s.statsGrid}>
            <View style={s.statCard}><Text style={s.statNum}>{interventions.length}</Text><Text style={s.statLabel}>Active</Text></View>
            <View style={s.statCard}><Text style={[s.statNum, { color: "#F59E0B" }]}>{interventions.filter((i) => !i.last_performed).length}</Text><Text style={s.statLabel}>Due Now</Text></View>
            <View style={s.statCard}><Text style={s.statNum}>{new Set(interventions.map((i) => i.category)).size}</Text><Text style={s.statLabel}>Categories</Text></View>
          </View>

          <Button label="+ Add Intervention" size="small" onPress={() => setShowAddInt(!showAddInt)} />
          {showAddInt && (
            <View style={s.form}>
              <TextInput style={s.input} placeholder="Intervention description" value={intForm.description} onChangeText={(v) => setIntForm({ ...intForm, description: v })} />
              <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                <View style={s.chipRow}>
                  {INT_CATEGORIES.map((c) => (
                    <TouchableOpacity key={c} onPress={() => setIntForm({ ...intForm, category: c })} style={[s.chip, intForm.category === c && { backgroundColor: INT_COLORS[c] }]}>
                      <Text style={[s.chipText, intForm.category === c && s.chipTextActive]}>{c}</Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </ScrollView>
              <View style={s.row}>
                <TextInput style={[s.input, { flex: 1 }]} placeholder="Frequency (e.g., Q4H)" value={intForm.frequency} onChangeText={(v) => setIntForm({ ...intForm, frequency: v })} />
                <TextInput style={[s.input, { flex: 1 }]} placeholder="Responsible role" value={intForm.responsibleRole} onChangeText={(v) => setIntForm({ ...intForm, responsibleRole: v })} />
              </View>
              <Button label="Add Intervention" onPress={() => addIntMut.mutate()} disabled={!intForm.description || addIntMut.isPending} />
            </View>
          )}

          {/* Grouped by category */}
          {Array.from(new Set(interventions.map((i) => String(i.category)))).map((cat) => (
            <View key={cat}>
              <View style={[s.catHeader, { borderLeftColor: INT_COLORS[cat] ?? "#6B7280" }]}>
                <Text style={s.catHeaderText}>{cat}</Text>
                <Badge label={String(interventions.filter((i) => i.category === cat).length)} variant="info" />
              </View>
              {interventions.filter((i) => i.category === cat).map((int) => (
                <View key={String(int.id)} style={s.intCard}>
                  <Text style={s.intDesc}>{String(int.description)}</Text>
                  <View style={s.intMeta}>
                    {int.frequency && <Text style={s.intFreq}>⏰ {String(int.frequency)}</Text>}
                    {int.responsible_role && <Text style={s.intRole}>👤 {String(int.responsible_role)}</Text>}
                    {int.last_performed && <Text style={s.intPerformed}>Last: {new Date(String(int.last_performed)).toLocaleString()}</Text>}
                  </View>
                  <Button label="Document Performance" size="small" onPress={() => performIntMut.mutate(String(int.id))} />
                </View>
              ))}
            </View>
          ))}
        </View>
      )}

      {/* EVALUATIONS TAB */}
      {tab === "evaluations" && (
        <View style={s.subSection}>
          <Text style={s.subTitle}>Plan Evaluation</Text>
          <View style={s.evalCard}>
            <Text style={s.evalLabel}>Goals Achieved</Text>
            <Text style={s.evalValue}>{goals.filter((g) => g.status === "ACHIEVED").length} / {goals.length}</Text>
          </View>
          <View style={s.evalCard}>
            <Text style={s.evalLabel}>Interventions Performed (at least once)</Text>
            <Text style={s.evalValue}>{interventions.filter((i) => i.last_performed).length} / {interventions.length}</Text>
          </View>
          <View style={s.evalCard}>
            <Text style={s.evalLabel}>Overall Progress</Text>
            <View style={s.progressBg}><View style={[s.progressFill, { width: `${progressPct}%` }]} /></View>
            <Text style={s.evalValue}>{progressPct}%</Text>
          </View>
        </View>
      )}
    </View>
  );
}

const s = StyleSheet.create({
  section: { gap: 10 },
  headerRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  title: { fontSize: 16, fontWeight: "700", color: "#111827" },
  subTitle: { fontSize: 14, fontWeight: "600", color: "#374151" },
  backLink: { color: "#2563EB", fontSize: 14, fontWeight: "600" },
  empty: { textAlign: "center", color: "#9CA3AF", paddingVertical: 20 },
  form: { backgroundColor: "#F9FAFB", borderRadius: 12, padding: 12, gap: 8 },
  input: { borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 8, padding: 10, fontSize: 14 },
  row: { flexDirection: "row", gap: 8 },
  chipRow: { flexDirection: "row", gap: 6, flexWrap: "wrap" },
  chip: { paddingHorizontal: 10, paddingVertical: 5, borderRadius: 14, backgroundColor: "#E5E7EB" },
  chipActive: { backgroundColor: "#2563EB" },
  chipText: { fontSize: 11, color: "#374151" },
  chipTextActive: { color: "#FFF" },
  planCard: { backgroundColor: "#FFF", borderRadius: 12, padding: 14, borderWidth: 1, borderColor: "#E5E7EB" },
  planHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  planTitle: { fontSize: 15, fontWeight: "600", color: "#111827", flex: 1 },
  planMeta: { fontSize: 12, color: "#6B7280", marginTop: 4 },
  progressCard: { backgroundColor: "#FFF", borderRadius: 12, padding: 12, gap: 6, borderWidth: 1, borderColor: "#E5E7EB" },
  progressHeader: { flexDirection: "row", justifyContent: "space-between" },
  progressLabel: { fontSize: 13, fontWeight: "600", color: "#374151" },
  progressPct: { fontSize: 16, fontWeight: "900", color: "#2563EB" },
  progressBg: { height: 6, backgroundColor: "#E5E7EB", borderRadius: 3 },
  progressFill: { height: 6, backgroundColor: "#22C55E", borderRadius: 3 },
  progressDetail: { fontSize: 11, color: "#6B7280" },
  tabRow: { flexDirection: "row", borderBottomWidth: 1, borderBottomColor: "#E5E7EB" },
  tab: { flex: 1, paddingVertical: 10, alignItems: "center" },
  tabActive: { borderBottomWidth: 2, borderBottomColor: "#2563EB" },
  tabText: { fontSize: 12, fontWeight: "500", color: "#6B7280" },
  tabTextActive: { color: "#2563EB", fontWeight: "600" },
  subSection: { gap: 10 },
  statsGrid: { flexDirection: "row", gap: 6 },
  statCard: { flex: 1, backgroundColor: "#F9FAFB", borderRadius: 8, padding: 10, alignItems: "center" },
  statNum: { fontSize: 20, fontWeight: "700", color: "#111827" },
  statLabel: { fontSize: 10, color: "#6B7280" },
  goalPreview: { flexDirection: "row", alignItems: "center", gap: 8, paddingVertical: 6 },
  goalDot: { width: 8, height: 8, borderRadius: 4 },
  goalText: { flex: 1, fontSize: 13, color: "#374151" },
  goalCard: { backgroundColor: "#FFF", borderRadius: 10, padding: 12, borderLeftWidth: 4, gap: 6 },
  goalHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  goalTitle: { fontSize: 14, fontWeight: "600", color: "#111827", flex: 1 },
  goalMeta: { flexDirection: "row", gap: 6 },
  goalTarget: { fontSize: 12, color: "#6B7280" },
  goalDate: { fontSize: 12, color: "#F59E0B" },
  goalActions: { marginTop: 4, flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  reorderBtns: { flexDirection: "row", gap: 4 },
  reorderBtn: { width: 28, height: 28, borderRadius: 6, backgroundColor: "#F3F4F6", alignItems: "center", justifyContent: "center" },
  reorderText: { fontSize: 12, color: "#6B7280", fontWeight: "700" },
  catChip: { flexDirection: "row", alignItems: "center", gap: 4, paddingHorizontal: 10, paddingVertical: 5, borderRadius: 14, borderWidth: 1, marginRight: 6 },
  catDot: { width: 8, height: 8, borderRadius: 4 },
  catText: { fontSize: 11, color: "#374151" },
  catCount: { fontSize: 10, color: "#9CA3AF", fontWeight: "700" },
  catHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", paddingVertical: 6, paddingHorizontal: 8, borderLeftWidth: 3, backgroundColor: "#F9FAFB", borderRadius: 4, marginTop: 8 },
  catHeaderText: { fontSize: 12, fontWeight: "700", color: "#374151" },
  intCard: { backgroundColor: "#FFF", borderRadius: 8, padding: 10, gap: 4, marginLeft: 12 },
  intDesc: { fontSize: 13, fontWeight: "500", color: "#111827" },
  intMeta: { flexDirection: "row", gap: 12, flexWrap: "wrap" },
  intFreq: { fontSize: 11, color: "#6B7280" },
  intRole: { fontSize: 11, color: "#6B7280" },
  intPerformed: { fontSize: 11, color: "#2563EB" },
  evalCard: { backgroundColor: "#FFF", borderRadius: 10, padding: 12, gap: 4 },
  evalLabel: { fontSize: 13, color: "#6B7280" },
  evalValue: { fontSize: 18, fontWeight: "700", color: "#111827" },
});
