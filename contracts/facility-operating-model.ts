/**
 * Impilo Facility Operating Model Contract
 *
 * Canonical architecture contract for how facility tiers, tenancy,
 * deployment pods, continuity requirements, and UX shape fit together.
 *
 * Key rule: provider identities are never infrastructure boundaries.
 * Deployment boundaries are expressed through tenant, pod, facility,
 * and workspace scopes.
 */

export type TenantId = string;
export type PodId = string;
export type FacilityId = string;
export type WorkspaceId = string;

/**
 * National facility tier taxonomy for Zimbabwe deployments.
 * Other jurisdictions may map their local tiers onto this model.
 */
export type FacilityTier =
  | "COMMUNITY"
  | "HEALTH_POST"
  | "CLINIC"
  | "POLYCLINIC"
  | "RURAL_HOSPITAL"
  | "DISTRICT_OR_MISSION_HOSPITAL"
  | "GENERAL_HOSPITAL"
  | "PROVINCIAL_OR_TERTIARY_HOSPITAL"
  | "CENTRAL_HOSPITAL"
  | "QUINARY_HOSPITAL"
  | "VIRTUAL_HOSPITAL";

/**
 * Runtime/deployment posture for a facility or cluster.
 */
export type FacilityDeploymentMode =
  | "CENTRAL_ONLY"
  | "SHARED_POD"
  | "DEDICATED_POD"
  | "EDGE_ASSISTED"
  | "VIRTUAL_ONLY";

/**
 * Degree to which care delivery must continue when upstream connectivity
 * or the national center is unavailable.
 */
export type ContinuityClass =
  | "CONNECTED_TOLERANT"
  | "LOCAL_EXECUTION_REQUIRED"
  | "EDGE_CRITICAL";

/**
 * Practical operating pattern for the facility.
 */
export type WorkflowArchetype =
  | "COMMUNITY_OUTREACH"
  | "PRIMARY_CARE"
  | "AMBULATORY_MULTI_SERVICE"
  | "HOSPITAL_INPATIENT"
  | "REFERRAL_AND_SPECIALIST"
  | "VIRTUAL_CARE_NETWORK";

/**
 * Typical service capabilities enabled at a facility.
 * This is intentionally additive: tiers set defaults, but actual facility
 * configuration decides the final enabled set.
 */
export interface FacilityCapabilityProfile {
  registration: boolean;
  triage: boolean;
  outpatient: boolean;
  emergency: boolean;
  inpatient: boolean;
  maternity: boolean;
  theatre: boolean;
  pharmacy: boolean;
  laboratory: boolean;
  imaging: boolean;
  telemedicine: boolean;
  outreach: boolean;
  publicHealth: boolean;
  billingAndCashier: boolean;
}

/**
 * Workspaces that shape the user experience and launch pathways.
 */
export interface FacilityWorkflowProfile {
  archetype: WorkflowArchetype;
  workspaceIds: WorkspaceId[];
  defaultLaunchActions: string[];
  patientFlowNotes: string[];
}

/**
 * Tenant is the legal and policy boundary.
 * A tenant may own one or more pods.
 */
export interface TenantBoundary {
  tenantId: TenantId;
  legalOwner: string;
  operatingModel: "SINGLE_OPERATOR" | "FEDERATED_MULTI_OPERATOR";
}

/**
 * Pod is the runtime/deployment boundary.
 * A pod may serve one major facility, a district cluster, an operator cluster,
 * or a virtual hospital network.
 */
export interface PodBoundary {
  podId: PodId;
  tenantId: TenantId;
  authorityModel: "NATIONAL_CONSUMER" | "LOCAL_EXECUTION" | "HYBRID";
  servedFacilityIds: FacilityId[];
  deploymentMode: FacilityDeploymentMode;
}

/**
 * Canonical facility record used for architecture and deployment planning.
 */
export interface FacilityOperatingProfile {
  facilityId: FacilityId;
  tenantId: TenantId;
  podId: PodId;
  tier: FacilityTier;
  deploymentMode: FacilityDeploymentMode;
  continuityClass: ContinuityClass;
  capabilityProfile: FacilityCapabilityProfile;
  workflowProfile: FacilityWorkflowProfile;
  /**
   * Major hospitals often justify dedicated pods.
   * Smaller sites typically inherit district/operator shared pods.
   */
  localExecutionRecommended: boolean;
}

/**
 * Architecture invariants that must remain true in all deployments.
 */
export const FACILITY_ARCHITECTURE_RULES = [
  "Provider identities are never deployment boundaries.",
  "Facility tier does not equal tenant boundary.",
  "Facility tier does not automatically equal pod boundary.",
  "If essential care must continue during central outage, local execution is required.",
  "National authoritative registries and local care execution remain distinct concerns.",
  "Tier sets defaults; facility capability and workflow profiles determine the actual runtime shape.",
] as const;

