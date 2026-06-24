import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

// ── Enums ────────────────────────────────────────────────────────────

export type IdentityStatus =
  | "DRAFT"
  | "PROVISIONAL"
  | "REGISTERED"
  | "PENDING_VERIFICATION"
  | "PENDING_MATCH_REVIEW"
  | "VERIFIED"
  | "ACTIVE"
  | "FLAGGED_FOR_REVIEW"
  | "RESTRICTED"
  | "INACTIVE"
  | "DECEASED"
  | "MERGED";

export type ClientVerificationState =
  | "UNVERIFIED"
  | "SELF_ASSERTED"
  | "PROVIDER_CAPTURED"
  | "PARTIALLY_VERIFIED"
  | "VERIFIED"
  | "REVIEW_REQUIRED";

export type ClientRegistrationType =
  | "SELF_INITIATED"
  | "PROVIDER_ASSISTED"
  | "FACILITY_REGISTRATION"
  | "COMMUNITY_REGISTRATION"
  | "OUTREACH_REGISTRATION"
  | "VIRTUAL_REGISTRATION"
  | "BULK_IMPORT"
  | "INTEROPERABILITY_IMPORT";

export type ClientRegistrationWorkflowState =
  | "DRAFT"
  | "INITIATED"
  | "SUBMITTED"
  | "PARTIALLY_CAPTURED"
  | "PENDING_VERIFICATION"
  | "PENDING_MATCHING"
  | "PENDING_REVIEW"
  | "COMPLETED"
  | "REJECTED"
  | "CLOSED_OUT";

export type ClientMatchReviewStatus =
  | "OPEN"
  | "NEEDS_REVIEW"
  | "CONFIRMED_DUPLICATE"
  | "CONFIRMED_DISTINCT"
  | "MERGED"
  | "CANCELLED";

export type ClientRelationshipType =
  | "GUARDIAN_OF"
  | "DEPENDENT_OF"
  | "CAREGIVER_OF"
  | "NEXT_OF_KIN"
  | "PROXY_ACCESS_FOR";

export type ClientStewardshipActionType =
  | "VERIFY_IDENTITY"
  | "REVIEW_DUPLICATE"
  | "COMPLETE_DEMOGRAPHICS"
  | "LINK_GUARDIAN"
  | "AUTHORISATION_REVIEW"
  | "MERGE_REVIEW"
  | "CORRECT_RECORD"
  | "UNRESOLVED_MATCH"
  | "OTHER";

export type ClientStewardshipStatus =
  | "OPEN"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED"
  | "VERIFIED";

// ── Interfaces ───────────────────────────────────────────────────────

interface PagedItems<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface IdentitySummary {
  healthId: string;
  identityStatus: IdentityStatus;
  verificationStatus: ClientVerificationState;
  identityAssuranceLevel: number;
}

export interface RegistrationView {
  registrationId: string;
  registrationType: ClientRegistrationType;
  initiatedChannel: string;
  initiatedBy: string;
  linkedFacilityId: number | null;
  linkedProviderId: string | null;
  linkedServiceContextId: string | null;
  workflowState: ClientRegistrationWorkflowState;
  completionState: string | null;
  verificationState: ClientVerificationState;
  submissionDate: string | null;
  notes: string | null;
  provenance: Record<string, unknown>;
}

export interface IdentifierView {
  identifierId: string;
  identifierType: string;
  identifierValue: string;
  status: string;
  primaryFlag: boolean;
  issueDate: string | null;
  expiryDate: string | null;
  source: string | null;
}

export interface AliasView {
  aliasId: string;
  aliasType: string;
  aliasValue: string;
  source: string | null;
  confidence: number | null;
  activeFlag: boolean;
}

export interface EvidenceItem {
  evidenceId: string;
  registrationId: string;
  evidenceType: string;
  evidenceReference: string;
  verificationState: ClientVerificationState;
  verifiedBy: string | null;
  verifiedAt: string | null;
  notes: string | null;
}

export interface VerificationReview {
  reviewId: string;
  registrationId: string | null;
  reviewType: string;
  status: string;
  reviewer: string | null;
  decision: string;
  notes: string | null;
  reviewedAt: string | null;
}

export interface MatchCandidate {
  matchId: number;
  sourceHealthId: string;
  candidateHealthId: string;
  matchScore: number;
  matchReasonSummary: string | null;
  status: ClientMatchReviewStatus;
  generatedAt: string;
  resolvedBy: string | null;
  resolvedAt: string | null;
}

