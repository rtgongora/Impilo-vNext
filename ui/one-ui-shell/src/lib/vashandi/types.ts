/**
 * Vashandi operational workforce types — aligned with BFF VashandiController wrap envelope.
 */

export type VashandiIntegrationStatus =
  | "LIVE"
  | "UPSTREAM_UNAVAILABLE"
  | "DEGRADED"
  | "DENIED"
  | "PENDING_BACKEND";

export type VashandiActionStatus =
  | "completed"
  | "denied"
  | "upstream_unavailable"
  | "degraded"
  | "pending_backend";

export type WorkforceStatus =
  | "active"
  | "pending_appointment"
  | "on_leave"
  | "transferred_pending_assumption"
  | "seconded"
  | "suspended"
  | "dismissed"
  | "retired"
  | "resigned"
  | "contract_expired"
  | "under_review"
  | "offboarded"
  | string;

export type AssignmentStatus =
  | "draft"
  | "requested"
  | "prechecked"
  | "pending_approval"
  | "approved"
  | "active"
  | "suspended"
  | "ended"
  | "expired"
  | "rejected"
  | string;

export type RosterStatus = "draft" | "pending_approval" | "approved" | "published" | "archived" | string;

export type ShiftStatus =
  | "scheduled"
  | "confirmed"
  | "checked_in"
  | "late"
  | "missed"
  | "checked_out"
  | "completed"
  | "cancelled"
  | "swapped"
  | "covered"
  | string;

export type AccessRiskSeverity = "low" | "medium" | "high" | "critical" | string;

export type AccessRiskStatus = "open" | "resolved" | "revoked" | string;

export interface VashandiPolicyDecision {
  allowed?: boolean;
  policyPackage?: string;
  policyVersion?: string;
  decisionId?: string;
  warnings?: string[];
  approvalsRequired?: string[];
}

export interface VashandiActionResponse<TData = unknown> {
  success: boolean;
  status: VashandiActionStatus | string;
  requestId?: string;
  correlationId?: string;
  integrationStatus?: VashandiIntegrationStatus | string;
  integrationMessage?: string;
  auditStatus?: string;
  auditEventId?: string;
  auditCorrelationId?: string;
  policyDecision?: VashandiPolicyDecision;
  friendlyTitle?: string;
  friendlyMessage?: string;
  blockedReason?: string;
  data?: TData;
}

