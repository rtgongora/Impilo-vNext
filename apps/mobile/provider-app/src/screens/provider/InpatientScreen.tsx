/**
 * InpatientScreen — Care pathways, emergency protocols, and inpatient management.
 * Tabs: Care Plans, Fluid I/O, Emergency, EWS, Admissions, Rounds, Observations, Transfers
 */
import React, { useState } from "react";
import { View, Text, TextInput, ScrollView, TouchableOpacity, FlatList, StyleSheet, Alert } from "react-native";
import { Screen, Header, Button, Badge, LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  fetchCarePlans, createCarePlan, fetchFluidBalance, recordFluid,
  listEmergencyActivations, activateEmergency, logEmergencyAction, endEmergency,
  recordResuscitation, recordApgar, fetchApgar, recordEWS, fetchEWS,
  createAdmission, listAdmissions, startWardRound, addRoundEntry, listWardRounds,
  recordObservation, fetchObservations, requestTransfer, listTransfers,
  listEscalations, acknowledgeEscalation, respondEscalation, type InpatientEscalation,
} from "../../services/inpatientService";
import { useEncounterStore } from "../../stores/encounterStore";
import { NEWS2ScoringScreen } from "./NEWS2ScoringScreen";
import { ResuscitationScreen } from "./ResuscitationScreen";
import { APGARScreen } from "./APGARScreen";
import { DischargeClearanceScreen } from "./DischargeClearanceScreen";
import { CarePlanDetailScreen } from "./CarePlanDetailScreen";
import { TraumaScreen } from "./TraumaScreen";
import { EdVisitScreen } from "./EdVisitScreen";
import { VitalsMonitorScreen } from "./VitalsMonitorScreen";
import { ShiftHandoffScreen } from "./ShiftHandoffScreen";
import { CriticalEventScreen } from "./CriticalEventScreen";
import { WardAlertsScreen } from "./WardAlertsScreen";
import { TheatreProcedureScreen } from "./TheatreProcedureScreen";

type InpatientTab = "care" | "fluid" | "vitals" | "emergency" | "ed" | "resus" | "trauma" | "ews" | "escalations" | "apgar" | "theatre" | "admit" | "rounds" | "obs" | "transfer" | "handoff" | "alerts" | "clearance";

const TABS: { id: InpatientTab; label: string }[] = [
  { id: "care", label: "Care Plans" }, { id: "fluid", label: "Fluid I/O" },
  { id: "vitals", label: "Vitals" }, { id: "emergency", label: "Emergency" },
  { id: "ed", label: "ED Visit" }, { id: "resus", label: "Resus" }, { id: "trauma", label: "Trauma" },
  { id: "ews", label: "NEWS2" },   { id: "escalations", label: "Escalations" },   { id: "apgar", label: "APGAR" },
  { id: "theatre", label: "Theatre" },
  { id: "admit", label: "Admissions" }, { id: "rounds", label: "Rounds" },
  { id: "obs", label: "Observations" }, { id: "transfer", label: "Transfers" },
  { id: "handoff", label: "Handoff" }, { id: "alerts", label: "Alerts" },
  { id: "clearance", label: "Discharge" },
];

