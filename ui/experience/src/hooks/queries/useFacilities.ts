import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface FacilityOperatingModel {
  facilityTier?: string | null;
  deploymentMode?: string | null;
  continuityClass?: string | null;
  workflowArchetype?: string | null;
}

export interface FacilityResourceAttributes {
  name: string;
  code: string;
  facilityType: string;
  district?: string | null;
  province?: string | null;
  region?: string | null;
  status: string;
  latitude?: number | null;
  longitude?: number | null;
  bedCount?: number | null;
  capabilities: string[];
  operatingHours?: string | null;
  contactPhone?: string | null;
  facilityTier?: string | null;
  deploymentMode?: string | null;
  continuityClass?: string | null;
  workflowArchetype?: string | null;
  operatingModel?: FacilityOperatingModel;
  [key: string]: unknown;
}

export interface FacilityResource {
  id: string;
  type: string;
  attributes: FacilityResourceAttributes;
}

interface FacilitiesParams {
  search?: string;
  status?: string;
  facilityType?: string;
  province?: string;
  page?: number;
  size?: number;
}

type FacilitiesResponse = ApiResponse<FacilityResource[]>;

export function useFacilities(params?: FacilitiesParams) {
  return useQuery<FacilitiesResponse>({
    queryKey: ["facilities", params],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (params?.search) searchParams.set("search", params.search);
      if (params?.status) searchParams.set("status", params.status);
      if (params?.facilityType) searchParams.set("facility_type", params.facilityType);
      if (params?.province) searchParams.set("province", params.province);
      if (params?.page !== undefined) searchParams.set("page", String(params.page));
      if (params?.size !== undefined) searchParams.set("size", String(params.size));

      const qs = searchParams.toString();
      const path = `/internal/v1/facilities${qs ? `?${qs}` : ""}`;
      return apiClient.get<FacilitiesResponse>(path);
    },
  });
}
