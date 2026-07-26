/**
 * Specialty tool registry — the single source of truth for what every specialty tool label
 * in this app actually is.
 *
 * WHY THIS FILE EXISTS. The panel used to decide a tool's form BY ITS POSITION in the
 * workspace array: index >= 4 meant "coming soon", index === 3 meant a generic two-number
 * adder, everything else meant a free-text notes box. Partograph rendered as a notes box
 * because it happened to be first in its list. That mechanism produced a clinical surface
 * whose behaviour had nothing to do with the clinical instrument named on it, and it is the
 * root defect behind every phantom tool in this panel — not the individual labels.
 *
 * Position is now never consulted. Every label resolves through an explicit entry here, and
 * a test asserts that every label in SPECIALTY_WORKSPACES has one, so a new label cannot
 * silently inherit a fallback.
 *
 * Every entry is in exactly one of three states, per the standing rule that we complete
 * functionality rather than delete it to hide gaps:
 *
 *   WIRED           reachable, real, persists against a record.
 *   CONSOLIDATED    the canonical implementation lives elsewhere in this app; this entry
 *                   points at it instead of keeping a second copy.
 *   IN_DEVELOPMENT  not built here yet, naming the owning lane and wave. This is a
 *                   transitional state, never a destination.
 *
 * "UNASSIGNED" owners are awaiting a coordinator routing decision and are listed in
 * docs/clinical/specialty-tool-ownership-map.md. They are not orphan withdrawals: the label
 * stays, states plainly that it is not built, and names that its owner is being assigned.
 */

export type ToolDisposition =
  | { state: "WIRED"; form: string; note: string }
  | { state: "CONSOLIDATED"; surface: string; route: string; note: string }
  | {
      state: "IN_DEVELOPMENT";
      owner: string;
      wave: string;
      note: string;
      evidence?: string;
      withdrawnForSafety?: boolean;
    };

