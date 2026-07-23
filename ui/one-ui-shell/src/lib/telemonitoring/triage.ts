/**
 * OF-B28 — triage-board ordering and response-window helpers (§14.6:
 * "sorted by severity and response-window pressure").
 */

import type { AlertEpisodeView, AlertSeverity } from "./types";

const SEVERITY_RANK: Record<AlertSeverity, number> = {
  EMERGENCY: 0,
  RED: 1,
  AMBER: 2,
};

export function severityRank(severity: AlertSeverity): number {
  return SEVERITY_RANK[severity] ?? 99;
}

/** Severity first (EMERGENCY → RED → AMBER), then tightest response deadline. */
export function triageSort(episodes: AlertEpisodeView[]): AlertEpisodeView[] {
  return [...episodes].sort((a, b) => {
    const bySeverity = severityRank(a.severity) - severityRank(b.severity);
    if (bySeverity !== 0) return bySeverity;
    const aDeadline = a.responseDeadlineAt ? new Date(a.responseDeadlineAt).getTime() : Infinity;
    const bDeadline = b.responseDeadlineAt ? new Date(b.responseDeadlineAt).getTime() : Infinity;
    return aDeadline - bDeadline;
  });
}

export interface ResponseWindow {
  /** Milliseconds remaining (negative = overdue). Null when no deadline is set. */
  remainingMs: number | null;
  overdue: boolean;
  /** Compact human label, e.g. "12m left" / "overdue 1h 05m" / "no window". */
  label: string;
}

export function responseWindow(episode: AlertEpisodeView, now: Date = new Date()): ResponseWindow {
  if (!episode.responseDeadlineAt) {
    return { remainingMs: null, overdue: false, label: "no response window" };
  }
  const deadline = new Date(episode.responseDeadlineAt).getTime();
  if (Number.isNaN(deadline)) {
    return { remainingMs: null, overdue: false, label: "no response window" };
  }
  const remaining = deadline - now.getTime();
  const abs = Math.abs(remaining);
  const hours = Math.floor(abs / 3_600_000);
  const minutes = Math.floor((abs % 3_600_000) / 60_000);
  const span = hours > 0 ? `${hours}h ${String(minutes).padStart(2, "0")}m` : `${minutes}m`;
  return remaining < 0
    ? { remainingMs: remaining, overdue: true, label: `overdue ${span}` }
    : { remainingMs: remaining, overdue: false, label: `${span} left` };
}

/** §14.7 rung catalogue — labels for the ladder recorder and history timeline. */
export const LADDER_RUNGS: { rung: number; label: string }[] = [
  { rung: 1, label: "Guided repeat reading" },
  { rung: 2, label: "In-app guidance" },
  { rung: 3, label: "Caregiver notification" },
  { rung: 4, label: "CHW task" },
  { rung: 5, label: "Secure chat" },
  { rung: 6, label: "Scheduled virtual review" },
  { rung: 7, label: "Urgent teleconsultation" },
  { rung: 8, label: "Facility contact" },
  { rung: 9, label: "Physical assessment" },
  { rung: 10, label: "Transport request" },
  { rung: 11, label: "Daidzai EMS dispatch" },
  { rung: 12, label: "Nhume urgent logistics" },
  { rung: 13, label: "Ndila routing support" },
  { rung: 14, label: "Emergency services handover" },
  { rung: 15, label: "Virtual support until handover" },
];

export function rungLabel(rung: number | null | undefined): string {
  if (rung == null) return "—";
  return LADDER_RUNGS.find((r) => r.rung === rung)?.label ?? `Rung ${rung}`;
}
