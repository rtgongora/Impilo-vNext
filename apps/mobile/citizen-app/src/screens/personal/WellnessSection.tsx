import React, { useState } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, TextInput } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Button, LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  fetchActivities, logActivity,
  fetchVitals, logVital,
  fetchMoodLog, logMood,
} from "../../services/wellnessService";
import { useAppStore } from "../../stores/appStore";
import {
  USE_SEED_DATA,
  SEED_WELLNESS_ACTIVITIES, SEED_WELLNESS_VITALS, SEED_MOOD_ENTRIES,
} from "../../data/seedHealthRecords";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_RED, APP_RED_LIGHT, APP_GOLD, APP_GOLD_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

type Tab = "activity" | "vitals" | "mood";

const VITAL_TYPE_CFG: Record<string, { label: string; icon: React.ComponentProps<typeof Ionicons>["name"]; color: string; bg: string }> = {
  BLOOD_PRESSURE: { label: "Blood Pressure", icon: "heart",             color: APP_RED,        bg: APP_RED_LIGHT    },
  BLOOD_GLUCOSE:  { label: "Blood Glucose",  icon: "flask",             color: APP_GOLD,       bg: APP_GOLD_LIGHT   },
  HEART_RATE:     { label: "Heart Rate",     icon: "pulse",             color: APP_RED,        bg: APP_RED_LIGHT    },
  WEIGHT:         { label: "Weight",         icon: "body-outline",      color: APP_GREEN,      bg: APP_GREEN_LIGHT  },
  OXYGEN_SAT:     { label: "SpO₂",          icon: "water-outline",     color: APP_GREEN_DARK, bg: APP_GREEN_XLIGHT },
  TEMPERATURE:    { label: "Temperature",    icon: "thermometer-outline",color: APP_GOLD,      bg: APP_GOLD_LIGHT   },
};

const MOOD_EMOJIS = ["😢", "😟", "😐", "🙂", "😁"];
const VITAL_TYPES = ["BLOOD_PRESSURE", "BLOOD_GLUCOSE", "HEART_RATE", "WEIGHT", "OXYGEN_SAT", "TEMPERATURE"];

function StatRing({ value, target, label, color, unit }: { value: number; target: number; label: string; color: string; unit: string }) {
  const pct = Math.min(100, (value / target) * 100);
  return (
    <View style={ring.wrap}>
      <View style={[ring.bar, { backgroundColor: color + "22" }]}>
        <View style={[ring.fill, { width: `${pct}%` as never, backgroundColor: color }]} />
      </View>
      <View style={ring.text}>
        <Text style={[ring.value, { color }]}>{value.toLocaleString()}</Text>
        <Text style={ring.label}>{label}</Text>
        <Text style={ring.unit}>{unit}</Text>
      </View>
    </View>
  );
}
const ring = StyleSheet.create({
  wrap: { flex: 1, alignItems: "center", gap: 6 },
  bar: { width: "80%", height: 5, borderRadius: 3, overflow: "hidden" },
  fill: { height: "100%", borderRadius: 3 },
  text: { alignItems: "center" },
  value: { fontSize: 18, fontWeight: "800" },
  label: { fontSize: 10, fontWeight: "600", color: APP_TEXT_2, textAlign: "center" },
  unit:  { fontSize: 9, color: APP_TEXT_3 },
});