export function InpatientScreen() {
  const [tab, setTab] = useState<InpatientTab>("care");

  return (
    <Screen><Header title="Inpatient Management" />
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={s.tabBar} contentContainerStyle={s.tabBarContent}>
        {TABS.map((t) => (
          <TouchableOpacity key={t.id} onPress={() => setTab(t.id)} style={[s.tab, tab === t.id && s.activeTab]}>
            <Text style={[s.tabText, tab === t.id && s.activeTabText]}>{t.label}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
      <ScrollView style={s.content} contentContainerStyle={s.pad}>
        {tab === "care" && <CarePlanDetailScreen />}
        {tab === "fluid" && <FluidBalancePanel />}
        {tab === "emergency" && <CriticalEventScreen />}
        {tab === "ed" && <EdVisitScreen />}
        {tab === "ews" && <NEWS2ScoringScreen />}
        {tab === "escalations" && <EscalationsPanel />}
        {tab === "admit" && <AdmissionsPanel />}
        {tab === "rounds" && <WardRoundsPanel />}
        {tab === "obs" && <ObservationsPanel />}
        {tab === "transfer" && <TransfersPanel />}
        {tab === "resus" && <ResuscitationScreen />}
        {tab === "trauma" && <TraumaScreen />}
        {tab === "apgar" && <APGARScreen />}
        {tab === "theatre" && <TheatreProcedureScreen />}
        {tab === "vitals" && <VitalsMonitorScreen />}
        {tab === "handoff" && <ShiftHandoffScreen />}
        {tab === "alerts" && <WardAlertsScreen />}
        {tab === "clearance" && <DischargeClearanceScreen />}
      </ScrollView>
    </Screen>
  );
}

function CarePlansPanel() {
  const { activeEncounter } = useEncounterStore();
  const pid = activeEncounter?.patientId ?? "";
  const qc = useQueryClient();
  const { data: plans = [], isLoading } = useQuery({ queryKey: ["care-plans", pid], queryFn: () => fetchCarePlans(pid), enabled: !!pid });
  const [title, setTitle] = useState("");
  const [goalDesc, setGoalDesc] = useState("");
  const mutation = useMutation({
    mutationFn: () => createCarePlan({ subject_cpid: pid, journey_id: activeEncounter?.journeyId, encounter_id: activeEncounter?.id, title, goals: goalDesc ? [{ description: goalDesc, category: "CLINICAL" }] : [] }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["care-plans"] }); setTitle(""); setGoalDesc(""); },
  });
  if (isLoading) return <LoadingSpinner />;
  return (
    <View style={s.section}>
      <Text style={s.title}>Care Plans</Text>
      <TextInput style={s.input} placeholder="Plan title" value={title} onChangeText={setTitle} />
      <TextInput style={s.input} placeholder="Primary goal" value={goalDesc} onChangeText={setGoalDesc} />
      <Button title="Create Care Plan" onPress={() => mutation.mutate()} disabled={!title || mutation.isPending} />
      {(plans as Array<Record<string, unknown>>).map((p) => (
        <View key={String(p.id)} style={s.card}>
          <Text style={s.cardTitle}>{String(p.title)}</Text>
          <Badge label={String(p.status)} variant={p.status === "ACTIVE" ? "success" : "default"} />
          <Text style={s.meta}>Goals: {((p.goals as unknown[]) ?? []).length} · Interventions: {((p.interventions as unknown[]) ?? []).length}</Text>
        </View>
      ))}
      {(plans as unknown[]).length === 0 && <Text style={s.empty}>No active care plans</Text>}
    </View>
  );
}

function FluidBalancePanel() {
  const { activeEncounter } = useEncounterStore();
  const pid = activeEncounter?.patientId ?? "";
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ["fluid", pid], queryFn: () => fetchFluidBalance(pid), enabled: !!pid });
  const [form, setForm] = useState({ entryType: "INTAKE", category: "ORAL", volumeMl: "", description: "" });
  const mutation = useMutation({
    mutationFn: () => recordFluid({ patientId: pid, encounterId: activeEncounter?.id, ...form, volumeMl: parseInt(form.volumeMl) }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["fluid"] }); setForm({ ...form, volumeMl: "", description: "" }); },
  });
  const summary = (data as Record<string, unknown>)?.summary as Record<string, number> | undefined;
  const records = ((data as Record<string, unknown>)?.data ?? []) as Array<Record<string, unknown>>;
  const intakeRecords = records.filter((r) => r.entry_type === "INTAKE");
  const outputRecords = records.filter((r) => r.entry_type === "OUTPUT");

  // Category totals
  const intakeByCat: Record<string, number> = {};
  intakeRecords.forEach((r) => { const c = String(r.category); intakeByCat[c] = (intakeByCat[c] ?? 0) + (r.volume_ml as number); });
  const outputByCat: Record<string, number> = {};
  outputRecords.forEach((r) => { const c = String(r.category); outputByCat[c] = (outputByCat[c] ?? 0) + (r.volume_ml as number); });

  return (
    <View style={s.section}>
      <Text style={s.title}>Fluid Balance</Text>
      {summary && (
        <View style={s.statsRow}>
          <View style={[s.statCard, { borderLeftColor: "#3B82F6" }]}><Text style={{ fontSize: 12, color: "#3B82F6" }}>💧</Text><Text style={s.statNum}>{summary.totalIntake}ml</Text><Text style={s.statLabel}>Total Intake</Text></View>
          <View style={[s.statCard, { borderLeftColor: "#F59E0B" }]}><Text style={{ fontSize: 12, color: "#F59E0B" }}>📈</Text><Text style={s.statNum}>{summary.totalOutput}ml</Text><Text style={s.statLabel}>Total Output</Text></View>
          <View style={[s.statCard, { borderLeftColor: summary.balance >= 0 ? "#22C55E" : "#DC2626" }]}><Text style={{ fontSize: 12 }}>⚖️</Text><Text style={[s.statNum, { color: summary.balance >= 0 ? "#22C55E" : "#DC2626" }]}>{summary.balance > 0 ? "+" : ""}{summary.balance}ml</Text><Text style={s.statLabel}>Net Balance</Text></View>
        </View>
      )}

      {/* 2-column breakdown */}
      <View style={s.row}>
        <View style={{ flex: 1, gap: 4 }}>
          <Text style={{ fontSize: 13, fontWeight: "700", color: "#3B82F6" }}>INTAKE</Text>
          {Object.entries(intakeByCat).map(([cat, vol]) => (
            <View key={cat} style={{ flexDirection: "row", justifyContent: "space-between" }}>
              <Text style={{ fontSize: 12, color: "#374151" }}>{cat}</Text>
              <Text style={{ fontSize: 12, fontWeight: "600", color: "#111827", fontVariant: ["tabular-nums"] }}>{vol}ml</Text>
            </View>
          ))}
          {Object.keys(intakeByCat).length === 0 && <Text style={{ fontSize: 11, color: "#9CA3AF" }}>No intake recorded</Text>}
        </View>
        <View style={{ width: 1, backgroundColor: "#E5E7EB" }} />
        <View style={{ flex: 1, gap: 4 }}>
          <Text style={{ fontSize: 13, fontWeight: "700", color: "#F59E0B" }}>OUTPUT</Text>
          {Object.entries(outputByCat).map(([cat, vol]) => (
            <View key={cat} style={{ flexDirection: "row", justifyContent: "space-between" }}>
              <Text style={{ fontSize: 12, color: "#374151" }}>{cat}</Text>
              <Text style={{ fontSize: 12, fontWeight: "600", color: "#111827", fontVariant: ["tabular-nums"] }}>{vol}ml</Text>
            </View>
          ))}
          {Object.keys(outputByCat).length === 0 && <Text style={{ fontSize: 11, color: "#9CA3AF" }}>No output recorded</Text>}
        </View>
      </View>

      {/* Record entry */}
      <View style={s.row}>
        {["INTAKE", "OUTPUT"].map((t) => <Button key={t} title={t} size="sm" variant={form.entryType === t ? "default" : "outline"} onPress={() => setForm({ ...form, entryType: t })} />)}
      </View>
      <View style={s.row}>
        {(form.entryType === "INTAKE" ? ["ORAL", "IV", "FEEDS"] : ["URINE", "STOOL", "VOMIT", "DRAIN"]).map((c) =>
          <TouchableOpacity key={c} onPress={() => setForm({ ...form, category: c })} style={[s.chip, form.category === c && s.activeChip]}>
            <Text style={[s.chipText, form.category === c && s.activeChipText]}>{c}</Text>
          </TouchableOpacity>
        )}
      </View>
      <View style={s.row}>
        <TextInput style={[s.input, { flex: 2 }]} placeholder="Volume (ml)" value={form.volumeMl} onChangeText={(v) => setForm({ ...form, volumeMl: v })} keyboardType="numeric" />
        <TextInput style={[s.input, { flex: 3 }]} placeholder="Description (optional)" value={form.description} onChangeText={(v) => setForm({ ...form, description: v })} />
      </View>
      <Button title="Record Entry" onPress={() => mutation.mutate()} disabled={!form.volumeMl || mutation.isPending} />
    </View>
  );
}

