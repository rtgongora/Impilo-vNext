/**
 * Experience UI — Facilities Query Hook
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type FacilityTier =
  | "COMMUNITY"
  | "HEALTH_POST"
  | "CLINIC"
  | "POLYCLINIC"
  | "RURAL_HOSPITAL"
  | "DISTRICT_OR_MISSION_HOSPITAL"
  | "GENERAL_HOSPITAL"
  | "PROVINCIAL_OR_TERTIARY_HOSPITAL"
  | "CENTRAL_HOSPITAL"
  | "QUINARY_HOSPITAL"
  | "VIRTUAL_HOSPITAL";

export type FacilityDeploymentMode =
  | "CENTRAL_ONLY"
  | "SHARED_POD"
  | "DEDICATED_POD"
  | "EDGE_ASSISTED"
  | "VIRTUAL_ONLY";

export type ContinuityClass =
  | "CONNECTED_TOLERANT"
  | "LOCAL_EXECUTION_REQUIRED"
  | "EDGE_CRITICAL";

export type WorkflowArchetype =
  | "COMMUNITY_OUTREACH"
  | "PRIMARY_CARE"
  | "AMBULATORY_MULTI_SERVICE"
  | "HOSPITAL_INPATIENT"
  | "REFERRAL_AND_SPECIALIST"
  | "VIRTUAL_CARE_NETWORK";

export interface FacilityOperatingModel {
  facilityTier?: FacilityTier;
  deploymentMode?: FacilityDeploymentMode;
  continuityClass?: ContinuityClass;
  workflowArchetype?: WorkflowArchetype;
}

export interface FacilityResource {
  id: string;
  type: "facility";
  attributes: {
    name: string;
    code: string;
    facilityType: string;
    status: string;
    capabilities: string[];
    operatingModel?: FacilityOperatingModel;
    facilityTier?: FacilityTier;
    deploymentMode?: FacilityDeploymentMode;
    continuityClass?: ContinuityClass;
    workflowArchetype?: WorkflowArchetype;
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
