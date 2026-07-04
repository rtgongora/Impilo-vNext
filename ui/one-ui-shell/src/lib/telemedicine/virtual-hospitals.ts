/**
 * Virtual Hospital operating model — configuration substrate (doctrine-as-data).
 *
 * Product doctrine: virtual hospitals are SERVICE ARCHITECTURE, not duplicate
 * building architecture. They are governed pools of clinical capability
 * organised as virtual service-delivery institutions — never automatic copies
 * of physical facilities, and never rows in the physical facility registry.
 *
 * This module is the seed specification for the backend substrate (handoff
 * HO-2 in docs/architecture/telemedicine-virtual-hospitals-operating-model.md).
 * Every definition carries an honest `substrateStatus` so the UI never claims
 * runtime capability (queues, rosters, live metrics) that does not exist yet.
 *
 * Identity doctrine — these are DISTINCT identifier classes and must never be
 * collapsed into one field:
 *  - Physical Facility ID (Tuso facility registry)
 *  - Virtual Hospital ID (this substrate)
 *  - Operating Authority ID (accountable organisation)
 *  - Provider Home Affiliation (Varapi)
 *  - Provider Virtual Privilege (per virtual hospital, policy-gated)
 *  - Encounter Delivery Context (where care actually happened)
 */

/** How the virtual institution relates to physical facilities and authority. */
export type VirtualHospitalOperatingModel =
  | "HOSTED" // hosted by one accountable physical facility
  | "NETWORKED" // staffed across a facility network; own operating identity
  | "PROGRAMME_OWNED" // owned by a national programme / service line
  | "EMERGENCY_ACTIVATED" // activated/scaled during emergencies and disasters
  | "DIASPORA_ENABLED"; // may include approved cross-border participants

export type VirtualHospitalLevel =
  | "NATIONAL"
  | "PROVINCIAL"
  | "SPECIALIST"
  | "PROGRAMME"
  | "COMMUNITY"
  | "EMERGENCY"
  | "LEARNING";

/**
 * Roles a physical facility can play towards a virtual hospital. A facility
 * link NEVER implies its staff are assigned to virtual duties — staffing is a
 * separate, explicit mapping (STAFF_CONTRIBUTING).
 */
export type LinkedFacilityRole =
  | "ACCESS_POINT"
  | "REQUESTER"
  | "REFERRAL_DESTINATION"
  | "DIAGNOSTICS_SITE"
  | "DISPENSING_SITE"
  | "PROCEDURE_SITE"
  | "HOST_FACILITY"
  | "STAFF_CONTRIBUTING";

/** Policy-gated participation rights for cross-border / diaspora providers. */
export type CrossBorderPrivilege =
  | "PROVIDER_TO_PROVIDER_ADVICE_ONLY"
  | "MDT_ONLY"
  | "DIRECT_PATIENT_TELECONSULT"
  | "PRESCRIBING"
  | "ORDERING"
  | "COUNTERSIGNATURE"
  | "EMERGENCY_ADVISORY_ONLY"
  | "TEACHING_SUPERVISION_ONLY"
  | "BLOCKED_PENDING_REGULATORY_APPROVAL";

export type BillingModel =
  | "PUBLIC_SERVICE"
  | "PROGRAMME_FUNDED"
  | "DONOR_PARTNER_SUPPORTED"
  | "EMPLOYER_OCCUPATIONAL_HEALTH"
  | "MEDICAL_AID_REIMBURSED"
  | "PRIVATE_SELF_PAY"
  | "DIASPORA_PAID"
  | "CROSS_BORDER_PAID"
  | "SUBSCRIPTION_CONTINUITY_CARE"
  | "EMERGENCY_DISASTER_FREE"
  | "TEACHING_SUPERVISION_FUNDED";

/**
 * Honest lifecycle for each institution. Nothing in this file may claim
 * OPERATIONAL until the backend substrate (registry, queues, rosters) exists.
 */
