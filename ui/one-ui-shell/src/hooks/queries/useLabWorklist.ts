/**
 * Experience UI — Lab facility worklist hooks (OROS via BFF)
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface LabWorklistItem {
  orderId: string;
  orderType?: string;
  status: string;
  priority?: string;
  patientCpid?: string;
  facilityId?: string;
  placedAt?: string;
  placedBy?: string;
  encounterRef?: string;
  ziboOrderCode?: string;
  clinicalNotes?: string;
  updatedAt?: string;
  [key: string]: unknown;
}

export interface LabWorklistSummary {
  pending_collection: number;
  in_progress: number;
  completed: number;
  urgent: number;
  total: number;
}

export interface LabWorklistData {
  items: LabWorklistItem[];
  page: number;
  size: number;
  total_elements: number;
  summary: LabWorklistSummary;
}

type LabWorklistResponse = ApiResponse<LabWorklistData>;
type LabWorklistActionResponse = ApiResponse<LabWorklistItem>;

export interface LabWorklistQueryParams {
  facilityId?: string;
  workspaceId?: string;
  type?: string;
  status?: string;
  priority?: string;
  page?: number;
  size?: number;
}

function buildLabWorklistQuery(params: LabWorklistQueryParams): string {
  const qp = new URLSearchParams();
  if (params.facilityId) qp.set("facility_id", params.facilityId);
  if (params.workspaceId) qp.set("workspace_id", params.workspaceId);
  if (params.type) qp.set("type", params.type);
  if (params.status) qp.set("status", params.status);
  if (params.priority) qp.set("priority", params.priority);
  qp.set("page", String(params.page ?? 0));
  qp.set("size", String(params.size ?? 20));
  return qp.toString();
}

export function useLabWorklist(params: LabWorklistQueryParams) {
  const qs = buildLabWorklistQuery(params);

  return useQuery<LabWorklistResponse>({
    queryKey: [
      "lab-worklists",
      params.facilityId ?? null,
      params.workspaceId ?? null,
      params.type ?? "LAB",
      params.status ?? null,
      params.priority ?? null,
      params.page ?? 0,
      params.size ?? 20,
    ],
    queryFn: () =>
      apiClient.get<LabWorklistResponse>(`/internal/v1/lab-worklists?${qs}`),
    enabled: !!params.facilityId,
    staleTime: 15_000,
  });
}

export function useAcceptLabWorklistItem() {
  const queryClient = useQueryClient();

  return useMutation<LabWorklistActionResponse, unknown, { orderId: string }>({
    mutationFn: ({ orderId }) =>
      apiClient.post<LabWorklistActionResponse>(
        `/internal/v1/lab-worklists/${encodeURIComponent(orderId)}/accept`,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["lab-worklists"] });
    },
  });
}

export function useRejectLabWorklistItem() {
  const queryClient = useQueryClient();

  return useMutation<LabWorklistActionResponse, unknown, { orderId: string; reason: string }>({
    mutationFn: ({ orderId, reason }) =>
      apiClient.post<LabWorklistActionResponse>(
        `/internal/v1/lab-worklists/${encodeURIComponent(orderId)}/reject`,
        { reason },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["lab-worklists"] });
    },
  });
}
