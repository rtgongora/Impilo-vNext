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

export interface ReconciliationQueueSnapshot {
  items?: unknown[];
  total_elements?: number;
  totalElements?: number;
  [key: string]: unknown;
}

export function useProviderReconciliationQueue(status?: string) {
  return useQuery<ReconciliationQueueSnapshot>({
    queryKey: ["registry-reconciliation-queue", status],
    queryFn: async () => {
      const params = new URLSearchParams({ page: "0", size: "20" });
      if (status) params.set("status", status);
      const response = await apiClient.get<{ data: ReconciliationQueueSnapshot }>(
        `/internal/v1/registry/reconciliation/queue?${params.toString()}`,
      );
      const payload = response.data;
      if (payload && typeof payload === "object" && "data" in (payload as object)) {
        return ((payload as { data: ReconciliationQueueSnapshot }).data) ?? {};
      }
      return (payload as ReconciliationQueueSnapshot) ?? {};
    },
  });
}

export function useDecideReconciliationCase() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { caseId: number; action: "ACCEPT" | "REJECT" | "MERGE" | "DEFER"; reason?: string }) =>
      apiClient.post<{ data: unknown }>(`/internal/v1/registry/reconciliation/${input.caseId}/decision`, {
        action: input.action,
        reason: input.reason ?? "",
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["registry-reconciliation-queue"] }),
  });
}
