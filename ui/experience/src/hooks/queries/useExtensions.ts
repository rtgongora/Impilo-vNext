/**
 * Extension Points Hooks — Health OS §11 (Extension Points)
 *
 * Queries forms-service and rules-service via the Experience BFF
 * ExtensionController for governed module extensibility.
 */

import { useQuery, useMutation } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface FormDefinition {
  id: string;
  name: string;
  version: string;
  status: "DRAFT" | "PUBLISHED" | "RETIRED";
  content: Record<string, unknown>;
}

export interface RuleDefinition {
  id: string;
  name: string;
  version: string;
  status: "ACTIVE" | "INACTIVE";
  expression: string;
}

export function useForms(page = 0, size = 20) {
  return useQuery({
    queryKey: ["extensions", "forms", page, size],
    queryFn: () => apiClient.get<ApiResponse<FormDefinition[]>>(`/internal/v1/extensions/forms?page=${page}&size=${size}`),
  });
}

export function useForm(formId: string | undefined) {
  return useQuery({
    queryKey: ["extensions", "form", formId],
    queryFn: () => apiClient.get<ApiResponse<FormDefinition>>(`/internal/v1/extensions/forms/${formId}`),
    enabled: !!formId,
  });
}

export function useRules(page = 0, size = 20) {
  return useQuery({
    queryKey: ["extensions", "rules", page, size],
    queryFn: () => apiClient.get<ApiResponse<RuleDefinition[]>>(`/internal/v1/extensions/rules?page=${page}&size=${size}`),
  });
}

export function useEvaluateRule() {
  return useMutation({
    mutationFn: (body: { ruleId: string; facts: Record<string, unknown> }) =>
      apiClient.post<ApiResponse<{ result: boolean; reason: string }>>(`/internal/v1/extensions/rules/${body.ruleId}/evaluate`, body.facts),
  });
}