// ── Activity Tab ───────────────────────────────────────────────────────────────
function ActivityTab({ patientId }: { patientId: string }) {
  const qc = useQueryClient();
  const { data: activities = [], isLoading } = useQuery({
    queryKey: ["wellness-activities", patientId, USE_SEED_DATA],
    queryFn: async () => USE_SEED_DATA
      ? (await new Promise<void>((r) => setTimeout(r, 300)), SEED_WELLNESS_ACTIVITIES)
      : fetchActivities(patientId),
    enabled: USE_SEED_DATA || !!patientId,
  });
  const [f, setF] = useState({ steps: "", waterMl: "", sleepHours: "", activeMinutes: "" });
  const [showLog, setShowLog] = useState(false);

  const mutation = useMutation({
    mutationFn: () => logActivity({ patientId, steps: +f.steps || 0, waterMl: +f.waterMl || 0, sleepHours: +f.sleepHours || undefined, activeMinutes: +f.activeMinutes || 0 }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["wellness-activities"] }); setF({ steps: "", waterMl: "", sleepHours: "", activeMinutes: "" }); setShowLog(false); },
  });

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  const today = activities[0];
  const week  = activities.slice(0, 7);
  const avgSteps = Math.round(week.reduce((a, x) => a + x.steps, 0) / week.length);

  return (
    <ScrollView style={s.tabContent} contentContainerStyle={s.tabPadding}>
      {/* Today's stats */}
      <View style={s.statsCard}>
        <Text style={s.statsTitle}>Today's Activity</Text>
        <View style={s.statsRow}>
          <StatRing value={today?.steps ?? 0}        target={10000} label="Steps"   color={APP_GREEN}      unit="goal 10k"  />
          <StatRing value={today?.activeMinutes ?? 0} target={30}    label="Active"  color={APP_GOLD}       unit="goal 30min"/>
          <StatRing value={today?.waterMl ?? 0}       target={2000}  label="Water"   color={APP_GREEN_DARK} unit="goal 2L"   />
          <StatRing value={today?.sleepHours ?? 0}    target={8}     label="Sleep"   color="#7C3AED"        unit="goal 8h"   />
        </View>
      </View>

      {/* 7-day summary */}
      <View style={s.weekCard}>
        <Text style={s.statsTitle}>7-Day Average</Text>
        <View style={s.weekStats}>
          <View style={s.weekStat}>
            <Text style={[s.weekNum, { color: APP_GREEN }]}>{avgSteps.toLocaleString()}</Text>
            <Text style={s.weekLbl}>avg steps</Text>
          </View>
          <View style={s.weekStat}>
            <Text style={[s.weekNum, { color: APP_GOLD }]}>
              {(week.reduce((a, x) => a + x.activeMinutes, 0) / week.length).toFixed(0)}
            </Text>
            <Text style={s.weekLbl}>avg min active</Text>
          </View>
          <View style={s.weekStat}>
            <Text style={[s.weekNum, { color: APP_GREEN_DARK }]}>
              {((week.reduce((a, x) => a + x.waterMl, 0) / week.length) / 1000).toFixed(1)}L
            </Text>
            <Text style={s.weekLbl}>avg water</Text>
          </View>
        </View>
        {/* Mini step chart */}
        <View style={s.chart}>
          {week.slice().reverse().map((a, i) => {
            const h = Math.max(6, (a.steps / 12000) * 60);
            const isToday = i === week.length - 1;
            return (
              <View key={a.id} style={s.chartBar}>
                <View style={[s.chartFill, { height: h, backgroundColor: isToday ? APP_GREEN : APP_GREEN_LIGHT }]} />
                <Text style={s.chartDay}>{new Date(a.activityDate).toLocaleDateString([], { weekday: "narrow" })}</Text>
              </View>
            );
          })}
        </View>
      </View>

      {/* Log button */}
      <Pressable onPress={() => setShowLog(!showLog)} style={[s.logBtn, showLog && s.logBtnActive]}>
        <Ionicons name={showLog ? "close" : "add-circle-outline"} size={16} color={showLog ? APP_TEXT_2 : "#FFFFFF"} />
        <Text style={[s.logBtnText, showLog && { color: APP_TEXT_2 }]}>
          {showLog ? "Cancel" : "Log Today's Activity"}
        </Text>
      </Pressable>

      {showLog ? (
        <View style={s.formCard}>
          <View style={s.inputRow}>
            <View style={s.inputWrap}>
              <Text style={s.inputLabel}>Steps</Text>
              <TextInput style={s.input} placeholder="0" value={f.steps} onChangeText={(v) => setF({ ...f, steps: v })} keyboardType="numeric" placeholderTextColor={APP_TEXT_3} />
            </View>
            <View style={s.inputWrap}>
              <Text style={s.inputLabel}>Water (ml)</Text>
              <TextInput style={s.input} placeholder="0" value={f.waterMl} onChangeText={(v) => setF({ ...f, waterMl: v })} keyboardType="numeric" placeholderTextColor={APP_TEXT_3} />
            </View>
          </View>
          <View style={s.inputRow}>
            <View style={s.inputWrap}>
              <Text style={s.inputLabel}>Sleep (hrs)</Text>
              <TextInput style={s.input} placeholder="0" value={f.sleepHours} onChangeText={(v) => setF({ ...f, sleepHours: v })} keyboardType="numeric" placeholderTextColor={APP_TEXT_3} />
            </View>
            <View style={s.inputWrap}>
              <Text style={s.inputLabel}>Active (min)</Text>
              <TextInput style={s.input} placeholder="0" value={f.activeMinutes} onChangeText={(v) => setF({ ...f, activeMinutes: v })} keyboardType="numeric" placeholderTextColor={APP_TEXT_3} />
            </View>
          </View>
          <Button title={mutation.isPending ? "Saving…" : "Save"} onPress={() => mutation.mutate()} disabled={mutation.isPending || USE_SEED_DATA} />
        </View>
      ) : null}
    </ScrollView>
  );
}

