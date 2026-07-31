/**
 * SurgeryEpisodesScreen — Provider mobile surgical episode spine + course-of-care.
 *
 * Mirrors web `/work/clinical/surgery` via BFF `/internal/v1/surgery/**`.
 * Honesty: list failure ≠ empty episodes; assessment/decision/follow-up 404 = not recorded.
 */
import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  ScrollView,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  RefreshControl,
  Alert,
} from "react-native";
import {
  Screen,
  Header,
  Badge,
  Button,
  LoadingSpinner,
  ErrorState,
  colors,
} from "@impilo/mobile-design-system";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEncounterStore } from "../../stores/encounterStore";
import {
  listSurgicalEpisodes,
  openSurgicalEpisode,
  getSurgicalAssessment,
  recordSurgicalAssessment,
  getSurgicalDecision,
  recordSurgicalDecision,
  reopenSurgicalEpisode,
  listEpisodeSpecialties,
  addEpisodeSpecialty,
  listPrehabItems,
  recordPrehabItem,
  listComplications,
  recogniseComplication,
  listLongitudinalObjects,
  placeLongitudinalObject,
  getSurgicalFollowup,
  recordSurgicalFollowup,
  listWaitlistRevalidations,
  appendWaitlistRevalidation,
  isNotRecorded,
  type SurgicalEpisode,
} from "../../services/surgeryService";

const SURGICAL_SPECIALTIES = [
  "GENERAL_SURGERY",
  "ORTHOPAEDICS",
  "UROLOGY",
  "ENT",
  "OPHTHALMOLOGY",
  "COLORECTAL",
  "UPPER_GI_HPB",
  "BREAST_ENDOCRINE",
  "VASCULAR",
  "NEUROSURGERY",
  "CARDIOTHORACIC",
  "MAXILLOFACIAL",
  "PLASTICS",
  "PAEDIATRIC_SURGERY",
  "SURGICAL_ONCOLOGY",
];

const REOPENABLE = new Set(["OPERATED", "CLOSED"]);

function AssessmentBlock({ episodeId }: { episodeId: string }) {
  const qc = useQueryClient();
  const q = useQuery({
    queryKey: ["surgery", "assessment", episodeId],
    queryFn: () => getSurgicalAssessment(episodeId),
    retry: false,
  });
  const [presenting, setPresenting] = useState("");
  const save = useMutation({
    mutationFn: () =>
      recordSurgicalAssessment(episodeId, {
        presentingProblem: presenting.trim() || undefined,
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["surgery", "assessment", episodeId] });
      setPresenting("");
    },
  });

  if (q.isLoading) return <Text style={styles.muted}>Loading assessment…</Text>;
  if (q.isError && !isNotRecorded(q.error)) {
    return (
      <Text style={styles.danger} testID="surgery-assessment-unavailable">
        Could not read the assessment. This is not the same as no assessment being recorded.
      </Text>
    );
  }

  return (
    <View testID="surgery-assessment-panel" style={styles.panel}>
      <Text style={styles.panelTitle}>Assessment (S2)</Text>
      {q.isError && isNotRecorded(q.error) ? (
        <Text style={styles.muted} testID="surgery-assessment-not-recorded">
          No assessment recorded for this episode yet.
        </Text>
      ) : q.data ? (
        <View testID="surgery-assessment-view">
          <Text style={styles.body}>
            {String(q.data.presentingProblem ?? "—")}
            {q.data.assessedBy ? ` · by ${q.data.assessedBy}` : ""}
          </Text>
        </View>
      ) : null}
      <TextInput
        testID="surgery-assessment-presenting"
        style={styles.input}
        placeholder="Presenting problem"
        value={presenting}
        onChangeText={setPresenting}
      />
      <Button
        title={save.isPending ? "Saving…" : "Save assessment"}
        testID="surgery-assessment-save"
        onPress={() => save.mutate()}
        disabled={save.isPending || !presenting.trim()}
      />
      {save.isError && (
        <Text style={styles.danger} testID="surgery-assessment-save-failed">
          Save failed — the assessment has NOT been recorded.
        </Text>
      )}
    </View>
  );
}

