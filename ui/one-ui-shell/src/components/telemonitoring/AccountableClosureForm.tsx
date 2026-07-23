"use client";

/**
 * OF-B26/OF-B28 — the §14.6 accountable-closure form.
 *
 * Closure invariant: an episode closes ONLY with (a) reviewer identity,
 * (b) clinical assessment, (c) action taken, (d) outcome classification.
 * The form REFUSES submit until all four are present — and the upstream
 * enforces the same law, so a bypassed client still cannot close blind.
 *
 * Emergency-severity episodes additionally surface the assumption-of-care
 * recorder (§14.7 rungs 14–15): an unresolved handover is a named failure
 * mode and must never be closed silently.
 */

import { useState } from "react";
import type { AlertEpisodeView } from "@/lib/telemonitoring/types";

export interface AccountableClosureFormProps {
  episode: AlertEpisodeView;
  busy?: boolean;
  errorMessage?: string | null;
  onResolve: (closure: { resolvedBy: string; assessment: string; action: string; outcome: string }) => void;
  onRecordAssumptionOfCare?: (note: string) => void;
  assumptionBusy?: boolean;
}

const OUTCOME_OPTIONS = [
  "RESOLVED_NO_HARM",
  "RESOLVED_AFTER_INTERVENTION",
  "ESCALATED_TO_CARE",
  "DEVICE_FAULT_CONFIRMED",
  "FALSE_ALARM_THRESHOLD_REVIEWED",
];

export function AccountableClosureForm({
  episode,
  busy = false,
  errorMessage,
  onResolve,
  onRecordAssumptionOfCare,
  assumptionBusy = false,
}: AccountableClosureFormProps) {
  const [resolvedBy, setResolvedBy] = useState("");
  const [assessment, setAssessment] = useState("");
  const [action, setAction] = useState("");
  const [outcome, setOutcome] = useState("");
  const [assumptionNote, setAssumptionNote] = useState("");

  const complete =
    resolvedBy.trim().length > 0 &&
    assessment.trim().length > 0 &&
    action.trim().length > 0 &&
    outcome.trim().length > 0;

  const isEmergency = episode.severity === "EMERGENCY";
  const assumptionRecorded = !!episode.assumptionOfCareBy;

  return (
    <form
      data-testid="accountable-closure-form"
      className="space-y-3"
      onSubmit={(e) => {
        e.preventDefault();
        if (!complete || busy) return;
        onResolve({
          resolvedBy: resolvedBy.trim(),
          assessment: assessment.trim(),
          action: action.trim(),
          outcome: outcome.trim(),
        });
      }}
    >
      <p className="text-xs text-slate-500">
        Accountable closure: reviewer, assessment, action and outcome are all required — an
        episode never auto-vanishes.
      </p>

      {isEmergency ? (
        <div className="rounded-lg border border-rose-200 bg-rose-50 p-3">
          <label htmlFor="tm-assumption-note" className="mb-1 block text-xs font-semibold text-rose-900">
            Assumption of care (emergency handover)
          </label>
          {assumptionRecorded ? (
            <p className="text-xs text-rose-800">
              Recorded by {episode.assumptionOfCareBy}
              {episode.assumptionOfCareNote ? ` — ${episode.assumptionOfCareNote}` : ""}.
            </p>
          ) : (
            <>
              <textarea
                id="tm-assumption-note"
                value={assumptionNote}
                onChange={(e) => setAssumptionNote(e.target.value)}
                rows={2}
                className="w-full rounded-md border border-rose-200 p-2 text-sm"
                placeholder="Who assumed physical care of the patient, where, and when."
              />
              <button
                type="button"
                disabled={assumptionNote.trim().length === 0 || assumptionBusy || !onRecordAssumptionOfCare}
                onClick={() => onRecordAssumptionOfCare?.(assumptionNote.trim())}
                className="mt-2 rounded-md bg-rose-600 px-3 py-1.5 text-xs font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
              >
                {assumptionBusy ? "Recording…" : "Record assumption of care"}
              </button>
              <p className="mt-1 text-[11px] text-rose-700">
                An emergency episode without a recorded handover stays on the unresolved-handover
                board — record who assumed care before closing.
              </p>
            </>
          )}
        </div>
      ) : null}

      <div>
        <label htmlFor="tm-resolved-by" className="mb-1 block text-xs font-medium text-slate-600">
          Reviewer (name and role)
        </label>
        <input
          id="tm-resolved-by"
          value={resolvedBy}
          onChange={(e) => setResolvedBy(e.target.value)}
          className="w-full rounded-md border border-slate-300 p-2 text-sm"
          placeholder="e.g. S. Ncube, monitoring nurse"
        />
      </div>

      <div>
        <label htmlFor="tm-assessment" className="mb-1 block text-xs font-medium text-slate-600">
          Clinical assessment of the contributing readings
        </label>
        <textarea
          id="tm-assessment"
          value={assessment}
          onChange={(e) => setAssessment(e.target.value)}
          rows={2}
          className="w-full rounded-md border border-slate-300 p-2 text-sm"
        />
      </div>

      <div>
        <label htmlFor="tm-action" className="mb-1 block text-xs font-medium text-slate-600">
          Action taken (or explicit, reasoned no-action-required)
        </label>
        <textarea
          id="tm-action"
          value={action}
          onChange={(e) => setAction(e.target.value)}
          rows={2}
          className="w-full rounded-md border border-slate-300 p-2 text-sm"
        />
      </div>

      <div>
        <label htmlFor="tm-outcome" className="mb-1 block text-xs font-medium text-slate-600">
          Outcome classification
        </label>
        <select
          id="tm-outcome"
          value={outcome}
          onChange={(e) => setOutcome(e.target.value)}
          className="w-full rounded-md border border-slate-300 p-2 text-sm"
        >
          <option value="">Select an outcome…</option>
          {OUTCOME_OPTIONS.map((o) => (
            <option key={o} value={o}>
              {o.replaceAll("_", " ").toLowerCase()}
            </option>
          ))}
        </select>
      </div>

      {errorMessage ? (
        <p className="rounded-md bg-rose-50 p-2 text-xs text-rose-700">{errorMessage}</p>
      ) : null}

      <button
        type="submit"
        disabled={!complete || busy}
        className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
      >
        {busy ? "Closing…" : "Close episode with accountable record"}
      </button>
      {!complete ? (
        <p className="text-[11px] text-slate-500">
          All four closure fields are required before this episode can be closed.
        </p>
      ) : null}
    </form>
  );
}