// ── Vitals Tab ─────────────────────────────────────────────────────────────────
function VitalsTab({ patientId }: { patientId: string }) {
  const qc = useQueryClient();
  const { data: vitals = [], isLoading } = useQuery({
    queryKey: ["wellness-vitals", patientId, USE_SEED_DATA],
    queryFn: async () => USE_SEED_DATA
      ? (await new Promise<void>((r) => setTimeout(r, 300)), SEED_WELLNESS_VITALS)
      : fetchVitals(patientId),
    enabled: USE_SEED_DATA || !!patientId,
  });
  const [showLog, setShowLog] = useState(false);
  const [vType, setVType] = useState("BLOOD_PRESSURE");
  const [value, setValue] = useState("");
  const [unit, setUnit] = useState("");

  const mutation = useMutation({
    mutationFn: () => logVital({ patientId, vitalType: vType, value: +value, unit, source: "Manual" }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["wellness-vitals"] }); setValue(""); setShowLog(false); },
  });

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  const latest: Record<string, typeof vitals[0]> = {};
  for (const v of vitals) {
    if (!latest[v.vitalType]) latest[v.vitalType] = v;
  }

  return (
    <ScrollView style={s.tabContent} contentContainerStyle={s.tabPadding}>
      <View style={s.vitalsGrid}>
        {Object.entries(latest).map(([type, v]) => {
          const cfg = VITAL_TYPE_CFG[type] ?? { label: type, icon: "pulse" as const, color: APP_GREEN, bg: APP_GREEN_XLIGHT };
          return (
            <View key={type} style={[s.vitalCard, { borderTopColor: cfg.color }]}>
              <View style={[s.vitalIcon, { backgroundColor: cfg.bg }]}>
                <Ionicons name={cfg.icon} size={18} color={cfg.color} />
              </View>
              <Text style={[s.vitalValue, { color: cfg.color }]}>{v.value}</Text>
              <Text style={s.vitalUnit}>{v.unit}</Text>
              <Text style={s.vitalType}>{cfg.label}</Text>
              <Text style={s.vitalDate}>{new Date(v.measuredAt).toLocaleDateString([], { month: "short", day: "numeric" })}</Text>
              {v.notes ? <Text style={s.vitalNotes} numberOfLines={1}>{v.notes}</Text> : null}
            </View>
          );
        })}
      </View>

      <Pressable onPress={() => setShowLog(!showLog)} style={[s.logBtn, showLog && s.logBtnActive]}>
        <Ionicons name={showLog ? "close" : "add-circle-outline"} size={16} color={showLog ? APP_TEXT_2 : "#FFFFFF"} />
        <Text style={[s.logBtnText, showLog && { color: APP_TEXT_2 }]}>{showLog ? "Cancel" : "Log a Vital"}</Text>
      </Pressable>

      {showLog ? (
        <View style={s.formCard}>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={s.typeRow}>
            {VITAL_TYPES.map((t) => (
              <Pressable key={t} onPress={() => setVType(t)} style={[s.typeChip, vType === t && s.typeChipActive]}>
                <Text style={[s.typeChipText, vType === t && s.typeChipTextActive]}>{(VITAL_TYPE_CFG[t]?.label ?? t).replace(/_/g, " ")}</Text>
              </Pressable>
            ))}
          </ScrollView>
          <View style={s.inputRow}>
            <View style={s.inputWrap}>
              <Text style={s.inputLabel}>Value</Text>
              <TextInput style={s.input} placeholder="e.g. 128" value={value} onChangeText={setValue} keyboardType="numeric" placeholderTextColor={APP_TEXT_3} />
            </View>
            <View style={s.inputWrap}>
              <Text style={s.inputLabel}>Unit</Text>
              <TextInput style={s.input} placeholder="e.g. mmHg" value={unit} onChangeText={setUnit} placeholderTextColor={APP_TEXT_3} />
            </View>
          </View>
          <Button title={mutation.isPending ? "Saving…" : "Save Vital"} onPress={() => mutation.mutate()} disabled={!value || mutation.isPending || USE_SEED_DATA} />
        </View>
      ) : null}
    </ScrollView>
  );
}