export interface WorkforceProfile {
  id: string;
  tenantId?: string;
  healthId?: string;
  providerWorkerId?: string;
  keycloakUserId?: string;
  primaryOrganisationId?: string;
  primaryEmployerOrganisationId?: string;
  workerType?: string;
  profession?: string;
  cadre?: string;
  currentStatus?: WorkforceStatus;
  accessRiskStatus?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface WorkforceMembership {
  id: string;
  workforceProfileId: string;
  organisationId: string;
  organisationType?: string;
  membershipType?: string;
  status?: string;
  effectiveDate?: string;
  expiryDate?: string;
  source?: string;
}

export interface WorkforceAssignment {
  id: string;
  workforceProfileId: string;
  assignmentType?: string;
  organisationId?: string;
  facilityId?: string;
  departmentId?: string;
  unitId?: string;
  programmeId?: string;
  workspaceId?: string;
  roleTemplateId?: string;
  supervisorProfileId?: string;
  status?: AssignmentStatus;
  startDate?: string;
  endDate?: string;
  sourceAuthority?: string;
  eligibilityStatus?: string;
  opaDecisionId?: string;
  /**
   * Identity-program (D-P7): how the person is engaged at this posting.
   * PERMANENT | ROTATION | LOCUM | OUTREACH | TELEMED | SPECIALIST_POOL |
   * SUPERVISORY | TRAINING. Non-PERMANENT engagements are end-dated and swept to
   * `ended` on expiry (which tears down the matching WORK_CONTEXT token).
   */
  engagementType?: string;
}

/** Engagement types (D-P7). A non-PERMANENT engagement must be end-dated. */
export const ENGAGEMENT_TYPES = [
  "PERMANENT",
  "ROTATION",
  "LOCUM",
  "OUTREACH",
  "TELEMED",
  "SPECIALIST_POOL",
  "SUPERVISORY",
  "TRAINING",
] as const;

export type EngagementType = (typeof ENGAGEMENT_TYPES)[number];

export interface Roster {
  id: string;
  organisationId?: string;
  facilityId?: string;
  departmentId?: string;
  unitId?: string;
  rosterType?: string;
  periodStart?: string;
  periodEnd?: string;
  status?: RosterStatus;
  createdBy?: string;
  approvedBy?: string;
}

export interface Shift {
  id: string;
  rosterId: string;
  workforceProfileId: string;
  assignmentId?: string;
  shiftType?: string;
  startTime?: string;
  endTime?: string;
  locationType?: string;
  facilityId?: string;
  virtualPoolId?: string;
  status?: ShiftStatus;
  checkInRequired?: boolean;
}

/** A virtual (on-call) pool summary — a distinct `vsh_shift.virtual_pool_id` with coverage. */
export interface VirtualPool {
  poolId: string;
  shiftCount?: number;
  onDutyCount?: number;
  nextShiftStart?: string | null;
}

export interface VirtualPoolDutyMember {
  shiftId: string;
  workforceProfileId: string;
  shiftType?: string;
  status?: ShiftStatus;
  startTime?: string;
  endTime?: string;
}

export interface VirtualPoolOnDuty {
  poolId: string;
  asOf?: string;
  onDutyCount?: number;
  onDuty?: VirtualPoolDutyMember[];
  nextShiftStart?: string | null;
}

export interface AttendanceEvent {
  id: string;
  shiftId?: string;
  workforceProfileId: string;
  eventType?: string;
  eventTime?: string;
  checkInMode?: string;
  deviceId?: string;
  supervisorConfirmedBy?: string;
  offline?: boolean;
}

export interface LeaveAvailability {
  id: string;
  workforceProfileId: string;
  leaveType?: string;
  status?: string;
  startDate?: string;
  endDate?: string;
  approvedBy?: string;
  sourceAuthority?: string;
}

export interface AccessRisk {
  id: string;
  workforceProfileId: string;
  riskType?: string;
  severity?: AccessRiskSeverity;
  detectedAt?: string;
  status?: AccessRiskStatus;
  recommendedAction?: string;
  resolvedBy?: string;
  resolvedAt?: string;
}

export interface WorkforceEligibilityResult {
  assignmentId?: string;
  overallStatus?: string;
  opaDecisionId?: string;
  checks?: Array<Record<string, unknown>>;
  message?: string;
}

export interface StaffingAnalyticsResponse {
  counts?: Record<string, number>;
  status?: string;
}

export interface RosterCoverageResponse {
  approvedRosters?: number;
  scheduledShifts?: number;
  checkedInShifts?: number;
  facilityScheduledShifts?: number;
  status?: string;
}

export interface AttendanceAnalyticsResponse {
  checkIns?: number;
  checkOuts?: number;
  supervisorConfirmed?: number;
  status?: string;
}

export interface AccessRiskAnalyticsResponse {
  openRisks?: number;
  criticalRisks?: number;
  highRisks?: number;
  status?: string;
}

export interface VashandiPrecheckRequest {
  requestedAction: string;
  sourcePage?: string;
  targetWorkforceProfileId?: string;
  targetAssignmentId?: string;
  targetFacilityId?: string;
  roleTemplate?: string;
  requestedScope?: string;
  context?: Record<string, unknown>;
}

export interface CreateAssignmentRequest {
  workforceProfileId: string;
  assignmentType: string;
  organisationId?: string;
  facilityId?: string;
  departmentId?: string;
  unitId?: string;
  programmeId?: string;
  workspaceId?: string;
  roleTemplateId?: string;
  supervisorProfileId?: string;
  startDate?: string;
  endDate?: string;
  sourceAuthority?: string;
  engagementType?: string;
}

export interface CheckInRequest {
  shiftId: string;
  workforceProfileId: string;
  eventTime?: string;
  checkInMode?: string;
  deviceId?: string;
  offline?: boolean;
}

export interface CheckOutRequest {
  /** Rostered shift UUID — omit for ad-hoc (self-service) attendance. */
  shiftId?: string;
  workforceProfileId: string;
  eventTime?: string;
  checkInMode?: string;
  offline?: boolean;
}

export interface ApiClientErrorLike {
  status?: number;
  statusCode?: number;
  error?: { code?: string; message?: string };
}
