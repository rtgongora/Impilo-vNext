import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface FacilityRegistrySummary {
  facilityId: number;
  facilityCode: string;
  name: string;
  facilityType: string;
  status: string;
  regulatoryStatus: string;
  registrationPathway: string | null;
  province: string | null;
  district: string | null;
  institutionFileNumber: string | null;
  latestCertificateNumber: string | null;
  latestCertificateExpiryDate: string | null;
  openApplications: number;
  overdueActions: number;
}

export interface FacilityRegistryPage {
  items: FacilityRegistrySummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface FacilityMaster {
  facilityId: number;
  facilityCode: string;
  name: string;
  aliasNames: string[];
  ownershipType: string | null;
  operatingEntity: string | null;
  facilityClass: string | null;
  facilityCategory: string | null;
  facilityType: string | null;
  legalStatus: string | null;
  registrationPathway: string | null;
  status: string;
  operationalStatus: string | null;
  regulatoryStatus: string;
  institutionFileNumber: string | null;
  province: string | null;
  district: string | null;
  city: string | null;
  ward: string | null;
  postalCode: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  latitude: number | null;
  longitude: number | null;
  metadata: Record<string, unknown> | null;
  createdAt: string;
  updatedAt: string;
}

export interface FacilityApplicationView {
  applicationId: string;
  applicationType: string;
  pathway: string | null;
  applicantName: string;
  submissionDate: string | null;
  currentWorkflowState: string;
  priority: string;
  feeState: string;
  committeeState: string;
  finalDecision: string | null;
  decisionDate: string | null;
  institutionFileNumber: string | null;
  requestedChanges: Record<string, unknown> | null;
  metadata: Record<string, unknown> | null;
  workflowNotes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface FacilityDocumentView {
  documentId: string;
  documentType: string;
  fileReference: string;
  version: number;
  verificationState: string;
  uploadedBy: string;
  uploadedAt: string;
  notes: string | null;
  metadata: Record<string, unknown> | null;
}

export interface FacilityInspectionView {
  inspectionId: string;
  applicationId: string | null;
  inspectionType: string;
  templateCode: string | null;
  templateName: string | null;
  scheduledDate: string | null;
  actualDate: string | null;
  inspectorAssignments: Array<Record<string, unknown>>;
  status: string;
  reportStatus: string;
  outcome: string | null;
  recommendation: string | null;
  severitySummary: string | null;
  nextAction: string | null;
  nextInspectionDueDate: string | null;
  captureMode: string;
  offlineCaptureReference: string | null;
  evidenceSummary: Record<string, unknown> | null;
  notes: string | null;
}

export interface FacilityFindingView {
  findingId: string;
  inspectionId: string;
  checklistItemCode: string;
  checklistSection: string | null;
  requirementText: string;
  criticalFlag: boolean;
  status: string;
  severity: string;
  comments: string | null;
  evidenceRefs: Array<Record<string, unknown>>;
  rectificationDeadline: string | null;
  rectificationStatus: string;
}

export interface ComplianceActionView {
  actionId: string;
  findingId: string;
  actionType: string;
  ownerName: string | null;
  ownerRole: string | null;
  dueDate: string;
  status: string;
  completionNotes: string | null;
  completionEvidenceRefs: Array<Record<string, unknown>>;
  verifiedBy: string | null;
  verifiedAt: string | null;
  verificationNotes: string | null;
}

export interface CommitteeReviewView {
  reviewId: string;
  applicationId: string | null;
  inspectionId: string | null;
  committeeType: string;
  scheduledDate: string | null;
  decision: string;
  notes: string | null;
  resolutionReference: string | null;
  authorityContext: string | null;
  decidedBy: string;
  decidedAt: string;
  metadata: Record<string, unknown> | null;
}

export interface CertificateView {
  certificateId: string;
  applicationId: string | null;
  certificateType: string;
  certificateNumber: string;
  issueDate: string;
  expiryDate: string | null;
  status: string;
  issuedUnderAuthority: string;
  digitalArtifactReference: string | null;
  supersedesCertificateId: string | null;
  metadata: Record<string, unknown> | null;
  createdAt: string;
  issuedBy: string;
}

export interface UnitView {
  unitId: number;
  name: string;
  unitType: string;
  serviceLine: string | null;
  registrationRequired: boolean;
  regulatoryStatus: string;
  certificateStatus: string | null;
  metadata: Record<string, unknown> | null;
}

export interface PractitionerAssignmentView {
  assignmentId: number;
  providerPublicId: string;
  role: string;
  startDate: string;
  endDate: string | null;
  approvalState: string;
  sourceCouncilReference: string | null;
  notes: string | null;
}

export interface StatusHistoryView {
  changedAt: string;
  regulatoryStatus: string;
  applicationState: string | null;
  changedBy: string;
  authorityContext: string | null;
  reason: string | null;
  sourceApplicationId: string | null;
}

export interface AuditEventView {
  auditId: string;
  createdAt: string;
  actorId: string;
  actorType: string | null;
  authorityContext: string | null;
  action: string;
  targetEntityType: string;
  targetEntityId: string;
  beforeState: Record<string, unknown> | null;
  afterState: Record<string, unknown> | null;
  correlationId: string | null;
  metadata: Record<string, unknown> | null;
}

export interface EnforcementCaseView {
  caseId: string;
  applicationId: string | null;
  triggerType: string;
  status: string;
  recommendation: string | null;
  authorityRoute: string | null;
  linkedInspectionIds: string[];
  linkedEvidence: Array<Record<string, unknown>>;
  closureReason: string | null;
  decisionDetails: string | null;
  openedAt: string;
  closedAt: string | null;
  openedBy: string;
  updatedBy: string | null;
}

export interface FacilityProfile {
  master: FacilityMaster;
  applications: FacilityApplicationView[];
  documents: FacilityDocumentView[];
  inspections: FacilityInspectionView[];
  findings: FacilityFindingView[];
  complianceActions: ComplianceActionView[];
  committeeReviews: CommitteeReviewView[];
  certificates: CertificateView[];
  units: UnitView[];
  practitionerAssignments: PractitionerAssignmentView[];
  statusHistory: StatusHistoryView[];
  auditTrail: AuditEventView[];
  enforcementCases: EnforcementCaseView[];
}

export interface FacilityDashboardSummary {
  totalFacilities: number;
  activeFacilities: number;
  pendingInspection: number;
  pendingCommitteeReviews: number;
  overdueComplianceActions: number;
  renewalsDueSoon: number;
  openEnforcementCases: number;
  facilitiesByRegulatoryStatus: Record<string, number>;
}

interface FacilityRegistryListParams {
  search?: string;
  regulatoryStatus?: string;
  facilityType?: string;
  province?: string;
  district?: string;
  page?: number;
  size?: number;
}

export interface UnitDraft {
  name: string;
  unitType: string;
  serviceLine?: string;
  registrationRequired: boolean;
  metadata?: Record<string, unknown>;
}

export interface PractitionerDraft {
  providerPublicId: string;
  role?: string;
  startDate: string;
  sourceCouncilReference?: string;
  notes?: string;
}

export interface CreateFacilityApplicationPayload {
  facilityId?: number;
  applicationType: string;
  pathway?: string;
  applicantName: string;
  applicantEmail?: string;
  applicantPhone?: string;
  applicantOrganisation?: string;
  priority?: string;
  feeState?: string;
  workflowNotes?: string;
  facilityCode?: string;
  facilityName?: string;
  aliasNames?: string[];
  ownershipType?: string;
  operatingEntity?: string;
  facilityClass?: string;
  facilityCategory?: string;
  facilityType?: string;
  legalStatus?: string;
  registrationPathway?: string;
  province?: string;
  district?: string;
  city?: string;
  ward?: string;
  postalCode?: string;
  addressLine1?: string;
  addressLine2?: string;
  latitude?: number;
  longitude?: number;
  metadata?: Record<string, unknown>;
  requestedUnits?: UnitDraft[];
  practitionerInCharge?: PractitionerDraft;
}

export interface UploadFacilityDocumentPayload {
  facilityId?: number;
  applicationId?: string;
  documentType: string;
  fileReference: string;
  version?: number;
  verificationState?: string;
  notes?: string;
  metadata?: Record<string, unknown>;
}

export interface ScheduleInspectionPayload {
  facilityId: number;
  applicationId?: string;
  inspectionType: string;
  scheduledDate?: string;
  templateCode?: string;
  inspectorAssignments?: Array<{
    actorId: string;
    displayName?: string;
    role?: string;
    authorityContext?: string;
  }>;
  notes?: string;
  captureMode?: string;
  offlineCaptureReference?: string;
}

export interface RecordInspectionPayload {
  actualDate?: string;
  notes?: string;
  recommendation?: string;
  nextAction?: string;
  nextInspectionDueDate?: string;
  findings: Array<{
    checklistItemCode: string;
    checklistSection?: string;
    requirementText: string;
    criticalFlag?: boolean;
    status: string;
    severity?: string;
    comments?: string;
    rectificationDeadline?: string;
    evidenceRefs?: Array<Record<string, unknown>>;
    ownerName?: string;
    ownerRole?: string;
    actionType?: string;
  }>;
}

type FacilityProfileResponse = ApiResponse<FacilityProfile>;
type DashboardResponse = ApiResponse<FacilityDashboardSummary>;
type FacilityRegistryListResponse = ApiResponse<FacilityRegistryPage>;

export function useFacilityRegistryFacilities(params?: FacilityRegistryListParams) {
  return useQuery<FacilityRegistryListResponse>({
    queryKey: ["facility-registry", "list", params],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (params?.search) searchParams.set("search", params.search);
      if (params?.regulatoryStatus) searchParams.set("regulatoryStatus", params.regulatoryStatus);
      if (params?.facilityType) searchParams.set("facilityType", params.facilityType);
      if (params?.province) searchParams.set("province", params.province);
      if (params?.district) searchParams.set("district", params.district);
      if (params?.page !== undefined) searchParams.set("page", String(params.page));
      if (params?.size !== undefined) searchParams.set("size", String(params.size));

      const qs = searchParams.toString();
      const path = `/internal/v1/facility-registry/facilities${qs ? `?${qs}` : ""}`;
      return apiClient.get<FacilityRegistryListResponse>(path);
    },
  });
}

export function useFacilityProfile(facilityId: string | number | undefined) {
  return useQuery<FacilityProfileResponse>({
    queryKey: ["facility-registry", "profile", facilityId],
    queryFn: () =>
      apiClient.get<FacilityProfileResponse>(`/internal/v1/facility-registry/facilities/${facilityId}`),
    enabled: facilityId !== undefined && facilityId !== null,
  });
}

export interface FacilityStatusSummary {
  id: number;
  code: string;
  name: string;
  status: string;
  operationalStatus: string | null;
  regulatoryStatus: string | null;
}

export function useFacilityStatusSummary(facilityId: string | number | undefined) {
  return useQuery<ApiResponse<FacilityStatusSummary>>({
    queryKey: ["facility-registry", "status-summary", facilityId],
    queryFn: () =>
      apiClient.get<ApiResponse<FacilityStatusSummary>>(
        `/internal/v1/facility-registry/facilities/${facilityId}/status-summary`,
      ),
    enabled: facilityId !== undefined && facilityId !== null,
  });
}

export function useFacilityDashboardSummary() {
  return useQuery<DashboardResponse>({
    queryKey: ["facility-registry", "dashboard"],
    queryFn: () => apiClient.get<DashboardResponse>("/internal/v1/facility-registry/dashboard/summary"),
  });
}

export function useChecklistTemplates(inspectionType: string | undefined, facilityType?: string | null) {
  return useQuery<ApiResponse<Array<Record<string, unknown>>>>({
    queryKey: ["facility-registry", "checklists", inspectionType, facilityType],
    queryFn: () => {
      const params = new URLSearchParams();
      params.set("inspectionType", inspectionType ?? "INITIAL");
      if (facilityType) params.set("facilityType", facilityType);
      return apiClient.get<ApiResponse<Array<Record<string, unknown>>>>(
        `/internal/v1/facility-registry/checklist-templates?${params.toString()}`,
      );
    },
    enabled: !!inspectionType,
  });
}

function invalidateFacilityRegistry(queryClient: ReturnType<typeof useQueryClient>, facilityId?: number | string) {
  void queryClient.invalidateQueries({ queryKey: ["facility-registry"] });
  if (facilityId !== undefined) {
    void queryClient.invalidateQueries({ queryKey: ["facility-registry", "profile", facilityId] });
  }
}

export function useCreateFacilityApplication() {
  const queryClient = useQueryClient();
  return useMutation<FacilityProfileResponse, unknown, CreateFacilityApplicationPayload>({
    mutationFn: (body) =>
      apiClient.post<FacilityProfileResponse>("/internal/v1/facility-registry/applications", body),
    onSuccess: (response) => invalidateFacilityRegistry(queryClient, response.data.master.facilityId),
  });
}

export function useSubmitFacilityApplication() {
  const queryClient = useQueryClient();
  return useMutation<FacilityProfileResponse, unknown, { applicationId: string; facilityId: number }>({
    mutationFn: ({ applicationId }) =>
      apiClient.post<FacilityProfileResponse>(`/internal/v1/facility-registry/applications/${applicationId}/submit`),
    onSuccess: (response, variables) => invalidateFacilityRegistry(queryClient, variables.facilityId),
  });
}

export function useMarkApplicationReadyForInspection() {
  const queryClient = useQueryClient();
  return useMutation<FacilityProfileResponse, unknown, { applicationId: string; facilityId: number }>({
    mutationFn: ({ applicationId }) =>
      apiClient.post<FacilityProfileResponse>(
        `/internal/v1/facility-registry/applications/${applicationId}/ready-for-inspection`,
      ),
    onSuccess: (response, variables) => invalidateFacilityRegistry(queryClient, variables.facilityId),
  });
}

export function useUploadFacilityDocument() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<FacilityDocumentView>, unknown, UploadFacilityDocumentPayload & { facilityIdHint?: number }>({
    mutationFn: (body) => apiClient.post<ApiResponse<FacilityDocumentView>>("/internal/v1/facility-registry/documents", body),
    onSuccess: (_response, variables) => invalidateFacilityRegistry(queryClient, variables.facilityId ?? variables.facilityIdHint),
  });
}

