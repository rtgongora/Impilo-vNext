/**
 * Canonical Lovable-parity encounter workspace navigation.
 * Maps persistent encounter menu sections and wizard steps to real /ehr routes.
 */

export type EncounterWorkspaceSectionId =
  | "overview"
  | "assessment"
  | "problems_diagnoses"
  | "orders_results"
  | "care_management"
  | "consults_referrals"
  | "notes_attachments"
  | "visit_outcome";

export type ClinicalWorkflowStepId =
  | "patient_context"
  | "vitals_triage"
  | "assessment"
  | "problems_diagnoses"
  | "orders_results"
  | "care_plan"
  | "consults_referrals"
  | "notes_attachments"
  | "visit_outcome";

export interface RoleNavFlags {
  isClinical: boolean;
  isPrescriber: boolean;
  isDispenser: boolean;
  isAdmin: boolean;
}

export interface EncounterNavContext {
  patientId: string;
  /** Active encounter from route or resolved active encounter */
  encounterId: string | null;
}

const SECTION_ORDER: EncounterWorkspaceSectionId[] = [
  "overview",
  "assessment",
  "problems_diagnoses",
  "orders_results",
  "care_management",
  "consults_referrals",
  "notes_attachments",
  "visit_outcome",
];

/** First URL segment after /ehr/:patientId/ — maps to a workspace section */
const SEGMENT_TO_SECTION: Record<string, EncounterWorkspaceSectionId> = {
  // overview — chart root only (no segment)
  vitals: "assessment",
  allergies: "assessment",
  history: "assessment",
  assessments: "assessment",
  encounter: "assessment",
  conditions: "problems_diagnoses",
  orders: "orders_results",
  results: "orders_results",
  imaging: "orders_results",
  procedures: "orders_results",
  medications: "care_management",
  "care-plans": "care_management",
  goals: "care_management",
  "care-team": "care_management",
  consults: "consults_referrals",
  referrals: "consults_referrals",
  teleconsults: "consults_referrals",
  notes: "notes_attachments",
  documents: "notes_attachments",
  discharge: "visit_outcome",
  // chart utilities still roll up to nearest section for highlight
  summary: "overview",
  timeline: "overview",
  encounters: "overview",
  ips: "overview",
};

