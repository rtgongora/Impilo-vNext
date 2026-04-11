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
  category: "HAEMATOLOGY" | "CHEMISTRY" | "MICROBIOLOGY" | "SEROLOGY" | "COAGULATION" | "URINALYSIS" | "ENDOCRINE" | "IMAGING" | "CARDIOLOGY" | "PATHOLOGY" | "TUMOUR_MARKERS" | "TOXICOLOGY" | "PULMONARY" | "IMMUNOLOGY";
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

// ── Additional Haematology ──────────────────────────────────────────

export const RETIC: Investigation = { code: "RETIC", name: "Reticulocyte Count", shortName: "Retics", category: "HAEMATOLOGY", type: "LAB", turnaroundHours: 4, isPanel: false, common: false, resultParameters: [{ code: "RETIC_PCT", name: "Reticulocytes", unit: "%", referenceRange: { low: 0.5, high: 2.5, unit: "%" } }, { code: "RETIC_ABS", name: "Absolute Reticulocytes", unit: "×10⁹/L", referenceRange: { low: 25, high: 100, unit: "×10⁹/L" } }] };
export const BLOOD_GROUP: Investigation = { code: "BG", name: "Blood Group & Rh", shortName: "Group", category: "HAEMATOLOGY", type: "LAB", turnaroundHours: 1, isPanel: false, common: true, resultParameters: [{ code: "ABO", name: "ABO Group", unit: "", referenceRange: { unit: "A/B/AB/O" } }, { code: "RH", name: "Rh Factor", unit: "", referenceRange: { unit: "Pos/Neg" } }] };
export const GXM: Investigation = { code: "GXM", name: "Group & Cross Match", shortName: "X-match", category: "HAEMATOLOGY", type: "LAB", turnaroundHours: 1, isPanel: false, common: true, resultParameters: [{ code: "GXM_RESULT", name: "Compatibility", unit: "", referenceRange: { unit: "Compatible" } }] };
export const COOMBS: Investigation = { code: "COOMBS", name: "Direct Coombs Test (DAT)", shortName: "DAT", category: "HAEMATOLOGY", type: "LAB", turnaroundHours: 2, isPanel: false, common: false, resultParameters: [{ code: "DAT", name: "DAT Result", unit: "", referenceRange: { unit: "Negative" } }] };
export const HB_ELEC: Investigation = { code: "HB_ELEC", name: "Haemoglobin Electrophoresis", shortName: "Hb Elec", category: "HAEMATOLOGY", type: "LAB", turnaroundHours: 24, isPanel: true, common: false, resultParameters: [{ code: "HBA", name: "HbA", unit: "%", referenceRange: { low: 95, high: 98, unit: "%" } }, { code: "HBA2", name: "HbA2", unit: "%", referenceRange: { low: 1.5, high: 3.5, unit: "%" } }, { code: "HBF", name: "HbF", unit: "%", referenceRange: { low: 0, high: 1, unit: "%" } }, { code: "HBS", name: "HbS", unit: "%", referenceRange: { low: 0, high: 0, unit: "%" } }] };
export const IRON_STUDIES: Investigation = { code: "IRON", name: "Iron Studies", shortName: "Iron", category: "HAEMATOLOGY", type: "LAB", turnaroundHours: 4, isPanel: true, common: true, resultParameters: [{ code: "FE", name: "Serum Iron", unit: "µmol/L", referenceRange: { low: 10, high: 30, unit: "µmol/L" } }, { code: "TIBC", name: "TIBC", unit: "µmol/L", referenceRange: { low: 45, high: 72, unit: "µmol/L" } }, { code: "FERRITIN", name: "Ferritin", unit: "µg/L", referenceRange: { low: 15, high: 200, unit: "µg/L" } }, { code: "TSAT", name: "Transferrin Saturation", unit: "%", referenceRange: { low: 20, high: 50, unit: "%" } }] };
export const MALARIA: Investigation = { code: "MALARIA", name: "Malaria Smear (Thick & Thin)", shortName: "Malaria", category: "HAEMATOLOGY", type: "LAB", turnaroundHours: 1, isPanel: false, common: true, resultParameters: [{ code: "MAL_RESULT", name: "Parasites", unit: "", referenceRange: { unit: "Not seen" } }, { code: "MAL_SPECIES", name: "Species", unit: "", referenceRange: { unit: "N/A" } }, { code: "MAL_DENSITY", name: "Parasite Density", unit: "/µL", referenceRange: { low: 0, high: 0, unit: "/µL" } }] };
export const MALARIA_RDT: Investigation = { code: "MRDT", name: "Malaria Rapid Diagnostic Test", shortName: "mRDT", category: "HAEMATOLOGY", type: "LAB", turnaroundHours: 0.25, isPanel: false, common: true, resultParameters: [{ code: "MRDT_RESULT", name: "Result", unit: "", referenceRange: { unit: "Negative" } }] };

