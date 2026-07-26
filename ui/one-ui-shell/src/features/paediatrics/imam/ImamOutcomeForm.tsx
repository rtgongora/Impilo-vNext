"use client";

import React from "react";
import type { ImamAssessment, ImamEpisode } from "@/hooks/queries/useImam";
import { useCloseImamEpisode } from "@/hooks/queries/useImam";
import { OUTCOME_OPTIONS, overrideReasonPrompt, requiresOverrideReason } from "./imam-presentation";

/**
 * Closing the episode.
 *
 * The rule the form exists to make visible: a cure is never certified on criteria that were not
 * met, or that could not be checked. The clinician can still record it — there are good reasons
 * to discharge a child who has not formally recovered, and blocking that would be blocking care —
 * but the reason is required and is kept on the child's record. Without it a programme's cure
 * rate silently absorbs every discharge nobody had to justify.
 *
 * The server enforces the same rule; this only ensures the clinician meets it in the form rather
 * than through a rejected submission whose reason they have to guess.
 */
export interface ImamOutcomeFormProps {
  episode: ImamEpisode;
  assessment: ImamAssessment | null;
  onClosed?: () => void;
}

export function ImamOutcomeForm({ episode, assessment, onClosed }: ImamOutcomeFormProps) {
  const close = useCloseImamEpisode();
  const [outcome, setOutcome] = React.useState("");
  const [reason, setReason] = React.useState("");
  const [note, setNote] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);

  const needsReason = requiresOverrideReason(outcome, assessment);
  const selected = OUTCOME_OPTIONS.find((option) => option.value === outcome);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!outcome) {
      setError("Choose the outcome this child actually had.");
      return;
    }
    if (needsReason && !reason.trim()) {
      setError(overrideReasonPrompt(assessment));
      return;
    }
    try {
      await close.mutateAsync({
        episodeId: episode.id,
        patientId: episode.patientId,
        outcome,
        outcomeAt: new Date().toISOString(),
        outcomeNote: note.trim() || null,
        dischargeOverrideReason: needsReason ? reason.trim() : null,
      });
      onClosed?.();
    } catch (caught) {
      const body = caught as { message?: string };
      setError(body?.message ?? "The episode could not be closed. Nothing has been changed.");
    }
  };

  return (
    <form
      onSubmit={submit}
      className="rounded-lg border border-border bg-background p-4"
      data-testid="imam-outcome-form"
    >
      <h3 className="text-sm font-semibold text-foreground">Close this episode</h3>
      <p className="mt-0.5 text-xs text-muted-foreground">
        Transferred out and medical transfer are not outcomes of care — the episode continues elsewhere
        — and are kept separate from cured so the programme&apos;s recovery rate stays true.
      </p>

      <label className="mt-3 block text-xs font-medium text-muted-foreground">
        Outcome
        <select
          value={outcome}
          onChange={(event) => setOutcome(event.target.value)}
          data-testid="imam-outcome-select"
          className="mt-1 block min-h-[44px] w-full rounded-md border border-border bg-background px-3 text-sm text-foreground"
        >
          <option value="">Choose an outcome…</option>
          {OUTCOME_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </label>
      {selected && <p className="mt-1 text-xs text-muted-foreground">{selected.hint}</p>}

      {needsReason && (
        <div
          className="mt-3 rounded-md border border-amber-500 bg-amber-50 p-3"
          data-testid="imam-override-required"
        >
          <p className="text-xs font-semibold text-amber-900">A reason is required</p>
          <p className="mt-1 text-xs text-amber-900">{overrideReasonPrompt(assessment)}</p>
          <label className="mt-2 block text-xs font-medium text-amber-900">
            Why is this child being discharged as cured?
            <textarea
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              rows={2}
              data-testid="imam-override-reason"
              className="mt-1 block w-full rounded-md border border-amber-500 bg-background px-3 py-2 text-sm text-foreground"
            />
          </label>
        </div>
      )}

      <label className="mt-3 block text-xs font-medium text-muted-foreground">
        Note
        <textarea
          value={note}
          onChange={(event) => setNote(event.target.value)}
          rows={2}
          data-testid="imam-outcome-note"
          className="mt-1 block w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
        />
      </label>

      {error && (
        <p className="mt-3 rounded-md border border-red-500 bg-red-50 p-2 text-xs text-red-900" role="alert">
          {error}
        </p>
      )}

      <button
        type="submit"
        disabled={close.isPending}
        data-testid="imam-outcome-submit"
        className="mt-3 min-h-[44px] rounded-md border border-border px-4 text-sm font-medium text-foreground disabled:opacity-60"
      >
        {close.isPending ? "Closing…" : "Close episode"}
      </button>
    </form>
  );
}