export function sectionForPathname(pathname: string, patientId: string): EncounterWorkspaceSectionId | null {
  const prefix = `/ehr/${patientId}`;
  if (!pathname.startsWith(prefix)) return null;
  const tail = pathname.slice(prefix.length).replace(/^\//, "");
  if (!tail) return "overview";
  const first = tail.split("/")[0] ?? "";
  if (first === "encounter") return "assessment";
  return SEGMENT_TO_SECTION[first] ?? null;
}

export function isSectionVisible(section: EncounterWorkspaceSectionId, roles: RoleNavFlags): boolean {
  if (section === "overview") return true;
  if (roles.isAdmin) return true;
  if (section === "orders_results" && roles.isDispenser && !roles.isClinical) return true;
  if (!roles.isClinical && !roles.isPrescriber && !roles.isDispenser) return false;
  return true;
}

export function buildSectionHref(section: EncounterWorkspaceSectionId, ctx: EncounterNavContext): string {
  const { patientId, encounterId } = ctx;
  const q = (path: string) =>
    encounterId && section !== "overview"
      ? `${path}${path.includes("?") ? "&" : "?"}encounter_id=${encodeURIComponent(encounterId)}`
      : path;

  switch (section) {
    case "overview":
      return `/ehr/${patientId}`;
    case "assessment":
      if (encounterId) return `/ehr/${patientId}/encounter/${encounterId}`;
      return q(`/ehr/${patientId}/vitals`);
    case "problems_diagnoses":
      return q(`/ehr/${patientId}/conditions`);
    case "orders_results":
      return q(`/ehr/${patientId}/orders`);
    case "care_management":
      return q(`/ehr/${patientId}/care-plans`);
    case "consults_referrals":
      return q(`/ehr/${patientId}/consults`);
    case "notes_attachments":
      return q(`/ehr/${patientId}/notes`);
    case "visit_outcome":
      return q(`/ehr/${patientId}/discharge`);
    default:
      return `/ehr/${patientId}`;
  }
}

export const ENCOUNTER_MENU_LABELS: Record<EncounterWorkspaceSectionId, string> = {
  overview: "Overview",
  assessment: "Assessment",
  problems_diagnoses: "Problems & Diagnoses",
  orders_results: "Orders & Results",
  care_management: "Care & Management",
  consults_referrals: "Consults & Referrals",
  notes_attachments: "Notes & Attachments",
  visit_outcome: "Visit Outcome",
};

export function orderedWorkspaceSections(): EncounterWorkspaceSectionId[] {
  return [...SECTION_ORDER];
}

export interface WizardStepModel {
  id: ClinicalWorkflowStepId;
  label: string;
}

/** Default configurable workflow — programmes can replace via ClinicalWorkflowConfig */
export const DEFAULT_CLINICAL_WIZARD_STEPS: WizardStepModel[] = [
  { id: "patient_context", label: "Patient Context" },
  { id: "vitals_triage", label: "Vitals & Triage" },
  { id: "assessment", label: "Assessment" },
  { id: "problems_diagnoses", label: "Problems & Diagnoses" },
  { id: "orders_results", label: "Orders & Results" },
  { id: "care_plan", label: "Care Plan" },
  { id: "consults_referrals", label: "Consults & Referrals" },
  { id: "notes_attachments", label: "Notes & Attachments" },
  { id: "visit_outcome", label: "Visit Outcome" },
];

export function wizardStepIndexForPathname(
  pathname: string,
  patientId: string,
  steps: WizardStepModel[] = DEFAULT_CLINICAL_WIZARD_STEPS,
): number {
  const vitalsPath = `/ehr/${patientId}/vitals`;
  if (pathname === vitalsPath || pathname.startsWith(`${vitalsPath}?`)) {
    const i = steps.findIndex((s) => s.id === "vitals_triage");
    return i >= 0 ? i : 0;
  }

  const section = sectionForPathname(pathname, patientId);
  const map: Record<EncounterWorkspaceSectionId, ClinicalWorkflowStepId> = {
    overview: "patient_context",
    assessment: "assessment",
    problems_diagnoses: "problems_diagnoses",
    orders_results: "orders_results",
    care_management: "care_plan",
    consults_referrals: "consults_referrals",
    notes_attachments: "notes_attachments",
    visit_outcome: "visit_outcome",
  };
  const stepId = section ? map[section] : "patient_context";
  const idx = steps.findIndex((s) => s.id === stepId);
  return idx >= 0 ? idx : 0;
}

/** Maps wizard step to encounter menu section for highlighting and Tshepo-aligned UI gating. */
export function encounterSectionForWizardStepId(
  stepId: ClinicalWorkflowStepId,
): EncounterWorkspaceSectionId {
  const map: Record<ClinicalWorkflowStepId, EncounterWorkspaceSectionId> = {
    patient_context: "overview",
    vitals_triage: "assessment",
    assessment: "assessment",
    problems_diagnoses: "problems_diagnoses",
    orders_results: "orders_results",
    care_plan: "care_management",
    consults_referrals: "consults_referrals",
    notes_attachments: "notes_attachments",
    visit_outcome: "visit_outcome",
  };
  return map[stepId];
}

export function buildWizardStepHref(
  stepId: ClinicalWorkflowStepId,
  ctx: EncounterNavContext,
): string {
  const map: Record<ClinicalWorkflowStepId, EncounterWorkspaceSectionId> = {
    patient_context: "overview",
    vitals_triage: "assessment",
    assessment: "assessment",
    problems_diagnoses: "problems_diagnoses",
    orders_results: "orders_results",
    care_plan: "care_management",
    consults_referrals: "consults_referrals",
    notes_attachments: "notes_attachments",
    visit_outcome: "visit_outcome",
  };
  if (stepId === "vitals_triage") {
    const { patientId, encounterId } = ctx;
    if (encounterId) return `/ehr/${patientId}/encounter/${encounterId}`;
    return `/ehr/${patientId}/vitals`;
  }
  const sec = map[stepId];
  if (sec === "overview") return `/ehr/${ctx.patientId}`;
  return buildSectionHref(sec, ctx);
}
