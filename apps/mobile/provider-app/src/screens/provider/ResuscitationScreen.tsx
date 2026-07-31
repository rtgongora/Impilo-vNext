/**
 * ResuscitationScreen — Phase-based resuscitation via /internal/v1/ed/resuscitation/*
 */
import React, { useState, useEffect, useCallback, useRef } from "react";
import { View, Text, ScrollView, TouchableOpacity, TextInput, StyleSheet, Alert, Vibration } from "react-native";
import { Screen, Header, Button, Badge, colors } from "@impilo/mobile-design-system";
import { useMutation } from "@tanstack/react-query";
import {
  activateResuscitation,
  startResusPhase,
  addCprCycle,
  addResusMedication,
  endResuscitation,
} from "../../services/resusService";

type Phase = "INACTIVE" | "ACTIVATION" | "RESUSCITATION" | "POST_ROSC" | "TERMINATED" | "SUMMARY";

const RESUS_MEDS = [
  { name: "Adrenaline 1mg", dose: "1mg", route: "IV" },
  { name: "Amiodarone 300mg", dose: "300mg", route: "IV" },
  { name: "Amiodarone 150mg", dose: "150mg", route: "IV" },
  { name: "Atropine 0.5mg", dose: "0.5mg", route: "IV" },
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

  useEffect(() => {
    if (phase !== "INACTIVE" && phase !== "SUMMARY") {
      timerRef.current = setInterval(() => setElapsedSecs((s) => s + 1), 1000);
    }
    return () => { if (timerRef.current) clearInterval(timerRef.current); };
  }, [phase]);

  useEffect(() => {
    if (cprRunning) {
      cprTimerRef.current = setInterval(() => {
        setCprSecs((s) => {
          if (s >= 119) {
            Vibration.vibrate([0, 500, 200, 500]);
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
    mutationFn: () => activateResuscitation({ protocolType: "CODE_BLUE", teamLeader: teamAssignments["Team Leader"] ?? "" }),
    onSuccess: (data) => { setActivationId(data?.id ?? null); setPhase("ACTIVATION"); },
  });

  const startResusMut = useMutation({
    mutationFn: () => startResusPhase(activationId!, { phase: "RESUSCITATION", rhythm }),
    onSuccess: (data) => { setPhaseId(data?.id ?? null); setPhase("RESUSCITATION"); },
  });

  const recordCPRMut = useMutation({
    mutationFn: (cycleNum: number) =>
      addCprCycle(activationId!, { cycleNumber: cycleNum, durationSecs: 120, quality: "ADEQUATE", rhythm }),
  });

  const recordMedMut = useMutation({
    mutationFn: (med: { name: string; dose: string; route: string }) =>
      addResusMedication(activationId!, { name: med.name, dose: med.dose, route: med.route }),
  });

  const endMut = useMutation({
    mutationFn: () =>
      endResuscitation(activationId!, {
        outcome,
        notes: `Rhythm: ${rhythm}, CPR cycles: ${cprCycleCount}, Defibs: ${defibCount}`,
      }),
    onSuccess: () => setPhase("SUMMARY"),
  });

  const startCPRCycle = useCallback(() => {
    const newCount = cprCycleCount + 1;
    setCprCycleCount(newCount);
    setCprSecs(0);
    setCprRunning(true);
    recordCPRMut.mutate(newCount);
  }, [cprCycleCount, recordCPRMut]);

  const stopCPR = useCallback(() => { setCprRunning(false); setCprSecs(0); }, []);

  const giveMed = useCallback((med: typeof RESUS_MEDS[0]) => {
    setMedsGiven((prev) => [...prev, `${med.name} @ ${fmt(elapsedSecs)}`]);
    recordMedMut.mutate(med);
  }, [elapsedSecs, recordMedMut]);

  if (phase === "INACTIVE") {
    return (
      <Screen><Header title="Resuscitation" />
        <View style={styles.center}>
          <TouchableOpacity style={styles.bigActivateBtn} onPress={() => activateMut.mutate()} disabled={activateMut.isPending}>
            <Text style={styles.bigActivateText}>ACTIVATE CODE BLUE</Text>
            <Text style={styles.bigActivateSub}>Canonical /internal/v1/ed/resuscitation</Text>
          </TouchableOpacity>
        </View>
      </Screen>
    );
  }

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
          </View>
        </ScrollView>
      </Screen>
    );
  }

  return (
    <Screen><Header title={`Resuscitation — ${phase.replace(/_/g, " ")}`} />
      <ScrollView style={styles.scroll} contentContainerStyle={styles.pad}>
        <View style={styles.timerBar}>
          <Text style={styles.elapsed}>{fmt(elapsedSecs)}</Text>
          <Badge label={phase.replace(/_/g, " ")} variant={phase === "RESUSCITATION" ? "destructive" : "warning"} />
        </View>

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
            <Button title="Begin Resuscitation" onPress={() => startResusMut.mutate()} disabled={!activationId} />
          </View>
        )}

        {phase === "RESUSCITATION" && (
          <View style={styles.section}>
            <View style={[styles.cprCard, cprRunning && { borderColor: colors.ui.error.main }]}>
              <Text style={styles.cprLabel}>CPR Cycle {cprCycleCount}</Text>
              <Text style={[styles.cprTimer, cprSecs > 100 && { color: colors.ui.error.main }]}>{fmt(cprSecs)}</Text>
              <View style={styles.cprActions}>
                {!cprRunning ? (
                  <Button title={`Start Cycle ${cprCycleCount + 1}`} onPress={startCPRCycle} />
                ) : (
                  <Button title="Stop CPR" variant="outline" onPress={stopCPR} />
                )}
              </View>
            </View>
            <View style={styles.chipRow}>
              {RHYTHMS.map((r) => (
                <TouchableOpacity key={r} onPress={() => setRhythm(r)} style={[styles.chip, rhythm === r && styles.activeChip]}>
                  <Text style={[styles.chipText, rhythm === r && styles.activeChipText]}>{r}</Text>
                </TouchableOpacity>
              ))}
            </View>
            <Button title={`Defibrillation (${defibCount})`} onPress={() => { setDefibCount((d) => d + 1); Alert.alert("Shock Delivered", `Defibrillation #${defibCount + 1}`); }} />
            {RESUS_MEDS.map((med) => (
              <TouchableOpacity key={med.name} style={styles.medBtn} onPress={() => giveMed(med)}>
                <Text style={styles.medBtnText}>{med.name}</Text>
              </TouchableOpacity>
            ))}
            <View style={{ marginTop: 16, gap: 8 }}>
              {["ROSC", "STABILISED", "TRANSFERRED", "DEATH"].map((o) => (
                <TouchableOpacity key={o} onPress={() => setOutcome(o)} style={[styles.chip, outcome === o && styles.activeChip]}>
                  <Text style={[styles.chipText, outcome === o && styles.activeChipText]}>{o}</Text>
                </TouchableOpacity>
              ))}
              <Button title="End Protocol" onPress={() => endMut.mutate()} variant="outline" />
            </View>
          </View>
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, justifyContent: "center", alignItems: "center", padding: 32 },
  bigActivateBtn: { backgroundColor: colors.ui.error.main, borderRadius: 24, padding: 40, alignItems: "center", width: "100%" },
  bigActivateText: { color: "#FFF", fontSize: 24, fontWeight: "900" },
  bigActivateSub: { color: "#FCA5A5", fontSize: 12, marginTop: 8 },
  scroll: { flex: 1 },
  pad: { padding: 16, gap: 12, paddingBottom: 32 },
  timerBar: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", backgroundColor: colors.gray[800], borderRadius: 12, padding: 16 },
  elapsed: { color: "#FFF", fontSize: 32, fontWeight: "900", fontVariant: ["tabular-nums"] },
  section: { gap: 10 },
  sectionTitle: { fontSize: 14, fontWeight: "700", color: colors.gray[900] },
  roleRow: { flexDirection: "row", alignItems: "center", gap: 8 },
  roleLabel: { width: 130, fontSize: 13, fontWeight: "600", color: colors.gray[700] },
  roleInput: { flex: 1, borderWidth: 1, borderColor: colors.gray[300], borderRadius: 6, padding: 8, fontSize: 13 },
  chipRow: { flexDirection: "row", gap: 6, flexWrap: "wrap" },
  chip: { paddingHorizontal: 10, paddingVertical: 6, borderRadius: 14, backgroundColor: colors.gray[200] },
  activeChip: { backgroundColor: colors.ui.error.main },
  chipText: { fontSize: 11, color: colors.gray[700], fontWeight: "500" },
  activeChipText: { color: "#FFF" },
  cprCard: { backgroundColor: "#FFF", borderRadius: 16, padding: 20, alignItems: "center", borderWidth: 3, borderColor: colors.gray[200] },
  cprLabel: { fontSize: 14, fontWeight: "600", color: colors.gray[500] },
  cprTimer: { fontSize: 64, fontWeight: "900", color: colors.gray[900], fontVariant: ["tabular-nums"] },
  cprActions: { marginTop: 12, width: "100%" },
  medBtn: { backgroundColor: "#EFF6FF", borderRadius: 8, padding: 10, borderWidth: 1, borderColor: "#BFDBFE" },
  medBtnText: { fontSize: 13, fontWeight: "600", color: "#1D4ED8" },
  summaryCard: { backgroundColor: "#FFF", borderRadius: 16, padding: 20, gap: 8 },
  summaryTitle: { fontSize: 20, fontWeight: "700", color: colors.gray[900] },
  summaryItem: { fontSize: 14, color: colors.gray[700] },
});
