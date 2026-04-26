/**
 * Federated search index (Health OS §12) — BFF → search-service via {@code GET /internal/v1/search}.
 * Distinct from `useSearch` in `useGuidance.ts`, which calls guidance-service.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface KnowledgeSearchHit {
  id: string;
  entityType: string;
  entityId: string;
  contentJson?: Record<string, unknown>;
  tags?: string[];
  searchableText?: string;
  indexedAt?: string;
}

export interface KnowledgeSearchPayload {
  results: KnowledgeSearchHit[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface KnowledgeSearchParams {
  /** Submitted query (trimmed, non-empty enables the query). */
  q: string;
  /** Optional search-service entity type filter (omit for all types). */
  entityType?: string;
  page?: number;
  size?: number;
}

function buildSearchPath(params: KnowledgeSearchParams): string {
  const page = params.page ?? 0;
  const size = params.size ?? 20;
  const sp = new URLSearchParams({
    q: params.q.trim(),
    page: String(page),
    size: String(size),
  });
  if (params.entityType && params.entityType.trim().length > 0) {
    sp.set("entityType", params.entityType.trim());
  }
  return `/internal/v1/search?${sp.toString()}`;
}

export function useKnowledgeSearch(params: KnowledgeSearchParams) {
  const q = params.q.trim();
  return useQuery({
    queryKey: ["search", "knowledge", q, params.entityType ?? "", params.page ?? 0, params.size ?? 20],
    queryFn: () => apiClient.get<ApiResponse<KnowledgeSearchPayload>>(buildSearchPath(params)),
    enabled: q.length >= 1,
  });
}
