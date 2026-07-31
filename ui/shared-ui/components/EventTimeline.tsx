"use client";

import React from "react";

/**
 * An ordered rail of things that happened, with who did them and when.
 *
 * This is the generic primitive; `TraumaEpisodeTimeline` remains its own component because it is
 * bound to the daidzai read model's phase vocabulary and its continuum-link semantics, neither of
 * which generalise. What generalises is the rail: chronological ordering, a marker per entry, an
 * actor, a time, and — the part the trauma component has no need of — an explicit statement about
 * the *gaps* between entries.
 *
 * Serial reassessment is the reason for that last part. A timeline of observations that simply stops
 * looks the same whether the patient was not reassessed or the reassessment was not recorded here.
 * When the caller supplies `expectedIntervalSeconds` (from the protocol in force — never a constant
 * in this file), the rail says how long the current gap has run and whether it has passed that
 * interval. It never says the patient deteriorated, was not reassessed, or is overdue for care: it
 * reports the age of the last recorded entry, which is the only thing the UI actually knows.
 *
 * Entries are rendered in the order given. Sorting is the caller's decision because "chronological"
 * is ambiguous when an entry's recorded time and its occurrence time differ, and this component is
 * not entitled to pick.
 */
export interface TimelineEntry {
  id: string;
  /** ISO-8601, when this occurred (not when it was recorded, if the two differ). */
  occurredAt: string;
  /** Short heading, e.g. "Adrenaline 1 mg IV". */
  label: string;
  /** Optional second line. */
  detail?: string;
  /** Who or what performed it. */
  actor?: string;
  /** Owning service or source, shown small and monospaced. */
  source?: string;
  /** Tone of the marker. Semantics are the caller's; this only picks a colour. */
  tone?: "default" | "critical" | "success" | "warning" | "info";
}

export interface EventTimelineProps {
  entries: TimelineEntry[];
  /**
   * Cadence the protocol in force expects between entries, in seconds. When supplied, the rail
   * reports the age of the most recent entry against it.
   */
  expectedIntervalSeconds?: number;
  /** What an empty rail means. Required, so "nothing here" is never left to the reader to interpret. */
  emptyMeaning: string;
  /** Overrides "now" for tests. */
  now?: number;
  className?: string;
  "data-testid"?: string;
}

const toneClass: Record<NonNullable<TimelineEntry["tone"]>, string> = {
  default: "bg-neutral-400",
  critical: "bg-danger",
  success: "bg-success",
  warning: "bg-warning",
  info: "bg-info",
};

function formatTime(iso: string): string {
  const parsed = Date.parse(iso);
  return Number.isNaN(parsed) ? iso : new Date(parsed).toLocaleString();
}

function formatGap(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds));
  if (safe < 60) return `${safe}s`;
  const minutes = Math.floor(safe / 60);
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  return remainder ? `${hours} h ${remainder} min` : `${hours} h`;
}

/**
 * How long since the most recent entry, and whether that exceeds the supplied cadence. Exported for
 * tests: it is the only non-presentational decision this component makes.
 */
export function gapSinceLatest(
  entries: TimelineEntry[],
  expectedIntervalSeconds: number | undefined,
  now: number,
): { seconds: number; exceedsExpected: boolean } | null {
  if (!entries.length) return null;
  const latest = entries.reduce((max, entry) => {
    const parsed = Date.parse(entry.occurredAt);
    return Number.isNaN(parsed) ? max : Math.max(max, parsed);
  }, Number.NEGATIVE_INFINITY);
  if (!Number.isFinite(latest)) return null;
  const seconds = (now - latest) / 1000;
  return {
    seconds,
    exceedsExpected: expectedIntervalSeconds != null && seconds > expectedIntervalSeconds,
  };
}

export function EventTimeline({
  entries,
  expectedIntervalSeconds,
  emptyMeaning,
  now = Date.now(),
  className = "",
  "data-testid": testId = "event-timeline",
}: EventTimelineProps) {
  const gap = gapSinceLatest(entries, expectedIntervalSeconds, now);

  if (entries.length === 0) {
    return (
      <p
        className={`rounded-lg border border-dashed border-border bg-background px-3 py-4 text-center text-xs text-muted-foreground ${className}`}
        data-testid={`${testId}-empty`}
      >
        {emptyMeaning}
      </p>
    );
  }

  return (
    <div className={className} data-testid={testId}>
      {gap ? (
        <p
          className={`mb-3 text-xs ${gap.exceedsExpected ? "font-medium text-warning-foreground" : "text-muted-foreground"}`}
          data-testid={`${testId}-gap`}
          data-exceeds-expected={gap.exceedsExpected ? "true" : "false"}
        >
          Last entry {formatGap(gap.seconds)} ago
          {expectedIntervalSeconds != null
            ? ` · expected every ${formatGap(expectedIntervalSeconds)}`
            : ""}
        </p>
      ) : null}

      <ol className="space-y-3 border-l border-border pl-4">
        {entries.map((entry) => (
          <li key={entry.id} className="relative" data-testid={`${testId}-entry`}>
            <span
              className={`absolute -left-[21px] top-1 h-3 w-3 rounded-full ${toneClass[entry.tone ?? "default"]}`}
              aria-hidden="true"
            />
            <div className="flex flex-wrap items-baseline gap-x-2">
              <span className="text-sm font-semibold text-foreground">{entry.label}</span>
              {entry.actor ? (
                <span className="text-xs text-muted-foreground">{entry.actor}</span>
              ) : null}
              {entry.source ? (
                <span className="font-mono text-[11px] text-muted-foreground">{entry.source}</span>
              ) : null}
              <span className="ml-auto font-mono text-[11px] text-muted-foreground">
                {formatTime(entry.occurredAt)}
              </span>
            </div>
            {entry.detail ? (
              <div className="text-xs text-muted-foreground">{entry.detail}</div>
            ) : null}
          </li>
        ))}
      </ol>
    </div>
  );
}