// ── Additional Coagulation ─────────────────────────────────────────

export const DDIMER: Investigation = { code: "DDIMER", name: "D-Dimer", shortName: "D-dimer", category: "COAGULATION", type: "LAB", turnaroundHours: 2, isPanel: false, common: true, resultParameters: [{ code: "DDIMER", name: "D-Dimer", unit: "mg/L FEU", referenceRange: { low: 0, high: 0.5, unit: "mg/L" } }] };
export const FIBRINOGEN: Investigation = { code: "FIB", name: "Fibrinogen", shortName: "Fibrinogen", category: "COAGULATION", type: "LAB", turnaroundHours: 2, isPanel: false, common: false, resultParameters: [{ code: "FIB", name: "Fibrinogen", unit: "g/L", referenceRange: { low: 2.0, high: 4.0, unit: "g/L" } }] };

// ── Additional Chemistry ───────────────────────────────────────────

export const RENAL: Investigation = { code: "RFT", name: "Renal Function Panel", shortName: "RFTs", category: "CHEMISTRY", type: "LAB", turnaroundHours: 2, isPanel: true, common: true, resultParameters: [{ code: "CREAT", name: "Creatinine", unit: "µmol/L", referenceRange: { low: 60, high: 110, unit: "µmol/L" } }, { code: "EGFR", name: "eGFR", unit: "mL/min/1.73m²", referenceRange: { low: 90, high: 999, unit: "mL/min" } }, { code: "URIC", name: "Uric Acid", unit: "mmol/L", referenceRange: { low: 0.15, high: 0.45, unit: "mmol/L" } }, { code: "CA", name: "Calcium", unit: "mmol/L", referenceRange: { low: 2.15, high: 2.55, unit: "mmol/L" }, criticalLow: 1.6, criticalHigh: 3.2 }, { code: "PO4", name: "Phosphate", unit: "mmol/L", referenceRange: { low: 0.8, high: 1.5, unit: "mmol/L" } }, { code: "MG", name: "Magnesium", unit: "mmol/L", referenceRange: { low: 0.7, high: 1.0, unit: "mmol/L" } }] };
export const CARDIAC: Investigation = { code: "CARDIAC", name: "Cardiac Markers", shortName: "Cardiac", category: "CHEMISTRY", type: "LAB", turnaroundHours: 1, isPanel: true, common: true, resultParameters: [{ code: "TROP_I", name: "Troponin I", unit: "ng/L", referenceRange: { low: 0, high: 14, unit: "ng/L" }, criticalHigh: 100 }, { code: "CK", name: "Creatine Kinase", unit: "U/L", referenceRange: { low: 25, high: 200, unit: "U/L" } }, { code: "CKMB", name: "CK-MB", unit: "U/L", referenceRange: { low: 0, high: 25, unit: "U/L" } }, { code: "BNP", name: "NT-proBNP", unit: "pg/mL", referenceRange: { low: 0, high: 125, unit: "pg/mL" } }, { code: "LDH", name: "LDH", unit: "U/L", referenceRange: { low: 120, high: 246, unit: "U/L" } }] };
export const AMYLASE: Investigation = { code: "AMYLASE", name: "Serum Amylase", shortName: "Amylase", category: "CHEMISTRY", type: "LAB", turnaroundHours: 2, isPanel: false, common: true, resultParameters: [{ code: "AMYLASE", name: "Amylase", unit: "U/L", referenceRange: { low: 28, high: 100, unit: "U/L" } }] };
export const LIPASE: Investigation = { code: "LIPASE", name: "Serum Lipase", shortName: "Lipase", category: "CHEMISTRY", type: "LAB", turnaroundHours: 2, isPanel: false, common: false, resultParameters: [{ code: "LIPASE", name: "Lipase", unit: "U/L", referenceRange: { low: 0, high: 60, unit: "U/L" } }] };
export const BONE_PROFILE: Investigation = { code: "BONE", name: "Bone Profile", shortName: "Bone", category: "CHEMISTRY", type: "LAB", turnaroundHours: 4, isPanel: true, common: false, resultParameters: [{ code: "CA_B", name: "Calcium", unit: "mmol/L", referenceRange: { low: 2.15, high: 2.55, unit: "mmol/L" } }, { code: "PO4_B", name: "Phosphate", unit: "mmol/L", referenceRange: { low: 0.8, high: 1.5, unit: "mmol/L" } }, { code: "ALP_B", name: "ALP", unit: "U/L", referenceRange: { low: 40, high: 130, unit: "U/L" } }, { code: "VITD", name: "Vitamin D (25-OH)", unit: "nmol/L", referenceRange: { low: 50, high: 150, unit: "nmol/L" } }, { code: "PTH", name: "PTH", unit: "pmol/L", referenceRange: { low: 1.6, high: 6.9, unit: "pmol/L" } }] };
export const ABG: Investigation = { code: "ABG", name: "Arterial Blood Gas", shortName: "ABG", category: "CHEMISTRY", type: "LAB", turnaroundHours: 0.25, isPanel: true, common: true, resultParameters: [{ code: "PH", name: "pH", unit: "", referenceRange: { low: 7.35, high: 7.45, unit: "" }, criticalLow: 7.1, criticalHigh: 7.6 }, { code: "PO2", name: "pO₂", unit: "mmHg", referenceRange: { low: 80, high: 100, unit: "mmHg" }, criticalLow: 40 }, { code: "PCO2", name: "pCO₂", unit: "mmHg", referenceRange: { low: 35, high: 45, unit: "mmHg" } }, { code: "HCO3_ABG", name: "HCO₃⁻", unit: "mmol/L", referenceRange: { low: 22, high: 26, unit: "mmol/L" } }, { code: "BE", name: "Base Excess", unit: "mmol/L", referenceRange: { low: -2, high: 2, unit: "mmol/L" } }, { code: "LACTATE", name: "Lactate", unit: "mmol/L", referenceRange: { low: 0.5, high: 2.2, unit: "mmol/L" }, criticalHigh: 4.0 }] };
export const CSF: Investigation = { code: "CSF", name: "CSF Analysis", shortName: "CSF", category: "CHEMISTRY", type: "LAB", turnaroundHours: 2, isPanel: true, common: false, resultParameters: [{ code: "CSF_PROT", name: "CSF Protein", unit: "g/L", referenceRange: { low: 0.15, high: 0.45, unit: "g/L" } }, { code: "CSF_GLU", name: "CSF Glucose", unit: "mmol/L", referenceRange: { low: 2.5, high: 4.4, unit: "mmol/L" } }, { code: "CSF_WBC", name: "CSF WBC", unit: "/µL", referenceRange: { low: 0, high: 5, unit: "/µL" } }, { code: "CSF_GRAM", name: "Gram Stain", unit: "", referenceRange: { unit: "No organisms" } }] };