function EmergencyPanel() {
  const qc = useQueryClient();
  const { data: activations = [], isLoading } = useQuery({ queryKey: ["emergencies"], queryFn: listEmergencyActivations });
  const [protocol, setProtocol] = useState("CODE_BLUE");
  const PROTOCOLS = ["CODE_BLUE", "RAPID_RESPONSE", "TRAUMA", "NEONATAL_RESUS", "OBSTETRIC_EMERGENCY", "ANAPHYLAXIS"];

  const activateMut = useMutation({
    mutationFn: () => activateEmergency({ protocolType: protocol }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["emergencies"] }); Alert.alert("ACTIVATED", `${protocol} protocol activated`); },
  });

  if (isLoading) return <LoadingSpinner />;
  return (
    <View style={s.section}>
      <TouchableOpacity style={s.sosButton} onPress={() => activateMut.mutate()}>
        <Text style={s.sosText}>ACTIVATE EMERGENCY</Text>
        <Text style={s.sosSubtext}>{protocol.replace(/_/g, " ")}</Text>
      </TouchableOpacity>
      <View style={s.row}>
        {PROTOCOLS.map((p) => <TouchableOpacity key={p} onPress={() => setProtocol(p)} style={[s.chip, protocol === p && s.activeChip]}>
          <Text style={[s.chipText, protocol === p && s.activeChipText]}>{p.replace(/_/g, " ")}</Text>
        </TouchableOpacity>)}
      </View>
      <Text style={s.title}>Recent Activations</Text>
      {(activations as Array<Record<string, unknown>>).map((a) => (
        <View key={String(a.id)} style={[s.card, { borderLeftColor: a.status === "ACTIVE" ? "#DC2626" : "#22C55E", borderLeftWidth: 4 }]}>
          <Text style={s.cardTitle}>{String(a.protocol_type ?? "").replace(/_/g, " ")}</Text>
          <Badge label={String(a.status)} variant={a.status === "ACTIVE" ? "destructive" : "success"} />
          <Text style={s.meta}>{a.activation_time ? new Date(a.activation_time as string).toLocaleString() : ""}</Text>
        </View>
      ))}
    </View>
  );
}