function DecisionBlock({ episodeId }: { episodeId: string }) {
  const qc = useQueryClient();
  const q = useQuery({
    queryKey: ["surgery", "decision", episodeId],
    queryFn: () => getSurgicalDecision(episodeId),
    retry: false,
  });
  const [benefit, setBenefit] = useState("");
  const [finalDecision, setFinalDecision] = useState("");
  const [forum, setForum] = useState("");
  const [mdtRef, setMdtRef] = useState("");
  const save = useMutation({
    mutationFn: () => {
      const payload: Record<string, unknown> = {};
      if (benefit.trim()) payload.expectedBenefit = benefit.trim();
      if (finalDecision) payload.finalDecision = finalDecision;
      if (forum === "MDT") {
        payload.decisionForum = "MDT";
        payload.mdtDecisionRef = mdtRef.trim();
        payload.mdtDecisionSource = "PCT_MDT_DECISION";
      } else if (forum === "INDIVIDUAL") {
        payload.decisionForum = "INDIVIDUAL";
      }
      return recordSurgicalDecision(episodeId, payload);
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["surgery", "decision", episodeId] });
      setBenefit("");
      setFinalDecision("");
      setForum("");
      setMdtRef("");
    },
  });

  const mdtIncomplete = forum === "MDT" && !mdtRef.trim();

  if (q.isLoading) return <Text style={styles.muted}>Loading decision…</Text>;
  if (q.isError && !isNotRecorded(q.error)) {
    return (
      <Text style={styles.danger} testID="surgery-decision-unavailable">
        Could not read the decision record. This is not the same as no decision being recorded.
      </Text>
    );
  }

  return (
    <View testID="surgery-decision-panel" style={styles.panel}>
      <Text style={styles.panelTitle}>Decision / MDT (S3)</Text>
      {q.isError && isNotRecorded(q.error) ? (
        <Text style={styles.muted} testID="surgery-decision-not-recorded">
          No decision record for this episode yet.
        </Text>
      ) : q.data ? (
        <View testID="surgery-decision-view">
          <Text style={styles.body}>
            {q.data.finalDecision ?? "No final decision yet"}
            {q.data.decidedBy ? ` · ${q.data.decidedBy}` : ""}
          </Text>
          {q.data.decisionForum === "MDT" && (
            <Text style={styles.meta} testID="surgery-decision-forum">
              MDT · {q.data.mdtDecisionRef}
              {q.data.mdtDecisionVerified ? (
                <Text testID="surgery-decision-mdt-verified"> (confirmed in PCT)</Text>
              ) : (
                <Text style={styles.warn} testID="surgery-decision-mdt-unverified">
                  {" "}
                  (recorded, not yet confirmed against PCT)
                </Text>
              )}
            </Text>
          )}
        </View>
      ) : null}
      <TextInput
        testID="surgery-decision-benefit"
        style={styles.input}
        placeholder="Expected benefit"
        value={benefit}
        onChangeText={setBenefit}
      />
      <View style={styles.chipRow}>
        {(["", "PROCEED", "DO_NOT_PROCEED", "DEFER"] as const).map((v) => (
          <TouchableOpacity
            key={v || "none"}
            testID={`surgery-decision-final-${v || "none"}`}
            style={[styles.chip, finalDecision === v && styles.chipActive]}
            onPress={() => setFinalDecision(v)}
          >
            <Text style={styles.chipText}>{v || "No final"}</Text>
          </TouchableOpacity>
        ))}
      </View>
      <View style={styles.chipRow}>
        {(["", "INDIVIDUAL", "MDT"] as const).map((v) => (
          <TouchableOpacity
            key={v || "leave"}
            testID={`surgery-decision-forum-${v || "leave"}`}
            style={[styles.chip, forum === v && styles.chipActive]}
            onPress={() => setForum(v)}
          >
            <Text style={styles.chipText}>{v || "Forum unchanged"}</Text>
          </TouchableOpacity>
        ))}
      </View>
      {forum === "MDT" && (
        <TextInput
          testID="surgery-decision-mdt-ref"
          style={styles.input}
          placeholder="PCT board decision UUID"
          value={mdtRef}
          onChangeText={setMdtRef}
        />
      )}
      <Button
        title={save.isPending ? "Saving…" : "Save decision"}
        testID="surgery-decision-save"
        onPress={() => save.mutate()}
        disabled={save.isPending || mdtIncomplete}
      />
      {mdtIncomplete && (
        <Text style={styles.muted} testID="surgery-decision-mdt-required">
          An MDT decision needs the PCT board decision it came from.
        </Text>
      )}
      {save.isError && (
        <Text style={styles.danger} testID="surgery-decision-save-failed">
          Save failed — the decision has NOT been recorded.
        </Text>
      )}
    </View>
  );
}

