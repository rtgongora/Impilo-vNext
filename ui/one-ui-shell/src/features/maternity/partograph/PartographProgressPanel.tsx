"use client";

import React from "react";
import type { MaternitySummaryProgress } from "@/hooks/queries/useMaternitySummary";
import { formatHoursBehind, presentProgress } from "./partograph-progress-presentation";

/**
 * The partograph's verdict: which side of the alert and action lines this labour is on, and what to
 * do about it.
 *
 * The engine already computed all of this — the alert line rising 1 cm/hour from the recognition of
 * active labour, the action line four hours to its right, the origin pinned so a corrected reading
 * cannot silently rewrite whether the labour had crossed it. The summary displayed only the raw
 * status name in muted grey. This renders the answer.
 */
export function PartographProgressPanel({ progress }: { progress: MaternitySummaryProgress }) {
  const p = presentProgress(progress);
  const behind = formatHoursBehind(progress.hours_behind_alert_line);
  const outstanding = progress.outstanding_observations ?? [];

  return (
    <section
      className={`mt-3 rounded-lg border p-3 ${p.containerClass}`}
      data-testid="partograph-progress"
      data-tone={p.tone}
      data-status={progress.status}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm font-semibold text-foreground">{p.label}</span>
        <span className={`rounded px-2 py-0.5 text-xs font-semibold ${p.chipClass}`}>
          {p.tone === "insufficient" ? "Not established" : progress.status.replace(/_/g, " ")}
        </span>
      </div>

      {/* The action, not just the status. A status a midwife has to translate is one that gets
          skimmed, and on this screen the translation is "intervene now". */}
      {p.action && (
        <p className="mt-1 text-sm font-medium text-foreground" data-testid="partograph-progress-action">
          {p.action}
        </p>
      )}

      {(progress.latest_dilation_cm !== null || progress.expected_dilation_cm !== null) && (
        <p className="mt-1 text-xs text-muted-foreground" data-testid="partograph-progress-dilation">
          {progress.latest_dilation_cm !== null ? `${progress.latest_dilation_cm} cm recorded` : "no dilation recorded"}
          {progress.expected_dilation_cm !== null ? ` · ${progress.expected_dilation_cm} cm expected on the alert line` : ""}
          {behind ? ` · ${behind}` : ""}
        </p>
      )}

      {outstanding.length > 0 && (
        // What the verdict is waiting on. Without this, "not established" is a dead end rather than
        // a job — and the job is the point.
        <div className="mt-2" data-testid="partograph-progress-outstanding">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Not yet recorded
          </p>
          <ul className="mt-1 list-disc space-y-0.5 pl-5">
            {outstanding.map((o) => (
              <li key={o} className="text-sm text-foreground">
                {o.replace(/_/g, " ").toLowerCase()}
              </li>
            ))}
          </ul>
        </div>
      )}

      {p.tone === "insufficient" && (
        <p className="mt-2 text-sm text-foreground" data-testid="partograph-progress-insufficient-note">
          Not enough has been recorded to say whether this labour is progressing. This is not a
          finding that it is — an unmonitored labour is not a reassuring one.
        </p>
      )}

      <p className="mt-2 text-xs text-muted-foreground" data-testid="partograph-progress-provenance">
        {progress.content_source}
        {progress.content_version ? ` · ${progress.content_version}` : ""}
      </p>
    </section>
  );
}
