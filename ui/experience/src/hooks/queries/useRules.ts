/**
 * Health Intelligence Plane — governed business rules (rules-service via BFF).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type RulesEnvelope = ApiResponse<unknown>;

const RULES_KEY = ["extensions", "rules"] as const;

export function useRulesList() {
  return useQuery({
    queryKey: RULES_KEY,
    queryFn: () => apiClient.get<RulesEnvelope>("/internal/v1/extensions/rules"),
  });
}

export function useCreateRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<RulesEnvelope>("/internal/v1/extensions/rules", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: RULES_KEY });
    },
  });
}

/** Registers a new immutable rule version (rules-service: POST /rules/{key}/versions). */
export function useCreateRuleVersion() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ key, body }: { key: string; body: Record<string, unknown> }) =>
      apiClient.post<RulesEnvelope>(`/internal/v1/extensions/rules/${encodeURIComponent(key)}/versions`, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: RULES_KEY });
    },
  });
}

export function useActivateRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ key, version }: { key: string; version: number }) =>
      apiClient.post<RulesEnvelope>(
        `/internal/v1/extensions/rules/${encodeURIComponent(key)}/activate?version=${version}`,
        {}
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: RULES_KEY });
    },
  });
}

export function useDeactivateRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (key: string) =>
      apiClient.post<RulesEnvelope>(`/internal/v1/extensions/rules/${encodeURIComponent(key)}/deactivate`, {}),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: RULES_KEY });
    },
  });
}

export function useEvaluateRule() {
  return useMutation({
    mutationFn: ({ key, facts }: { key: string; facts: Record<string, unknown> }) =>
      apiClient.post<RulesEnvelope>(`/internal/v1/extensions/rules/${encodeURIComponent(key)}/evaluate`, {
        facts,
      }),
  });
}
