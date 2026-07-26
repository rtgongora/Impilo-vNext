"use client";

import React from "react";
import Link from "next/link";
import { useImamTracingQueue, type ImamTracingRow } from "@/hooks/queries/useImam";
import { formatMuac, oedemaLabel, PROGRAMME_SHORT, TONE_BADGE } from "./imam-presentation";

/**
 * The defaulter tracing worklist for a facility.
 *
 * Two properties decide whether this list gets used or ignored:
 *
 *  - **The child nobody has ever reviewed is called out separately.** They arrive with the same
 *    missed-visit count as anyone else, and a plain count cannot say that no one has laid eyes on
 *    them since the day they were found to be malnourished.
 *  - **An empty list must mean nobody is overdue.** If the query fails this shows the failure, not
 *    a clean sheet: a clinic reading "no children to trace" goes home, and that is the worst
 *    possible false negative for a feature whose entire purpose is finding children who stopped
 *    coming.
 */
export function ImamTracingQueue({ facilityId }: { facilityId: string | null }) {
  const queue = useImamTracingQueue(facilityId);

  if (queue.isLoading) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="imam-tracing-loading">
        Loading the tracing worklist…
      </p>
    );
  }

  if (queue.isError) {
    const body = queue.error as { message?: string } | undefined;
    return (
      <div className="rounded-lg border border-red-500 bg-red-50 p-4" data-testid="imam-tracing-unavailable">
        <p className="text-sm font-semibold text-red-900">The tracing worklist could not be loaded</p>
        <p className="mt-1 text-xs text-red-900">
          {body?.message ??
            "This is not an empty worklist. Whether any child is overdue has not been established."}
        </p>
      </div>
    );
  }

  const rows = queue.data ?? [];
  const neverReviewed = rows.filter((row) => row.neverReviewed);
  const defaulters = rows.filter((row) => !row.neverReviewed && row.attendanceStatus === "DEFAULTER");
  const toTrace = rows.filter((row) => !row.neverReviewed && row.attendanceStatus !== "DEFAULTER");

  if (rows.length === 0) {
    return (
      <div
        className="rounded-lg border border-emerald-500 bg-emerald-50 p-4"
        data-testid="imam-tracing-empty"
      >
        <p className="text-sm font-semibold text-emerald-900">No child is overdue a review</p>
        <p className="mt-1 text-xs text-emerald-900">
          Every open nutrition episode at this facility is within its review interval.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4" data-testid="imam-tracing-queue">
      {neverReviewed.length > 0 && (
        <Group
          testId="imam-tracing-never-reviewed"
          tone="critical"
          title={`${neverReviewed.length} child${neverReviewed.length === 1 ? "" : "ren"} enrolled and never reviewed`}
          blurb="Nobody has seen these children since the day they were enrolled. They are the most urgent rows on this list, not the quietest."
          rows={neverReviewed}
        />
      )}
      {defaulters.length > 0 && (
        <Group
          testId="imam-tracing-defaulters"
          tone="critical"
          title={`${defaulters.length} defaulter${defaulters.length === 1 ? "" : "s"}`}
          blurb="Absent for the defined consecutive reviews. Trace, and record what the trace found rather than closing the episode blind."
          rows={defaulters}
        />
      )}
      {toTrace.length > 0 && (
        <Group
          testId="imam-tracing-to-trace"
          tone="warning"
          title={`${toTrace.length} child${toTrace.length === 1 ? "" : "ren"} to trace`}
          blurb="One review missed. Tracing now is the point — by the time a child meets the defaulter definition, the window in which a home visit brings them back has usually closed."
          rows={toTrace}
        />
      )}
    </div>
  );
}

function Group({
  testId,
  tone,
  title,
  blurb,
  rows,
}: {
  testId: string;
  tone: "critical" | "warning";
  title: string;
  blurb: string;
  rows: ImamTracingRow[];
}) {
  const border = tone === "critical" ? "border-red-500" : "border-amber-500";
  return (
    <section className={`rounded-lg border ${border} bg-background p-4`} data-testid={testId}>
      <h3 className="text-sm font-semibold text-foreground">{title}</h3>
      <p className="mt-0.5 text-xs text-muted-foreground">{blurb}</p>
      <ul className="mt-3 space-y-2">
        {rows.map((row) => (
          <li key={row.episodeId} className="rounded-md border border-border p-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <Link
                href={`/ehr/${row.patientId}/imam`}
                className="min-h-[44px] text-sm font-medium text-primary underline-offset-2 hover:underline"
                data-testid={`imam-tracing-link-${row.episodeId}`}
              >
                {row.patientId}
              </Link>
              <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${TONE_BADGE[tone]}`}>
                {PROGRAMME_SHORT[row.programme] ?? row.programme} ·{" "}
                {row.consecutiveMissedVisits} missed
              </span>
            </div>
            <p className="mt-1 text-xs text-muted-foreground">
              {row.neverReviewed
                ? `Enrolled ${row.enrolledAt?.slice(0, 10) ?? "—"} and never reviewed`
                : `Last seen ${row.lastContactOn ?? "—"}`}
              {" · "}
              {row.daysSinceLastContact} days ago · admitted at {formatMuac(row.admissionMuacCm)},{" "}
              {oedemaLabel(row.admissionOedema).toLowerCase()}
            </p>
          </li>
        ))}
      </ul>
    </section>
  );
}
