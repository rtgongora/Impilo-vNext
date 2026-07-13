/**
 * HPA regulatory platform hooks — application-type/classification/rule registers,
 * premises & occupancy, requests-for-information, council reviews, PIC (practitioner
 * in charge) nominations, inspection visits and the public certificate verifier.
 *
 * All routes live under the experience-bff facility-registry surface
 * (`/internal/v1/facility-registry/**`) and share the `{ data, meta }` envelope,
 * mirroring the conventions in `useFacilityRegulatory.ts`.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

const BASE = "/internal/v1/facility-registry";

/* ─── Register types ─── */

export interface ApplicationTypeView {
  code: string;
  displayName: string;
  engineType: string | null;
  route: string | null;
  councilReviewRequired: boolean;
  inspectionRequired: boolean;
  feeRequired: boolean;
  manualRouteSummary: string | null;
  sourceManual: string | null;
  sourcePageStart: number | null;
  sourcePageEnd: number | null;
  validationStatus: string | null;
  active: boolean;
}

export interface FacilityClassificationView {
  classificationId: number;
  classCode: "A" | "B" | "C" | string;
  facilityTypeLabel: string;
  sourceManual: string | null;
  sourcePage: number | null;
  validationStatus: string | null;
  active: boolean;
}

export type RegulatoryRuleStatus = "PENDING_REGULATOR_APPROVAL" | "ACTIVE" | "RETIRED";

export interface RegulatoryRuleView {
  ruleId: string;
  code: string;
  version: number;
  owner: string | null;
  ruleKind: "PRINCIPLE" | "CONFIG" | string;
  statement: string;
  valueJson: Record<string, unknown> | null;
  implementation: string | null;
  status: RegulatoryRuleStatus | string;
  sourceManual: string | null;
  sourcePages: string | null;
  approvedBy: string | null;
  approvedAt: string | null;
}

/* ─── Premises & occupancy types ─── */

export interface PremisesView {
  premisesId: number;
  label: string;
  addressLine1: string | null;
  city: string | null;
  province: string | null;
  district: string | null;
  ward: string | null;
  postalCode: string | null;
  latitude: number | null;
  longitude: number | null;
  occupancyTenure: string | null;
  tenureEvidenceRef: string | null;
  localAuthorityReportRef: string | null;
}

export interface CreatePremisesPayload {
  label: string;
  addressLine1?: string;
  city?: string;
  province?: string;
  district?: string;
  ward?: string;
  postalCode?: string;
  latitude?: number;
  longitude?: number;
  occupancyTenure?: string;
  tenureEvidenceRef?: string;
  localAuthorityReportRef?: string;
}

export type OccupancyRole = "PRIMARY" | "SHARED_OCCUPANT";

export interface PremisesOccupancyView {
  occupancyId?: number;
  premisesId?: number;
  facilityId: number;
  occupancyRole?: string;
  role?: string;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
  notes?: string | null;
}

export interface OccupancyHistoryView {
  occupancyId?: number;
  premisesId: number;
  premisesLabel?: string | null;
  occupancyRole: string;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  notes?: string | null;
}

/* ─── Requests for information (RFI) ─── */

export type InformationRequestStatus = "OPEN" | "RESPONDED" | "CLOSED";

export interface InformationRequestView {
  rfiId: string;
  requestedBy: string | null;
  requestedAt: string | null;
  message: string;
  dueDate: string | null;
  status: InformationRequestStatus | string;
  responseNotes: string | null;
  respondedAt: string | null;
}

/* ─── Council reviews ─── */

export type CouncilReviewStatus = "PENDING" | "ENDORSED" | "REJECTED" | "NOT_REQUIRED";

export interface CouncilReviewRecordView {
  reviewId: string;
  reviewingAuthority: string;
  referenceNumber: string | null;
  status: CouncilReviewStatus | string;
  conditions: string | null;
  notes: string | null;
  recordedBy: string | null;
  recordedAt: string | null;
}

export interface RecordCouncilReviewPayload {
  reviewingAuthority: string;
  referenceNumber?: string;
  submittedDate?: string;
  decisionDate?: string;
  status: CouncilReviewStatus | string;
  conditions?: string;
  notes?: string;
}

/* ─── PIC nominations ─── */

