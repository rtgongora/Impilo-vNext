"use client";

/**
 * Restraint events and their review.
 *
 * Recording restraint states what was tried before it — the service requires
 * `deEscalationAttempted` and will not accept the event without it. That requirement is the point:
 * an episode of restraint with no account of what preceded it cannot be reviewed afterwards, and
 * review is the only thing that makes the practice accountable.
 *
 * Ending and reviewing are separate acts. Ending says the restraint stopped; reviewing says
 * somebody afterwards judged whether it should have happened. An event can sit ended and unreviewed
 * for days, which is exactly what the facility-wide backlog exists to show.
 */

import { useState } from "react";

import {
  RESTRAINT_TYPES,
  useMentalHealthRestraintActions,
  useMentalHealthRestraintEvents,
} from "@/hooks/queries/useMentalHealth";
import { bffErrorMessage } from "@/lib/bff-errors";

import { PanelShell } from "./PanelShell";

function when(value: unknown): string {
  if (!value) return "—";
  const parsed = new Date(String(value));
  return Number.isNaN(parsed.getTime()) ? String(value) : parsed.toLocaleString();
}

export function RestraintPanel({ referralId, actor }: { referralId: string; actor: string }) {
  const events = useMentalHealthRestraintEvents(referralId);
  const actions = useMentalHealthRestraintActions(referralId);

  const [restraintType, setRestraintType] = useState<string>("PHYSICAL");
  const [reason, setReason] = useState("");
  const [deEscalation, setDeEscalation] = useState("");
  const [reviewOutcome, setReviewOutcome] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const rows = events.data ?? [];

  return (
    <PanelShell
      title="Restraint"
      subject="this patient's restraint events"
      description="mh_restraint_event — recording requires what de-escalation was attempted first; ending and reviewing are separate acts"
      isLoading={events.isLoading}
      isError={events.isError}
      error={events.error}
      isEmpty={rows.length === 0}
      emptyMessage="No restraint has been recorded for this patient."
      data-testid="mh-restraint"
      actions={
        <form
          className="space-y-3"
          data-testid="mh-restraint-form"
          onSubmit={async (event) => {
            event.preventDefault();
            setFormError(null);
            try {
              await actions.record.mutateAsync({
                restraintType,
                reason: reason.trim(),
                deEscalationAttempted: deEscalation.trim(),
                initiatedBy: actor || undefined,
              });
              setReason("");
              setDeEscalation("");
            } catch (error) {
              setFormError(bffErrorMessage(error, "this restraint event"));
            }
          }}
        >
          <label className="block text-sm">
            <span className="font-medium text-foreground">Type</span>
            <select
              className="mt-1 block rounded border border-border bg-background px-2 py-1.5 text-sm"
              value={restraintType}
              onChange={(event) => setRestraintType(event.target.value)}
              data-testid="mh-restraint-type"
            >
              {RESTRAINT_TYPES.map((value) => (
                <option key={value} value={value}>
                  {value.toLowerCase()}
                </option>
              ))}
            </select>
          </label>

          <label className="block text-sm">
            <span className="font-medium text-foreground">Reason</span>
            <textarea
              className="mt-1 block w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
              rows={2}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              data-testid="mh-restraint-reason"
            />
          </label>

          <label className="block text-sm">
            <span className="font-medium text-foreground">De-escalation attempted first</span>
            <textarea
              className="mt-1 block w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
              rows={2}
              value={deEscalation}
              onChange={(event) => setDeEscalation(event.target.value)}
              placeholder="What was tried before restraint"
              data-testid="mh-restraint-de-escalation"
            />
          </label>

          {formError ? (
            <p className="text-sm text-danger" data-testid="mh-restraint-error">
              {formError}
            </p>
          ) : null}

          <button
            type="submit"
            className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
            disabled={actions.record.isPending || !reason.trim() || !deEscalation.trim()}
            data-testid="mh-restraint-submit"
          >
            {actions.record.isPending ? "Recording…" : "Record restraint event"}
          </button>
        </form>
      }
    >
      <ul className="space-y-2">
        {actionError ? (
          <li className="text-sm text-danger" data-testid="mh-restraint-action-error">
            {actionError}
          </li>
        ) : null}
        {rows.map((row, index) => {
          const id = String(row.id ?? index);
          const ended = !!row.ended_at;
          const reviewed = !!row.reviewed_at;
          return (
            <li key={id} className="rounded border border-border p-3 text-sm" data-testid="mh-restraint-row">
              <p className="font-medium text-foreground">
                {String(row.restraint_type ?? "—").toLowerCase()} · {when(row.initiated_at)}
              </p>
              <p className="mt-1 text-muted-foreground">{String(row.reason ?? "")}</p>
              <p className="mt-1 text-xs text-muted-foreground">
                De-escalation: {String(row.de_escalation_attempted ?? "not recorded")}
              </p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                {ended ? `Ended ${when(row.ended_at)}` : "Still in place"} ·{" "}
                {reviewed
                  ? `Reviewed by ${String(row.reviewed_by ?? "—")}: ${String(row.review_outcome ?? "")}`
                  : "Not yet reviewed"}
              </p>

              <div className="mt-2 flex flex-wrap items-center gap-2">
                {!ended ? (
                  <button
                    type="button"
                    className="rounded border border-border px-2 py-1 text-xs"
                    disabled={actions.end.isPending}
                    data-testid="mh-restraint-end"
                    onClick={async () => {
                      setActionError(null);
                      try {
                        await actions.end.mutateAsync(id);
                      } catch (error) {
                        setActionError(bffErrorMessage(error, "this restraint event"));
                      }
                    }}
                  >
                    Mark ended
                  </button>
                ) : null}

                {!reviewed ? (
                  <>
                    <input
                      className="rounded border border-border bg-background px-2 py-1 text-xs"
                      placeholder="Review outcome"
                      value={reviewOutcome[id] ?? ""}
                      onChange={(event) =>
                        setReviewOutcome((prev) => ({ ...prev, [id]: event.target.value }))
                      }
                      data-testid="mh-restraint-review-outcome"
                    />
                    <button
                      type="button"
                      className="rounded border border-border px-2 py-1 text-xs disabled:opacity-50"
                      disabled={actions.review.isPending || !(reviewOutcome[id] ?? "").trim()}
                      data-testid="mh-restraint-review"
                      onClick={async () => {
                        setActionError(null);
                        try {
                          await actions.review.mutateAsync({
                            eventId: id,
                            reviewOutcome: (reviewOutcome[id] ?? "").trim(),
                            reviewedBy: actor || undefined,
                          });
                          setReviewOutcome((prev) => ({ ...prev, [id]: "" }));
                        } catch (error) {
                          setActionError(bffErrorMessage(error, "this review"));
                        }
                      }}
                    >
                      Record review
                    </button>
                  </>
                ) : null}
              </div>
            </li>
          );
        })}
      </ul>
    </PanelShell>
  );
}
