/**
 * WellnessSection — Activity tracking, vitals, mood, and challenges.
 */
import React, { useState } from "react";
import { View, Text, TouchableOpacity, TextInput, StyleSheet, ScrollView } from "react-native";
import { Card, Button, Badge, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchActivities, logActivity, fetchVitals, logVital, fetchMoodLog, logMood, fetchChallenges, joinChallenge } from "../../services/wellnessService";
import { useAppStore } from "../../stores/appStore";

type WellnessTab = "activity" | "vitals" | "mood" | "challenges";

const MOOD_EMOJIS = ["😢", "😟", "😐", "🙂", "😁"];
const VITAL_TYPES = ["HEART_RATE", "BLOOD_PRESSURE", "TEMPERATURE", "WEIGHT", "BLOOD_GLUCOSE", "OXYGEN_SAT"];

export function WellnessSection() {
  const [tab, setTab] = useState<WellnessTab>("activity");
  const profile = useAppStore((s) => s.profile);
  const patientId = profile?.cpid ?? "";

  return (
    <View style={styles.container}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.tabBar}>
        {(["activity", "vitals", "mood", "challenges"] as WellnessTab[]).map((t) => (
          <TouchableOpacity key={t} onPress={() => setTab(t)} style={[styles.tab, tab === t && styles.activeTab]}>
            <Text style={[styles.tabText, tab === t && styles.activeTabText]}>{t.charAt(0).toUpperCase() + t.slice(1)}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
      {tab === "activity" && <ActivityTab patientId={patientId} />}
      {tab === "vitals" && <VitalsTab patientId={patientId} />}
      {tab === "mood" && <MoodTab patientId={patientId} />}
      {tab === "challenges" && <ChallengesTab patientId={patientId} />}
    </View>
  );
}

function ActivityTab({ patientId }: { patientId: string }) {
  const queryClient = useQueryClient();
  const { data: activities = [], isLoading } = useQuery({
    queryKey: ["wellness-activities", patientId],
    queryFn: () => fetchActivities(patientId),
    enabled: !!patientId,
  });
  const [form, setForm] = useState({ steps: "", waterMl: "", sleepHours: "", activeMinutes: "" });

  const mutation = useMutation({
    mutationFn: () => logActivity({ patientId, steps: Number(form.steps) || 0, waterMl: Number(form.waterMl) || 0, sleepHours: Number(form.sleepHours) || undefined, activeMinutes: Number(form.activeMinutes) || 0 }),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["wellness-activities"] }); setForm({ steps: "", waterMl: "", sleepHours: "", activeMinutes: "" }); },
  });

  if (isLoading) return <LoadingSpinner />;

  const today = activities[0];

  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Today's Activity</Text>
      <View style={styles.statsGrid}>
        <StatCard label="Steps" value={today?.steps ?? 0} target={10000} unit="" />
        <StatCard label="Active Min" value={today?.activeMinutes ?? 0} target={30} unit="min" />
        <StatCard label="Water" value={today?.waterMl ?? 0} target={2000} unit="ml" />
        <StatCard label="Sleep" value={today?.sleepHours ?? 0} target={8} unit="hrs" />
      </View>
      <Text style={styles.sectionTitle}>Log Activity</Text>
      <View style={styles.inputRow}>
        <TextInput style={styles.smallInput} placeholder="Steps" value={form.steps} onChangeText={(v) => setForm({ ...form, steps: v })} keyboardType="numeric" />
        <TextInput style={styles.smallInput} placeholder="Water (ml)" value={form.waterMl} onChangeText={(v) => setForm({ ...form, waterMl: v })} keyboardType="numeric" />
      </View>
      <View style={styles.inputRow}>
        <TextInput style={styles.smallInput} placeholder="Sleep (hrs)" value={form.sleepHours} onChangeText={(v) => setForm({ ...form, sleepHours: v })} keyboardType="numeric" />
        <TextInput style={styles.smallInput} placeholder="Active min" value={form.activeMinutes} onChangeText={(v) => setForm({ ...form, activeMinutes: v })} keyboardType="numeric" />
      </View>
      <Button title="Save" onPress={() => mutation.mutate()} disabled={mutation.isPending} />
    </View>
  );
}