export type PicAxisStatus = "PASS" | "WARN" | "FAIL";

export interface PicEligibilityAxis {
  status: PicAxisStatus | string;
  evidence?: string | null;
}

export interface PicEligibilitySnapshot {
  axes: Record<string, PicEligibilityAxis>;
  reasons: string[];
  displayName: string | null;
  profession: string | null;
  cadre: string | null;
  councilCode: string | null;
}

export type PicEligibilityResult = "ELIGIBLE" | "INELIGIBLE" | "CONDITIONAL";

export interface PicNominationView {
  nominationId: string;
  state: string;
  eligibilityResult: PicEligibilityResult | string | null;
  eligibilitySnapshot: PicEligibilitySnapshot | null;
  providerPublicId: string;
  impiloHealthId: string | null;
  facilityId?: number;
  facilityUnitId?: number | null;
  linkedApplicationId?: string | null;
  nominatedAt: string | null;
  practitionerResponse: string | null;
  reviewState: string | null;
  reviewAuthority: string | null;
  activatedAssignmentId: number | null;
  notes?: string | null;
}

export interface NominatePicPayload {
  facilityId: number;
  facilityUnitId?: number;
  providerPublicId: string;
  linkedApplicationId?: string;
  notes?: string;
}

/* ─── Inspection visits ─── */

export type VisitMode = "ONSITE" | "DESKTOP" | "VIRTUAL";
export type VisitStatus = "PLANNED" | "IN_PROGRESS" | "COMPLETED";

export interface VisitTeamMember {
  actorId: string;
  name: string;
  role?: string | null;
  councilNominee?: boolean;
  coiDeclared?: boolean;
  coiNotes?: string | null;
}

export interface InspectionVisitView {
  visitId: string;
  visitNumber: number;
  scheduledDate: string | null;
  actualDate: string | null;
  mode: VisitMode | string;
  leadInspectorId: string | null;
  leadInspectorName: string | null;
  team: VisitTeamMember[] | null;
  status: VisitStatus | string;
  notes: string | null;
}

export interface CreateVisitPayload {
  scheduledDate?: string;
  mode: VisitMode | string;
  leadInspectorName?: string;
  team?: VisitTeamMember[];
  notes?: string;
}

export type VisitItemResponse =
  | "COMPLIANT"
  | "NON_COMPLIANT"
  | "NOT_APPLICABLE"
  | "NOT_OBSERVED"
  | "UNABLE_TO_VERIFY";

export interface VisitResponseItemPayload {
  itemCode: string;
  itemSection?: string;
  requirementText?: string;
  response: VisitItemResponse | string;
  comments?: string;
  severity?: string;
  critical?: boolean;
}

export interface VisitResponseView {
  responseId?: string;
  itemCode: string;
  itemSection?: string | null;
  requirementText?: string | null;
  response: string;
  comments?: string | null;
  severity?: string | null;
  critical?: boolean | null;
  recordedBy?: string | null;
  recordedAt?: string | null;
}

/* ─── Composable inspection checklist catalogue (manual-derived) ─── */

export type ChecklistObligation =
  | "MANDATORY"
  | "OPTIONAL"
  | "RECOMMENDED"
  | "WHERE_APPLICABLE"
  | "WHERE_POSSIBLE";

export interface ChecklistMeasurementSpec {
  dimension?: string | null;
  unit?: string | null;
  expected?: string | null;
}

/** One manual-derived checklist requirement. Keys may be absent per item. */
export interface ChecklistItemView {
  code: string;
  section?: string | null;
  subsection?: string | null;
  sourcePage?: number | null;
  sourceHeading?: string | null;
  requirement?: string | null;
  requirementType?: string | null;
  obligation?: ChecklistObligation | string | null;
  applicability?: string | null;
  responseType?: "STATUS" | "MEASUREMENT" | string | null;
  evidence?: string | null;
  measurement?: ChecklistMeasurementSpec | null;
  quantity?: string | null;
  alternatives?: string | null;
  conditional?: string | null;
  group?: string | null;
  groupRole?: "PARENT" | "COMPONENT" | string | null;
  publicSafety?: boolean | null;
  sourceItemNumber?: string | null;
  reviewRequired?: boolean | null;
  reviewReason?: string | null;
}