export function useScheduleFacilityInspection() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<FacilityInspectionView>, unknown, ScheduleInspectionPayload>({
    mutationFn: (body) => apiClient.post<ApiResponse<FacilityInspectionView>>("/internal/v1/facility-registry/inspections", body),
    onSuccess: (_response, variables) => invalidateFacilityRegistry(queryClient, variables.facilityId),
  });
}

export function useRecordFacilityInspection() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<FacilityInspectionView>, unknown, { inspectionId: string; facilityId: number; body: RecordInspectionPayload }>({
    mutationFn: ({ inspectionId, body }) =>
      apiClient.post<ApiResponse<FacilityInspectionView>>(
        `/internal/v1/facility-registry/inspections/${inspectionId}/record`,
        body,
      ),
    onSuccess: (_response, variables) => invalidateFacilityRegistry(queryClient, variables.facilityId),
  });
}

export function useUpdateComplianceAction() {
  const queryClient = useQueryClient();
  return useMutation<FacilityProfileResponse, unknown, { actionId: string; facilityId: number; status: string; completionNotes?: string; verificationNotes?: string }>({
    mutationFn: ({ actionId, ...body }) =>
      apiClient.post<FacilityProfileResponse>(`/internal/v1/facility-registry/compliance-actions/${actionId}`, body),
    onSuccess: (_response, variables) => invalidateFacilityRegistry(queryClient, variables.facilityId),
  });
}

