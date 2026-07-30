/**
 * Order sets — NOT a live ambulatory SoR.
 *
 * `ORDER_SETS` and `components/ehr/cart/*` (EncounterCart, OrderSetPicker) are orphaned static UI.
 * Nothing in the product mounts them. There is no OROS/PCT ambulatory order-set definition that
 * places real orders when selected. Pathway "apply" on the mobile BFF starts a CKP pathway session
 * and places no order. ED has separate instance-tracking order sets — a different surface.
 *
 * Do not wire these constants into a submit path. Keep as a design sketch until a real SoR exists.
 *
 * @see docs/clinical/adult-medicine-domain-pack/completion-register.md §11
 */

export const AMBULATORY_ORDER_SETS_BUILT = false as const;

/**
 * Design-sketch catalogue only — not served to clinicians as a live feature.
 *
 * Each sketch set may contain investigations, medications, nursing observations, diet/activity.
 */

export interface OrderSetItem {
  type: "INVESTIGATION" | "MEDICATION" | "NURSING" | "DIET" | "ACTIVITY" | "REFERRAL";
  code: string;
  name: string;
  dose?: string;
  frequency?: string;
  route?: string;
  duration?: string;
  priority: "ROUTINE" | "URGENT" | "STAT";
  notes?: string;
  /** Can be removed by user before submission. */
  removable: boolean;
}

export interface OrderSet {
  id: string;
  name: string;
  description: string;
  category: string;
  specialty?: string;
  items: OrderSetItem[];
}

