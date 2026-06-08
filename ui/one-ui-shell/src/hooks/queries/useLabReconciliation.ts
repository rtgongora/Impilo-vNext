/**
 * Experience UI — Lab reconciliation hooks (OROS via BFF)
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface LabReconciliationItem {
  recId: string;
  orderId?: string;
  externalKey?: string;
  externalSystem?: string;
  patientCpid?: string;
  confidence?: number;
  status: string;
  opsNotes?: string;
  matchedAt?: string;
  resolvedBy?: string;
  createdAt?: string;
  [key: string]: unknown;
}

export interface LabReconciliationSummary {
  pending: number;
  matched: number;
  resolved: number;
  total: number;
}

export interface LabReconciliationData {
  items: LabReconciliationItem[];
  page: number;
  size: number;
  total_elements: number;
  summary: LabReconciliationSummary;
}

type LabReconciliationResponse = ApiResponse<LabReconciliationData>;
type LabReconciliationActionResponse = ApiResponse<LabReconciliationItem>;

export interface LabReconciliationQueryParams {
  page?: number;
  size?: number;
}

export function useLabReconciliation(params: LabReconciliationQueryParams = {}) {
  const page = params.page ?? 0;
  const size = params.size ?? 50;
  const qs = new URLSearchParams({
    page: String(page),
    size: String(size),
  }).toString();

  return useQuery<LabReconciliationResponse>({
    queryKey: ["lab-reconciliation", page, size],
    queryFn: () =>
      apiClient.get<LabReconciliationResponse>(`/internal/v1/lab-reconciliation?${qs}`),
    staleTime: 15_000,
  });
}

export function useMatchLabReconciliation() {
  const queryClient = useQueryClient();

  return useMutation<LabReconciliationActionResponse, unknown, { recId: string; orderId: string }>({
    mutationFn: ({ recId, orderId }) =>
      apiClient.post<LabReconciliationActionResponse>(
        `/internal/v1/lab-reconciliation/${encodeURIComponent(recId)}/match`,
        { order_id: orderId },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["lab-reconciliation"] });
    },
  });
}

export function useResolveLabReconciliation() {
  const queryClient = useQueryClient();

  return useMutation<LabReconciliationActionResponse, unknown, { recId: string; notes?: string }>({
    mutationFn: ({ recId, notes }) =>
      apiClient.post<LabReconciliationActionResponse>(
        `/internal/v1/lab-reconciliation/${encodeURIComponent(recId)}/resolve`,
        { notes: notes ?? "" },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["lab-reconciliation"] });
    },
  });
}