/** Keyed by the exact tool label as it appears in SPECIALTY_WORKSPACES. */
export const SPECIALTY_TOOL_REGISTRY: Record<string, ToolDisposition> = {
  "Pre-op Assessment": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "ASA Classification": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Airway Assessment (Mallampati)": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Anaesthetic Plan": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Recovery Checklist": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Pain Protocol": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Burns Assessment (Rule of 9s)": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "W1",
    note: "Acute burns %TBSA and injury-clocked Parkland now exist in the shared burn-domain library. What is missing is this screen's connection to it and the write to the patient's burn assessment, so the calculation is deliberately not offered here until entering a value also records it.",
    withdrawnForSafety: true },
  "Fluid Resuscitation (Parkland)": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "W1",
    note: "Acute burns %TBSA and injury-clocked Parkland now exist in the shared burn-domain library. What is missing is this screen's connection to it and the write to the patient's burn assessment, so the calculation is deliberately not offered here until entering a value also records it.",
    withdrawnForSafety: true },
  "Wound Chart": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Graft Planning": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Pain Ladder": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Nutrition Plan": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "ECG Interpretation": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Troponin Tracker": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "ACS Protocol": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Heart Failure Assessment": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Anticoagulation Plan": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Cardiac Rehab": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Chemo Protocol Selection": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Dose Calculator (BSA)": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Pre-Chemo Checklist": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Toxicity Grading (CTCAE)": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Antiemetic Protocol": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Blood Count Review": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Lesion Mapping": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Biopsy Request": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Phototherapy Log": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Dermatology Atlas": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Patch Test Record": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Wound Assessment": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Dialysis Prescription": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Fluid Balance": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Kt/V Calculator": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Access Assessment": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Electrolyte Tracker": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Dry Weight Trend": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Audiometry Record": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Tympanogram": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Flexible Nasendoscopy": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Voice Assessment": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Thyroid Nodule FNA": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Sleep Study Request": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Endoscopy Report": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Liver Function Trend": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "MELD Score": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "TBC", note: "Absent from the estate." },
  "Child-Pugh Score": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "TBC", note: "Referenced in pct; this surface is not connected to it." },
  "IBD Activity Index": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Nutrition Assessment": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Blood Film Review": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Coagulation Panel": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Transfusion Request": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Sickle Cell Crisis Protocol": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Bone Marrow Report": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Anticoagulation Clinic": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "APACHE II Score": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "TBC", note: "Absent from the estate." },
  "SOFA Score": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "TBC", note: "Referenced in CKP content only; no scoring surface." },
  "Ventilator Settings": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Sedation (RASS)": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "TBC", note: "ICU sedation scale. Absent from the estate." },
  "Nutrition (NUTRIC)": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Daily ICU Checklist": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "APGAR Record": { state: "CONSOLIDATED", surface: "APGARScreen", route: "Inpatient → APGAR tab",
    note: "The real APGAR lives in APGARScreen and writes inpatient.apgar_score through POST /internal/v1/apgar. This entry points there rather than keeping a second copy." },
  "Gestational Age Assessment": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Growth Chart (Fenton)": { state: "IN_DEVELOPMENT", owner: "Paediatrics", wave: "TBC", note: "Preterm growth charting. Reassigned from RMNP to the paediatric pack." },
  "Surfactant Protocol": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Bilirubin Chart": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Feeding Plan": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "eGFR Trend": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Urinalysis Review": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Biopsy Report": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Transplant Assessment": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Immunosuppression Protocol": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Dialysis Access": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "NIHSS Score": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "TBC", note: "Stroke severity scoring. Absent from the estate." },
  "GCS Tracker": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "TBC", note: "GCS exists in daidzai and inpatient; this surface is not connected to either." },
  "Seizure Log": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Lumbar Puncture Record": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "MS Relapse Assessment": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Cognitive Screen (MMSE/MoCA)": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Partograph": { state: "IN_DEVELOPMENT", owner: "RMNP", wave: "governed form delivered",
    note: "PCT backend (V056) and the BFF partograph routes exist, and RMNP has published the governed form definition and the mobile contract. What is missing is this app's wiring to them.",
    evidence: "services/forms-service/src/main/resources/seed-forms/13-labour-partograph-observation.json; docs/clinical/rmnp/partograph-ctg-mobile-contract.md" },
  "CTG Interpretation": { state: "IN_DEVELOPMENT", owner: "RMNP", wave: "governed form delivered",
    note: "PCT backend (V056) and restored BFF routes exist; RMNP has published the governed CTG annotation form. Mobile wiring is the remaining step.",
    evidence: "services/forms-service/src/main/resources/seed-forms/14-ctg-annotation.json; docs/clinical/rmnp/partograph-ctg-mobile-contract.md" },
  "Bishop Score": { state: "IN_DEVELOPMENT", owner: "RMNP", wave: "TBC", note: "Cervical favourability scoring. No backend today; routed to RMNP." },
  "PPH Protocol": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Eclampsia Protocol": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Neonatal Resuscitation": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Staging (TNM)": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Performance Status (ECOG)": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Treatment Plan": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Symptom Assessment (ESAS)": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Palliative Care Needs": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "MDT Summary": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Visual Acuity Record": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "IOP Measurement": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Fundoscopy Report": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Visual Field Test": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Slit Lamp Findings": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Refraction Record": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Fracture Classification": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Neurovascular Check": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Cast/Splint Record": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "ROM Assessment": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "VTE Prophylaxis": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Rehab Milestones": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Mental State Examination": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "PHQ-9": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "forms-service questionnaire now; interpretation awaits mental-health-service (8397)",
    note: "Capture lands as a governed forms-service questionnaire. Scoring and interpretation wait for the mental-health service." },
  "GAD-7": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "forms-service questionnaire now; interpretation awaits mental-health-service (8397)",
    note: "Capture lands as a governed forms-service questionnaire. Scoring and interpretation wait for the mental-health service." },
  "Risk Assessment": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "TBC", withdrawnForSafety: true,
    note: "Withdrawn on safety grounds ahead of routing. This rendered a two-number adder under a suicide- and violence-risk label, so it presented an arbitrary sum as a risk result. Risk assessment belongs with the mental-health service (8397) and its governed instrument." },
  "Capacity Assessment": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
  "Section/Involuntary Hold": { state: "IN_DEVELOPMENT", owner: "UNASSIGNED", wave: "TBC", note: "Not built. Owning lane is awaiting a coordinator routing decision." },
};

export function dispositionForTool(toolName: string): ToolDisposition | undefined {
  return SPECIALTY_TOOL_REGISTRY[toolName];
}

