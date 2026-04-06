/**
 * TraumaScreen — 5-phase trauma activation with ABCDE primary survey,
 * secondary survey, vitals, team roles, and trauma scoring.
 */
import React, { useState, useEffect } from "react";
import { View, Text, ScrollView, TouchableOpacity, TextInput, StyleSheet } from "react-native";
import { Screen, Header, Button, Badge } from "@impilo/mobile-design-system";

type Phase = "ACTIVATION" | "PRIMARY" | "INTERVENTIONS" | "SECONDARY" | "SUMMARY";

const ABCDE: Record<string, { label: string; color: string; items: string[] }> = {
  A: { label: "Airway", color: "#DC2626", items: ["Airway patent", "Airway compromised — intervention needed", "C-spine immobilisation maintained"] },
  B: { label: "Breathing", color: "#F97316", items: ["Breathing adequate", "Equal chest expansion", "Breath sounds equal bilaterally", "High-flow oxygen applied"] },
  C: { label: "Circulation", color: "#EAB308", items: ["Pulse present", "IV access established", "Visible bleeding controlled", "Fluid resuscitation initiated"] },
  D: { label: "Disability", color: "#22C55E", items: ["GCS assessed", "Pupils checked", "Limb movement assessed", "Blood glucose checked"] },
  E: { label: "Exposure", color: "#3B82F6", items: ["Patient fully exposed", "Log roll performed", "Hypothermia prevention measures", "Temperature measured"] },
};

const TEAM_ROLES = [
  { role: "Team Leader", required: true }, { role: "Airway", required: true },
  { role: "Circulation/IV", required: true }, { role: "Procedures", required: true },
  { role: "Documentation", required: true }, { role: "Radiology Liaison", required: false },
];

const SECONDARY_REGIONS = ["Head", "Face", "Neck", "Chest", "Abdomen", "Pelvis", "Spine", "Extremities"];
const MECHANISMS = ["RTA Driver", "RTA Passenger", "RTA Pedestrian", "Fall", "Assault", "Stabbing", "Gunshot", "Burns", "Other"];
const DISPOSITIONS = ["Theatre", "ICU", "HDU", "Ward Admission", "Observation", "Transfer", "Discharge"];

