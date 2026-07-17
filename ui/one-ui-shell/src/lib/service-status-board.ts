/**
 * Indicative public service-status board (local content).
 *
 * IMPORTANT — this is NOT a live status feed. A real-time service-status backend is a later
 * wave; until it exists we must not fabricate live states. This module is the single, clearly
 * labelled, local, honest source for a static "what Impilo offers and its general availability"
 * board. Every state here is authored and marked INDICATIVE in the UI. When the status backend
 * lands, this board is replaced by a resolved feed — do not grow live semantics here.
 */

export type ServiceState =
  | "OPERATIONAL"
  | "DEGRADED"
  | "PARTIAL"
  | "UNAVAILABLE"
  | "MAINTENANCE"
  | "RESTORED"
  /**
   * UNKNOWN — a live probe exists for a group but could not resolve a state (the live
   * status backend returned UNKNOWN). Rendered as a neutral, honest "not monitored" label;
   * NEVER shown as green. This is distinct from the authored INDICATIVE states above, which
   * are local fallbacks used when a group is not live-monitored at all.
   */
  | "UNKNOWN";

export interface ServiceStatusEntry {
  group: string;
  description: string;
  /** Indicative, authored state — not a live probe. */
  state: ServiceState;
}

export const STATUS_BOARD_UPDATED = "2026-07-17";

export const SERVICE_STATUS_BOARD: ServiceStatusEntry[] = [
  {
    group: "Find care & facility directory",
    description: "Search facilities, opening hours and how to visit.",
    state: "OPERATIONAL",
  },
  {
    group: "Emergency help",
    description: "Emergency numbers and guidance. Never blocked by sign-in.",
    state: "OPERATIONAL",
  },
  {
    group: "Sign in & accounts",
    description: "Create an account, sign in and request a Health ID.",
    state: "OPERATIONAL",
  },
  {
    group: "Health records & results",
    description: "View your records, results and appointments once signed in.",
    state: "OPERATIONAL",
  },
  {
    group: "Payments & health cover",
    description: "Check cover, view bills and pay for care.",
    state: "OPERATIONAL",
  },
  {
    group: "Report a problem & feedback",
    description: "Report unsafe care or a complaint, and get involved.",
    state: "OPERATIONAL",
  },
];

export const STATE_META: Record<ServiceState, { label: string; badge: string; dot: string }> = {
  OPERATIONAL: { label: "Operational", badge: "bg-emerald-50 text-emerald-700", dot: "bg-emerald-500" },
  RESTORED: { label: "Restored", badge: "bg-emerald-50 text-emerald-700", dot: "bg-emerald-500" },
  MAINTENANCE: { label: "Maintenance", badge: "bg-sky-50 text-sky-700", dot: "bg-sky-500" },
  DEGRADED: { label: "Degraded", badge: "bg-amber-50 text-amber-700", dot: "bg-amber-500" },
  PARTIAL: { label: "Partial", badge: "bg-amber-50 text-amber-700", dot: "bg-amber-500" },
  UNAVAILABLE: { label: "Unavailable", badge: "bg-red-50 text-red-700", dot: "bg-red-500" },
  UNKNOWN: { label: "Status not monitored", badge: "bg-slate-100 text-slate-600", dot: "bg-slate-400" },
};
