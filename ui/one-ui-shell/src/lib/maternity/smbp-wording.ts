/**
 * W12 — SMBP verdict presentation helpers.
 *
 * The clinical message/note text is already patient-safe (CKP's
 * `SmbpEscalationService.message()`/`.note()`) — this file only adds the badge treatment, so the
 * verdict is never rendered as a green "controlled" state when it isn't, and INSUFFICIENT_DATA
 * never reads as reassurance (docs/clinical/rmnp/citizen-pregnancy-smbp-mobile-contract.md §4.2).
 */

import type { SmbpVerdict } from "@/hooks/queries/useConfidentialMaternity";

export interface SmbpVerdictBadge {
  label: string;
  className: string;
}

export function smbpVerdictBadge(verdict: SmbpVerdict): SmbpVerdictBadge {
  switch (verdict) {
    case "URGENT":
      return { label: "Needs urgent care", className: "border-red-300 bg-red-50 text-red-900" };
    case "ELEVATED_REFER":
      return {
        label: "Come in for a check",
        className: "border-amber-300 bg-amber-50 text-amber-900",
      };
    case "CONTROLLED":
      return {
        label: "Looks controlled",
        className: "border-emerald-300 bg-emerald-50 text-emerald-900",
      };
    case "INSUFFICIENT_DATA":
    default:
      // Never green, never "controlled" — a quiet week of readings is not reassurance.
      return {
        label: "Not enough readings yet",
        className: "border-slate-300 bg-slate-50 text-slate-700",
      };
  }
}

/**
 * The honest outage line for a 502 READINGS_UNAVAILABLE/CKP_UNAVAILABLE. Prefers the server's own
 * `clinical_note` (it is written for her) and falls back to a safe default that still refuses to
 * imply "no readings" or "controlled".
 */
export function smbpUnavailableNotice(err: unknown): string {
  const e = err as { error?: { clinical_note?: string } } | null;
  return (
    e?.error?.clinical_note ??
    "We couldn't check your blood-pressure readings just now. This is not the same as having no readings — please try again shortly, and contact your care team if you feel unwell."
  );
}
