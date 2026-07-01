import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/**
 * Admin facility-absorption read hooks. Backed by policy-protected experience-bff routes under
 * /internal/v1/admin/** which proxy TUSO (the facility system of record). No fixtures — every field
 * comes from the persisted facility_import_run state and the TUSO facility provenance read-model.
 */

export interface FacilityImportRunTotals {
  recordsTotal: number;
  recordsImported: number;
  recordsCreated: number;
  recordsUpdated: number;
  recordsSkipped: number;
  recordsFailed: number;
  warningsCount: number;
  missingFacilityCode: number;
  duplicateFacilityCode: number;
  duplicateFacilityName: number;
  excludedTotal: number;
  acceptableMissing: number;
  withValidCoordinates: number;
}

export interface FacilityImportRunView {
  runId: number;
  packId: string;
  sourceLabel: string | null;
  sourceFileName: string | null;
  status: string;
  dryRun: boolean;
  initiatedBy: string | null;
  startedAt: string | null;
  completedAt: string | null;
  totals: FacilityImportRunTotals;
  qualitySummary?: Record<string, unknown>;
}

export interface FacilityImportRunList {
  count: number;
  runs: FacilityImportRunView[];
}

export interface FacilityImportRow {
  id: number;
  sourceRow: string | null;
  facilityName: string | null;
  facilityCode: string | null;
  province: string | null;
  district: string | null;
  facilityType: string | null;
  ownership: string | null;
  operatingStatus: string | null;
  latitude: number | null;
  longitude: number | null;
  outcome: string;
  importDecision: string | null;
  exclusionReason: string | null;
  duplicateType: string | null;
  duplicateGroupKey: string | null;
  acceptableMissing: Record<string, boolean> | null;
  hasAcceptableMissing: boolean;
  matchedFacilityId: number | null;
  resultFacilityId: number | null;
  reviewDecision: string | null;
  decisionStatus: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewReason: string | null;
  conflictReason: string | null;
}

