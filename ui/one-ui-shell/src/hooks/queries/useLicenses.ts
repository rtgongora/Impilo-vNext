/**
 * Experience UI — Provider License Query Hook
 *
 * Fetches license data from the BFF registry endpoint,
 * which proxies to VARAPI's license service.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface LicenseResource {
  id: string;
  status: "ACTIVE" | "SUSPENDED" | "REVOKED" | "EXPIRED";
  licenseNumber?: string;
  issuedBy?: string;
  validFrom?: string;
  validTo?: string;
  cadre?: string;
  [key: string]: unknown;
}

type LicensesResponse = ApiResponse<LicenseResource[]>;

export function useProviderLicenses(providerId: string | undefined) {
  return useQuery<LicensesResponse>({
    queryKey: ["provider-licenses", providerId],
    queryFn: () =>
      apiClient.get<LicensesResponse>(
        `/internal/v1/registry/providers/${providerId}/licenses`
      ),
    enabled: !!providerId,
  });
}

/**
 * Checks if the current provider has at least one active license.
 */
export function hasActiveLicense(licenses: LicenseResource[]): boolean {
  return licenses.some((l) => l.status === "ACTIVE");
}