function EWSPanel() {
  const { activeEncounter } = useEncounterStore();
  const pid = activeEncounter?.patientId ?? "";
  const qc = useQueryClient();
  const { data: scores = [] } = useQuery({ queryKey: ["ews", pid], queryFn: () => fetchEWS(pid), enabled: !!pid });
  const [total, setTotal] = useState("");
  const mutation = useMutation({
    mutationFn: () => recordEWS({ patientId: pid, encounterId: activeEncounter?.id, totalScore: parseInt(total), scoreType: "NEWS2", components: "{}" }),
    onSuccess: (data) => { qc.invalidateQueries({ queryKey: ["ews"] }); Alert.alert("EWS Recorded", `Risk: ${data.riskLevel}${data.escalationRequired ? " — ESCALATION REQUIRED" : ""}`); setTotal(""); },
  });
  const riskColors: Record<string, string> = { NONE: "#22C55E", LOW: "#3B82F6", MEDIUM: "#F59E0B", HIGH: "#DC2626" };
  return (
    <View style={s.section}>
      <Text style={s.title}>Early Warning Score (NEWS2)</Text>
      <TextInput style={s.input} placeholder="Total score (0-20)" value={total} onChangeText={setTotal} keyboardType="numeric" />
      <Button title="Record EWS" onPress={() => mutation.mutate()} disabled={!total || mutation.isPending} />
      {(scores as Array<Record<string, unknown>>).map((sc) => (
        <View key={String(sc.id)} style={[s.card, { borderLeftColor: riskColors[String(sc.risk_level)] ?? "#6B7280", borderLeftWidth: 4 }]}>
          <Text style={s.cardTitle}>Score: {String(sc.total_score)} — {String(sc.risk_level)}</Text>
          {Boolean(sc.escalation_required) ? (
            <Badge label="ESCALATION REQUIRED" variant="destructive" />
          ) : null}
          <Text style={s.meta}>{sc.recorded_at ? new Date(sc.recorded_at as string).toLocaleString() : ""}</Text>
        </View>
      ))}
    </View>
  );
}