function SpecialtiesBlock({ episodeId }: { episodeId: string }) {
  const qc = useQueryClient();
  const q = useQuery({
    queryKey: ["surgery", "specialties", episodeId],
    queryFn: () => listEpisodeSpecialties(episodeId),
  });
  const [specialty, setSpecialty] = useState(SURGICAL_SPECIALTIES[0]);
  const add = useMutation({
    mutationFn: () => addEpisodeSpecialty(episodeId, { specialty, role: "SHARED" }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["surgery", "specialties", episodeId] }),
  });

  return (
    <View testID="surgery-specialties-panel" style={styles.panel}>
      <Text style={styles.panelTitle}>Specialties on this case</Text>
      {q.isLoading ? (
        <Text style={styles.muted}>Loading specialties…</Text>
      ) : q.isError ? (
        <Text style={styles.danger} testID="surgery-specialties-unavailable">
          Could not read which teams are on this case. This is not the same as there being only one
          team.
        </Text>
      ) : (q.data ?? []).length === 0 ? (
        <Text style={styles.muted} testID="surgery-specialties-empty">
          No specialty recorded on this episode.
        </Text>
      ) : (
        <View testID="surgery-specialties-list">
          {(q.data ?? []).map((s) => (
            <Text key={s.id} style={styles.body}>
              {s.role} · {s.specialty}
              {s.contribution ? ` — ${s.contribution}` : ""}
            </Text>
          ))}
        </View>
      )}
      <ScrollView horizontal style={styles.chipScroll}>
        {SURGICAL_SPECIALTIES.map((s) => (
          <TouchableOpacity
            key={s}
            style={[styles.chip, specialty === s && styles.chipActive]}
            onPress={() => setSpecialty(s)}
          >
            <Text style={styles.chipText}>{s}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
      <Button
        title="Add shared specialty"
        testID="surgery-specialty-add"
        onPress={() => add.mutate()}
        disabled={add.isPending || q.isError}
      />
    </View>
  );
}

function CourseOfCareBlock({ episodeId }: { episodeId: string }) {
  const qc = useQueryClient();
  const prehabQ = useQuery({
    queryKey: ["surgery", "prehab", episodeId],
    queryFn: () => listPrehabItems(episodeId),
  });
  const compQ = useQuery({
    queryKey: ["surgery", "complications", episodeId],
    queryFn: () => listComplications(episodeId),
  });
  const longQ = useQuery({
    queryKey: ["surgery", "longitudinal", episodeId],
    queryFn: () => listLongitudinalObjects(episodeId),
  });
  const followQ = useQuery({
    queryKey: ["surgery", "followup", episodeId],
    queryFn: () => getSurgicalFollowup(episodeId),
    retry: false,
  });
  const waitQ = useQuery({
    queryKey: ["surgery", "waitlist", episodeId],
    queryFn: () => listWaitlistRevalidations(episodeId),
  });

  const recordPrehab = useMutation({
    mutationFn: () =>
      recordPrehabItem(episodeId, { domain: "NUTRITION", status: "PLANNED", notes: "Mobile" }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["surgery", "prehab", episodeId] }),
  });
  const recognise = useMutation({
    mutationFn: () =>
      recogniseComplication(episodeId, { complicationType: "SURGICAL_SITE_INFECTION" }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["surgery", "complications", episodeId] }),
  });
  const placeObj = useMutation({
    mutationFn: () =>
      placeLongitudinalObject(episodeId, {
        kind: "DRAIN",
        site: "Abdomen",
        indication: "Post-op drainage",
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["surgery", "longitudinal", episodeId] }),
  });
  const saveFollow = useMutation({
    mutationFn: () =>
      recordSurgicalFollowup(episodeId, { restrictions: "Light duties", transportArranged: true }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["surgery", "followup", episodeId] }),
  });
  const revalidate = useMutation({
    mutationFn: () =>
      appendWaitlistRevalidation(episodeId, { stillClinicallyAppropriate: true, notes: "Mobile" }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["surgery", "waitlist", episodeId] }),
  });

  return (
    <View testID="surgery-course-of-care" style={styles.panel}>
      <Text style={styles.panelTitle}>Course of care</Text>

      <Text style={styles.subTitle}>Prehab</Text>
      {prehabQ.isError ? (
        <Text style={styles.danger} testID="surgery-prehab-unavailable">
          Could not read optimisation items. This is not the same as there being none.
        </Text>
      ) : (prehabQ.data ?? []).length === 0 ? (
        <Text style={styles.muted} testID="surgery-prehab-empty">
          No optimisation items recorded yet.
        </Text>
      ) : (
        <View testID="surgery-prehab-list">
          {(prehabQ.data ?? []).map((i) => (
            <Text key={i.id} style={styles.body}>
              {i.domain} · {i.status ?? "—"}
            </Text>
          ))}
        </View>
      )}
      <Button title="Record prehab item" testID="surgery-prehab-save" onPress={() => recordPrehab.mutate()} />

      <Text style={styles.subTitle}>Complications</Text>
      {compQ.isError ? (
        <Text style={styles.danger} testID="surgery-complications-unavailable">
          Could not read complications. This is not the same as there being none.
        </Text>
      ) : (compQ.data ?? []).length === 0 ? (
        <Text style={styles.muted} testID="surgery-complications-empty">
          No complications recognised on this episode.
        </Text>
      ) : (
        <View testID="surgery-complications-list">
          {(compQ.data ?? []).map((c) => (
            <Text key={c.id} style={styles.body}>
              {c.complicationType} · {c.status}
              {c.gradeCode ? ` · grade ${c.gradeCode}` : ""}
            </Text>
          ))}
        </View>
      )}
      <Button
        title="Recognise complication"
        testID="surgery-complication-recognise"
        onPress={() => recognise.mutate()}
      />

      <Text style={styles.subTitle}>Longitudinal objects</Text>
      {longQ.isError ? (
        <Text style={styles.danger} testID="surgery-longitudinal-unavailable">
          Could not read drains/stomas/wounds. This is not the same as there being none.
        </Text>
      ) : (longQ.data ?? []).length === 0 ? (
        <Text style={styles.muted} testID="surgery-longitudinal-empty">
          No drains, stomas, or wounds recorded.
        </Text>
      ) : (
        <View testID="surgery-longitudinal-list">
          {(longQ.data ?? []).map((o) => (
            <Text key={o.id} style={styles.body}>
              {o.kind} · {o.site} · {o.status}
            </Text>
          ))}
        </View>
      )}
      <Button title="Place drain" testID="surgery-longitudinal-place" onPress={() => placeObj.mutate()} />

      <Text style={styles.subTitle}>Follow-up</Text>
      {followQ.isError && !isNotRecorded(followQ.error) ? (
        <Text style={styles.danger} testID="surgery-followup-unavailable">
          Could not read follow-up. This is not the same as none being recorded.
        </Text>
      ) : followQ.isError && isNotRecorded(followQ.error) ? (
        <Text style={styles.muted} testID="surgery-followup-not-recorded">
          No follow-up recorded yet.
        </Text>
      ) : followQ.data ? (
        <Text style={styles.body} testID="surgery-followup-view">
          {followQ.data.restrictions ?? "—"}
          {followQ.data.transportArranged ? " · transport arranged" : ""}
        </Text>
      ) : null}
      <Button title="Record follow-up" testID="surgery-followup-save" onPress={() => saveFollow.mutate()} />

      <Text style={styles.subTitle}>Waitlist revalidation</Text>
      {waitQ.isError ? (
        <Text style={styles.danger} testID="surgery-waitlist-unavailable">
          Could not read waitlist revalidation history. This is not the same as never revalidated.
        </Text>
      ) : (waitQ.data ?? []).length === 0 ? (
        <Text style={styles.muted} testID="surgery-waitlist-empty">
          No waitlist revalidations recorded.
        </Text>
      ) : (
        <View testID="surgery-waitlist-list">
          {(waitQ.data ?? []).map((w) => (
            <Text key={w.id} style={styles.body}>
              {w.revalidatedAt} · appropriate={String(w.stillClinicallyAppropriate)}
            </Text>
          ))}
        </View>
      )}
      <Button
        title="Append revalidation"
        testID="surgery-waitlist-append"
        onPress={() => revalidate.mutate()}
      />
    </View>
  );
}