export function useRecordCommitteeDecision() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<CommitteeReviewView>, unknown, {
    facilityId: number;
    applicationId: string;
    inspectionId?: string;
    committeeType: string;
    scheduledDate?: string;
    decision: string;
    notes?: string;
    resolutionReference?: string;
    authorityContext?: string;
    issueCertificate?: boolean;
    certificateType?: string;
    digitalArtifactReference?: string;
    metadata?: Record<string, unknown>;
  }>({
    mutationFn: (body) => apiClient.post<ApiResponse<CommitteeReviewView>>("/internal/v1/facility-registry/committee-reviews", body),
    onSuccess: (_response, variables) => invalidateFacilityRegistry(queryClient, variables.facilityId),
  });
}

export function useOpenEnforcementCase() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<EnforcementCaseView>, unknown, {
    facilityId: number;
    applicationId?: string;
    triggerType: string;
    status?: string;
    recommendation?: string;
    authorityRoute?: string;
    linkedInspectionIds?: string[];
    linkedEvidence?: Array<Record<string, unknown>>;
    closureReason?: string;
    decisionDetails?: string;
  }>({
    mutationFn: (body) => apiClient.post<ApiResponse<EnforcementCaseView>>("/internal/v1/facility-registry/enforcement-cases", body),
    onSuccess: (_response, variables) => invalidateFacilityRegistry(queryClient, variables.facilityId),
  });
}

