/**
 * Pure mapping from CDS interpretation codes (produced by the clinical-knowledge-platform interpretation
 * engine) to a mobile flag presentation. Advisory display only — never overrides clinician judgement.
 * NO_REFERENCE_RANGE is rendered explicitly (never as "normal").
 */

export type InterpretationCode =
  | "NORMAL"
  | "LOW"
  | "HIGH"
  | "CRITICAL_LOW"
  | "CRITICAL_HIGH"
  | "NO_REFERENCE_RANGE"
  | "UNIT_MISMATCH"
  | "DATA_QUALITY";

export interface InterpretationFlagStyle {
  label: string;
  color: string;
  bg: string;
}

const NORMAL: InterpretationFlagStyle = { label: "Normal", color: "#005420", bg: "#E6F5EC" };
const LOW: InterpretationFlagStyle = { label: "Low", color: "#1565C0", bg: "#E3F2FD" };
const HIGH: InterpretationFlagStyle = { label: "High", color: "#E65100", bg: "#FFF3E0" };
const CRIT_LOW: InterpretationFlagStyle = { label: "Critical low", color: "#C62828", bg: "#FFEBEE" };
const CRIT_HIGH: InterpretationFlagStyle = { label: "Critical high", color: "#C62828", bg: "#FFEBEE" };
const NEUTRAL = (label: string): InterpretationFlagStyle => ({ label, color: "#616161", bg: "#F5F5F5" });

const FLAGS: Record<InterpretationCode, InterpretationFlagStyle> = {
  NORMAL,
  LOW,
  HIGH,
  CRITICAL_LOW: CRIT_LOW,
  CRITICAL_HIGH: CRIT_HIGH,
  NO_REFERENCE_RANGE: NEUTRAL("No reference range"),
  UNIT_MISMATCH: NEUTRAL("Unit mismatch"),
  DATA_QUALITY: { label: "Data quality", color: "#E65100", bg: "#FFF3E0" },
};

export function interpretationFlag(code: InterpretationCode): InterpretationFlagStyle {
  return FLAGS[code] ?? NEUTRAL(code);
}

/** True for codes that are clinically outside the reference interval (LOW/HIGH/CRITICAL_*). */
export function isAbnormalInterpretation(code: InterpretationCode): boolean {
  return code === "LOW" || code === "HIGH" || code === "CRITICAL_LOW" || code === "CRITICAL_HIGH";
}

export function isCriticalInterpretation(code: InterpretationCode): boolean {
  return code === "CRITICAL_LOW" || code === "CRITICAL_HIGH";
}

export type Trend = "RISING" | "FALLING" | "STABLE";

export function trendArrow(trend?: Trend | null): string {
  if (trend === "RISING") return "▲";
  if (trend === "FALLING") return "▼";
  if (trend === "STABLE") return "▬";
  return "";
}
