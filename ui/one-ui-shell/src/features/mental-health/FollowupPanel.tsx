"use client";

/**
 * Follow-up after a psychiatric emergency contact.
 *
 * The period after discharge from a mental-health crisis is when contact is most likely to be lost,
 * so a scheduled follow-up that nobody completed stays visible as scheduled rather than quietly
 * ageing out of the list. Completing one records what came of it, which is what makes a missed
 * follow-up distinguishable from an unrecorded one.
 */

import { useState } from "react";

import {
  FOLLOWUP_TYPES,
  useMentalHealthFollowupActions,
  useMentalHealthFollowups,
} from "@/hooks/queries/useMentalHealth";
import { bffErrorMessage } from "@/lib/bff-errors";

import { PanelShell } from "./PanelShell";

function when(value: unknown): string {
  if (!value) return "—";
  const parsed = new Date(String(value));
  return Number.isNaN(parsed.getTime()) ? String(value) : parsed.toLocaleString();
}

export function FollowupPanel({ referralId, actor }: { referralId: string; actor: string }) {
  const followups = useMentalHealthFollowups(referralId);
  const actions = useMentalHealthFollowupActions(referralId);

  const [followupType, setFollowupType] = useState<string>("CLINIC_REVIEW");
  const [scheduledAt, setScheduledAt] = useState("");
  const [outcomeNotes, setOutcomeNotes] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const rows = followups.data ?? [];

  return (
    <PanelShell
      title="Follow-up"
      subject="this patient's follow-up appointments"
      description="mh_followup — a scheduled contact stays visible until somebody records what came of it"
      isLoading={followups.isLoading}
      isError={followups.isError}
      error={followups.error}
      isEmpty={rows.length === 0}
      emptyMessage="No follow-up has been scheduled for this patient."
      data-testid="mh-followups"
      actions={
        <form
          className="space-y-3"
          data-testid="mh-followup-form"
          onSubmit={async (event) => {
            event.preventDefault();
            setFormError(null);
            try {
              await actions.schedule.mutateAsync({
                scheduledAt: new Date(scheduledAt).toISOString(),
                followupType,
                scheduledBy: actor || undefined,
              });
              setScheduledAt("");
            } catch (error) {
              setFormError(bffErrorMessage(error, "this follow-up"));
            }
          }}
        >
          <div className="flex flex-wrap gap-3">
            <label className="text-sm">
              <span className="block font-medium text-foreground">Type</span>
              <select
                className="mt-1 rounded border border-border bg-background px-2 py-1.5 text-sm"
                value={followupType}
                onChange={(event) => setFollowupType(event.target.value)}
                data-testid="mh-followup-type"
              >
                {FOLLOWUP_TYPES.map((value) => (
                  <option key={value} value={value}>
                    {value.replaceAll("_", " ").toLowerCase()}
                  </option>
                ))}
              </select>
            </label>

            <label className="text-sm">
              <span className="block font-medium text-foreground">When</span>
              <input
                type="datetime-local"
                className="mt-1 rounded border border-border bg-background px-2 py-1.5 text-sm"
                value={scheduledAt}
                onChange={(event) => setScheduledAt(event.target.value)}
                data-testid="mh-followup-scheduled-at"
              />
            </label>
          </div>

          {formError ? (
            <p className="text-sm text-danger" data-testid="mh-followup-error">
              {formError}
            </p>
          ) : null}

          <button
            type="submit"
            className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
            disabled={actions.schedule.isPending || !scheduledAt}
            data-testid="mh-followup-submit"
          >
            {actions.schedule.isPending ? "Scheduling…" : "Schedule follow-up"}
          </button>
        </form>
      }
    >
      <ul className="space-y-2">
        {actionError ? (
          <li className="text-sm text-danger" data-testid="mh-followup-action-error">
            {actionError}
          </li>
        ) : null}
        {rows.map((row, index) => {
          const id = String(row.id ?? index);
          const completed = !!row.completed_at;
          return (
            <li key={id} className="rounded border border-border p-3 text-sm" data-testid="mh-followup-row">
              <p className="font-medium text-foreground">
                {String(row.followup_type ?? "Follow-up").replaceAll("_", " ").toLowerCase()} ·{" "}
                {when(row.scheduled_at)}
              </p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                Scheduled by {String(row.scheduled_by ?? "—")} ·{" "}
                {completed
                  ? `completed ${when(row.completed_at)}`
                  : "not yet completed — no contact has been recorded"}
              </p>
              {completed && row.outcome_notes ? (
                <p className="mt-1 text-muted-foreground">{String(row.outcome_notes)}</p>
              ) : null}

              {!completed ? (
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <input
                    className="rounded border border-border bg-background px-2 py-1 text-xs"
                    placeholder="What came of it"
                    value={outcomeNotes[id] ?? ""}
                    onChange={(event) =>
                      setOutcomeNotes((prev) => ({ ...prev, [id]: event.target.value }))
                    }
                    data-testid="mh-followup-outcome"
                  />
                  <button
                    type="button"
                    className="rounded border border-border px-2 py-1 text-xs disabled:opacity-50"
                    disabled={actions.complete.isPending}
                    data-testid="mh-followup-complete"
                    onClick={async () => {
                      setActionError(null);
                      try {
                        await actions.complete.mutateAsync({
                          followupId: id,
                          outcomeNotes: (outcomeNotes[id] ?? "").trim() || undefined,
                        });
                      } catch (error) {
                        setActionError(bffErrorMessage(error, "this follow-up"));
                      }
                    }}
                  >
                    Mark completed
                  </button>
                </div>
              ) : null}
            </li>
          );
        })}
      </ul>
    </PanelShell>
  );
}
