/**
 * EdVisitScreen — mobile ED casualty wizard (arrival → triage → treatment → disposition).
 */
import React, { useCallback, useState } from "react";
import { View, Text, ScrollView, TextInput, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Screen, Header, Button, Badge, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  listEdVisits,
  openEdVisit,
  getEdVisit,
  edTriage,
  edStartEncounter,
  edDisposition,
  scoreEdTriage,
} from "../../services/edService";
import { searchICD11, type ICD11SearchResult } from "../../services/diagnosisService";

const STEPS = ["Trackboard", "Triage", "Treatment", "Disposition"] as const;
const ACUITY = [
  { level: 1, label: "Red" },
  { level: 2, label: "Orange" },
  { level: 3, label: "Yellow" },
  { level: 4, label: "Green" },
  { level: 5, label: "Blue" },
];
const DISC_KEYS = [
  { id: "requires_resuscitation", label: "Life-saving intervention" },
  { id: "airway_compromise", label: "Airway compromise" },
  { id: "altered_consciousness", label: "Altered consciousness" },
  { id: "severe_pain_distress", label: "Severe pain/distress" },
] as const;

export function EdVisitScreen() {
  const qc = useQueryClient();
  const [step, setStep] = useState(0);
  const [visitId, setVisitId] = useState<string | null>(null);
  const [patientCpid, setPatientCpid] = useState("");
  const [complaint, setComplaint] = useState("");
  const [acuity, setAcuity] = useState(3);
  const [pain, setPain] = useState("5");
  const [disc, setDisc] = useState<Record<string, boolean>>({});
  const [dxQuery, setDxQuery] = useState("");
  const [dxResults, setDxResults] = useState<ICD11SearchResult[]>([]);
  const [dx, setDx] = useState<ICD11SearchResult | null>(null);
  const [disposition, setDisposition] = useState("DISCHARGE");

  const { data: visits, isLoading: loadingVisits } = useQuery({
    queryKey: ["ed-visits"],
    queryFn: () => listEdVisits(),
  });

  const { data: visit, isLoading: loadingVisit } = useQuery({
    queryKey: ["ed-visit", visitId],
    queryFn: async () => {
      const res = await getEdVisit(visitId!);
      return res.data as Record<string, unknown>;
    },
    enabled: !!visitId,
  });

  const openMutation = useMutation({
    mutationFn: () =>
      openEdVisit({
        patientCpid: patientCpid || "MOBILE-ED",
        chiefComplaint: complaint || "Undifferentiated",
        arrivalMode: "WALK_IN",
      }),
    onSuccess: (res) => {
      const id = String((res.data as { visit_id?: string })?.visit_id ?? "");
      if (id) {
        setVisitId(id);
        setStep(1);
        void qc.invalidateQueries({ queryKey: ["ed-visits"] });
      }
    },
    onError: () => Alert.alert("Error", "Could not open ED visit"),
  });

  const triageMutation = useMutation({
    mutationFn: () =>
      edTriage(visitId!, {
        acuity,
        triageSystem: "ESI",
        autoAcuity: true,
        applyDiscriminators: true,
        chiefComplaint: complaint,
        painScore: Number(pain),
        discriminators: { ...disc, resources_needed: 1, pain_score: Number(pain) },
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["ed-visit", visitId] });
      setStep(2);
    },
    onError: () => Alert.alert("Error", "Triage failed"),
  });

  const encounterMutation = useMutation({
    mutationFn: () => edStartEncounter(visitId!),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["ed-visit", visitId] }),
    onError: () => Alert.alert("Error", "Could not start encounter"),
  });

  const dispositionMutation = useMutation({
    mutationFn: () =>
      edDisposition(visitId!, {
        dispositionType: disposition,
        primaryDiagnosisCode: dx?.code,
        primaryDiagnosisDisplay: dx?.description,
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["ed-visit", visitId] });
      Alert.alert("Complete", "ED disposition recorded");
    },
    onError: () => Alert.alert("Error", "Disposition failed"),
  });

  const previewScore = useCallback(async () => {
    try {
      const res = await scoreEdTriage({
        scoreBoth: true,
        painScore: Number(pain),
        discriminators: { ...disc, pain_score: Number(pain), resources_needed: 1 },
      });
      const data = res.data as Record<string, unknown>;
      const recommended = Number(data?.recommended_acuity);
      if (recommended >= 1 && recommended <= 5) setAcuity(recommended);
    } catch {
      /* non-blocking */
    }
  }, [disc, pain]);

  const searchDx = async () => {
    if (!dxQuery.trim()) return;
    const results = await searchICD11(dxQuery);
    setDxResults(results);
  };

  const status = String(visit?.status ?? "");
  const visitList = (visits ?? []) as Record<string, unknown>[];

  return (
    <Screen>
      <Header title="ED Casualty" subtitle="Full visit wizard" />
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={s.tabBar} contentContainerStyle={s.tabPad}>
        {STEPS.map((label, i) => (
          <TouchableOpacity key={label} onPress={() => setStep(i)} style={[s.tab, step === i && s.activeTab]}>
            <Text style={[s.tabText, step === i && s.activeTabText]}>{label}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      <ScrollView style={s.content} contentContainerStyle={s.pad}>
        {step === 0 && (
          <View style={s.section}>
            <Text style={s.h2}>ED trackboard</Text>
            {loadingVisits ? <LoadingSpinner /> : null}
            {visitList.map((v) => (
              <TouchableOpacity
                key={String(v.visit_id)}
                style={s.card}
                onPress={() => {
                  setVisitId(String(v.visit_id));
                  setStep(1);
                }}
              >
                <Text style={s.cardTitle}>{String(v.patient_cpid)}</Text>
                <Badge variant="outline">{String(v.status)}</Badge>
                <Text style={s.muted}>{String(v.chief_complaint ?? "")}</Text>
              </TouchableOpacity>
            ))}
            <Text style={s.label}>Patient CPID</Text>
            <TextInput value={patientCpid} onChangeText={setPatientCpid} style={s.input} placeholder="CPID" />
            <Text style={s.label}>Chief complaint</Text>
            <TextInput value={complaint} onChangeText={setComplaint} style={s.input} placeholder="Complaint" />
            <Button title="Register arrival" onPress={() => openMutation.mutate()} loading={openMutation.isPending} />
          </View>
        )}

        {step === 1 && visitId && (
          <View style={s.section}>
            {loadingVisit ? <LoadingSpinner /> : null}
            <Text style={s.h2}>Structured triage</Text>
            <Text style={s.muted}>Visit {visitId} — {status}</Text>
            <View style={s.row}>
              {ACUITY.map((a) => (
                <TouchableOpacity key={a.level} onPress={() => setAcuity(a.level)} style={[s.chip, acuity === a.level && s.chipOn]}>
                  <Text style={s.chipText}>{a.label}</Text>
                </TouchableOpacity>
              ))}
            </View>
            <Text style={s.label}>Pain 0–10</Text>
            <TextInput value={pain} onChangeText={setPain} keyboardType="numeric" style={s.input} />
            {DISC_KEYS.map((d) => (
              <TouchableOpacity key={d.id} style={s.checkRow} onPress={() => setDisc({ ...disc, [d.id]: !disc[d.id] })}>
                <Text>{disc[d.id] ? "☑" : "☐"} {d.label}</Text>
              </TouchableOpacity>
            ))}
            <Button title="Preview ESI/MTS score" variant="outline" onPress={previewScore} />
            <Button title="Complete triage" onPress={() => triageMutation.mutate()} loading={triageMutation.isPending} />
          </View>
        )}

        {step === 2 && visitId && (
          <View style={s.section}>
            <Text style={s.h2}>Treatment</Text>
            <Text style={s.muted}>Status: {status}</Text>
            <Button
              title={status === "IN_TREATMENT" ? "Encounter active" : "Start ED encounter"}
              onPress={() => encounterMutation.mutate()}
              loading={encounterMutation.isPending}
              disabled={status === "IN_TREATMENT"}
            />
            <Button title="Go to disposition" variant="outline" onPress={() => setStep(3)} />
          </View>
        )}

        {step === 3 && visitId && (
          <View style={s.section}>
            <Text style={s.h2}>Disposition & ICD-11</Text>
            <View style={s.row}>
              {["DISCHARGE", "ADMIT", "TRANSFER", "REFER"].map((d) => (
                <TouchableOpacity key={d} onPress={() => setDisposition(d)} style={[s.chip, disposition === d && s.chipOn]}>
                  <Text style={s.chipText}>{d}</Text>
                </TouchableOpacity>
              ))}
            </View>
            <TextInput value={dxQuery} onChangeText={setDxQuery} style={s.input} placeholder="Search ICD-11" />
            <Button title="Search ICD-11" variant="outline" onPress={searchDx} />
            {dxResults.map((r) => (
              <TouchableOpacity key={r.code} style={s.card} onPress={() => setDx(r)}>
                <Text style={s.cardTitle}>{r.code} — {r.description}</Text>
              </TouchableOpacity>
            ))}
            {dx && <Text style={s.muted}>Selected: {dx.code} {dx.description}</Text>}
            <Button
              title="Complete disposition"
              onPress={() => dispositionMutation.mutate()}
              loading={dispositionMutation.isPending}
              disabled={!dx?.code}
            />
          </View>
        )}
      </ScrollView>
    </Screen>
  );
}

const s = StyleSheet.create({
  tabBar: { maxHeight: 44, borderBottomWidth: 1, borderColor: colors.gray[200] },
  tabPad: { paddingHorizontal: 8, alignItems: "center" },
  tab: { paddingHorizontal: 12, paddingVertical: 10, marginRight: 4 },
  activeTab: { borderBottomWidth: 2, borderColor: colors.ui.error.main },
  tabText: { fontSize: 13, color: colors.gray[500] },
  activeTabText: { color: colors.ui.error.main, fontWeight: "600" },
  content: { flex: 1 },
  pad: { padding: 16, gap: 8 },
  section: { gap: 10 },
  h2: { fontSize: 16, fontWeight: "600" },
  label: { fontSize: 13, color: colors.gray[700] },
  muted: { fontSize: 12, color: colors.gray[500] },
  input: { borderWidth: 1, borderColor: colors.gray[300], borderRadius: 8, padding: 10, fontSize: 14 },
  row: { flexDirection: "row", flexWrap: "wrap", gap: 6 },
  chip: { paddingHorizontal: 10, paddingVertical: 6, borderRadius: 16, backgroundColor: colors.gray[100] },
  chipOn: { backgroundColor: colors.ui.error.light },
  chipText: { fontSize: 12 },
  card: { borderWidth: 1, borderColor: colors.gray[200], borderRadius: 8, padding: 10, marginBottom: 6 },
  cardTitle: { fontWeight: "600", fontSize: 14 },
  checkRow: { paddingVertical: 4 },
});
