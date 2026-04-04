/**
 * Experience UI — Lab Orders Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface LabOrderResource {
  id: string;
  type: "lab_order";
  attributes: {
    patientId: string;
    encounterId: string;
    orderNumber: string;
    testName: string;
    testCode: string;
    category: string;
    priority: string;
    status: string;
    clinicalNotes: string | null;
    orderedBy: string;
    orderedByName: string;
    facilityId: string;
    collectedAt: string | null;
    resultedAt: string | null;
    createdAt: string;
  };
}

interface CreateLabOrderPayload {
  patientId: string;
  encounterId: string;
  testName: string;
  testCode: string;
  category: string;
  priority: string;
  clinicalNotes?: string | null;
  facilityId: string;
  [key: string]: unknown;
}

type LabOrdersResponse = ApiResponse<LabOrderResource[]>;
type LabOrderResponse = ApiResponse<LabOrderResource>;

export function useLabOrders(patientId: string) {
  return useQuery<LabOrdersResponse>({
    queryKey: ["lab-orders", { patientId }],
    queryFn: () =>
      apiClient.get<LabOrdersResponse>(
        `/internal/v1/lab-orders?patient_id=${encodeURIComponent(patientId)}`
      ),
    enabled: !!patientId,
  });
}

export function useLabOrder(id: string) {
  return useQuery<LabOrderResponse>({
    queryKey: ["lab-orders", id],
    queryFn: () =>
      apiClient.get<LabOrderResponse>(`/internal/v1/lab-orders/${id}`),
    enabled: !!id,
  });
}

export function useCreateLabOrder() {
  const queryClient = useQueryClient();

  return useMutation<LabOrderResponse, unknown, CreateLabOrderPayload>({
    mutationFn: (payload: CreateLabOrderPayload) =>
      apiClient.post<LabOrderResponse>("/internal/v1/lab-orders", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["lab-orders"] });
    },
  });
}

export function useCollectLabOrder() {
  const queryClient = useQueryClient();

  return useMutation<LabOrderResponse, unknown, { id: string }>({
    mutationFn: ({ id }: { id: string }) =>
      apiClient.post<LabOrderResponse>(`/internal/v1/lab-orders/${id}/collect`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["lab-orders"] });
    },
  });
}

export function useCancelLabOrder() {
  const queryClient = useQueryClient();

  return useMutation<LabOrderResponse, unknown, { id: string; reason?: string }>({
    mutationFn: ({ id, reason }: { id: string; reason?: string }) =>
      apiClient.post<LabOrderResponse>(`/internal/v1/lab-orders/${id}/cancel`, reason ? { reason } : undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["lab-orders"] });
    },
  });
}

export function useResultLabOrder() {
  const queryClient = useQueryClient();

  return useMutation<LabOrderResponse, unknown, { id: string }>({
    mutationFn: ({ id }: { id: string }) =>
      apiClient.post<LabOrderResponse>(`/internal/v1/lab-orders/${id}/result`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["lab-orders"] });
    },
  });
}
