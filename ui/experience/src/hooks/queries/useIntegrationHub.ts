/**
 * Integration Hub — Experience BFF proxies to integration-hub-service.
 * Used by admin integration surfaces (routes registry, dispatch health).
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

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
