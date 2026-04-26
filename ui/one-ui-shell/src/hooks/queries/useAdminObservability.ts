/**
 * Admin observability - Experience BFF liveness (`GET /health`).
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface BffHealthPayload {
  status: string;
  service: string;
}

export function useBffHealthQuery(options?: { refetchIntervalMs?: number }) {
  return useQuery({
    queryKey: ["observability", "bff-health"],
    queryFn: async () => {
      const startedAt =
        typeof performance !== "undefined" && performance.now ? performance.now() : Date.now();
      const body = await apiClient.get<BffHealthPayload>("/health");
      const finishedAt =
        typeof performance !== "undefined" && performance.now ? performance.now() : Date.now();

      return {
        ...body,
        latencyMs: Math.max(0, Math.round(finishedAt - startedAt)),
        checkedAt: new Date().toISOString(),
      };
    },
    refetchInterval: options?.refetchIntervalMs ?? 30_000,
  });
}
