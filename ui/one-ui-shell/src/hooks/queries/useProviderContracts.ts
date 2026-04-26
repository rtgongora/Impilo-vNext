/**
 * Payer admin — provider contracts & networks (Experience BFF /internal/v1).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

function unwrapArray(res: unknown): unknown[] {
  if (!res) return [];
  if (Array.isArray(res)) return res;
  const obj = res as { data?: unknown };
  if (Array.isArray(obj.data)) return obj.data;
  return [];
}

function withQuery(path: string, params: Record<string, string | undefined | null>) {
  const search = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === "") continue;
    search.set(k, String(v));
  }
  const qs = search.toString();
  return `${path}${qs ? `?${qs}` : ""}`;
}

export type ProviderContractFilters = {
  provider_id?: string;
  payer_id?: string;
  status?: string;
};

export type ProviderNetworkFilters = {
  payer_id?: string;
  status?: string;
};

export function useProviderContracts(filters?: ProviderContractFilters | null) {
  const f = filters ?? {};
  return useQuery({
    queryKey: ["provider-contracts", f.provider_id ?? "", f.payer_id ?? "", f.status ?? ""],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown[]>(
        withQuery("/internal/v1/provider-contracts", {
          provider_id: f.provider_id,
          payer_id: f.payer_id,
          status: f.status,
        }),
      );
      return unwrapArray(res);
    },
  });
}

export function useProviderNetworks(filters?: ProviderNetworkFilters | null) {
  const f = filters ?? {};
  return useQuery({
    queryKey: ["provider-networks", f.payer_id ?? "", f.status ?? ""],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown[]>(
        withQuery("/internal/v1/provider-networks", {
          payer_id: f.payer_id,
          status: f.status,
        }),
      );
      return unwrapArray(res);
    },
  });
}

export function useNetworkMembers(networkId: string | null) {
  return useQuery({
    queryKey: ["provider-network-members", networkId],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown> | unknown[]>(
        `/internal/v1/provider-networks/${encodeURIComponent(networkId ?? "")}/members`,
      );
      return unwrapArray(res);
    },
    enabled: Boolean(networkId),
  });
}

export function useCreateContract() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<unknown>("/internal/v1/provider-contracts", body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["provider-contracts"] });
    },
  });
}

export function useSuspendContract() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason?: string }) =>
      apiClient.delete<unknown>(
        `/internal/v1/provider-contracts/${encodeURIComponent(id)}`,
        reason ? { reason } : undefined,
      ),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["provider-contracts"] });
    },
  });
}

export function useReinstateContract() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiClient.post<unknown>(`/internal/v1/provider-contracts/${encodeURIComponent(id)}/reinstate`, {}),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["provider-contracts"] });
    },
  });
}

export function useCreateNetwork() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<unknown>("/internal/v1/provider-networks", body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["provider-networks"] });
    },
  });
}

export function useAddNetworkMember() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ networkId, body }: { networkId: string; body: Record<string, unknown> }) =>
      apiClient.post<unknown>(`/internal/v1/provider-networks/${encodeURIComponent(networkId)}/members`, body),
    onSuccess: (_data, vars) => {
      void qc.invalidateQueries({ queryKey: ["provider-networks"] });
      void qc.invalidateQueries({ queryKey: ["provider-network-members", vars.networkId] });
    },
  });
}

export function useRemoveNetworkMember() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ networkId, providerId }: { networkId: string; providerId: string }) =>
      apiClient.delete<unknown>(
        `/internal/v1/provider-networks/${encodeURIComponent(networkId)}/members/${encodeURIComponent(providerId)}`,
      ),
    onSuccess: (_data, vars) => {
      void qc.invalidateQueries({ queryKey: ["provider-networks"] });
      void qc.invalidateQueries({ queryKey: ["provider-network-members", vars.networkId] });
    },
  });
}