// ── Additional Endocrine / Hormones ────────────────────────────────

export const CORTISOL: Investigation = { code: "CORTISOL", name: "Serum Cortisol (Morning)", shortName: "Cortisol", category: "ENDOCRINE", type: "LAB", turnaroundHours: 4, isPanel: false, common: false, resultParameters: [{ code: "CORT_AM", name: "Morning Cortisol", unit: "nmol/L", referenceRange: { low: 185, high: 624, unit: "nmol/L" } }] };
export const TESTOSTERONE: Investigation = { code: "TESTO", name: "Testosterone", shortName: "Testo", category: "ENDOCRINE", type: "LAB", turnaroundHours: 4, isPanel: false, common: false, resultParameters: [{ code: "TESTO", name: "Total Testosterone", unit: "nmol/L", referenceRange: { low: 8.6, high: 29, unit: "nmol/L", context: "Male adult" } }] };
export const FERTILITY: Investigation = { code: "FERT", name: "Fertility Panel (Female)", shortName: "Fertility", category: "ENDOCRINE", type: "LAB", turnaroundHours: 4, isPanel: true, common: false, resultParameters: [{ code: "FSH", name: "FSH", unit: "IU/L", referenceRange: { low: 1.5, high: 12.4, unit: "IU/L", context: "Follicular" } }, { code: "LH", name: "LH", unit: "IU/L", referenceRange: { low: 1.7, high: 8.6, unit: "IU/L", context: "Follicular" } }, { code: "E2", name: "Oestradiol", unit: "pmol/L", referenceRange: { low: 46, high: 607, unit: "pmol/L", context: "Follicular" } }, { code: "PROG", name: "Progesterone", unit: "nmol/L", referenceRange: { low: 0.3, high: 2.5, unit: "nmol/L", context: "Follicular" } }, { code: "PRL", name: "Prolactin", unit: "mIU/L", referenceRange: { low: 102, high: 496, unit: "mIU/L" } }] };
export const BHCG: Investigation = { code: "BHCG", name: "Beta-HCG (Pregnancy)", shortName: "β-HCG", category: "ENDOCRINE", type: "LAB", turnaroundHours: 2, isPanel: false, common: true, resultParameters: [{ code: "BHCG", name: "Beta-HCG", unit: "IU/L", referenceRange: { low: 0, high: 5, unit: "IU/L", context: "Non-pregnant" } }] };
export const INSULIN: Investigation = { code: "INSULIN", name: "Fasting Insulin & C-Peptide", shortName: "Insulin", category: "ENDOCRINE", type: "LAB", turnaroundHours: 4, isPanel: true, common: false, resultParameters: [{ code: "INS", name: "Fasting Insulin", unit: "mIU/L", referenceRange: { low: 2.6, high: 24.9, unit: "mIU/L" } }, { code: "CPEP", name: "C-Peptide", unit: "nmol/L", referenceRange: { low: 0.37, high: 1.47, unit: "nmol/L" } }] };

