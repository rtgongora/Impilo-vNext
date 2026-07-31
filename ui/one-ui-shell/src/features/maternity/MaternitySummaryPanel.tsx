"use client";

/**
 * Maternity summary — the at-a-glance header for the maternity EHR page: is a partograph or CTG
 * session open, how many labour observations exist, and when the last one was recorded.
 *
 * Backend: `GET /internal/v1/maternity/summary` via `useMaternitySummary.ts`. The one rule this panel
 * must never break: a 502 (`maternity_summary_unavailable`) is an outage, not "no maternity record
 * for this woman" — see that hook's doc comment. This panel renders the outage as its own banner and
 * never falls through to the calm "no active session" empty state on that path.
 */

import { AlertTriangle, Baby, HeartPulse, Loader2 } from "lucide-react";
import {
  isMaternitySummaryUnavailable,
  useMaternitySummary,
} from "@/hooks/queries/useMaternitySummary";

export interface MaternitySummaryPanelProps {
  patientId: string;
  encounterId?: string;
}

function formatTimestamp(iso: string | null): string {
  if (!iso) return "No observations recorded yet";
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

export function MaternitySummaryPanel({ patientId, encounterId }: MaternitySummaryPanelProps) {
  const summary = useMaternitySummary(patientId, encounterId);

  if (summary.isLoading) {
    return (
      <div
        className="mb-4 flex items-center gap-2 rounded-lg border border-border bg-card px-4 py-3 text-sm text-muted-foreground"
        data-testid="maternity-summary-loading"
      >
        <Loader2 className="h-4 w-4 animate-spin" />
        Loading maternity summary…
      </div>
    );
  }

  if (summary.isError) {
    const unavailable = isMaternitySummaryUnavailable(summary.error);
    return (
      <div
        className="mb-4 flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft px-4 py-3 text-sm text-red-900"
        data-testid="maternity-summary-unavailable"
        role="alert"
      >
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
        <div>
          <p className="font-semibold">The maternity summary could not be retrieved</p>
          <p className="mt-1 text-xs">
            {unavailable
              ? "This is not the same as this patient having no maternity record — the summary could not be read. Do not treat it as an empty chart."
              : "Something went wrong loading the maternity summary. Try again."}
          </p>
        </div>
      </div>
    );
  }

  const data = summary.data?.data;
  if (!data) return null;

  return (
    <div className="mb-4 rounded-lg border border-pink-200/90 bg-card p-4" data-testid="maternity-summary-panel">
      <div className="flex flex-wrap items-center gap-4">
        <div className="flex items-center gap-2 text-sm" data-testid="maternity-summary-partograph">
          <Baby className={`h-4 w-4 ${data.partograph_active ? "text-pink-600" : "text-muted-foreground"}`} />
          <span className="font-medium text-foreground">
            Partograph: {data.partograph_active ? "Active" : "No open session"}
          </span>
          {data.partograph_active && data.progress?.status && (
            <span className="text-xs text-muted-foreground">({data.progress.status.replace(/_/g, " ").toLowerCase()})</span>
          )}
        </div>
        <div className="flex items-center gap-2 text-sm" data-testid="maternity-summary-ctg">
          <HeartPulse className={`h-4 w-4 ${data.ctg_active ? "text-rose-600" : "text-muted-foreground"}`} />
          <span className="font-medium text-foreground">
            CTG: {data.ctg_active ? "Active" : "No open session"}
          </span>
        </div>
      </div>
      <div className="mt-2 flex flex-wrap gap-4 text-xs text-muted-foreground">
        <span data-testid="maternity-summary-observation-count">
          {data.observation_count} labour observation{data.observation_count === 1 ? "" : "s"} recorded
        </span>
        <span data-testid="maternity-summary-last-observed">
          Last observed: {formatTimestamp(data.last_observed_at)}
        </span>
      </div>
    </div>
  );
}
