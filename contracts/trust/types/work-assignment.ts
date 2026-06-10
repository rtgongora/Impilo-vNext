/**
 * WorkAssignment — maps to FHIR PractitionerRole conceptually.
 */

export type WorkAssignmentSubjectType =
  | "provider_worker"
  | "marketplace_actor"
  | "regulator"
  | "system_admin";

export type WorkAssignmentStatus =
  | "draft"
  | "pending_approval"
  | "pending_assumption_of_duty"
  | "active"
  | "active_temporary"
  | "active_restricted"
  | "suspended"
  | "ended"
  | "expired"
  | "revoked"
  | "rejected"
  | "transferred"
  | "under_review";

export const ACTIVE_WORK_ASSIGNMENT_STATUSES: ReadonlySet<WorkAssignmentStatus> = new Set([
  "active",
  "active_temporary",
  "active_restricted",
]);

export interface WorkAssignment {
  assignmentId: string;
  subjectId: string;
  subjectType: WorkAssignmentSubjectType;
  providerWorkerId?: string;
  organisationId?: string;
  facilityId?: string;
  facilityName?: string;
  locationId?: string;
  jurisdictionId?: string;
  contextType: string;
  workspaceId?: string;
  roleTemplateId?: string;
  roleName?: string;
  assignmentType: string;
  assignmentStatus: WorkAssignmentStatus;
  assumptionOfDutyStatus?: "pending" | "confirmed" | "not_required";
  startDate?: string;
  endDate?: string;
  approver?: string;
  approvalEvidence?: Record<string, unknown>;
  sourceWorkspace?: string;
  startupRoute?: string;
  policyDecision?: "allow" | "limited" | "deny";
  auditMetadata?: Record<string, unknown>;
  provenanceMetadata?: Record<string, unknown>;
  /** Employer organisation type for public-sector assignment gating (HSC doctrine). */
  employerOrganisationType?: string;
}

export function isActiveWorkAssignment(assignment: Pick<WorkAssignment, "assignmentStatus">): boolean {
  return ACTIVE_WORK_ASSIGNMENT_STATUSES.has(assignment.assignmentStatus);
}

export function normalizeWorkAssignment(raw: Record<string, unknown>): WorkAssignment | null {
  const assignmentId = String(raw.assignmentId ?? raw.id ?? "");
  if (!assignmentId) return null;
  const statusRaw = String(raw.assignmentStatus ?? raw.status ?? "draft")
    .toLowerCase()
    .replace(/[\s-]+/g, "_") as WorkAssignmentStatus;
  return {
    assignmentId,
    subjectId: String(raw.subjectId ?? raw.subject_id ?? ""),
    subjectType: (raw.subjectType ?? raw.subject_type ?? "provider_worker") as WorkAssignmentSubjectType,
    providerWorkerId: raw.providerWorkerId ? String(raw.providerWorkerId) : raw.provider_worker_id ? String(raw.provider_worker_id) : undefined,
    organisationId: raw.organisationId ? String(raw.organisationId) : undefined,
    facilityId: raw.facilityId ? String(raw.facilityId) : raw.facility_id ? String(raw.facility_id) : undefined,
    facilityName: raw.facilityName ? String(raw.facilityName) : raw.facility_name ? String(raw.facility_name) : undefined,
    locationId: raw.locationId ? String(raw.locationId) : undefined,
    jurisdictionId: raw.jurisdictionId ? String(raw.jurisdictionId) : undefined,
    contextType: String(raw.contextType ?? raw.context_type ?? "facility_clinical"),
    workspaceId: raw.workspaceId ? String(raw.workspaceId) : raw.workspace_id ? String(raw.workspace_id) : undefined,
    roleTemplateId: raw.roleTemplateId ? String(raw.roleTemplateId) : raw.role_template_id ? String(raw.role_template_id) : undefined,
    roleName: raw.roleName ? String(raw.roleName) : raw.role_name ? String(raw.role_name) : undefined,
    assignmentType: String(raw.assignmentType ?? raw.assignment_type ?? "facility_assignment"),
    assignmentStatus: statusRaw,
    assumptionOfDutyStatus: raw.assumptionOfDutyStatus as WorkAssignment["assumptionOfDutyStatus"],
    startDate: raw.startDate ? String(raw.startDate) : undefined,
    endDate: raw.endDate ? String(raw.endDate) : undefined,
    approver: raw.approver ? String(raw.approver) : undefined,
    startupRoute: raw.startupRoute ? String(raw.startupRoute) : undefined,
    policyDecision: raw.policyDecision as WorkAssignment["policyDecision"],
  };
}

export function mapGovernanceAssignmentNode(node: Record<string, unknown>): WorkAssignment | null {
  const status = String(node.status ?? node.assignmentStatus ?? "draft").toUpperCase();
  const mappedStatus = status === "ACTIVE" ? "active" : status.toLowerCase().replace(/[\s-]+/g, "_");
  return normalizeWorkAssignment({
    assignmentId: node.assignmentId ?? node.id,
    subjectId: node.subjectId,
    subjectType: node.subjectType ?? "provider_worker",
    providerWorkerId: node.providerWorkerId ?? node.providerId,
    organisationId: node.organisationId,
    facilityId: node.facilityId ?? node.targetId,
    facilityName: node.facilityName ?? node.targetName,
    contextType: node.contextType ?? node.workContextType ?? "facility_clinical",
    workspaceId: node.workspaceId ?? node.roleCode,
    roleTemplateId: node.roleTemplateId ?? node.roleCode,
    roleName: node.roleName ?? node.roleDisplayName,
    assignmentType: node.assignmentType ?? "facility_assignment",
    assignmentStatus: mappedStatus,
    assumptionOfDutyStatus: node.assumptionOfDutyStatus,
    startupRoute: node.startupRoute,
  });
}
