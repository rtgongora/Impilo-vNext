import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/**
 * Facility legitimacy composite read hook (IATG WS-E). Backed by the fail-closed experience-bff
 * proxy {@code GET /internal/v1/facilities/{id}/status-composite}, which passes the TUSO composite
 * (regulatory status + certificates + per-source verdicts + derived platform-access verdict)
 * through unchanged. No fixtures — every field comes from the TUSO source-legitimacy model.
 */

export type FacilityLegitimacySource =
  | "HPA_LEGAL"
  | "MINISTRY_OPERATIONAL"
  | "PLATFORM_OPERATIONAL"
  | string;

export type FacilityLegitimacyStatus =
  | "REGISTERED_CURRENT"
  | "EXPIRED"
  | "SUSPENDED"
  | "KNOWN_NOT_COMPLIANT"
  | "PENDING_VERIFICATION"
  | "NOT_FOUND"
  | "GOVERNMENT_OPERATIONAL_EXCEPTION"
  | string;

export interface FacilitySourceLegitimacyView {
  source: FacilityLegitimacySource;
  status: FacilityLegitimacyStatus;
  asOf: string | null;
  evidenceRef: string | null;
  allowedOnPlatform: boolean;
  reason: string | null;
  recordedBy: string | null;
}

export interface FacilityCertificateSummary {
  certificateId: string;
  certificateType: string;
  certificateNumber: string;
  status: string;
  issueDate: string | null;
  expiryDate: string | null;
  issuedUnderAuthority: string | null;
}

export interface FacilityStatusComposite {
  facilityId: string;
  facilityCode: string | null;
  facilityName: string | null;
  regulatoryStatus: string | null;
  regulatoryStatusUpdatedAt: string | null;
  certificates: FacilityCertificateSummary[];
  sourceLegitimacy: FacilitySourceLegitimacyView[];
  platformAccessAllowed: boolean;
  reasons: string[];
}

export function useFacilityStatusComposite(facilityId: string) {
  return useQuery<ApiResponse<FacilityStatusComposite>>({
    queryKey: ["facility-status-composite", facilityId],
    queryFn: () =>
      apiClient.get<ApiResponse<FacilityStatusComposite>>(
        `/internal/v1/facilities/${facilityId}/status-composite`,
      ),
    enabled: !!facilityId,
  });
}
