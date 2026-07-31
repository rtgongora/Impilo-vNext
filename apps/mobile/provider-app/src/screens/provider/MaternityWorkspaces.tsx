/**
 * Partograph, CTG and maternal near-miss — governed maternal-instrument workspaces.
 *
 * Replaces the generic notes-box the obstetrics specialty workspace fell back to. That fallback
 * came from a resolver that picked a tool's form by its POSITION in `specialtyWorkspaces.ts`'s
 * tool array — "Partograph" was a notes box only because it is first in that array, never because
 * anyone decided a partograph is notes. That positional resolver is gone: tools now resolve
 * through `data/specialtyToolRegistry.ts`, where these three are registered as WIRED against the
 * `PartographWorkspace`, `CtgWorkspace` and `MaternalNearMissWorkspace` surfaces below. Partograph
 * and CTG persist against a real backend (pct-service V056); near-miss identification persists
 * nothing anywhere (CKP's classification is stateless by design — see
 * `MaternalNearMissController.java`'s doc comment on the clinical-knowledge-platform side). All
 * three share governed form definitions served from forms-service; this file adds no persistence
 * of its own.
 *
 * See docs/clinical/rmnp/partograph-ctg-mobile-contract.md for the six behaviours the partograph
 * and CTG UIs must preserve. The short version: a 200 saying "no session is open" and a 502 saying
 * "could not ask" are different clinical statements and must never render the same way;
 * INSUFFICIENT_DATA is the loudest state, not the calmest; and nothing here ever carries a
 * previous value into a new entry. The near-miss workspace answers to the same law under its own
 * contract: blank ≠ ABSENT ≠ unrecognised, INDETERMINATE is never rendered as "no near-miss", and
 * a 502 is an outage, not a clinical finding.
 */

import React, { useMemo, useState } from "react";
import { View, Text, TextInput, ScrollView, Pressable, StyleSheet } from "react-native";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@impilo/mobile-api-client";
import { colors } from "@impilo/mobile-design-system";
import { getFormCatalog } from "../../services/encounterFormsService";
import {
  openPartograph,
  getActivePartograph,
  getPartograph,
  addPartographPoint,
  closePartograph,
  openCtgSession,
  getActiveCtgSession,
  getCtgSession,
  getCtgChunks,
  addCtgAnnotation,
  classifyNearMissForm,
  computeNearMissIndicators,
  fetchBirthDestination,
  getMaternitySummary,
  type PartographProgress,
  type AddPartographPointResult,
  type CtgChunk,
  type NearMissClassifyResult,
  type NearMissIndicatorOutcome,
  type NearMissIndicatorsResult,
  type BirthDestinationRequiredLevel,
  type BirthDestinationVerdict,
  type MaternitySummary,
} from "../../services/maternityService";

// --- shared governed-form rendering (mirrors EncounterFormsPanel's approach) ---------------

interface ClinicalField {
  id: string;
  label: string;
  kind: string;
  unit?: string;
  options?: { code: string; display: string }[];
  validation?: { required?: boolean };
}
interface ClinicalSection {
  id: string;
  title: string;
  fields: ClinicalField[];
}
interface ClinicalDefinition {
  id: string;
  title: string;
  sections: ClinicalSection[];
}

function useGovernedForm(formKey: string) {
  const catalog = useQuery({ queryKey: ["mobile-forms-catalog"], queryFn: getFormCatalog });
  const definition = useMemo(() => {
    for (const entry of catalog.data ?? []) {
      if (entry.formKey !== formKey) continue;
      try {
        return JSON.parse(entry.definitionJson) as ClinicalDefinition;
      } catch {
        return null;
      }
    }
    return null;
  }, [catalog.data, formKey]);
  return { isLoading: catalog.isLoading, isError: catalog.isError, definition };
}

function nowIso(): string {
  return new Date().toISOString();
}

function requiredFieldsSatisfied(definition: ClinicalDefinition, answers: Record<string, unknown>): boolean {
  for (const section of definition.sections) {
    for (const field of section.fields) {
      if (field.validation?.required) {
        const v = answers[field.id];
        if (v === undefined || v === null || v === "") return false;
      }
    }
  }
  return true;
}

function GovernedFormFields({
  definition,
  answers,
  setAnswers,
}: {
  definition: ClinicalDefinition;
  answers: Record<string, unknown>;
  setAnswers: React.Dispatch<React.SetStateAction<Record<string, unknown>>>;
}) {
  return (
    <>
      {definition.sections.map((section) => (
        <View key={section.id} style={styles.section}>
          <Text style={styles.sectionTitle}>{section.title}</Text>
          {section.fields.map((f) => (
            <GovernedField key={f.id} field={f} answers={answers} setAnswers={setAnswers} />
          ))}
        </View>
      ))}
    </>
  );
}

