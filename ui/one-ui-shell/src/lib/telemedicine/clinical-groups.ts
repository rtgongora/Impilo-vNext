/**
 * Clinical group taxonomy and governance doctrine (config-as-data).
 *
 * Groups are for DISCOVERY and ROUTING — session modes are for clinical
 * governance. Governed clinical groups do NOT exist in any backend yet, and
 * the wellness social groups in community-service are explicitly unsuitable
 * (no consent/clinical-audit model). Group creation is therefore fail-closed:
 * this module documents the taxonomy and the governance a backend must
 * enforce (handoff HO-5), and the UI states that creation is blocked until
 * that backend exists. That fail-closed posture is itself acceptance journey
 * #25 (governance blocks unauthorised clinical group creation).
 */

export type ClinicalGroupType =
  | "SPECIALTY"
  | "DEPARTMENT"
  | "FACILITY_WORKSPACE"
  | "WARD"
  | "THEATRE_PROCEDURE_TEAM"
  | "CASUALTY_EMERGENCY"
  | "MDT_PANEL"
  | "AUDIT_COMMITTEE"
  | "SUPERVISION_LEARNING"
  | "DIAGNOSTICS_REVIEW"
  | "PROGRAMME_SERVICE_LINE"
  | "COMMUNITY_FOLLOWUP"
  | "ON_CALL_POOL"
  | "DIASPORA_SPECIALIST_POOL"
  | "DISASTER_RESPONSE_POOL";

export interface ClinicalGroupTypeDescriptor {
  type: ClinicalGroupType;
  label: string;
  purpose: string;
  examples: string[];
  /** Session-mode ids (session-modes.ts) this group type may open. */
  allowedSessionModeIds: string[];
  /** Whether members may ever see patient-identifiable data through the group. */
  patientDataExposure: "NEVER" | "CASE_PRESENTATION_ONLY" | "CARE_TEAM_SCOPED";
}

