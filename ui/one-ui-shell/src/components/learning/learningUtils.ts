export type Row = Record<string, unknown>;
export type SectionKey = "overview" | "catalog" | "learner" | "studio" | "reports";
export type ModalKey =
  | "course"
  | "enrolment"
  | "ai"
  | "library"
  | "media"
  | "notification"
  | "activity"
  | "cohort"
  | "session";

export function asRecord(value: unknown): Row {
  return value && typeof value === "object" && !Array.isArray(value) ? (value as Row) : {};
}

export function asArray(value: unknown): Row[] {
  return Array.isArray(value) ? value.map(asRecord) : [];
}

export function asText(value: unknown, fallback = "-") {
  return value === undefined || value === null || value === "" ? fallback : String(value);
}

export function count(value: unknown) {
  if (Array.isArray(value)) return value.length;
  if (typeof value === "number") return value;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}
