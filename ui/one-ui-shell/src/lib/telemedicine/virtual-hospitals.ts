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
  /**
   * Computed by the BFF from PCT materialisation state: LIVE only when a
   * materialised, active pct queue backs this definition. Never stored.
   */
  status: "AWAITING_BACKEND" | "LIVE";
  /** Runtime read-backs (present only when PCT stats are available). */
  depth?: number | null;
  oldestWaitingMinutes?: number | null;
  slaBreaches?: number | null;
  slaMinutes?: number | null;
}

/** Vashandi duty composition for the institution's primary pool. */
export interface VirtualPoolDuty {
  poolId: string;
  /** "true" | "false" | "UNKNOWN" — UNKNOWN when vashandi is unreachable. */
  status: "true" | "false" | "UNKNOWN";
  onDutyCount?: number;
  onDuty?: Array<{
    workforceProfileId: string | null;
    shiftType: string | null;
    status: string | null;
    startTime: string | null;
    endTime: string | null;
  }>;
  nextShiftStart?: string | null;
}

/** Full routing seam row (governance panel; includes inactive seams). */
export interface VirtualServiceRoutingSeam {
  id?: number;
  routingType: string;
  targetRef: string;
  note?: string | null;
  active: boolean;
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

  // ---- TUSO-backed governance/runtime fields (absent on static fallback) ----
  /** Fail-closed lifecycle: CONFIGURED → ACTIVATION_REQUESTED → ACTIVE → SUSPENDED. */
  lifecycleStatus?: "CONFIGURED" | "ACTIVATION_REQUESTED" | "ACTIVE" | "SUSPENDED" | null;
  activatedAt?: string | null;
  activatedBy?: string | null;
  /** Every seam incl. inactive ones (governance panel). */
  routingSeams?: VirtualServiceRoutingSeam[];
  /** The pool key (first active seam target_ref) the runtime composition used. */
  primaryPoolId?: string | null;
  /** True when PCT queue stats could not be read (queues stay AWAITING_BACKEND). */
  queueStatsDegraded?: boolean;
  /** Vashandi duty snapshot for the primary pool. */
  duty?: VirtualPoolDuty;
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

/**
 * The virtual-hospital DATA now lives in the TUSO virtual-service registry
 * (canonical document: contracts/telemedicine/virtual-hospitals.json, seeded
 * into tuso.virtual_service_* by tools/generate-virtual-service-seed.mjs).
 * UI surfaces read it through the BFF via useVirtualHospitals()
 * (@/hooks/queries/useTelemedicineOperatingModel) — the static
 * ALL_VIRTUAL_HOSPITALS constant has been deleted deliberately; only the
 * types, labels and province reference remain here.
 */

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