export const CLINICAL_GROUP_TYPES: ClinicalGroupTypeDescriptor[] = [
  {
    type: "SPECIALTY",
    label: "Clinical specialty group",
    purpose: "Route consults and case presentations to a specialty's pooled capability.",
    examples: ["Neurosurgeons", "Cardiothoracic Surgeons", "Provincial Obstetric Consultants"],
    allowedSessionModeIds: ["provider-to-provider-advice", "mdt-case-review", "case-presentation"],
    patientDataExposure: "CASE_PRESENTATION_ONLY",
  },
  {
    type: "DEPARTMENT",
    label: "Department group",
    purpose: "Reach a facility department's clinical team.",
    examples: ["Nutritionists at Hospital X", "Radiology Department"],
    allowedSessionModeIds: ["provider-to-provider-advice", "direct-clinical-encounter"],
    patientDataExposure: "CARE_TEAM_SCOPED",
  },
  {
    type: "FACILITY_WORKSPACE",
    label: "Facility workspace group",
    purpose: "Route to a workspace inside a facility (casualty, OPD, ward, theatre).",
    examples: ["Casualty On-Call Group", "OPD Team"],
    allowedSessionModeIds: ["provider-to-provider-advice", "hybrid-encounter-support"],
    patientDataExposure: "CARE_TEAM_SCOPED",
  },
  {
    type: "WARD",
    label: "Ward group",
    purpose: "Ward-level coordination and escalation.",
    examples: ["Ward 4 Clinical Team"],
    allowedSessionModeIds: ["hybrid-encounter-support", "provider-to-provider-advice"],
    patientDataExposure: "CARE_TEAM_SCOPED",
  },
  {
    type: "THEATRE_PROCEDURE_TEAM",
    label: "Theatre / procedure team",
    purpose: "Procedure and theatre tele-support with strict role permissions.",
    examples: ["Theatre Tele-support Group"],
    allowedSessionModeIds: ["hybrid-encounter-support", "teaching-supervision"],
    patientDataExposure: "CARE_TEAM_SCOPED",
  },
  {
    type: "CASUALTY_EMERGENCY",
    label: "Casualty / emergency unit group",
    purpose: "Emergency advice and rapid escalation routing.",
    examples: ["Casualty On-Call Group", "Emergency Advisory Pool"],
    allowedSessionModeIds: ["emergency-advisory", "hybrid-encounter-support"],
    patientDataExposure: "CARE_TEAM_SCOPED",
  },
  {
    type: "MDT_PANEL",
    label: "MDT panel",
    purpose: "Multidisciplinary case review with pseudonymised presentation by default.",
    examples: ["Oncology MDT", "Complex Case Review Board"],
    allowedSessionModeIds: ["mdt-case-review", "case-presentation"],
    patientDataExposure: "CASE_PRESENTATION_ONLY",
  },
  {
    type: "AUDIT_COMMITTEE",
    label: "Audit committee",
    purpose: "Mortality/adverse-event review with no-name/no-blame configuration.",
    examples: ["Maternal Death Audit Panel", "Perinatal Death Review Panel"],
    allowedSessionModeIds: ["audit-mortality-review", "case-presentation"],
    patientDataExposure: "CASE_PRESENTATION_ONLY",
  },
  {
    type: "SUPERVISION_LEARNING",
    label: "Supervision / learning group",
    purpose: "Teaching, mentorship and supervised participation with restricted roles.",
    examples: ["Clinical Case Review Group", "Registrar Supervision Group"],
    allowedSessionModeIds: ["teaching-supervision", "case-presentation"],
    patientDataExposure: "CASE_PRESENTATION_ONLY",
  },
  {
    type: "DIAGNOSTICS_REVIEW",
    label: "Diagnostics review group",
    purpose: "Imaging/lab interpretation and diagnostic escalation.",
    examples: ["Radiology Review Group"],
    allowedSessionModeIds: ["diagnostics-review", "mdt-case-review"],
    patientDataExposure: "CASE_PRESENTATION_ONLY",
  },
  {
    type: "PROGRAMME_SERVICE_LINE",
    label: "Programme / service line group",
    purpose: "Programme-owned care model coordination.",
    examples: ["National Mental Health Specialists", "NCD Follow-up Coordinators"],
    allowedSessionModeIds: ["provider-to-provider-advice", "mdt-case-review"],
    patientDataExposure: "CASE_PRESENTATION_ONLY",
  },
  {
    type: "COMMUNITY_FOLLOWUP",
    label: "Community follow-up group",
    purpose: "CHW-supported follow-up and facility-community linkage.",
    examples: ["District CHW Follow-up Team"],
    allowedSessionModeIds: ["provider-to-provider-advice"],
    patientDataExposure: "CARE_TEAM_SCOPED",
  },
  {
    type: "ON_CALL_POOL",
    label: "On-call pool",
    purpose: "Rostered availability pool for accept/assign routing.",
    examples: ["On-call Neurosurgery Group"],
    allowedSessionModeIds: ["provider-to-provider-advice", "emergency-advisory"],
    patientDataExposure: "CARE_TEAM_SCOPED",
  },
  {
    type: "DIASPORA_SPECIALIST_POOL",
    label: "Diaspora specialist pool",
    purpose:
      "Approved cross-border participation, strictly privilege- and jurisdiction-gated (advice/MDT only unless policy permits more).",
    examples: ["Zimbabwe Diaspora Specialist Pool"],
    allowedSessionModeIds: ["provider-to-provider-advice", "mdt-case-review", "teaching-supervision"],
    patientDataExposure: "CASE_PRESENTATION_ONLY",
  },
  {
    type: "DISASTER_RESPONSE_POOL",
    label: "Emergency / disaster response pool",
    purpose: "Surge rosters activated during emergencies; friction reduced, audit increased.",
    examples: ["Emergency Disaster Response Pool"],
    allowedSessionModeIds: ["emergency-advisory", "provider-to-provider-advice"],
    patientDataExposure: "CARE_TEAM_SCOPED",
  },
];

/**
 * Governance a clinical-group backend MUST enforce before any group becomes
 * creatable. Displayed in the UI as the fail-closed rationale.
 */
export const GROUP_GOVERNANCE_REQUIREMENTS: string[] = [
  "Named owner and moderator with verified provider identity (Varapi)",
  "Explicit link to facility, virtual hospital or workspace",
  "Membership eligibility rules (cadre, specialty, registration status)",
  "Allowed request types and allowed session modes",
  "Allowed patient identity visibility levels per session mode",
  "Recording rules (default off; governed exceptions only)",
  "External/diaspora participant rules gated by jurisdiction policy",
  "Default queue and escalation rules",
  "Audit trail for membership changes, requests and every case access",
  "Inactivity/retirement rules",
];

/**
 * Why group creation is disabled today. Fail-closed truthful UX — this IS the
 * enforcement of acceptance journey #25 until the backend exists.
 */
export const GROUP_CREATION_BLOCKED_REASON =
  "Governed clinical groups require a backend that enforces ownership, eligibility, session-mode limits, identity-visibility policy and audit (Tshepo/OPA-backed). No such backend exists yet; wellness social groups are not clinically governed and must not carry patient data. Creation stays disabled until the governed model ships.";