// ── Tumour Markers ─────────────────────────────────────────────────

export const PSA: Investigation = { code: "PSA", name: "Prostate Specific Antigen", shortName: "PSA", category: "TUMOUR_MARKERS", type: "LAB", turnaroundHours: 4, isPanel: false, common: true, resultParameters: [{ code: "PSA", name: "Total PSA", unit: "µg/L", referenceRange: { low: 0, high: 4.0, unit: "µg/L" } }] };
export const CEA: Investigation = { code: "CEA", name: "Carcinoembryonic Antigen", shortName: "CEA", category: "TUMOUR_MARKERS", type: "LAB", turnaroundHours: 4, isPanel: false, common: false, resultParameters: [{ code: "CEA", name: "CEA", unit: "µg/L", referenceRange: { low: 0, high: 5.0, unit: "µg/L" } }] };
export const CA125: Investigation = { code: "CA125", name: "CA-125", shortName: "CA-125", category: "TUMOUR_MARKERS", type: "LAB", turnaroundHours: 4, isPanel: false, common: false, resultParameters: [{ code: "CA125", name: "CA-125", unit: "U/mL", referenceRange: { low: 0, high: 35, unit: "U/mL" } }] };
export const AFP: Investigation = { code: "AFP", name: "Alpha-Fetoprotein", shortName: "AFP", category: "TUMOUR_MARKERS", type: "LAB", turnaroundHours: 4, isPanel: false, common: false, resultParameters: [{ code: "AFP", name: "AFP", unit: "IU/mL", referenceRange: { low: 0, high: 10, unit: "IU/mL" } }] };

// ── Immunology ─────────────────────────────────────────────────────

