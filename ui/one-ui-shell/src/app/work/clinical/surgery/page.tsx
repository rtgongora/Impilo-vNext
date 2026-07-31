"use client";

/**
 * Surgical episodes — S1 episode spine, S2 general surgical assessment, S3 decision record.
 * Route: /work/clinical/surgery | Zone: operations
 *
 * The clinician surface that makes surgery-service capability reachable: find a patient's
 * surgical episodes, open a new episode (CC-5-anchored: journey/encounter + operative
 * indication), work assessment/decision, and the course-of-care panels (prehab, complications,
 * longitudinal objects, follow-up, waitlist revalidation) via the real BFF routes.
 *
 * EMPTY VS UNKNOWN VS UNAVAILABLE, rendered as three distinct states, never collapsed:
 *   - isError                → "could not read the surgical record" (an error banner, never an
 *                              empty list — the "no conditions for every patient" bug class)
 *   - resolved []            → the patient genuinely has no surgical episodes
 *   - 404 on assessment/decision/followup (isNotRecorded) → "not recorded yet", an honest gap
 *                              that is NOT a failure and NOT rendered as one
 */

import { useState } from "react";
import { AlertTriangle, Loader2, Search } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { ComplicationPathwayPanel } from "@/components/clinical/surgery/ComplicationPathwayPanel";
import { FollowupPanel } from "@/components/clinical/surgery/FollowupPanel";
import { LongitudinalObjectsPanel } from "@/components/clinical/surgery/LongitudinalObjectsPanel";
import { PrehabPanel } from "@/components/clinical/surgery/PrehabPanel";
import { WaitlistRevalidationPanel } from "@/components/clinical/surgery/WaitlistRevalidationPanel";
import {
  isNotRecorded,
  useAddEpisodeSpecialty,
  useEpisodeSpecialties,
  useOpenSurgicalEpisode,
  useRecordSurgicalAssessment,
  useRecordSurgicalDecision,
  useRemoveEpisodeSpecialty,
  useReopenSurgicalEpisode,
  useSurgicalAssessment,
  useSurgicalDecision,
  useSurgicalEpisodes,
  useTransferEpisodeLead,
  useTransitionSurgicalEpisode,
  type SurgicalAssessment,
  type SurgicalDecision,
  type SurgicalEpisode,
  type SurgicalEpisodeStatus,
} from "@/hooks/queries/useSurgeryEpisodes";

/**
 * REOPENED is deliberately absent: it is reachable only through the audited reopen route,
 * which carries a mandatory reason. Offering it here as a plain transition would let a
 * clinician reopen a case with no recorded cause — and the server refuses it anyway.
 */
const STATUS_OPTIONS: SurgicalEpisodeStatus[] = [
  "ASSESSMENT",
  "LISTED_FOR_SURGERY",
  "OPERATED",
  "CLOSED",
  "ABANDONED",
];

const STATUS_STYLE: Record<string, string> = {
  ASSESSMENT: "bg-blue-50 text-blue-800",
  LISTED_FOR_SURGERY: "bg-amber-50 text-amber-800",
  OPERATED: "bg-emerald-50 text-emerald-800",
  CLOSED: "bg-gray-100 text-gray-600",
  ABANDONED: "bg-gray-100 text-gray-600",
  REOPENED: "bg-rose-50 text-rose-800",
};

/** The fifteen surgical specialties, matching surgery-service's own CHECK constraint. */
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

/** Reopening is only offered from the states surgery-service actually accepts it from. */
const REOPENABLE_FROM: SurgicalEpisodeStatus[] = ["OPERATED", "CLOSED"];

/** S2 free-text elements, complete against SurgicalAssessmentService's own field list. */
const ASSESSMENT_TEXT_FIELDS: Array<{ key: keyof SurgicalAssessment; label: string }> = [
  { key: "presentingProblem", label: "Presenting problem" },
  { key: "symptomTimeline", label: "Symptom timeline" },
  { key: "examinationFindings", label: "Examination findings" },
  { key: "differentialDiagnosisNotes", label: "Differential diagnosis" },
  { key: "surgicalRiskAssessment", label: "Surgical risk assessment" },
  { key: "woundHealingHistory", label: "Wound healing history" },
  { key: "bleedingThrombosisHistory", label: "Bleeding / thrombosis history" },
  { key: "infectionHistory", label: "Infection history" },
  { key: "anaestheticHistoryNotes", label: "Anaesthetic history" },
  { key: "previousSurgeryNotes", label: "Previous surgery notes" },
  { key: "allergyReviewNotes", label: "Allergy review notes" },
  { key: "medicationReconciliationNotes", label: "Medication reconciliation notes" },
  { key: "nutritionAssessment", label: "Nutrition" },
  { key: "frailtyNotes", label: "Frailty" },
  { key: "socialSupportNotes", label: "Social support" },
  { key: "transportPlan", label: "Transport plan" },
  { key: "workLivelihoodImpact", label: "Work / livelihood impact" },
  { key: "patientGoals", label: "Patient goals" },
];