export interface MergeCase {
  mergeCaseId: string;
  primaryHealthId: string;
  secondaryHealthId: string;
  initiatedBy: string | null;
  reviewedBy: string | null;
  decision: string | null;
  survivorshipSummary: string | null;
  status: string;
  executedAt: string | null;
  mergeHistoryId: number | null;
}

export interface Relationship {
  relationshipId: string;
  clientHealthId: string;
  relatedClientHealthId: string;
  relationshipType: ClientRelationshipType;
  startDate: string | null;
  endDate: string | null;
  status: string;
  notes: string | null;
}

export interface AuthorizationLink {
  authorizationLinkId: string;
  authorisationType: string;
  referenceId: string;
  status: string;
  startDate: string | null;
  endDate: string | null;
}

export interface StewardshipAction {
  actionId: string;
  actionType: ClientStewardshipActionType;
  owner: string | null;
  dueDate: string | null;
  status: ClientStewardshipStatus;
  completionNotes: string | null;
  verifiedBy: string | null;
  verifiedAt: string | null;
  relatedRegistrationId: string | null;
  relatedMergeCaseId: string | null;
}

export interface StatusHistoryView {
  statusHistoryId: string;
  previousStatus: string;
  newStatus: string;
  reason: string | null;
  changedBy: string | null;
  changedAt: string;
  provenanceContext: Record<string, unknown>;
}

export interface AuditEventView {
  auditEventId: string;
  actor: string;
  actorType: string;
  action: string;
  targetEntity: string;
  targetId: string;
  beforeState: Record<string, unknown> | null;
  afterState: Record<string, unknown> | null;
  correlationId: string | null;
  provenanceContext: Record<string, unknown>;
  createdAt: string;
}

export interface ClientMaster {
  healthId: string;
  crid: string;
  impiloId: string | null;
  firstName: string;
  middleName: string | null;
  lastName: string;
  dateOfBirth: string | null;
  sex: string | null;
  lifecycleStatus: IdentityStatus;
  verificationStatus: ClientVerificationState;
  identityAssuranceLevel: number;
  activeFlag: boolean;
  deceasedFlag: boolean;
  goldenRecordFlag: boolean;
  demographics: Record<string, unknown>;
  contacts: Record<string, unknown>;
  address: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface ClientProfile {
  master: ClientMaster;
  registrations: RegistrationView[];
  identifiers: IdentifierView[];
  aliases: AliasView[];
  evidence: EvidenceItem[];
  verificationReviews: VerificationReview[];
  matchCandidates: MatchCandidate[];
  mergeCases: MergeCase[];
  relationships: Relationship[];
  authorizationLinks: AuthorizationLink[];
  stewardshipActions: StewardshipAction[];
  statusHistory: StatusHistoryView[];
  auditTrail: AuditEventView[];
}

export interface RegistrationDraft {
  clientHealthId?: string | null;
  registrationType: ClientRegistrationType;
  initiatedChannel: string;
  linkedProviderId?: string | null;
  linkedFacilityId?: number | null;
  linkedServiceContextId?: string | null;
  firstName?: string | null;
  middleName?: string | null;
  lastName?: string | null;
  dateOfBirth?: string | null;
  estimatedDateOfBirth?: boolean | null;
  sex?: string | null;
  phone?: string | null;
  email?: string | null;
  nationalIdReference?: string | null;
  passportReference?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  city?: string | null;
  district?: string | null;
  province?: string | null;
  notes?: string | null;
  metadata?: Record<string, unknown> | null;
  previousNames?: string[] | null;
  issueProvisionalIdentifier?: boolean | null;
}

export interface ClientRegistrySummary {
  healthId: string;
  crid: string;
  impiloId: string | null;
  displayName: string;
  lifecycleStatus: IdentityStatus;
  verificationStatus: ClientVerificationState;
  identityAssuranceLevel: number;
  goldenRecord: boolean;
  active: boolean;
  deceased: boolean;
  latestRegistrationType: string | null;
  latestRegistrationChannel: string | null;
  openStewardshipActions: number;
  openMatches: number;
}

export interface DashboardSummary {
  totalClients: number;
  provisionalClients: number;
  pendingVerification: number;
  pendingMatchReview: number;
  openStewardshipActions: number;
  mergeCasesOpen: number;
  clientsByStatus: Record<string, number>;
}

export interface StewardshipWorkspace {
  duplicateQueue: MatchCandidate[];
  verificationQueue: VerificationReview[];
  stewardshipQueue: StewardshipAction[];
}

export interface DemographicCorrection {
  firstName?: string | null;
  middleName?: string | null;
  lastName?: string | null;
  dateOfBirth?: string | null;
  sex?: string | null;
  phone?: string | null;
  email?: string | null;
  notes?: string | null;
  requireReview: boolean;
}

const QUERY_KEY_PREFIX = ["vito", "client-registry"];
const BASE = "/internal/v1/vito/client-registry";

// ── Queries ──────────────────────────────────────────────────────────

export function useClientRegistrySearch(
  query?: string,
  status?: IdentityStatus,
  verificationState?: ClientVerificationState,
  page = 0,
  size = 20
) {
  return useQuery({
    queryKey: [...QUERY_KEY_PREFIX, "clients", query, status, verificationState, page, size],
    queryFn: () => {
      const params = new URLSearchParams();
      if (query) params.append("query", query);
      if (status) params.append("status", status);
      if (verificationState) params.append("verificationState", verificationState);
      params.append("page", page.toString());
      params.append("size", size.toString());
      return apiClient.get<ApiResponse<PagedItems<ClientRegistrySummary>>>(
        `${BASE}/clients?${params.toString()}`
      );
    },
  });
}

export function useClientProfile(healthId: string | undefined) {
  return useQuery({
    queryKey: [...QUERY_KEY_PREFIX, "clients", healthId],
    queryFn: () =>
      apiClient.get<ApiResponse<ClientProfile>>(`${BASE}/clients/${healthId}`),
    enabled: !!healthId,
  });
}

/** Editable demographic fields (subset of VITO's ClientDemographicsUpdateRequest). */
export interface DemographicsUpdate {
  givenName?: string;
  middleName?: string;
  familyName?: string;
  dateOfBirth?: string | null;
  sex?: string;
  phone?: string;
  email?: string;
  addressLine1?: string;
  city?: string;
  district?: string;
  province?: string;
}

/** Update a client's demographics — proxies VITO's canonical PUT /v1/clients/{healthId} (G055). */
export function useUpdateClientDemographics(healthId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: DemographicsUpdate) =>
      apiClient.put<ApiResponse<ClientProfile>>(`${BASE}/clients/${healthId}/demographics`, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [...QUERY_KEY_PREFIX, "clients", healthId] });
    },
  });
}

