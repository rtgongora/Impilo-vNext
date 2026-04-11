/**
 * Investigation Catalog — standard laboratory and imaging investigations
 * with panels, result templates, and reference ranges.
 *
 * Each investigation has:
 * - Standard result parameters with reference ranges
 * - Panel groupings (e.g. FBC = 7 parameters)
 * - Customisable reference ranges by lab/setting
 * - Category for filtering (Haematology, Chemistry, Microbiology, Imaging, etc.)
 */

export interface ReferenceRange {
  low?: number;
  high?: number;
  unit: string;
  /** Context-specific ranges (paediatric, pregnancy, etc.) */
  context?: string;
}

export interface ResultParameter {
  code: string;
  name: string;
  unit: string;
  referenceRange: ReferenceRange;
  criticalLow?: number;
  criticalHigh?: number;
}

export interface Investigation {
  code: string;
  name: string;
  shortName: string;
  category: "HAEMATOLOGY" | "CHEMISTRY" | "MICROBIOLOGY" | "SEROLOGY" | "COAGULATION" | "URINALYSIS" | "ENDOCRINE" | "IMAGING" | "CARDIOLOGY" | "PATHOLOGY";
  type: "LAB" | "IMAGING" | "PROCEDURE";
  turnaroundHours: number;
  resultParameters: ResultParameter[];
  /** True if this is a panel (group of tests ordered together). */
  isPanel: boolean;
  /** Common/frequently ordered flag for quick-pick lists. */
  common: boolean;
}

// ── Haematology ─────────────────────────────────────────────────────

export const FBC: Investigation = {
  code: "FBC", name: "Full Blood Count", shortName: "FBC",
  category: "HAEMATOLOGY", type: "LAB", turnaroundHours: 2, isPanel: true, common: true,
  resultParameters: [
    { code: "WBC", name: "White Blood Cells", unit: "×10⁹/L", referenceRange: { low: 4.0, high: 11.0, unit: "×10⁹/L" }, criticalLow: 2.0, criticalHigh: 30.0 },
    { code: "RBC", name: "Red Blood Cells", unit: "×10¹²/L", referenceRange: { low: 4.5, high: 5.5, unit: "×10¹²/L" } },
    { code: "HB", name: "Haemoglobin", unit: "g/dL", referenceRange: { low: 12.0, high: 17.0, unit: "g/dL" }, criticalLow: 7.0, criticalHigh: 20.0 },
    { code: "HCT", name: "Haematocrit", unit: "%", referenceRange: { low: 36, high: 48, unit: "%" } },
    { code: "MCV", name: "Mean Corpuscular Volume", unit: "fL", referenceRange: { low: 80, high: 100, unit: "fL" } },
    { code: "PLT", name: "Platelets", unit: "×10⁹/L", referenceRange: { low: 150, high: 400, unit: "×10⁹/L" }, criticalLow: 50, criticalHigh: 1000 },
    { code: "NEUT", name: "Neutrophils", unit: "×10⁹/L", referenceRange: { low: 2.0, high: 7.5, unit: "×10⁹/L" } },
  ],
};

export const ESR: Investigation = {
  code: "ESR", name: "Erythrocyte Sedimentation Rate", shortName: "ESR",
  category: "HAEMATOLOGY", type: "LAB", turnaroundHours: 1, isPanel: false, common: true,
  resultParameters: [
    { code: "ESR", name: "ESR", unit: "mm/hr", referenceRange: { low: 0, high: 20, unit: "mm/hr" } },
  ],
};

// ── Chemistry ───────────────────────────────────────────────────────

export const UE: Investigation = {
  code: "UE", name: "Urea & Electrolytes", shortName: "U&E",
  category: "CHEMISTRY", type: "LAB", turnaroundHours: 2, isPanel: true, common: true,
  resultParameters: [
    { code: "NA", name: "Sodium", unit: "mmol/L", referenceRange: { low: 135, high: 145, unit: "mmol/L" }, criticalLow: 120, criticalHigh: 160 },
    { code: "K", name: "Potassium", unit: "mmol/L", referenceRange: { low: 3.5, high: 5.0, unit: "mmol/L" }, criticalLow: 2.5, criticalHigh: 6.5 },
    { code: "CL", name: "Chloride", unit: "mmol/L", referenceRange: { low: 98, high: 106, unit: "mmol/L" } },
    { code: "CO2", name: "Bicarbonate", unit: "mmol/L", referenceRange: { low: 22, high: 29, unit: "mmol/L" } },
    { code: "UREA", name: "Urea", unit: "mmol/L", referenceRange: { low: 2.5, high: 7.1, unit: "mmol/L" } },
    { code: "CREAT", name: "Creatinine", unit: "µmol/L", referenceRange: { low: 60, high: 110, unit: "µmol/L" } },
  ],
};