export const ANA: Investigation = { code: "ANA", name: "Antinuclear Antibody", shortName: "ANA", category: "IMMUNOLOGY", type: "LAB", turnaroundHours: 24, isPanel: false, common: false, resultParameters: [{ code: "ANA_RESULT", name: "ANA", unit: "", referenceRange: { unit: "Negative" } }, { code: "ANA_TITRE", name: "Titre", unit: "", referenceRange: { unit: "<1:80" } }] };
export const RF: Investigation = { code: "RF", name: "Rheumatoid Factor", shortName: "RF", category: "IMMUNOLOGY", type: "LAB", turnaroundHours: 4, isPanel: false, common: false, resultParameters: [{ code: "RF", name: "RF", unit: "IU/mL", referenceRange: { low: 0, high: 14, unit: "IU/mL" } }] };
export const COMPLEMENT: Investigation = { code: "COMP", name: "Complement (C3, C4)", shortName: "C3/C4", category: "IMMUNOLOGY", type: "LAB", turnaroundHours: 4, isPanel: true, common: false, resultParameters: [{ code: "C3", name: "C3", unit: "g/L", referenceRange: { low: 0.9, high: 1.8, unit: "g/L" } }, { code: "C4", name: "C4", unit: "g/L", referenceRange: { low: 0.1, high: 0.4, unit: "g/L" } }] };
export const CD4: Investigation = { code: "CD4", name: "CD4 Count", shortName: "CD4", category: "IMMUNOLOGY", type: "LAB", turnaroundHours: 4, isPanel: false, common: true, resultParameters: [{ code: "CD4_ABS", name: "CD4 Absolute", unit: "cells/µL", referenceRange: { low: 500, high: 1500, unit: "cells/µL" } }, { code: "CD4_PCT", name: "CD4 %", unit: "%", referenceRange: { low: 30, high: 60, unit: "%" } }] };
export const HIV_VL: Investigation = { code: "HIV_VL", name: "HIV Viral Load", shortName: "VL", category: "IMMUNOLOGY", type: "LAB", turnaroundHours: 48, isPanel: false, common: true, resultParameters: [{ code: "VL", name: "Viral Load", unit: "copies/mL", referenceRange: { low: 0, high: 0, unit: "Undetectable (<20)" } }] };

// ── Serology (additional) ──────────────────────────────────────────

export const HEPC: Investigation = { code: "HEPC", name: "Hepatitis C Antibody", shortName: "Anti-HCV", category: "SEROLOGY", type: "LAB", turnaroundHours: 4, isPanel: false, common: true, resultParameters: [{ code: "HCV_AB", name: "HCV Antibody", unit: "", referenceRange: { unit: "Non-reactive" } }] };
export const SYPHILIS: Investigation = { code: "SYPH", name: "Syphilis (RPR/VDRL)", shortName: "RPR", category: "SEROLOGY", type: "LAB", turnaroundHours: 2, isPanel: false, common: true, resultParameters: [{ code: "RPR", name: "RPR", unit: "", referenceRange: { unit: "Non-reactive" } }] };
export const COVID: Investigation = { code: "COVID", name: "SARS-CoV-2 PCR", shortName: "COVID PCR", category: "SEROLOGY", type: "LAB", turnaroundHours: 6, isPanel: false, common: true, resultParameters: [{ code: "COVID_PCR", name: "SARS-CoV-2", unit: "", referenceRange: { unit: "Not detected" } }] };

// ── Microbiology ───────────────────────────────────────────────────

