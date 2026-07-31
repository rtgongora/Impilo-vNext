/**
 * TheatreProcedureScreen — Procedure-episode wizard (pre-op, consent, WHO checklist, intra-op).
 *
 * Kept as the episode wizard; theatre board/case actions live on TheatreQueueScreen /
 * TheatreCaseScreen. Optional episodeId opens a specific episode (e.g. linked from a theatre case —
 * case id IS the procedure episode id).
 */
import React, { useState } from "react";
import { View, Text, ScrollView, TextInput, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Screen, Header, Button, Badge, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEncounterStore } from "../../stores/encounterStore";
import {
  listProcedureEpisodes, createProcedureEpisode, getProcedureEpisode,
  submitProcedurePreop, completeChecklistPhase, requestProcedureConsent, provideProcedureConsentExplanation,
  verifyProcedureConsentIdentity, grantProcedureConsent, syncProcedureConsent, startProcedure,
  recordIntraop, enterPacu, completePostop, completeProcedureEpisode,
} from "../../services/procedureService";

type Phase = "SIGN_IN" | "TIME_OUT" | "SIGN_OUT";

type Props = {
  /** When set (theatre case → episode), open this episode directly. */
  episodeId?: string;
  onBack?: () => void;
};

export function TheatreProcedureScreen({ episodeId: initialEpisodeId, onBack }: Props = {}) {
  const { activeEncounter } = useEncounterStore();
  const pid = activeEncounter?.patientId ?? "";
  const qc = useQueryClient();
  const [episodeId, setEpisodeId] = useState<string | null>(initialEpisodeId ?? null);
  const [asaClass, setAsaClass] = useState("II");
  const [mallampati, setMallampati] = useState("II");
  const [notes, setNotes] = useState("");
  const [note, setNote] = useState("");

  const { data: episodes = [], isLoading } = useQuery({
    queryKey: ["procedure-episodes", pid],
    queryFn: () => listProcedureEpisodes(pid),
    enabled: !!pid && !initialEpisodeId,
  });

  const { data: episode, refetch, isLoading: episodeLoading, isError: episodeError } = useQuery({
    queryKey: ["procedure-episode", episodeId],
    queryFn: () => getProcedureEpisode(episodeId!),
    enabled: !!episodeId,
  });

  const createMut = useMutation({
    mutationFn: () => createProcedureEpisode({
      patientId: pid,
      procedureName: "Theatre procedure",
      encounterId: activeEncounter?.id,
      scheduledAt: new Date().toISOString(),
    }),
    onSuccess: (data) => {
      setEpisodeId(data.id);
      qc.invalidateQueries({ queryKey: ["procedure-episodes"] });
    },
  });

  const refresh = () => { void refetch(); qc.invalidateQueries({ queryKey: ["procedure-episodes"] }); };

  const active = episode as Record<string, unknown> | undefined;
  const checklist = (active?.checklist as Array<Record<string, unknown>>) ?? [];
  const status = String(active?.status ?? "");

  const completePhase = async (phase: Phase) => {
    if (!episodeId) return;
    const items = checklist.filter((c) => c.phase === phase && !c.completed);
    await completeChecklistPhase(episodeId, phase, items.map((i) => String(i.id)));
    refresh();
  };

  // Linked from a theatre case: episode id is known; encounter is optional.
  if (!pid && !initialEpisodeId) {
    return <Screen><Header title="Theatre Procedure" /><Text style={s.hint}>Select an active patient encounter first.</Text></Screen>;
  }

  if (isLoading || (episodeId && episodeLoading)) {
    return <Screen><Header title="Theatre Procedure" /><LoadingSpinner /></Screen>;
  }

  if (episodeId && episodeError) {
    return (
      <Screen>
        <Header title="Theatre Procedure" />
        {onBack ? (
          <TouchableOpacity onPress={onBack} style={{ padding: 16 }}>
            <Text style={s.back}>← Back to theatre case</Text>
          </TouchableOpacity>
        ) : null}
        <Text testID="theatre-procedure-error" style={s.hint}>
          Procedure episode could not be loaded. Do not assume pre-op or consent state.
        </Text>
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="Theatre Procedure" />
      <ScrollView style={s.wrap} contentContainerStyle={s.pad}>
        {onBack ? (
          <TouchableOpacity onPress={onBack}>
            <Text style={s.back}>← Back to theatre case</Text>
          </TouchableOpacity>
        ) : null}
        {!episodeId ? (
          <View style={s.section}>
            <Text style={s.title}>Procedure cases</Text>
            {(episodes as Array<Record<string, unknown>>).map((ep) => (
              <TouchableOpacity key={String(ep.id)} style={s.card} onPress={() => setEpisodeId(String(ep.id))}>
                <Text style={s.cardTitle}>{String(ep.procedure_name)}</Text>
                <Badge label={String(ep.status)} variant="info" />
              </TouchableOpacity>
            ))}
            <Button title="New procedure case" onPress={() => createMut.mutate()} disabled={createMut.isPending} />
          </View>
        ) : (
          <View style={s.section}>
            <TouchableOpacity onPress={() => setEpisodeId(null)}><Text style={s.back}>← All cases</Text></TouchableOpacity>
            <Text style={s.title}>{String(active?.procedure_name ?? "Procedure")}</Text>
            <Badge label={status} variant={status === "COMPLETED" ? "success" : "warning"} />

            <Text style={s.sub}>1. Nursing pre-op</Text>
            <Button title="Submit nursing pre-op" variant="secondary" onPress={async () => {
              await submitProcedurePreop(episodeId, { assessmentType: "NURSING", fastingVerified: true, allergiesReviewed: true, assessedBy: "nurse-mobile" });
              refresh();
            }} />

            <Text style={s.sub}>2. Anaesthesia pre-op (ASA {asaClass}, Mallampati {mallampati})</Text>
            <View style={s.asaRow}>
              {["I", "II", "III", "IV", "V"].map((c) => (
                <TouchableOpacity key={c} onPress={() => setAsaClass(c)} style={[s.asaBtn, asaClass === c && s.asaActive]}>
                  <Text>{c}</Text>
                </TouchableOpacity>
              ))}
            </View>
            <View style={s.asaRow}>
              {["I", "II", "III", "IV"].map((c) => (
                <TouchableOpacity key={c} onPress={() => setMallampati(c)} style={[s.asaBtn, mallampati === c && s.asaActive]}>
                  <Text>M{c}</Text>
                </TouchableOpacity>
              ))}
            </View>
            <TextInput style={s.input} value={notes} onChangeText={setNotes} placeholder="Anaesthetist notes…" multiline />
            <Button title="Anaesthetist clearance + scores" variant="secondary" onPress={async () => {
              await submitProcedurePreop(episodeId, {
                assessmentType: "ANAESTHESIA",
                asaClass,
                mallampatiClass: mallampati,
                anaesthetistNotes: notes || undefined,
                clearedForTheatre: true,
                assessedBy: "anaesthetist-mobile",
                scores: [{ scoreType: "APFEL", components: { postopOpioids: true } }],
              });
              refresh();
            }} />

            <Text style={s.sub}>3. MVUMO consent ({String(active?.consent_status ?? "PENDING")})</Text>
            {active?.consent_status !== "GRANTED" ? (
              <>
                {!active?.mvumo_consent_request_id ? (
                  <Button title="Create MVUMO consent request" variant="secondary" onPress={async () => {
                    await requestProcedureConsent(episodeId);
                    refresh();
                  }} />
                ) : (
                  <>
                    <Button title="1. Record explanation" variant="secondary" onPress={async () => {
                      await provideProcedureConsentExplanation(episodeId, {
                        explanationNotes: "Procedure risks and benefits explained.",
                        actualMethod: "FACILITY_TABLET",
                      });
                      refresh();
                    }} />
                    <Button title="2. Verify identity" variant="secondary" onPress={async () => {
                      await verifyProcedureConsentIdentity(episodeId, {
                        identityVerified: true,
                        actualMethod: "FACILITY_WITNESSED",
                      });
                      refresh();
                    }} />
                    <Button title="3. Grant consent (Tshepo)" onPress={async () => {
                      await grantProcedureConsent(episodeId, {
                        actualAssurance: "L3_WITNESSED_HIGH_ASSURANCE",
                        grantedByRef: "Provider/theatre-clinician",
                      });
                      refresh();
                    }} />
                    <Button title="Sync from MVUMO" variant="outline" onPress={async () => {
                      await syncProcedureConsent(episodeId);
                      refresh();
                    }} />
                  </>
                )}
              </>
            ) : (
              <Text style={s.hint}>Consent granted — theatre start enabled</Text>
            )}

            <Text style={s.sub}>4. WHO checklist</Text>
            {(["SIGN_IN", "TIME_OUT", "SIGN_OUT"] as Phase[]).map((phase) => (
              <Button key={phase} title={`Complete ${phase}`} variant="outline" onPress={() => completePhase(phase)} />
            ))}

            <Text style={s.sub}>5. Theatre</Text>
            <Button
              title="Start procedure"
              disabled={active?.consent_status !== "GRANTED"}
              onPress={async () => {
                try {
                  await startProcedure(episodeId);
                  refresh();
                } catch {
                  Alert.alert("Cannot start", "MVUMO surgical consent must be GRANTED first.");
                }
              }}
            />

            <Text style={s.sub}>6. Intra-op note</Text>
            <TextInput style={s.input} value={note} onChangeText={setNote} placeholder="Operative events…" multiline />
            <Button title="Record intra-op" variant="secondary" onPress={async () => {
              if (!note.trim()) return;
              await recordIntraop(episodeId, { eventType: "NOTE", description: note, recordedBy: "surgeon-mobile" });
              setNote("");
              refresh();
            }} />

            <Text style={s.sub}>7. PACU / post-op</Text>
            <Button title="Enter PACU" variant="secondary" onPress={async () => { await enterPacu(episodeId); refresh(); }} />
            <Button title="Complete PACU (Aldrete 8)" onPress={async () => {
              await completePostop(episodeId, { aldreteScore: 8, painScore: 2, disposition: "WARD", recordedBy: "pacu-mobile" });
              refresh();
            }} />

            <Button title="Close episode" onPress={async () => {
              await completeProcedureEpisode(episodeId);
              Alert.alert("Complete", "Procedure episode closed.");
              refresh();
            }} />
          </View>
        )}
      </ScrollView>
    </Screen>
  );
}

const s = StyleSheet.create({
  wrap: { flex: 1 },
  pad: { padding: 16, gap: 10, paddingBottom: 40 },
  hint: { padding: 24, color: colors.gray[500] },
  section: { gap: 10 },
  title: { fontSize: 18, fontWeight: "700", color: colors.gray[900] },
  sub: { fontSize: 14, fontWeight: "600", marginTop: 8, color: colors.gray[700] },
  back: { color: "#2563EB", marginBottom: 8 },
  card: { padding: 12, borderRadius: 10, borderWidth: 1, borderColor: colors.gray[200], flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  cardTitle: { fontWeight: "600" },
  input: { borderWidth: 1, borderColor: colors.gray[300], borderRadius: 8, padding: 10, minHeight: 80, textAlignVertical: "top" },
  asaRow: { flexDirection: "row", gap: 8 },
  asaBtn: { padding: 8, borderRadius: 8, borderWidth: 1, borderColor: colors.gray[300] },
  asaActive: { backgroundColor: colors.ui.success.light, borderColor: colors.ui.success.main },
});