// ── Mood Tab ───────────────────────────────────────────────────────────────────
function MoodTab({ patientId }: { patientId: string }) {
  const qc = useQueryClient();
  const { data: entries = [], isLoading } = useQuery({
    queryKey: ["wellness-mood", patientId, USE_SEED_DATA],
    queryFn: async () => USE_SEED_DATA
      ? (await new Promise<void>((r) => setTimeout(r, 300)), SEED_MOOD_ENTRIES)
      : fetchMoodLog(patientId),
    enabled: USE_SEED_DATA || !!patientId,
  });
  const [score, setScore] = useState(3);
  const [notes, setNotes] = useState("");

  const mutation = useMutation({
    mutationFn: () => logMood({ patientId, moodScore: score, notes }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["wellness-mood"] }); setNotes(""); },
  });

  if (isLoading) return <View style={s.centred}><LoadingSpinner size="md" /></View>;

  const MOOD_COLORS = [APP_RED, "#E57C23", APP_GOLD, APP_GREEN_DARK, APP_GREEN];

  return (
    <ScrollView style={s.tabContent} contentContainerStyle={s.tabPadding}>
      {/* Log today's mood */}
      <View style={s.moodLogCard}>
        <Text style={s.statsTitle}>How are you feeling today?</Text>
        <View style={s.moodRow}>
          {MOOD_EMOJIS.map((e, i) => (
            <Pressable key={i} onPress={() => setScore(i + 1)} style={[s.moodBtn, score === i + 1 && { borderColor: MOOD_COLORS[i], borderWidth: 2 }]}>
              <Text style={s.moodEmoji}>{e}</Text>
            </Pressable>
          ))}
        </View>
        <TextInput
          style={s.moodInput} placeholder="Optional note…" value={notes}
          onChangeText={setNotes} multiline placeholderTextColor={APP_TEXT_3}
        />
        <Button title={mutation.isPending ? "Saving…" : "Log Mood"} onPress={() => mutation.mutate()} disabled={mutation.isPending || USE_SEED_DATA} />
      </View>

      {/* History */}
      <Text style={s.sectionLabel}>Recent Entries</Text>
      {entries.map((e) => (
        <View key={e.id} style={s.moodHistoryCard}>
          <Text style={s.moodHistoryEmoji}>{MOOD_EMOJIS[e.moodScore - 1]}</Text>
          <View style={s.moodHistoryInfo}>
            <Text style={s.moodHistoryDate}>{new Date(e.loggedAt).toLocaleDateString([], { weekday: "short", month: "short", day: "numeric" })}</Text>
            {e.notes ? <Text style={s.moodHistoryNote}>{e.notes}</Text> : null}
            <View style={s.moodMetaRow}>
              {e.energyLevel ? <Text style={s.moodMeta}>⚡ Energy {e.energyLevel}/5</Text> : null}
              {e.stressLevel ? <Text style={s.moodMeta}>😤 Stress {e.stressLevel}/5</Text> : null}
            </View>
          </View>
          <View style={[s.moodScoreBadge, { backgroundColor: MOOD_COLORS[(e.moodScore - 1)] + "22" }]}>
            <Text style={[s.moodScoreNum, { color: MOOD_COLORS[e.moodScore - 1] }]}>{e.moodScore}/5</Text>
          </View>
        </View>
      ))}
    </ScrollView>
  );
}