export interface PagedRows {
  content: FacilityImportRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ImportBucket {
  count: number;
  preview: FacilityImportRow[];
}

export interface FacilityImportReviewBuckets {
  runId: number;
  importedRows: ImportBucket;
  missingFacilityCode: ImportBucket;
  duplicateFacilityCodes: ImportBucket;
  duplicateFacilityNames: ImportBucket;
  acceptableMissingFields: ImportBucket;
  failedRows: ImportBucket;
}

export interface DuplicateGroup {
  duplicateGroupKey: string;
  duplicateType: string;
  size: number;
  rows: FacilityImportRow[];
}

export interface DuplicateGroupsResponse {
  runId: number;
  duplicateType: string;
  groupCount: number;
  groups: DuplicateGroup[];
}

export interface RowFilters {
  outcome?: string;
  duplicateType?: string;
  province?: string;
  district?: string;
  facilityCode?: string;
  facilityName?: string;
  hasAcceptableMissingFields?: boolean;
  page?: number;
  size?: number;
}

export interface FacilityExternalIdentifier {
  system: string;
  value: string;
  issuingAuthority: string;
}

export interface FacilityProvenance {
  identity: {
    internalFacilityId: number | null;
    facilityUuid: string | null;
    facilityCode: string | null;
    facilityCodeLabel: string;
    externalIdentifiers: FacilityExternalIdentifier[];
    sourceLabel: string | null;
    sourceRow: string | null;
    importedFromMaster: boolean;
    lifecycleState: string | null;
    operationalStatus: string | null;
  };
  acceptableMissing: {
    missingLatitude: boolean;
    missingLongitude: boolean;
    missingFacilityType: boolean;
    missingOwnership: boolean;
    missingOperatingStatus: boolean;
  };
  checklist: Array<{ key: string; label: string; status: string; owner: string }>;
  downstreamMaterialisationStatus: string;
}

export function useFacilityImportRuns() {
  return useQuery<ApiResponse<FacilityImportRunList>>({
    queryKey: ["facility-import-runs"],
    queryFn: () =>
      apiClient.get<ApiResponse<FacilityImportRunList>>("/internal/v1/admin/facility-import-runs"),
  });
}

export function useFacilityImportRun(runId: string) {
  return useQuery<ApiResponse<FacilityImportRunView>>({
    queryKey: ["facility-import-run", runId],
    queryFn: () =>
      apiClient.get<ApiResponse<FacilityImportRunView>>(
        `/internal/v1/admin/facility-import-runs/${runId}`,
      ),
    enabled: !!runId,
  });
}

export function useFacilityImportReview(runId: string) {
  return useQuery<ApiResponse<FacilityImportReviewBuckets>>({
    queryKey: ["facility-import-review", runId],
    queryFn: () =>
      apiClient.get<ApiResponse<FacilityImportReviewBuckets>>(
        `/internal/v1/admin/facility-import-runs/${runId}/review`,
      ),
    enabled: !!runId,
  });
}

function rowQuery(filters?: RowFilters): string {
  if (!filters) return "";
  const sp = new URLSearchParams();
  if (filters.outcome) sp.set("outcome", filters.outcome);
  if (filters.duplicateType) sp.set("duplicateType", filters.duplicateType);
  if (filters.province) sp.set("province", filters.province);
  if (filters.district) sp.set("district", filters.district);
  if (filters.facilityCode) sp.set("facilityCode", filters.facilityCode);
  if (filters.facilityName) sp.set("facilityName", filters.facilityName);
  if (filters.hasAcceptableMissingFields != null)
    sp.set("hasAcceptableMissingFields", String(filters.hasAcceptableMissingFields));
  if (filters.page != null) sp.set("page", String(filters.page));
  if (filters.size != null) sp.set("size", String(filters.size));
  const qs = sp.toString();
  return qs ? `?${qs}` : "";
}

export function useFacilityImportRows(runId: string, filters?: RowFilters) {
  return useQuery<ApiResponse<PagedRows>>({
    queryKey: ["facility-import-rows", runId, filters],
    queryFn: () =>
      apiClient.get<ApiResponse<PagedRows>>(
        `/internal/v1/admin/facility-import-runs/${runId}/rows${rowQuery(filters)}`,
      ),
    enabled: !!runId,
  });
}

export function useFacilityImportDuplicates(runId: string, type: string) {
  return useQuery<ApiResponse<DuplicateGroupsResponse>>({
    queryKey: ["facility-import-duplicates", runId, type],
    queryFn: () =>
      apiClient.get<ApiResponse<DuplicateGroupsResponse>>(
        `/internal/v1/admin/facility-import-runs/${runId}/duplicates?type=${encodeURIComponent(type)}`,
      ),
    enabled: !!runId && !!type,
  });
}

// ---- Review-decision mutations ----

const ROWS_BASE = "/internal/v1/admin/facility-import-runs";

function useReviewInvalidation(runId: string) {
  const qc = useQueryClient();
  return () => {
    qc.invalidateQueries({ queryKey: ["facility-import-rows", runId] });
    qc.invalidateQueries({ queryKey: ["facility-import-review", runId] });
    qc.invalidateQueries({ queryKey: ["facility-import-run", runId] });
    qc.invalidateQueries({ queryKey: ["facility-import-duplicates", runId] });
  };
}

export interface CanonicalValues {
  facilityCode?: string;
  facilityName?: string;
  province?: string;
  district?: string;
  latitude?: number;
  longitude?: number;
  facilityType?: string;
  ownership?: string;
  operatingStatus?: string;
  contact?: string;
  bedCapacity?: number;
  reviewNotes?: string;
}

export function useSupplyCode(runId: string) {
  const invalidate = useReviewInvalidation(runId);
  return useMutation({
    mutationFn: (v: { rowId: number; facilityCode: string; reason?: string }) =>
      apiClient.post<ApiResponse<FacilityImportRow>>(`${ROWS_BASE}/${runId}/rows/${v.rowId}/supply-code`, {
        facilityCode: v.facilityCode,
        reason: v.reason,
      }),
    onSuccess: invalidate,
  });
}

export function useRejectRow(runId: string) {
  const invalidate = useReviewInvalidation(runId);
  return useMutation({
    mutationFn: (v: { rowId: number; reason?: string }) =>
      apiClient.post<ApiResponse<FacilityImportRow>>(`${ROWS_BASE}/${runId}/rows/${v.rowId}/reject`, {
        reason: v.reason,
      }),
    onSuccess: invalidate,
  });
}

export function useSkipRow(runId: string) {
  const invalidate = useReviewInvalidation(runId);
  return useMutation({
    mutationFn: (v: { rowId: number; reason?: string }) =>
      apiClient.post<ApiResponse<FacilityImportRow>>(`${ROWS_BASE}/${runId}/rows/${v.rowId}/skip`, {
        reason: v.reason,
      }),
    onSuccess: invalidate,
  });
}

export function useMatchExisting(runId: string) {
  const invalidate = useReviewInvalidation(runId);
  return useMutation({
    mutationFn: (v: { rowId: number; facilityId: number; reason?: string }) =>
      apiClient.post<ApiResponse<FacilityImportRow>>(`${ROWS_BASE}/${runId}/rows/${v.rowId}/match-existing`, {
        facilityId: v.facilityId,
        reason: v.reason,
      }),
    onSuccess: invalidate,
  });
}

export function useResolveDistinct(runId: string) {
  const invalidate = useReviewInvalidation(runId);
  return useMutation({
    mutationFn: (v: { rowId: number; reason: string }) =>
      apiClient.post<ApiResponse<FacilityImportRow>>(`${ROWS_BASE}/${runId}/rows/${v.rowId}/resolve-distinct`, {
        reason: v.reason,
      }),
    onSuccess: invalidate,
  });
}

export function useUpdateCanonical(runId: string) {
  const invalidate = useReviewInvalidation(runId);
  return useMutation({
    mutationFn: (v: { rowId: number; values: CanonicalValues }) =>
      apiClient.patch<ApiResponse<FacilityImportRow>>(
        `${ROWS_BASE}/${runId}/rows/${v.rowId}/canonical-values`,
        v.values,
      ),
    onSuccess: invalidate,
  });
}

export function useApproveRow(runId: string) {
  const invalidate = useReviewInvalidation(runId);
  return useMutation({
    mutationFn: (v: { rowId: number }) =>
      apiClient.post<ApiResponse<FacilityImportRow>>(`${ROWS_BASE}/${runId}/rows/${v.rowId}/approve`),
    onSuccess: invalidate,
  });
}

export function useApplyApproved(runId: string) {
  const invalidate = useReviewInvalidation(runId);
  return useMutation({
    mutationFn: (v?: { rowIds?: number[] }) =>
      apiClient.post<ApiResponse<{ applied: number; skipped: number }>>(
        `${ROWS_BASE}/${runId}/apply-approved`,
        { rowIds: v?.rowIds ?? null },
      ),
    onSuccess: invalidate,
  });
}

export function useFacilityProvenance(facilityId: string) {
  return useQuery<ApiResponse<FacilityProvenance>>({
    queryKey: ["facility-import-provenance", facilityId],
    queryFn: () =>
      apiClient.get<ApiResponse<FacilityProvenance>>(
        `/internal/v1/admin/facilities/${facilityId}/import-provenance`,
      ),
    enabled: !!facilityId,
  });
}
