"use client";

/**
 * Assessment and the risk formulation that concludes it.
 *
 * The two are separate records on purpose: risk is keyed on an assessment, not on the referral, so
 * a formulation always says which assessment reached it. A later assessment reaches its own, and
 * the earlier one stays on the record rather than being overwritten — which is what makes it
 * possible to see afterwards that the risk was judged low that morning.
 */

import { useState } from "react";

import {
  ASSESSMENT_TYPES,
  CAPACITY_OUTCOMES,
  RISK_LEVELS,
  useMentalHealthAssessments,
  useMentalHealthRiskFormulations,
  useRecordMentalHealthAssessment,
  useRecordMentalHealthRiskFormulation,
} from "@/hooks/queries/useMentalHealth";
import { bffErrorMessage } from "@/lib/bff-errors";

import { PanelShell } from "./PanelShell";

function when(value: unknown): string {
  if (!value) return "—";
  const parsed = new Date(String(value));
  return Number.isNaN(parsed.getTime()) ? String(value) : parsed.toLocaleString();
}

export function AssessmentPanel({ referralId, actor }: { referralId: string; actor: string }) {
  const assessments = useMentalHealthAssessments(referralId);
  const record = useRecordMentalHealthAssessment(referralId);

  const [assessmentType, setAssessmentType] = useState<string>("INITIAL");
  const [mentalStateExam, setMentalStateExam] = useState("");
  const [capacityAssessed, setCapacityAssessed] = useState(false);
  const [capacityOutcome, setCapacityOutcome] = useState<string>("HAS_CAPACITY");
  const [notes, setNotes] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const rows = assessments.data ?? [];
  const latestId = rows.length ? String(rows[rows.length - 1].id ?? "") : "";

  return (
    <div className="space-y-4">
      <PanelShell
        title="Assessment"
        subject="this patient's mental-health assessments"
        description="mh_assessment — mental state examination and, where it was carried out, the capacity decision"
        isLoading={assessments.isLoading}
        isError={assessments.isError}
        error={assessments.error}
        isEmpty={rows.length === 0}
        emptyMessage="No assessment has been recorded against this referral."
        data-testid="mh-assessments"
        actions={
          <form
            className="space-y-3"
            data-testid="mh-assessment-form"
            onSubmit={async (event) => {
              event.preventDefault();
              setFormError(null);
              try {
                await record.mutateAsync({
                  assessmentType,
                  mentalStateExam: mentalStateExam.trim() || undefined,
                  capacityAssessed,
                  // The service refuses an outcome with no assessment behind it
                  // (chk_mha_capacity_consistency), so it is sent only when one was carried out.
                  capacityOutcome: capacityAssessed ? capacityOutcome : undefined,
                  assessedBy: actor || undefined,
                  notes: notes.trim() || undefined,
                });
                setMentalStateExam("");
                setNotes("");
              } catch (error) {
                setFormError(bffErrorMessage(error, "this assessment"));
              }
            }}
          >
            <div className="flex flex-wrap gap-3">
              <label className="text-sm">
                <span className="block font-medium text-foreground">Type</span>
                <select
                  className="mt-1 rounded border border-border bg-background px-2 py-1.5 text-sm"
                  value={assessmentType}
                  onChange={(event) => setAssessmentType(event.target.value)}
                  data-testid="mh-assessment-type"
                >
                  {ASSESSMENT_TYPES.map((value) => (
                    <option key={value} value={value}>
                      {value.toLowerCase()}
                    </option>
                  ))}
                </select>
              </label>

              <label className="flex items-center gap-2 self-end text-sm">
                <input
                  type="checkbox"
                  checked={capacityAssessed}
                  onChange={(event) => setCapacityAssessed(event.target.checked)}
                  data-testid="mh-capacity-assessed"
                />
                Capacity was assessed
              </label>

              {capacityAssessed ? (
                <label className="text-sm">
                  <span className="block font-medium text-foreground">Capacity outcome</span>
                  <select
                    className="mt-1 rounded border border-border bg-background px-2 py-1.5 text-sm"
                    value={capacityOutcome}
                    onChange={(event) => setCapacityOutcome(event.target.value)}
                    data-testid="mh-capacity-outcome"
                  >
                    {CAPACITY_OUTCOMES.map((value) => (
                      <option key={value} value={value}>
                        {value.replaceAll("_", " ").toLowerCase()}
                      </option>
                    ))}
                  </select>
                </label>
              ) : null}
            </div>

            <label className="block text-sm">
              <span className="font-medium text-foreground">Mental state examination</span>
              <textarea
                className="mt-1 block w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
                rows={3}
                value={mentalStateExam}
                onChange={(event) => setMentalStateExam(event.target.value)}
                data-testid="mh-mental-state-exam"
              />
            </label>

            <label className="block text-sm">
              <span className="font-medium text-foreground">Notes</span>
              <textarea
                className="mt-1 block w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
                rows={2}
                value={notes}
                onChange={(event) => setNotes(event.target.value)}
              />
            </label>

            {formError ? (
              <p className="text-sm text-danger" data-testid="mh-assessment-error">
                {formError}
              </p>
            ) : null}

            <button
              type="submit"
              className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
              disabled={record.isPending}
              data-testid="mh-assessment-submit"
            >
              {record.isPending ? "Recording…" : "Record assessment"}
            </button>
          </form>
        }
      >
        <ul className="space-y-2">
          {rows.map((row, index) => (
            <li
              key={String(row.id ?? index)}
              className="rounded border border-border p-3 text-sm"
              data-testid="mh-assessment-row"
            >
              <p className="font-medium text-foreground">
                {String(row.assessment_type ?? "Assessment").toLowerCase()} · {when(row.assessed_at)}
              </p>
              {row.mental_state_exam ? (
                <p className="mt-1 text-muted-foreground">{String(row.mental_state_exam)}</p>
              ) : null}
              <p className="mt-1 text-xs text-muted-foreground">
                {row.capacity_assessed
                  ? `Capacity: ${String(row.capacity_outcome ?? "recorded").replaceAll("_", " ").toLowerCase()}`
                  : "Capacity was not assessed at this contact."}
              </p>
              <p className="mt-0.5 text-xs text-muted-foreground">By {String(row.assessed_by ?? "—")}</p>
            </li>
          ))}
        </ul>
      </PanelShell>

      {latestId ? <RiskFormulationPanel assessmentId={latestId} actor={actor} /> : null}
    </div>
  );
}

