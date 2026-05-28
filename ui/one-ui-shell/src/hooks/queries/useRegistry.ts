/**
 * Experience UI — Registry Query Hooks
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface ProviderResource {
  id: string;
  type: "provider";
  attributes: {
    displayName: string;
    registrationNumber: string;
    speciality: string;
    status: string;
    [key: string]: unknown;
  };
}

interface ProvidersParams {
  search?: string;
  status?: string;
}

type ProvidersResponse = ApiResponse<ProviderResource[]>;
type ProviderResponse = ApiResponse<ProviderResource>;

export function useProviders(params?: ProvidersParams) {
  return useQuery<ProvidersResponse>({
    queryKey: ["providers", params],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (params?.search) searchParams.set("search", params.search);
      if (params?.status) searchParams.set("status", params.status);

      const qs = searchParams.toString();
      const path = `/internal/v1/registry/providers${qs ? `?${qs}` : ""}`;
      return apiClient.get<ProvidersResponse>(path);
    },
  });
}

export function useChangeProviderStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { id: string; newStatus: string; reason?: string }) =>
      apiClient.post<ProviderResponse>(`/internal/v1/registry/providers/${input.id}/status`, {
        newStatus: input.newStatus,
        reason: input.reason ?? "",
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["providers"] }),
  });
}

export function useProvider(id: string) {
  return useQuery<ProviderResponse>({
    queryKey: ["providers", id],
    queryFn: () => apiClient.get<ProviderResponse>(`/internal/v1/registry/providers/${id}`),
    enabled: !!id,
  });
}
