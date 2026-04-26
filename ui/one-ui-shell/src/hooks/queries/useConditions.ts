/**
 * Experience UI — Conditions Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface ConditionResource {
  id: string;
  type: "condition";
  attributes: {
    patientId: string;
    encounterId: string | null;
    conditionName: string;
    icdCode: string | null;
    category: string;
    clinicalStatus: string;
    severity: string;
    onsetDate: string | null;
    recordedBy: string;
    notes: string | null;
    createdAt: string;
  };
}

interface CreateConditionPayload {
  patientId: string;
  encounterId?: string | null;
  conditionName: string;
  icdCode?: string | null;
  category: string;
  severity: string;
  onsetDate?: string | null;
  notes?: string | null;
  [key: string]: unknown;
}

type ConditionsResponse = ApiResponse<ConditionResource[]>;
type ConditionResponse = ApiResponse<ConditionResource>;

export function useConditions(patientId: string) {
  return useQuery<ConditionsResponse>({
    queryKey: ["conditions", { patientId }],
    queryFn: () =>
      apiClient.get<ConditionsResponse>(
        `/internal/v1/conditions?patient_id=${encodeURIComponent(patientId)}`
      ),
    enabled: !!patientId,
  });
}

export function useCreateCondition() {
  const queryClient = useQueryClient();

  return useMutation<ConditionResponse, unknown, CreateConditionPayload>({
    mutationFn: (payload: CreateConditionPayload) =>
      apiClient.post<ConditionResponse>("/internal/v1/conditions", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["conditions"] });
    },
  });
}

export function useResolveCondition() {
  const queryClient = useQueryClient();

  return useMutation<ConditionResponse, unknown, { id: string }>({
    mutationFn: ({ id }: { id: string }) =>
      apiClient.post<ConditionResponse>(`/internal/v1/conditions/${id}/resolve`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["conditions"] });
    },
  });
}
