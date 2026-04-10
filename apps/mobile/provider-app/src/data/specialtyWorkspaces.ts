export type SpecialtyWorkspaceDef = {
  id: string;
  name: string;
  icon: string;
  tools: string[];
};

/** 18 Lovable-aligned specialty workspaces (108 tool labels). */
export const SPECIALTY_WORKSPACES: readonly SpecialtyWorkspaceDef[] = [
  { id: "anaesthesia", name: "Anaesthesia", icon: "Syringe", tools: ["Pre-op Assessment", "ASA Classification", "Airway Assessment (Mallampati)", "Anaesthetic Plan", "Recovery Checklist", "Pain Protocol"] },
  { id: "burns", name: "Burns Unit", icon: "Flame", tools: ["Burns Assessment (Rule of 9s)", "Fluid Resuscitation (Parkland)", "Wound Chart", "Graft Planning", "Pain Ladder", "Nutrition Plan"] },
  { id: "cardiology", name: "Cardiology", icon: "Heart", tools: ["ECG Interpretation", "Troponin Tracker", "ACS Protocol", "Heart Failure Assessment", "Anticoagulation Plan", "Cardiac Rehab"] },
  { id: "chemo", name: "Chemotherapy", icon: "Pill", tools: ["Chemo Protocol Selection", "Dose Calculator (BSA)", "Pre-Chemo Checklist", "Toxicity Grading (CTCAE)", "Antiemetic Protocol", "Blood Count Review"] },
  { id: "dermatology", name: "Dermatology", icon: "Scan", tools: ["Lesion Mapping", "Biopsy Request", "Phototherapy Log", "Dermatology Atlas", "Patch Test Record", "Wound Assessment"] },
  { id: "dialysis", name: "Dialysis", icon: "Activity", tools: ["Dialysis Prescription", "Fluid Balance", "Kt/V Calculator", "Access Assessment", "Electrolyte Tracker", "Dry Weight Trend"] },
  { id: "ent", name: "ENT", icon: "Ear", tools: ["Audiometry Record", "Tympanogram", "Flexible Nasendoscopy", "Voice Assessment", "Thyroid Nodule FNA", "Sleep Study Request"] },
  { id: "gastro", name: "Gastroenterology", icon: "Utensils", tools: ["Endoscopy Report", "Liver Function Trend", "MELD Score", "Child-Pugh Score", "IBD Activity Index", "Nutrition Assessment"] },
  { id: "haematology", name: "Haematology", icon: "Droplet", tools: ["Blood Film Review", "Coagulation Panel", "Transfusion Request", "Sickle Cell Crisis Protocol", "Bone Marrow Report", "Anticoagulation Clinic"] },
  { id: "icu", name: "Intensive Care", icon: "Monitor", tools: ["APACHE II Score", "SOFA Score", "Ventilator Settings", "Sedation (RASS)", "Nutrition (NUTRIC)", "Daily ICU Checklist"] },
  { id: "neonatal", name: "Neonatal", icon: "Baby", tools: ["APGAR Record", "Gestational Age Assessment", "Growth Chart (Fenton)", "Surfactant Protocol", "Bilirubin Chart", "Feeding Plan"] },
  { id: "nephrology", name: "Nephrology", icon: "Filter", tools: ["eGFR Trend", "Urinalysis Review", "Biopsy Report", "Transplant Assessment", "Immunosuppression Protocol", "Dialysis Access"] },
  { id: "neurology", name: "Neurology", icon: "Brain", tools: ["NIHSS Score", "GCS Tracker", "Seizure Log", "Lumbar Puncture Record", "MS Relapse Assessment", "Cognitive Screen (MMSE/MoCA)"] },
  { id: "obstetrics", name: "Obstetrics", icon: "Baby", tools: ["Partograph", "CTG Interpretation", "Bishop Score", "PPH Protocol", "Eclampsia Protocol", "Neonatal Resuscitation"] },
  { id: "oncology", name: "Oncology", icon: "Target", tools: ["Staging (TNM)", "Performance Status (ECOG)", "Treatment Plan", "Symptom Assessment (ESAS)", "Palliative Care Needs", "MDT Summary"] },
  { id: "ophthalmology", name: "Ophthalmology", icon: "Eye", tools: ["Visual Acuity Record", "IOP Measurement", "Fundoscopy Report", "Visual Field Test", "Slit Lamp Findings", "Refraction Record"] },
  { id: "orthopaedics", name: "Orthopaedics", icon: "Bone", tools: ["Fracture Classification", "Neurovascular Check", "Cast/Splint Record", "ROM Assessment", "VTE Prophylaxis", "Rehab Milestones"] },
  { id: "psychiatry", name: "Psychiatry", icon: "Brain", tools: ["Mental State Examination", "PHQ-9", "GAD-7", "Risk Assessment", "Capacity Assessment", "Section/Involuntary Hold"] },
] as const;

export function getSpecialtyById(id: string): SpecialtyWorkspaceDef | undefined {
  return SPECIALTY_WORKSPACES.find((w) => w.id === id);
}
