/**
 * Facility admin mutations — create/update via BFF admin routes.
 */

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import type { FacilityResource } from "@/hooks/queries/useFacilities";

export type FacilityLevel = "TERTIARY" | "SECONDARY" | "PRIMARY" | "CLINIC";

export type FacilityOwnership = "PUBLIC" | "PRIVATE" | "NGO" | "FAITH_BASED";

export interface FacilityAdminPayload {
  facilityCode: string;
  name: string;
  type: string;
  level: FacilityLevel;
  ownership: FacilityOwnership;
  province: string;
  district: string;
  latitude: number;
  longitude: number;
}

type FacilityResponse = ApiResponse<FacilityResource>;

export function useCreateFacility() {
  const queryClient = useQueryClient();
  return useMutation<FacilityResponse, unknown, FacilityAdminPayload>({
    mutationFn: (body) =>
      apiClient.post<FacilityResponse>("/internal/v1/admin/facilities", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["facilities"] });
    },
  });
}

export function useUpdateFacility(facilityId: string) {
  const queryClient = useQueryClient();
  return useMutation<FacilityResponse, unknown, FacilityAdminPayload>({
    mutationFn: (body) =>
      apiClient.put<FacilityResponse>(
        `/internal/v1/admin/facilities/${facilityId}`,
        body,
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["facilities"] });
      void queryClient.invalidateQueries({ queryKey: ["facilities", facilityId] });
    },
  });
}
