/**
 * MentalHealthReferralScreen — assessment, risk, safety plan, involuntary, restraint, admission, follow-up.
 */
import React, { useState } from "react";
import { View, Text, ScrollView, TextInput, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Button, LoadingSpinner, ErrorState, colors } from "@impilo/mobile-design-system";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getMentalHealthReferral,
  listAssessments,
  recordAssessment,
  listRiskFormulations,
  recordRiskFormulation,
  listSafetyPlans,
  recordSafetyPlan,
  listInvoluntaryEpisodes,
  openInvoluntaryEpisode,
  closeInvoluntaryEpisode,
  listRestraintEvents,
  recordRestraintEvent,
  endRestraintEvent,
  listAdmissionRequests,
  raiseAdmissionRequest,
  listFollowups,
  scheduleFollowup,
} from "../../services/mentalHealthService";

type Tab = "assessment" | "risk" | "safety" | "involuntary" | "restraint" | "admission" | "followup";

const TABS: { id: Tab; label: string }[] = [
  { id: "assessment", label: "Assessment" },
  { id: "risk", label: "Risk" },
  { id: "safety", label: "Safety" },
  { id: "involuntary", label: "Involuntary" },
  { id: "restraint", label: "Restraint" },
  { id: "admission", label: "Admission" },
  { id: "followup", label: "Follow-up" },
];

