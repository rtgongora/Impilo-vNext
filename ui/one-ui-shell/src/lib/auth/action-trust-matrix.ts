/**
 * Risk-based Action → Assurance Trust Matrix (Health OS §5, §10; gateway doctrine §4.1).
 *
 * A single, data-driven mapping of *what a citizen is trying to reach or do* to the
 * identity assurance it requires. It unifies three previously-disjoint layers:
 *
 *   1. the per-route `guard` enum (routes.ts) — still authoritative for role/context;
 *   2. the hard-coded `HEALTH_RESTRICTED_PREFIXES` bounce in AuthGuardProvider (now
 *      derived from ROUTE_ASSURANCE_RULES here); and
 *   3. per-action step-up (useStepUp → tshepo PolicyEngine / identity-assurance RiskEngine),
 *      which stays the server-side authority — this matrix only decides *which* assurance
 *      / step-up a given action needs so the UI can explain and route honestly.
 *
 * Doctrine: this matrix is a SUPERSET of the prior gates — it never lowers a restriction.
 * The BFF Session Experience Contract remains authoritative for route *visibility*; this
 * governs the assurance dimension only. Client evaluation is defence-in-depth and a UX
 * affordance (honest explainers), never the sole enforcement point.
 */

/** Identity assurance ladder — mirrors AuthUser["assuranceLevel"]. */
export type AssuranceLevel = "UNVERIFIED" | "TEMPORARY" | "VERIFIED";

/** Low → high. Index is the rank used for comparisons. */
export const ASSURANCE_ORDER: readonly AssuranceLevel[] = [
  "UNVERIFIED",
  "TEMPORARY",
  "VERIFIED",
] as const;

/** Human labels for each level, reused by the status chip and explainers. */
export const ASSURANCE_LABELS: Record<AssuranceLevel, string> = {
  UNVERIFIED: "Basic",
  TEMPORARY: "Temporary",
  VERIFIED: "Verified",
};

export function assuranceRank(level: AssuranceLevel | null | undefined): number {
  if (!level) return 0;
  const idx = ASSURANCE_ORDER.indexOf(level);
  return idx < 0 ? 0 : idx;
}

/** True when `current` satisfies (meets or exceeds) `required`. */
export function meetsAssurance(
  current: AssuranceLevel | null | undefined,
  required: AssuranceLevel,
): boolean {
  return assuranceRank(current) >= assuranceRank(required);
}

// ── Route → assurance rules ─────────────────────────────────────────────────────────

export interface RouteAssuranceRule {
  /** Path prefix this rule guards (prefix match on the pathname). */
  prefix: string;
  /** Minimum assurance a person needs to enter this surface. */
  requires: AssuranceLevel;
  /** Short human name of the surface, for explainers. */
  surface: string;
  /** What information/capability is protected here, for explainers. */
  protects: string;
}

/**
 * Assurance-gated route prefixes. This set is a strict superset of the former
 * HEALTH_RESTRICTED_PREFIXES array (which bounced UNVERIFIED users only): every prefix
 * there requires at least TEMPORARY here, so UNVERIFIED remains blocked and TEMPORARY /
 * VERIFIED remain allowed — behaviour preserved, now data-driven and explainable.
 */
export const ROUTE_ASSURANCE_RULES: readonly RouteAssuranceRule[] = [
  { prefix: "/ehr", requires: "TEMPORARY", surface: "Health records", protects: "your clinical records and history" },
  { prefix: "/clinical", requires: "TEMPORARY", surface: "Clinical care", protects: "clinical encounters and care actions" },
  { prefix: "/pharmacy", requires: "TEMPORARY", surface: "Pharmacy", protects: "prescriptions and medication records" },
  { prefix: "/lab", requires: "TEMPORARY", surface: "Laboratory", protects: "lab orders and results" },
  { prefix: "/queue", requires: "TEMPORARY", surface: "Care queue", protects: "care queue and encounter workflows" },
  { prefix: "/scheduling", requires: "TEMPORARY", surface: "Scheduling", protects: "clinical scheduling and rosters" },
  { prefix: "/shift", requires: "TEMPORARY", surface: "Shift", protects: "shift and workspace context" },
  { prefix: "/telemedicine", requires: "TEMPORARY", surface: "Telemedicine", protects: "remote consultations" },
] as const;

/** Return the most specific (longest-prefix) matching route assurance rule, or null. */
export function matchRouteAssuranceRule(pathname: string): RouteAssuranceRule | null {
  let best: RouteAssuranceRule | null = null;
  for (const rule of ROUTE_ASSURANCE_RULES) {
    if (pathname === rule.prefix || pathname.startsWith(`${rule.prefix}/`) || pathname.startsWith(rule.prefix)) {
      if (!best || rule.prefix.length > best.prefix.length) best = rule;
    }
  }
  return best;
}

// ── Action → assurance rules ────────────────────────────────────────────────────────

/** Canonical sensitive action identifiers evaluated by the matrix. */
export type TrustAction =
  | "health-record.view"
  | "health-record.share"
  | "prescription.collect"
  | "prescription.prescribe"
  | "appointment.book"
  | "telemedicine.join"
  | "claim.submit"
  | "delegated-pickup.authorize"
  | "wallet.high-value-payment";

export interface ActionTrustRule {
  action: TrustAction;
  /** Minimum assurance the action needs. */
  requires: AssuranceLevel;
  /**
   * Whether a fresh step-up challenge (useStepUp → PolicyEngine) is required even when
   * the assurance level is already met. The server is authoritative; this drives the UI
   * into the challenge loop proactively.
   */
  needsStepUp: boolean;
  /** Preferred step-up method to seed the challenge (server may override). */
  stepUpMethod?: string;
  /** Human name of the action, for explainers. */
  label: string;
  /** What information/capability the action touches, for explainers. */
  protects: string;
}