function VitalsTab({ patientId }: { patientId: string }) {
  const queryClient = useQueryClient();
  const { data: vitals = [], isLoading } = useQuery({
    queryKey: ["wellness-vitals", patientId],
    queryFn: () => fetchVitals(patientId),
    enabled: !!patientId,
  });
  const [form, setForm] = useState({ vitalType: "HEART_RATE", value: "", unit: "bpm" });

  const mutation = useMutation({
    mutationFn: () => logVital({ patientId, vitalType: form.vitalType, value: Number(form.value), unit: form.unit }),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["wellness-vitals"] }); setForm({ ...form, value: "" }); },
  });

  if (isLoading) return <LoadingSpinner />;

  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Log Vital Sign</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: 8 }}>
        {VITAL_TYPES.map((vt) => (
          <TouchableOpacity key={vt} onPress={() => setForm({ ...form, vitalType: vt, unit: getDefaultUnit(vt) })}
            style={[styles.chip, form.vitalType === vt && styles.activeChip]}>
            <Text style={[styles.chipText, form.vitalType === vt && styles.activeChipText]}>{vt.replace(/_/g, " ")}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
      <View style={styles.inputRow}>
        <TextInput style={[styles.smallInput, { flex: 2 }]} placeholder="Value" value={form.value} onChangeText={(v) => setForm({ ...form, value: v })} keyboardType="numeric" />
        <Text style={styles.unitLabel}>{form.unit}</Text>
      </View>
      <Button title="Log Vital" onPress={() => mutation.mutate()} disabled={!form.value || mutation.isPending} />
      <Text style={[styles.sectionTitle, { marginTop: 16 }]}>Recent Readings</Text>
      {vitals.slice(0, 10).map((v) => (
        <View key={v.id} style={styles.listItem}>
          <Text style={styles.listTitle}>{v.vitalType.replace(/_/g, " ")}</Text>
          <Text style={styles.listValue}>{v.value} {v.unit}</Text>
          <Text style={styles.listDate}>{new Date(v.measuredAt).toLocaleString()}</Text>
        </View>
      ))}
    </View>
  );
}

function MoodTab({ patientId }: { patientId: string }) {
  const queryClient = useQueryClient();
  const { data: moods = [], isLoading } = useQuery({
    queryKey: ["wellness-mood", patientId],
    queryFn: () => fetchMoodLog(patientId),
    enabled: !!patientId,
  });
  const [score, setScore] = useState(3);
  const [notes, setNotes] = useState("");

  const mutation = useMutation({
    mutationFn: () => logMood({ patientId, moodScore: score, notes }),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["wellness-mood"] }); setNotes(""); },
  });

  if (isLoading) return <LoadingSpinner />;

  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>How are you feeling?</Text>
      <View style={styles.moodRow}>
        {MOOD_EMOJIS.map((emoji, i) => (
          <TouchableOpacity key={i} onPress={() => setScore(i + 1)} style={[styles.moodButton, score === i + 1 && styles.activeMood]}>
            <Text style={styles.moodEmoji}>{emoji}</Text>
          </TouchableOpacity>
        ))}
      </View>
      <TextInput style={styles.input} placeholder="Notes (optional)" value={notes} onChangeText={setNotes} multiline />
      <Button title="Log Mood" onPress={() => mutation.mutate()} disabled={mutation.isPending} />
      <Text style={[styles.sectionTitle, { marginTop: 16 }]}>Recent Entries</Text>
      {moods.slice(0, 7).map((m) => (
        <View key={m.id} style={styles.listItem}>
          <Text style={styles.moodEmoji}>{MOOD_EMOJIS[(m.moodScore ?? 3) - 1]}</Text>
          <View style={{ flex: 1 }}>
            {m.notes ? <Text style={styles.listTitle}>{m.notes}</Text> : null}
            <Text style={styles.listDate}>{new Date(m.loggedAt).toLocaleDateString()}</Text>
          </View>
        </View>
      ))}
    </View>
  );
}

function ChallengesTab({ patientId }: { patientId: string }) {
  const queryClient = useQueryClient();
  const { data: challenges = [], isLoading } = useQuery({
    queryKey: ["wellness-challenges"],
    queryFn: fetchChallenges,
  });

  const joinMutation = useMutation({
    mutationFn: (id: string) => joinChallenge(id, patientId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["wellness-challenges"] }),
  });

  if (isLoading) return <LoadingSpinner />;

  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>Active Challenges</Text>
      {challenges.map((c) => (
        <View key={c.id} style={styles.challengeCard}>
          <View style={styles.challengeHeader}>
            <Text style={styles.challengeTitle}>{c.title}</Text>
            <Badge label={c.challengeType} variant="info" />
          </View>
          {c.description ? <Text style={styles.challengeDesc}>{c.description}</Text> : null}
          <Text style={styles.challengeMeta}>Goal: {c.targetValue.toLocaleString()} {c.targetUnit} · {c.participantCount} joined</Text>
          <Text style={styles.challengeMeta}>{new Date(c.startDate).toLocaleDateString()} — {new Date(c.endDate).toLocaleDateString()}</Text>
          <Button title="Join Challenge" onPress={() => joinMutation.mutate(c.id)} size="sm" />
        </View>
      ))}
    </View>
  );
}

