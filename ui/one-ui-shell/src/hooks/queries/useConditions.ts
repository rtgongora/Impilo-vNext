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
  /**
   * Set only after the server has reported a duplicate and the clinician has answered. Sending it
   * unprompted would skip the check the answer exists to resolve.
   */
  duplicateResolution?: DuplicateResolution;
  [key: string]: unknown;
}

export type DuplicateResolution = "SAME_PROBLEM_RETURNING" | "DISTINCT_PROBLEM";

/**
 * The 409 the BFF returns when this condition is already on the patient's list.
 *
 * <p>It is <strong>data, not a failure</strong>. The server is not reporting that the write broke —
 * it is asking a question only the clinician can answer, and it carries the existing condition
 * because the question is unanswerable without seeing it. Rendering this as "something went wrong,
 * please try again" produces a dead end: the retry returns the same 409 forever.</p>
 */
export interface DuplicateConditionReport {
  status: 409;
  error: "condition_already_on_list";
  message: string;
  existing?: ConditionResource;
  resolutions?: Array<{ duplicate_resolution: DuplicateResolution; effect: string }>;
}

/** Narrows a thrown api-client error to the duplicate question. */
export function asDuplicateReport(error: unknown): DuplicateConditionReport | null {
  if (!error || typeof error !== "object") return null;
  const candidate = error as Partial<DuplicateConditionReport>;
  return candidate.status === 409 && candidate.error === "condition_already_on_list"
    ? (candidate as DuplicateConditionReport)
    : null;
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
