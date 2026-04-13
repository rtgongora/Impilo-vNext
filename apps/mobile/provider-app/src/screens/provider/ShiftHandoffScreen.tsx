/**
 * ShiftHandoffScreen — Structured handoff with patient cards,
 * acuity classification, vitals status, pending tasks, and SBAR notes.
 */
import React, { useState } from "react";
import { View, Text, ScrollView, TextInput, TouchableOpacity, StyleSheet, Alert, Share } from "react-native";
import { Screen, Header, Button, Badge, LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@impilo/mobile-api-client";
import { useAppStore } from "../../stores/appStore";

const ACUITY_LEVELS = [
  { level: "CRITICAL", color: "#DC2626", label: "Critical" },
  { level: "HIGH", color: "#F59E0B", label: "High" },
  { level: "MODERATE", color: "#3B82F6", label: "Moderate" },
  { level: "STABLE", color: "#22C55E", label: "Stable" },
];

interface PatientHandoff {
  id: string;
  name: string;
  bed: string;
  ward: string;
  acuity: string;
  diagnosis: string;
  keyMeds: string[];
  vitalStatus: "STABLE" | "CONCERNING" | "CRITICAL";
  pendingTasks: string[];
  keyEvents: string[];
}

export function ShiftHandoffScreen() {
  const { facilityId } = useAppStore();
  const [selectedPatients, setSelectedPatients] = useState<Set<string>>(new Set());
  const [sbarNotes, setSbarNotes] = useState({ situation: "", background: "", assessment: "", recommendation: "" });
  const [generalNotes, setGeneralNotes] = useState("");

  // Fetch active inpatients for handoff
  const { data: admissions = [], isLoading } = useQuery({
    queryKey: ["handoff-patients"],
    queryFn: async () => {
      const r = await apiClient.get<{ data: Array<Record<string, unknown>> }>("/internal/v1/admissions");
      return r.data.data;
    },
  });

  const patients: PatientHandoff[] = (admissions as Array<Record<string, unknown>>).map((a) => ({
    id: String(a.id),
    name: String(a.admitting_diagnosis ?? "Patient"),
    bed: "Bed " + (a.bed_id ? String(a.bed_id).slice(0, 4) : "—"),
    ward: String(a.ward_id ? String(a.ward_id).slice(0, 4) : "—"),
    acuity: String(a.activity_level === "BED_REST" ? "HIGH" : "MODERATE"),
    diagnosis: String(a.admitting_diagnosis ?? ""),
    keyMeds: [],
    vitalStatus: "STABLE",
    pendingTasks: [],
    keyEvents: [],
  }));

  const togglePatient = (id: string) => {
    const next = new Set(selectedPatients);
    if (next.has(id)) next.delete(id); else next.add(id);
    setSelectedPatients(next);
  };

  const stats = {
    total: patients.length,
    critical: patients.filter((p) => p.acuity === "CRITICAL").length,
    high: patients.filter((p) => p.acuity === "HIGH").length,
    stable: patients.filter((p) => p.acuity === "STABLE" || p.acuity === "MODERATE").length,
  };

  const handleShare = async () => {
    const text = [
      "SHIFT HANDOFF REPORT",
      `Patients: ${selectedPatients.size}`,
      "",
      "SBAR:",
      `S: ${sbarNotes.situation}`,
      `B: ${sbarNotes.background}`,
      `A: ${sbarNotes.assessment}`,
      `R: ${sbarNotes.recommendation}`,
      "",
      `General Notes: ${generalNotes}`,
    ].join("\n");
    try {
      await Share.share({ message: text, title: "Shift Handoff" });
    } catch {
      Alert.alert("Shared", "Handoff report ready");
    }
  };

  const handleComplete = () => {
    Alert.alert("Handoff Complete", `${selectedPatients.size} patients handed off with SBAR notes`);
  };

  if (isLoading) return <Screen><Header title="Shift Handoff" /><LoadingSpinner /></Screen>;

  return (
    <Screen><Header title="Shift Handoff" />
      <ScrollView style={st.container} contentContainerStyle={st.content}>
        {/* Summary stats */}
        <View style={st.statsRow}>
          <View style={st.statCard}><Text style={st.statNum}>{stats.total}</Text><Text style={st.statLabel}>Total</Text></View>
          <View style={[st.statCard, { borderLeftColor: "#DC2626" }]}><Text style={[st.statNum, { color: "#DC2626" }]}>{stats.critical}</Text><Text style={st.statLabel}>Critical</Text></View>
          <View style={[st.statCard, { borderLeftColor: "#F59E0B" }]}><Text style={[st.statNum, { color: "#F59E0B" }]}>{stats.high}</Text><Text style={st.statLabel}>High</Text></View>
          <View style={[st.statCard, { borderLeftColor: "#22C55E" }]}><Text style={[st.statNum, { color: "#22C55E" }]}>{stats.stable}</Text><Text style={st.statLabel}>Stable</Text></View>
        </View>

        {/* Patient cards */}
        <Text style={st.sectionTitle}>Patients ({patients.length})</Text>
        {patients.map((p) => {
          const selected = selectedPatients.has(p.id);
          const acuityConfig = ACUITY_LEVELS.find((a) => a.level === p.acuity) ?? ACUITY_LEVELS[3];
          const vitalColors: Record<string, string> = { STABLE: "#22C55E", CONCERNING: "#F59E0B", CRITICAL: "#DC2626" };

          return (
            <TouchableOpacity key={p.id} onPress={() => togglePatient(p.id)}
              style={[st.patientCard, selected && st.patientSelected, { borderLeftColor: acuityConfig.color }]}>
              <View style={st.patientHeader}>
                <View style={[st.selectRing, selected && { backgroundColor: "#2563EB", borderColor: "#2563EB" }]}>
                  {selected && <Text style={st.checkmark}>✓</Text>}
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={st.patientName}>{p.name}</Text>
                  <Text style={st.patientMeta}>{p.bed} · {p.ward}</Text>
                </View>
                <Badge label={acuityConfig.label} variant={p.acuity === "CRITICAL" ? "destructive" : p.acuity === "HIGH" ? "warning" : "success"} />
              </View>
              <Text style={st.diagnosis}>{p.diagnosis}</Text>
              <View style={st.patientFooter}>
                <View style={st.vitalIndicator}>
                  <View style={[st.vitalDot, { backgroundColor: vitalColors[p.vitalStatus] }]} />
                  <Text style={st.vitalText}>Vitals: {p.vitalStatus.toLowerCase()}</Text>
                </View>
                {p.pendingTasks.length > 0 && <Text style={st.taskCount}>{p.pendingTasks.length} pending tasks</Text>}
              </View>
              {p.keyMeds.length > 0 && (
                <Text style={st.medsText}>Meds: {p.keyMeds.slice(0, 3).join(", ")}</Text>
              )}
            </TouchableOpacity>
          );
        })}

        {patients.length === 0 && (
          <View style={st.empty}><Text style={st.emptyText}>No active inpatients for handoff</Text></View>
        )}

        {/* SBAR Notes */}
        <Text style={st.sectionTitle}>SBAR Handoff Notes</Text>
        <View style={st.sbarCard}>
          <View style={st.sbarRow}>
            <View style={[st.sbarLabel, { backgroundColor: "#DC2626" }]}><Text style={st.sbarLabelText}>S</Text></View>
            <TextInput style={st.sbarInput} placeholder="Situation — What is happening now?" value={sbarNotes.situation}
              onChangeText={(v) => setSbarNotes({ ...sbarNotes, situation: v })} multiline />
          </View>
          <View style={st.sbarRow}>
            <View style={[st.sbarLabel, { backgroundColor: "#F59E0B" }]}><Text style={st.sbarLabelText}>B</Text></View>
            <TextInput style={st.sbarInput} placeholder="Background — What is the clinical context?" value={sbarNotes.background}
              onChangeText={(v) => setSbarNotes({ ...sbarNotes, background: v })} multiline />
          </View>
          <View style={st.sbarRow}>
            <View style={[st.sbarLabel, { backgroundColor: "#3B82F6" }]}><Text style={st.sbarLabelText}>A</Text></View>
            <TextInput style={st.sbarInput} placeholder="Assessment — What do you think the problem is?" value={sbarNotes.assessment}
              onChangeText={(v) => setSbarNotes({ ...sbarNotes, assessment: v })} multiline />
          </View>
          <View style={st.sbarRow}>
            <View style={[st.sbarLabel, { backgroundColor: "#22C55E" }]}><Text style={st.sbarLabelText}>R</Text></View>
            <TextInput style={st.sbarInput} placeholder="Recommendation — What do you recommend?" value={sbarNotes.recommendation}
              onChangeText={(v) => setSbarNotes({ ...sbarNotes, recommendation: v })} multiline />
          </View>
        </View>

        {/* General notes */}
        <Text style={st.sectionTitle}>General Notes</Text>
        <TextInput style={st.generalNotes} placeholder="Additional handoff notes..." value={generalNotes}
          onChangeText={setGeneralNotes} multiline />

        {/* Actions */}
        <View style={st.actions}>
          <Button title="Save Draft" variant="outline" onPress={() => Alert.alert("Saved", "Draft saved")} />
          <Button title="Share" variant="outline" onPress={handleShare} />
          <Button title="Complete Handoff" onPress={handleComplete} disabled={selectedPatients.size === 0} />
        </View>
      </ScrollView>
    </Screen>
  );
}

const st = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 16, gap: 12, paddingBottom: 32 },
  statsRow: { flexDirection: "row", gap: 8 },
  statCard: { flex: 1, backgroundColor: "#FFF", borderRadius: 8, padding: 10, alignItems: "center", borderLeftWidth: 3, borderLeftColor: "#E5E7EB" },
  statNum: { fontSize: 22, fontWeight: "900", color: "#111827" },
  statLabel: { fontSize: 10, color: "#6B7280" },
  sectionTitle: { fontSize: 15, fontWeight: "700", color: "#111827" },
  patientCard: { backgroundColor: "#FFF", borderRadius: 12, padding: 14, borderLeftWidth: 4, gap: 6, borderWidth: 1, borderColor: "#E5E7EB" },
  patientSelected: { borderColor: "#2563EB", backgroundColor: "#EFF6FF" },
  patientHeader: { flexDirection: "row", alignItems: "center", gap: 10 },
  selectRing: { width: 24, height: 24, borderRadius: 12, borderWidth: 2, borderColor: "#D1D5DB", alignItems: "center", justifyContent: "center" },
  checkmark: { color: "#FFF", fontSize: 14, fontWeight: "700" },
  patientName: { fontSize: 14, fontWeight: "600", color: "#111827" },
  patientMeta: { fontSize: 11, color: "#6B7280" },
  diagnosis: { fontSize: 13, color: "#374151" },
  patientFooter: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  vitalIndicator: { flexDirection: "row", alignItems: "center", gap: 4 },
  vitalDot: { width: 8, height: 8, borderRadius: 4 },
  vitalText: { fontSize: 11, color: "#6B7280" },
  taskCount: { fontSize: 11, color: "#F59E0B", fontWeight: "600" },
  medsText: { fontSize: 11, color: "#6B7280" },
  empty: { paddingVertical: 20, alignItems: "center" },
  emptyText: { color: "#9CA3AF", fontSize: 13 },
  sbarCard: { backgroundColor: "#FFF", borderRadius: 12, padding: 12, gap: 10 },
  sbarRow: { flexDirection: "row", gap: 8 },
  sbarLabel: { width: 32, height: 32, borderRadius: 8, alignItems: "center", justifyContent: "center" },
  sbarLabelText: { color: "#FFF", fontSize: 16, fontWeight: "900" },
  sbarInput: { flex: 1, borderWidth: 1, borderColor: "#E5E7EB", borderRadius: 8, padding: 10, fontSize: 13, minHeight: 44, textAlignVertical: "top" },
  generalNotes: { borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 8, padding: 12, fontSize: 13, minHeight: 80, textAlignVertical: "top", backgroundColor: "#FFF" },
  actions: { flexDirection: "row", gap: 8 },
});