function GovernedField({
  field,
  answers,
  setAnswers,
}: {
  field: ClinicalField;
  answers: Record<string, unknown>;
  setAnswers: React.Dispatch<React.SetStateAction<Record<string, unknown>>>;
}) {
  const set = (v: unknown) => setAnswers((a) => ({ ...a, [field.id]: v }));
  const label = `${field.label}${field.validation?.required ? " *" : ""}`;

  if (field.kind === "datetime") {
    const value = (answers[field.id] as string | undefined) ?? "";
    return (
      <View style={styles.field}>
        <Text style={styles.label}>{label}</Text>
        <View style={styles.datetimeRow}>
          <TextInput
            testID={`field-${field.id}`}
            style={[styles.input, { flex: 1 }]}
            value={value}
            onChangeText={(t: string) => set(t)}
            placeholder="ISO time (e.g. 2026-07-26T14:00:00Z)"
          />
          <Pressable style={styles.nowBtn} onPress={() => set(nowIso())} testID={`field-${field.id}-now`}>
            <Text style={styles.nowBtnText}>Now</Text>
          </Pressable>
        </View>
      </View>
    );
  }

  if (field.kind === "coded_single" && field.options) {
    return (
      <View style={styles.field}>
        <Text style={styles.label}>{label}</Text>
        <View style={styles.options}>
          {field.options.map((opt) => (
            <Pressable
              key={opt.code}
              // Tapping the already-selected option retracts it back to blank rather than being a
              // no-op — a clinician must be able to undo an accidental tap on a coded criterion,
              // not merely switch it to the other code.
              onPress={() => set(answers[field.id] === opt.code ? undefined : opt.code)}
              style={[styles.option, answers[field.id] === opt.code && styles.optionSelected]}
              testID={`field-${field.id}-opt-${opt.code}`}
            >
              <Text
                style={[styles.optionText, answers[field.id] === opt.code && styles.optionTextSelected]}
              >
                {opt.display}
              </Text>
            </Pressable>
          ))}
          {(answers[field.id] === undefined || answers[field.id] === null) && (
            <Text style={styles.notAssessedHint}>not assessed</Text>
          )}
        </View>
      </View>
    );
  }

  const isNumeric = field.kind === "numeric_with_unit" || field.kind === "decimal";
  const keyboard = isNumeric ? "decimal-pad" : "default";
  return (
    <View style={styles.field}>
      <Text style={styles.label}>
        {label}
        {field.unit ? ` (${field.unit})` : ""}
      </Text>
      <TextInput
        testID={`field-${field.id}`}
        value={answers[field.id] === undefined || answers[field.id] === null ? "" : String(answers[field.id])}
        keyboardType={keyboard as "decimal-pad" | "default"}
        onChangeText={(t: string) => set(isNumeric && t !== "" ? Number(t) : t === "" ? undefined : t)}
        placeholder={isNumeric ? "Not assessed" : undefined}
        style={[styles.input, field.kind === "text" && { minHeight: 60 }]}
        multiline={field.kind === "text"}
      />
    </View>
  );
}

/** Renders `error.code === "PCT_UNAVAILABLE"` distinctly from any other failure. Never an empty state. */
function UnavailableBanner({ error, subject }: { error: unknown; subject: string }) {
  const isPctUnavailable = error instanceof ApiError && error.code === "PCT_UNAVAILABLE";
  return (
    <View style={styles.unavailableBanner} accessibilityRole="alert">
      <Text style={styles.unavailableTitle}>
        {isPctUnavailable ? `The ${subject} could not be read` : "Something went wrong"}
      </Text>
      <Text style={styles.unavailableBody}>
        {isPctUnavailable
          ? `This is not the same as "no observations" — the record could not be reached. Do not act as if this patient has no ${subject} data.`
          : "Please try again."}
      </Text>
    </View>
  );
}

function PatientIdentifiers({
  patientId,
  setPatientId,
  encounterId,
  setEncounterId,
}: {
  patientId: string;
  setPatientId: (v: string) => void;
  encounterId: string;
  setEncounterId: (v: string) => void;
}) {
  return (
    <View style={styles.identifierRow}>
      <TextInput
        style={[styles.input, { flex: 1 }]}
        placeholder="Patient ID"
        value={patientId}
        onChangeText={setPatientId}
        testID="maternity-patient-id"
      />
      <TextInput
        style={[styles.input, { flex: 1 }]}
        placeholder="Encounter ID (optional)"
        value={encounterId}
        onChangeText={setEncounterId}
        testID="maternity-encounter-id"
      />
    </View>
  );
}

// --- Maternity summary header ---------------------------------------------------------------
//
// Backend: `GET /internal/v1/maternity/summary` — see `getMaternitySummary`'s doc comment in
// maternityService.ts. A 502 (`maternity_summary_unavailable`) renders as its own banner here,
// never as "no maternity record" — the same discipline `UnavailableBanner` already applies to
// PCT_UNAVAILABLE for the partograph and CTG queries above.

/** The header area for a known patientId: is a partograph/CTG session open, how many labour
 * observations exist, and when the last one was recorded. Shown once patientId is known — the
 * summary is a real-time cross-cut of the same record the workspace below is about to open. */
function MaternitySummaryHeader({ patientId }: { patientId: string }) {
  const summary = useQuery({
    queryKey: ["maternity-summary", patientId],
    queryFn: () => getMaternitySummary(patientId),
    enabled: patientId.trim().length > 0,
  });

  if (!patientId.trim()) return null;

  if (summary.isLoading) {
    return <Text style={styles.hint}>Loading maternity summary…</Text>;
  }

  if (summary.isError) {
    const unavailable = summary.error instanceof ApiError && summary.error.code === "maternity_summary_unavailable";
    return (
      <View style={styles.unavailableBanner} testID="maternity-summary-unavailable" accessibilityRole="alert">
        <Text style={styles.unavailableTitle}>The maternity summary could not be retrieved</Text>
        <Text style={styles.unavailableBody}>
          {unavailable
            ? "This is not the same as this patient having no maternity record — do not treat it as an empty chart."
            : "Please try again."}
        </Text>
      </View>
    );
  }

  const data: MaternitySummary | undefined = summary.data;
  if (!data) return null;

  return (
    <View style={styles.summaryHeader} testID="maternity-summary-header">
      <Text style={styles.summaryLine}>
        Partograph: {data.partograph_active ? "Active" : "No open session"}
        {data.partograph_active && data.progress?.status ? ` (${data.progress.status})` : ""}
      </Text>
      <Text style={styles.summaryLine}>CTG: {data.ctg_active ? "Active" : "No open session"}</Text>
      <Text style={styles.summaryLine}>
        {data.observation_count} observation{data.observation_count === 1 ? "" : "s"} recorded
        {data.last_observed_at ? ` · last ${new Date(data.last_observed_at).toLocaleString()}` : ""}
      </Text>
    </View>
  );
}

// --- Birth destination -----------------------------------------------------------------------
//
// Backend: `GET /internal/v1/maternity/birth-destination` — see `fetchBirthDestination`'s doc
// comment. `CAPABILITY_UNKNOWN` renders distinctly from `BELOW_REQUIRED_LEVEL`: the former is
// "nobody has assessed this yet" (call ahead), the latter is "assessed, and it does not meet the
// level needed" — collapsing them turns the common unassessed case into a false refusal.

const REQUIRED_LEVEL_OPTIONS: BirthDestinationRequiredLevel[] = ["UNKNOWN", "BEMONC", "CEMONC"];