function StatCard({ label, value, target, unit }: { label: string; value: number; target: number; unit: string }) {
  const pct = Math.min((value / target) * 100, 100);
  return (
    <View style={styles.statCard}>
      <Text style={styles.statLabel}>{label}</Text>
      <Text style={styles.statValue}>{value.toLocaleString()}{unit ? ` ${unit}` : ""}</Text>
      <View style={styles.progressBar}><View style={[styles.progressFill, { width: `${pct}%` }]} /></View>
      <Text style={styles.statTarget}>{Math.round(pct)}% of {target.toLocaleString()}</Text>
    </View>
  );
}

function getDefaultUnit(type: string): string {
  const units: Record<string, string> = { HEART_RATE: "bpm", BLOOD_PRESSURE: "mmHg", TEMPERATURE: "°C", WEIGHT: "kg", BLOOD_GLUCOSE: "mmol/L", OXYGEN_SAT: "%" };
  return units[type] ?? "";
}

const styles = StyleSheet.create({
  container: { gap: 12 },
  tabBar: { marginBottom: 12 },
  tab: { paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20, backgroundColor: colors.gray[100], marginRight: 8 },
  activeTab: { backgroundColor: "#2563EB" },
  tabText: { fontSize: 13, color: colors.gray[500], fontWeight: "500" },
  activeTabText: { color: "#FFFFFF" },
  section: { gap: 12 },
  sectionTitle: { fontSize: 16, fontWeight: "700", color: colors.gray[900] },
  statsGrid: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  statCard: { flex: 1, minWidth: "45%", backgroundColor: colors.gray[50], borderRadius: 12, padding: 12, gap: 4 },
  statLabel: { fontSize: 11, color: colors.gray[500], fontWeight: "600" },
  statValue: { fontSize: 20, fontWeight: "700", color: colors.gray[900] },
  progressBar: { height: 4, backgroundColor: colors.gray[200], borderRadius: 2 },
  progressFill: { height: 4, backgroundColor: "#2563EB", borderRadius: 2 },
  statTarget: { fontSize: 10, color: colors.gray[400] },
  inputRow: { flexDirection: "row", gap: 8, alignItems: "center" },
  smallInput: { flex: 1, borderWidth: 1, borderColor: colors.gray[300], borderRadius: 8, padding: 10, fontSize: 14 },
  input: { borderWidth: 1, borderColor: colors.gray[300], borderRadius: 8, padding: 12, fontSize: 14, minHeight: 60 },
  unitLabel: { fontSize: 14, color: colors.gray[500], minWidth: 40 },
  chip: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 16, backgroundColor: colors.gray[100], marginRight: 6 },
  activeChip: { backgroundColor: "#2563EB" },
  chipText: { fontSize: 11, color: colors.gray[500] },
  activeChipText: { color: "#FFFFFF" },
  listItem: { flexDirection: "row", alignItems: "center", gap: 12, paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: colors.gray[100] },
  listTitle: { fontSize: 13, fontWeight: "500", color: colors.gray[700] },
  listValue: { fontSize: 14, fontWeight: "700", color: colors.gray[900] },
  listDate: { fontSize: 11, color: colors.gray[400] },
  moodRow: { flexDirection: "row", justifyContent: "space-around", paddingVertical: 8 },
  moodButton: { padding: 12, borderRadius: 16, backgroundColor: colors.gray[100] },
  activeMood: { backgroundColor: "#DBEAFE", borderWidth: 2, borderColor: "#2563EB" },
  moodEmoji: { fontSize: 28 },
  challengeCard: { backgroundColor: colors.gray[50], borderRadius: 12, padding: 16, gap: 8 },
  challengeHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  challengeTitle: { fontSize: 15, fontWeight: "600", color: colors.gray[900], flex: 1 },
  challengeDesc: { fontSize: 13, color: colors.gray[500] },
  challengeMeta: { fontSize: 12, color: colors.gray[400] },
});
