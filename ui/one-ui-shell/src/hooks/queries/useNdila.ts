"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type NdilaTileConfig = {
  provider?: string;
  tileUrlTemplate?: string;
  /** Self-hosted MVT street lane (public gateway passthrough) — the browser-reachable basemap. */
  vectorTileUrlTemplate?: string;
  attribution?: string;
  maxZoom?: number;
  supportsOffline?: boolean;
};

type AnyRecord = Record<string, unknown>;

export function useNdilaTileConfig() {
  return useQuery({
    queryKey: ["ndila", "tiles", "config"],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<NdilaTileConfig & { providerName?: string }>>(
        "/internal/v1/ndila/tiles/config",
      );
      const raw = res.data ?? {};
      return {
        ...raw,
        provider: raw.provider ?? raw.providerName,
      };
    },
    staleTime: 60_000,
  });
}

export function useNdilaNearbyAssets() {
  return useMutation({
    mutationFn: (body: AnyRecord) =>
      apiClient.post<ApiResponse<unknown>>("/internal/v1/ndila/tracking/nearby", body),
  });
}