export interface InspectionModuleView {
  code: string;
  version: number;
  title: string;
  scope: "COMMON" | "SERVICE_SPECIFIC" | string;
  sourceDoc?: string | null;
  sourceHeading?: string | null;
  sourcePages?: number[] | null;
  notes?: string | null;
  items?: ChecklistItemView[] | null;
  itemCount?: number | null;
  reviewRequiredCount?: number | null;
  status?: string | null;
}

export interface InspectionCompositionView {
  code: string;
  version: number;
  title: string;
  description?: string | null;
  moduleCodes: string[];
  inspectionTypes?: string[] | null;
  status?: string | null;
}

/** A module as it appears inside a composed checklist (frozen version). */
export interface ComposedChecklistModule {
  code: string;
  version: number;
  title: string;
  scope?: string | null;
  sourceDoc?: string | null;
  sourceHeading?: string | null;
  sourcePages?: number[] | null;
  notes?: string | null;
  items?: ChecklistItemView[] | null;
}

export interface InspectionChecklistView {
  inspectionId: string;
  mode: "COMPOSED" | "TEMPLATE" | string;
  compositionCode?: string | null;
  modules?: ComposedChecklistModule[] | null;
  totalItems?: number | null;
  templateCode?: string | null;
  templateVersion?: number | null;
  templateStatus?: string | null;
  items?: ChecklistItemView[] | null;
}

export interface VisitOutstandingMandatoryItem {
  code: string;
  section?: string | null;
  requirement?: string | null;
}

export interface VisitMissingEvidenceItem {
  code: string;
  expectedEvidence?: string | null;
  response?: string | null;
}

export interface VisitProgressView {
  visitId: string;
  visitStatus: string;
  totalItems: number;
  answered: number;
  outstandingMandatory: VisitOutstandingMandatoryItem[];
  missingRequiredEvidence: VisitMissingEvidenceItem[];
}

/* ─── Public certificate verification ─── */

export interface FacilityCertificateVerification {
  verified: boolean;
  certificateNumber: string;
  certificateType: string | null;
  status: string;
  issuedUnderAuthority: string | null;
  issueDate: string | null;
  expiryDate: string | null;
  conditions: string | null;
  supersedes?: string | null;
  facilityName: string | null;
  province: string | null;
  district: string | null;
  facilityType: string | null;
  licensedUnit?: string | null;
}

/* ─── Shared invalidation (mirrors useFacilityRegulatory.ts) ─── */

function invalidateFacilityRegistry(
  queryClient: ReturnType<typeof useQueryClient>,
  ...specificKeys: Array<ReadonlyArray<unknown>>
) {
  void queryClient.invalidateQueries({ queryKey: ["facility-registry"] });
  for (const key of specificKeys) {
    void queryClient.invalidateQueries({ queryKey: key as unknown[] });
  }
}

/* ─── Register hooks ─── */

export function useApplicationTypes() {
  return useQuery<ApiResponse<ApplicationTypeView[]>>({
    queryKey: ["facility-registry", "application-types"],
    queryFn: () => apiClient.get<ApiResponse<ApplicationTypeView[]>>(`${BASE}/application-types`),
  });
}

export function useClassifications() {
  return useQuery<ApiResponse<FacilityClassificationView[]>>({
    queryKey: ["facility-registry", "classifications"],
    queryFn: () => apiClient.get<ApiResponse<FacilityClassificationView[]>>(`${BASE}/classifications`),
  });
}

export function useRegulatoryRules() {
  return useQuery<ApiResponse<RegulatoryRuleView[]>>({
    queryKey: ["facility-registry", "rules"],
    queryFn: () => apiClient.get<ApiResponse<RegulatoryRuleView[]>>(`${BASE}/rules`),
  });
}

export function useApproveRule() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<RegulatoryRuleView>, unknown, { ruleId: string }>({
    mutationFn: ({ ruleId }) =>
      apiClient.post<ApiResponse<RegulatoryRuleView>>(`${BASE}/rules/${ruleId}/approve`),
    onSuccess: () => invalidateFacilityRegistry(queryClient, ["facility-registry", "rules"]),
  });
}

/* ─── Premises & occupancy hooks ─── */

