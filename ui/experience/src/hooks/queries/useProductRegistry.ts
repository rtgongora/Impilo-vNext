/**
 * Experience UI — Product registry (MSIKA) query hooks.
 *
 * Backed by Experience BFF:
 * - GET /internal/v1/product-registry/search
 * - GET /internal/v1/product-registry/items/{itemId}
 * - GET /internal/v1/product-registry/catalogs/{catalogId}/items
 *
 * The controller forwards upstream JSON as-is; responses here are treated as unknown JSON.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type ProductRegistryJson = unknown;

export function useProductRegistrySearch(params: { q?: string; size?: number; page?: number; kind?: string }) {
  return useQuery<ProductRegistryJson>({
    queryKey: ["product-registry", "search", params],
    queryFn: () => {
      const qs = new URLSearchParams();
      if (params.q) qs.set("q", params.q);
      if (params.kind) qs.set("kind", params.kind);
      qs.set("page", String(params.page ?? 0));
      qs.set("size", String(params.size ?? 20));
      const query = qs.toString();
      return apiClient.get<ProductRegistryJson>(`/internal/v1/product-registry/search${query ? `?${query}` : ""}`);
    },
  });
}

export function useProductRegistryItem(itemId: string | undefined) {
  return useQuery<ProductRegistryJson>({
    queryKey: ["product-registry", "items", itemId],
    queryFn: () => apiClient.get<ProductRegistryJson>(`/internal/v1/product-registry/items/${encodeURIComponent(String(itemId))}`),
    enabled: Boolean(itemId),
  });
}

