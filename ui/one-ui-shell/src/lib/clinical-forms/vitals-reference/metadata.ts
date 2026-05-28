/**
 * Central vitals metadata — thresholds and units are defined here, not in UI components.
 * Values are clinical approximations for adult acute settings; extend with paediatric/neonatal tables.
 */

import type { AgeBand } from "../types";

export type VitalId =
  | "systolic_bp"
  | "diastolic_bp"
  | "heart_rate"
  | "respiratory_rate"
  | "temperature_c"
  | "spo2"
  | "pain_score";

export interface VitalNumericThresholds {
  plausibleMin: number;
  plausibleMax: number;
  /** Typical resting adult ward ranges — not diagnostic */
  normalMin?: number;
  normalMax?: number;
  abnormalLow?: number;
  abnormalHigh?: number;
  criticalLow?: number;
  criticalHigh?: number;
}

export interface VitalDefinition {
  id: VitalId;
  display: string;
  unit: string;
  /** LOINC where applicable */
  loinc?: string;
  /** UCUM */
  ucum?: string;
  /** Default adult acute thresholds; keyed overrides per band */
  thresholds: VitalNumericThresholds;
  bandOverrides?: Partial<Record<AgeBand, Partial<VitalNumericThresholds>>>;
}

const adultBpSys: VitalNumericThresholds = {
  plausibleMin: 50,
  plausibleMax: 280,
  normalMin: 90,
  normalMax: 140,
  abnormalLow: 90,
  abnormalHigh: 140,
  criticalLow: 85,
  criticalHigh: 180,
};

const adultBpDia: VitalNumericThresholds = {
  plausibleMin: 30,
  plausibleMax: 180,
  normalMin: 60,
  normalMax: 90,
  abnormalLow: 60,
  abnormalHigh: 90,
  criticalLow: 50,
  criticalHigh: 110,
};

const childBpSysOverride: Partial<VitalNumericThresholds> = {
  normalMin: 80,
  normalMax: 120,
  criticalLow: 70,
  criticalHigh: 160,
};

export const VITAL_DEFINITIONS: Record<VitalId, VitalDefinition> = {
  systolic_bp: {
    id: "systolic_bp",
    display: "Systolic blood pressure",
    unit: "mmHg",
    loinc: "8480-6",
    ucum: "mm[Hg]",
    thresholds: adultBpSys,
    bandOverrides: {
      CHILD: childBpSysOverride,
      ADOLESCENT: childBpSysOverride,
      INFANT: { plausibleMin: 40, plausibleMax: 140, criticalLow: 50, criticalHigh: 130 },
      NEONATAL: { plausibleMin: 30, plausibleMax: 100, criticalLow: 40, criticalHigh: 90 },
    },
  },
  diastolic_bp: {
    id: "diastolic_bp",
    display: "Diastolic blood pressure",
    unit: "mmHg",
    loinc: "8462-4",
    ucum: "mm[Hg]",
    thresholds: adultBpDia,
    bandOverrides: {
      CHILD: { normalMin: 50, normalMax: 80, criticalLow: 40, criticalHigh: 95 },
      INFANT: { plausibleMin: 20, plausibleMax: 100, criticalLow: 30, criticalHigh: 85 },
      NEONATAL: { plausibleMin: 15, plausibleMax: 80, criticalLow: 25, criticalHigh: 70 },
    },
  },
  heart_rate: {
    id: "heart_rate",
    display: "Heart rate",
    unit: "/min",
    loinc: "8867-4",
    thresholds: {
      plausibleMin: 25,
      plausibleMax: 240,
      normalMin: 60,
      normalMax: 100,
      abnormalLow: 50,
      abnormalHigh: 120,
      criticalLow: 40,
      criticalHigh: 140,
    },
    bandOverrides: {
      INFANT: { plausibleMin: 80, plausibleMax: 200, normalMin: 100, normalMax: 160, criticalLow: 70, criticalHigh: 190 },
      NEONATAL: { plausibleMin: 90, plausibleMax: 220, normalMin: 100, normalMax: 180, criticalLow: 80, criticalHigh: 200 },
      CHILD: { plausibleMin: 60, plausibleMax: 160, normalMin: 70, normalMax: 120, criticalLow: 50, criticalHigh: 150 },
    },
  },
  respiratory_rate: {
    id: "respiratory_rate",
    display: "Respiratory rate",
    unit: "/min",
    loinc: "9279-1",
    thresholds: {
      plausibleMin: 4,
      plausibleMax: 70,
      normalMin: 12,
      normalMax: 20,
      abnormalLow: 10,
      abnormalHigh: 24,
      criticalLow: 8,
      criticalHigh: 30,
    },
    bandOverrides: {
      INFANT: { normalMin: 24, normalMax: 40, criticalLow: 20, criticalHigh: 55 },
      NEONATAL: { normalMin: 30, normalMax: 60, criticalLow: 25, criticalHigh: 70 },
      CHILD: { normalMin: 16, normalMax: 28, criticalLow: 12, criticalHigh: 36 },
    },
  },
  temperature_c: {
    id: "temperature_c",
    display: "Body temperature",
    unit: "Cel",
    loinc: "8310-5",
    ucum: "Cel",
    thresholds: {
      plausibleMin: 32,
      plausibleMax: 43,
      normalMin: 36.0,
      normalMax: 37.5,
      abnormalLow: 35.5,
      abnormalHigh: 38.0,
      criticalLow: 35.0,
      criticalHigh: 39.5,
    },
  },
  spo2: {
    id: "spo2",
    display: "Oxygen saturation",
    unit: "%",
    loinc: "2708-6",
    thresholds: {
      plausibleMin: 50,
      plausibleMax: 100,
      normalMin: 95,
      normalMax: 100,
      abnormalLow: 92,
      abnormalHigh: 100,
      criticalLow: 88,
    },
  },
  pain_score: {
    id: "pain_score",
    display: "Pain score",
    unit: "{score}",
    loinc: "38208-5",
    thresholds: {
      plausibleMin: 0,
      plausibleMax: 10,
      normalMin: 0,
      normalMax: 3,
      abnormalLow: 0,
      abnormalHigh: 6,
      criticalHigh: 9,
    },
  },
};

export function thresholdsForVital(id: VitalId, band: AgeBand): VitalNumericThresholds {
  const def = VITAL_DEFINITIONS[id];
  const base = { ...def.thresholds };
  const ov = def.bandOverrides?.[band];
  return ov ? { ...base, ...ov } : base;
}