export function useClientIdentitySummary(healthId: string | undefined) {
  return useQuery({
    queryKey: [...QUERY_KEY_PREFIX, "clients", healthId, "identity-summary"],
    queryFn: () =>
      apiClient.get<ApiResponse<IdentitySummary>>(
        `${BASE}/clients/${healthId}/identity-summary`
      ),
    enabled: !!healthId,
  });
}

export function useRegistryDashboard() {
  return useQuery({
    queryKey: [...QUERY_KEY_PREFIX, "dashboard"],
    queryFn: () =>
      apiClient.get<ApiResponse<DashboardSummary>>(`${BASE}/dashboard/summary`),
  });
}

export function useStewardshipWorkspace() {
  return useQuery({
    queryKey: [...QUERY_KEY_PREFIX, "stewardship", "workspace"],
    queryFn: () =>
      apiClient.get<ApiResponse<StewardshipWorkspace>>(`${BASE}/stewardship/workspace`),
  });
}

// ── Mutations ────────────────────────────────────────────────────────

export function useCreateRegistration() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: RegistrationDraft) =>
      apiClient.post<ApiResponse<ClientProfile>>(`${BASE}/registrations`, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY_PREFIX });
    },
  });
}

export function useSubmitRegistration() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (registrationId: string) =>
      apiClient.post<ApiResponse<ClientProfile>>(
        `${BASE}/registrations/${registrationId}/submit`,
        {}
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY_PREFIX });
    },
  });
}

export function useAddEvidence() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      healthId,
      body,
    }: {
      healthId: string;
      body: {
        registrationId: string;
        evidenceType: string;
        evidenceReference: string;
        verificationState?: ClientVerificationState;
        notes?: string;
      };
    }) =>
      apiClient.post<ApiResponse<EvidenceItem>>(
        `${BASE}/clients/${healthId}/evidence`,
        body
      ),
    onSuccess: (_res, { healthId }) => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY_PREFIX });
      void queryClient.invalidateQueries({
        queryKey: [...QUERY_KEY_PREFIX, "clients", healthId],
      });
    },
  });
}