export const ACTION_TRUST_RULES: Record<TrustAction, ActionTrustRule> = {
  "health-record.view": {
    action: "health-record.view",
    requires: "TEMPORARY",
    needsStepUp: false,
    label: "View health records",
    protects: "your clinical records and history",
  },
  "health-record.share": {
    action: "health-record.share",
    requires: "VERIFIED",
    needsStepUp: true,
    stepUpMethod: "MFA",
    label: "Share health records",
    protects: "who can see your clinical records",
  },
  "prescription.collect": {
    action: "prescription.collect",
    requires: "TEMPORARY",
    needsStepUp: false,
    label: "Collect a prescription",
    protects: "dispensing of your medication",
  },
  "prescription.prescribe": {
    action: "prescription.prescribe",
    requires: "VERIFIED",
    needsStepUp: true,
    stepUpMethod: "MFA",
    label: "Prescribe medication",
    protects: "issuing regulated prescriptions",
  },
  "appointment.book": {
    action: "appointment.book",
    requires: "TEMPORARY",
    needsStepUp: false,
    label: "Book an appointment",
    protects: "booking care in your name",
  },
  "telemedicine.join": {
    action: "telemedicine.join",
    requires: "TEMPORARY",
    needsStepUp: false,
    label: "Join a telemedicine consult",
    protects: "your remote consultation",
  },
  "claim.submit": {
    action: "claim.submit",
    requires: "VERIFIED",
    needsStepUp: true,
    stepUpMethod: "MFA",
    label: "Submit a claim",
    protects: "financial claims against your coverage",
  },
  "delegated-pickup.authorize": {
    action: "delegated-pickup.authorize",
    requires: "VERIFIED",
    needsStepUp: true,
    stepUpMethod: "SMS_OTP",
    label: "Authorise delegated pickup",
    protects: "letting someone collect on your behalf",
  },
  "wallet.high-value-payment": {
    action: "wallet.high-value-payment",
    requires: "VERIFIED",
    needsStepUp: true,
    stepUpMethod: "MFA",
    label: "Make a high-value payment",
    protects: "money moving from your wallet",
  },
};

export function matchActionTrustRule(action: TrustAction): ActionTrustRule {
  return ACTION_TRUST_RULES[action];
}

// ── Decisions ───────────────────────────────────────────────────────────────────────

/** Where a blocked person is sent to raise their assurance. */
export const ASSURANCE_UPGRADE_PATH = "/auth/register/assurance";

export interface TrustDecision {
  /** True when the current level satisfies the requirement (and no fresh step-up is pending). */
  allowed: boolean;
  /** Minimum assurance required. */
  requires: AssuranceLevel;
  /** The level the caller evaluated against. */
  current: AssuranceLevel;
  /** True when the assurance level itself is insufficient (an upgrade is needed). */
  needsUpgrade: boolean;
  /** True when a fresh step-up challenge is required for an otherwise-permitted action. */
  needsStepUp: boolean;
  /** Preferred step-up method when needsStepUp is true. */
  stepUpMethod?: string;
  /** Human name of the surface/action being evaluated. */
  label: string;
  /** What is protected, for the honest explainer. */
  protects: string;
  /** Path into the upgrade flow (present when needsUpgrade). */
  upgradePath?: string;
}

const NORMALIZE = (level: AssuranceLevel | null | undefined): AssuranceLevel =>
  level && ASSURANCE_ORDER.includes(level) ? level : "UNVERIFIED";

/**
 * Evaluate whether a pathname is reachable at the current assurance level.
 * Returns null when no assurance rule applies (route is unrestricted on this dimension).
 */
export function evaluateRouteTrust(
  pathname: string,
  current: AssuranceLevel | null | undefined,
): TrustDecision | null {
  const rule = matchRouteAssuranceRule(pathname);
  if (!rule) return null;
  const level = NORMALIZE(current);
  const needsUpgrade = !meetsAssurance(level, rule.requires);
  return {
    allowed: !needsUpgrade,
    requires: rule.requires,
    current: level,
    needsUpgrade,
    needsStepUp: false,
    label: rule.surface,
    protects: rule.protects,
    upgradePath: needsUpgrade ? ASSURANCE_UPGRADE_PATH : undefined,
  };
}

/**
 * Evaluate a sensitive action. `stepUpFresh` lets the caller declare that a step-up was
 * recently completed (so `needsStepUp` resolves to false); the server (PolicyEngine)
 * remains authoritative and may still require one.
 */
export function evaluateActionTrust(
  action: TrustAction,
  current: AssuranceLevel | null | undefined,
  stepUpFresh = false,
): TrustDecision {
  const rule = ACTION_TRUST_RULES[action];
  const level = NORMALIZE(current);
  const needsUpgrade = !meetsAssurance(level, rule.requires);
  const needsStepUp = !needsUpgrade && rule.needsStepUp && !stepUpFresh;
  return {
    allowed: !needsUpgrade && !needsStepUp,
    requires: rule.requires,
    current: level,
    needsUpgrade,
    needsStepUp,
    stepUpMethod: needsStepUp ? rule.stepUpMethod : undefined,
    label: rule.label,
    protects: rule.protects,
    upgradePath: needsUpgrade ? ASSURANCE_UPGRADE_PATH : undefined,
  };
}