export const BLOOD_CULTURE: Investigation = { code: "BC", name: "Blood Culture (Aerobic & Anaerobic)", shortName: "Blood Cx", category: "MICROBIOLOGY", type: "LAB", turnaroundHours: 72, isPanel: false, common: true, resultParameters: [{ code: "BC_RESULT", name: "Growth", unit: "", referenceRange: { unit: "No growth" } }, { code: "BC_ORG", name: "Organism", unit: "", referenceRange: { unit: "N/A" } }, { code: "BC_SENS", name: "Sensitivities", unit: "", referenceRange: { unit: "N/A" } }] };
export const URINE_MCS: Investigation = { code: "UMCS", name: "Urine MCS", shortName: "Urine Cx", category: "MICROBIOLOGY", type: "LAB", turnaroundHours: 48, isPanel: false, common: true, resultParameters: [{ code: "UMCS_RESULT", name: "Growth", unit: "", referenceRange: { unit: "No significant growth" } }, { code: "UMCS_ORG", name: "Organism", unit: "", referenceRange: { unit: "N/A" } }, { code: "UMCS_COLONY", name: "Colony Count", unit: "CFU/mL", referenceRange: { low: 0, high: 10000, unit: "CFU/mL" } }] };
export const SPUTUM_MCS: Investigation = { code: "SMCS", name: "Sputum MCS", shortName: "Sputum Cx", category: "MICROBIOLOGY", type: "LAB", turnaroundHours: 72, isPanel: false, common: true, resultParameters: [{ code: "SMCS_RESULT", name: "Growth", unit: "", referenceRange: { unit: "Normal flora" } }, { code: "SMCS_ORG", name: "Organism", unit: "", referenceRange: { unit: "N/A" } }] };
export const WOUND_MCS: Investigation = { code: "WMCS", name: "Wound Swab MCS", shortName: "Wound Cx", category: "MICROBIOLOGY", type: "LAB", turnaroundHours: 48, isPanel: false, common: true, resultParameters: [{ code: "WMCS_RESULT", name: "Growth", unit: "", referenceRange: { unit: "No growth" } }, { code: "WMCS_ORG", name: "Organism", unit: "", referenceRange: { unit: "N/A" } }] };
export const GENEXPERT: Investigation = { code: "XPERT", name: "GeneXpert MTB/RIF", shortName: "Xpert", category: "MICROBIOLOGY", type: "LAB", turnaroundHours: 2, isPanel: false, common: true, resultParameters: [{ code: "MTB", name: "MTB Detected", unit: "", referenceRange: { unit: "Not detected" } }, { code: "RIF", name: "Rifampicin Resistance", unit: "", referenceRange: { unit: "Not detected" } }] };
export const AFB: Investigation = { code: "AFB", name: "AFB Smear (ZN Stain)", shortName: "AFB", category: "MICROBIOLOGY", type: "LAB", turnaroundHours: 4, isPanel: false, common: true, resultParameters: [{ code: "AFB_RESULT", name: "AFB", unit: "", referenceRange: { unit: "Negative" } }] };
export const STOOL_MCS: Investigation = { code: "STOOL", name: "Stool MCS", shortName: "Stool Cx", category: "MICROBIOLOGY", type: "LAB", turnaroundHours: 72, isPanel: false, common: false, resultParameters: [{ code: "STOOL_RESULT", name: "Growth", unit: "", referenceRange: { unit: "Normal flora" } }, { code: "STOOL_PARA", name: "Ova & Parasites", unit: "", referenceRange: { unit: "Not seen" } }] };

// ── Toxicology / Therapeutic Drug Monitoring ───────────────────────

export const LITHIUM: Investigation = { code: "LI", name: "Lithium Level", shortName: "Lithium", category: "TOXICOLOGY", type: "LAB", turnaroundHours: 2, isPanel: false, common: false, resultParameters: [{ code: "LI", name: "Lithium", unit: "mmol/L", referenceRange: { low: 0.4, high: 1.0, unit: "mmol/L" }, criticalHigh: 1.5 }] };
export const VALPROATE: Investigation = { code: "VPA", name: "Valproate Level", shortName: "Valproate", category: "TOXICOLOGY", type: "LAB", turnaroundHours: 4, isPanel: false, common: false, resultParameters: [{ code: "VPA", name: "Valproic Acid", unit: "mg/L", referenceRange: { low: 50, high: 100, unit: "mg/L" }, criticalHigh: 150 }] };
export const DIGOXIN: Investigation = { code: "DIG", name: "Digoxin Level", shortName: "Digoxin", category: "TOXICOLOGY", type: "LAB", turnaroundHours: 2, isPanel: false, common: false, resultParameters: [{ code: "DIG", name: "Digoxin", unit: "nmol/L", referenceRange: { low: 1.0, high: 2.6, unit: "nmol/L" }, criticalHigh: 3.8 }] };
export const PHENYTOIN: Investigation = { code: "PHT", name: "Phenytoin Level", shortName: "Phenytoin", category: "TOXICOLOGY", type: "LAB", turnaroundHours: 4, isPanel: false, common: false, resultParameters: [{ code: "PHT", name: "Phenytoin", unit: "µmol/L", referenceRange: { low: 40, high: 80, unit: "µmol/L" }, criticalHigh: 100 }] };
export const VANCOMYCIN: Investigation = { code: "VANCO", name: "Vancomycin Level", shortName: "Vanco", category: "TOXICOLOGY", type: "LAB", turnaroundHours: 2, isPanel: false, common: false, resultParameters: [{ code: "VANCO_TROUGH", name: "Trough Level", unit: "mg/L", referenceRange: { low: 10, high: 20, unit: "mg/L" }, criticalHigh: 40 }] };