export type SubstrateStatus =
  | "CONFIGURED_AWAITING_SUBSTRATE" // model agreed; backend registry pending (HO-2)
  | "ROUTABLE_VIA_EXISTING_SEAMS" // reachable today through real teleconsult routing
  | "OPERATIONAL"; // reserved for when backend queues/rosters are live

/** Routing that works TODAY through the real teleconsult stack. */
export interface CurrentRoutingSeam {
  /** BFF-validated routing type usable now (e.g. TEAM/SPECIALTY_POOL). */
  routingType: "TEAM" | "SPECIALTY_POOL" | "WORKSPACE" | "FACILITY_SERVICE" | "PRACTITIONER";
  /** Target reference convention for the teleconsult request. */
  targetRef: string;
  note: string;
}

export interface VirtualHospitalQueueSpec {
  id: string;
  name: string;
  /** Every queue is a spec until the virtual queue engine exists. */
  status: "AWAITING_BACKEND";
}

export interface VirtualHospitalDefinition {
  /** Virtual Hospital ID — distinct identifier class, never a facility id. */
  id: string;
  name: string;
  level: VirtualHospitalLevel;
  operatingModel: VirtualHospitalOperatingModel;
  /** Accountable organisation (Operating Authority ID class). */
  operatingAuthority: string;
  /**
   * Regulatory/licensing posture is configurable and auditable — never a
   * hard-coded legal assumption (telehealth regulation is evolving).
   */
  regulatoryStatus: "POLICY_CONFIGURABLE_PENDING_DETERMINATION";
  purpose: string;
  catchment: string;
  serviceLines: string[];
  /** Cadres expected in the provider pool (roster substrate pending). */
  providerPoolCadres: string[];
  /** Facility roles this institution expects (explicit, never implicit). */
  linkedFacilityRoles: LinkedFacilityRole[];
  /** Whether approved diaspora/cross-border participation is in scope. */
  crossBorderParticipation: boolean;
  allowedCrossBorderPrivileges: CrossBorderPrivilege[];
  billingModels: BillingModel[];
  /** Session-mode ids from session-modes.ts relevant to this institution. */
  sessionModeIds: string[];
  queues: VirtualHospitalQueueSpec[];
  substrateStatus: SubstrateStatus;
  /** Real, working entry paths available today (deep links / routing seams). */
  currentRoutingSeams: CurrentRoutingSeam[];
  /** For provincial instances: ISO-style province code. */
  provinceCode?: string;
}

/**
 * Zimbabwe provinces. Tuso holds districts/wards via the bulk geo import API
 * but has no seeded province table; these codes mirror the `province_code`
 * convention used by Tuso ZW geo data and should be reconciled against Tuso
 * once province reference data is bulk-loaded (see capability map §3.3).
 */
export const ZW_PROVINCES: ReadonlyArray<{ code: string; name: string }> = [
  { code: "ZW-HA", name: "Harare" },
  { code: "ZW-BU", name: "Bulawayo" },
  { code: "ZW-MA", name: "Manicaland" },
  { code: "ZW-MC", name: "Mashonaland Central" },
  { code: "ZW-ME", name: "Mashonaland East" },
  { code: "ZW-MW", name: "Mashonaland West" },
  { code: "ZW-MV", name: "Masvingo" },
  { code: "ZW-MN", name: "Matabeleland North" },
  { code: "ZW-MS", name: "Matabeleland South" },
  { code: "ZW-MI", name: "Midlands" },
];

const TELECONSULT_SEAM = (specialty: string): CurrentRoutingSeam => ({
  routingType: "SPECIALTY_POOL",
  targetRef: specialty,
  note: "Reachable today as a teleconsult SPECIALTY_POOL routing target (real BFF-validated path).",
});

const queue = (id: string, name: string): VirtualHospitalQueueSpec => ({
  id,
  name,
  status: "AWAITING_BACKEND",
});