// ── HAR W6/W7 — cross-facility worklists ────────────────────────────────────
//
// Per-facility task lists already existed and are right for a facility administrator. They are
// useless for a steward: the HPA import produced 24,616 data-gap tasks across 5,509 facilities and
// 6,180 PIC nominations, reachable only one facility at a time. These read the aggregate views.

export interface DataGapTaskRow {
  id: number;
  facilityId: number;
  facilityUuid?: string;
  facilityCode?: string;
  facilityName?: string;
  province?: string;
  district?: string;
  regulatoryStatus?: string;
  taskType: string;
  dimension?: string;
  requiredInformation: string;
  whyRequired?: string;
  responsibleActor: string;
  acceptedEvidence?: string;
  priority: string;
  visibility: string;
  status: string;
  updatedAt?: string;
}

export interface DataGapWorklistPage {
  items: DataGapTaskRow[];
  page: number;
  size: number;
  totalElements: number;
}

export interface DataGapWorklistParams {
  dimension?: string;
  responsibleActor?: string;
  priority?: string;
  province?: string;
  taskType?: string;
  status?: string;
  page?: number;
  size?: number;
}

export function useDataGapWorklist(params?: DataGapWorklistParams) {
  return useQuery<ApiResponse<DataGapWorklistPage>>({
    queryKey: ["facility-registry", "data-gap-worklist", params],
    queryFn: () => {
      const sp = new URLSearchParams();
      if (params?.dimension) sp.set("dimension", params.dimension);
      if (params?.responsibleActor) sp.set("responsibleActor", params.responsibleActor);
      if (params?.priority) sp.set("priority", params.priority);
      if (params?.province) sp.set("province", params.province);
      if (params?.taskType) sp.set("taskType", params.taskType);
      sp.set("status", params?.status ?? "OPEN");
      sp.set("page", String(params?.page ?? 0));
      sp.set("size", String(params?.size ?? 50));
      return apiClient.get<ApiResponse<DataGapWorklistPage>>(
        `/internal/v1/facility-registry/data-gap-worklist?${sp.toString()}`,
      );
    },
  });
}

