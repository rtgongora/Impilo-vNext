/**
 * Experience UI — Facilities Query Hook
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface FacilityResource {
  id: string;
  type: "facility";
  attributes: {
    name: string;
    code: string;
    facilityType: string;
    status: string;
    capabilities: string[];
    [key: string]: unknown;
  };
}

interface FacilitiesParams {
  search?: string;
  status?: string;
  page?: number;
}

type FacilitiesResponse = ApiResponse<FacilityResource[]>;

export function useFacilities(params?: FacilitiesParams) {
  return useQuery<FacilitiesResponse>({
    queryKey: ["facilities", params],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (params?.search) searchParams.set("search", params.search);
      if (params?.status) searchParams.set("status", params.status);
      if (params?.page !== undefined) searchParams.set("page", String(params.page));

      const qs = searchParams.toString();
      const path = `/internal/v1/facilities${qs ? `?${qs}` : ""}`;
      return apiClient.get<FacilitiesResponse>(path);
    },
  });
}