/** The 12 strategic virtual hospitals (deliberately NOT one per facility). */
export const STRATEGIC_VIRTUAL_HOSPITALS: VirtualHospitalDefinition[] = [
  {
    id: "vh-national-telemedicine",
    name: "National Telemedicine Hospital",
    level: "NATIONAL",
    operatingModel: "NETWORKED",
    operatingAuthority: "MoHCC (national)",
    regulatoryStatus: "POLICY_CONFIGURABLE_PENDING_DETERMINATION",
    purpose:
      "Countrywide telemedicine coordination, national specialist support, overflow support, quality monitoring, and national service continuity.",
    catchment: "National — all provinces, all levels of care",
    serviceLines: [
      "General teleconsultation",
      "Facility support",
      "Specialist escalation",
      "Emergency overflow",
      "Remote monitoring review",
      "Quality & audit monitoring",
    ],
    providerPoolCadres: [
      "Telemedicine triage providers",
      "Medical officers",
      "Nurses/midwives",
      "Specialists",
      "Diagnostics reviewers",
      "Mental health providers",
      "Supervisors",
      "Helpdesk agents",
    ],
    linkedFacilityRoles: ["ACCESS_POINT", "REQUESTER", "REFERRAL_DESTINATION", "STAFF_CONTRIBUTING"],
    crossBorderParticipation: true,
    allowedCrossBorderPrivileges: [
      "PROVIDER_TO_PROVIDER_ADVICE_ONLY",
      "MDT_ONLY",
      "BLOCKED_PENDING_REGULATORY_APPROVAL",
    ],
    billingModels: ["PUBLIC_SERVICE", "PROGRAMME_FUNDED", "EMERGENCY_DISASTER_FREE"],
    sessionModeIds: [
      "direct-clinical-encounter",
      "provider-to-provider-advice",
      "emergency-advisory",
      "mdt-case-review",
    ],
    queues: [
      queue("national-general-teleconsult", "National General Teleconsult Queue"),
      queue("facility-support", "Facility Support Queue"),
      queue("specialist-escalation", "Specialist Escalation Queue"),
      queue("emergency-overflow", "Emergency Overflow Queue"),
      queue("remote-monitoring-review", "Remote Monitoring Review Queue"),
      queue("missed-failed-session", "Missed/Failed Session Queue"),
    ],
    substrateStatus: "ROUTABLE_VIA_EXISTING_SEAMS",
    currentRoutingSeams: [TELECONSULT_SEAM("GENERAL_TELEMEDICINE")],
  },
  {
    id: "vh-specialist",
    name: "Virtual Specialist Hospital",
    level: "SPECIALIST",
    operatingModel: "NETWORKED",
    operatingAuthority: "MoHCC with central hospital specialist base",
    regulatoryStatus: "POLICY_CONFIGURABLE_PENDING_DETERMINATION",
    purpose:
      "Specialist-care virtual institution: provider-to-provider consultation, specialist teleconsults, MDT reviews, complex case review, procedure support, specialist follow-up.",
    catchment: "National, provincial spokes",
    serviceLines: [
      "Internal medicine",
      "Surgery",
      "Paediatrics",
      "Obstetrics & gynaecology",
      "Anaesthesia",
      "Psychiatry",
      "Radiology/diagnostics",
      "Rehabilitation medicine",
      "Infectious diseases",
    ],
    providerPoolCadres: ["Specialists", "Medical officers", "Supervisors"],
    linkedFacilityRoles: ["REQUESTER", "REFERRAL_DESTINATION", "HOST_FACILITY", "STAFF_CONTRIBUTING"],
    crossBorderParticipation: true,
    allowedCrossBorderPrivileges: [
      "PROVIDER_TO_PROVIDER_ADVICE_ONLY",
      "MDT_ONLY",
      "TEACHING_SUPERVISION_ONLY",
      "BLOCKED_PENDING_REGULATORY_APPROVAL",
    ],
    billingModels: ["PUBLIC_SERVICE", "MEDICAL_AID_REIMBURSED", "PRIVATE_SELF_PAY"],
    sessionModeIds: ["provider-to-provider-advice", "mdt-case-review", "case-presentation", "diagnostics-review"],
    queues: [
      queue("specialist-request", "General Specialist Request Queue"),
      queue("p2p-advice", "Provider-to-Provider Advice Queue"),
      queue("mdt-review", "MDT Review Queue"),
      queue("procedure-support", "Procedure/Theatre Support Queue"),
      queue("urgent-escalation", "Urgent Specialist Escalation Queue"),
    ],
    substrateStatus: "ROUTABLE_VIA_EXISTING_SEAMS",
    currentRoutingSeams: [TELECONSULT_SEAM("SPECIALIST_POOL")],
  },
  {
    id: "vh-maternal-newborn",
    name: "Virtual Maternal and Newborn Unit",
    level: "PROGRAMME",
    operatingModel: "PROGRAMME_OWNED",
    operatingAuthority: "MoHCC maternal & newborn health programme",
    regulatoryStatus: "POLICY_CONFIGURABLE_PENDING_DETERMINATION",
    purpose:
      "Maternal, newborn, pregnancy, postnatal, midwifery, neonatal and high-risk follow-up services with danger-sign escalation.",
    catchment: "National programme, facility spokes",
    serviceLines: [
      "ANC teleconsultation",
      "PNC follow-up",
      "High-risk pregnancy monitoring",
      "Neonatal review",
      "Midwife-to-doctor escalation",
      "Emergency obstetric advisory",
    ],
    providerPoolCadres: ["Midwives", "Obstetricians", "Neonatologists", "Medical officers"],
    linkedFacilityRoles: ["ACCESS_POINT", "REQUESTER", "REFERRAL_DESTINATION", "STAFF_CONTRIBUTING"],
    crossBorderParticipation: false,
    allowedCrossBorderPrivileges: [],
    billingModels: ["PUBLIC_SERVICE", "PROGRAMME_FUNDED", "DONOR_PARTNER_SUPPORTED"],
    sessionModeIds: ["direct-clinical-encounter", "provider-to-provider-advice", "emergency-advisory"],
    queues: [
      queue("anc-teleconsult", "ANC Teleconsult Queue"),
      queue("pnc-followup", "PNC Follow-up Queue"),
      queue("high-risk-pregnancy", "High-Risk Pregnancy Queue"),
      queue("neonatal-review", "Neonatal Review Queue"),
      queue("emergency-obstetric", "Emergency Obstetric/Newborn Advisory Queue"),
    ],
    substrateStatus: "ROUTABLE_VIA_EXISTING_SEAMS",
    currentRoutingSeams: [TELECONSULT_SEAM("MATERNAL_NEWBORN")],
  },
  {
    id: "vh-mental-health",
    name: "Virtual Mental Health Unit",
    level: "PROGRAMME",
    operatingModel: "PROGRAMME_OWNED",
    operatingAuthority: "MoHCC mental health programme",
    regulatoryStatus: "POLICY_CONFIGURABLE_PENDING_DETERMINATION",
    purpose:
      "Counselling, psychiatric review, follow-up, crisis routing, medication review, psychosocial support and continuity of care — with sensitive-case governance.",
    catchment: "National programme",
    serviceLines: [
      "Counselling",
      "Psychiatric review",
      "Crisis escalation",
      "Medication review",
      "Follow-up/continuity",
    ],
    providerPoolCadres: ["Psychiatrists", "Psychologists", "Counsellors", "Mental health nurses"],
    linkedFacilityRoles: ["ACCESS_POINT", "REQUESTER", "REFERRAL_DESTINATION"],
    crossBorderParticipation: false,
    allowedCrossBorderPrivileges: [],
    billingModels: ["PUBLIC_SERVICE", "PROGRAMME_FUNDED"],
    sessionModeIds: ["direct-clinical-encounter", "emergency-advisory", "case-notes-review"],
    queues: [
      queue("mh-teleconsult", "General Mental Health Teleconsult Queue"),
      queue("counselling", "Counselling Queue"),
      queue("psychiatric-review", "Psychiatric Review Queue"),
      queue("crisis-escalation", "Crisis/Urgent Escalation Queue"),
      queue("medication-review", "Medication Review Queue"),
    ],
    substrateStatus: "CONFIGURED_AWAITING_SUBSTRATE",
    currentRoutingSeams: [],
  },
  {
    id: "vh-chronic-disease",
    name: "Virtual Chronic Disease Clinic",
    level: "PROGRAMME",
    operatingModel: "PROGRAMME_OWNED",
    operatingAuthority: "MoHCC NCD programme",
    regulatoryStatus: "POLICY_CONFIGURABLE_PENDING_DETERMINATION",
    purpose:
      "Long-term condition management: medication follow-up, adherence support, results review, monitoring and continuity of care.",
    catchment: "National programme",
    serviceLines: [
      "Hypertension",
      "Diabetes",
      "Asthma/COPD",
      "Epilepsy",
      "Medication adherence",
      "Results review",
    ],
    providerPoolCadres: ["Medical officers", "Nurses", "Pharmacy advisers"],
    linkedFacilityRoles: ["ACCESS_POINT", "REQUESTER", "REFERRAL_DESTINATION", "DISPENSING_SITE"],
    crossBorderParticipation: false,
    allowedCrossBorderPrivileges: [],
    billingModels: ["PUBLIC_SERVICE", "MEDICAL_AID_REIMBURSED", "SUBSCRIPTION_CONTINUITY_CARE"],
    sessionModeIds: ["direct-clinical-encounter", "diagnostics-review"],
    queues: [
      queue("ncd-followup", "NCD Tele-follow-up Queue"),
      queue("results-review", "Results Review Queue"),
      queue("medication-review", "Medication Review Queue"),
      queue("abnormal-observation", "Abnormal Observation Escalation Queue"),
      queue("missed-followup", "Missed Follow-up Queue"),
    ],
    substrateStatus: "CONFIGURED_AWAITING_SUBSTRATE",
    currentRoutingSeams: [],
  },
  {
    id: "vh-radiology-diagnostics",
    name: "Virtual Radiology/Diagnostics Review Centre",
    level: "SPECIALIST",
    operatingModel: "NETWORKED",
    operatingAuthority: "MoHCC diagnostics network",
    regulatoryStatus: "POLICY_CONFIGURABLE_PENDING_DETERMINATION",
    purpose:
      "Remote interpretation, imaging/lab review, diagnostic escalation, PACS/DICOM-linked case review and specialist diagnostic recommendations.",
    catchment: "National, hub-and-spoke",
    serviceLines: [
      "Imaging review",
      "Radiology opinion",
      "Lab result interpretation",
      "Diagnostic MDT",
      "Facility diagnostic support",
    ],
    providerPoolCadres: ["Radiologists", "Pathologists", "Diagnostics reviewers"],
    linkedFacilityRoles: ["REQUESTER", "DIAGNOSTICS_SITE", "STAFF_CONTRIBUTING"],
    crossBorderParticipation: true,
    allowedCrossBorderPrivileges: [
      "PROVIDER_TO_PROVIDER_ADVICE_ONLY",
      "MDT_ONLY",
      "BLOCKED_PENDING_REGULATORY_APPROVAL",
    ],
    billingModels: ["PUBLIC_SERVICE", "MEDICAL_AID_REIMBURSED"],
    sessionModeIds: ["diagnostics-review", "mdt-case-review", "provider-to-provider-advice"],
    queues: [
      queue("imaging-review", "Imaging Review Queue"),
      queue("urgent-imaging", "Urgent Imaging Review Queue"),
      queue("lab-interpretation", "Lab Interpretation Queue"),
      queue("diagnostic-mdt", "Diagnostic MDT Queue"),
    ],
    substrateStatus: "ROUTABLE_VIA_EXISTING_SEAMS",
    currentRoutingSeams: [TELECONSULT_SEAM("RADIOLOGY_DIAGNOSTICS")],
  },
  {
    id: "vh-icu-support",
    name: "Virtual ICU Support Desk",
    level: "SPECIALIST",
    operatingModel: "NETWORKED",
    operatingAuthority: "MoHCC critical care network",
    regulatoryStatus: "POLICY_CONFIGURABLE_PENDING_DETERMINATION",
    purpose:
      "High-acuity remote support: ICU, HDU, ward deterioration, emergency stabilisation, post-operative critical support.",
    catchment: "National, facility spokes",
    serviceLines: [
      "Urgent ICU support",
      "Ward deterioration escalation",
      "Critical care advice",
      "Post-op critical review",
    ],
    providerPoolCadres: ["Intensivists", "Anaesthetists", "Critical care nurses"],
    linkedFacilityRoles: ["REQUESTER", "REFERRAL_DESTINATION", "STAFF_CONTRIBUTING"],
    crossBorderParticipation: true,
    allowedCrossBorderPrivileges: ["EMERGENCY_ADVISORY_ONLY", "BLOCKED_PENDING_REGULATORY_APPROVAL"],
    billingModels: ["PUBLIC_SERVICE", "EMERGENCY_DISASTER_FREE"],
    sessionModeIds: ["emergency-advisory", "hybrid-encounter-support", "provider-to-provider-advice"],
    queues: [
      queue("urgent-icu", "Urgent ICU Support Queue"),
      queue("ward-deterioration", "Ward Deterioration Queue"),
      queue("critical-advice", "Critical Care Advice Queue"),
    ],
    substrateStatus: "CONFIGURED_AWAITING_SUBSTRATE",
    currentRoutingSeams: [],
  },
  {
    id: "vh-emergency-advisory",
    name: "Virtual Emergency Advisory Unit",
    level: "EMERGENCY",
    operatingModel: "EMERGENCY_ACTIVATED",
    operatingAuthority: "MoHCC emergency care / Daidzai coordination",
    regulatoryStatus: "POLICY_CONFIGURABLE_PENDING_DETERMINATION",
    purpose:
      "Rapid-response telemedicine: urgent triage, emergency advice, referral routing, dispatch support, facility escalation with SLA timers.",
    catchment: "National, 24/7 when activated",
    serviceLines: [
      "Emergency tele-triage",
      "Facility emergency advice",
      "Transfer/referral escalation",
      "Post-emergency follow-up",
    ],
    providerPoolCadres: ["Emergency physicians", "Medical officers", "Triage nurses"],
    linkedFacilityRoles: ["ACCESS_POINT", "REQUESTER", "REFERRAL_DESTINATION"],
    crossBorderParticipation: true,
    allowedCrossBorderPrivileges: ["EMERGENCY_ADVISORY_ONLY", "BLOCKED_PENDING_REGULATORY_APPROVAL"],
    billingModels: ["EMERGENCY_DISASTER_FREE", "PUBLIC_SERVICE"],
    sessionModeIds: ["emergency-advisory", "hybrid-encounter-support"],
    queues: [
      queue("emergency-tele-triage", "Emergency Tele-triage Queue"),
      queue("facility-emergency-advice", "Facility Emergency Advice Queue"),
      queue("transfer-escalation", "Transfer/Referral Escalation Queue"),
      queue("unanswered-emergency", "Unanswered Emergency Queue"),
    ],
    substrateStatus: "CONFIGURED_AWAITING_SUBSTRATE",
    currentRoutingSeams: [],
  },
  {
    id: "vh-rehabilitation",
    name: "Virtual Rehabilitation Clinic",
    level: "PROGRAMME",
    operatingModel: "PROGRAMME_OWNED",
    operatingAuthority: "MoHCC rehabilitation programme",
    regulatoryStatus: "POLICY_CONFIGURABLE_PENDING_DETERMINATION",
    purpose:
      "Rehabilitation, physiotherapy, occupational therapy, speech therapy, assistive-device follow-up and functional recovery monitoring.",
    catchment: "National programme",
    serviceLines: [
      "Physiotherapy follow-up",
      "Occupational therapy",
      "Speech/swallow review",
      "Post-stroke rehab",
      "Assistive device review",
    ],
    providerPoolCadres: ["Physiotherapists", "Occupational therapists", "Speech therapists"],
    linkedFacilityRoles: ["ACCESS_POINT", "REQUESTER", "REFERRAL_DESTINATION"],
    crossBorderParticipation: false,
    allowedCrossBorderPrivileges: [],
    billingModels: ["PUBLIC_SERVICE", "MEDICAL_AID_REIMBURSED"],
    sessionModeIds: ["direct-clinical-encounter"],
    queues: [
      queue("physio-followup", "Physiotherapy Follow-up Queue"),
      queue("ot", "Occupational Therapy Queue"),
      queue("post-stroke", "Post-Stroke Rehab Queue"),
      queue("assistive-device", "Assistive Device Review Queue"),
    ],
    substrateStatus: "CONFIGURED_AWAITING_SUBSTRATE",
    currentRoutingSeams: [],
  },
  {
    id: "vh-community-followup",
    name: "Virtual Community Follow-up Unit",
    level: "COMMUNITY",
    operatingModel: "PROGRAMME_OWNED",
    operatingAuthority: "MoHCC community health / CHW programme",
    regulatoryStatus: "POLICY_CONFIGURABLE_PENDING_DETERMINATION",
    purpose:
      "Community-based care: CHW-supported follow-up, home monitoring, defaulter tracing, outreach continuity, facility-community linkage.",
    catchment: "National, district/community spokes",
    serviceLines: [
      "CHW follow-up",
      "Home-based care monitoring",
      "Missed appointment tracing",
      "Community escalation",
      "Facility handover",
    ],
    providerPoolCadres: ["CHWs", "Community nurses", "Follow-up coordinators"],
    linkedFacilityRoles: ["ACCESS_POINT", "REQUESTER", "REFERRAL_DESTINATION"],
    crossBorderParticipation: false,
    allowedCrossBorderPrivileges: [],
    billingModels: ["PUBLIC_SERVICE", "PROGRAMME_FUNDED", "DONOR_PARTNER_SUPPORTED"],
    sessionModeIds: ["direct-clinical-encounter", "provider-to-provider-advice"],
    queues: [
      queue("chw-followup", "CHW Follow-up Queue"),
      queue("home-monitoring", "Home-Based Care Monitoring Queue"),
      queue("missed-appointment", "Missed Appointment Queue"),
      queue("facility-handover", "Facility Handover Queue"),
    ],
    substrateStatus: "CONFIGURED_AWAITING_SUBSTRATE",
    currentRoutingSeams: [],
  },
  {
    id: "vh-learning-supervision",
    name: "Virtual Learning and Supervision Hospital",
    level: "LEARNING",
    operatingModel: "NETWORKED",
    operatingAuthority: "MoHCC training directorate with Fundo",
    regulatoryStatus: "POLICY_CONFIGURABLE_PENDING_DETERMINATION",
    purpose:
      "Governed virtual teaching, mentorship, supervision, case review and CPD — never uncontrolled recording or casual observation of patients.",
    catchment: "National",
    serviceLines: [
      "Supervised clinical sessions",
      "Case review",
      "MDT teaching",
      "Procedure observation",
      "Learning artefact governance",
    ],
    providerPoolCadres: ["Supervisors", "Mentors", "Learners (restricted roles)"],
    linkedFacilityRoles: ["REQUESTER", "STAFF_CONTRIBUTING"],
    crossBorderParticipation: true,
    allowedCrossBorderPrivileges: ["TEACHING_SUPERVISION_ONLY", "BLOCKED_PENDING_REGULATORY_APPROVAL"],
    billingModels: ["TEACHING_SUPERVISION_FUNDED", "PUBLIC_SERVICE"],
    sessionModeIds: ["teaching-supervision", "case-presentation", "audit-mortality-review"],
    queues: [
      queue("supervision-request", "Supervision Request Queue"),
      queue("teaching-session", "Teaching Session Queue"),
      queue("case-review", "Case Review Queue"),
      queue("artefact-approval", "Learning Artefact Approval Queue"),
    ],
    substrateStatus: "ROUTABLE_VIA_EXISTING_SEAMS",
    currentRoutingSeams: [
      {
        routingType: "SPECIALTY_POOL",
        targetRef: "LEARNING_SUPERVISION",
        note: "LEARNING_LIVE session template exists (W0); supervision requests routable as teleconsults today.",
      },
    ],
  },
];