const DESTINATION_STATUS_LABEL: Record<BirthDestinationVerdict["status"], string> = {
  MEETS_REQUIRED_LEVEL: "Meets the level of care needed",
  BELOW_REQUIRED_LEVEL: "Assessed below the level of care needed",
  CAPABILITY_UNKNOWN: "Emergency obstetric capability not yet assessed",
  NOT_OPERATIONAL: "Not a confirmed operating maternity destination",
  REQUIRED_LEVEL_UNKNOWN: "Level of care needed has not been established",
  UNAVAILABLE: "Could not be determined",
};

export function BirthDestinationSection() {
  const [facilityId, setFacilityId] = useState("");
  const [requiredLevel, setRequiredLevel] = useState<BirthDestinationRequiredLevel>("UNKNOWN");

  const assess = useMutation({
    mutationFn: () => fetchBirthDestination(Number(facilityId), requiredLevel),
  });

  const canAssess = /^\d+$/.test(facilityId.trim());
  const unavailable = assess.isError && assess.error instanceof ApiError && assess.error.status === 502;

  return (
    <View style={styles.workspace} testID="birth-destination-section">
      <Text style={styles.workspaceTitle}>Birth destination check</Text>
      <View style={styles.identifierRow}>
        <TextInput
          style={[styles.input, { flex: 1 }]}
          placeholder="Facility ID"
          value={facilityId}
          onChangeText={setFacilityId}
          keyboardType="number-pad"
          testID="birth-destination-facility-id"
        />
      </View>
      <View style={styles.options}>
        {REQUIRED_LEVEL_OPTIONS.map((level) => (
          <Pressable
            key={level}
            onPress={() => setRequiredLevel(level)}
            style={[styles.option, requiredLevel === level && styles.optionSelected]}
            testID={`birth-destination-level-${level}`}
          >
            <Text style={[styles.optionText, requiredLevel === level && styles.optionTextSelected]}>{level}</Text>
          </Pressable>
        ))}
      </View>
      <Pressable
        style={[styles.primaryBtn, (!canAssess || assess.isPending) && styles.primaryBtnDisabled]}
        disabled={!canAssess || assess.isPending}
        onPress={() => assess.mutate()}
        testID="birth-destination-assess"
      >
        <Text style={styles.primaryBtnText}>{assess.isPending ? "Assessing…" : "Assess"}</Text>
      </Pressable>

      {unavailable && (
        <View style={styles.unavailableBanner} testID="birth-destination-unavailable" accessibilityRole="alert">
          <Text style={styles.unavailableTitle}>The facility&apos;s status could not be retrieved</Text>
          <Text style={styles.unavailableBody}>
            Do not treat this as either a safe or an unsafe destination — it is unknown. Confirm by
            phone before directing her here.
          </Text>
        </View>
      )}

      {assess.isError && !unavailable && (
        <Text style={styles.errorText}>Could not assess this facility. Try again.</Text>
      )}

      {assess.data && !unavailable && (
        <View style={styles.progressBox} testID="birth-destination-verdict">
          <Text style={styles.progressStatus}>{DESTINATION_STATUS_LABEL[assess.data.status]}</Text>
          <Text style={styles.progressDetail}>{assess.data.message}</Text>
          <Text style={styles.progressDetail}>{assess.data.guidance}</Text>
          {assess.data.call_ahead && (
            <Text style={styles.progressAction} testID="birth-destination-call-ahead">
              Call ahead
            </Text>
          )}
        </View>
      )}
    </View>
  );
}

// --- Progress display (partograph) ---------------------------------------------------------

const STATUS_LABEL: Record<PartographProgress["status"], string> = {
  LEFT_OF_ALERT: "On track — at or ahead of the alert line",
  BETWEEN_ALERT_AND_ACTION: "Alert line crossed — reassess",
  AT_OR_RIGHT_OF_ACTION: "Action line crossed — act now",
  SECOND_STAGE: "Second stage — alert/action lines no longer apply",
  INSUFFICIENT_DATA: "Insufficient data — this labour has not been assessed",
};

/**
 * INSUFFICIENT_DATA renders with the loudest styling here, never the calmest — a partograph with
 * nothing on it is a more urgent finding than one progressing slowly (contract §4.4).
 */
function progressStyle(status: PartographProgress["status"]) {
  if (status === "INSUFFICIENT_DATA" || status === "AT_OR_RIGHT_OF_ACTION") {
    return { bg: "#FEE2E2", border: "#DC2626", text: "#7F1D1D" };
  }
  if (status === "BETWEEN_ALERT_AND_ACTION") {
    return { bg: "#FEF3C7", border: "#F59E0B", text: "#78350F" };
  }
  if (status === "SECOND_STAGE") {
    return { bg: "#DBEAFE", border: "#2563EB", text: "#1E3A8A" };
  }
  return { bg: "#D1FAE5", border: "#22C55E", text: "#14532D" };
}

function ProgressPanel({ progress }: { progress: PartographProgress }) {
  const s = progressStyle(progress.status);
  return (
    <View style={[styles.progressBox, { backgroundColor: s.bg, borderColor: s.border }]} testID="partograph-progress">
      <Text style={[styles.progressStatus, { color: s.text }]}>{STATUS_LABEL[progress.status]}</Text>
      {progress.recommended_action && (
        <Text style={[styles.progressAction, { color: s.text }]}>{progress.recommended_action}</Text>
      )}
      {progress.latest_dilation_cm != null && (
        <Text style={styles.progressDetail}>
          Latest dilation: {progress.latest_dilation_cm} cm
          {progress.expected_dilation_cm != null ? ` (expected ${progress.expected_dilation_cm} cm)` : ""}
        </Text>
      )}
      {/* Outstanding observations are never suppressed, regardless of status. */}
      {progress.outstanding_observations.length > 0 && (
        <View style={styles.outstandingBlock} testID="partograph-outstanding">
          <Text style={styles.outstandingTitle}>Outstanding observations</Text>
          {progress.outstanding_observations.map((line, i) => (
            <Text key={i} style={styles.outstandingLine}>
              • {line}
            </Text>
          ))}
        </View>
      )}
    </View>
  );
}

// --- Partograph workspace -------------------------------------------------------------------

const PARTOGRAPH_FORM_KEY = "impilo.labour.partograph.observation.v1";