/**
 * EscalationsPanel — WS#6 deterioration escalations awaiting clinician response.
 * Scoped to a ward; acknowledge + respond are audited safety actions (BFF → inpatient-service).
 */
function EscalationsPanel() {
  const [wardId, setWardId] = useState("");
  const qc = useQueryClient();
  const { data: escalations = [], isLoading } = useQuery({
    queryKey: ["escalations", wardId],
    queryFn: () => listEscalations({ wardId }),
    enabled: !!wardId,
  });
  const ack = useMutation({
    mutationFn: (id: string) => acknowledgeEscalation(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["escalations"] }); Alert.alert("Acknowledged"); },
  });
  const respond = useMutation({
    mutationFn: (id: string) => respondEscalation(id, "Reviewed at bedside"),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["escalations"] }); Alert.alert("Responded"); },
  });
  const riskColors: Record<string, string> = { LOW: "#3B82F6", MEDIUM: "#F59E0B", HIGH: "#DC2626" };
  return (
    <View style={s.section}>
      <Text style={s.title}>Deterioration Escalations</Text>
      <TextInput style={s.input} placeholder="Ward ID" value={wardId} onChangeText={setWardId} />
      {!wardId ? (
        <Text style={s.meta}>Enter a ward ID to load open escalations.</Text>
      ) : isLoading ? (
        <LoadingSpinner />
      ) : (escalations as InpatientEscalation[]).length === 0 ? (
        <Text style={s.meta}>No open escalations on this ward.</Text>
      ) : (
        (escalations as InpatientEscalation[]).map((e) => {
          const id = e.escalation_id ?? e.id ?? "";
          return (
            <View key={id} style={[s.card, { borderLeftColor: riskColors[String(e.risk_level)] ?? "#6B7280", borderLeftWidth: 4 }]}>
              <Text style={s.cardTitle}>EWS {e.total_score} — {e.risk_level}</Text>
              <Text style={s.meta}>Patient {e.subject_cpid} · {e.status}</Text>
              {e.response_due_at ? <Text style={s.meta}>Response due {new Date(e.response_due_at).toLocaleString()}</Text> : null}
              <View style={s.row}>
                <Button title="Acknowledge" size="sm" variant="outline" onPress={() => ack.mutate(id)} disabled={ack.isPending} />
                <Button title="Respond" size="sm" onPress={() => respond.mutate(id)} disabled={respond.isPending} />
              </View>
            </View>
          );
        })
      )}
    </View>
  );
}