function EpisodeDetail({
  episode,
  onBack,
}: {
  episode: SurgicalEpisode;
  onBack: () => void;
}) {
  const qc = useQueryClient();
  const [reopenReason, setReopenReason] = useState("");
  const reopen = useMutation({
    mutationFn: () => reopenSurgicalEpisode(episode.id, { reason: reopenReason.trim() }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["surgery", "episodes"] });
      Alert.alert("Reopened", "Episode reopened for return to theatre.");
      onBack();
    },
  });

  return (
    <Screen>
      <Header title="Surgical episode" onBack={onBack} />
      <ScrollView testID="surgery-episode-detail" contentContainerStyle={styles.pad}>
        <Text style={styles.title}>{episode.operativeIndication ?? "Surgical episode"}</Text>
        <View style={styles.row}>
          <Badge label={episode.status} />
          {episode.specialty ? <Badge label={episode.specialty} /> : null}
        </View>
        <Text style={styles.meta}>
          {episode.subjectCpid}
          {episode.journeyId ? ` · journey ${episode.journeyId}` : ""}
        </Text>
        {episode.reopenReason ? (
          <Text style={styles.meta}>
            Reopened: {episode.reopenReason}
            {episode.reopenedBy ? ` by ${episode.reopenedBy}` : ""}
          </Text>
        ) : null}

        <AssessmentBlock episodeId={episode.id} />
        <DecisionBlock episodeId={episode.id} />
        <SpecialtiesBlock episodeId={episode.id} />
        <CourseOfCareBlock episodeId={episode.id} />

        {REOPENABLE.has(episode.status) && (
          <View style={styles.panel} testID="surgery-reopen-panel">
            <Text style={styles.panelTitle}>Reopen (return to theatre)</Text>
            <TextInput
              testID="surgery-reopen-reason"
              style={styles.input}
              placeholder="Mandatory reopen reason"
              value={reopenReason}
              onChangeText={setReopenReason}
            />
            <Button
              title={reopen.isPending ? "Reopening…" : "Reopen episode"}
              testID="surgery-reopen"
              onPress={() => reopen.mutate()}
              disabled={reopen.isPending || !reopenReason.trim()}
            />
            {reopen.isError && (
              <Text style={styles.danger} testID="surgery-reopen-failed">
                Reopen failed — the episode was NOT reopened.
              </Text>
            )}
          </View>
        )}
      </ScrollView>
    </Screen>
  );
}

