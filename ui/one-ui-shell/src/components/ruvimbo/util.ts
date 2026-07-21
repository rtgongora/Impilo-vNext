/**
 * Shared helpers for the Ruvimbo (health-financing) workspaces. Extracted from the
 * original monolithic /coverage/member page so every Ruvimbo surface — citizen,
 * provider, payer, administration, performance — reads envelope-tolerant BFF data
 * the same way and renders status with one consistent colour vocabulary.
 */

export function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === "object" ? (v as Record<string, unknown>) : {};
}

export function readStr(r: Record<string, unknown>, ...keys: string[]): string {
  for (const k of keys) {
    const x = r[k];
    if (typeof x === "string" && x.length > 0) return x;
  }
  return "";
}

export function readNum(r: Record<string, unknown>, ...keys: string[]): number {
  for (const k of keys) {
    const x = r[k];
    if (typeof x === "number") return x;
    if (typeof x === "string" && x.trim()) {
      const n = Number(x);
      if (!Number.isNaN(n)) return n;
    }
  }
  return 0;
}

export function formatMonthYear(iso: string): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString(undefined, { month: "short", year: "numeric" });
}

export function formatDate(iso: string): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });
}

/** Utilisation percentage (0–100) or null when the limit is unknown/zero. */
export function utilizationPct(used: number, limit: number): number | null {
  if (limit <= 0 || Number.isNaN(limit) || Number.isNaN(used)) return null;
  return Math.min(100, (used / limit) * 100);
}

export function utilizationBarTone(pct: number): string {
  if (pct < 50) return "bg-green-500";
  if (pct <= 80) return "bg-amber-500";
  return "bg-red-500";
}

/** Canonical status → badge class map, shared across Ruvimbo surfaces. */
export const STATUS_TONE: Record<string, string> = {
  // generic lifecycle
  ACTIVE: "bg-green-100 text-green-800",
  PENDING: "bg-amber-100 text-warning-foreground",
  UNDER_REVIEW: "bg-blue-100 text-blue-800",
  APPROVED: "bg-green-100 text-green-800",
  PARTIALLY_APPROVED: "bg-amber-100 text-warning-foreground",
  DENIED: "bg-red-100 text-red-800",
  REJECTED: "bg-red-100 text-red-800",
  EXPIRED: "bg-neutral-100 text-foreground",
  DRAFT: "bg-neutral-100 text-foreground",
  SUBMITTED: "bg-blue-100 text-blue-800",
  ADJUDICATED: "bg-amber-100 text-warning-foreground",
  PAID: "bg-green-100 text-green-800",
  DUE: "bg-amber-100 text-warning-foreground",
  OVERDUE: "bg-red-100 text-red-800",
  WAIVED: "bg-neutral-100 text-foreground",
  // verification axis
  VERIFIED: "bg-green-100 text-green-800",
  UNVERIFIED: "bg-amber-100 text-warning-foreground",
};

export function statusTone(status: string): string {
  return STATUS_TONE[status?.toUpperCase?.() ?? ""] ?? "bg-neutral-100 text-foreground";
}
