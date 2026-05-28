"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type NdilaTileConfig = {
  provider?: string;
  tileUrlTemplate?: string;
  attribution?: string;
  maxZoom?: number;
};

type AnyRecord = Record<string, unknown>;

export function useNdilaTileConfig() {
  return useQuery({
    queryKey: ["ndila", "tiles", "config"],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<NdilaTileConfig>>("/internal/v1/ndila/tiles/config");
      return res.data ?? {};
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