/** The plain reviewed flags an assessor may set directly (the ref-paired ones are CHECK-gated server-side). */
const ASSESSMENT_FLAG_FIELDS: Array<{ key: keyof SurgicalAssessment; label: string }> = [
  { key: "previousSurgeryReviewed", label: "Previous surgery reviewed" },
  { key: "allergiesReviewed", label: "Allergies reviewed (pct_allergies)" },
  { key: "medicationsReviewed", label: "Medications reconciled (OROS)" },
];

/** S3 free-text elements, complete against SurgicalDecisionService's own field list. */
const DECISION_TEXT_FIELDS: Array<{ key: keyof SurgicalDecision; label: string }> = [
  { key: "naturalHistory", label: "Natural history if not operated" },
  { key: "expectedBenefit", label: "Expected benefit" },
  { key: "materialRisksConsidered", label: "Material risks the surgeon weighed" },
  { key: "anaestheticImplications", label: "Anaesthetic implications" },
  { key: "bloodImplications", label: "Blood / transfusion implications" },
  { key: "functionalImplications", label: "Functional implications" },
  { key: "fertilityImplications", label: "Fertility implications" },
  { key: "stomaPossibilityNotes", label: "Stoma possibility notes" },
  { key: "implantPossibilityNotes", label: "Implant possibility notes" },
  { key: "rehabilitationExpectation", label: "Rehabilitation expectation" },
  { key: "financialAccessImplications", label: "Financial / access implications" },
  { key: "patientPreference", label: "Patient preference" },
];

function FieldGrid<T extends object>({
  fields,
  draft,
  setDraft,
  testidPrefix,
}: {
  fields: Array<{ key: keyof T; label: string }>;
  draft: Partial<T>;
  setDraft: (d: Partial<T>) => void;
  testidPrefix: string;
}) {
  return (
    <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
      {fields.map((f) => (
        <label key={String(f.key)} className="block text-xs">
          <span className="font-semibold uppercase text-muted-foreground">{f.label}</span>
          <textarea
            rows={2}
            value={(draft[f.key] as string | null | undefined) ?? ""}
            onChange={(e) => setDraft({ ...draft, [f.key]: e.target.value })}
            className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1 text-sm"
            data-testid={`${testidPrefix}-${String(f.key)}`}
          />
        </label>
      ))}
    </div>
  );
}

