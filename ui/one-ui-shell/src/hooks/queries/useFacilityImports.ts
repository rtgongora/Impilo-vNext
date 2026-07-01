import { useQuery } from "@tanstack/react-query";
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

export interface FacilityImportReview {
  contract: string;
  runId: number;
  buckets: {
    missing_facility_code: number;
    duplicate_facility_code: number;
    duplicate_facility_name: number;
    acceptable_missing: number;
  };
  rowLevelDetailAvailable: boolean;
  message: string;
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
  return useQuery<ApiResponse<FacilityImportReview>>({
    queryKey: ["facility-import-review", runId],
    queryFn: () =>
      apiClient.get<ApiResponse<FacilityImportReview>>(
        `/internal/v1/admin/facility-import-runs/${runId}/review`,
      ),
    enabled: !!runId,
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