export function MentalHealthReferralScreen({ referralId, onBack }: { referralId: string; onBack: () => void }) {
  const [tab, setTab] = useState<Tab>("assessment");
  const [notes, setNotes] = useState("");
  const [riskSummary, setRiskSummary] = useState("");
  const [warningSigns, setWarningSigns] = useState("");
  const [legalBasis, setLegalBasis] = useState("");
  const [restraintReason, setRestraintReason] = useState("");
  const [admissionReason, setAdmissionReason] = useState("");
  const [followupAt, setFollowupAt] = useState("");
  const qc = useQueryClient();
  const actorId = "provider-mobile";

  const invalidate = () => void qc.invalidateQueries({ queryKey: ["mh", referralId] });

  const { data: referral, isError, refetch } = useQuery({
    queryKey: ["mh-referral", referralId],
    queryFn: () => getMentalHealthReferral(referralId),
  });

  const { data: assessments, isLoading: loadingAssessments } = useQuery({
    queryKey: ["mh", referralId, "assessments"],
    queryFn: () => listAssessments(referralId),
  });

  const latestAssessmentId = String((assessments ?? [])[0]?.id ?? "");

  const { data: risks } = useQuery({
    queryKey: ["mh", referralId, "risk", latestAssessmentId],
    queryFn: () => listRiskFormulations(latestAssessmentId),
    enabled: !!latestAssessmentId,
  });

  const { data: safetyPlans } = useQuery({
    queryKey: ["mh", referralId, "safety"],
    queryFn: () => listSafetyPlans(referralId),
  });

  const { data: involuntary } = useQuery({
    queryKey: ["mh", referralId, "involuntary"],
    queryFn: () => listInvoluntaryEpisodes(referralId),
  });

  const { data: restraints } = useQuery({
    queryKey: ["mh", referralId, "restraint"],
    queryFn: () => listRestraintEvents(referralId),
  });

  const { data: admissions } = useQuery({
    queryKey: ["mh", referralId, "admission"],
    queryFn: () => listAdmissionRequests(referralId),
  });

  const { data: followups } = useQuery({
    queryKey: ["mh", referralId, "followup"],
    queryFn: () => listFollowups(referralId),
  });

  const assessmentMut = useMutation({
    mutationFn: () => recordAssessment(referralId, { assessmentType: "INITIAL", mentalStateExam: notes, assessedBy: actorId }),
    onSuccess: () => { invalidate(); setNotes(""); Alert.alert("Assessment recorded"); },
    onError: () => Alert.alert("Error", "Assessment failed — service may be unavailable."),
  });

  const riskMut = useMutation({
    mutationFn: () =>
      recordRiskFormulation(latestAssessmentId, {
        riskToSelf: "MODERATE",
        riskToOthers: "LOW",
        riskSummary,
        formulatedBy: actorId,
      }),
    onSuccess: () => { invalidate(); setRiskSummary(""); Alert.alert("Risk formulation recorded"); },
    onError: () => Alert.alert("Error", "Risk formulation failed"),
  });

  const safetyMut = useMutation({
    mutationFn: () => recordSafetyPlan(referralId, { warningSigns, createdBy: actorId }),
    onSuccess: () => { invalidate(); setWarningSigns(""); Alert.alert("Safety plan recorded"); },
    onError: () => Alert.alert("Error", "Safety plan failed"),
  });

  const involuntaryMut = useMutation({
    mutationFn: () => openInvoluntaryEpisode(referralId, { legalBasis, authorisedBy: actorId }),
    onSuccess: () => { invalidate(); Alert.alert("Involuntary episode opened"); },
    onError: () => Alert.alert("Error", "Involuntary episode failed"),
  });

  const restraintMut = useMutation({
    mutationFn: () =>
      recordRestraintEvent(referralId, {
        restraintType: "PHYSICAL",
        reason: restraintReason,
        deEscalationAttempted: "Yes",
        initiatedBy: actorId,
      }),
    onSuccess: () => { invalidate(); setRestraintReason(""); Alert.alert("Restraint event recorded"); },
    onError: () => Alert.alert("Error", "Restraint record failed"),
  });

  const admissionMut = useMutation({
    mutationFn: () => raiseAdmissionRequest(referralId, { reason: admissionReason, requestedBy: actorId }),
    onSuccess: () => { invalidate(); setAdmissionReason(""); Alert.alert("Admission request raised"); },
    onError: () => Alert.alert("Error", "Admission request failed"),
  });

  const followupMut = useMutation({
    mutationFn: () => scheduleFollowup(referralId, { scheduledAt: followupAt, followupType: "CLINIC_REVIEW", scheduledBy: actorId }),
    onSuccess: () => { invalidate(); setFollowupAt(""); Alert.alert("Follow-up scheduled"); },
    onError: () => Alert.alert("Error", "Follow-up failed"),
  });

  return (
    <View style={s.flex}>
      <View style={s.topBar}>
        <TouchableOpacity onPress={onBack}><Text style={s.back}>← Queue</Text></TouchableOpacity>
        <Text style={s.title}>MH {referralId}</Text>
      </View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={s.tabBar}>
        {TABS.map((t) => (
          <TouchableOpacity key={t.id} onPress={() => setTab(t.id)} style={[s.tab, tab === t.id && s.activeTab]}>
            <Text style={[s.tabText, tab === t.id && s.activeTabText]}>{t.label}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
      <ScrollView contentContainerStyle={s.pad}>
        {isError ? <ErrorState message="Could not read referral" onRetry={() => void refetch()} /> : null}
        {referral ? <Text style={s.muted}>Status {String(referral.status)} · CPID {String(referral.subject_cpid ?? "—")}</Text> : null}
        {loadingAssessments ? <LoadingSpinner /> : null}

        {tab === "assessment" ? (
          <View style={s.section}>
            <TextInput value={notes} onChangeText={setNotes} style={s.input} placeholder="Mental state exam notes" multiline />
            <Button title="Record assessment" onPress={() => assessmentMut.mutate()} disabled={!notes.trim()} />
            {(assessments ?? []).map((a) => (
              <Text key={String(a.id)} style={s.muted}>{String(a.assessment_type)} — {String(a.assessed_at ?? "")}</Text>
            ))}
          </View>
        ) : null}

        {tab === "risk" ? (
          <View style={s.section}>
            {!latestAssessmentId ? <Text style={s.muted}>Record an assessment first.</Text> : null}
            <TextInput value={riskSummary} onChangeText={setRiskSummary} style={s.input} placeholder="Risk summary" multiline />
            <Button title="Record risk formulation" onPress={() => riskMut.mutate()} disabled={!latestAssessmentId || !riskSummary.trim()} />
            {(risks ?? []).map((r) => <Text key={String(r.id)} style={s.muted}>{String(r.risk_summary)}</Text>)}
          </View>
        ) : null}

        {tab === "safety" ? (
          <View style={s.section}>
            <TextInput value={warningSigns} onChangeText={setWarningSigns} style={s.input} placeholder="Warning signs" multiline />
            <Button title="Record safety plan" onPress={() => safetyMut.mutate()} />
            {(safetyPlans ?? []).map((p) => <Text key={String(p.id)} style={s.muted}>{String(p.warning_signs)}</Text>)}
          </View>
        ) : null}

        {tab === "involuntary" ? (
          <View style={s.section}>
            <TextInput value={legalBasis} onChangeText={setLegalBasis} style={s.input} placeholder="Legal basis" />
            <Button title="Open involuntary episode" onPress={() => involuntaryMut.mutate()} disabled={!legalBasis.trim()} />
            {(involuntary ?? []).map((ep) => (
              <View key={String(ep.id)} style={s.row}>
                <Text style={s.muted}>{String(ep.status)} — {String(ep.legal_basis)}</Text>
                {String(ep.status) === "OPEN" && ep.id ? (
                  <Button title="Close" size="sm" variant="outline" onPress={() => closeInvoluntaryEpisode(String(ep.id), "Clinical review complete").then(invalidate)} />
                ) : null}
              </View>
            ))}
          </View>
        ) : null}

        {tab === "restraint" ? (
          <View style={s.section}>
            <TextInput value={restraintReason} onChangeText={setRestraintReason} style={s.input} placeholder="Restraint reason" />
            <Button title="Record restraint" onPress={() => restraintMut.mutate()} disabled={!restraintReason.trim()} />
            {(restraints ?? []).map((ev) => (
              <View key={String(ev.id)} style={s.row}>
                <Text style={s.muted}>{String(ev.restraint_type)} — {String(ev.reason)}</Text>
                {!ev.ended_at && ev.id ? (
                  <Button title="End" size="sm" variant="outline" onPress={() => endRestraintEvent(String(ev.id)).then(invalidate)} />
                ) : null}
              </View>
            ))}
          </View>
        ) : null}

        {tab === "admission" ? (
          <View style={s.section}>
            <TextInput value={admissionReason} onChangeText={setAdmissionReason} style={s.input} placeholder="Admission reason" />
            <Button title="Raise admission request" onPress={() => admissionMut.mutate()} disabled={!admissionReason.trim()} />
            {(admissions ?? []).map((a) => <Text key={String(a.id)} style={s.muted}>{String(a.status)} — {String(a.reason)}</Text>)}
          </View>
        ) : null}

        {tab === "followup" ? (
          <View style={s.section}>
            <TextInput value={followupAt} onChangeText={setFollowupAt} style={s.input} placeholder="ISO datetime e.g. 2026-08-01T09:00:00Z" />
            <Button title="Schedule follow-up" onPress={() => followupMut.mutate()} disabled={!followupAt.trim()} />
            {(followups ?? []).map((f) => <Text key={String(f.id)} style={s.muted}>{String(f.followup_type)} @ {String(f.scheduled_at)}</Text>)}
          </View>
        ) : null}
      </ScrollView>
    </View>
  );
}

const s = StyleSheet.create({
  flex: { flex: 1 },
  topBar: { flexDirection: "row", alignItems: "center", gap: 8, paddingHorizontal: 16, paddingVertical: 8, borderBottomWidth: 1, borderColor: colors.gray[200] },
  back: { color: "#7C3AED", fontWeight: "600", fontSize: 13 },
  title: { flex: 1, fontSize: 15, fontWeight: "700" },
  tabBar: { maxHeight: 40, borderBottomWidth: 1, borderColor: colors.gray[200] },
  tab: { paddingHorizontal: 10, paddingVertical: 8 },
  activeTab: { borderBottomWidth: 2, borderColor: "#7C3AED" },
  tabText: { fontSize: 11, color: colors.gray[500] },
  activeTabText: { color: "#7C3AED", fontWeight: "600" },
  pad: { padding: 16, gap: 10, paddingBottom: 32 },
  section: { gap: 10 },
  muted: { fontSize: 12, color: colors.gray[500] },
  input: { borderWidth: 1, borderColor: colors.gray[300], borderRadius: 8, padding: 10, fontSize: 14, minHeight: 60, textAlignVertical: "top" },
  row: { flexDirection: "row", flexWrap: "wrap", gap: 8, alignItems: "center" },
});