export function useVerificationReview() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      healthId,
      body,
    }: {
      healthId: string;
      body: {
        registrationId?: string;
        reviewType: string;
        decision: string;
        notes?: string;
        identityAssuranceLevel?: number;
        issueImpiloId?: boolean;
      };
    }) =>
      apiClient.post<ApiResponse<VerificationReview>>(
        `${BASE}/clients/${healthId}/verification-reviews`,
        body
      ),
    onSuccess: (_res, { healthId }) => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY_PREFIX });
      void queryClient.invalidateQueries({
        queryKey: [...QUERY_KEY_PREFIX, "clients", healthId],
      });
    },
  });
}

export function useTriggerMatch() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (healthId: string) =>
      apiClient.post<ApiResponse<MatchCandidate[]>>(
        `${BASE}/clients/${healthId}/match`,
        {}
      ),
    onSuccess: (_res, healthId) => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY_PREFIX });
      void queryClient.invalidateQueries({
        queryKey: [...QUERY_KEY_PREFIX, "clients", healthId],
      });
    },
  });
}

export function useReviewMatchCandidate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      matchId,
      body,
    }: {
      matchId: number;
      body: { outcome: ClientMatchReviewStatus; notes?: string };
    }) =>
      apiClient.post<ApiResponse<MatchCandidate>>(
        `${BASE}/match-candidates/${matchId}/review`,
        body
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY_PREFIX });
    },
  });
}

export function useCreateMergeCase() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      primaryHealthId: string;
      secondaryHealthId: string;
      linkedMatchId?: number;
      linkedDedupCaseId?: number;
      survivorshipSummary?: string;
    }) =>
      apiClient.post<ApiResponse<MergeCase>>(`${BASE}/merge-cases`, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY_PREFIX });
    },
  });
}

export function useMergeCaseDecision() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      mergeCaseId,
      body,
    }: {
      mergeCaseId: string;
      body: { decision: string; survivorshipSummary?: string };
    }) =>
      apiClient.post<ApiResponse<MergeCase>>(
        `${BASE}/merge-cases/${mergeCaseId}/decision`,
        body
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY_PREFIX });
    },
  });
}

export function useAddRelationship() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      healthId,
      body,
    }: {
      healthId: string;
      body: {
        relatedClientHealthId: string;
        relationshipType: ClientRelationshipType;
        startDate?: string;
        endDate?: string;
        notes?: string;
      };
    }) =>
      apiClient.post<ApiResponse<Relationship>>(
        `${BASE}/clients/${healthId}/relationships`,
        body
      ),
    onSuccess: (_res, { healthId }) => {
      void queryClient.invalidateQueries({
        queryKey: [...QUERY_KEY_PREFIX, "clients", healthId],
      });
    },
  });
}

export function useAddAuthorizationLink() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      healthId,
      body,
    }: {
      healthId: string;
      body: {
        authorisationType: string;
        referenceId: string;
        status?: string;
        startDate?: string;
        endDate?: string;
      };
    }) =>
      apiClient.post<ApiResponse<AuthorizationLink>>(
        `${BASE}/clients/${healthId}/authorization-links`,
        body
      ),
    onSuccess: (_res, { healthId }) => {
      void queryClient.invalidateQueries({
        queryKey: [...QUERY_KEY_PREFIX, "clients", healthId],
      });
    },
  });
}

export function useCreateStewardshipAction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      healthId,
      body,
    }: {
      healthId: string;
      body: {
        relatedRegistrationId?: string;
        relatedMergeCaseId?: string;
        actionType: ClientStewardshipActionType;
        owner?: string;
        dueDate?: string;
        completionNotes?: string;
      };
    }) =>
      apiClient.post<ApiResponse<StewardshipAction>>(
        `${BASE}/clients/${healthId}/stewardship-actions`,
        body
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY_PREFIX });
    },
  });
}

export function useRecordCorrection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      healthId,
      body,
    }: {
      healthId: string;
      body: DemographicCorrection;
    }) =>
      apiClient.post<ApiResponse<ClientProfile>>(
        `${BASE}/clients/${healthId}/corrections`,
        body
      ),
    onSuccess: (_res, { healthId }) => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY_PREFIX });
      void queryClient.invalidateQueries({
        queryKey: [...QUERY_KEY_PREFIX, "clients", healthId],
      });
    },
  });
}