export const LFT: Investigation = {
  code: "LFT", name: "Liver Function Tests", shortName: "LFTs",
  category: "CHEMISTRY", type: "LAB", turnaroundHours: 2, isPanel: true, common: true,
  resultParameters: [
    { code: "TBIL", name: "Total Bilirubin", unit: "µmol/L", referenceRange: { low: 0, high: 21, unit: "µmol/L" } },
    { code: "ALT", name: "ALT", unit: "U/L", referenceRange: { low: 0, high: 41, unit: "U/L" } },
    { code: "AST", name: "AST", unit: "U/L", referenceRange: { low: 0, high: 40, unit: "U/L" } },
    { code: "ALP", name: "Alkaline Phosphatase", unit: "U/L", referenceRange: { low: 40, high: 130, unit: "U/L" } },
    { code: "GGT", name: "GGT", unit: "U/L", referenceRange: { low: 0, high: 60, unit: "U/L" } },
    { code: "ALB", name: "Albumin", unit: "g/L", referenceRange: { low: 35, high: 50, unit: "g/L" } },
    { code: "TP", name: "Total Protein", unit: "g/L", referenceRange: { low: 60, high: 80, unit: "g/L" } },
  ],
};

export const GLUCOSE: Investigation = {
  code: "GLU", name: "Blood Glucose", shortName: "Glucose",
  category: "CHEMISTRY", type: "LAB", turnaroundHours: 1, isPanel: false, common: true,
  resultParameters: [
    { code: "GLU_F", name: "Fasting Glucose", unit: "mmol/L", referenceRange: { low: 3.9, high: 5.6, unit: "mmol/L" }, criticalLow: 2.2, criticalHigh: 25.0 },
    { code: "GLU_R", name: "Random Glucose", unit: "mmol/L", referenceRange: { low: 3.9, high: 7.8, unit: "mmol/L" } },
  ],
};

export const HBA1C: Investigation = {
  code: "HBA1C", name: "Glycated Haemoglobin", shortName: "HbA1c",
  category: "CHEMISTRY", type: "LAB", turnaroundHours: 4, isPanel: false, common: true,
  resultParameters: [
    { code: "HBA1C", name: "HbA1c", unit: "%", referenceRange: { low: 4.0, high: 5.6, unit: "%" } },
  ],
};

export const LIPOGRAM: Investigation = {
  code: "LIPOGRAM", name: "Lipid Panel", shortName: "Lipogram",
  category: "CHEMISTRY", type: "LAB", turnaroundHours: 2, isPanel: true, common: true,
  resultParameters: [
    { code: "CHOL", name: "Total Cholesterol", unit: "mmol/L", referenceRange: { low: 0, high: 5.2, unit: "mmol/L" } },
    { code: "HDL", name: "HDL Cholesterol", unit: "mmol/L", referenceRange: { low: 1.0, high: 999, unit: "mmol/L" } },
    { code: "LDL", name: "LDL Cholesterol", unit: "mmol/L", referenceRange: { low: 0, high: 3.4, unit: "mmol/L" } },
    { code: "TRIG", name: "Triglycerides", unit: "mmol/L", referenceRange: { low: 0, high: 1.7, unit: "mmol/L" } },
  ],
};

export const CRP: Investigation = {
  code: "CRP", name: "C-Reactive Protein", shortName: "CRP",
  category: "CHEMISTRY", type: "LAB", turnaroundHours: 2, isPanel: false, common: true,
  resultParameters: [
    { code: "CRP", name: "CRP", unit: "mg/L", referenceRange: { low: 0, high: 5, unit: "mg/L" } },
  ],
};

// ── Coagulation ─────────────────────────────────────────────────────

export const COAG: Investigation = {
  code: "COAG", name: "Coagulation Screen", shortName: "Coags",
  category: "COAGULATION", type: "LAB", turnaroundHours: 2, isPanel: true, common: true,
  resultParameters: [
    { code: "PT", name: "Prothrombin Time", unit: "s", referenceRange: { low: 11, high: 13.5, unit: "s" } },
    { code: "INR", name: "INR", unit: "", referenceRange: { low: 0.8, high: 1.2, unit: "" } },
    { code: "APTT", name: "Activated PTT", unit: "s", referenceRange: { low: 25, high: 35, unit: "s" } },
  ],
};

// ── Endocrine ───────────────────────────────────────────────────────

export const TFT: Investigation = {
  code: "TFT", name: "Thyroid Function Tests", shortName: "TFTs",
  category: "ENDOCRINE", type: "LAB", turnaroundHours: 4, isPanel: true, common: true,
  resultParameters: [
    { code: "TSH", name: "TSH", unit: "mIU/L", referenceRange: { low: 0.4, high: 4.0, unit: "mIU/L" } },
    { code: "FT4", name: "Free T4", unit: "pmol/L", referenceRange: { low: 12, high: 22, unit: "pmol/L" } },
    { code: "FT3", name: "Free T3", unit: "pmol/L", referenceRange: { low: 3.1, high: 6.8, unit: "pmol/L" } },
  ],
};