export function PartographWorkspace() {
  const qc = useQueryClient();
  const [patientId, setPatientId] = useState("");
  const [encounterId, setEncounterId] = useState("");
  const [recording, setRecording] = useState(false);
  const [answers, setAnswers] = useState<Record<string, unknown>>({});
  const [justRecorded, setJustRecorded] = useState<AddPartographPointResult | null>(null);

  const form = useGovernedForm(PARTOGRAPH_FORM_KEY);

  const active = useQuery({
    queryKey: ["maternity-partograph-active", patientId, encounterId],
    queryFn: () => getActivePartograph(patientId, encounterId || undefined),
    enabled: patientId.trim().length > 0,
  });

  const sessionId =
    active.data?.partographActive === true ? active.data.session.session_id : null;

  const detail = useQuery({
    queryKey: ["maternity-partograph-detail", sessionId],
    queryFn: () => getPartograph(sessionId as string),
    enabled: !!sessionId,
  });

  const openMutation = useMutation({
    mutationFn: () => openPartograph({ patientId, encounterId: encounterId || undefined }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["maternity-partograph-active", patientId, encounterId] });
    },
  });

  const pointMutation = useMutation({
    mutationFn: () => addPartographPoint(sessionId as string, answers),
    onSuccess: (result) => {
      // The assessment must reach the person who just recorded it immediately — do not wait for
      // a refetch round-trip (contract §4.3).
      setJustRecorded(result);
      // Never carry a value forward into the next entry (contract §4.5).
      setAnswers({});
      setRecording(false);
      void qc.invalidateQueries({ queryKey: ["maternity-partograph-detail", sessionId] });
      void qc.invalidateQueries({ queryKey: ["maternity-partograph-active", patientId, encounterId] });
    },
  });

  const closeMutation = useMutation({
    mutationFn: () => closePartograph(sessionId as string, {}),
    onSuccess: () => {
      setJustRecorded(null);
      void qc.invalidateQueries({ queryKey: ["maternity-partograph-active", patientId, encounterId] });
    },
  });

  const liveProgress = justRecorded?.progress ?? (active.data?.partographActive ? active.data.session.progress : null);

  return (
    <ScrollView style={styles.workspace} testID="partograph-workspace">
      <Text style={styles.workspaceTitle}>Partograph</Text>
      <PatientIdentifiers
        patientId={patientId}
        setPatientId={setPatientId}
        encounterId={encounterId}
        setEncounterId={setEncounterId}
      />

      <MaternitySummaryHeader patientId={patientId} />

      {!patientId.trim() && <Text style={styles.hint}>Enter a patient ID to open or view a labour chart.</Text>}

      {patientId.trim().length > 0 && active.isLoading && (
        <Text style={styles.hint}>Checking for an open partograph…</Text>
      )}

      {patientId.trim().length > 0 && active.isError && (
        <UnavailableBanner error={active.error} subject="partograph" />
      )}

      {active.data?.partographActive === false && (
        <View style={styles.noSessionBox} testID="partograph-no-session">
          <Text style={styles.noSessionText}>No partograph is open for this patient.</Text>
          <Pressable
            style={styles.primaryBtn}
            onPress={() => openMutation.mutate()}
            disabled={openMutation.isPending}
            testID="partograph-open"
          >
            <Text style={styles.primaryBtnText}>{openMutation.isPending ? "Opening…" : "Open partograph"}</Text>
          </Pressable>
          {openMutation.isError && <Text style={styles.errorText}>Could not open a session. Try again.</Text>}
        </View>
      )}

      {active.data?.partographActive === true && sessionId && (
        <View style={styles.sessionBox}>
          <Text style={styles.sessionMeta}>
            Session opened {new Date(active.data.session.started_at).toLocaleString()} · {active.data.session.status}
          </Text>

          {liveProgress && <ProgressPanel progress={liveProgress} />}

          {detail.data && detail.data.points.length > 0 && (
            <View style={styles.pointsList} testID="partograph-points">
              <Text style={styles.sectionTitle}>Recorded observations ({detail.data.points.length})</Text>
              {detail.data.points
                .slice()
                .reverse()
                .slice(0, 5)
                .map((p) => (
                  <Text key={p.observation_id} style={styles.pointLine}>
                    {new Date(p.observed_at).toLocaleString()} — {p.phase}
                    {p.cervical_dilation_cm != null ? ` · ${p.cervical_dilation_cm} cm` : ""}
                    {p.fetal_heart_rate_bpm != null ? ` · FHR ${p.fetal_heart_rate_bpm}` : ""}
                  </Text>
                ))}
            </View>
          )}

          {!recording && (
            <Pressable
              style={styles.primaryBtn}
              onPress={() => {
                setAnswers({});
                setRecording(true);
              }}
              testID="partograph-record-open"
            >
              <Text style={styles.primaryBtnText}>Record observation</Text>
            </Pressable>
          )}

          {recording && form.definition && (
            <View style={styles.form} testID="partograph-form">
              <GovernedFormFields definition={form.definition} answers={answers} setAnswers={setAnswers} />
              <Pressable
                style={[
                  styles.primaryBtn,
                  !requiredFieldsSatisfied(form.definition, answers) && styles.primaryBtnDisabled,
                ]}
                disabled={!requiredFieldsSatisfied(form.definition, answers) || pointMutation.isPending}
                onPress={() => pointMutation.mutate()}
                testID="partograph-submit"
              >
                <Text style={styles.primaryBtnText}>
                  {pointMutation.isPending ? "Recording…" : "Record observation"}
                </Text>
              </Pressable>
              <Pressable style={styles.secondaryBtn} onPress={() => setRecording(false)}>
                <Text style={styles.secondaryBtnText}>Cancel</Text>
              </Pressable>
              {pointMutation.isError && (
                <Text style={styles.errorText} accessibilityRole="alert">
                  Could not record this observation. It has not been saved — try again.
                </Text>
              )}
            </View>
          )}
          {recording && form.isLoading && <Text style={styles.hint}>Loading the observation form…</Text>}
          {recording && !form.isLoading && !form.definition && (
            <Text style={styles.errorText}>The observation form definition is unavailable.</Text>
          )}

          <Pressable
            style={styles.secondaryBtn}
            onPress={() => closeMutation.mutate()}
            disabled={closeMutation.isPending}
            testID="partograph-close"
          >
            <Text style={styles.secondaryBtnText}>{closeMutation.isPending ? "Closing…" : "Close session"}</Text>
          </Pressable>
        </View>
      )}

      <View style={styles.sectionDivider} />
      <BirthDestinationSection />
    </ScrollView>
  );
}

