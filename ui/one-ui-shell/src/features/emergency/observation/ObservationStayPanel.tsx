"use client";

/**
 * Observation / short stay — the genuinely unowned middle state. Not an admission (no ward bed, no
 * admission record) and not a discharge (the patient has not left).
 *
 * The duration display uses the shared `ElapsedTimer`, anchored on pct's `started_at`, so "how long
 * has this patient been under observation" is answered from the server's clock rather than the
 * device's. `min_observation_minutes` is passed through to the timer as a cadence when pct supplied
 * one; it is a value a caller recorded, not one this component or pct computes, and when it is
 * absent the timer says no cadence is configured rather than inventing one.
 */

import { useState } from "react";
import { ElapsedTimer, EventTimeline, type TimelineEntry } from "shared-ui";

import type { EmergencyObservationStayRow } from "@/hooks/queries/useEmergencyEpisode";

const OUTCOMES = ["DISCHARGED", "ADMITTED", "ESCALATED_TO_RESUS", "ABSCONDED"] as const;

export interface ObservationStayPanelProps {
  stays: EmergencyObservationStayRow[] | undefined;
  isLoading: boolean;
  isError: boolean;
  onStart: (input: { reason: string }) => Promise<void>;
  onEnd: (input: { stayId: string; outcome: string }) => Promise<void>;
  isMutating?: boolean;
  serverError?: string | null;
  serverNow?: string;
}

export function ObservationStayPanel({
  stays,
  isLoading,
  isError,
  onStart,
  onEnd,
  isMutating,
  serverError,
  serverNow,
}: ObservationStayPanelProps) {
  const [reason, setReason] = useState("");
  const [outcome, setOutcome] = useState<string>(OUTCOMES[0]);

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Loading observation stays…</p>;
  }

  if (isError) {
    return (
      <p className="rounded-lg border border-danger/30 bg-danger-soft p-3 text-sm text-danger" data-testid="observation-unreadable">
        Observation stays could not be retrieved. Do not treat this as an absence of observation
        stays on this episode.
      </p>
    );
  }

  const rows = stays ?? [];
  const open = rows.find((stay) => !stay.ended_at);

  const history: TimelineEntry[] = rows.map((stay) => ({
    id: String(stay.stay_id),
    occurredAt: String(stay.started_at ?? ""),
    label: stay.ended_at ? `Observation ended — ${String(stay.outcome ?? "outcome not stated")}` : "Observation started",
    detail: stay.reason ? String(stay.reason) : undefined,
    actor: stay.ended_at ? String(stay.ended_by ?? "") : String(stay.started_by ?? ""),
    tone: stay.ended_at ? "default" : "info",
  }));

  return (
    <div className="space-y-4" data-testid="observation-panel">
      {open ? (
        <div className="rounded-lg border border-info/25 bg-info-soft p-4" data-testid="observation-open">
          <h3 className="text-sm font-semibold text-foreground">Under observation</h3>
          <div className="mt-2">
            <ElapsedTimer
              startedAt={String(open.started_at)}
              serverNow={serverNow}
              stopped={false}
              intervals={
                open.min_observation_minutes
                  ? [
                      {
                        label: "Minimum period recorded for this stay",
                        seconds: Number(open.min_observation_minutes) * 60,
                      },
                    ]
                  : undefined
              }
              data-testid="observation-timer"
            />
          </div>
          {open.reason ? (
            <p className="mt-2 text-xs text-muted-foreground">{String(open.reason)}</p>
          ) : null}

          <div className="mt-3 flex flex-wrap items-end gap-2">
            <label className="text-xs text-muted-foreground">
              Outcome
              <select
                className="mt-1 block rounded border border-border bg-background px-2 py-1 text-sm"
                value={outcome}
                onChange={(event) => setOutcome(event.target.value)}
                data-testid="observation-outcome"
              >
                {OUTCOMES.map((value) => (
                  <option key={value} value={value}>
                    {value.replaceAll("_", " ").toLowerCase()}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="button"
              className="rounded border border-border px-3 py-1.5 text-sm disabled:opacity-50"
              disabled={isMutating}
              onClick={() => void onEnd({ stayId: String(open.stay_id), outcome })}
              data-testid="observation-end"
            >
              End observation
            </button>
          </div>
        </div>
      ) : (
        <div className="rounded-lg border border-border bg-card p-4">
          <h3 className="text-sm font-semibold text-foreground">Start an observation stay</h3>
          <p className="mt-1 text-xs text-muted-foreground">
            Neither an admission nor a discharge. pct allows one open stay per episode.
          </p>
          <div className="mt-2 flex flex-wrap items-end gap-2">
            <label className="flex-1 text-xs text-muted-foreground">
              Reason
              <input
                className="mt-1 block w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                data-testid="observation-reason"
              />
            </label>
            <button
              type="button"
              className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
              disabled={isMutating || !reason.trim()}
              onClick={() => void onStart({ reason })}
              data-testid="observation-start"
            >
              Start
            </button>
          </div>
        </div>
      )}

      {serverError ? (
        <p className="rounded border border-danger/30 bg-danger-soft px-3 py-2 text-sm text-danger" data-testid="observation-server-error">
          {serverError}
        </p>
      ) : null}

      <section>
        <h3 className="mb-2 text-sm font-semibold text-foreground">Observation history</h3>
        <EventTimeline
          entries={history}
          emptyMeaning="No observation stay has been recorded on this episode."
        />
      </section>
    </div>
  );
}
