/**
 * ReferralStatusBadge — presentational chip mapping a teleconsult referral's lifecycle
 * status to a human label and a semantic tone.
 *
 * Pure presentation: no data fetching, no side effects. The status→descriptor map is
 * exported so the mapping can be unit-tested and reused (e.g. worklist buckets).
 *
 * Tones:
 *   active     — work is in flight (accepted / in review / scheduled / responded / reopened)
 *   waiting    — parked awaiting a party or a step (draft/routed/submitted/offered/awaiting-*)
 *   terminal   — the loop is closed (completed / closed)
 *   exception  — an off-path outcome (declined/cancelled/escalated/transferred/expired/…)
 */

export type ReferralStatusTone = "active" | "waiting" | "terminal" | "exception";

export interface ReferralStatusDescriptor {
  label: string;
  tone: ReferralStatusTone;
}

/** Every state-machine value the teleconsult lifecycle can surface. */
export const REFERRAL_STATUS_DESCRIPTORS: Record<string, ReferralStatusDescriptor> = {
  DRAFT: { label: "Draft", tone: "waiting" },
  ROUTED: { label: "Routed", tone: "waiting" },
  SUBMITTED: { label: "Submitted", tone: "waiting" },
  OFFERED: { label: "Offered", tone: "waiting" },
  NEEDS_MORE_INFORMATION: { label: "Needs more information", tone: "waiting" },
  ACCEPTED: { label: "Accepted", tone: "active" },
  IN_REVIEW: { label: "In review", tone: "active" },
  SCHEDULED: { label: "Scheduled", tone: "active" },
  RESPONDED: { label: "Responded", tone: "active" },
  AWAITING_LOCAL_ACTION: { label: "Awaiting local action", tone: "waiting" },
  AWAITING_PATIENT_ACTION: { label: "Awaiting patient action", tone: "waiting" },
  AWAITING_RESULTS: { label: "Awaiting results", tone: "waiting" },
  FOLLOW_UP_DUE: { label: "Follow-up due", tone: "waiting" },
  COMPLETED_AWAITING_CLOSURE: { label: "Completed — awaiting closure", tone: "waiting" },
  COMPLETED: { label: "Completed", tone: "terminal" },
  CLOSED: { label: "Closed", tone: "terminal" },
  REOPENED: { label: "Reopened", tone: "active" },
  DECLINED: { label: "Declined", tone: "exception" },
  ESCALATED: { label: "Escalated", tone: "exception" },
  TRANSFERRED: { label: "Transferred", tone: "exception" },
  CANCELLED: { label: "Cancelled", tone: "exception" },
  EXPIRED: { label: "Expired", tone: "exception" },
  ABANDONED: { label: "Abandoned", tone: "exception" },
  ENTERED_IN_ERROR: { label: "Entered in error", tone: "exception" },
};

const TONE_CLASS: Record<ReferralStatusTone, string> = {
  active: "bg-emerald-100 text-emerald-700",
  waiting: "bg-amber-100 text-amber-700",
  terminal: "bg-slate-100 text-slate-600",
  exception: "bg-rose-100 text-rose-700",
};

const UNKNOWN_CLASS = "bg-slate-100 text-slate-500";

function prettify(raw: string): string {
  return raw
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/^./, (c) => c.toUpperCase());
}

/**
 * Resolve a status string to a label, tone and className. Unknown/blank values degrade to a
 * neutral chip with a prettified label rather than throwing — never fabricate a known tone.
 */
export function referralStatusDescriptor(status: string | null | undefined): {
  label: string;
  tone: ReferralStatusTone | "unknown";
  className: string;
} {
  const key = String(status ?? "").trim().toUpperCase();
  const found = REFERRAL_STATUS_DESCRIPTORS[key];
  if (found) {
    return { label: found.label, tone: found.tone, className: TONE_CLASS[found.tone] };
  }
  return { label: key ? prettify(key) : "Unknown", tone: "unknown", className: UNKNOWN_CLASS };
}

export function ReferralStatusBadge({
  status,
  className,
}: {
  status: string | null | undefined;
  className?: string;
}) {
  const descriptor = referralStatusDescriptor(status);
  return (
    <span
      data-testid="referral-status-badge"
      data-tone={descriptor.tone}
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${descriptor.className}${
        className ? ` ${className}` : ""
      }`}
    >
      {descriptor.label}
    </span>
  );
}