// ── Main ──────────────────────────────────────────────────────────────────────
export function WellnessSection() {
  const [tab, setTab] = useState<Tab>("activity");
  const profile   = useAppStore((s) => s.profile);
  const patientId = profile?.cpid ?? "";

  const TABS: { id: Tab; label: string; icon: React.ComponentProps<typeof Ionicons>["name"] }[] = [
    { id: "activity", label: "Activity", icon: "walk-outline"         },
    { id: "vitals",   label: "Vitals",   icon: "pulse-outline"         },
    { id: "mood",     label: "Mood",     icon: "happy-outline"         },
  ];

  return (
    <View style={s.root}>
      {/* Tab bar */}
      <View style={s.tabBar}>
        {TABS.map((t) => (
          <Pressable key={t.id} onPress={() => setTab(t.id)} style={[s.tab, tab === t.id && s.tabActive]}>
            <Ionicons name={t.icon} size={15} color={tab === t.id ? APP_GREEN : APP_TEXT_2} />
            <Text style={[s.tabText, tab === t.id && s.tabTextActive]}>{t.label}</Text>
          </Pressable>
        ))}
      </View>
      {tab === "activity" && <ActivityTab patientId={patientId} />}
      {tab === "vitals"   && <VitalsTab   patientId={patientId} />}
      {tab === "mood"     && <MoodTab     patientId={patientId} />}
    </View>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  centred: { flex: 1, alignItems: "center", justifyContent: "center", padding: 40 },

  tabBar: {
    flexDirection: "row", backgroundColor: APP_SURFACE,
    borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: APP_BORDER,
  },
  tab: { flex: 1, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 5, paddingVertical: 11 },
  tabActive: { borderBottomWidth: 2, borderBottomColor: APP_GREEN },
  tabText: { fontSize: 13, fontWeight: "500", color: APP_TEXT_2 },
  tabTextActive: { color: APP_GREEN, fontWeight: "700" },

  tabContent: { flex: 1 },
  tabPadding: { padding: 16, gap: 14, paddingBottom: 40 },

  statsCard: { backgroundColor: APP_SURFACE, borderRadius: 18, padding: 16, shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3 },
  statsTitle: { fontSize: 14, fontWeight: "700", color: APP_TEXT, marginBottom: 14 },
  statsRow: { flexDirection: "row", gap: 8 },

  weekCard: { backgroundColor: APP_SURFACE, borderRadius: 18, padding: 16, shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3 },
  weekStats: { flexDirection: "row", gap: 8, marginBottom: 14 },
  weekStat: { flex: 1, alignItems: "center" },
  weekNum: { fontSize: 20, fontWeight: "800" },
  weekLbl: { fontSize: 10, color: APP_TEXT_2, textAlign: "center" },
  chart: { flexDirection: "row", alignItems: "flex-end", gap: 6, height: 70 },
  chartBar: { flex: 1, alignItems: "center", gap: 4 },
  chartFill: { width: "100%", borderRadius: 4 },
  chartDay: { fontSize: 9, color: APP_TEXT_3 },

  vitalsGrid: { flexDirection: "row", flexWrap: "wrap", gap: 10 },
  vitalCard: {
    width: "47%", backgroundColor: APP_SURFACE, borderRadius: 16, padding: 14,
    borderTopWidth: 3, gap: 3,
    shadowColor: "#000", shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.05, shadowRadius: 4, elevation: 2,
  },
  vitalIcon: { width: 36, height: 36, borderRadius: 10, alignItems: "center", justifyContent: "center", marginBottom: 6 },
  vitalValue: { fontSize: 22, fontWeight: "800" },
  vitalUnit: { fontSize: 11, color: APP_TEXT_2 },
  vitalType: { fontSize: 12, fontWeight: "600", color: APP_TEXT, marginTop: 2 },
  vitalDate: { fontSize: 10, color: APP_TEXT_3 },
  vitalNotes: { fontSize: 10, color: APP_TEXT_3, marginTop: 2 },

  moodLogCard: { backgroundColor: APP_SURFACE, borderRadius: 18, padding: 16, gap: 14, shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3 },
  moodRow: { flexDirection: "row", justifyContent: "space-around" },
  moodBtn: { width: 48, height: 48, borderRadius: 24, borderWidth: 2, borderColor: "transparent", alignItems: "center", justifyContent: "center" },
  moodEmoji: { fontSize: 28 },
  moodInput: { borderWidth: 1, borderColor: APP_BORDER, borderRadius: 12, padding: 12, fontSize: 14, color: APP_TEXT, minHeight: 60 },

  sectionLabel: { fontSize: 12, fontWeight: "700", color: APP_TEXT_2, textTransform: "uppercase", letterSpacing: 0.8, paddingHorizontal: 4 },
  moodHistoryCard: {
    flexDirection: "row", alignItems: "center", gap: 12,
    backgroundColor: APP_SURFACE, borderRadius: 16, padding: 14,
    shadowColor: "#000", shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.04, shadowRadius: 4, elevation: 2,
  },
  moodHistoryEmoji: { fontSize: 28 },
  moodHistoryInfo: { flex: 1 },
  moodHistoryDate: { fontSize: 13, fontWeight: "600", color: APP_TEXT },
  moodHistoryNote: { fontSize: 12, color: APP_TEXT_2, marginTop: 2 },
  moodMetaRow: { flexDirection: "row", gap: 10, marginTop: 4 },
  moodMeta: { fontSize: 11, color: APP_TEXT_3 },
  moodScoreBadge: { paddingHorizontal: 8, paddingVertical: 4, borderRadius: 10 },
  moodScoreNum: { fontSize: 13, fontWeight: "800" },

  logBtn: {
    flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8,
    backgroundColor: APP_GREEN, paddingVertical: 13, borderRadius: 16,
  },
  logBtnActive: { backgroundColor: "#EEF2F2" },
  logBtnText: { fontSize: 14, fontWeight: "700", color: "#FFFFFF" },

  formCard: { backgroundColor: APP_SURFACE, borderRadius: 18, padding: 16, gap: 12, shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3 },
  inputRow: { flexDirection: "row", gap: 10 },
  inputWrap: { flex: 1, gap: 5 },
  inputLabel: { fontSize: 12, fontWeight: "600", color: APP_TEXT_2 },
  input: { borderWidth: 1, borderColor: APP_BORDER, borderRadius: 12, paddingHorizontal: 12, paddingVertical: 10, fontSize: 14, color: APP_TEXT, backgroundColor: APP_BG },
  typeRow: { gap: 8, paddingBottom: 4 },
  typeChip: { paddingHorizontal: 12, paddingVertical: 7, borderRadius: 20, backgroundColor: "#EEF2F2" },
  typeChipActive: { backgroundColor: APP_GREEN_XLIGHT, borderWidth: 1, borderColor: APP_GREEN_LIGHT },
  typeChipText: { fontSize: 12, color: APP_TEXT_2, fontWeight: "500" },
  typeChipTextActive: { color: APP_GREEN, fontWeight: "700" },
});
