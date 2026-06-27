/**
 * Single source of truth → governed seed generator.
 *
 * Transforms the provisional DRAFT vitals reference assets (`vitals-reference/metadata.ts`) into cited
 * FHIR R4 `ObservationDefinition` resources for ZIBO. This is the ONLY place the UI seed crosses into the
 * backend governed library, so a drift-guard test (export-reference-ranges.test.ts) asserts the committed
 * seed JSON equals this generator's output — the UI assets and the backend seed can never silently diverge.
 *
 * Doctrine: these are PROVISIONAL DRAFT ranges (status DRAFT, advisory). They are superseded by cited
 * national-guideline / NEWS2 / WHO-growth ObservationDefinitions (promoted DRAFT→ACTIVE in Phase 7) and,
 * for labs, by the performing lab's delivered interval. They are never authoritative.
 */

import { VITAL_DEFINITIONS, type VitalDefinition, type VitalNumericThresholds } from "../src/lib/clinical-forms/vitals-reference/metadata";
import type { AgeBand } from "../src/lib/clinical-forms/types";

export const SEED_VERSION = "0.1.0-draft";
const CITATION =
  "Provisional DRAFT seed derived from Impilo vitals-reference assets (adult acute approximations + " +
  "paediatric/neonatal band overrides). NOT authoritative — supersede with cited national guideline / " +
  "NEWS2 / WHO growth standards (Phase 7) and prefer lab-delivered intervals for labs.";

/** AgeBand → [lowYears, highYears|null] mirroring ageBandFromMonths() (single source for boundaries). */
const BAND_AGE_YEARS: Record<AgeBand, [number, number | null]> = {
  NEONATAL: [0, 1 / 12],
  INFANT: [1 / 12, 2],
  CHILD: [2, 12],
  ADOLESCENT: [12, 18],
  ADULT: [18, 65],
  OLDER_ADULT: [65, null],
};
const BAND_ORDER: AgeBand[] = ["NEONATAL", "INFANT", "CHILD", "ADOLESCENT", "ADULT", "OLDER_ADULT"];

function round(n: number): number {
  return Math.round(n * 1e6) / 1e6;
}

function thresholdsForBand(def: VitalDefinition, band: AgeBand): VitalNumericThresholds {
  const base = { ...def.thresholds };
  const ov = def.bandOverrides?.[band];
  return ov ? { ...base, ...ov } : base;
}

interface FhirQty {
  value: number;
  unit: string;
}

function qualifiedInterval(
  category: "reference" | "critical",
  band: AgeBand,
  low: number | undefined,
  high: number | undefined,
  unit: string,
): Record<string, unknown> | null {
  if (low === undefined && high === undefined) return null;
  const [ageLow, ageHigh] = BAND_AGE_YEARS[band];
  const age: Record<string, FhirQty> = { low: { value: round(ageLow), unit: "a" } };
  if (ageHigh !== null) age.high = { value: round(ageHigh), unit: "a" };
  const range: Record<string, FhirQty> = {};
  if (low !== undefined) range.low = { value: low, unit };
  if (high !== undefined) range.high = { value: high, unit };
  return { category, age, range };
}

function observationDefinition(def: VitalDefinition): Record<string, unknown> | null {
  if (!def.loinc) return null;
  const unit = def.ucum ?? def.unit;
  const intervals: Record<string, unknown>[] = [];
  for (const band of BAND_ORDER) {
    const t = thresholdsForBand(def, band);
    const ref = qualifiedInterval("reference", band, t.normalMin, t.normalMax, unit);
    if (ref) intervals.push(ref);
    const crit = qualifiedInterval("critical", band, t.criticalLow, t.criticalHigh, unit);
    if (crit) intervals.push(crit);
  }
  if (intervals.length === 0) return null;
  return {
    resourceType: "ObservationDefinition",
    status: "draft",
    code: {
      coding: [{ system: "http://loinc.org", code: def.loinc, display: def.display }],
      text: def.display,
    },
    permittedDataType: ["Quantity"],
    quantitativeDetails: { unit: { text: unit } },
    qualifiedInterval: intervals,
  };
}

export interface ReferenceRangeSeed {
  version: string;
  generatedFrom: string;
  citation: string;
  definitions: {
    canonicalUrl: string;
    version: string;
    name: string;
    title: string;
    publisher: string;
    content: Record<string, unknown>;
  }[];
}

/** Build the full governed DRAFT seed from the single-source UI vitals assets. Deterministic. */
export function buildReferenceRangeSeed(): ReferenceRangeSeed {
  const definitions: ReferenceRangeSeed["definitions"] = [];
  for (const id of Object.keys(VITAL_DEFINITIONS) as (keyof typeof VITAL_DEFINITIONS)[]) {
    const def = VITAL_DEFINITIONS[id];
    const content = observationDefinition(def);
    if (!content) continue;
    definitions.push({
      canonicalUrl: `http://impilo.health.zw/ObservationDefinition/vital-${def.id.replace(/_/g, "-")}`,
      version: SEED_VERSION,
      name: `Vital_${def.id}`,
      title: `${def.display} reference intervals (DRAFT seed)`,
      publisher: "MoHCC Impilo (provisional DRAFT seed)",
      content,
    });
  }
  return {
    version: SEED_VERSION,
    generatedFrom: "ui/one-ui-shell/src/lib/clinical-forms/vitals-reference/metadata.ts",
    citation: CITATION,
    definitions,
  };
}