/** One provincial virtual hospital per province — instances of one model. */
export const PROVINCIAL_VIRTUAL_HOSPITALS: VirtualHospitalDefinition[] = ZW_PROVINCES.map(
  (province) => ({
    id: `vh-provincial-${province.code.toLowerCase()}`,
    name: `${province.name} Provincial Virtual Hospital`,
    level: "PROVINCIAL" as const,
    operatingModel: "NETWORKED" as const,
    operatingAuthority: `${province.name} provincial health authority`,
    regulatoryStatus: "POLICY_CONFIGURABLE_PENDING_DETERMINATION" as const,
    purpose:
      "Decentralised telemedicine operations: local facility support, provincial clinical routing, provincial virtual queues and management.",
    catchment: `${province.name} province — linked districts and facilities`,
    serviceLines: [
      "Provincial general teleconsult",
      "Facility-to-province support",
      "District escalation",
      "Provincial tele-follow-up",
      "Referral handover",
    ],
    providerPoolCadres: ["Medical officers", "Nurses", "Provincial specialists"],
    linkedFacilityRoles: [
      "ACCESS_POINT",
      "REQUESTER",
      "REFERRAL_DESTINATION",
      "STAFF_CONTRIBUTING",
    ] as LinkedFacilityRole[],
    crossBorderParticipation: false,
    allowedCrossBorderPrivileges: [],
    billingModels: ["PUBLIC_SERVICE"] as BillingModel[],
    sessionModeIds: ["direct-clinical-encounter", "provider-to-provider-advice"],
    queues: [
      queue("provincial-general", "Provincial General Teleconsult Queue"),
      queue("facility-to-province", "Facility-to-Province Support Queue"),
      queue("district-escalation", "District Escalation Queue"),
      queue("provincial-followup", "Provincial Tele-follow-up Queue"),
    ],
    substrateStatus: "CONFIGURED_AWAITING_SUBSTRATE" as const,
    currentRoutingSeams: [],
    provinceCode: province.code,
  }),
);