export function TraumaScreen() {
  const [phase, setPhase] = useState<Phase>("ACTIVATION");
  const [elapsedSecs, setElapsedSecs] = useState(0);
  const [mechanism, setMechanism] = useState("RTA Driver");
  const [traumaLevel, setTraumaLevel] = useState("2");
  const [team, setTeam] = useState<Record<string, string>>({});
  const [checks, setChecks] = useState<Record<string, boolean>>({});
  const [vitals, setVitals] = useState({ hr: "", bp: "", rr: "", spo2: "", gcs: "" });
  const [secondaryNotes, setSecondaryNotes] = useState<Record<string, string>>({});
  const [disposition, setDisposition] = useState("");

  useEffect(() => {
    const t = setInterval(() => setElapsedSecs((s) => s + 1), 1000);
    return () => clearInterval(t);
  }, []);

  const fmt = (secs: number) => `${Math.floor(secs / 60).toString().padStart(2, "0")}:${(secs % 60).toString().padStart(2, "0")}`;
  const toggleCheck = (key: string) => setChecks({ ...checks, [key]: !checks[key] });

  // GCS components from input
  const gcsTotal = parseInt(vitals.gcs) || 0;

  return (
    <Screen><Header title={`Trauma — ${phase}`} />
      <View style={st.timerBar}>
        <Text style={st.elapsed}>{fmt(elapsedSecs)}</Text>
        <Badge label={`Level ${traumaLevel}`} variant={traumaLevel === "1" ? "destructive" : "warning"} />
        <Badge label={phase} variant="info" />
      </View>
      <ScrollView style={st.scroll} contentContainerStyle={st.pad}>

        {phase === "ACTIVATION" && (
          <View style={st.section}>
            <Text style={st.title}>Trauma Activation</Text>
            <Text style={st.label}>Mechanism of Injury</Text>
            <View style={st.chipRow}>
              {MECHANISMS.map((m) => (
                <TouchableOpacity key={m} onPress={() => setMechanism(m)} style={[st.chip, mechanism === m && st.chipActive]}>
                  <Text style={[st.chipText, mechanism === m && st.chipTextActive]}>{m}</Text>
                </TouchableOpacity>
              ))}
            </View>
            <Text style={st.label}>Trauma Level</Text>
            <View style={st.chipRow}>
              {["1", "2", "3"].map((l) => (
                <TouchableOpacity key={l} onPress={() => setTraumaLevel(l)} style={[st.levelBtn, traumaLevel === l && st.levelActive]}>
                  <Text style={[st.levelText, traumaLevel === l && st.levelTextActive]}>Level {l}</Text>
                </TouchableOpacity>
              ))}
            </View>
            <Text style={st.label}>Team Assignment</Text>
            {TEAM_ROLES.map((tr) => (
              <View key={tr.role} style={st.roleRow}>
                <Text style={st.roleLabel}>{tr.role} {tr.required ? "*" : ""}</Text>
                <TextInput style={st.roleInput} placeholder="Name" value={team[tr.role] ?? ""} onChangeText={(v) => setTeam({ ...team, [tr.role]: v })} />
              </View>
            ))}
            <Button label="Begin Primary Survey" onPress={() => setPhase("PRIMARY")} />
          </View>
        )}

        {phase === "PRIMARY" && (
          <View style={st.section}>
            <Text style={st.title}>ABCDE Primary Survey</Text>
            <View style={st.vitalsBar}>
              <TextInput style={st.vitalInput} placeholder="HR" value={vitals.hr} onChangeText={(v) => setVitals({ ...vitals, hr: v })} keyboardType="numeric" />
              <TextInput style={st.vitalInput} placeholder="BP" value={vitals.bp} onChangeText={(v) => setVitals({ ...vitals, bp: v })} />
              <TextInput style={st.vitalInput} placeholder="RR" value={vitals.rr} onChangeText={(v) => setVitals({ ...vitals, rr: v })} keyboardType="numeric" />
              <TextInput style={st.vitalInput} placeholder="SpO2" value={vitals.spo2} onChangeText={(v) => setVitals({ ...vitals, spo2: v })} keyboardType="numeric" />
              <TextInput style={st.vitalInput} placeholder="GCS" value={vitals.gcs} onChangeText={(v) => setVitals({ ...vitals, gcs: v })} keyboardType="numeric" />
            </View>
            {Object.entries(ABCDE).map(([key, section]) => (
              <View key={key} style={[st.abcdeSection, { borderLeftColor: section.color }]}>
                <View style={[st.abcdeHeader, { backgroundColor: section.color + "20" }]}>
                  <Text style={[st.abcdeLetter, { color: section.color }]}>{key}</Text>
                  <Text style={st.abcdeLabel}>{section.label}</Text>
                  <Text style={st.abcdeCount}>{section.items.filter((i) => checks[`${key}-${i}`]).length}/{section.items.length}</Text>
                </View>
                {section.items.map((item) => {
                  const ck = `${key}-${item}`;
                  return (
                    <TouchableOpacity key={ck} onPress={() => toggleCheck(ck)} style={st.checkRow}>
                      <View style={[st.checkbox, checks[ck] && st.checkboxChecked]}>
                        {checks[ck] && <Text style={st.checkmark}>✓</Text>}
                      </View>
                      <Text style={[st.checkText, checks[ck] && st.checkTextDone]}>{item}</Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
            ))}
            <Button label="Proceed to Interventions" onPress={() => setPhase("INTERVENTIONS")} />
          </View>
        )}

        {phase === "INTERVENTIONS" && (
          <View style={st.section}>
            <Text style={st.title}>Interventions</Text>
            <Text style={st.hint}>Document interventions performed during primary survey</Text>
            <Button label="Proceed to Secondary Survey" onPress={() => setPhase("SECONDARY")} />
          </View>
        )}

        {phase === "SECONDARY" && (
          <View style={st.section}>
            <Text style={st.title}>Secondary Survey — Head to Toe</Text>
            {SECONDARY_REGIONS.map((region) => (
              <View key={region} style={st.regionCard}>
                <Text style={st.regionLabel}>{region}</Text>
                <TextInput style={st.regionInput} placeholder={`${region} findings...`} value={secondaryNotes[region] ?? ""}
                  onChangeText={(v) => setSecondaryNotes({ ...secondaryNotes, [region]: v })} multiline />
              </View>
            ))}
            <Text style={st.label}>Disposition</Text>
            <View style={st.chipRow}>
              {DISPOSITIONS.map((d) => (
                <TouchableOpacity key={d} onPress={() => setDisposition(d)} style={[st.chip, disposition === d && st.chipActive]}>
                  <Text style={[st.chipText, disposition === d && st.chipTextActive]}>{d}</Text>
                </TouchableOpacity>
              ))}
            </View>
            <Button label="Complete & View Summary" onPress={() => setPhase("SUMMARY")} />
          </View>
        )}

        {phase === "SUMMARY" && (
          <View style={st.section}>
            <Text style={st.title}>Trauma Summary</Text>
            <View style={st.summaryCard}>
              <Text style={st.summaryItem}>Duration: {fmt(elapsedSecs)}</Text>
              <Text style={st.summaryItem}>Mechanism: {mechanism}</Text>
              <Text style={st.summaryItem}>Level: {traumaLevel}</Text>
              <Text style={st.summaryItem}>GCS: {vitals.gcs || "—"}</Text>
              <Text style={st.summaryItem}>Primary Survey: {Object.values(checks).filter(Boolean).length}/{Object.keys(ABCDE).reduce((a, k) => a + ABCDE[k].items.length, 0)} checks</Text>
              <Text style={st.summaryItem}>Disposition: {disposition || "—"}</Text>
            </View>
          </View>
        )}
      </ScrollView>
    </Screen>
  );
}

const st = StyleSheet.create({
  timerBar: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", backgroundColor: "#1F2937", padding: 12 },
  elapsed: { color: "#FFF", fontSize: 24, fontWeight: "900", fontVariant: ["tabular-nums"] },
  scroll: { flex: 1 },
  pad: { padding: 16, gap: 12, paddingBottom: 32 },
  section: { gap: 10 },
  title: { fontSize: 16, fontWeight: "700", color: "#111827" },
  label: { fontSize: 13, fontWeight: "600", color: "#374151" },
  hint: { fontSize: 13, color: "#6B7280" },
  chipRow: { flexDirection: "row", gap: 6, flexWrap: "wrap" },
  chip: { paddingHorizontal: 10, paddingVertical: 6, borderRadius: 14, backgroundColor: "#E5E7EB" },
  chipActive: { backgroundColor: "#DC2626" },
  chipText: { fontSize: 11, color: "#374151" },
  chipTextActive: { color: "#FFF" },
  levelBtn: { flex: 1, padding: 12, borderRadius: 8, borderWidth: 2, borderColor: "#E5E7EB", alignItems: "center" },
  levelActive: { borderColor: "#DC2626", backgroundColor: "#FEF2F2" },
  levelText: { fontSize: 14, fontWeight: "600", color: "#6B7280" },
  levelTextActive: { color: "#DC2626" },
  roleRow: { flexDirection: "row", alignItems: "center", gap: 8 },
  roleLabel: { width: 120, fontSize: 13, fontWeight: "500", color: "#374151" },
  roleInput: { flex: 1, borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 6, padding: 8, fontSize: 13 },
  vitalsBar: { flexDirection: "row", gap: 4, backgroundColor: "#111827", borderRadius: 8, padding: 8 },
  vitalInput: { flex: 1, backgroundColor: "#374151", borderRadius: 6, padding: 8, fontSize: 13, color: "#FFF", textAlign: "center" },
  abcdeSection: { borderLeftWidth: 4, borderRadius: 8, overflow: "hidden" },
  abcdeHeader: { flexDirection: "row", alignItems: "center", gap: 8, padding: 10 },
  abcdeLetter: { fontSize: 20, fontWeight: "900", width: 28 },
  abcdeLabel: { flex: 1, fontSize: 14, fontWeight: "600", color: "#111827" },
  abcdeCount: { fontSize: 12, color: "#6B7280", fontWeight: "600" },
  checkRow: { flexDirection: "row", alignItems: "center", gap: 10, padding: 10, paddingLeft: 16 },
  checkbox: { width: 22, height: 22, borderWidth: 2, borderColor: "#D1D5DB", borderRadius: 4, alignItems: "center", justifyContent: "center" },
  checkboxChecked: { backgroundColor: "#22C55E", borderColor: "#22C55E" },
  checkmark: { color: "#FFF", fontSize: 14, fontWeight: "700" },
  checkText: { fontSize: 13, color: "#374151", flex: 1 },
  checkTextDone: { textDecorationLine: "line-through", color: "#9CA3AF" },
  regionCard: { gap: 4 },
  regionLabel: { fontSize: 13, fontWeight: "600", color: "#374151" },
  regionInput: { borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 6, padding: 8, fontSize: 13, minHeight: 40 },
  summaryCard: { backgroundColor: "#F9FAFB", borderRadius: 12, padding: 16, gap: 6 },
  summaryItem: { fontSize: 14, color: "#374151" },
});
