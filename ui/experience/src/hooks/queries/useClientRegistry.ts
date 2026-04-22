"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface ClientRegistrySummary {
  healthId: string;
  crid: string;
  impiloId?: string | null;
  displayName: string;
  lifecycleStatus: string;
  verificationStatus: string;
  identityAssuranceLevel: number;
  goldenRecord: boolean;
  active: boolean;
  deceased: boolean;
  latestRegistrationType?: string | null;
  latestRegistrationChannel?: string | null;
  openStewardshipActions: number;
  openMatches: number;
}

export interface ClientDashboardSummary {
  totalClients: number;
  provisionalClients: number;
  pendingVerification: number;
  pendingMatchReview: number;
  openStewardshipActions: number;
  mergeCasesOpen: number;
  clientsByStatus: Record<string, number>;
}

export interface ClientProfile {
  master: {
    healthId: string;
    crid: string;
    impiloId?: string | null;
    firstName?: string | null;
    middleName?: string | null;
    lastName?: string | null;
    dateOfBirth?: string | null;
    sex?: string | null;
    lifecycleStatus: string;
    verificationStatus: string;
    identityAssuranceLevel: number;
    activeFlag: boolean;
    deceasedFlag: boolean;
    goldenRecordFlag: boolean;
    demographics: Record<string, unknown>;
    contacts: Record<string, unknown>;
    address: Record<string, unknown>;
    createdAt: string;
    updatedAt: string;
  };
  registrations: Array<{
    registrationId: string;
    registrationType: string;
    initiatedChannel: string;
    initiatedBy: string;
    linkedFacilityId?: number | null;
    linkedProviderId?: string | null;
    linkedServiceContextId?: string | null;
    workflowState: string;
    completionState: string;
    verificationState: string;
    submissionDate?: string | null;
    notes?: string | null;
    provenance: Record<string, unknown>;
  }>;
  identifiers: Array<{
    identifierId: string;
    identifierType: string;
    identifierValue: string;
    status: string;
    primaryFlag: boolean;
    issueDate?: string | null;
    expiryDate?: string | null;
    source?: string | null;
  }>;
  aliases: Array<{
    aliasId: string;
    aliasType: string;
    aliasValue: string;
    source?: string | null;
    confidence?: number | null;
    activeFlag: boolean;
  }>;
  evidence: Array<{
    evidenceId: string;
    registrationId?: string | null;
    evidenceType: string;
    evidenceReference: string;
    verificationState: string;
    verifiedBy?: string | null;
    verifiedAt?: string | null;
    notes?: string | null;
  }>;
  verificationReviews: Array<{
    reviewId: string;
    registrationId?: string | null;
    reviewType: string;
    status: string;
    reviewer?: string | null;
    decision?: string | null;
    notes?: string | null;
    reviewedAt?: string | null;
  }>;
  matchCandidates: Array<{
    matchId: number;
    sourceHealthId: string;
    candidateHealthId: string;
    matchScore: number;
    matchReasonSummary: string;
    status: string;
    generatedAt: string;
    resolvedBy?: string | null;
    resolvedAt?: string | null;
  }>;
  mergeCases: Array<{
    mergeCaseId: string;
    primaryHealthId: string;
    secondaryHealthId: string;
    initiatedBy: string;
    reviewedBy?: string | null;
    decision?: string | null;
    survivorshipSummary?: string | null;
    status: string;
    executedAt?: string | null;
    mergeHistoryId?: number | null;
  }>;
  relationships: Array<{
    relationshipId: string;
    clientHealthId: string;
    relatedClientHealthId: string;
    relationshipType: string;
    startDate?: string | null;
    endDate?: string | null;
    status: string;
    notes?: string | null;
  }>;
  authorizationLinks: Array<{
    authorizationLinkId: string;
    authorisationType: string;
    referenceId: string;
    status: string;
    startDate?: string | null;
    endDate?: string | null;
  }>;
  stewardshipActions: Array<{
    actionId: string;
    actionType: string;
    owner?: string | null;
    dueDate?: string | null;
    status: string;
    completionNotes?: string | null;
    verifiedBy?: string | null;
    verifiedAt?: string | null;
    relatedRegistrationId?: string | null;
    relatedMergeCaseId?: string | null;
  }>;
  statusHistory: Array<{
    statusHistoryId: string;
    previousStatus?: string | null;
    newStatus: string;
    reason?: string | null;
    changedBy: string;
    changedAt: string;
    provenanceContext: Record<string, unknown>;
  }>;
  auditTrail: Array<{
    auditEventId: string;
    actor: string;
    actorType?: string | null;
    action: string;
    targetEntity: string;
    targetId?: string | null;
    beforeState: Record<string, unknown>;
    afterState: Record<string, unknown>;
    correlationId?: string | null;
    provenanceContext: Record<string, unknown>;
    createdAt: string;
  }>;
}

export interface StewardshipWorkspace {
  duplicateQueue: ClientProfile["matchCandidates"];
  verificationQueue: ClientProfile["verificationReviews"];
  stewardshipQueue: ClientProfile["stewardshipActions"];
}

interface PagedItems<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