// --- CTG workspace ---------------------------------------------------------------------------

const CTG_FORM_KEY = "impilo.labour.ctg.annotation.v1";

const SEVERITY_COLOR: Record<string, string> = {
  NORMAL: "#14532D",
  NON_REASSURING: "#78350F",
  ABNORMAL: "#7F1D1D",
};

/** One chunk on the trace timeline. A gap is drawn as a gap, never joined across (contract §4.6). */
function ChunkSegment({ chunk }: { chunk: CtgChunk }) {
  const hasGap = chunk.missing_sample_count > 0;
  return (
    <View
      style={[styles.chunkSegment, hasGap && styles.chunkSegmentGap]}
      testID={`ctg-chunk-${chunk.chunk_id}`}
    >
      <Text style={styles.chunkLabel}>
        {chunk.channel} · {new Date(chunk.started_at).toLocaleTimeString()} · {chunk.sample_count} samples
      </Text>
      {hasGap && (
        <Text style={styles.chunkGapText} testID={`ctg-chunk-gap-${chunk.chunk_id}`}>
          Gap — {chunk.missing_sample_count} sample{chunk.missing_sample_count === 1 ? "" : "s"} not captured.
          Not interpolated: a flat line here would look identical to one that was measured.
        </Text>
      )}
    </View>
  );
}

export function CtgWorkspace() {
  const qc = useQueryClient();
  const [patientId, setPatientId] = useState("");
  const [encounterId, setEncounterId] = useState("");
  const [annotating, setAnnotating] = useState(false);
  const [answers, setAnswers] = useState<Record<string, unknown>>({});

  const form = useGovernedForm(CTG_FORM_KEY);

  const active = useQuery({
    queryKey: ["maternity-ctg-active", patientId, encounterId],
    queryFn: () => getActiveCtgSession(patientId, encounterId || undefined),
    enabled: patientId.trim().length > 0,
  });

  const sessionId = active.data?.ctgActive === true ? active.data.session.session_id : null;

  const detail = useQuery({
    queryKey: ["maternity-ctg-detail", sessionId],
    queryFn: () => getCtgSession(sessionId as string),
    enabled: !!sessionId,
  });

  const chunks = useQuery({
    queryKey: ["maternity-ctg-chunks", sessionId],
    queryFn: () => getCtgChunks(sessionId as string),
    enabled: !!sessionId,
  });

  const openMutation = useMutation({
    mutationFn: () => openCtgSession({ patientId, encounterId: encounterId || undefined }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["maternity-ctg-active", patientId, encounterId] });
    },
  });

  const annotateMutation = useMutation({
    mutationFn: () => addCtgAnnotation(sessionId as string, answers),
    onSuccess: () => {
      setAnswers({});
      setAnnotating(false);
      void qc.invalidateQueries({ queryKey: ["maternity-ctg-detail", sessionId] });
    },
  });

  return (
    <ScrollView style={styles.workspace} testID="ctg-workspace">
      <Text style={styles.workspaceTitle}>CTG interpretation</Text>
      <PatientIdentifiers
        patientId={patientId}
        setPatientId={setPatientId}
        encounterId={encounterId}
        setEncounterId={setEncounterId}
      />

      {!patientId.trim() && <Text style={styles.hint}>Enter a patient ID to open or view fetal monitoring.</Text>}

      {patientId.trim().length > 0 && active.isLoading && (
        <Text style={styles.hint}>Checking for an open monitoring session…</Text>
      )}

      {patientId.trim().length > 0 && active.isError && (
        <UnavailableBanner error={active.error} subject="CTG trace" />
      )}

      {active.data?.ctgActive === false && (
        <View style={styles.noSessionBox} testID="ctg-no-session">
          <Text style={styles.noSessionText}>No monitoring session is open for this patient.</Text>
          <Pressable
            style={styles.primaryBtn}
            onPress={() => openMutation.mutate()}
            disabled={openMutation.isPending}
            testID="ctg-open"
          >
            <Text style={styles.primaryBtnText}>{openMutation.isPending ? "Opening…" : "Open monitoring session"}</Text>
          </Pressable>
        </View>
      )}

      {active.data?.ctgActive === true && sessionId && (
        <View style={styles.sessionBox}>
          <Text style={styles.sessionMeta}>
            Session opened {new Date(active.data.session.started_at).toLocaleString()} · {active.data.session.status}
            {active.data.session.device_id ? ` · device ${active.data.session.device_id}` : ""}
          </Text>

          <Text style={styles.sectionTitle}>Trace</Text>
          {chunks.isLoading && <Text style={styles.hint}>Loading trace…</Text>}
          {chunks.data && chunks.data.length === 0 && (
            <Text style={styles.hint}>No trace data received from a monitor yet.</Text>
          )}
          {chunks.data && chunks.data.length > 0 && (
            <View testID="ctg-trace">
              {chunks.data.map((c) => (
                <ChunkSegment key={c.chunk_id} chunk={c} />
              ))}
            </View>
          )}

          {detail.data && detail.data.annotations.length > 0 && (
            <View style={styles.pointsList} testID="ctg-annotations">
              <Text style={styles.sectionTitle}>Clinician findings ({detail.data.annotations.length})</Text>
              {detail.data.annotations
                .slice()
                .reverse()
                .map((a) => (
                  <View key={a.annotation_id} style={styles.annotationRow}>
                    <Text style={styles.pointLine}>
                      {new Date(a.recorded_at).toLocaleString()} — {a.category}
                      {a.value ? ` (${a.value})` : ""}
                    </Text>
                    {a.severity && (
                      <Text style={[styles.severityBadge, { color: SEVERITY_COLOR[a.severity] ?? colors.gray[700] }]}>
                        {a.severity}
                      </Text>
                    )}
                  </View>
                ))}
            </View>
          )}

          {!annotating && (
            <Pressable
              style={styles.primaryBtn}
              onPress={() => {
                setAnswers({});
                setAnnotating(true);
              }}
              testID="ctg-annotate-open"
            >
              <Text style={styles.primaryBtnText}>Record a finding</Text>
            </Pressable>
          )}

          {annotating && form.definition && (
            <View style={styles.form} testID="ctg-form">
              <GovernedFormFields definition={form.definition} answers={answers} setAnswers={setAnswers} />
              <Pressable
                style={[
                  styles.primaryBtn,
                  !requiredFieldsSatisfied(form.definition, answers) && styles.primaryBtnDisabled,
                ]}
                disabled={!requiredFieldsSatisfied(form.definition, answers) || annotateMutation.isPending}
                onPress={() => annotateMutation.mutate()}
                testID="ctg-annotate-submit"
              >
                <Text style={styles.primaryBtnText}>
                  {annotateMutation.isPending ? "Recording…" : "Record finding"}
                </Text>
              </Pressable>
              <Pressable style={styles.secondaryBtn} onPress={() => setAnnotating(false)}>
                <Text style={styles.secondaryBtnText}>Cancel</Text>
              </Pressable>
              {annotateMutation.isError && (
                <Text style={styles.errorText} accessibilityRole="alert">
                  Could not record this finding — try again.
                </Text>
              )}
            </View>
          )}
          {annotating && form.isLoading && <Text style={styles.hint}>Loading the annotation form…</Text>}
        </View>
      )}
    </ScrollView>
  );
}

