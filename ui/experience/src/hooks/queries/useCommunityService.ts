/**
 * Experience UI — Community (Clinical Plane) Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type CommunityUnitsResponse = ApiResponse<unknown>;
type CommunityUnitResponse = ApiResponse<unknown>;
type OutreachVisitsResponse = ApiResponse<unknown>;
type ChwAssignmentsResponse = ApiResponse<unknown>;
type OutreachVisitResponse = ApiResponse<unknown>;

export function useCommunityUnits() {
  return useQuery<CommunityUnitsResponse>({
    queryKey: ["community-units"],
    queryFn: () => apiClient.get<CommunityUnitsResponse>("/internal/v1/community/units"),
  });
}

export function useCommunityUnit(id?: string | number | null) {
  return useQuery<CommunityUnitResponse>({
    queryKey: ["community-unit", id],
    queryFn: () => apiClient.get<CommunityUnitResponse>(`/internal/v1/community/units/${id}`),
    enabled: !!id,
  });
}

export function useOutreachVisits(unitId?: string | number | null) {
  return useQuery<OutreachVisitsResponse>({
    queryKey: ["community-visits", unitId ?? null],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (unitId != null && unitId !== "") searchParams.set("unit_id", String(unitId));
      const qs = searchParams.toString();
      const path = `/internal/v1/community/visits${qs ? `?${qs}` : ""}`;
      return apiClient.get<OutreachVisitsResponse>(path);
    },
  });
}

export function useChwAssignments(unitId?: string | number | null) {
  return useQuery<ChwAssignmentsResponse>({
    queryKey: ["community-assignments", unitId ?? null],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (unitId != null && unitId !== "") searchParams.set("unit_id", String(unitId));
      const qs = searchParams.toString();
      const path = `/internal/v1/community/assignments${qs ? `?${qs}` : ""}`;
      return apiClient.get<ChwAssignmentsResponse>(path);
    },
  });
}

export function useCreateCommunityUnit() {
  const queryClient = useQueryClient();

  return useMutation<CommunityUnitResponse, unknown, Record<string, unknown>>({
    mutationFn: (body) =>
      apiClient.post<CommunityUnitResponse>("/internal/v1/community/units", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["community-units"] });
    },
  });
}

export function useCreateOutreachVisit() {
  const queryClient = useQueryClient();

  return useMutation<OutreachVisitResponse, unknown, Record<string, unknown>>({
    mutationFn: (body) =>
      apiClient.post<OutreachVisitResponse>("/internal/v1/community/visits", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["community-visits"] });
    },
  });
}

export function useStartVisit() {
  const queryClient = useQueryClient();

  return useMutation<OutreachVisitResponse, unknown, { id: string | number; body?: unknown }>({
    mutationFn: ({ id, body }) =>
      apiClient.put<OutreachVisitResponse>(`/internal/v1/community/visits/${id}/start`, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["community-visits"] });
    },
  });
}

export function useCompleteVisit() {
  const queryClient = useQueryClient();

  return useMutation<OutreachVisitResponse, unknown, { id: string | number; body?: unknown }>({
    mutationFn: ({ id, body }) =>
      apiClient.put<OutreachVisitResponse>(`/internal/v1/community/visits/${id}/complete`, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["community-visits"] });
    },
  });
}

export function useCreateChwAssignment() {
  const queryClient = useQueryClient();

  return useMutation<ChwAssignmentsResponse, unknown, Record<string, unknown>>({
    mutationFn: (body) =>
      apiClient.post<ChwAssignmentsResponse>("/internal/v1/community/assignments", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["community-assignments"] });
    },
  });
}
