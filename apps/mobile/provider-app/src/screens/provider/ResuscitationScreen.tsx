/**
 * ResuscitationScreen — Phase-based resuscitation workflow with CPR timer,
 * cycle tracking, medication recording, and defibrillation logging.
 */
import React, { useState, useEffect, useCallback, useRef } from "react";
import { View, Text, ScrollView, TouchableOpacity, TextInput, StyleSheet, Alert, Vibration } from "react-native";
import { Screen, Header, Button, Badge } from "@impilo/mobile-design-system";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@impilo/mobile-api-client";

type Phase = "INACTIVE" | "ACTIVATION" | "RESUSCITATION" | "POST_ROSC" | "TERMINATED" | "SUMMARY";

const RESUS_MEDS = [
  { name: "Adrenaline 1mg", dose: "1mg", route: "IV" },
  { name: "Amiodarone 300mg", dose: "300mg", route: "IV" },
  { name: "Amiodarone 150mg", dose: "150mg", route: "IV" },
  { name: "Atropine 0.5mg", dose: "0.5mg", route: "IV" },
  { name: "Calcium Chloride 10ml", dose: "10ml 10%", route: "IV" },
  { name: "Sodium Bicarbonate 50ml", dose: "50ml 8.4%", route: "IV" },
  { name: "Magnesium 2g", dose: "2g", route: "IV" },
];

const RHYTHMS = ["VF", "Pulseless VT", "PEA", "Asystole", "Sinus Rhythm", "AF", "SVT", "Bradycardia"];

const TEAM_ROLES = ["Team Leader", "Airway", "Chest Compressions", "IV/IO Access", "Medications", "Defibrillator", "Documentation"];

