/**
 * Patient identity visibility levels and privacy badge metadata.
 *
 * Presentation model only: enforcement requires Tshepo/OPA policy work
 * (handoff HO-4). The UI must always show which privacy level applies so no
 * surface can silently expose more identity than the session mode permits.
 */

export type IdentityVisibilityLevel =
  | "FULL"
  | "CARE_TEAM_LIMITED"
  | "PSEUDONYMISED"
  | "ANONYMISED"
  | "EMERGENCY_ACCESS"
  | "SENSITIVE_RESTRICTED";

export interface PrivacyLevelDescriptor {
  level: IdentityVisibilityLevel;
  label: string;
  description: string;
  /** Tailwind classes for the badge (kept here so badges render identically everywhere). */
  badgeClassName: string;
  /** True when policy enforcement for this level exists today. */
  policyEnforced: boolean;
}

export const PRIVACY_LEVELS: Record<IdentityVisibilityLevel, PrivacyLevelDescriptor> = {
  FULL: {
    level: "FULL",
    label: "Full identity",
    description:
      "Name, Impilo ID and demographics visible. Direct care by the authorised care team only.",
    badgeClassName: "bg-emerald-50 text-emerald-700 border-emerald-200",
    policyEnforced: false,
  },
  CARE_TEAM_LIMITED: {
    level: "CARE_TEAM_LIMITED",
    label: "Care-team limited",
    description:
      "Full identity only for providers directly involved in care; observers and learners see masked data.",
    badgeClassName: "bg-sky-50 text-sky-700 border-sky-200",
    policyEnforced: false,
  },
  PSEUDONYMISED: {
    level: "PSEUDONYMISED",
    label: "Pseudonymised",
    description:
      "Case code instead of name; limited demographics. For MDT, audit and teaching when full identity is unnecessary.",
    badgeClassName: "bg-violet-50 text-violet-700 border-violet-200",
    policyEnforced: false,
  },
  ANONYMISED: {
    level: "ANONYMISED",
    label: "De-identified",
    description:
      "Identifiers removed or masked. For teaching, audit, case libraries and broad panel discussion. Imaging/DICOM requires metadata and burned-in pixel de-identification checks.",
    badgeClassName: "bg-slate-100 text-slate-700 border-slate-200",
    policyEnforced: false,
  },
  EMERGENCY_ACCESS: {
    level: "EMERGENCY_ACCESS",
    label: "Emergency access",
    description:
      "Identity shown under emergency/break-glass policy. Justification and strict audit required.",
    badgeClassName: "bg-red-50 text-red-700 border-red-200",
    policyEnforced: false,
  },
  SENSITIVE_RESTRICTED: {
    level: "SENSITIVE_RESTRICTED",
    label: "Sensitive / restricted case",
    description:
      "Additional restrictions apply (e.g. mental health, protected cases). Minimum-necessary access with elevated audit.",
    badgeClassName: "bg-amber-50 text-amber-800 border-amber-200",
    policyEnforced: false,
  },
};

export const PRIVACY_LEVEL_ORDER: IdentityVisibilityLevel[] = [
  "FULL",
  "CARE_TEAM_LIMITED",
  "PSEUDONYMISED",
  "ANONYMISED",
  "EMERGENCY_ACCESS",
  "SENSITIVE_RESTRICTED",
];