function AdmissionsPanel() {
  const qc = useQueryClient();
  const { data: admissions = [], isLoading } = useQuery({ queryKey: ["admissions"], queryFn: () => listAdmissions() });
  const [form, setForm] = useState({ patientId: "", admissionType: "EMERGENCY", admittingDiagnosis: "", dietOrders: "REGULAR", activityLevel: "BED_REST" });
  const mutation = useMutation({ mutationFn: () => createAdmission(form), onSuccess: () => { qc.invalidateQueries({ queryKey: ["admissions"] }); Alert.alert("Admitted"); } });
  if (isLoading) return <LoadingSpinner />;
  return (
    <View style={s.section}>
      <Text style={s.title}>New Admission</Text>
      <TextInput style={s.input} placeholder="Patient ID" value={form.patientId} onChangeText={(v) => setForm({ ...form, patientId: v })} />
      <TextInput style={s.input} placeholder="Admitting diagnosis" value={form.admittingDiagnosis} onChangeText={(v) => setForm({ ...form, admittingDiagnosis: v })} />
      <View style={s.row}>
        {["EMERGENCY", "ELECTIVE", "TRANSFER"].map((t) => <Button key={t} title={t} size="sm" variant={form.admissionType === t ? "default" : "outline"} onPress={() => setForm({ ...form, admissionType: t })} />)}
      </View>
      <Button title="Admit Patient" onPress={() => mutation.mutate()} disabled={!form.patientId || mutation.isPending} />
      <Text style={[s.title, { marginTop: 12 }]}>Active Admissions ({(admissions as unknown[]).length})</Text>
      {(admissions as Array<Record<string, unknown>>).map((a) => (
        <View key={String(a.id)} style={s.card}>
          <Text style={s.cardTitle}>{String(a.admitting_diagnosis ?? "Admission")}</Text>
          <Text style={s.meta}>{String(a.admission_type)} · {String(a.diet_orders)} · {String(a.activity_level)}</Text>
        </View>
      ))}
    </View>
  );
}

function WardRoundsPanel() {
  const [wardId, setWardId] = useState("");
  const qc = useQueryClient();
  const { data: rounds = [] } = useQuery({ queryKey: ["rounds", wardId], queryFn: () => listWardRounds(wardId), enabled: !!wardId });
  const mutation = useMutation({ mutationFn: () => startWardRound({ wardId, roundType: "MORNING" }), onSuccess: () => qc.invalidateQueries({ queryKey: ["rounds"] }) });
  return (
    <View style={s.section}>
      <Text style={s.title}>Ward Rounds</Text>
      <TextInput style={s.input} placeholder="Ward ID" value={wardId} onChangeText={setWardId} />
      <Button title="Start Ward Round" onPress={() => mutation.mutate()} disabled={!wardId || mutation.isPending} />
      {(rounds as Array<Record<string, unknown>>).map((r) => (
        <View key={String(r.id)} style={s.card}>
          <Text style={s.cardTitle}>{String(r.round_type)} Round</Text>
          <Badge label={String(r.status)} variant={r.status === "IN_PROGRESS" ? "warning" : "success"} />
          <Text style={s.meta}>Led by: {String(r.led_by ?? "—")}</Text>
        </View>
      ))}
    </View>
  );
}

function ObservationsPanel() {
  const { activeEncounter } = useEncounterStore();
  const pid = activeEncounter?.patientId ?? "";
  const qc = useQueryClient();
  const { data: obs = [] } = useQuery({ queryKey: ["observations", pid], queryFn: () => fetchObservations(pid), enabled: !!pid });
  const [params, setParams] = useState("");
  const mutation = useMutation({
    mutationFn: () => recordObservation({ patientId: pid, encounterId: activeEncounter?.id, chartType: "VITALS", parameters: params }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["observations"] }); setParams(""); },
  });
  return (
    <View style={s.section}>
      <Text style={s.title}>Observation Charts</Text>
      <TextInput style={[s.input, { minHeight: 60 }]} placeholder='Parameters JSON: {"hr": 80, "bp": "120/80"}' value={params} onChangeText={setParams} multiline />
      <Button title="Record Observation" onPress={() => mutation.mutate()} disabled={!params || mutation.isPending} />
      {(obs as Array<Record<string, unknown>>).map((o) => (
        <View key={String(o.id)} style={s.card}>
          <Text style={s.cardTitle}>{String(o.chart_type)}</Text>
          <Text style={s.meta}>{JSON.stringify(o.parameters)}</Text>
          <Text style={s.meta}>{o.recorded_at ? new Date(o.recorded_at as string).toLocaleString() : ""}</Text>
        </View>
      ))}
    </View>
  );
}