export interface DataGapWorklistSummary {
  byDimension: Array<{ dimension?: string; priority?: string; taskCount: number }>;
  byProvince: Array<{ province?: string; taskCount: number; facilityCount: number }>;
}

export function useDataGapWorklistSummary(status = "OPEN") {
  return useQuery<ApiResponse<DataGapWorklistSummary>>({
    queryKey: ["facility-registry", "data-gap-worklist", "summary", status],
    queryFn: () =>
      apiClient.get<ApiResponse<DataGapWorklistSummary>>(
        `/internal/v1/facility-registry/data-gap-worklist/summary?status=${encodeURIComponent(status)}`,
      ),
  });
}

/**
 * Bulk transition. The backend caps the batch and appends the same audit entry the single-task path
 * writes; RESOLVED is as far as it goes, because a data gap closes when evidence arrives, not when
 * a steward clears a row.
 */
export function useBulkTransitionDataGapTasks() {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<{ requested: number; updated: number; status: string }>,
    unknown,
    { taskIds: number[]; status: string; note?: string }
  >({
    mutationFn: (body) =>
      apiClient.post<ApiResponse<{ requested: number; updated: number; status: string }>>(
        "/internal/v1/facility-registry/data-gap-worklist/bulk-transition",
        body,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["facility-registry", "data-gap-worklist"] });
    },
  });
}

export interface PicNominationRow {
  nomination: {
    nominationId: string;
    facilityId: number;
    providerPublicId: string;
    state: string;
    eligibilityResult?: string;
    nominatedAt?: string;
  };
  facilityCode?: string;
  facilityName?: string;
  province?: string;
  facilityRegulatoryStatus?: string;
}

export interface PicNominationQueuePage {
  content: PicNominationRow[];
  number: number;
  size: number;
  totalElements: number;
}

export function usePicNominationQueue(params?: {
  state?: string;
  province?: string;
  page?: number;
  size?: number;
}) {
  return useQuery<ApiResponse<PicNominationQueuePage>>({
    queryKey: ["facility-registry", "pic-nomination-queue", params],
    queryFn: () => {
      const sp = new URLSearchParams();
      if (params?.state) sp.set("state", params.state);
      if (params?.province) sp.set("province", params.province);
      sp.set("page", String(params?.page ?? 0));
      sp.set("size", String(params?.size ?? 50));
      return apiClient.get<ApiResponse<PicNominationQueuePage>>(
        `/internal/v1/facility-registry/pic-nominations?${sp.toString()}`,
      );
    },
  });
}
