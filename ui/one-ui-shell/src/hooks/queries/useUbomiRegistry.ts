/**
 * UBOMI civil registry — BFF-ready hook with honest not-wired handling.
 * Sovereign ubomi-service exists; Experience BFF bridge is not yet canonical.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface UbomiRegistryStatus {
  available: boolean;
  message: string;
  module: string;
}

const UBOMI_BFF_PROBE = "/internal/v1/ubomi/status";

export function useUbomiRegistry() {
  return useQuery({
    queryKey: ["ubomi", "registry-status"],
    queryFn: async (): Promise<UbomiRegistryStatus> => {
      try {
        const res = await apiClient.get<ApiResponse<UbomiRegistryStatus>>(UBOMI_BFF_PROBE);
        if (res?.data?.available !== undefined) {
          return res.data;
        }
      } catch {
        /* fall through to not-wired */
      }
      return {
        available: false,
        message: "UBOMI service is not reachable through the Experience BFF.",
        module: "ubomi-service",
      };
    },
    staleTime: 60_000,
    retry: false,
  });
}