// --- Maternal near-miss workspace ------------------------------------------------------------

const NEAR_MISS_FORM_KEY = "impilo.maternal.nearmiss.assessment.v1";
const LINK_PREFIX = "nearMiss.";

function bareId(linkId: string): string {
  return linkId.startsWith(LINK_PREFIX) ? linkId.slice(LINK_PREFIX.length) : linkId;
}

/** Humanises a tier code like `NEAR_MISS_CARDIOVASCULAR` into "Cardiovascular". */
function humaniseTierCode(code: string): string {
  return code
    .replace(/^NEAR_MISS_/, "")
    .split("_")
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(" ");
}

/**
 * Whether a submitted form-21 answer set failed the BFF's read, and why. Mirrors the web lane's
 * `useMaternalNearMiss.ts` — see that file's doc comment for the full shape reference, since both
 * surfaces read the same controller.
 */
function refusalCopy(error: unknown): { title: string; body: string; list?: string[] } | null {
  if (!(error instanceof ApiError)) return null;
  if (error.code === "unrecognised_form_answer") {
    return {
      title: "These answers could not be read",
      body:
        error.message ||
        "An unreadable answer being silently treated as a negative is how a near-miss is recorded as a normal birth. Correct them and resubmit.",
      list: (error.details?.unrecognised_answers as string[] | undefined) ?? undefined,
    };
  }
  if (error.status >= 500) {
    return {
      title: "The near-miss criteria could not be evaluated",
      body:
        error.message ||
        "This is not a finding of \u2018no near-miss\u2019. Classify clinically and resubmit when the service returns.",
    };
  }
  return { title: "Could not classify this assessment", body: "Nothing was recorded — try again." };
}

export function MaternalNearMissWorkspace() {
  const [answers, setAnswers] = useState<Record<string, unknown>>({});
  const [result, setResult] = useState<NearMissClassifyResult | null>(null);

  const form = useGovernedForm(NEAR_MISS_FORM_KEY);

  const labelById = useMemo(() => {
    const map = new Map<string, string>();
    for (const section of form.definition?.sections ?? []) {
      for (const f of section.fields) {
        map.set(bareId(f.id), f.label);
      }
    }
    return map;
  }, [form.definition]);

  const classify = useMutation({
    mutationFn: () => classifyNearMissForm(answers, NEAR_MISS_FORM_KEY),
    onSuccess: (r) => setResult(r),
  });

  function handleClear() {
    setAnswers({});
    setResult(null);
    classify.reset();
  }

  const refusal = classify.isError ? refusalCopy(classify.error) : null;
  const blank = result?.meta.criteria_left_blank ?? [];
  const data = result?.data;
  const inconclusive = !!data && (data.status === "INDETERMINATE" || data.provisional);

  return (
    <ScrollView style={styles.workspace} testID="near-miss-workspace">
      <Text style={styles.workspaceTitle}>Maternal near-miss assessment</Text>
      <Text style={styles.hint}>
        WHO organ-dysfunction criteria — identification only, nothing is saved. Leave a field blank
        if it was not assessed; a blank is never treated as a negative finding.
      </Text>

      {form.isLoading && <Text style={styles.hint}>Loading the near-miss assessment form…</Text>}
      {!form.isLoading && !form.definition && (
        <Text style={styles.errorText}>The near-miss assessment form definition is unavailable.</Text>
      )}

      {form.definition && (
        <View style={styles.form} testID="near-miss-form">
          <GovernedFormFields definition={form.definition} answers={answers} setAnswers={setAnswers} />

          <Pressable
            style={[styles.primaryBtn, classify.isPending && styles.primaryBtnDisabled]}
            disabled={classify.isPending}
            onPress={() => classify.mutate()}
            testID="near-miss-submit"
          >
            <Text style={styles.primaryBtnText}>
              {classify.isPending ? "Classifying…" : "Classify near-miss"}
            </Text>
          </Pressable>
          <Pressable style={styles.secondaryBtn} onPress={handleClear} testID="near-miss-clear">
            <Text style={styles.secondaryBtnText}>Clear form</Text>
          </Pressable>

          {refusal && (
            <View style={styles.unavailableBanner} testID="near-miss-refusal" accessibilityRole="alert">
              <Text style={styles.unavailableTitle}>{refusal.title}</Text>
              <Text style={styles.unavailableBody}>{refusal.body}</Text>
              {refusal.list && refusal.list.length > 0 && (
                <View style={{ marginTop: 4 }}>
                  {refusal.list.map((a) => (
                    <Text key={a} style={styles.unavailableBody}>
                      • {a}
                    </Text>
                  ))}
                </View>
              )}
            </View>
          )}

          {data && (
            <View style={styles.sessionBox} testID="near-miss-outcome">
              {data.is_near_miss ? (
                <View style={[styles.progressBox, { backgroundColor: "#FEE2E2", borderColor: "#DC2626" }]}>
                  <Text style={[styles.progressStatus, { color: "#7F1D1D" }]}>
                    Near-miss identified — {data.classification_name}
                  </Text>
                  {data.provisional && (
                    <Text style={[styles.progressAction, { color: "#7F1D1D" }]}>
                      Provisional: a more severe finding above this one could not be excluded.
                    </Text>
                  )}
                  {data.rationale && <Text style={styles.progressDetail}>{data.rationale}</Text>}
                  {data.review_note && <Text style={styles.progressDetail}>{data.review_note}</Text>}
                </View>
              ) : inconclusive ? (
                <View style={[styles.progressBox, { backgroundColor: "#FEE2E2", borderColor: "#DC2626" }]} testID="near-miss-indeterminate">
                  <Text style={[styles.progressStatus, { color: "#7F1D1D" }]}>
                    Indeterminate — a near-miss cannot be ruled out
                  </Text>
                  <Text style={[styles.progressDetail, { color: "#7F1D1D" }]}>
                    {data.rationale ||
                      "A severity row could not be excluded on what was recorded. This is not the same as \u2018no near-miss\u2019."}
                  </Text>
                  {data.unresolved_criteria.length > 0 && (
                    <View style={styles.outstandingBlock}>
                      <Text style={styles.outstandingTitle}>Could not exclude</Text>
                      {data.unresolved_criteria.map((code) => (
                        <Text key={code} style={styles.outstandingLine}>
                          • {humaniseTierCode(code)}
                        </Text>
                      ))}
                    </View>
                  )}
                  {data.missing_inputs.length > 0 && (
                    <View style={styles.outstandingBlock}>
                      <Text style={styles.outstandingTitle}>Complete these to resolve it</Text>
                      {data.missing_inputs.map((linkId) => (
                        <Text key={linkId} style={styles.outstandingLine}>
                          • {labelById.get(bareId(linkId)) ?? linkId}
                        </Text>
                      ))}
                    </View>
                  )}
                </View>
              ) : (
                <View style={[styles.progressBox, { backgroundColor: "#D1FAE5", borderColor: "#22C55E" }]}>
                  <Text style={[styles.progressStatus, { color: "#14532D" }]}>
                    WHO near-miss criteria not met
                  </Text>
                  <Text style={styles.progressDetail}>
                    {data.rationale ||
                      "No organ-dysfunction criterion was met on the information recorded. This is not a statement that the woman was well."}
                  </Text>
                </View>
              )}

              {blank.length > 0 && (
                <View style={styles.pointsList} testID="near-miss-blank">
                  <Text style={styles.sectionTitle}>Left blank — not assessed, not recorded as absent</Text>
                  {blank.map((field) => (
                    <Text key={field} style={styles.pointLine}>
                      • {labelById.get(field) ?? field}
                    </Text>
                  ))}
                </View>
              )}
            </View>
          )}
        </View>
      )}

      <View style={styles.sectionDivider} />
      <NearMissIndicatorsSection />
    </ScrollView>
  );
}