export const ALL_VIRTUAL_HOSPITALS: VirtualHospitalDefinition[] = [
  ...STRATEGIC_VIRTUAL_HOSPITALS,
  ...PROVINCIAL_VIRTUAL_HOSPITALS,
];

export function getVirtualHospital(id: string): VirtualHospitalDefinition | undefined {
  return ALL_VIRTUAL_HOSPITALS.find((vh) => vh.id === id);
}

export const OPERATING_MODEL_LABELS: Record<VirtualHospitalOperatingModel, string> = {
  HOSTED: "Hosted by a physical facility",
  NETWORKED: "Networked across facilities",
  PROGRAMME_OWNED: "Programme-owned service line",
  EMERGENCY_ACTIVATED: "Emergency/disaster activated",
  DIASPORA_ENABLED: "Diaspora/cross-border enabled",
};

export const SUBSTRATE_STATUS_LABELS: Record<SubstrateStatus, string> = {
  CONFIGURED_AWAITING_SUBSTRATE: "Configured — awaiting backend substrate",
  ROUTABLE_VIA_EXISTING_SEAMS: "Routable today via teleconsult routing",
  OPERATIONAL: "Operational",
};

export const LINKED_FACILITY_ROLE_LABELS: Record<LinkedFacilityRole, string> = {
  ACCESS_POINT: "Access point",
  REQUESTER: "Requester",
  REFERRAL_DESTINATION: "Referral destination",
  DIAGNOSTICS_SITE: "Diagnostics site",
  DISPENSING_SITE: "Dispensing site",
  PROCEDURE_SITE: "Procedure site",
  HOST_FACILITY: "Host facility (explicit)",
  STAFF_CONTRIBUTING: "Staff-contributing (explicit mapping only)",
};