export function ResuscitationScreen() {
  const [phase, setPhase] = useState<Phase>("INACTIVE");
  const [activationId, setActivationId] = useState<string | null>(null);
  const [phaseId, setPhaseId] = useState<string | null>(null);
  const [elapsedSecs, setElapsedSecs] = useState(0);
  const [cprCycleCount, setCprCycleCount] = useState(0);
  const [cprRunning, setCprRunning] = useState(false);
  const [cprSecs, setCprSecs] = useState(0);
  const [rhythm, setRhythm] = useState("Asystole");
  const [defibCount, setDefibCount] = useState(0);
  const [medsGiven, setMedsGiven] = useState<string[]>([]);
  const [teamAssignments, setTeamAssignments] = useState<Record<string, string>>({});
  const [outcome, setOutcome] = useState("STABILISED");
  const timerRef = useRef<NodeJS.Timeout | null>(null);
  const cprTimerRef = useRef<NodeJS.Timeout | null>(null);

  // Main elapsed timer
  useEffect(() => {
    if (phase !== "INACTIVE" && phase !== "SUMMARY") {
      timerRef.current = setInterval(() => setElapsedSecs((s) => s + 1), 1000);
    }
    return () => { if (timerRef.current) clearInterval(timerRef.current); };
  }, [phase]);

  // CPR cycle timer (2 min cycles)
  useEffect(() => {
    if (cprRunning) {
      cprTimerRef.current = setInterval(() => {
        setCprSecs((s) => {
          if (s >= 119) {
            Vibration.vibrate([0, 500, 200, 500]); // Alert: cycle complete
            return 0;
          }
          return s + 1;
        });
      }, 1000);
    }
    return () => { if (cprTimerRef.current) clearInterval(cprTimerRef.current); };
  }, [cprRunning]);

  const fmt = (secs: number) => `${Math.floor(secs / 60).toString().padStart(2, "0")}:${(secs % 60).toString().padStart(2, "0")}`;

  const activateMut = useMutation({
    mutationFn: () => apiClient.post<{ data: { id: string } }>("/internal/v1/emergency/activate", { protocolType: "CODE_BLUE" }),
    onSuccess: (data) => { setActivationId(data.data.data.id); setPhase("ACTIVATION"); },
  });

  const startResusMut = useMutation({
    mutationFn: () => apiClient.post<{ data: { id: string } }>(`/internal/v1/emergency/${activationId}/phases`, { phase: "RESUSCITATION", rhythm }),
    onSuccess: (data) => { setPhaseId(data.data.data.id); setPhase("RESUSCITATION"); },
  });

  const recordCPRMut = useMutation({
    mutationFn: (cycleNum: number) => apiClient.post(`/internal/v1/emergency/${activationId}/cpr-cycles`, { cycleNumber: cycleNum, durationSecs: 120, quality: "ADEQUATE" }),
  });

  const recordMedMut = useMutation({
    mutationFn: (med: { name: string; dose: string; route: string }) =>
      apiClient.post(`/internal/v1/emergency/${activationId}/medications`, { medication: med.name, dose: med.dose, route: med.route }),
  });

  const endMut = useMutation({
    mutationFn: () => apiClient.post(`/internal/v1/emergency/${activationId}/end`, { outcome, notes: `Rhythm: ${rhythm}, CPR cycles: ${cprCycleCount}, Defibs: ${defibCount}` }),
    onSuccess: () => setPhase("SUMMARY"),
  });

  const startCPRCycle = useCallback(() => {
    const newCount = cprCycleCount + 1;
    setCprCycleCount(newCount);
    setCprSecs(0);
    setCprRunning(true);
    recordCPRMut.mutate(newCount);
  }, [cprCycleCount]);

  const stopCPR = useCallback(() => { setCprRunning(false); setCprSecs(0); }, []);

  const giveMed = useCallback((med: typeof RESUS_MEDS[0]) => {
    setMedsGiven((prev) => [...prev, `${med.name} @ ${fmt(elapsedSecs)}`]);
    recordMedMut.mutate(med);
  }, [elapsedSecs]);

  // ── INACTIVE ──
  if (phase === "INACTIVE") {
    return (
      <Screen><Header title="Resuscitation" />
        <View style={styles.center}>
          <TouchableOpacity style={styles.bigActivateBtn} onPress={() => activateMut.mutate()}>
            <Text style={styles.bigActivateText}>ACTIVATE CODE BLUE</Text>
            <Text style={styles.bigActivateSub}>Tap to begin resuscitation protocol</Text>
          </TouchableOpacity>
        </View>
      </Screen>
    );
  }

  // ── SUMMARY ──
  if (phase === "SUMMARY") {
    return (
      <Screen><Header title="Resuscitation Summary" />
        <ScrollView style={styles.scroll} contentContainerStyle={styles.pad}>
          <View style={styles.summaryCard}>
            <Text style={styles.summaryTitle}>Resuscitation Complete</Text>
            <Badge label={outcome} variant={outcome === "ROSC" ? "success" : "default"} />
            <Text style={styles.summaryItem}>Duration: {fmt(elapsedSecs)}</Text>
            <Text style={styles.summaryItem}>CPR Cycles: {cprCycleCount}</Text>
            <Text style={styles.summaryItem}>Defibrillations: {defibCount}</Text>
            <Text style={styles.summaryItem}>Final Rhythm: {rhythm}</Text>
            <Text style={styles.summaryItem}>Medications: {medsGiven.length}</Text>
            {medsGiven.map((m, i) => <Text key={i} style={styles.medLog}>  • {m}</Text>)}
          </View>
        </ScrollView>
      </Screen>
    );
  }

  return (
    <Screen><Header title={`Resuscitation — ${phase.replace(/_/g, " ")}`} />
      <ScrollView style={styles.scroll} contentContainerStyle={styles.pad}>
        {/* Timer bar */}
        <View style={styles.timerBar}>
          <Text style={styles.elapsed}>{fmt(elapsedSecs)}</Text>
          <Badge label={phase.replace(/_/g, " ")} variant={phase === "RESUSCITATION" ? "destructive" : "warning"} />
        </View>

        {/* ACTIVATION phase: team assignment + rhythm */}
        {phase === "ACTIVATION" && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Team Roles</Text>
            {TEAM_ROLES.map((role) => (
              <View key={role} style={styles.roleRow}>
                <Text style={styles.roleLabel}>{role}</Text>
                <TextInput style={styles.roleInput} placeholder="Name" value={teamAssignments[role] ?? ""}
                  onChangeText={(v) => setTeamAssignments({ ...teamAssignments, [role]: v })} />
              </View>
            ))}
            <Text style={styles.sectionTitle}>Initial Rhythm</Text>
            <View style={styles.chipRow}>
              {RHYTHMS.map((r) => (
                <TouchableOpacity key={r} onPress={() => setRhythm(r)} style={[styles.chip, rhythm === r && styles.activeChip]}>
                  <Text style={[styles.chipText, rhythm === r && styles.activeChipText]}>{r}</Text>
                </TouchableOpacity>
              ))}
            </View>
            <Button title="Begin Resuscitation" onPress={() => startResusMut.mutate()} />
          </View>
        )}

        {/* RESUSCITATION phase: CPR timer + meds + defib */}
        {phase === "RESUSCITATION" && (
          <View style={styles.section}>
            {/* CPR Timer */}
            <View style={[styles.cprCard, cprRunning && { borderColor: "#DC2626" }]}>
              <Text style={styles.cprLabel}>CPR Cycle {cprCycleCount}</Text>
              <Text style={[styles.cprTimer, cprSecs > 100 && { color: "#DC2626" }]}>{fmt(cprSecs)}</Text>
              <Text style={styles.cprHint}>{cprRunning ? "2-min cycle — vibrates on completion" : "Start a cycle"}</Text>
              <View style={styles.cprActions}>
                {!cprRunning ? (
                  <Button title={`Start Cycle ${cprCycleCount + 1}`} onPress={startCPRCycle} />
                ) : (
                  <Button title="Stop CPR" variant="outline" onPress={stopCPR} />
                )}
              </View>
            </View>

            {/* Rhythm check */}
            <Text style={styles.sectionTitle}>Current Rhythm</Text>
            <View style={styles.chipRow}>
              {RHYTHMS.map((r) => (
                <TouchableOpacity key={r} onPress={() => setRhythm(r)} style={[styles.chip, rhythm === r && styles.activeChip]}>
                  <Text style={[styles.chipText, rhythm === r && styles.activeChipText]}>{r}</Text>
                </TouchableOpacity>
              ))}
            </View>

            {/* Defibrillation */}
            <Button title={`Defibrillation (${defibCount})`} onPress={() => { setDefibCount((d) => d + 1); Alert.alert("Shock Delivered", `Defibrillation #${defibCount + 1}`); }} />

            {/* Medications */}
            <Text style={styles.sectionTitle}>Medications</Text>
            {RESUS_MEDS.map((med) => (
              <TouchableOpacity key={med.name} style={styles.medBtn} onPress={() => giveMed(med)}>
                <Text style={styles.medBtnText}>{med.name} ({med.route})</Text>
              </TouchableOpacity>
            ))}

            {/* Medication log */}
            {medsGiven.length > 0 && (
              <View style={styles.logSection}>
                <Text style={styles.sectionTitle}>Given</Text>
                {medsGiven.map((m, i) => <Text key={i} style={styles.medLog}>• {m}</Text>)}
              </View>
            )}

            {/* End resuscitation */}
            <View style={{ marginTop: 16, gap: 8 }}>
              <Text style={styles.sectionTitle}>End Resuscitation</Text>
              <View style={styles.chipRow}>
                {["ROSC", "STABILISED", "TRANSFERRED", "DEATH"].map((o) => (
                  <TouchableOpacity key={o} onPress={() => setOutcome(o)} style={[styles.chip, outcome === o && styles.activeChip]}>
                    <Text style={[styles.chipText, outcome === o && styles.activeChipText]}>{o}</Text>
                  </TouchableOpacity>
                ))}
              </View>
              <Button title="End Protocol" onPress={() => endMut.mutate()} variant="outline" />
            </View>
          </View>
        )}

        {/* POST_ROSC / TERMINATED */}
        {(phase === "POST_ROSC" || phase === "TERMINATED") && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Post-Resuscitation</Text>
            <Button title="Complete & View Summary" onPress={() => endMut.mutate()} />
          </View>
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, justifyContent: "center", alignItems: "center", padding: 32 },
  bigActivateBtn: { backgroundColor: "#DC2626", borderRadius: 24, padding: 40, alignItems: "center", width: "100%" },
  bigActivateText: { color: "#FFF", fontSize: 24, fontWeight: "900" },
  bigActivateSub: { color: "#FCA5A5", fontSize: 14, marginTop: 8 },
  scroll: { flex: 1 },
  pad: { padding: 16, gap: 12, paddingBottom: 32 },
  timerBar: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", backgroundColor: "#1F2937", borderRadius: 12, padding: 16 },
  elapsed: { color: "#FFF", fontSize: 32, fontWeight: "900", fontVariant: ["tabular-nums"] },
  section: { gap: 10 },
  sectionTitle: { fontSize: 14, fontWeight: "700", color: "#111827" },
  roleRow: { flexDirection: "row", alignItems: "center", gap: 8 },
  roleLabel: { width: 130, fontSize: 13, fontWeight: "600", color: "#374151" },
  roleInput: { flex: 1, borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 6, padding: 8, fontSize: 13 },
  chipRow: { flexDirection: "row", gap: 6, flexWrap: "wrap" },
  chip: { paddingHorizontal: 10, paddingVertical: 6, borderRadius: 14, backgroundColor: "#E5E7EB" },
  activeChip: { backgroundColor: "#DC2626" },
  chipText: { fontSize: 11, color: "#374151", fontWeight: "500" },
  activeChipText: { color: "#FFF" },
  cprCard: { backgroundColor: "#FFF", borderRadius: 16, padding: 20, alignItems: "center", borderWidth: 3, borderColor: "#E5E7EB" },
  cprLabel: { fontSize: 14, fontWeight: "600", color: "#6B7280" },
  cprTimer: { fontSize: 64, fontWeight: "900", color: "#111827", fontVariant: ["tabular-nums"] },
  cprHint: { fontSize: 12, color: "#9CA3AF" },
  cprActions: { marginTop: 12, width: "100%" },
  medBtn: { backgroundColor: "#EFF6FF", borderRadius: 8, padding: 10, borderWidth: 1, borderColor: "#BFDBFE" },
  medBtnText: { fontSize: 13, fontWeight: "600", color: "#1D4ED8" },
  logSection: { gap: 2 },
  medLog: { fontSize: 12, color: "#6B7280" },
  summaryCard: { backgroundColor: "#FFF", borderRadius: 16, padding: 20, gap: 8 },
  summaryTitle: { fontSize: 20, fontWeight: "700", color: "#111827" },
  summaryItem: { fontSize: 14, color: "#374151" },
});
