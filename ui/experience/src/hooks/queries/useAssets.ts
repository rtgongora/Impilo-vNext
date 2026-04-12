/**
 * Experience UI — Asset registry (asset-registry-service via BFF).
 *
 * Proxies to `/internal/v1/asset-registry/**` (BFF `AssetRegistrySupplyBffController`).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

function q(params: Record<string, string | number | undefined | null>): string {
  const usp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === "") continue;
    usp.set(k, String(v));
  }
  const s = usp.toString();
  return s ? `?${s}` : "";
}

export function useAssets(
  opts?: { facilityId?: string; status?: string; cursor?: number; limit?: number },
) {
  return useQuery({
    queryKey: ["asset-registry", "assets", opts],
    queryFn: () =>
      apiClient.get<unknown>(
        `/internal/v1/asset-registry/assets${q({
          facility_id: opts?.facilityId,
          status: opts?.status,
          cursor: opts?.cursor ?? 0,
          limit: opts?.limit ?? 20,
        })}`,
      ),
  });
}

export function useAsset(assetId?: string | null) {
  return useQuery({
    queryKey: ["asset-registry", "asset", assetId],
    queryFn: () =>
      apiClient.get<unknown>(`/internal/v1/asset-registry/assets/${encodeURIComponent(assetId!)}`),
    enabled: !!assetId,
  });
}

export function useAssetSnapshots(opts?: { cursor?: number; limit?: number; asOf?: string }) {
  return useQuery({
    queryKey: ["asset-registry", "snapshots", opts],
    queryFn: () =>
      apiClient.get<unknown>(
        `/internal/v1/asset-registry/snapshots/assets${q({
          cursor: opts?.cursor ?? 0,
          limit: opts?.limit ?? 100,
          as_of: opts?.asOf,
        })}`,
      ),
  });
}

export function useUpsertAsset() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { assetId: string; body: Record<string, unknown> }) =>
      apiClient.put<unknown>(
        `/internal/v1/asset-registry/assets/${encodeURIComponent(args.assetId)}`,
        args.body,
      ),
    onSuccess: (_d, { assetId }) => {
      qc.invalidateQueries({ queryKey: ["asset-registry", "asset", assetId] });
      qc.invalidateQueries({ queryKey: ["asset-registry", "assets"] });
      qc.invalidateQueries({ queryKey: ["asset-registry", "snapshots"] });
    },
  });
}

export function useUpdateAssetStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { assetId: string; body: Record<string, unknown> }) =>
      apiClient.put<unknown>(
        `/internal/v1/asset-registry/assets/${encodeURIComponent(args.assetId)}/status`,
        args.body,
      ),
    onSuccess: (_d, { assetId }) => {
      qc.invalidateQueries({ queryKey: ["asset-registry", "asset", assetId] });
      qc.invalidateQueries({ queryKey: ["asset-registry", "assets"] });
    },
  });
}

export function useRetireAsset() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (assetId: string) =>
      apiClient.delete<unknown>(`/internal/v1/asset-registry/assets/${encodeURIComponent(assetId)}`),
    onSuccess: (_d, assetId) => {
      qc.invalidateQueries({ queryKey: ["asset-registry", "asset", assetId] });
      qc.invalidateQueries({ queryKey: ["asset-registry", "assets"] });
      qc.invalidateQueries({ queryKey: ["asset-registry", "snapshots"] });
    },
  });
}