// ── Urinalysis ──────────────────────────────────────────────────────

export const URINALYSIS: Investigation = {
  code: "UA", name: "Urinalysis", shortName: "Urinalysis",
  category: "URINALYSIS", type: "LAB", turnaroundHours: 1, isPanel: true, common: true,
  resultParameters: [
    { code: "UA_PH", name: "pH", unit: "", referenceRange: { low: 4.5, high: 8.0, unit: "" } },
    { code: "UA_SG", name: "Specific Gravity", unit: "", referenceRange: { low: 1.005, high: 1.030, unit: "" } },
    { code: "UA_PROT", name: "Protein", unit: "", referenceRange: { low: 0, high: 0, unit: "Negative" } },
    { code: "UA_GLU", name: "Glucose", unit: "", referenceRange: { low: 0, high: 0, unit: "Negative" } },
    { code: "UA_WBC", name: "WBC", unit: "/hpf", referenceRange: { low: 0, high: 5, unit: "/hpf" } },
    { code: "UA_RBC", name: "RBC", unit: "/hpf", referenceRange: { low: 0, high: 2, unit: "/hpf" } },
  ],
};

// ── Serology ────────────────────────────────────────────────────────

export const HIV_TEST: Investigation = {
  code: "HIV", name: "HIV Rapid Test", shortName: "HIV",
  category: "SEROLOGY", type: "LAB", turnaroundHours: 0.5, isPanel: false, common: true,
  resultParameters: [
    { code: "HIV_AB", name: "HIV Antibody", unit: "", referenceRange: { low: 0, high: 0, unit: "Non-reactive" } },
  ],
};

export const HEPB: Investigation = {
  code: "HEPB", name: "Hepatitis B Surface Antigen", shortName: "HBsAg",
  category: "SEROLOGY", type: "LAB", turnaroundHours: 2, isPanel: false, common: true,
  resultParameters: [
    { code: "HBSAG", name: "HBsAg", unit: "", referenceRange: { low: 0, high: 0, unit: "Non-reactive" } },
  ],
};

// ── Imaging ─────────────────────────────────────────────────────────

export const CXR: Investigation = {
  code: "CXR", name: "Chest X-Ray", shortName: "CXR",
  category: "IMAGING", type: "IMAGING", turnaroundHours: 1, isPanel: false, common: true,
  resultParameters: [
    { code: "CXR_REPORT", name: "Report", unit: "", referenceRange: { unit: "Narrative" } },
  ],
};

export const ABDO_US: Investigation = {
  code: "ABDO_US", name: "Abdominal Ultrasound", shortName: "Abdo US",
  category: "IMAGING", type: "IMAGING", turnaroundHours: 4, isPanel: false, common: true,
  resultParameters: [
    { code: "ABDO_US_REPORT", name: "Report", unit: "", referenceRange: { unit: "Narrative" } },
  ],
};

export const CT_HEAD: Investigation = {
  code: "CT_HEAD", name: "CT Head", shortName: "CT Head",
  category: "IMAGING", type: "IMAGING", turnaroundHours: 2, isPanel: false, common: false,
  resultParameters: [
    { code: "CT_HEAD_REPORT", name: "Report", unit: "", referenceRange: { unit: "Narrative" } },
  ],
};

// ── Cardiology ──────────────────────────────────────────────────────

export const ECG: Investigation = {
  code: "ECG", name: "12-Lead ECG", shortName: "ECG",
  category: "CARDIOLOGY", type: "PROCEDURE", turnaroundHours: 0.5, isPanel: false, common: true,
  resultParameters: [
    { code: "ECG_RATE", name: "Rate", unit: "bpm", referenceRange: { low: 60, high: 100, unit: "bpm" } },
    { code: "ECG_RHYTHM", name: "Rhythm", unit: "", referenceRange: { unit: "Sinus" } },
    { code: "ECG_REPORT", name: "Interpretation", unit: "", referenceRange: { unit: "Narrative" } },
  ],
};

// ── Master catalog ──────────────────────────────────────────────────

export const INVESTIGATION_CATALOG: Investigation[] = [
  FBC, ESR, UE, LFT, GLUCOSE, HBA1C, LIPOGRAM, CRP, COAG, TFT,
  URINALYSIS, HIV_TEST, HEPB, CXR, ABDO_US, CT_HEAD, ECG,
];

/** Get common investigations for quick-pick. */
export function getCommonInvestigations(): Investigation[] {
  return INVESTIGATION_CATALOG.filter((i) => i.common);
}

/** Get investigations by category. */
export function getInvestigationsByCategory(category: Investigation["category"]): Investigation[] {
  return INVESTIGATION_CATALOG.filter((i) => i.category === category);
}

/** Get all investigation categories. */
export function getInvestigationCategories(): Investigation["category"][] {
  return [...new Set(INVESTIGATION_CATALOG.map((i) => i.category))];
}