export function usePremises() {
  return useQuery<ApiResponse<PremisesView[]>>({
    queryKey: ["facility-registry", "premises"],
    queryFn: () => apiClient.get<ApiResponse<PremisesView[]>>(`${BASE}/premises`),
  });
}

export function useCreatePremises() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<PremisesView>, unknown, CreatePremisesPayload>({
    mutationFn: (body) => apiClient.post<ApiResponse<PremisesView>>(`${BASE}/premises`, body),
    onSuccess: () => invalidateFacilityRegistry(queryClient, ["facility-registry", "premises"]),
  });
}

export function useAssignOccupancy() {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<PremisesOccupancyView>,
    unknown,
    { premisesId: number; facilityId: number; role: OccupancyRole | string; notes?: string }
  >({
    mutationFn: ({ premisesId, ...body }) =>
      apiClient.post<ApiResponse<PremisesOccupancyView>>(`${BASE}/premises/${premisesId}/occupancies`, body),
    onSuccess: (_response, variables) =>
      invalidateFacilityRegistry(
        queryClient,
        ["facility-registry", "premises-occupancies", variables.premisesId],
        ["facility-registry", "occupancy-history", variables.facilityId],
      ),
  });
}

export function usePremisesOccupants(premisesId: number | string | undefined) {
  return useQuery<ApiResponse<PremisesOccupancyView[]>>({
    queryKey: ["facility-registry", "premises-occupancies", premisesId],
    queryFn: () =>
      apiClient.get<ApiResponse<PremisesOccupancyView[]>>(`${BASE}/premises/${premisesId}/occupancies`),
    enabled: premisesId !== undefined && premisesId !== null && premisesId !== "",
  });
}

export function useOccupancyHistory(facilityId: number | string | undefined) {
  return useQuery<ApiResponse<OccupancyHistoryView[]>>({
    queryKey: ["facility-registry", "occupancy-history", facilityId],
    queryFn: () =>
      apiClient.get<ApiResponse<OccupancyHistoryView[]>>(`${BASE}/facilities/${facilityId}/occupancy-history`),
    enabled: facilityId !== undefined && facilityId !== null && facilityId !== "",
  });
}

/* ─── RFI hooks ─── */

export function useInformationRequests(applicationId: string | undefined) {
  return useQuery<ApiResponse<InformationRequestView[]>>({
    queryKey: ["facility-registry", "information-requests", applicationId],
    queryFn: () =>
      apiClient.get<ApiResponse<InformationRequestView[]>>(
        `${BASE}/applications/${applicationId}/information-requests`,
      ),
    enabled: !!applicationId,
  });
}

export function useOpenInformationRequest() {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<InformationRequestView>,
    unknown,
    { applicationId: string; message: string; dueDate?: string }
  >({
    mutationFn: ({ applicationId, ...body }) =>
      apiClient.post<ApiResponse<InformationRequestView>>(
        `${BASE}/applications/${applicationId}/information-requests`,
        body,
      ),
    onSuccess: (_response, variables) =>
      invalidateFacilityRegistry(queryClient, [
        "facility-registry",
        "information-requests",
        variables.applicationId,
      ]),
  });
}

export function useRespondInformationRequest() {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<InformationRequestView>,
    unknown,
    { rfiId: string; responseNotes: string; responseDocumentId?: string; applicationId?: string }
  >({
    mutationFn: ({ rfiId, applicationId: _applicationId, ...body }) =>
      apiClient.post<ApiResponse<InformationRequestView>>(`${BASE}/information-requests/${rfiId}/respond`, body),
    onSuccess: (_response, variables) =>
      invalidateFacilityRegistry(queryClient, [
        "facility-registry",
        "information-requests",
        variables.applicationId,
      ]),
  });
}

export function useCloseInformationRequest() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<InformationRequestView>, unknown, { rfiId: string; applicationId?: string }>({
    mutationFn: ({ rfiId }) =>
      apiClient.post<ApiResponse<InformationRequestView>>(`${BASE}/information-requests/${rfiId}/close`),
    onSuccess: (_response, variables) =>
      invalidateFacilityRegistry(queryClient, [
        "facility-registry",
        "information-requests",
        variables.applicationId,
      ]),
  });
}

/* ─── Council review hooks ─── */

