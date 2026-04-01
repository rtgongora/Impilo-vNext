import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { vitoApiClient } from "@/lib/apiClient";
import type {
  Client,
  ClientStatus,
  PagedResponse,
  GenericResponse,
  VerifyClientPayload,
} from "@/types/vito";

export interface ListClientsParams {
  page?: number;
  size?: number;
  q?: string;
  status?: ClientStatus | "ALL";
}

export const clientKeys = {
  all: ["clients"] as const,
  list: (params: ListClientsParams) => [...clientKeys.all, "list", params] as const,
  detail: (healthId: string) => [...clientKeys.all, "detail", healthId] as const,
};

export function useListClients(params: ListClientsParams = {}) {
  return useQuery({
    queryKey: clientKeys.list(params),
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (params.page !== undefined) searchParams.set("page", String(params.page));
      if (params.size !== undefined) searchParams.set("size", String(params.size));
      if (params.q) searchParams.set("q", params.q);
      if (params.status && params.status !== "ALL") searchParams.set("status", params.status);
      const qs = searchParams.toString();
      return vitoApiClient.get<PagedResponse<Client>>(`/v1/clients${qs ? `?${qs}` : ""}`);
    },
    staleTime: 30_000,
  });
}

export function useClientDetail(healthId: string) {
  return useQuery({
    queryKey: clientKeys.detail(healthId),
    queryFn: () => vitoApiClient.get<Client>(`/v1/clients/${healthId}`),
    enabled: !!healthId,
    staleTime: 30_000,
  });
}

export function useVerifyClient() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ healthId, payload }: { healthId: string; payload: VerifyClientPayload }) =>
      vitoApiClient.post<GenericResponse>(`/v1/clients/${healthId}/verify`, payload),
    onSuccess: (_, { healthId }) => {
      qc.invalidateQueries({ queryKey: clientKeys.detail(healthId) });
      qc.invalidateQueries({ queryKey: clientKeys.all });
    },
  });
}

export function useDeactivateClient() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ healthId, reason }: { healthId: string; reason: string }) =>
      vitoApiClient.post<GenericResponse>(`/v1/clients/${healthId}/deactivate`, { reason }),
    onSuccess: (_, { healthId }) => {
      qc.invalidateQueries({ queryKey: clientKeys.detail(healthId) });
      qc.invalidateQueries({ queryKey: clientKeys.all });
    },
  });
}

export function useMarkDeceased() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      healthId,
      dateOfDeath,
      source,
    }: {
      healthId: string;
      dateOfDeath: string;
      source?: string;
    }) =>
      vitoApiClient.post<GenericResponse>(`/v1/clients/${healthId}/deceased`, {
        dateOfDeath,
        source,
      }),
    onSuccess: (_, { healthId }) => {
      qc.invalidateQueries({ queryKey: clientKeys.detail(healthId) });
      qc.invalidateQueries({ queryKey: clientKeys.all });
    },
  });
}
