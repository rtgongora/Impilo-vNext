/**
 * Citizen fundraisers — shared types + helpers for the /my-life/fundraisers surfaces.
 *
 * Backed by the BFF composition at /internal/v1/citizen/fundraisers (Daidzai holds the
 * CASE and its verification lifecycle; mushe holds the MONEY). Lifecycle vocabulary:
 * DRAFT → PENDING_VERIFICATION → ACTIVE → FULFILLED | CANCELLED | REJECTED | REVOKED.
 * Only verified-ACTIVE cases are publicly listed and accept donations.
 */

export interface FundraiserCase {
  id?: string;
  assistanceReference?: string;
  title?: string;
  story?: string;
  category?: string;
  targetAmount?: number | string;
  raisedAmount?: number | string;
  donorCount?: number;
  currency?: string;
  verificationStatus?: string;
  shareToken?: string;
  attestationPublicToken?: string;
  subjectIdentityMode?: string;
  requesterActorId?: string;
  createdAt?: string;
  endsAt?: string;
}

export interface FundraiserDetail {
  case?: FundraiserCase;
  money?: {
    request?: {
      title?: string;
      targetAmount?: number | string;
      raisedAmount?: number | string;
      currency?: string;
      status?: string;
    };
    contributions?: unknown[];
  };
  moneyUnavailable?: boolean;
  shareUrl?: string;
  independentVerificationUrl?: string;
}

export interface FundraiserTimelineEvent {
  id?: string;
  eventType?: string;
  fromStatus?: string;
  toStatus?: string;
  note?: string;
  occurredAt?: string;
}

export interface FundraiserContribution {
  contributionId?: string;
  amount?: number | string;
  isAnonymous?: boolean;
  contributorLabel?: string;
  message?: string;
  recordedAt?: string;
}

/** Honest chip meta per verification status — never dress a DRAFT up as live. */
export const STATUS_META: Record<string, { label: string; className: string }> = {
  DRAFT: { label: "Draft — not public", className: "bg-slate-100 text-slate-700" },
  PENDING_VERIFICATION: {
    label: "Awaiting facility verification",
    className: "bg-amber-100 text-amber-800",
  },
  ACTIVE: { label: "Verified · Open for donations", className: "bg-emerald-100 text-emerald-800" },
  FULFILLED: { label: "Fulfilled", className: "bg-teal-100 text-teal-800" },
  CANCELLED: { label: "Cancelled", className: "bg-slate-100 text-slate-600" },
  REJECTED: { label: "Verification rejected", className: "bg-red-100 text-red-800" },
  REVOKED: { label: "Verification revoked", className: "bg-red-100 text-red-800" },
};

export function statusMeta(status?: string): { label: string; className: string } {
  if (status && STATUS_META[status]) return STATUS_META[status];
  return { label: status ? status.replace(/_/g, " ") : "Unknown", className: "bg-slate-100 text-slate-600" };
}

/** A fundraiser is "verified" only in the provider-attested states. */
export function isVerified(status?: string): boolean {
  return status === "ACTIVE" || status === "FULFILLED";
}

export function num(v: number | string | undefined | null): number {
  if (v === undefined || v === null) return 0;
  const n = typeof v === "number" ? v : Number(v);
  return Number.isFinite(n) ? n : 0;
}

export function progressPct(raised?: number | string, target?: number | string): number {
  const t = num(target);
  if (t <= 0) return 0;
  return Math.min(Math.round((num(raised) / t) * 100), 100);
}

export function money(amount: number | string | undefined, currency?: string): string {
  return `${currency ?? "USD"} ${num(amount).toLocaleString()}`;
}

/** Lists come back as raw JSON arrays from the BFF composition (no envelope). */
export function asArray<T>(payload: unknown): T[] {
  if (Array.isArray(payload)) return payload as T[];
  if (payload && typeof payload === "object") {
    const inner = (payload as { data?: unknown }).data;
    if (Array.isArray(inner)) return inner as T[];
  }
  return [];
}

export function unwrap<T>(payload: unknown): T | null {
  if (payload && typeof payload === "object" && "data" in payload) {
    const inner = (payload as { data?: unknown }).data;
    if (inner && typeof inner === "object") return inner as T;
  }
  return (payload as T) ?? null;
}

/** Surface upstream rejections verbatim-ish — never invent a friendlier lie. */
export function errMessage(e: unknown, fallback: string): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string; code?: string }; status?: number; message?: string };
    if (obj.error?.message) return obj.error.message;
    if (typeof obj.message === "string" && obj.message) return obj.message;
    if (obj.status) return `${fallback} (HTTP ${obj.status}).`;
  }
  return fallback;
}
