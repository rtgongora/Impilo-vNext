/**
 * Integration Hub — Experience BFF proxies to integration-hub-service.
 * Used by admin integration surfaces (routes registry, dispatch health).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface IntegrationMappingTemplateRow {
  id?: string;
  name?: string;
  sourceSystem?: string;
  targetSystem?: string;
  [key: string]: unknown;
}

function asTemplateRows(raw: unknown): IntegrationMappingTemplateRow[] {
  if (Array.isArray(raw)) return raw as IntegrationMappingTemplateRow[];
  if (raw && typeof raw === "object") {
    const r = raw as Record<string, unknown>;
    if (Array.isArray(r.content)) return r.content as IntegrationMappingTemplateRow[];
    if (Array.isArray(r.items)) return r.items as IntegrationMappingTemplateRow[];
  }
  return [];
}

export function useIntegrationHubRoutes() {
  return useQuery({
    queryKey: ["integration-hub", "routes"],
    queryFn: () => apiClient.get<{ data: unknown }>("/internal/v1/integration-hub/routes"),
    staleTime: 30_000,
  });
}

export function useIntegrationHubDeadLetters(page = 0, size = 20) {
  return useQuery({
    queryKey: ["integration-hub", "deadletters", page, size],
    queryFn: () =>
      apiClient.get<{ data: unknown }>(
        `/internal/v1/integration-hub/deadletters?page=${page}&size=${size}`,
      ),
    staleTime: 15_000,
  });
}

export function useIntegrationMappingTemplates() {
  return useQuery({
    queryKey: ["integration-hub", "mapping-templates"],
    queryFn: async () => {
      const res = await apiClient.get<{ data: unknown }>("/internal/v1/integration-hub/mapping-templates");
      return asTemplateRows(res.data);
    },
    staleTime: 30_000,
  });
}

export function useCreateIntegrationMappingTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<{ data: unknown }>("/internal/v1/integration-hub/mapping-templates", body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["integration-hub", "mapping-templates"] }),
  });
}

export function useReplayIntegrationDeadLetter() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (deadLetterId: string) =>
      apiClient.post<{ data: unknown }>(
        `/internal/v1/integration-hub/deadletters/${encodeURIComponent(deadLetterId)}/replay`,
        {},
      ),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["integration-hub", "deadletters"] });
      void qc.invalidateQueries({ queryKey: ["integration-hub", "routes"] });
    },
  });
}
