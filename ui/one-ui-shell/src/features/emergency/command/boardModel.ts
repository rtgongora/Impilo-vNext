/**
 * Board arithmetic for the emergency command view: counting, grouping and ordering rows the server
 * has already classified.
 *
 * Nothing here derives a clinical conclusion. Severity, state and alert type all arrive decided by
 * pct-service; this module only counts them and puts them in an order a human can scan. That
 * boundary is enforced by `scripts/guard/check-no-ts-clinical-logic.sh`, which scopes this directory.
 *
 * The one judgement it does make is what to do with a value it has never seen. An unrecognised
 * severity is sorted last and rendered with its own raw label — never silently dropped and never
 * folded into a bucket it may not belong to. pct is free to add a severity without this file being
 * redeployed, and the worst outcome is a row in an unhelpful position rather than a row that
 * vanished.
 */

import type {
  EmergencyAlertRow,
  EmergencyCommandSummary,
  EmergencyEpisodeRow,
} from "@/hooks/queries/useEmergencyEpisode";

// clinical-logic-guard: display ordering of a severity pct already assigned — this ranks rows for a
// human to scan, it does not decide any row's severity. An unranked value sorts last and keeps its
// own label rather than being folded into a bucket it may not belong to.
const SEVERITY_DISPLAY_ORDER = ["CRITICAL", "HIGH", "MODERATE", "LOW", "INFO"] as const;

export function severityRank(severity: string | null | undefined): number {
  if (!severity) return SEVERITY_DISPLAY_ORDER.length + 1;
  const index = SEVERITY_DISPLAY_ORDER.indexOf(
    severity.toUpperCase() as (typeof SEVERITY_DISPLAY_ORDER)[number],
  );
  return index === -1 ? SEVERITY_DISPLAY_ORDER.length : index;
}

/**
 * Open alerts first, then by severity, then oldest first — an old critical alert nobody has
 * acknowledged is the thing a charge nurse most needs at the top of a board.
 */
export function orderAlerts(alerts: EmergencyAlertRow[]): EmergencyAlertRow[] {
  return [...alerts].sort((a, b) => {
    const aResolved = a.status === "CLOSED" ? 1 : 0;
    const bResolved = b.status === "CLOSED" ? 1 : 0;
    if (aResolved !== bResolved) return aResolved - bResolved;

    const bySeverity = severityRank(a.severity) - severityRank(b.severity);
    if (bySeverity !== 0) return bySeverity;

    const aRaised = Date.parse(String(a.raised_at ?? "")) || Number.MAX_SAFE_INTEGER;
    const bRaised = Date.parse(String(b.raised_at ?? "")) || Number.MAX_SAFE_INTEGER;
    return aRaised - bRaised;
  });
}

export interface AlertStanding {
  /** Raised and not yet acknowledged by anyone. */
  unacknowledged: number;
  /** Acknowledged but with no recorded response. */
  acknowledgedNotResponded: number;
  /** Responded to but still open — the condition has not been declared gone. */
  respondedStillOpen: number;
  closed: number;
}

/**
 * Acknowledged, responded and closed are three separate authorities on pct's side and are counted
 * separately here. Collapsing them into "handled" is what lets an alert someone glanced at look the
 * same as one that was actually resolved.
 */
export function standingOf(alerts: EmergencyAlertRow[]): AlertStanding {
  const standing: AlertStanding = {
    unacknowledged: 0,
    acknowledgedNotResponded: 0,
    respondedStillOpen: 0,
    closed: 0,
  };
  for (const alert of alerts) {
    if (alert.status === "CLOSED" || alert.closed_at) standing.closed += 1;
    else if (alert.responded_at) standing.respondedStillOpen += 1;
    else if (alert.acknowledged_at) standing.acknowledgedNotResponded += 1;
    else standing.unacknowledged += 1;
  }
  return standing;
}

export interface StateTally {
  state: string;
  count: number;
}

/**
 * Episode counts by state. Reads the server's own `command-summary` when it is available, and falls
 * back to counting the board rows the client already holds — but the caller is told which happened,
 * because a count derived from a partially-loaded board is not the facility's true count and must
 * not be presented as one.
 */
export function tallyEpisodeStates(input: {
  summary?: EmergencyCommandSummary | null;
  episodes?: EmergencyEpisodeRow[] | null;
}): { tallies: StateTally[]; source: "server-summary" | "client-board" | "none" } {
  const fromSummary = input.summary?.episodes_by_state;
  if (fromSummary && Object.keys(fromSummary).length > 0) {
    return {
      tallies: Object.entries(fromSummary)
        .map(([state, count]) => ({ state, count: Number(count) || 0 }))
        .sort((a, b) => b.count - a.count || a.state.localeCompare(b.state)),
      source: "server-summary",
    };
  }

  if (input.episodes && input.episodes.length > 0) {
    const counts = new Map<string, number>();
    for (const episode of input.episodes) {
      const state = String(episode.state ?? "UNKNOWN");
      counts.set(state, (counts.get(state) ?? 0) + 1);
    }
    return {
      tallies: [...counts.entries()]
        .map(([state, count]) => ({ state, count }))
        .sort((a, b) => b.count - a.count || a.state.localeCompare(b.state)),
      source: "client-board",
    };
  }

  return { tallies: [], source: "none" };
}

/** Human-readable form of a SCREAMING_SNAKE state or severity, without inventing new vocabulary. */
export function humanise(value: string | null | undefined): string {
  if (!value) return "—";
  return value
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/^./, (c) => c.toUpperCase());
}