export function useCouncilReviews(applicationId: string | undefined) {
  return useQuery<ApiResponse<CouncilReviewRecordView[]>>({
    queryKey: ["facility-registry", "council-reviews", applicationId],
    queryFn: () =>
      apiClient.get<ApiResponse<CouncilReviewRecordView[]>>(`${BASE}/applications/${applicationId}/council-reviews`),
    enabled: !!applicationId,
  });
}

export function useRecordCouncilReview() {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<CouncilReviewRecordView>,
    unknown,
    { applicationId: string } & RecordCouncilReviewPayload
  >({
    mutationFn: ({ applicationId, ...body }) =>
      apiClient.post<ApiResponse<CouncilReviewRecordView>>(
        `${BASE}/applications/${applicationId}/council-reviews`,
        body,
      ),
    onSuccess: (_response, variables) =>
      invalidateFacilityRegistry(queryClient, ["facility-registry", "council-reviews", variables.applicationId]),
  });
}

/* ─── PIC nomination hooks ─── */

export function useFacilityPicNominations(facilityId: number | string | undefined) {
  return useQuery<ApiResponse<PicNominationView[]>>({
    queryKey: ["facility-registry", "pic-nominations", "facility", facilityId],
    queryFn: () =>
      apiClient.get<ApiResponse<PicNominationView[]>>(`${BASE}/facilities/${facilityId}/pic-nominations`),
    enabled: facilityId !== undefined && facilityId !== null && facilityId !== "",
  });
}

export function useProviderPicNominations(providerPublicId: string | undefined) {
  return useQuery<ApiResponse<PicNominationView[]>>({
    queryKey: ["facility-registry", "pic-nominations", "provider", providerPublicId],
    queryFn: () =>
      apiClient.get<ApiResponse<PicNominationView[]>>(
        `${BASE}/pic-nominations/by-provider/${encodeURIComponent(providerPublicId ?? "")}`,
      ),
    enabled: !!providerPublicId,
  });
}

function invalidatePicNominations(
  queryClient: ReturnType<typeof useQueryClient>,
  facilityId?: number | string,
  providerPublicId?: string,
) {
  invalidateFacilityRegistry(
    queryClient,
    ["facility-registry", "pic-nominations", "facility", facilityId],
    ["facility-registry", "pic-nominations", "provider", providerPublicId],
  );
}

export function useNominatePic() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<PicNominationView>, unknown, NominatePicPayload>({
    mutationFn: (body) => apiClient.post<ApiResponse<PicNominationView>>(`${BASE}/pic-nominations`, body),
    onSuccess: (_response, variables) =>
      invalidatePicNominations(queryClient, variables.facilityId, variables.providerPublicId),
  });
}

export function useRespondPicNomination() {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<PicNominationView>,
    unknown,
    {
      nominationId: string;
      response: "ACCEPTED" | "DECLINED" | "DISPUTED";
      notes?: string;
      facilityId?: number | string;
      providerPublicId?: string;
    }
  >({
    mutationFn: ({ nominationId, facilityId: _f, providerPublicId: _p, ...body }) =>
      apiClient.post<ApiResponse<PicNominationView>>(`${BASE}/pic-nominations/${nominationId}/respond`, body),
    onSuccess: (_response, variables) =>
      invalidatePicNominations(queryClient, variables.facilityId, variables.providerPublicId),
  });
}

export function useReviewPicNomination() {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<PicNominationView>,
    unknown,
    {
      nominationId: string;
      authority: string;
      reference?: string;
      approved: boolean;
      notes?: string;
      facilityId?: number | string;
      providerPublicId?: string;
    }
  >({
    mutationFn: ({ nominationId, facilityId: _f, providerPublicId: _p, ...body }) =>
      apiClient.post<ApiResponse<PicNominationView>>(`${BASE}/pic-nominations/${nominationId}/review`, body),
    onSuccess: (_response, variables) =>
      invalidatePicNominations(queryClient, variables.facilityId, variables.providerPublicId),
  });
}