// ── Urinalysis (additional) ────────────────────────────────────────

export const URINE_MICROALB: Investigation = { code: "UALB", name: "Urine Microalbumin", shortName: "uAlb", category: "URINALYSIS", type: "LAB", turnaroundHours: 4, isPanel: false, common: true, resultParameters: [{ code: "UALB", name: "Microalbumin", unit: "mg/L", referenceRange: { low: 0, high: 20, unit: "mg/L" } }, { code: "ACR", name: "Albumin:Creatinine Ratio", unit: "mg/mmol", referenceRange: { low: 0, high: 3.5, unit: "mg/mmol" } }] };

// ── Additional Imaging ─────────────────────────────────────────────

export const CT_CHEST: Investigation = { code: "CT_CHEST", name: "CT Chest", shortName: "CT Chest", category: "IMAGING", type: "IMAGING", turnaroundHours: 4, isPanel: false, common: false, resultParameters: [{ code: "CT_CHEST_RPT", name: "Report", unit: "", referenceRange: { unit: "Narrative" } }] };
export const CT_ABDO: Investigation = { code: "CT_ABDO", name: "CT Abdomen/Pelvis", shortName: "CT A/P", category: "IMAGING", type: "IMAGING", turnaroundHours: 4, isPanel: false, common: false, resultParameters: [{ code: "CT_ABDO_RPT", name: "Report", unit: "", referenceRange: { unit: "Narrative" } }] };
export const MRI_BRAIN: Investigation = { code: "MRI_BRAIN", name: "MRI Brain", shortName: "MRI Brain", category: "IMAGING", type: "IMAGING", turnaroundHours: 8, isPanel: false, common: false, resultParameters: [{ code: "MRI_BRAIN_RPT", name: "Report", unit: "", referenceRange: { unit: "Narrative" } }] };
export const US_PELVIS: Investigation = { code: "US_PELV", name: "Pelvic Ultrasound", shortName: "Pelvic US", category: "IMAGING", type: "IMAGING", turnaroundHours: 4, isPanel: false, common: true, resultParameters: [{ code: "US_PELV_RPT", name: "Report", unit: "", referenceRange: { unit: "Narrative" } }] };
export const US_RENAL: Investigation = { code: "US_RENAL", name: "Renal Ultrasound", shortName: "Renal US", category: "IMAGING", type: "IMAGING", turnaroundHours: 4, isPanel: false, common: false, resultParameters: [{ code: "US_RENAL_RPT", name: "Report", unit: "", referenceRange: { unit: "Narrative" } }] };
export const DVT_DOPPLER: Investigation = { code: "DVT_DOP", name: "DVT Doppler Ultrasound", shortName: "DVT Dopp", category: "IMAGING", type: "IMAGING", turnaroundHours: 4, isPanel: false, common: true, resultParameters: [{ code: "DVT_RESULT", name: "Report", unit: "", referenceRange: { unit: "No DVT" } }] };
export const MAMMOGRAM: Investigation = { code: "MAMMO", name: "Mammogram", shortName: "Mammo", category: "IMAGING", type: "IMAGING", turnaroundHours: 24, isPanel: false, common: true, resultParameters: [{ code: "BIRADS", name: "BI-RADS", unit: "", referenceRange: { unit: "1 — Negative" } }] };
export const XRAY_EXT: Investigation = { code: "XRAY", name: "X-Ray (Extremity)", shortName: "X-ray", category: "IMAGING", type: "IMAGING", turnaroundHours: 1, isPanel: false, common: true, resultParameters: [{ code: "XRAY_RPT", name: "Report", unit: "", referenceRange: { unit: "Narrative" } }] };
export const ECHO: Investigation = { code: "ECHO", name: "Echocardiogram (TTE)", shortName: "Echo", category: "CARDIOLOGY", type: "PROCEDURE", turnaroundHours: 4, isPanel: true, common: true, resultParameters: [{ code: "LVEF", name: "LVEF", unit: "%", referenceRange: { low: 55, high: 70, unit: "%" } }, { code: "LVIDD", name: "LVIDd", unit: "mm", referenceRange: { low: 35, high: 56, unit: "mm" } }, { code: "ECHO_RPT", name: "Report", unit: "", referenceRange: { unit: "Narrative" } }] };

