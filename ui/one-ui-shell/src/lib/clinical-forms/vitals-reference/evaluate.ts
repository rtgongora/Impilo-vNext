import type { AgeBand } from "../types";
import { thresholdsForVital, type VitalId } from "./metadata";

export type VitalFlagSeverity = "info" | "warning" | "critical";

export interface VitalEvaluation {
  vitalId: VitalId;
  severity: VitalFlagSeverity;
  code: "OUT_OF_PLAUSIBLE" | "ABNORMAL" | "CRITICAL_LOW" | "CRITICAL_HIGH" | "WITHIN_NORMAL";
  message: string;
  /** True when value is outside plausible range — UI should prompt override reason before save */
  requiresOverrideReason?: boolean;
}

export function evaluateNumericVital(
  vitalId: VitalId,
  value: number | null | undefined,
  ageBand: AgeBand,
): VitalEvaluation[] {
  if (value == null || Number.isNaN(value)) return [];
  const t = thresholdsForVital(vitalId, ageBand);
  const out: VitalEvaluation[] = [];

  if (value < t.plausibleMin || value > t.plausibleMax) {
    out.push({
      vitalId,
      severity: "critical",
      code: "OUT_OF_PLAUSIBLE",
      message: `Value ${value} is outside plausible range (${t.plausibleMin}–${t.plausibleMax}). Confirm or document reason.`,
      requiresOverrideReason: true,
    });
    return out;
  }

  if (t.criticalLow != null && value < t.criticalLow) {
    out.push({
      vitalId,
      severity: "critical",
      code: "CRITICAL_LOW",
      message: `Below critical low (${t.criticalLow}).`,
    });
  }
  if (t.criticalHigh != null && value > t.criticalHigh) {
    out.push({
      vitalId,
      severity: "critical",
      code: "CRITICAL_HIGH",
      message: `Above critical high (${t.criticalHigh}).`,
    });
  }

  if (out.length) return out;

  const belowNormal = t.normalMin != null && value < t.normalMin;
  const aboveNormal = t.normalMax != null && value > t.normalMax;
  if (belowNormal || aboveNormal) {
    const edge =
      (t.abnormalLow != null && value < t.abnormalLow) || (t.abnormalHigh != null && value > t.abnormalHigh);
    out.push({
      vitalId,
      severity: edge ? "warning" : "info",
      code: "ABNORMAL",
      message: edge
        ? "Outside typical normal range — verify clinically."
        : "Slightly outside typical resting range.",
    });
    return out;
  }

  out.push({
    vitalId,
    severity: "info",
    code: "WITHIN_NORMAL",
    message: "Within documented reference band for context.",
  });
  return out;
}
