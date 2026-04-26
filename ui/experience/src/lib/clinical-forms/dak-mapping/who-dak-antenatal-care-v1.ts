/**
 * Mapping table anchoring the exemplar to WHO Digital Adaptation Kit concepts (documentation layer).
 * Full DAK bundles (personas, scenarios, BPMN, data dictionary XML) are maintained by WHO;
 * this table links Impilo artefacts to those domains without importing entire DAK payloads.
 */

export const WHO_DAK_ANTENATAL_CARE_REF = "WHO ANC Digital Adaptation Kit (conceptual alignment — vNext exemplar)";

export interface DakProcessStep {
  id: string;
  label: string;
  description: string;
  /** Core data elements touched in this step (logical names) */
  coreDataElements: string[];
}

/** High-level care pathway steps aligned to ANC contact scheduling (simplified). */
export const ANC_FIRST_CONTACT_PROCESS: DakProcessStep[] = [
  {
    id: "anc-register",
    label: "Registration & history",
    description: "Identify woman, confirm pregnancy, prior obstetric history.",
    coreDataElements: ["gravida", "para", "lmp", "presenting_complaint"],
  },
  {
    id: "anc-screen",
    label: "Screening & diagnostics",
    description: "Syphilis and HIV pathways per national guideline.",
    coreDataElements: ["syphilis_screen", "hiv_status_known"],
  },
  {
    id: "anc-counsel",
    label: "Counselling & education",
    description: "Danger signs, birth preparedness, partner involvement.",
    coreDataElements: ["danger_signs_education", "partner_involvement"],
  },
  {
    id: "anc-refer",
    label: "Referral decision",
    description: "Escalate when red-flag symptoms or unstable vitals.",
    coreDataElements: ["referral_need"],
  },
];