export function useActivatePicNomination() {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<PicNominationView>,
    unknown,
    { nominationId: string; facilityId?: number | string; providerPublicId?: string }
  >({
    mutationFn: ({ nominationId }) =>
      apiClient.post<ApiResponse<PicNominationView>>(`${BASE}/pic-nominations/${nominationId}/activate`),
    onSuccess: (_response, variables) =>
      invalidatePicNominations(queryClient, variables.facilityId, variables.providerPublicId),
  });
}

export function useWithdrawPicNomination() {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<PicNominationView>,
    unknown,
    { nominationId: string; reason?: string; facilityId?: number | string; providerPublicId?: string }
  >({
    mutationFn: ({ nominationId, reason }) =>
      apiClient.post<ApiResponse<PicNominationView>>(`${BASE}/pic-nominations/${nominationId}/withdraw`, {
        reason,
      }),
    onSuccess: (_response, variables) =>
      invalidatePicNominations(queryClient, variables.facilityId, variables.providerPublicId),
  });
}

export function useResolvePicReview() {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<Record<string, unknown>>,
    unknown,
    { assignmentId: number | string; cleared: boolean; notes?: string; facilityId?: number | string }
  >({
    mutationFn: ({ assignmentId, facilityId: _f, ...body }) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>(
        `${BASE}/pic-assignments/${assignmentId}/resolve-review`,
        body,
      ),
    onSuccess: (_response, variables) =>
      invalidateFacilityRegistry(queryClient, ["facility-registry", "profile", variables.facilityId]),
  });
}

/* ─── Inspection visit hooks ─── */

export function useInspectionVisits(inspectionId: string | undefined) {
  return useQuery<ApiResponse<InspectionVisitView[]>>({
    queryKey: ["facility-registry", "inspection-visits", inspectionId],
    queryFn: () =>
      apiClient.get<ApiResponse<InspectionVisitView[]>>(`${BASE}/inspections/${inspectionId}/visits`),
    enabled: !!inspectionId,
  });
}

export function useCreateInspectionVisit() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<InspectionVisitView>, unknown, { inspectionId: string } & CreateVisitPayload>({
    mutationFn: ({ inspectionId, ...body }) =>
      apiClient.post<ApiResponse<InspectionVisitView>>(`${BASE}/inspections/${inspectionId}/visits`, body),
    onSuccess: (_response, variables) =>
      invalidateFacilityRegistry(queryClient, ["facility-registry", "inspection-visits", variables.inspectionId]),
  });
}

export function useVisitResponses(visitId: string | undefined) {
  return useQuery<ApiResponse<VisitResponseView[]>>({
    queryKey: ["facility-registry", "visit-responses", visitId],
    queryFn: () => apiClient.get<ApiResponse<VisitResponseView[]>>(`${BASE}/visits/${visitId}/responses`),
    enabled: !!visitId,
  });
}

export function useRecordVisitResponses() {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<VisitResponseView[]>,
    unknown,
    { visitId: string; items: VisitResponseItemPayload[]; inspectionId?: string }
  >({
    mutationFn: ({ visitId, items }) =>
      apiClient.post<ApiResponse<VisitResponseView[]>>(`${BASE}/visits/${visitId}/responses`, { items }),
    onSuccess: (_response, variables) =>
      invalidateFacilityRegistry(
        queryClient,
        ["facility-registry", "visit-responses", variables.visitId],
        ["facility-registry", "inspection-visits", variables.inspectionId],
      ),
  });
}

export function useCompleteVisit() {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<InspectionVisitView>,
    unknown,
    { visitId: string; notes?: string; inspectionId?: string }
  >({
    mutationFn: ({ visitId, notes }) =>
      apiClient.post<ApiResponse<InspectionVisitView>>(`${BASE}/visits/${visitId}/complete`, { notes }),
    onSuccess: (_response, variables) =>
      invalidateFacilityRegistry(
        queryClient,
        ["facility-registry", "inspection-visits", variables.inspectionId],
        ["facility-registry", "visit-responses", variables.visitId],
      ),
  });
}

/* ─── Inspection checklist catalogue hooks ─── */

export function useInspectionModules() {
  return useQuery<ApiResponse<InspectionModuleView[]>>({
    queryKey: ["facility-registry", "inspection-modules"],
    queryFn: () => apiClient.get<ApiResponse<InspectionModuleView[]>>(`${BASE}/inspection-modules`),
  });
}

