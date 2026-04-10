/**
 * Maps URL ?tab= values to in-page public-health tabs (deep links from home, etc.).
 */

export type PublicHealthActiveTab =
  | "dashboard"
  | "surveillance"
  | "outbreaks"
  | "inspections"
  | "complaints"
  | "campaigns"
  | "field"
  | "emergency";

const TAB_QUERY_MAP: Record<string, PublicHealthActiveTab> = {
  dashboard: "dashboard",
  surveillance: "surveillance",
  outbreaks: "outbreaks",
  inspections: "inspections",
  complaints: "complaints",
  campaigns: "campaigns",
  field: "field",
  sites: "field",
  emergency: "emergency",
};

export function publicHealthTabFromSearchParam(raw: string | null): PublicHealthActiveTab {
  if (!raw) return "dashboard";
  return TAB_QUERY_MAP[raw.toLowerCase()] ?? "dashboard";
}