export const ORDER_SETS: OrderSet[] = [
  {
    id: "admission-general",
    name: "General Admission",
    description: "Standard admission order set for medical ward",
    category: "Admission",
    items: [
      { type: "INVESTIGATION", code: "FBC", name: "Full Blood Count", priority: "ROUTINE", removable: true },
      { type: "INVESTIGATION", code: "UE", name: "Urea & Electrolytes", priority: "ROUTINE", removable: true },
      { type: "INVESTIGATION", code: "LFT", name: "Liver Function Tests", priority: "ROUTINE", removable: true },
      { type: "INVESTIGATION", code: "GLU", name: "Blood Glucose", priority: "ROUTINE", removable: true },
      { type: "INVESTIGATION", code: "CXR", name: "Chest X-Ray", priority: "ROUTINE", removable: true },
      { type: "INVESTIGATION", code: "ECG", name: "12-Lead ECG", priority: "ROUTINE", removable: true },
      { type: "INVESTIGATION", code: "UA", name: "Urinalysis", priority: "ROUTINE", removable: true },
      { type: "NURSING", code: "VS_Q4H", name: "Vitals 4-hourly", priority: "ROUTINE", removable: true },
      { type: "NURSING", code: "FLUID_CHART", name: "Fluid balance chart", priority: "ROUTINE", removable: true },
      { type: "NURSING", code: "FALLS_RISK", name: "Falls risk assessment", priority: "ROUTINE", removable: true },
      { type: "NURSING", code: "VTE_RISK", name: "VTE risk assessment", priority: "ROUTINE", removable: true },
      { type: "DIET", code: "DIET_NORMAL", name: "Normal diet", priority: "ROUTINE", removable: true },
      { type: "ACTIVITY", code: "AMB_ASST", name: "Ambulate with assistance", priority: "ROUTINE", removable: true },
    ],
  },
  {
    id: "acs-protocol",
    name: "ACS / Chest Pain Protocol",
    description: "Acute coronary syndrome workup and management",
    category: "Emergency",
    specialty: "Cardiology",
    items: [
      { type: "INVESTIGATION", code: "ECG", name: "12-Lead ECG", priority: "STAT", removable: false },
      { type: "INVESTIGATION", code: "TROP", name: "Troponin (serial)", priority: "STAT", removable: false },
      { type: "INVESTIGATION", code: "FBC", name: "Full Blood Count", priority: "URGENT", removable: true },
      { type: "INVESTIGATION", code: "UE", name: "Urea & Electrolytes", priority: "URGENT", removable: true },
      { type: "INVESTIGATION", code: "COAG", name: "Coagulation Screen", priority: "URGENT", removable: true },
      { type: "INVESTIGATION", code: "CXR", name: "Chest X-Ray", priority: "URGENT", removable: true },
      { type: "MEDICATION", code: "ASPIRIN", name: "Aspirin", dose: "300mg", route: "PO", frequency: "STAT", priority: "STAT", removable: false },
      { type: "MEDICATION", code: "CLOPI", name: "Clopidogrel", dose: "300mg", route: "PO", frequency: "STAT", priority: "STAT", removable: true },
      { type: "MEDICATION", code: "ENOX", name: "Enoxaparin", dose: "1mg/kg", route: "SC", frequency: "BD", priority: "URGENT", removable: true },
      { type: "MEDICATION", code: "GTN", name: "GTN Sublingual", dose: "0.5mg", route: "SL", frequency: "PRN", priority: "STAT", removable: true, notes: "If SBP > 90mmHg" },
      { type: "MEDICATION", code: "MORPHINE", name: "Morphine", dose: "2-5mg", route: "IV", frequency: "PRN", priority: "STAT", removable: true, notes: "For pain" },
      { type: "NURSING", code: "CARDIAC_MON", name: "Continuous cardiac monitoring", priority: "STAT", removable: false },
      { type: "NURSING", code: "VS_Q15M", name: "Vitals every 15 minutes", priority: "STAT", removable: false },
      { type: "NURSING", code: "O2_THERAPY", name: "Oxygen therapy — maintain SpO₂ > 94%", priority: "STAT", removable: true },
      { type: "DIET", code: "NBM", name: "Nil by mouth (pending cath lab)", priority: "URGENT", removable: true },
    ],
  },
  {
    id: "dka-protocol",
    name: "DKA Management Protocol",
    description: "Diabetic ketoacidosis — fluids, insulin, monitoring",
    category: "Emergency",
    specialty: "Endocrinology",
    items: [
      { type: "INVESTIGATION", code: "GLU", name: "Blood Glucose (hourly)", priority: "STAT", removable: false },
      { type: "INVESTIGATION", code: "UE", name: "Urea & Electrolytes (2-hourly)", priority: "STAT", removable: false },
      { type: "INVESTIGATION", code: "ABG", name: "Arterial Blood Gas", priority: "STAT", removable: false },
      { type: "INVESTIGATION", code: "FBC", name: "Full Blood Count", priority: "URGENT", removable: true },
      { type: "INVESTIGATION", code: "HBA1C", name: "HbA1c", priority: "ROUTINE", removable: true },
      { type: "MEDICATION", code: "NS_IV", name: "Normal Saline 0.9%", dose: "1L", route: "IV", frequency: "Over 1hr then 500ml/hr", priority: "STAT", removable: false },
      { type: "MEDICATION", code: "INSULIN_IV", name: "Actrapid Insulin Infusion", dose: "0.1 units/kg/hr", route: "IV", frequency: "Continuous", priority: "STAT", removable: false },
      { type: "MEDICATION", code: "KCL_IV", name: "Potassium Chloride", dose: "20-40mmol/L", route: "IV (in fluids)", frequency: "Per K+ level", priority: "URGENT", removable: true, notes: "If K+ < 5.5" },
      { type: "NURSING", code: "FLUID_CHART", name: "Strict fluid balance chart", priority: "STAT", removable: false },
      { type: "NURSING", code: "VS_Q1H", name: "Vitals hourly", priority: "STAT", removable: false },
      { type: "NURSING", code: "GCS_Q2H", name: "GCS every 2 hours", priority: "URGENT", removable: true },
      { type: "DIET", code: "NBM", name: "Nil by mouth until ketones clear", priority: "STAT", removable: false },
    ],
  },
  {
    id: "pneumonia-cap",
    name: "Community-Acquired Pneumonia",
    description: "CAP workup and empiric treatment (adult)",
    category: "Infectious Disease",
    specialty: "Pulmonology",
    items: [
      { type: "INVESTIGATION", code: "FBC", name: "Full Blood Count", priority: "URGENT", removable: true },
      { type: "INVESTIGATION", code: "CRP", name: "C-Reactive Protein", priority: "URGENT", removable: true },
      { type: "INVESTIGATION", code: "UE", name: "Urea & Electrolytes", priority: "URGENT", removable: true },
      { type: "INVESTIGATION", code: "CXR", name: "Chest X-Ray", priority: "URGENT", removable: false },
      { type: "INVESTIGATION", code: "SPUTUM_MCS", name: "Sputum MCS", priority: "ROUTINE", removable: true },
      { type: "INVESTIGATION", code: "BC", name: "Blood Cultures x2", priority: "URGENT", removable: true },
      { type: "INVESTIGATION", code: "HIV", name: "HIV Test (if unknown)", priority: "ROUTINE", removable: true },
      { type: "MEDICATION", code: "AMOX", name: "Amoxicillin", dose: "1g", route: "PO", frequency: "TDS", duration: "7 days", priority: "URGENT", removable: true },
      { type: "MEDICATION", code: "AZITHRO", name: "Azithromycin", dose: "500mg", route: "PO", frequency: "OD", duration: "3 days", priority: "URGENT", removable: true },
      { type: "MEDICATION", code: "PARACETAMOL", name: "Paracetamol", dose: "1g", route: "PO", frequency: "QDS PRN", priority: "ROUTINE", removable: true, notes: "For fever" },
      { type: "NURSING", code: "VS_Q4H", name: "Vitals 4-hourly", priority: "URGENT", removable: true },
      { type: "NURSING", code: "O2_THERAPY", name: "Oxygen — maintain SpO₂ > 92%", priority: "URGENT", removable: true },
      { type: "NURSING", code: "FLUID_CHART", name: "Fluid balance chart", priority: "ROUTINE", removable: true },
      { type: "DIET", code: "DIET_NORMAL", name: "Normal diet, encourage fluids", priority: "ROUTINE", removable: true },
    ],
  },
  {
    id: "preop-standard",
    name: "Pre-Operative Standard",
    description: "Standard pre-operative workup",
    category: "Surgical",
    items: [
      { type: "INVESTIGATION", code: "FBC", name: "Full Blood Count", priority: "URGENT", removable: true },
      { type: "INVESTIGATION", code: "UE", name: "Urea & Electrolytes", priority: "URGENT", removable: true },
      { type: "INVESTIGATION", code: "COAG", name: "Coagulation Screen", priority: "URGENT", removable: true },
      { type: "INVESTIGATION", code: "GXM", name: "Group & Cross Match", priority: "URGENT", removable: false },
      { type: "INVESTIGATION", code: "ECG", name: "12-Lead ECG", priority: "ROUTINE", removable: true },
      { type: "INVESTIGATION", code: "CXR", name: "Chest X-Ray", priority: "ROUTINE", removable: true },
      { type: "INVESTIGATION", code: "GLU", name: "Blood Glucose", priority: "ROUTINE", removable: true },
      { type: "NURSING", code: "VS_PRE", name: "Pre-op vitals", priority: "URGENT", removable: false },
      { type: "NURSING", code: "CONSENT", name: "Verify surgical consent", priority: "URGENT", removable: false },
      { type: "NURSING", code: "VTE_RISK", name: "VTE risk assessment", priority: "URGENT", removable: true },
      { type: "MEDICATION", code: "ENOX_PRE", name: "Enoxaparin", dose: "40mg", route: "SC", frequency: "Nocte", priority: "ROUTINE", removable: true, notes: "VTE prophylaxis" },
      { type: "DIET", code: "NBM_6H", name: "Nil by mouth 6 hours pre-op", priority: "URGENT", removable: false },
    ],
  },
];

export function getOrderSetsByCategory(category: string): OrderSet[] {
  return ORDER_SETS.filter((os) => os.category === category);
}

export function getOrderSetCategories(): string[] {
  return [...new Set(ORDER_SETS.map((os) => os.category))];
}