export function useInspectionCompositions() {
  return useQuery<ApiResponse<InspectionCompositionView[]>>({
    queryKey: ["facility-registry", "inspection-compositions"],
    queryFn: () =>
      apiClient.get<ApiResponse<InspectionCompositionView[]>>(`${BASE}/inspection-compositions`),
  });
}

export function useInspectionChecklist(inspectionId: string | undefined) {
  return useQuery<ApiResponse<InspectionChecklistView>>({
    queryKey: ["facility-registry", "inspection-checklist", inspectionId],
    queryFn: () =>
      apiClient.get<ApiResponse<InspectionChecklistView>>(`${BASE}/inspections/${inspectionId}/checklist`),
    enabled: !!inspectionId,
  });
}

export function useVisitProgress(visitId: string | undefined) {
  return useQuery<ApiResponse<VisitProgressView>>({
    queryKey: ["facility-registry", "visit-progress", visitId],
    queryFn: () => apiClient.get<ApiResponse<VisitProgressView>>(`${BASE}/visits/${visitId}/progress`),
    enabled: !!visitId,
  });
}

// ---- Fee schedule + application fee (SI 78 of 2017) ------------------------

export interface FeeScheduleView {
  feeId: string;
  scheduleCode: string;
  version: number;
  feeCode: string;
  appliesToCatalogueCode: string;
  appliesToClassCode: string | null;
  amount: number | null;
  currency: string | null;
  sourceInstrument: string;
  sourceSection: string | null;
  status: string;
  effectiveFrom: string | null;
  approvedBy: string | null;
  approvedAt: string | null;
  notes: string | null;
}

export interface ApplicationFeeView {
  applicationId: string;
  catalogueCode: string | null;
  feeRequired: boolean;
  feeState: string;
  feeCode: string | null;
  amount: number | null;
  currency: string | null;
  sourceInstrument: string | null;
  feeScheduleRef: string | null;
  feeReference: string | null;
  paymentReference: string | null;
  waivedBy: string | null;
  waivedAt: string | null;
  waiverReason: string | null;
  blocksInspection: boolean;
}

export function useFeeSchedules(scheduleCode = "SI_78_2017") {
  return useQuery<ApiResponse<FeeScheduleView[]>>({
    queryKey: ["facility-registry", "fee-schedules", scheduleCode],
    queryFn: () =>
      apiClient.get<ApiResponse<FeeScheduleView[]>>(`${BASE}/fee-schedules?scheduleCode=${scheduleCode}`),
  });
}

export function useCreateFeeSchedule() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<FeeScheduleView>, unknown, Record<string, unknown>>({
    mutationFn: (body) => apiClient.post<ApiResponse<FeeScheduleView>>(`${BASE}/fee-schedules`, body),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["facility-registry", "fee-schedules"] }),
  });
}

export function useApproveFeeSchedule() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<FeeScheduleView>, unknown, { feeId: string; effectiveFrom?: string }>({
    mutationFn: ({ feeId, effectiveFrom }) =>
      apiClient.post<ApiResponse<FeeScheduleView>>(`${BASE}/fee-schedules/${feeId}/approve`, { effectiveFrom }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["facility-registry", "fee-schedules"] }),
  });
}

export function useApplicationFee(applicationId: string | undefined) {
  return useQuery<ApiResponse<ApplicationFeeView>>({
    queryKey: ["facility-registry", "application-fee", applicationId],
    queryFn: () => apiClient.get<ApiResponse<ApplicationFeeView>>(`${BASE}/applications/${applicationId}/fee`),
    enabled: !!applicationId,
  });
}

export function useCreateFeePaymentIntent(applicationId: string) {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<ApplicationFeeView>, unknown, void>({
    mutationFn: () =>
      apiClient.post<ApiResponse<ApplicationFeeView>>(`${BASE}/applications/${applicationId}/fee/payment-intent`),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ["facility-registry", "application-fee", applicationId] }),
  });
}

export function useWaiveFee(applicationId: string) {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<ApplicationFeeView>, unknown, { reason: string }>({
    mutationFn: (body) =>
      apiClient.post<ApiResponse<ApplicationFeeView>>(`${BASE}/applications/${applicationId}/fee/waive`, body),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ["facility-registry", "application-fee", applicationId] }),
  });
}
