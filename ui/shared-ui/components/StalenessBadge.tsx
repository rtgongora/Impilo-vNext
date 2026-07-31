"use client";

import React from "react";

/**
 * States how old a displayed value is, and — more importantly — when the display cannot vouch for it
 * at all.
 *
 * The distinction this exists to hold is between *no value* and *no answer*. A vitals panel showing
 * nothing because the patient has no observations recorded, and one showing nothing because the
 * source could not be reached, look identical without this. The first is a clinical fact; the second
 * is a blind spot, and a clinician who cannot tell them apart will read the blind spot as the fact.
 *
 * `asOf` is the **observation** time — when the value was true of the patient — not when the request
 * was served. A five-second-old response carrying a four-hour-old blood pressure is a four-hour-old
 * blood pressure.
 *
 * Thresholds are per source and supplied by the caller, because "stale" means something different
 * for a continuous monitor feed than for a manually charted observation round. No threshold defined
 * here would be true of both.
 */
export type StalenessState =
  /** Read successfully and within the source's own freshness threshold. */
  | "LIVE"
  /** Read successfully, but older than the source's threshold. */
  | "STALE"
  /** The source could not be read. Not the same as having no data. */
  | "UNAVAILABLE"
  /** Served from an offline cache; the source has not been reached since. */
  | "CACHED_OFFLINE"
  /** Read, but the source did not say when the value was observed. */
  | "VERSION_UNKNOWN";

export interface StalenessBadgeProps {
  state: StalenessState;
  /** ISO-8601 observation time. Required for LIVE/STALE/CACHED_OFFLINE; absent means VERSION_UNKNOWN. */
  asOf?: string | null;
  /** What the badge is describing, e.g. "vital signs". Used in the spoken/assistive description. */
  subject: string;
  className?: string;
  "data-testid"?: string;
}

const presentation: Record<StalenessState, { label: string; className: string }> = {
  LIVE: { label: "Live", className: "bg-success-soft text-primary-hover border-success/25" },
  STALE: { label: "Stale", className: "bg-warning-soft text-warning-foreground border-warning/35" },
  UNAVAILABLE: { label: "Unavailable", className: "bg-danger-soft text-danger border-danger/28" },
  CACHED_OFFLINE: { label: "Offline copy", className: "bg-info-soft text-primary-hover border-info/25" },
  VERSION_UNKNOWN: {
    label: "Age unknown",
    className: "bg-neutral-100 text-foreground border-border",
  },
};

/**
 * Classify an observation time against a caller-supplied threshold.
 *
 * Deliberately total: an absent `asOf` is `VERSION_UNKNOWN`, never silently treated as fresh. An
 * unreachable source is `UNAVAILABLE` regardless of what a cache holds, unless the caller says the
 * value came from the offline cache.
 */
export function classifyStaleness(input: {
  asOf?: string | null;
  thresholdSeconds: number;
  sourceReachable: boolean;
  fromOfflineCache?: boolean;
  now?: number;
}): StalenessState {
  if (input.fromOfflineCache) return "CACHED_OFFLINE";
  if (!input.sourceReachable) return "UNAVAILABLE";
  if (!input.asOf) return "VERSION_UNKNOWN";
  const observed = Date.parse(input.asOf);
  if (Number.isNaN(observed)) return "VERSION_UNKNOWN";
  const ageSeconds = ((input.now ?? Date.now()) - observed) / 1000;
  return ageSeconds > input.thresholdSeconds ? "STALE" : "LIVE";
}

/** "4 h 12 min ago" — coarse on purpose; a false precision invites a false confidence. */
export function describeAge(asOf: string, now: number = Date.now()): string {
  const observed = Date.parse(asOf);
  if (Number.isNaN(observed)) return "at an unknown time";
  const seconds = Math.max(0, Math.floor((now - observed) / 1000));
  if (seconds < 60) return "less than a minute ago";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  if (hours < 24) return remainder ? `${hours} h ${remainder} min ago` : `${hours} h ago`;
  const days = Math.floor(hours / 24);
  return days === 1 ? "1 day ago" : `${days} days ago`;
}

export function StalenessBadge({
  state,
  asOf,
  subject,
  className = "",
  "data-testid": testId = "staleness-badge",
}: StalenessBadgeProps) {
  const { label, className: tone } = presentation[state];

  const description =
    state === "UNAVAILABLE"
      ? `${subject} could not be read. Do not treat this as an absence of ${subject}.`
      : state === "VERSION_UNKNOWN"
        ? `${subject} was read, but the source did not say when it was observed.`
        : state === "CACHED_OFFLINE"
          ? `${subject} is an offline copy${asOf ? `, observed ${describeAge(asOf)}` : ""}.`
          : `${subject} observed ${asOf ? describeAge(asOf) : "at an unknown time"}.`;

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium ${tone} ${className}`}
      title={description}
      data-testid={testId}
      data-state={state}
    >
      <span aria-hidden="true" className="inline-block h-1.5 w-1.5 rounded-full bg-current" />
      {label}
      {asOf && (state === "LIVE" || state === "STALE" || state === "CACHED_OFFLINE") ? (
        <span className="font-normal opacity-80">· {describeAge(asOf)}</span>
      ) : null}
      <span className="sr-only">{description}</span>
    </span>
  );
}
