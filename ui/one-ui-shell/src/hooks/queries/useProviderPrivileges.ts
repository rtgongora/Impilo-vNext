/**
 * Experience UI — Provider Privileges (Facility Affiliations) Hook
 *
 * Fetches provider-facility privilege data from VARAPI via BFF proxy.
 * Privileges represent the facilities where a provider is credentialed to work.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface ProviderPrivilege {
  id: string;
  facilityId: string;
  workspaceId?: string;
  scope: string;
  privilegeType: string;
  status: string;
  validFrom?: string;
  validTo?: string;
  [key: string]: unknown;
}

type PrivilegesResponse = ApiResponse<ProviderPrivilege[]>;

export function useProviderPrivileges(providerId: string | undefined) {
  return useQuery<PrivilegesResponse>({
    queryKey: ["provider-privileges", providerId],
    queryFn: () =>
      apiClient.get<PrivilegesResponse>(
        `/internal/v1/registry/providers/${providerId}/privileges`
      ),
    enabled: !!providerId,
  });
}
