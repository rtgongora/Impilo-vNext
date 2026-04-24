/**
 * Experience UI — Provider / council regulation (Varapi via BFF).
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type RegistryEnvelope<T> = { data: T };

export function useProviderCouncilObligations(providerNumericId: string | undefined) {
  return useQuery({
    queryKey: ["provider-council", "obligations", providerNumericId],
    queryFn: async () => {
      const r = await apiClient.get<RegistryEnvelope<unknown[]>>(
        `/internal/v1/registry/provider-council/obligations?providerId=${encodeURIComponent(providerNumericId ?? "")}`
      );
      return r.data ?? [];
    },
    enabled: !!providerNumericId,
  });
}

export function useProviderCouncilQueue(councilId: string | undefined, workflowStates?: string) {
  const qs = new URLSearchParams();
  if (councilId) {
    qs.set("councilId", councilId);
  }
  if (workflowStates) {
    qs.set("workflowStates", workflowStates);
  }
  const suffix = qs.toString();
  return useQuery({
    queryKey: ["provider-council", "queue", councilId, workflowStates],
    queryFn: async () => {
      const r = await apiClient.get<RegistryEnvelope<unknown[]>>(
        `/internal/v1/registry/provider-council/applications/open${suffix ? `?${suffix}` : ""}`
      );
      return r.data ?? [];
    },
    enabled: !!councilId,
  });
}

export function useFundoCpdCandidates(providerNumericId: string | undefined) {
  return useQuery({
    queryKey: ["provider-council", "fundo-cpd", providerNumericId],
    queryFn: async () => {
      const r = await apiClient.get<RegistryEnvelope<unknown[]>>(
        `/internal/v1/registry/provider-council/fundo-cpd-candidates?providerId=${encodeURIComponent(providerNumericId ?? "")}`
      );
      return r.data ?? [];
    },
    enabled: !!providerNumericId,
  });
}