function AssessmentPanel({ episodeId }: { episodeId: string }) {
  const assessmentQ = useSurgicalAssessment(episodeId);
  const record = useRecordSurgicalAssessment(episodeId);
  const [draft, setDraft] = useState<Partial<SurgicalAssessment>>({});
  const [editing, setEditing] = useState(false);

  const startEdit = () => {
    const base: Partial<SurgicalAssessment> = {};
    if (assessmentQ.data) {
      for (const f of ASSESSMENT_TEXT_FIELDS) {
        (base as Record<string, unknown>)[String(f.key)] = assessmentQ.data[f.key] ?? "";
      }
      for (const f of ASSESSMENT_FLAG_FIELDS) {
        (base as Record<string, unknown>)[String(f.key)] = assessmentQ.data[f.key] ?? false;
      }
    }
    setDraft(base);
    setEditing(true);
  };

  const save = () => {
    // Send only non-empty text values plus the flags — the PUT is a refinement, and an empty
    // string overwriting a recorded value with "" would be a silent erasure.
    const payload: Record<string, unknown> = {};
    for (const f of ASSESSMENT_TEXT_FIELDS) {
      const v = draft[f.key];
      if (typeof v === "string" && v.trim() !== "") payload[String(f.key)] = v;
    }
    for (const f of ASSESSMENT_FLAG_FIELDS) {
      if (typeof draft[f.key] === "boolean") payload[String(f.key)] = draft[f.key];
    }
    record.mutate(payload, { onSuccess: () => setEditing(false) });
  };

  return (
    <div className="mt-4 rounded-lg border border-gray-100 p-3" data-testid="surgery-assessment-panel">
      <div className="flex items-center justify-between">
        <h4 className="text-xs font-semibold uppercase text-muted-foreground">
          General surgical assessment (S2)
        </h4>
        {!editing && (
          <button
            type="button"
            onClick={startEdit}
            className="rounded-md border border-gray-200 px-2 py-1 text-xs"
            data-testid="surgery-assessment-edit"
          >
            {assessmentQ.data ? "Refine" : "Record"}
          </button>
        )}
      </div>

      {assessmentQ.isLoading ? (
        <p className="mt-2 text-sm text-muted-foreground">Loading assessment…</p>
      ) : assessmentQ.isError && !isNotRecorded(assessmentQ.error) ? (
        <p className="mt-2 text-sm text-danger" role="alert" data-testid="surgery-assessment-unavailable">
          Could not read the assessment. This is not the same as no assessment being recorded.
        </p>
      ) : !editing && assessmentQ.isError && isNotRecorded(assessmentQ.error) ? (
        <p className="mt-2 text-sm text-muted-foreground" data-testid="surgery-assessment-not-recorded">
          No assessment recorded for this episode yet.
        </p>
      ) : !editing && assessmentQ.data ? (
        <div className="mt-2 space-y-1 text-sm" data-testid="surgery-assessment-view">
          {ASSESSMENT_TEXT_FIELDS.filter((f) => assessmentQ.data?.[f.key]).map((f) => (
            <p key={String(f.key)}>
              <span className="text-xs font-semibold uppercase text-muted-foreground">{f.label}: </span>
              {String(assessmentQ.data?.[f.key])}
            </p>
          ))}
          <p className="text-xs text-muted-foreground">
            {ASSESSMENT_FLAG_FIELDS.map(
              (f) => `${f.label}: ${assessmentQ.data?.[f.key] ? "yes" : "no"}`,
            ).join(" · ")}
          </p>
          {assessmentQ.data.assessedBy && (
            <p className="text-xs text-muted-foreground">
              Last refined by {assessmentQ.data.assessedBy}
              {assessmentQ.data.assessedAt ? ` at ${assessmentQ.data.assessedAt}` : ""}
            </p>
          )}
        </div>
      ) : null}

      {editing && (
        <div className="mt-2">
          <FieldGrid<SurgicalAssessment>
            fields={ASSESSMENT_TEXT_FIELDS}
            draft={draft}
            setDraft={setDraft}
            testidPrefix="surgery-assessment-field"
          />
          <div className="mt-2 flex flex-wrap gap-3">
            {ASSESSMENT_FLAG_FIELDS.map((f) => (
              <label key={String(f.key)} className="flex items-center gap-1 text-xs">
                <input
                  type="checkbox"
                  checked={!!draft[f.key]}
                  onChange={(e) => setDraft({ ...draft, [f.key]: e.target.checked })}
                />
                {f.label}
              </label>
            ))}
          </div>
          <div className="mt-3 flex items-center gap-2">
            <button
              type="button"
              onClick={save}
              disabled={record.isPending}
              className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground disabled:opacity-50"
              data-testid="surgery-assessment-save"
            >
              {record.isPending ? "Saving…" : "Save assessment"}
            </button>
            <button
              type="button"
              onClick={() => setEditing(false)}
              className="rounded-md border border-gray-200 px-3 py-1.5 text-sm"
            >
              Cancel
            </button>
            {record.isError && (
              <span className="text-sm text-danger" role="alert" data-testid="surgery-assessment-save-failed">
                Save failed — the assessment has NOT been recorded.
              </span>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function DecisionPanel({ episodeId }: { episodeId: string }) {
  const decisionQ = useSurgicalDecision(episodeId);
  const record = useRecordSurgicalDecision(episodeId);
  const [draft, setDraft] = useState<Partial<SurgicalDecision>>({});
  const [finalDecision, setFinalDecision] = useState<string>("");
  const [forum, setForum] = useState<string>("");
  const [mdtRef, setMdtRef] = useState("");
  const [mdtSource, setMdtSource] = useState("PCT_MDT_DECISION");
  const [editing, setEditing] = useState(false);

  const startEdit = () => {
    const base: Partial<SurgicalDecision> = {};
    if (decisionQ.data) {
      for (const f of DECISION_TEXT_FIELDS) {
        (base as Record<string, unknown>)[String(f.key)] = decisionQ.data[f.key] ?? "";
      }
    }
    setDraft(base);
    setFinalDecision("");
    setForum(decisionQ.data?.decisionForum ?? "");
    setMdtRef(decisionQ.data?.mdtDecisionRef ?? "");
    setMdtSource(decisionQ.data?.mdtDecisionSource ?? "PCT_MDT_DECISION");
    setEditing(true);
  };

  const save = () => {
    const payload: Record<string, unknown> = {};
    for (const f of DECISION_TEXT_FIELDS) {
      const v = draft[f.key];
      if (typeof v === "string" && v.trim() !== "") payload[String(f.key)] = v;
    }
    // finalDecision travels only when explicitly chosen this save — the server stamps
    // decidedBy/decidedAt as an all-or-nothing trio with it.
    if (finalDecision) payload.finalDecision = finalDecision;
    // The forum travels as a set: MDT requires a board reference and its source; reverting to
    // INDIVIDUAL clears them. Sending a half-set is refused server-side by a CHECK constraint,
    // so the form sends whole states only.
    if (forum === "MDT") {
      payload.decisionForum = "MDT";
      payload.mdtDecisionRef = mdtRef.trim();
      payload.mdtDecisionSource = mdtSource;
    } else if (forum === "INDIVIDUAL") {
      payload.decisionForum = "INDIVIDUAL";
    }
    record.mutate(payload, { onSuccess: () => setEditing(false) });
  };

  const mdtIncomplete = forum === "MDT" && mdtRef.trim() === "";

  return (
    <div className="mt-4 rounded-lg border border-gray-100 p-3" data-testid="surgery-decision-panel">
      <div className="flex items-center justify-between">
        <h4 className="text-xs font-semibold uppercase text-muted-foreground">
          Surgical decision record (S3)
        </h4>
        {!editing && (
          <button
            type="button"
            onClick={startEdit}
            className="rounded-md border border-gray-200 px-2 py-1 text-xs"
            data-testid="surgery-decision-edit"
          >
            {decisionQ.data ? "Refine" : "Record"}
          </button>
        )}
      </div>

      {decisionQ.isLoading ? (
        <p className="mt-2 text-sm text-muted-foreground">Loading decision record…</p>
      ) : decisionQ.isError && !isNotRecorded(decisionQ.error) ? (
        <p className="mt-2 text-sm text-danger" role="alert" data-testid="surgery-decision-unavailable">
          Could not read the decision record. This is not the same as no decision being recorded.
        </p>
      ) : !editing && decisionQ.isError && isNotRecorded(decisionQ.error) ? (
        <p className="mt-2 text-sm text-muted-foreground" data-testid="surgery-decision-not-recorded">
          No decision record for this episode yet.
        </p>
      ) : !editing && decisionQ.data ? (
        <div className="mt-2 space-y-1 text-sm" data-testid="surgery-decision-view">
          {decisionQ.data.finalDecision ? (
            <p data-testid="surgery-decision-final">
              <span className="rounded bg-emerald-50 px-1.5 py-0.5 text-xs font-semibold uppercase text-emerald-800">
                {decisionQ.data.finalDecision}
              </span>{" "}
              <span className="text-xs text-muted-foreground">
                by {decisionQ.data.decidedBy} at {decisionQ.data.decidedAt}
              </span>
            </p>
          ) : (
            <p className="text-xs text-muted-foreground">No final decision yet — deliberation in progress.</p>
          )}
          {decisionQ.data.decisionForum === "MDT" ? (
            <p className="text-xs" data-testid="surgery-decision-forum">
              <span className="font-semibold uppercase text-muted-foreground">Forum: </span>
              Multidisciplinary team · board decision {decisionQ.data.mdtDecisionRef} in{" "}
              {decisionQ.data.mdtDecisionSource}
              {decisionQ.data.mdtDecisionVerified ? (
                <span className="ml-1 text-emerald-700" data-testid="surgery-decision-mdt-verified">
                  (confirmed present in PCT
                  {decisionQ.data.mdtDecisionVerifiedAt
                    ? ` at ${decisionQ.data.mdtDecisionVerifiedAt}`
                    : ""}
                  )
                </span>
              ) : (
                // Recorded-but-unconfirmed is not the same as "the board did not decide this",
                // and must not render as either a tick or an absence.
                <span className="ml-1 text-amber-700" data-testid="surgery-decision-mdt-unverified">
                  (recorded, not yet confirmed against PCT)
                </span>
              )}
            </p>
          ) : decisionQ.data.decisionForum ? (
            <p className="text-xs" data-testid="surgery-decision-forum">
              <span className="font-semibold uppercase text-muted-foreground">Forum: </span>
              Individual clinician
            </p>
          ) : null}
          {DECISION_TEXT_FIELDS.filter((f) => decisionQ.data?.[f.key]).map((f) => (
            <p key={String(f.key)}>
              <span className="text-xs font-semibold uppercase text-muted-foreground">{f.label}: </span>
              {String(decisionQ.data?.[f.key])}
            </p>
          ))}
        </div>
      ) : null}

      {editing && (
        <div className="mt-2">
          <FieldGrid<SurgicalDecision>
            fields={DECISION_TEXT_FIELDS}
            draft={draft}
            setDraft={setDraft}
            testidPrefix="surgery-decision-field"
          />
          <div className="mt-2">
            <label className="text-xs font-semibold uppercase text-muted-foreground">
              Final decision (records who and when, immutably paired)
            </label>
            <select
              value={finalDecision}
              onChange={(e) => setFinalDecision(e.target.value)}
              className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm md:w-64"
              data-testid="surgery-decision-final-select"
            >
              <option value="">Not decided this save</option>
              <option value="PROCEED">PROCEED</option>
              <option value="DO_NOT_PROCEED">DO_NOT_PROCEED</option>
              <option value="DEFER">DEFER</option>
            </select>
          </div>

          {/* V012 — MDT forum. Surgery REFERENCES PCT's board record; it keeps no copy. */}
          <div className="mt-2 grid grid-cols-1 gap-2 md:grid-cols-3">
            <label className="block text-xs">
              <span className="font-semibold uppercase text-muted-foreground">Decision forum</span>
              <select
                value={forum}
                onChange={(e) => setForum(e.target.value)}
                className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
                data-testid="surgery-decision-forum-select"
              >
                <option value="">Leave unchanged</option>
                <option value="INDIVIDUAL">Individual clinician</option>
                <option value="MDT">Multidisciplinary team</option>
              </select>
            </label>
            {forum === "MDT" && (
              <>
                <label className="block text-xs">
                  <span className="font-semibold uppercase text-muted-foreground">
                    PCT board decision ID
                  </span>
                  <input
                    type="text"
                    value={mdtRef}
                    onChange={(e) => setMdtRef(e.target.value)}
                    placeholder="UUID of the PCT MDT record"
                    className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
                    data-testid="surgery-decision-mdt-ref"
                  />
                </label>
                <label className="block text-xs">
                  <span className="font-semibold uppercase text-muted-foreground">Which PCT record</span>
                  <select
                    value={mdtSource}
                    onChange={(e) => setMdtSource(e.target.value)}
                    className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
                    data-testid="surgery-decision-mdt-source"
                  >
                    <option value="PCT_MDT_DECISION">pct_mdt_decisions</option>
                    <option value="PCT_MDT_CASE_ITEM">pct_mdt_sessions case item</option>
                  </select>
                </label>
              </>
            )}
          </div>
          {forum === "MDT" && (
            <p className="mt-1 text-xs text-muted-foreground">
              PCT holds two MDT records; pick the one the board decision was minuted in. Surgery
              stores only the reference.
            </p>
          )}

          <div className="mt-3 flex items-center gap-2">
            <button
              type="button"
              onClick={save}
              disabled={record.isPending || mdtIncomplete}
              className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground disabled:opacity-50"
              data-testid="surgery-decision-save"
            >
              {record.isPending ? "Saving…" : "Save decision record"}
            </button>
            <button
              type="button"
              onClick={() => setEditing(false)}
              className="rounded-md border border-gray-200 px-3 py-1.5 text-sm"
            >
              Cancel
            </button>
            {mdtIncomplete && (
              <span className="text-xs text-muted-foreground" data-testid="surgery-decision-mdt-required">
                An MDT decision needs the PCT board decision it came from.
              </span>
            )}
            {record.isError && (
              <span className="text-sm text-danger" role="alert" data-testid="surgery-decision-save-failed">
                Save failed — the decision has NOT been recorded.
              </span>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * V011 — every specialty operating on this case, not just the lead. Removal is offered only for
 * SHARED teams: the lead cannot be removed, only handed over, and the server enforces that too.
 */
function SpecialtiesPanel({ episodeId }: { episodeId: string }) {
  const specialtiesQ = useEpisodeSpecialties(episodeId);
  const add = useAddEpisodeSpecialty(episodeId);
  const transferLead = useTransferEpisodeLead(episodeId);
  const remove = useRemoveEpisodeSpecialty(episodeId);

  const [specialty, setSpecialty] = useState("");
  const [contribution, setContribution] = useState("");

  const present = new Set((specialtiesQ.data ?? []).map((s) => s.specialty));
  const selectable = SURGICAL_SPECIALTIES.filter((s) => !present.has(s));
  const failed = add.isError || transferLead.isError || remove.isError;

  const reset = () => {
    setSpecialty("");
    setContribution("");
  };

  return (
    <div className="mt-4 rounded-lg border border-gray-100 p-3" data-testid="surgery-specialties-panel">
      <h4 className="text-xs font-semibold uppercase text-muted-foreground">
        Specialties on this case (S1 · shared-specialty care)
      </h4>

      {specialtiesQ.isLoading ? (
        <p className="mt-2 text-sm text-muted-foreground">Loading specialties…</p>
      ) : specialtiesQ.isError ? (
        // Never an empty roster on failure: on a theatre surface that reads as "no second team
        // is coming", which is exactly the wrong thing to tell a room.
        <p className="mt-2 text-sm text-danger" role="alert" data-testid="surgery-specialties-unavailable">
          Could not read which teams are on this case. This is not the same as there being only
          one team.
        </p>
      ) : specialtiesQ.data && specialtiesQ.data.length === 0 ? (
        <p className="mt-2 text-sm text-muted-foreground" data-testid="surgery-specialties-empty">
          No specialty recorded on this episode.
        </p>
      ) : (
        <ul className="mt-2 space-y-1" data-testid="surgery-specialties-list">
          {specialtiesQ.data?.map((s) => (
            <li key={s.id} className="flex flex-wrap items-center gap-2 text-sm">
              <span
                className={`rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase ${
                  s.role === "LEAD" ? "bg-indigo-50 text-indigo-800" : "bg-gray-100 text-gray-600"
                }`}
              >
                {s.role}
              </span>
              <span className="font-medium">{s.specialty}</span>
              {s.contribution && <span className="text-xs text-muted-foreground">{s.contribution}</span>}
              <span className="text-xs text-muted-foreground">added by {s.addedBy}</span>
              {s.role === "SHARED" && (
                <button
                  type="button"
                  onClick={() => remove.mutate({ specialty: s.specialty })}
                  disabled={remove.isPending}
                  className="rounded-md border border-gray-200 px-2 py-0.5 text-xs disabled:opacity-50"
                  data-testid="surgery-specialty-remove"
                >
                  Remove
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      <div className="mt-3 flex flex-wrap items-end gap-2">
        <label className="block text-xs">
          <span className="font-semibold uppercase text-muted-foreground">Specialty</span>
          <select
            value={specialty}
            onChange={(e) => setSpecialty(e.target.value)}
            className="mt-0.5 block rounded-md border border-gray-200 px-2 py-1.5 text-sm"
            data-testid="surgery-specialty-select"
          >
            <option value="">Choose a specialty…</option>
            {selectable.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
        <label className="block flex-1 text-xs min-w-[180px]">
          <span className="font-semibold uppercase text-muted-foreground">Contribution</span>
          <input
            type="text"
            value={contribution}
            onChange={(e) => setContribution(e.target.value)}
            placeholder="e.g. vascular control of the iliac vessels"
            className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
            data-testid="surgery-specialty-contribution"
          />
        </label>
        <button
          type="button"
          disabled={!specialty || add.isPending}
          onClick={() =>
            add.mutate(
              { specialty, role: "SHARED", contribution: contribution.trim() || null },
              { onSuccess: reset },
            )
          }
          className="rounded-md border border-gray-200 px-3 py-1.5 text-sm disabled:opacity-50"
          data-testid="surgery-specialty-add"
        >
          {add.isPending ? "Adding…" : "Add joining team"}
        </button>
        <button
          type="button"
          disabled={!specialty || transferLead.isPending}
          onClick={() =>
            transferLead.mutate(
              { specialty, contribution: contribution.trim() || null },
              { onSuccess: reset },
            )
          }
          className="rounded-md border border-gray-200 px-3 py-1.5 text-sm disabled:opacity-50"
          data-testid="surgery-specialty-lead"
        >
          {transferLead.isPending ? "Handing over…" : "Hand the lead over"}
        </button>
      </div>
      {failed && (
        <p className="mt-2 text-sm text-danger" role="alert" data-testid="surgery-specialty-write-failed">
          That change was NOT saved — the specialties on this case are unchanged.
        </p>
      )}
    </div>
  );
}

/**
 * V010 — reopening for a return to theatre. Deliberately separate from the transition control:
 * the reason is mandatory, and the server records who reopened the episode and when. The
 * predecessor field is for the other shape (a reoperation given its own episode) and stays
 * optional.
 */
function ReopenPanel({ episode }: { episode: SurgicalEpisode }) {
  const reopen = useReopenSurgicalEpisode(episode.id);
  const [reason, setReason] = useState("");
  const [predecessor, setPredecessor] = useState("");

  if (!REOPENABLE_FROM.includes(episode.status)) return null;

  return (
    <div className="mt-4 rounded-lg border border-rose-100 p-3" data-testid="surgery-reopen-panel">
      <h4 className="text-xs font-semibold uppercase text-muted-foreground">
        Return to theatre — reopen this episode
      </h4>
      <p className="mt-1 text-xs text-muted-foreground">
        Reopening records a reason against your name. A plain status change to REOPENED is
        refused, so this is the only way a reoperation enters the record.
      </p>
      <div className="mt-2 grid grid-cols-1 gap-2 md:grid-cols-2">
        <label className="block text-xs">
          <span className="font-semibold uppercase text-muted-foreground">Reason (required)</span>
          <input
            type="text"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="e.g. post-operative haemorrhage requiring re-exploration"
            className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
            data-testid="surgery-reopen-reason"
          />
        </label>
        <label className="block text-xs">
          <span className="font-semibold uppercase text-muted-foreground">
            Predecessor episode (optional)
          </span>
          <input
            type="text"
            value={predecessor}
            onChange={(e) => setPredecessor(e.target.value)}
            placeholder="Episode this reoperation follows"
            className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
            data-testid="surgery-reopen-predecessor"
          />
        </label>
      </div>
      <div className="mt-3 flex items-center gap-2">
        <button
          type="button"
          disabled={!reason.trim() || reopen.isPending}
          onClick={() =>
            reopen.mutate(
              { reason: reason.trim(), reoperationOfEpisodeId: predecessor.trim() || null },
              {
                onSuccess: () => {
                  setReason("");
                  setPredecessor("");
                },
              },
            )
          }
          className="rounded-md border border-rose-200 px-3 py-1.5 text-sm text-rose-800 disabled:opacity-50"
          data-testid="surgery-reopen-submit"
        >
          {reopen.isPending ? "Reopening…" : "Reopen episode"}
        </button>
        {reopen.isError && (
          <span className="text-sm text-danger" role="alert" data-testid="surgery-reopen-failed">
            Reopen failed — the episode is unchanged and no return to theatre has been recorded.
          </span>
        )}
      </div>
    </div>
  );
}

function OpenEpisodeForm({ subjectCpid, onDone }: { subjectCpid: string; onDone: () => void }) {
  const open = useOpenSurgicalEpisode();
  const [form, setForm] = useState({
    journeyId: "",
    encounterId: "",
    operativeIndication: "",
    nonOperativeOptionsConsidered: "",
    conditionDisplay: "",
    diagnosticCertainty: "",
    evidence: "",
    specialty: "",
  });

  const submit = () => {
    open.mutate(
      {
        subjectCpid,
        journeyId: form.journeyId || null,
        encounterId: form.encounterId || null,
        operativeIndication: form.operativeIndication,
        nonOperativeOptionsConsidered: form.nonOperativeOptionsConsidered || null,
        conditionDisplay: form.conditionDisplay || null,
        diagnosticCertainty: form.diagnosticCertainty || null,
        evidence: form.evidence || null,
        specialty: form.specialty || null,
      },
      { onSuccess: onDone },
    );
  };

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [k]: e.target.value });

  return (
    <div className="mt-3 rounded-lg border border-gray-100 p-3" data-testid="surgery-open-episode-form">
      <h4 className="text-xs font-semibold uppercase text-muted-foreground">Open surgical episode</h4>
      <div className="mt-2 grid grid-cols-1 gap-2 md:grid-cols-2">
        <label className="block text-xs">
          <span className="font-semibold uppercase text-muted-foreground">
            Operative indication (required)
          </span>
          <input
            type="text"
            value={form.operativeIndication}
            onChange={set("operativeIndication")}
            className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
            data-testid="surgery-open-indication"
          />
        </label>
        <label className="block text-xs">
          <span className="font-semibold uppercase text-muted-foreground">Non-operative options considered</span>
          <input
            type="text"
            value={form.nonOperativeOptionsConsidered}
            onChange={set("nonOperativeOptionsConsidered")}
            className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
          />
        </label>
        {/* CC-5: the episode must carry a resolvable PCT anchor — journey or encounter. */}
        <label className="block text-xs">
          <span className="font-semibold uppercase text-muted-foreground">Journey ID (PCT anchor)</span>
          <input
            type="text"
            value={form.journeyId}
            onChange={set("journeyId")}
            className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
            data-testid="surgery-open-journey"
          />
        </label>
        <label className="block text-xs">
          <span className="font-semibold uppercase text-muted-foreground">Encounter ID</span>
          <input
            type="text"
            value={form.encounterId}
            onChange={set("encounterId")}
            className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
          />
        </label>
        <label className="block text-xs">
          <span className="font-semibold uppercase text-muted-foreground">Condition (display)</span>
          <input
            type="text"
            value={form.conditionDisplay}
            onChange={set("conditionDisplay")}
            className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
          />
        </label>
        <label className="block text-xs">
          <span className="font-semibold uppercase text-muted-foreground">Diagnostic certainty</span>
          <input
            type="text"
            value={form.diagnosticCertainty}
            onChange={set("diagnosticCertainty")}
            placeholder="e.g. CONFIRMED / SUSPECTED"
            className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
          />
        </label>
        <label className="block text-xs">
          <span className="font-semibold uppercase text-muted-foreground">Evidence</span>
          <input
            type="text"
            value={form.evidence}
            onChange={set("evidence")}
            className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
          />
        </label>
        <label className="block text-xs">
          <span className="font-semibold uppercase text-muted-foreground">Specialty</span>
          <input
            type="text"
            value={form.specialty}
            onChange={set("specialty")}
            placeholder="e.g. GENERAL_SURGERY"
            className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
          />
        </label>
      </div>
      <div className="mt-3 flex items-center gap-2">
        <button
          type="button"
          onClick={submit}
          disabled={open.isPending || !form.operativeIndication.trim()}
          className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground disabled:opacity-50"
          data-testid="surgery-open-submit"
        >
          {open.isPending ? "Opening…" : "Open episode"}
        </button>
        {open.isError && (
          <span className="text-sm text-danger" role="alert" data-testid="surgery-open-failed">
            Could not open the episode — nothing has been recorded.
          </span>
        )}
      </div>
    </div>
  );
}

export default function SurgicalEpisodesPage() {
  const [cpidInput, setCpidInput] = useState("");
  const [subjectCpid, setSubjectCpid] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [showOpenForm, setShowOpenForm] = useState(false);
  const [nextStatus, setNextStatus] = useState<string>("");

  const episodesQ = useSurgicalEpisodes(subjectCpid);
  const transition = useTransitionSurgicalEpisode(selectedId);

  const selected = episodesQ.data?.find((e) => e.id === selectedId) ?? null;

  return (
    <AppLayout>
      <PageShell
        title="Surgical Episodes"
        subtitle="Surgical episode spine, general surgical assessment and decision-making record"
      >
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <div className="relative flex-1 min-w-[240px]">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              type="text"
              value={cpidInput}
              onChange={(e) => setCpidInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && cpidInput.trim()) {
                  setSubjectCpid(cpidInput.trim());
                  setSelectedId(null);
                }
              }}
              placeholder="Patient CPID…"
              className="w-full rounded-md border border-gray-200 py-2 pl-9 pr-3 text-sm"
              data-testid="surgery-cpid-input"
            />
          </div>
          <button
            type="button"
            onClick={() => {
              if (cpidInput.trim()) {
                setSubjectCpid(cpidInput.trim());
                setSelectedId(null);
              }
            }}
            className="rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground"
            data-testid="surgery-load-episodes"
          >
            Load episodes
          </button>
          {subjectCpid && (
            <button
              type="button"
              onClick={() => setShowOpenForm((v) => !v)}
              className="rounded-md border border-gray-200 px-3 py-2 text-sm"
              data-testid="surgery-toggle-open-form"
            >
              {showOpenForm ? "Hide open-episode form" : "Open new episode"}
            </button>
          )}
        </div>

        {subjectCpid && showOpenForm && (
          <OpenEpisodeForm subjectCpid={subjectCpid} onDone={() => setShowOpenForm(false)} />
        )}

        <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-2">
          {/* ── Episode list ─────────────────────────────────────────── */}
          <div className="rounded-lg border border-gray-100">
            {!subjectCpid ? (
              <p className="p-8 text-center text-sm text-muted-foreground">
                Enter a patient CPID to load their surgical episodes.
              </p>
            ) : episodesQ.isLoading ? (
              <div className="flex items-center justify-center gap-2 p-12 text-sm text-muted-foreground">
                <Loader2 className="h-5 w-5 animate-spin" /> Loading episodes…
              </div>
            ) : episodesQ.isError ? (
              <div
                className="flex flex-col items-center gap-2 p-8 text-center text-sm text-danger"
                data-testid="surgery-episodes-unavailable"
                role="alert"
              >
                <AlertTriangle className="h-6 w-6" />
                Could not read this patient&apos;s surgical record. This is not the same as the
                patient having no surgical episodes — try again, or report if it persists.
              </div>
            ) : episodesQ.data && episodesQ.data.length === 0 ? (
              <p className="p-8 text-center text-sm text-muted-foreground" data-testid="surgery-episodes-empty">
                No surgical episodes for this patient.
              </p>
            ) : (
              <ul className="divide-y divide-gray-100" data-testid="surgery-episode-list">
                {episodesQ.data?.map((ep) => (
                  <li key={ep.id}>
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedId(ep.id);
                        setNextStatus("");
                      }}
                      className={`w-full px-4 py-3 text-left text-sm hover:bg-gray-50 ${
                        selectedId === ep.id ? "bg-gray-50" : ""
                      }`}
                      data-testid="surgery-episode-item"
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-medium">{ep.operativeIndication ?? "(no indication recorded)"}</span>
                        <span
                          className={`rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase ${
                            STATUS_STYLE[ep.status] ?? "bg-gray-100 text-gray-600"
                          }`}
                        >
                          {ep.status}
                        </span>
                      </div>
                      <div className="text-xs text-muted-foreground">
                        {ep.specialty ?? "unspecified specialty"}
                        {ep.pctContributed ? " · contributed to PCT problem list" : ""}
                        {ep.procedureEpisodeRef ? " · procedure episode linked" : ""}
                      </div>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* ── Episode detail: transition + S2 + S3 ──────────────────── */}
          <div className="rounded-lg border border-gray-100 p-4">
            {!selected ? (
              <p className="p-8 text-center text-sm text-muted-foreground">
                Select an episode to work its assessment and decision record.
              </p>
            ) : (
              <div data-testid="surgery-episode-detail">
                <h3 className="text-base font-semibold">{selected.operativeIndication}</h3>
                <p className="text-xs text-muted-foreground">
                  Episode {selected.id} · {selected.status}
                  {selected.journeyId ? ` · journey ${selected.journeyId}` : ""}
                  {selected.pctProblemRef ? ` · PCT problem ${selected.pctProblemRef}` : ""}
                </p>
                {selected.nonOperativeOptionsConsidered && (
                  <p className="mt-1 text-sm">
                    <span className="text-xs font-semibold uppercase text-muted-foreground">
                      Non-operative options considered:{" "}
                    </span>
                    {selected.nonOperativeOptionsConsidered}
                  </p>
                )}
                {selected.reopenedAt && (
                  <p className="mt-1 text-sm text-rose-800" data-testid="surgery-episode-reopen-trail">
                    <span className="text-xs font-semibold uppercase">Returned to theatre: </span>
                    {selected.reopenReason} — reopened by {selected.reopenedBy} at{" "}
                    {selected.reopenedAt}
                  </p>
                )}
                {selected.reoperationOfEpisodeId && (
                  <p className="text-xs text-muted-foreground" data-testid="surgery-episode-predecessor">
                    Reoperation following episode {selected.reoperationOfEpisodeId}
                  </p>
                )}

                <div className="mt-3 flex items-center gap-2">
                  <select
                    value={nextStatus}
                    onChange={(e) => setNextStatus(e.target.value)}
                    className="rounded-md border border-gray-200 px-2 py-1.5 text-sm"
                    data-testid="surgery-transition-select"
                  >
                    <option value="">Transition to…</option>
                    {STATUS_OPTIONS.filter((s) => s !== selected.status).map((s) => (
                      <option key={s} value={s}>
                        {s}
                      </option>
                    ))}
                  </select>
                  <button
                    type="button"
                    disabled={!nextStatus || transition.isPending}
                    onClick={() =>
                      transition.mutate(
                        { status: nextStatus as SurgicalEpisodeStatus },
                        { onSuccess: () => setNextStatus("") },
                      )
                    }
                    className="rounded-md border border-gray-200 px-3 py-1.5 text-sm disabled:opacity-50"
                    data-testid="surgery-transition-apply"
                  >
                    {transition.isPending ? "Applying…" : "Apply"}
                  </button>
                  {transition.isError && (
                    <span className="text-sm text-danger" role="alert" data-testid="surgery-transition-failed">
                      Transition failed — the episode status is unchanged.
                    </span>
                  )}
                </div>

                <SpecialtiesPanel episodeId={selected.id} />
                <ReopenPanel episode={selected} />
                <AssessmentPanel episodeId={selected.id} />
                <DecisionPanel episodeId={selected.id} />
                <PrehabPanel episodeId={selected.id} />
                <ComplicationPathwayPanel episodeId={selected.id} />
                <LongitudinalObjectsPanel episodeId={selected.id} />
                <FollowupPanel episodeId={selected.id} />
                <WaitlistRevalidationPanel episodeId={selected.id} />
              </div>
            )}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
