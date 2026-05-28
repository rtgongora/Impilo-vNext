/**
 * Canonical frontend maturity taxonomy for parity sweep.
 * Maps legacy badge enums to the five sweep labels required by doctrine.
 */

export type CanonicalMaturity = "live" | "partial" | "fixture" | "not_wired" | "blocked";

/** Legacy UI badge values (web + mobile design system). */
export type LegacyMaturity =
  | "live"
  | "connected"
  | "partial"
  | "fixture"
  | "prototype"
  | "not_wired"
  | "requires_backend"
  | "blocked";

export const CANONICAL_MATURITY_LABELS: Record<CanonicalMaturity, string> = {
  live: "Live",
  partial: "Partial",
  fixture: "Fixture",
  not_wired: "Not wired",
  blocked: "Blocked",
};

/** Map legacy badge status to canonical sweep label. */
export function toCanonicalMaturity(status: LegacyMaturity): CanonicalMaturity {
  switch (status) {
    case "live":
    case "connected":
      return "live";
    case "partial":
    case "prototype":
      return "partial";
    case "fixture":
      return "fixture";
    case "not_wired":
    case "requires_backend":
      return "not_wired";
    case "blocked":
      return "blocked";
    default:
      return "partial";
  }
}

/** Map canonical label back to preferred legacy badge for components. */
export function toLegacyBadge(status: CanonicalMaturity): LegacyMaturity {
  switch (status) {
    case "live":
      return "live";
    case "partial":
      return "partial";
    case "fixture":
      return "fixture";
    case "not_wired":
      return "not_wired";
    case "blocked":
      return "blocked";
    default:
      return "partial";
  }
}