// --- Severe maternal outcome indicators -------------------------------------------------------
//
// Backend: `POST /internal/v1/clinical/maternal/near-miss/indicators` — see
// `computeNearMissIndicators`'s doc comment in maternityService.ts. A null ratio/index is
// undefined, not zero, and the indeterminate count is always shown even though it feeds neither.

const INDICATOR_OUTCOME_OPTIONS: NearMissIndicatorOutcome[] = [
  "NEAR_MISS",
  "MATERNAL_DEATH",
  "NEAR_MISS_INDETERMINATE",
  "NOT_SEVERE",
];

function formatIndicatorValue(value: number | null, suffix: string): string {
  return value === null ? "Not computable (undefined)" : `${value.toFixed(2)}${suffix}`;
}

function NearMissIndicatorsSection() {
  const [period, setPeriod] = useState("");
  const [outcomes, setOutcomes] = useState<NearMissIndicatorOutcome[]>(["NEAR_MISS"]);

  const compute = useMutation({
    mutationFn: () => computeNearMissIndicators(period, outcomes.map((outcome) => ({ outcome }))),
  });

  function addCase() {
    setOutcomes((prev) => [...prev, "NEAR_MISS"]);
  }

  function setCaseOutcome(index: number, outcome: NearMissIndicatorOutcome) {
    setOutcomes((prev) => prev.map((o, i) => (i === index ? outcome : o)));
  }

  function removeCase(index: number) {
    setOutcomes((prev) => (prev.length > 1 ? prev.filter((_, i) => i !== index) : prev));
  }

  const result: NearMissIndicatorsResult | undefined = compute.data;
  const isUnavailable = compute.isError && compute.error instanceof ApiError && compute.error.status >= 500;

  return (
    <View style={styles.workspace} testID="near-miss-indicators-section">
      <Text style={styles.workspaceTitle}>Severe maternal outcome indicators</Text>
      <Text style={styles.hint}>
        The near-miss-to-death ratio and mortality index for a reporting period. Computed over
        zero deaths, the ratio is undefined — never rendered as zero.
      </Text>

      <TextInput
        style={styles.input}
        placeholder="Reporting period, e.g. 2026-Q2"
        value={period}
        onChangeText={setPeriod}
        testID="near-miss-indicators-period"
      />

      {outcomes.map((outcome, idx) => (
        <View key={idx} style={styles.identifierRow} testID={`near-miss-indicators-row-${idx}`}>
          <View style={[styles.options, { flex: 1 }]}>
            {INDICATOR_OUTCOME_OPTIONS.map((opt) => (
              <Pressable
                key={opt}
                onPress={() => setCaseOutcome(idx, opt)}
                style={[styles.option, outcome === opt && styles.optionSelected]}
                testID={`near-miss-indicators-outcome-${idx}-${opt}`}
              >
                <Text style={[styles.optionText, outcome === opt && styles.optionTextSelected]}>{opt}</Text>
              </Pressable>
            ))}
          </View>
          <Pressable onPress={() => removeCase(idx)} disabled={outcomes.length === 1} testID={`near-miss-indicators-remove-${idx}`}>
            <Text style={styles.secondaryBtnText}>Remove</Text>
          </Pressable>
        </View>
      ))}

      <Pressable style={styles.secondaryBtn} onPress={addCase} testID="near-miss-indicators-add-row">
        <Text style={styles.secondaryBtnText}>Add case</Text>
      </Pressable>

      <Pressable
        style={[styles.primaryBtn, compute.isPending && styles.primaryBtnDisabled]}
        disabled={compute.isPending}
        onPress={() => compute.mutate()}
        testID="near-miss-indicators-compute"
      >
        <Text style={styles.primaryBtnText}>{compute.isPending ? "Computing…" : "Compute"}</Text>
      </Pressable>

      {isUnavailable && (
        <View style={styles.unavailableBanner} testID="near-miss-indicators-unavailable" accessibilityRole="alert">
          <Text style={styles.unavailableTitle}>The near-miss criteria service could not be reached</Text>
          <Text style={styles.unavailableBody}>Nothing was computed. This is not a ratio of zero.</Text>
        </View>
      )}

      {compute.isError && !isUnavailable && (
        <Text style={styles.errorText}>Some cases could not be classified. Try again.</Text>
      )}

      {result && !isUnavailable && (
        <View style={styles.pointsList} testID="near-miss-indicators-result">
          <Text style={styles.pointLine}>Near-miss: {result.near_miss_count}</Text>
          <Text style={styles.pointLine}>Maternal death: {result.maternal_death_count}</Text>
          {/* Never dropped, even though it feeds neither ratio below. */}
          <Text style={styles.pointLine}>Indeterminate: {result.indeterminate_count}</Text>
          <Text style={styles.pointLine}>Severe maternal outcomes: {result.severe_maternal_outcome_count}</Text>
          <Text style={styles.progressStatus} testID="near-miss-indicators-ratio">
            Near-miss-to-death ratio: {formatIndicatorValue(result.near_miss_to_death_ratio, ":1")}
          </Text>
          <Text style={styles.progressStatus} testID="near-miss-indicators-mortality-index">
            Mortality index: {formatIndicatorValue(result.mortality_index, "%")}
          </Text>
          {result.note && <Text style={styles.progressDetail}>{result.note}</Text>}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  workspace: { flex: 1 },
  workspaceTitle: { fontSize: 16, fontWeight: "700", color: colors.gray[900], marginBottom: 8 },
  identifierRow: { flexDirection: "row", gap: 8, marginBottom: 10 },
  hint: { fontSize: 13, color: colors.gray[500], marginTop: 4 },
  sectionDivider: { height: 1, backgroundColor: colors.gray[200], marginVertical: 16 },
  summaryHeader: {
    borderWidth: 1,
    borderColor: colors.gray[200],
    borderRadius: 10,
    padding: 10,
    marginBottom: 10,
    gap: 2,
  },
  summaryLine: { fontSize: 12, color: colors.gray[700] },
  errorText: { fontSize: 13, color: "#B91C1C", marginTop: 6 },
  section: { marginBottom: 10 },
  sectionTitle: { fontSize: 13, fontWeight: "600", color: colors.gray[700], marginBottom: 6, marginTop: 8 },
  field: { marginBottom: 10 },
  label: { fontSize: 12, color: colors.gray[700], marginBottom: 3 },
  input: {
    borderWidth: 1,
    borderColor: colors.gray[300],
    borderRadius: 8,
    padding: 8,
    fontSize: 13,
  },
  datetimeRow: { flexDirection: "row", gap: 6, alignItems: "center" },
  nowBtn: { paddingHorizontal: 10, paddingVertical: 8, borderRadius: 8, backgroundColor: colors.gray[200] },
  nowBtnText: { fontSize: 12, fontWeight: "600", color: colors.gray[700] },
  options: { flexDirection: "row", flexWrap: "wrap", gap: 6 },
  option: {
    paddingVertical: 6,
    paddingHorizontal: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.gray[300],
  },
  optionSelected: { backgroundColor: "#DBEAFE", borderColor: "#3B82F6" },
  optionText: { fontSize: 12, color: colors.gray[700] },
  optionTextSelected: { color: "#1E3A8A", fontWeight: "600" },
  notAssessedHint: { fontSize: 11, color: colors.gray[500], alignSelf: "center", marginLeft: 4 },
  unavailableBanner: {
    backgroundColor: "#FEF2F2",
    borderWidth: 1,
    borderColor: "#FECACA",
    borderRadius: 10,
    padding: 12,
    marginTop: 8,
  },
  unavailableTitle: { fontSize: 14, fontWeight: "700", color: "#7F1D1D" },
  unavailableBody: { fontSize: 12, color: "#991B1B", marginTop: 4 },
  noSessionBox: { marginTop: 8, gap: 8 },
  noSessionText: { fontSize: 13, color: colors.gray[600] },
  sessionBox: { marginTop: 8, gap: 8 },
  sessionMeta: { fontSize: 12, color: colors.gray[500] },
  progressBox: { borderWidth: 1, borderRadius: 10, padding: 12, gap: 6 },
  progressStatus: { fontSize: 14, fontWeight: "700" },
  progressAction: { fontSize: 12 },
  progressDetail: { fontSize: 12, color: colors.gray[700] },
  outstandingBlock: { marginTop: 4, gap: 2 },
  outstandingTitle: { fontSize: 12, fontWeight: "700", color: "#7F1D1D" },
  outstandingLine: { fontSize: 12, color: "#7F1D1D" },
  pointsList: { gap: 4 },
  pointLine: { fontSize: 12, color: colors.gray[700] },
  annotationRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  severityBadge: { fontSize: 11, fontWeight: "700" },
  chunkSegment: {
    borderWidth: 1,
    borderColor: colors.gray[200],
    borderRadius: 8,
    padding: 8,
    marginBottom: 6,
  },
  chunkSegmentGap: {
    borderStyle: "dashed",
    borderColor: "#F59E0B",
    backgroundColor: "#FFFBEB",
  },
  chunkLabel: { fontSize: 12, color: colors.gray[700] },
  chunkGapText: { fontSize: 11, color: "#92400E", marginTop: 4 },
  form: { marginTop: 8, gap: 4 },
  primaryBtn: { backgroundColor: "#2563EB", borderRadius: 10, paddingVertical: 12, alignItems: "center", marginTop: 4 },
  primaryBtnDisabled: { opacity: 0.5 },
  primaryBtnText: { color: "#fff", fontWeight: "700", fontSize: 14 },
  secondaryBtn: { paddingVertical: 10, alignItems: "center" },
  secondaryBtnText: { color: "#2563EB", fontWeight: "600", fontSize: 13 },
});
