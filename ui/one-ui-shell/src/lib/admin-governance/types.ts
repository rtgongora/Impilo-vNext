/**
 * Administration & Governance — shared BFF contract types.
 */

export type AdminGovernanceStatus =
  | "allowed"
  | "denied"
  | "pending"
  | "submitted"
  | "completed"
  | "pending_backend";

export interface PolicyDecision {
  allowed: boolean;
  policyPackage: string;
  policyVersion: string;
  decisionId: string;
  warnings?: string[];
  approvalsRequired?: string[];
}

export interface AdminGovernanceActionResponse {
  status: AdminGovernanceStatus;
  requestId?: string;
  subjectId?: string;
  assignmentId?: string;
  friendlyTitle: string;
  friendlyMessage: string;
  allowedNextActions: string[];
  blockedReason?: string;
  policyDecision?: PolicyDecision;
  auditEventId?: string;
  auditStatus?: "ingested" | "queued" | "failed_non_blocking" | "pending_backend";
  auditCorrelationId?: string;
  integrationStatus?: "live" | "pending_backend";
  bootstrapAccountId?: string;
  bootstrapClosed?: boolean;
  roleExpiry?: string;
  payload?: Record<string, unknown>;
}

export interface OpaPrecheckRequest {
  requestedAction: string;
  sourcePage: string;
  targetOrganisationId?: string;
  targetSubjectId?: string;
  roleTemplate?: string;
  requestedScope?: string;
  requestedEnvironment?: string;
  requestedDataScope?: string;
  requestedDurationDays?: number;
  crossBorderFlag?: boolean;
  riskLevel?: string;
  context?: Record<string, unknown>;
}

export interface OnboardingFlowSubmission {
  organisationId?: string;
  targetHealthId?: string;
  targetProviderWorkerId?: string;
  roleTemplate?: string;
  requestedScope?: string;
  requestedEnvironment?: string;
  requestedDataScope?: string;
  requestedDurationDays?: number;
  crossBorderFlag?: boolean;
  purpose?: string;
  country?: string;
  profile?: Record<string, unknown>;
}

export interface AccessRequest {
  id: string;
  requestType: string;
  status: string;
  requesterId: string;
  requesterName?: string;
  organisationId?: string;
  organisationName?: string;
  targetSubjectId?: string;
  requestedRole?: string;
  requestedScope?: string;
  requestedEnvironment?: string;
  requestedDataScope?: string;
  riskLevel?: string;
  policyPrecheckResult?: string;
  approvalsRequired?: string[];
  auditTrail?: Array<Record<string, unknown>>;
  createdAt?: string;
  updatedAt?: string;
  payload?: Record<string, unknown>;
  sourceOfRecordStatus?: "workforce_governance" | "fallback_queue";
  fallbackRequestId?: string;
  durableRequestId?: string;
  downstreamCorrelationId?: string;
  reconciliationRequired?: boolean;
  lastSyncAttemptAt?: string;
  lastSyncStatus?: string;
}

export interface FacilityStaffAssignmentRequest {
  facilityId: string;
  providerWorkerId: string;
  departmentId?: string;
  workspaceId?: string;
  roleTemplate?: string;
  startDate?: string;
  endDate?: string;
  assumptionOfDutyRequired?: boolean;
}

export interface WorkAssignmentCreateRequest extends FacilityStaffAssignmentRequest {
  professionalStatus?: string;
  councilStatus?: string;
  hscStatus?: string;
  trainingWarnings?: string[];
}

export interface AuditEventEnvelope {
  id?: string;
  eventType: string;
  actorId?: string;
  occurredAt?: string;
  integrationStatus?: string;
}

export interface AdminGovernanceEnvelope<T> {
  data: {
    type: string;
    attributes: T;
  };
  meta?: {
    request_id?: string;
    correlation_id?: string;
  };
}

export interface AccessRequestListResponse {
  items: AccessRequest[];
}

export interface ApiClientErrorLike {
  error?: {
    code?: string;
    message?: string;
  };
  status?: number;
}

export interface OrganisationRecord {
  organisationId?: string;
  id?: string;
  organisationType?: string;
  legalName?: string;
  tradingName?: string;
  name?: string;
  organisationCode?: string;
  status?: string;
  lifecycleStatus?: string;
  verificationStatus?: string;
  metadata?: string | Record<string, unknown>;
}

export interface ProviderWorkerEligibility {
  providerWorkerId?: string;
  linkedHealthId?: string;
  fullName?: string;
  profession?: string;
  cadre?: string;
  licenceStatus?: string;
  professionalStatus?: string;
  councilStatusSummary?: Record<string, unknown>;
  hscEmploymentSummary?: Record<string, unknown>;
  trainingWarnings?: string[];
  eligibilityWarnings?: string[];
  blockedReasons?: string[];
  lookupAvailable?: boolean;
  integrationStatus?: string;
}

export interface LookupEnvelope<T = Record<string, unknown>> {
  integrationStatus: "live" | "pending_backend";
  friendlyMessage?: string | null;
  data: T;
}
