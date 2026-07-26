/**
 * Health OS Multi-Class Identifier Contract
 *
 * Defines the 6 identifier classes from the Health OS doctrine
 * (see docs/doctrine/health-os-doctrine.md §8).
 *
 * This file is the canonical TypeScript reference. Java counterparts
 * live in CompanionHeaders.java and TrustHeaders.java.
 */

// ── Actor IDs — who is involved ──────────────────────────────────────

/** Health ID — the canonical person anchor within the Health Operating System. */
export type HealthId = string;

/** Provider ID — regulated professional role identifier (VARAPI-issued). */
export type ProviderId = string;

/** Staff ID — organizational employment identifier. */
export type StaffId = string;

export type ActorType =
  | "PROVIDER"
  | "OPERATOR"
  | "CITIZEN"
  | "SYSTEM"
  | "CAREGIVER";

// ── Context IDs — where / under what organizational setting ──────────

export type FacilityId = string;
export type DepartmentId = string;
export type WardId = string;
export type WorkspaceId = string;
export type ProgrammeId = string;
export type ShiftId = string;

/** The full operational context of an action. */
export interface OperationalContext {
  facilityId: FacilityId;
  departmentId?: DepartmentId;
  wardId?: WardId;
  workspaceId?: WorkspaceId;
  programmeId?: ProgrammeId;
  shiftId?: ShiftId;
}

// ── Object IDs — what thing, resource, or catalog object ─────────────

export type ProductId = string;
export type MedicationId = string;
export type ServiceId = string;
export type AssetId = string;
export type EquipmentId = string;
export type DeviceId = string;
export type IoTDeviceId = string;
export type MarketplaceItemId = string;
export type WellnessSupportObjectId = string;

// ── Transaction IDs — specific operational or clinical action instances

export type PrescriptionId = string;
export type OrderId = string;
export type ReferralId = string;
export type AppointmentId = string;
export type ClaimId = string;
export type DispenseId = string;
export type PaymentId = string;

// ── Record IDs — persistent information objects ──────────────────────

export type ObservationId = string;
export type ConsentId = string;
export type DocumentId = string;
export type CarePlanId = string;
export type LabResultId = string;

// ── Event IDs — discrete events in the runtime ──────────────────────

export type AlertId = string;
export type NotificationId = string;
export type AuditEventId = string;
export type WorkflowEventId = string;
export type WellnessActivityEventId = string;
export type SleepEventId = string;
export type DietaryIntakeEventId = string;
export type IoTEventId = string;
export type ConversationalEventId = string;
export type PromptReminderEventId = string;
export type KnowledgeArtifactId = string;

// ── Purpose of Use ──────────────────────────────────────────────────

// Mirrors zw.gov.mohcc.impilo.tshepo.contracts.enums.PurposeOfUse exactly. TSHEPO's
// PolicyEngine denies any request whose purpose is outside this set (INVALID_PURPOSE),
// so a code that exists here but not in the Java enum is a request that cannot be made.
export type PurposeOfUse =
  | "TREATMENT"
  | "CARE_COORDINATION"
  | "PAYMENT"
  | "OPERATIONS"
  | "RESEARCH"
  | "PUBLIC_HEALTH"
  | "SELF_SERVICE"
  | "REGULATORY_DUTY"
  | "EMERGENCY"
  | "BREAK_GLASS"
  | "SYSTEM";

// ── Assurance Level ─────────────────────────────────────────────────

export type AssuranceLevel = "LOA1" | "LOA2" | "LOA3" | "LOA4";

// ── Access Mode ─────────────────────────────────────────────────────

export type AccessMode = "INTERNAL" | "EXTERNAL";

// ── Governed Action Context ─────────────────────────────────────────

/**
 * Full context required for a governed action per Health OS doctrine §11.
 * Not all fields are required for every action — the PolicyEngine determines
 * which dimensions apply based on the resource and operation.
 */
export interface GovernedActionContext {
  // 1. Person identity
  actorId: HealthId;
  // 2. Active role
  actorType: ActorType;
  // 3. Attached role identifier
  providerId?: ProviderId;
  staffId?: StaffId;
  // 4. Organizational affiliation
  tenantId: string;
  // 5. Facility or workspace context
  facilityId?: FacilityId;
  workspaceId?: WorkspaceId;
  // 6. Subject relationship
  subjectId?: HealthId;
  // 7. Purpose of use
  purposeOfUse: PurposeOfUse;
  // 8. Consent or legal basis
  consentRef?: string;
  // 9. Assurance level
  assuranceLevel?: AssuranceLevel;
  // 10. Workflow state (e.g. DRAFT, ACTIVE, DISCHARGED, SIGNED_OFF)
  workflowState?: string;
}

// ── Caregiver Linkage (Health OS §4, §5) ────────────────────────────

export type CaregiverLinkageType = "FAMILY" | "PROFESSIONAL" | "LEGAL" | "COMMUNITY";

export interface CaregiverLinkage {
  id: string;
  caregiverHealthId: HealthId;
  subjectHealthId: HealthId;
  relationship: string;
  linkageType: CaregiverLinkageType;
  grantedScopes: string[];
  consentRef?: string;
}

// ── Graduated Trust and Friction (Health OS §8) ─────────────────────

/** Friction must be proportionate to risk. */
export type FrictionLevel = "MINIMAL" | "LOW" | "STANDARD" | "ELEVATED" | "MAXIMUM";

// ── Progressive Identity Assurance (Health OS §10, §11) ─────────────

export type IdentityAssuranceState =
  | "SELF_REGISTERED"        // Self-registration, no proofing
  | "ASSISTED_REGISTRATION"  // Registered by authorized actor (CHW, facility)
  | "FACILITY_CONFIRMED"     // Confirmed at a facility with basic evidence
  | "REGISTRY_MATCHED"       // Matched to trusted registry (MOSIP, civil registry)
  | "COUNCIL_VALIDATED"      // Professional council validation (for providers)
  | "FULLY_VERIFIED";        // Full identity proofing complete

export interface TemporaryIdentity {
  tempId: string;
  assuranceState: IdentityAssuranceState;
  reason: string;
  permissionsAvailable: string[];
  upgradeRequirements: string[];
  eligibleUpgradePathways: string[];
  nextBestStep: string;
  createdBy: string;
  createdAt: string;
  expiresAt?: string;
}

// ── Header name constants (must match CompanionHeaders.java) ────────

export const HEALTH_OS_HEADERS = {
  // Mandatory
  TENANT_ID: "X-Tenant-ID",
  POD_ID: "X-Pod-ID",
  REQUEST_ID: "X-Request-ID",
  CORRELATION_ID: "X-Correlation-ID",
  // Actor identity
  ACTOR_ID: "X-Actor-ID",
  ACTOR_TYPE: "X-Actor-Type",
  PROVIDER_ID: "X-Provider-ID",
  // Context
  FACILITY_ID: "X-Facility-ID",
  DEPARTMENT_ID: "X-Department-ID",
  WARD_ID: "X-Ward-ID",
  WORKSPACE_ID: "X-Workspace-ID",
  PROGRAMME_ID: "X-Programme-ID",
  SHIFT_ID: "X-Shift-ID",
  // Governance
  PURPOSE_OF_USE: "X-Purpose-Of-Use",
  ASSURANCE_LEVEL: "X-Assurance-Level",
  SUBJECT_ID: "X-Subject-ID",
  ACCESS_MODE: "X-Access-Mode",
  // Workflow
  WORKFLOW_STATE: "X-Workflow-State",
  // Idempotency
  IDEMPOTENCY_KEY: "Idempotency-Key",
} as const;
