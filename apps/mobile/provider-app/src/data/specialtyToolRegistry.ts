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
 * Every IN_DEVELOPMENT entry names an owning lane — there are no unrouted labels, and a test
 * enforces it. Ownership was routed at workspace level by the coordinator; an owning lane may
 * flag an individual label back rather than claim it. The full label -> state -> owner -> wave
 * map is docs/clinical/specialty-tool-ownership-map.md, generated from this file so it cannot
 * drift from what the app actually does.
 *
 * WIRED and CONSOLIDATED entries name a surface in the panel's RENDERED_SURFACES map. The guard
 * test asserts that mapping is total in both directions, so an entry cannot claim a surface that
 * was never built, and a built surface cannot be left with nothing pointing at it.
 */

export type ToolDisposition =
  | { state: "WIRED"; surface: string; note: string }
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
  "Pre-op Assessment": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "ASA Classification": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Airway Assessment (Mallampati)": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Anaesthetic Plan": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Recovery Checklist": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Pain Protocol": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Burns Assessment (Rule of 9s)": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "W1",
    note: "Acute burns %TBSA and injury-clocked Parkland now exist in the shared burn-domain library. What is missing is this screen's connection to it and the write to the patient's burn assessment, so the calculation is deliberately not offered here until entering a value also records it.",
    withdrawnForSafety: true },
  "Fluid Resuscitation (Parkland)": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "W1",
    note: "Acute burns %TBSA and injury-clocked Parkland now exist in the shared burn-domain library. What is missing is this screen's connection to it and the write to the patient's burn assessment, so the calculation is deliberately not offered here until entering a value also records it.",
    withdrawnForSafety: true },
  "Wound Chart": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "W1", note: "Not built on this surface. Routed to the Emergency lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Graft Planning": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "W1", note: "Not built on this surface. Routed to the Emergency lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Pain Ladder": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "W1", note: "Not built on this surface. Routed to the Emergency lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Nutrition Plan": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "W1", note: "Not built on this surface. Routed to the Emergency lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "ECG Interpretation": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Troponin Tracker": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "ACS Protocol": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Heart Failure Assessment": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Anticoagulation Plan": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Cardiac Rehab": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Chemo Protocol Selection": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Dose Calculator (BSA)": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Pre-Chemo Checklist": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Toxicity Grading (CTCAE)": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Antiemetic Protocol": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Blood Count Review": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Lesion Mapping": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Biopsy Request": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Phototherapy Log": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Dermatology Atlas": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Patch Test Record": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Wound Assessment": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Dialysis Prescription": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Fluid Balance": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Kt/V Calculator": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Access Assessment": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Electrolyte Tracker": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Dry Weight Trend": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Audiometry Record": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Tympanogram": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Flexible Nasendoscopy": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Voice Assessment": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Thyroid Nodule FNA": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Sleep Study Request": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Endoscopy Report": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Liver Function Trend": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "MELD Score": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "TBC", note: "Absent from the estate." },
  "Child-Pugh Score": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "TBC", note: "Referenced in pct; this surface is not connected to it." },
  "IBD Activity Index": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Nutrition Assessment": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Blood Film Review": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Coagulation Panel": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Transfusion Request": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Sickle Cell Crisis Protocol": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Bone Marrow Report": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Anticoagulation Clinic": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "APACHE II Score": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "TBC", note: "Absent from the estate." },
  "SOFA Score": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "TBC", note: "Referenced in CKP content only; no scoring surface." },
  "Ventilator Settings": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "TBC", note: "Not built on this surface. Routed to the Emergency lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Sedation (RASS)": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "TBC", note: "ICU sedation scale. Absent from the estate." },
  "Nutrition (NUTRIC)": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "TBC", note: "Not built on this surface. Routed to the Emergency lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Daily ICU Checklist": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "TBC", note: "Not built on this surface. Routed to the Emergency lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "APGAR Record": { state: "CONSOLIDATED", surface: "APGARScreen", route: "Inpatient → APGAR tab",
    note: "The real APGAR lives in APGARScreen and writes inpatient.apgar_score through POST /internal/v1/apgar. This entry points there rather than keeping a second copy." },
  "Gestational Age Assessment": { state: "IN_DEVELOPMENT", owner: "RMNP", wave: "TBC", note: "Not built on this surface. Routed to the RMNP lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Growth Chart (Fenton)": { state: "IN_DEVELOPMENT", owner: "Paediatrics", wave: "Growth standards (backend and web done)",
    note: "Preterm growth charting exists and is not built on this surface. Infants born before 37 weeks are scored against the Fenton 2013 tables in libs/paediatric-domain, read at postmenstrual age, stamped with the standard and engine version at write, and stored in pct.pct_growth_measurements; the chart is drawn on the web clinical workspace. This app will consume the same API rather than grow a second growth calculation, which would be a second answer about the same baby.",
    evidence: "libs/paediatric-domain/.../growth/GrowthStandard.java · pct V400 · GET /internal/v1/growth/reference-curves · ui/one-ui-shell/src/app/ehr/[patientId]/growth-chart" },
  "Surfactant Protocol": { state: "IN_DEVELOPMENT", owner: "RMNP", wave: "TBC", note: "Not built on this surface. Routed to the RMNP lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Bilirubin Chart": { state: "IN_DEVELOPMENT", owner: "RMNP", wave: "TBC", note: "Not built on this surface. Routed to the RMNP lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Feeding Plan": { state: "IN_DEVELOPMENT", owner: "RMNP", wave: "TBC", note: "Not built on this surface. Routed to the RMNP lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "eGFR Trend": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Urinalysis Review": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Biopsy Report": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Transplant Assessment": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Immunosuppression Protocol": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Dialysis Access": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "NIHSS Score": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "TBC", note: "Stroke severity scoring. Absent from the estate." },
  "GCS Tracker": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "TBC", note: "GCS exists in daidzai and inpatient; this surface is not connected to either." },
  "Seizure Log": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Lumbar Puncture Record": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "MS Relapse Assessment": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Cognitive Screen (MMSE/MoCA)": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Partograph": { state: "WIRED", surface: "PartographWorkspace",
    note: "Delivered by the mobile lane against pct-service V056 and the RMNP pack's mobile contract (docs/clinical/rmnp/partograph-ctg-mobile-contract.md). This entry renders the governed workspace itself, so there is no second copy to drift." },
  "CTG Interpretation": { state: "WIRED", surface: "CtgWorkspace",
    note: "Delivered by the mobile lane against pct-service V056 and the RMNP pack's mobile contract (docs/clinical/rmnp/partograph-ctg-mobile-contract.md). This entry renders the governed workspace itself, so there is no second copy to drift." },
  "Bishop Score": { state: "IN_DEVELOPMENT", owner: "RMNP", wave: "TBC", note: "Cervical favourability scoring. No backend today; routed to RMNP." },
  "Maternal Near-Miss Assessment": { state: "WIRED", surface: "MaternalNearMissWorkspace",
    note: "Delivered against the WHO near-miss engine in clinical-knowledge-platform-service, joined to form 21 (impilo.maternal.nearmiss.assessment.v1) by experience-bff's MaternalNearMissController (docs/clinical/rmnp/chw-community-postnatal-mobile-contract.md's companion RMNP W12 near-miss work). Identification only — nothing is persisted; the confidential MPDSR review is a separate, later instrument. This entry renders the governed workspace itself, so there is no second copy to drift." },
  "PPH Protocol": { state: "IN_DEVELOPMENT", owner: "RMNP", wave: "TBC", note: "Not built on this surface. Routed to the RMNP lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Eclampsia Protocol": { state: "IN_DEVELOPMENT", owner: "RMNP", wave: "TBC", note: "Not built on this surface. Routed to the RMNP lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Neonatal Resuscitation": { state: "IN_DEVELOPMENT", owner: "RMNP", wave: "TBC", note: "Not built on this surface. Routed to the RMNP lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Staging (TNM)": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Performance Status (ECOG)": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Treatment Plan": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Symptom Assessment (ESAS)": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Palliative Care Needs": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "MDT Summary": { state: "IN_DEVELOPMENT", owner: "AdultMedicine", wave: "W6+", note: "Not built on this surface. Routed to the AdultMedicine lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Visual Acuity Record": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "IOP Measurement": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Fundoscopy Report": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Visual Field Test": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Slit Lamp Findings": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Refraction Record": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Fracture Classification": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Neurovascular Check": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Cast/Splint Record": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "ROM Assessment": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "VTE Prophylaxis": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Rehab Milestones": { state: "IN_DEVELOPMENT", owner: "Surgery", wave: "TBC", note: "Not built on this surface. Routed to the Surgery lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Mental State Examination": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "W13 (mental-health)", note: "Not built on this surface. Routed to the Emergency lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "PHQ-9": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "forms-service questionnaire now; interpretation awaits mental-health-service (8397)",
    note: "Capture lands as a governed forms-service questionnaire. Scoring and interpretation wait for the mental-health service." },
  "GAD-7": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "forms-service questionnaire now; interpretation awaits mental-health-service (8397)",
    note: "Capture lands as a governed forms-service questionnaire. Scoring and interpretation wait for the mental-health service." },
  "Risk Assessment": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "TBC", withdrawnForSafety: true,
    note: "Withdrawn on safety grounds ahead of routing. This rendered a two-number adder under a suicide- and violence-risk label, so it presented an arbitrary sum as a risk result. Risk assessment belongs with the mental-health service (8397) and its governed instrument." },
  "Capacity Assessment": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "W13 (mental-health)", note: "Not built on this surface. Routed to the Emergency lane at workspace level; that lane may flag individual labels back to the coordinator." },
  "Section/Involuntary Hold": { state: "IN_DEVELOPMENT", owner: "Emergency", wave: "W13 (mental-health)", note: "Not built on this surface. Routed to the Emergency lane at workspace level; that lane may flag individual labels back to the coordinator." },
};

export function dispositionForTool(toolName: string): ToolDisposition | undefined {
  return SPECIALTY_TOOL_REGISTRY[toolName];
}