// ── Pulmonary ──────────────────────────────────────────────────────

export const SPIROMETRY: Investigation = { code: "PFT", name: "Spirometry / PFTs", shortName: "PFTs", category: "PULMONARY", type: "PROCEDURE", turnaroundHours: 1, isPanel: true, common: true, resultParameters: [{ code: "FEV1", name: "FEV₁", unit: "L", referenceRange: { low: 2.5, high: 5.0, unit: "L" } }, { code: "FVC", name: "FVC", unit: "L", referenceRange: { low: 3.0, high: 6.0, unit: "L" } }, { code: "FEV1_FVC", name: "FEV₁/FVC", unit: "%", referenceRange: { low: 70, high: 100, unit: "%" } }, { code: "PEFR", name: "PEFR", unit: "L/min", referenceRange: { low: 300, high: 700, unit: "L/min" } }] };

// ── Pathology / Histology ──────────────────────────────────────────

export const BIOPSY: Investigation = { code: "BIOPSY", name: "Tissue Biopsy (Histology)", shortName: "Biopsy", category: "PATHOLOGY", type: "PROCEDURE", turnaroundHours: 120, isPanel: false, common: false, resultParameters: [{ code: "HISTO_RPT", name: "Histology Report", unit: "", referenceRange: { unit: "Narrative" } }] };
export const FNAC: Investigation = { code: "FNAC", name: "Fine Needle Aspiration Cytology", shortName: "FNAC", category: "PATHOLOGY", type: "PROCEDURE", turnaroundHours: 48, isPanel: false, common: false, resultParameters: [{ code: "CYTO_RPT", name: "Cytology Report", unit: "", referenceRange: { unit: "Narrative" } }] };
export const PAP_SMEAR: Investigation = { code: "PAP", name: "Pap Smear (Cervical Cytology)", shortName: "Pap", category: "PATHOLOGY", type: "PROCEDURE", turnaroundHours: 168, isPanel: false, common: true, resultParameters: [{ code: "PAP_RESULT", name: "Result", unit: "", referenceRange: { unit: "NILM" } }] };

// ── Master catalog ──────────────────────────────────────────────────

export const INVESTIGATION_CATALOG: Investigation[] = [
  // Haematology
  FBC, ESR, RETIC, BLOOD_GROUP, GXM, COOMBS, HB_ELEC, IRON_STUDIES, MALARIA, MALARIA_RDT,
  // Coagulation
  COAG, DDIMER, FIBRINOGEN,
  // Chemistry
  UE, LFT, GLUCOSE, HBA1C, LIPOGRAM, CRP, RENAL, CARDIAC, AMYLASE, LIPASE, BONE_PROFILE, ABG, CSF,
  // Endocrine
  TFT, CORTISOL, TESTOSTERONE, FERTILITY, BHCG, INSULIN,
  // Tumour Markers
  PSA, CEA, CA125, AFP,
  // Immunology
  ANA, RF, COMPLEMENT, CD4, HIV_VL,
  // Serology
  HIV_TEST, HEPB, HEPC, SYPHILIS, COVID,
  // Microbiology
  BLOOD_CULTURE, URINE_MCS, SPUTUM_MCS, WOUND_MCS, GENEXPERT, AFB, STOOL_MCS,
  // Toxicology
  LITHIUM, VALPROATE, DIGOXIN, PHENYTOIN, VANCOMYCIN,
  // Urinalysis
  URINALYSIS, URINE_MICROALB,
  // Imaging
  CXR, ABDO_US, CT_HEAD, CT_CHEST, CT_ABDO, MRI_BRAIN, US_PELVIS, US_RENAL, DVT_DOPPLER, MAMMOGRAM, XRAY_EXT,
  // Cardiology
  ECG, ECHO,
  // Pulmonary
  SPIROMETRY,
  // Pathology
  BIOPSY, FNAC, PAP_SMEAR,
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