function RiskFormulationPanel({ assessmentId, actor }: { assessmentId: string; actor: string }) {
  const formulations = useMentalHealthRiskFormulations(assessmentId);
  const record = useRecordMentalHealthRiskFormulation(assessmentId);

  const [riskToSelf, setRiskToSelf] = useState<string>("LOW");
  const [riskToOthers, setRiskToOthers] = useState<string>("LOW");
  const [riskSummary, setRiskSummary] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const rows = formulations.data ?? [];

  return (
    <PanelShell
      title="Risk formulation"
      subject="the risk formulation for the latest assessment"
      description="mh_risk_formulation — the conclusion of the most recent assessment, not a standing property of the patient"
      isLoading={formulations.isLoading}
      isError={formulations.isError}
      error={formulations.error}
      isEmpty={rows.length === 0}
      emptyMessage="The latest assessment has no risk formulation recorded against it."
      data-testid="mh-risk-formulations"
      actions={
        <form
          className="space-y-3"
          data-testid="mh-risk-form"
          onSubmit={async (event) => {
            event.preventDefault();
            setFormError(null);
            try {
              await record.mutateAsync({
                riskToSelf,
                riskToOthers,
                riskSummary: riskSummary.trim(),
                formulatedBy: actor || undefined,
              });
              setRiskSummary("");
            } catch (error) {
              setFormError(bffErrorMessage(error, "this risk formulation"));
            }
          }}
        >
          <div className="flex flex-wrap gap-3">
            <label className="text-sm">
              <span className="block font-medium text-foreground">Risk to self</span>
              <select
                className="mt-1 rounded border border-border bg-background px-2 py-1.5 text-sm"
                value={riskToSelf}
                onChange={(event) => setRiskToSelf(event.target.value)}
                data-testid="mh-risk-to-self"
              >
                {RISK_LEVELS.map((value) => (
                  <option key={value} value={value}>
                    {value.toLowerCase()}
                  </option>
                ))}
              </select>
            </label>
            <label className="text-sm">
              <span className="block font-medium text-foreground">Risk to others</span>
              <select
                className="mt-1 rounded border border-border bg-background px-2 py-1.5 text-sm"
                value={riskToOthers}
                onChange={(event) => setRiskToOthers(event.target.value)}
                data-testid="mh-risk-to-others"
              >
                {RISK_LEVELS.map((value) => (
                  <option key={value} value={value}>
                    {value.toLowerCase()}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <label className="block text-sm">
            <span className="font-medium text-foreground">Summary</span>
            <textarea
              className="mt-1 block w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
              rows={2}
              value={riskSummary}
              onChange={(event) => setRiskSummary(event.target.value)}
              placeholder="What the levels above are based on"
              data-testid="mh-risk-summary"
            />
          </label>

          {formError ? (
            <p className="text-sm text-danger" data-testid="mh-risk-error">
              {formError}
            </p>
          ) : null}

          <button
            type="submit"
            className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
            disabled={record.isPending || !riskSummary.trim()}
            data-testid="mh-risk-submit"
          >
            {record.isPending ? "Recording…" : "Record risk formulation"}
          </button>
        </form>
      }
    >
      <ul className="space-y-2">
        {rows.map((row, index) => (
          <li
            key={String(row.id ?? index)}
            className="rounded border border-border p-3 text-sm"
            data-testid="mh-risk-row"
          >
            <p className="font-medium text-foreground">
              To self: {String(row.risk_to_self ?? "—").toLowerCase()} · to others:{" "}
              {String(row.risk_to_others ?? "—").toLowerCase()}
            </p>
            <p className="mt-1 text-muted-foreground">{String(row.risk_summary ?? "")}</p>
            <p className="mt-0.5 text-xs text-muted-foreground">
              {String(row.formulated_by ?? "—")} · {when(row.formulated_at)}
            </p>
          </li>
        ))}
      </ul>
    </PanelShell>
  );
}
