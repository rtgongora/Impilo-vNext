/**
 * Maps captured vital values to interpretation observation inputs (LOINC + UCUM from the single-source
 * vitals metadata), so the CDS interpretation engine can resolve patient-appropriate reference ranges.
 * Vitals without a numeric value or a LOINC are omitted (never sent as nulls).
 */

import { VITAL_DEFINITIONS, type VitalId } from "../clinical-forms/vitals-reference/metadata";
import type { InterpretObservationInput } from "@/hooks/queries/useGuidance";

export type VitalValues = Partial<Record<VitalId, number | null | undefined>>;

export function vitalsToObservationInputs(vitals: VitalValues): InterpretObservationInput[] {
  const out: InterpretObservationInput[] = [];
  for (const id of Object.keys(vitals) as VitalId[]) {
    const value = vitals[id];
    if (value == null || Number.isNaN(value)) continue;
    const def = VITAL_DEFINITIONS[id];
    if (!def || !def.loinc) continue;
    out.push({
      loincCode: def.loinc,
      displayName: def.display,
      category: "VITAL",
      value,
      unit: def.ucum ?? def.unit,
    });
  }
  return out;
}