function TransfersPanel() {
  const qc = useQueryClient();
  const { data: transfers = [] } = useQuery({ queryKey: ["transfers"], queryFn: () => listTransfers() });
  const [form, setForm] = useState({ patientId: "", toWardId: "", reason: "CLINICAL", clinicalNotes: "" });
  const mutation = useMutation({ mutationFn: () => requestTransfer(form), onSuccess: () => { qc.invalidateQueries({ queryKey: ["transfers"] }); Alert.alert("Transfer Requested"); } });
  return (
    <View style={s.section}>
      <Text style={s.title}>Patient Transfers</Text>
      <TextInput style={s.input} placeholder="Patient ID" value={form.patientId} onChangeText={(v) => setForm({ ...form, patientId: v })} />
      <TextInput style={s.input} placeholder="To Ward ID" value={form.toWardId} onChangeText={(v) => setForm({ ...form, toWardId: v })} />
      <View style={s.row}>
        {["CLINICAL", "ADMINISTRATIVE", "CAPACITY"].map((r) => <Button key={r} title={r} size="sm" variant={form.reason === r ? "default" : "outline"} onPress={() => setForm({ ...form, reason: r })} />)}
      </View>
      <TextInput style={s.input} placeholder="Clinical notes" value={form.clinicalNotes} onChangeText={(v) => setForm({ ...form, clinicalNotes: v })} />
      <Button title="Request Transfer" onPress={() => mutation.mutate()} disabled={!form.patientId || !form.toWardId || mutation.isPending} />
      {(transfers as Array<Record<string, unknown>>).map((t) => (
        <View key={String(t.id)} style={s.card}>
          <Badge label={String(t.status)} variant={t.status === "COMPLETED" ? "success" : "warning"} />
          <Text style={s.cardTitle}>{String(t.reason)} transfer</Text>
          <Text style={s.meta}>{String(t.transfer_type)} · {t.requested_at ? new Date(t.requested_at as string).toLocaleString() : ""}</Text>
        </View>
      ))}
    </View>
  );
}

const s = StyleSheet.create({
  tabBar: { borderBottomWidth: 1, borderBottomColor: "#E5E7EB", paddingHorizontal: 8 },
  tabBarContent: { flexDirection: "row", gap: 4, paddingVertical: 4 },
  tab: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 14, backgroundColor: "#F3F4F6" },
  activeTab: { backgroundColor: "#7C3AED" },
  tabText: { fontSize: 12, color: "#6B7280", fontWeight: "500" },
  activeTabText: { color: "#FFF" },
  content: { flex: 1 },
  pad: { padding: 16, paddingBottom: 32 },
  section: { gap: 12 },
  title: { fontSize: 16, fontWeight: "700", color: "#111827" },
  input: { borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 8, padding: 10, fontSize: 14 },
  row: { flexDirection: "row", gap: 6, flexWrap: "wrap" },
  chip: { paddingHorizontal: 10, paddingVertical: 5, borderRadius: 14, backgroundColor: "#E5E7EB" },
  activeChip: { backgroundColor: "#7C3AED" },
  chipText: { fontSize: 11, color: "#6B7280" },
  activeChipText: { color: "#FFF" },
  card: { backgroundColor: "#F9FAFB", borderRadius: 12, padding: 12, gap: 4 },
  cardTitle: { fontSize: 14, fontWeight: "600", color: "#111827" },
  meta: { fontSize: 12, color: "#6B7280" },
  empty: { fontSize: 13, color: "#9CA3AF", textAlign: "center", paddingVertical: 20 },
  statsRow: { flexDirection: "row", gap: 8 },
  statCard: { flex: 1, backgroundColor: "#FFF", borderRadius: 8, padding: 10, borderLeftWidth: 3, alignItems: "center" },
  statNum: { fontSize: 18, fontWeight: "700", color: "#111827" },
  statLabel: { fontSize: 10, color: "#6B7280" },
  sosButton: { backgroundColor: "#DC2626", borderRadius: 16, padding: 24, alignItems: "center", gap: 4 },
  sosText: { color: "#FFF", fontSize: 20, fontWeight: "900" },
  sosSubtext: { color: "#FCA5A5", fontSize: 13 },
});
