import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/**
 * Facility classification reconciliation hooks. Backed by policy-protected experience-bff routes
 * under /internal/v1/admin/facility-classification/** which proxy TUSO (the facility system of
 * record). No fixtures — every field comes from the persisted facility_regulatory_profile state and
 * the versioned mapping rule pack. A suggestion is never treated as a confirmed classification.
 */

const BASE = "/internal/v1/admin/facility-classification";

export interface ClassificationSummary {
  total: number;
  byStatus: Record<string, number>;
  byHpaClass: Record<string, number>;
}

export interface ClassificationProfile {
  profileId: string;
  facilityId: number;
  sourceFacilityType: string | null;
  sourceOwnership: string | null;
  sourceLevel: string | null;
  sourceBedCapacity: string | null;
  canonicalType: string | null;
  ownershipAuthority: string | null;
  hpaRoute: string | null;
  careSetting: string | null;
  mobility: string | null;
  specialistScope: string | null;
  bedCountClaimed: number | null;
  bedCountConfirmed: number | null;
  bedBand: string | null;
  bedBandBasis: string | null;
  hpaClass: string;
  hpaClassJustification: string | null;
  hpaClassificationLabel: string | null;
  classificationStatus: string;
  dimensionStatuses: Record<string, unknown> | null;
  suggestion: Array<Record<string, unknown>> | null;
  requiredFacts: string[] | null;
  sourceFieldsUsed: Record<string, unknown> | null;
  unitsReviewStatus: string;
  confirmedBy: string | null;
  confirmedAt: string | null;
  reclassificationReviewRequired: boolean;
}

export interface ClassificationHistory {
  historyId: number;
  changeKind: string;
  prevSnapshot: Record<string, unknown> | null;
  newSnapshot: Record<string, unknown> | null;
  actor: string | null;
  runId: number | null;
  note: string | null;
  createdAt: string;
}

export interface ClassificationProfileDetail {
  profile: ClassificationProfile;
  history: ClassificationHistory[];
}

export interface PagedProfiles {
  items: ClassificationProfile[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface EnrichmentRun {
  id: number;
  dryRun: boolean;
  recordsTotal: number;
  recordsCreated: number;
  recordsUpdated: number;
  recordsUnchanged: number;
  recordsPreservedHuman: number;
  recordsFailed: number;
  countsByStatus: Record<string, number> | null;
  estateAudit: Record<string, unknown> | null;
  exceptionReport: Array<Record<string, unknown>> | null;
  status: string;
  startedAt: string;
  completedAt: string | null;
}

export interface UnitCandidate {
  candidateId: string;
  facilityId: number;
  suggestedUnitType: string;
  origin: string;
  servicePresence: string;
  status: string;
  rationale: string | null;
  createdUnitId: number | null;
  createdAt: string;
}

export interface ProfileFilters {
  status?: string;
  hpaClass?: string;
  canonicalType?: string;
  sourceType?: string;
  page?: number;
  size?: number;
}

export function useClassificationSummary() {
  return useQuery<ApiResponse<ClassificationSummary>>({
    queryKey: ["classification-summary"],
    queryFn: () => apiClient.get(`${BASE}/profiles/summary`),
  });
}

export function useClassificationProfiles(filters: ProfileFilters) {
  const params = new URLSearchParams();
  params.set("page", String(filters.page ?? 0));
  params.set("size", String(filters.size ?? 50));
  if (filters.status) params.set("status", filters.status);
  if (filters.hpaClass) params.set("hpaClass", filters.hpaClass);
  if (filters.canonicalType) params.set("canonicalType", filters.canonicalType);
  if (filters.sourceType) params.set("sourceType", filters.sourceType);
  return useQuery<ApiResponse<PagedProfiles>>({
    queryKey: ["classification-profiles", filters],
    queryFn: () => apiClient.get(`${BASE}/profiles?${params.toString()}`),
  });
}

export function useClassificationProfile(facilityId: number | null) {
  return useQuery<ApiResponse<ClassificationProfileDetail>>({
    queryKey: ["classification-profile", facilityId],
    queryFn: () => apiClient.get(`${BASE}/facilities/${facilityId}`),
    enabled: facilityId != null,
  });
}

export function useEnrichmentRuns() {
  return useQuery<ApiResponse<EnrichmentRun[]>>({
    queryKey: ["classification-runs"],
    queryFn: () => apiClient.get(`${BASE}/enrichment-runs`),
  });
}

export function useUnitCandidates(facilityId: number | null) {
  return useQuery<ApiResponse<UnitCandidate[]>>({
    queryKey: ["classification-unit-candidates", facilityId],
    queryFn: () => apiClient.get(`${BASE}/facilities/${facilityId}/unit-candidates`),
    enabled: facilityId != null,
  });
}

function useInvalidate() {
  const qc = useQueryClient();
  return () => {
    void qc.invalidateQueries({ queryKey: ["classification-summary"] });
    void qc.invalidateQueries({ queryKey: ["classification-profiles"] });
    void qc.invalidateQueries({ queryKey: ["classification-profile"] });
  };
}

export function useRunEnrichment() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (dryRun: boolean) => apiClient.post(`${BASE}/enrichment-runs`, { dryRun }),
    onSuccess: invalidate,
  });
}

export function useSupplyFacts(facilityId: number) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (body: { bedCountConfirmed?: number; careSetting?: string; mobility?: string; specialistScope?: string; evidenceNote?: string }) =>
      apiClient.post(`${BASE}/facilities/${facilityId}/facts`, body),
    onSuccess: invalidate,
  });
}

export function useConfirmClassification(facilityId: number) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (note?: string) => apiClient.post(`${BASE}/facilities/${facilityId}/confirm`, { note }),
    onSuccess: invalidate,
  });
}

export function useCorrectClassification(facilityId: number) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (body: { hpaClass: string; hpaClassificationLabel?: string; justification?: string; note?: string }) =>
      apiClient.post(`${BASE}/facilities/${facilityId}/correct`, body),
    onSuccess: invalidate,
  });
}

export function useBulkConfirm() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (statuses: string[]) => apiClient.post(`${BASE}/bulk-confirm`, { statuses }),
    onSuccess: invalidate,
  });
}

export function useConfirmUnitCandidate(facilityId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { candidateId: string; name?: string; serviceLine?: string; picRequired?: boolean }) =>
      apiClient.post(`${BASE}/unit-candidates/${args.candidateId}/confirm`, args),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["classification-unit-candidates", facilityId] }),
  });
}

export function useProposeUnitCandidate(facilityId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { suggestedUnitType: string; rationale?: string; evidenceRef?: string }) =>
      apiClient.post(`${BASE}/facilities/${facilityId}/unit-candidates`, body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["classification-unit-candidates", facilityId] }),
  });
}
