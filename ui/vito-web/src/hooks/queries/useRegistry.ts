import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { vitoApiClient } from "@/lib/apiClient";
import type {
  RegistryModeResponse,
  ProvisionalIdResponse,
  ProvisionalIdRecord,
  OpenCrMatchResponse,
  DedupCase,
  PagedResponse,
} from "@/types/vito";

export interface PendingProvisionalParams {
  facilityId?: string;
  page?: number;
  size?: number;
}

export interface RegistryDedupParams {
  page?: number;
  size?: number;
}

export interface OpenCrMatchPayload {
  givenName?: string;
  familyName?: string;
  dateOfBirth?: string;
  gender?: string;
}

export const registryKeys = {
  all: ["registry"] as const,
  mode: () => [...registryKeys.all, "mode"] as const,
  provisional: (params: PendingProvisionalParams) =>
    [...registryKeys.all, "provisional", params] as const,
  dedup: (params: RegistryDedupParams) => [...registryKeys.all, "dedup", params] as const,
};

export function useRegistryMode() {
  return useQuery({
    queryKey: registryKeys.mode(),
    queryFn: () => vitoApiClient.get<RegistryModeResponse>("/v1/registry/mode"),
    staleTime: 30_000,
  });
}

export function useIssueProvisional() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      facilityId,
      expiryHours,
    }: {
      facilityId: string;
      expiryHours?: number;
    }) =>
      vitoApiClient.post<ProvisionalIdResponse>("/v1/registry/provisional/issue", {
        facilityId,
        expiryHours,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: registryKeys.all });
    },
  });
}

export function usePendingProvisional(params: PendingProvisionalParams = {}) {
  return useQuery({
    queryKey: registryKeys.provisional(params),
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (params.facilityId) searchParams.set("facilityId", params.facilityId);
      if (params.page !== undefined) searchParams.set("page", String(params.page));
      if (params.size !== undefined) searchParams.set("size", String(params.size));
      const qs = searchParams.toString();
      return vitoApiClient.get<PagedResponse<ProvisionalIdRecord>>(
        `/v1/registry/provisional/pending${qs ? `?${qs}` : ""}`
      );
    },
    staleTime: 30_000,
  });
}

export function useReconcileProvisional() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      provisionalRef,
      healthId,
    }: {
      provisionalRef: string;
      healthId: string;
    }) =>
      vitoApiClient.post(`/v1/registry/provisional/${provisionalRef}/reconcile`, { healthId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: registryKeys.all });
    },
  });
}

export function useRegistryDedupPending(params: RegistryDedupParams = {}) {
  return useQuery({
    queryKey: registryKeys.dedup(params),
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (params.page !== undefined) searchParams.set("page", String(params.page));
      if (params.size !== undefined) searchParams.set("size", String(params.size));
      const qs = searchParams.toString();
      return vitoApiClient.get<PagedResponse<DedupCase>>(
        `/v1/registry/dedup/pending${qs ? `?${qs}` : ""}`
      );
    },
    staleTime: 30_000,
  });
}

export function useResolveRegistryDedup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      caseId,
      action,
      survivorCpid,
    }: {
      caseId: string;
      action: "MERGE" | "REJECT" | "DEFER";
      survivorCpid?: string;
    }) =>
      vitoApiClient.post(`/v1/registry/dedup/${caseId}/resolve`, { action, survivorCpid }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: registryKeys.all });
    },
  });
}

export function useOpenCrMatch() {
  return useMutation({
    mutationFn: (payload: OpenCrMatchPayload) =>
      vitoApiClient.post<OpenCrMatchResponse>("/v1/registry/opencr/match", payload),
  });
}