interface ClientListParams {
  query?: string;
  status?: string;
  verificationState?: string;
  page?: number;
  size?: number;
}

export function useClientRegistryClients(params: ClientListParams) {
  return useQuery({
    queryKey: ["client-registry", "clients", params],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (params.query) searchParams.set("query", params.query);
      if (params.status) searchParams.set("status", params.status);
      if (params.verificationState) searchParams.set("verificationState", params.verificationState);
      searchParams.set("page", String(params.page ?? 0));
      searchParams.set("size", String(params.size ?? 20));
      return apiClient.get<ApiResponse<PagedItems<ClientRegistrySummary>>>(`/internal/v1/client-registry/clients?${searchParams.toString()}`);
    },
  });
}

export function useClientRegistryDashboard() {
  return useQuery({
    queryKey: ["client-registry", "dashboard"],
    queryFn: () => apiClient.get<ApiResponse<ClientDashboardSummary>>("/internal/v1/client-registry/dashboard/summary"),
  });
}

export function useClientProfile(healthId: string | undefined) {
  return useQuery({
    queryKey: ["client-registry", "client", healthId],
    queryFn: () => apiClient.get<ApiResponse<ClientProfile>>(`/internal/v1/client-registry/clients/${healthId}`),
    enabled: !!healthId,
  });
}

export function useClientStewardshipWorkspace() {
  return useQuery({
    queryKey: ["client-registry", "stewardship-workspace"],
    queryFn: () => apiClient.get<ApiResponse<StewardshipWorkspace>>("/internal/v1/client-registry/stewardship/workspace"),
  });
}

function invalidateRegistry(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: ["client-registry"] });
}

export function useCreateClientRegistration() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<ClientProfile>>("/internal/v1/client-registry/registrations", body),
    onSuccess: () => invalidateRegistry(queryClient),
  });
}

export function useSubmitClientRegistration() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (registrationId: string) =>
      apiClient.post<ApiResponse<ClientProfile>>(`/internal/v1/client-registry/registrations/${registrationId}/submit`, {}),
    onSuccess: () => invalidateRegistry(queryClient),
  });
}

export function useAddClientEvidence(healthId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<ClientProfile["evidence"][number]>>(`/internal/v1/client-registry/clients/${healthId}/evidence`, body),
    onSuccess: () => invalidateRegistry(queryClient),
  });
}

export function useRecordVerificationReview(healthId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<ClientProfile["verificationReviews"][number]>>(`/internal/v1/client-registry/clients/${healthId}/verification-reviews`, body),
    onSuccess: () => invalidateRegistry(queryClient),
  });
}

export function useRunClientMatching(healthId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiClient.post<ApiResponse<ClientProfile["matchCandidates"]>>(`/internal/v1/client-registry/clients/${healthId}/match`, {}),
    onSuccess: () => invalidateRegistry(queryClient),
  });
}

export function useReviewClientMatchCandidate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ matchId, body }: { matchId: number; body: Record<string, unknown> }) =>
      apiClient.post<ApiResponse<ClientProfile["matchCandidates"][number]>>(`/internal/v1/client-registry/match-candidates/${matchId}/review`, body),
    onSuccess: () => invalidateRegistry(queryClient),
  });
}

export function useCreateClientMergeCase() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<ClientProfile["mergeCases"][number]>>("/internal/v1/client-registry/merge-cases", body),
    onSuccess: () => invalidateRegistry(queryClient),
  });
}

export function useDecideClientMergeCase() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ mergeCaseId, body }: { mergeCaseId: string; body: Record<string, unknown> }) =>
      apiClient.post<ApiResponse<ClientProfile["mergeCases"][number]>>(`/internal/v1/client-registry/merge-cases/${mergeCaseId}/decision`, body),
    onSuccess: () => invalidateRegistry(queryClient),
  });
}

export function useAddClientRelationship(healthId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<ClientProfile["relationships"][number]>>(`/internal/v1/client-registry/clients/${healthId}/relationships`, body),
    onSuccess: () => invalidateRegistry(queryClient),
  });
}

export function useAddAuthorizationLink(healthId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<ClientProfile["authorizationLinks"][number]>>(`/internal/v1/client-registry/clients/${healthId}/authorization-links`, body),
    onSuccess: () => invalidateRegistry(queryClient),
  });
}

export function useCreateStewardshipAction(healthId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<ClientProfile["stewardshipActions"][number]>>(`/internal/v1/client-registry/clients/${healthId}/stewardship-actions`, body),
    onSuccess: () => invalidateRegistry(queryClient),
  });
}

export function useUpdateStewardshipAction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ actionId, body }: { actionId: string; body: Record<string, unknown> }) =>
      apiClient.post<ApiResponse<ClientProfile["stewardshipActions"][number]>>(`/internal/v1/client-registry/stewardship-actions/${actionId}`, body),
    onSuccess: () => invalidateRegistry(queryClient),
  });
}

export function useRecordClientCorrection(healthId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<ClientProfile>>(`/internal/v1/client-registry/clients/${healthId}/corrections`, body),
    onSuccess: () => invalidateRegistry(queryClient),
  });
}
