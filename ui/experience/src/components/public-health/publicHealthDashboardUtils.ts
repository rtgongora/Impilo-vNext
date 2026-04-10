/**
 * Pure helpers for PublicHealthDashboard KPI aggregation (unit-tested).
 */

export function formatPublicHealthCompact(n: number): string {
  if (!Number.isFinite(n) || n < 0) return "0";
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${Math.round(n / 1_000)}K`;
  return String(Math.round(n));
}

export function isPublicHealthCampaignActive(status: string): boolean {
  const s = status.toLowerCase();
  return s !== "completed" && s !== "closed" && s !== "cancelled";
}

export function isPublicHealthCaseOpen(status: string): boolean {
  const s = status.toUpperCase();
  return s === "OPEN" || s === "ACTIVE" || s === "INVESTIGATING" || s === "NEW" || s === "PENDING" || s === "";
}

export function isPublicHealthSignalActive(status: string): boolean {
  const s = status.toUpperCase();
  return s !== "CLOSED" && s !== "RESOLVED" && s !== "ARCHIVED";
}