export function SurgeryEpisodesScreen() {
  const { activeEncounter } = useEncounterStore();
  const [cpid, setCpid] = useState(activeEncounter?.patientId ?? "");
  const [submittedCpid, setSubmittedCpid] = useState<string | null>(
    activeEncounter?.patientId ?? null,
  );
  const [selected, setSelected] = useState<SurgicalEpisode | null>(null);
  const [showOpen, setShowOpen] = useState(false);
  const [indication, setIndication] = useState("");
  const [journeyId, setJourneyId] = useState(activeEncounter?.id ?? "");
  const qc = useQueryClient();

  useEffect(() => {
    if (activeEncounter?.patientId) {
      setCpid(activeEncounter.patientId);
      setSubmittedCpid(activeEncounter.patientId);
      setJourneyId(activeEncounter.id ?? "");
    }
  }, [activeEncounter?.patientId, activeEncounter?.id]);

  const episodesQ = useQuery({
    queryKey: ["surgery", "episodes", submittedCpid],
    queryFn: () => listSurgicalEpisodes(submittedCpid!),
    enabled: !!submittedCpid,
  });

  const openMut = useMutation({
    mutationFn: () =>
      openSurgicalEpisode({
        subjectCpid: submittedCpid ?? cpid.trim(),
        operativeIndication: indication.trim(),
        journeyId: journeyId.trim() || null,
        encounterId: activeEncounter?.id ?? null,
      }),
    onSuccess: (ep) => {
      void qc.invalidateQueries({ queryKey: ["surgery", "episodes"] });
      setShowOpen(false);
      setIndication("");
      setSelected(ep);
    },
  });

  if (selected) {
    return <EpisodeDetail episode={selected} onBack={() => setSelected(null)} />;
  }

  return (
    <Screen>
      <Header title="Surgery" />
      <ScrollView
        testID="surgery-episodes-screen"
        contentContainerStyle={styles.pad}
        refreshControl={
          submittedCpid ? (
            <RefreshControl
              refreshing={episodesQ.isRefetching}
              onRefresh={() => void episodesQ.refetch()}
            />
          ) : undefined
        }
      >
        <Text style={styles.hint}>
          Find surgical episodes by CPID. Web spine: /work/clinical/surgery
        </Text>
        <TextInput
          testID="surgery-cpid-input"
          style={styles.input}
          placeholder="Subject CPID"
          value={cpid}
          onChangeText={setCpid}
          autoCapitalize="characters"
        />
        <Button
          title="Load episodes"
          testID="surgery-load-episodes"
          onPress={() => {
            const next = cpid.trim();
            if (next) setSubmittedCpid(next);
          }}
          disabled={!cpid.trim()}
        />

        {!submittedCpid ? (
          <Text style={styles.muted} testID="surgery-needs-cpid">
            Enter a CPID to load the surgical record.
          </Text>
        ) : episodesQ.isLoading ? (
          <LoadingSpinner />
        ) : episodesQ.isError ? (
          <ErrorState
            testID="surgery-episodes-unavailable"
            title="Surgical record unavailable"
            message="Could not read the surgical record. Do not treat this as an empty episode list."
            onRetry={() => void episodesQ.refetch()}
          />
        ) : (
          <View testID="surgery-episodes-list">
            {(episodesQ.data ?? []).length === 0 ? (
              <Text style={styles.muted} testID="surgery-episodes-empty">
                This patient has no surgical episodes.
              </Text>
            ) : (
              (episodesQ.data ?? []).map((ep) => (
                <TouchableOpacity
                  key={ep.id}
                  testID="surgery-episode-item"
                  style={styles.card}
                  onPress={() => setSelected(ep)}
                >
                  <Text style={styles.cardTitle}>{ep.operativeIndication ?? "Episode"}</Text>
                  <Text style={styles.meta}>
                    {ep.status}
                    {ep.specialty ? ` · ${ep.specialty}` : ""}
                  </Text>
                </TouchableOpacity>
              ))
            )}
          </View>
        )}

        {submittedCpid && !episodesQ.isError && (
          <View style={styles.panel}>
            <TouchableOpacity
              testID="surgery-open-toggle"
              onPress={() => setShowOpen((v) => !v)}
            >
              <Text style={styles.panelTitle}>{showOpen ? "Cancel open" : "Open new episode"}</Text>
            </TouchableOpacity>
            {showOpen && (
              <View testID="surgery-open-form">
                <TextInput
                  testID="surgery-open-indication"
                  style={styles.input}
                  placeholder="Operative indication (required)"
                  value={indication}
                  onChangeText={setIndication}
                />
                <TextInput
                  testID="surgery-open-journey"
                  style={styles.input}
                  placeholder="Journey ID (CC-5 anchor)"
                  value={journeyId}
                  onChangeText={setJourneyId}
                />
                <Button
                  title={openMut.isPending ? "Opening…" : "Open episode"}
                  testID="surgery-open-submit"
                  onPress={() => openMut.mutate()}
                  disabled={openMut.isPending || !indication.trim()}
                />
                {openMut.isError && (
                  <Text style={styles.danger} testID="surgery-open-failed">
                    Open failed — no episode was created.
                  </Text>
                )}
              </View>
            )}
          </View>
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  pad: { padding: 16, paddingBottom: 40 },
  hint: { color: colors.gray[600], fontSize: 13, marginBottom: 12 },
  muted: { color: colors.gray[500], fontSize: 13, marginTop: 8 },
  danger: { color: "#B91C1C", fontSize: 13, marginTop: 8 },
  warn: { color: "#B45309" },
  title: { fontSize: 18, fontWeight: "700", color: colors.gray[900], marginBottom: 8 },
  body: { fontSize: 14, color: colors.gray[800], marginTop: 4 },
  meta: { fontSize: 12, color: colors.gray[500], marginTop: 4 },
  row: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 },
  input: {
    borderWidth: 1,
    borderColor: colors.gray[200],
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    marginTop: 8,
    backgroundColor: "#fff",
    fontSize: 14,
  },
  card: {
    backgroundColor: "#fff",
    borderRadius: 10,
    borderWidth: 1,
    borderColor: colors.gray[200],
    padding: 14,
    marginTop: 10,
  },
  cardTitle: { fontSize: 15, fontWeight: "600", color: colors.gray[900] },
  panel: {
    marginTop: 16,
    padding: 12,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: colors.gray[200],
    backgroundColor: "#fff",
  },
  panelTitle: { fontSize: 12, fontWeight: "700", color: colors.gray[600], textTransform: "uppercase" },
  subTitle: {
    marginTop: 14,
    fontSize: 12,
    fontWeight: "700",
    color: colors.gray[700],
    textTransform: "uppercase",
  },
  chipRow: { flexDirection: "row", flexWrap: "wrap", gap: 6, marginTop: 8 },
  chipScroll: { marginTop: 8, maxHeight: 40 },
  chip: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: colors.gray[100],
    marginRight: 6,
  },
  chipActive: { backgroundColor: "#CCFBF1" },
  chipText: { fontSize: 11, color: colors.gray[800] },
});
