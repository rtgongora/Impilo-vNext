/**
 * Teleconsult routing directory hooks — the REAL, BFF-validated routing
 * search paths used when composing a telemedicine request:
 *
 *   GET /internal/v1/teleconsult/routing/providers?q=   (Varapi search)
 *   GET /internal/v1/teleconsult/routing/facilities?q=  (Tuso search)
 *   GET /internal/v1/teleconsult/routing/workspaces?facility_id= (Tuso)
 *
 * ON_CALL / POOL / NATIONAL_POOL / UNIT routing intentionally has no hook:
 * the BFF returns 501 ROUTING_TYPE_UNAVAILABLE for those kinds (fail-closed
 * until the pool/on-call directory backend exists — capability map HO-1).
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface RoutingProviderHit {
  providerPublicId?: string;
  givenName?: string;
  familyName?: string;
  title?: string;
  profession?: string;
  cadre?: string;
  status?: string;
  practiceNumber?: string;
  [key: string]: unknown;
}

export interface RoutingFacilityHit {
  id?: number | string;
  facilityId?: number | string;
  name?: string;
  facilityType?: string;
  province?: string;
  district?: string;
  status?: string;
  [key: string]: unknown;
}

export interface RoutingWorkspaceHit {
  id?: string;
  workspaceId?: string;
  name?: string;
  workspaceType?: string;
  status?: string;
  [key: string]: unknown;
}

function unwrapList<T>(payload: unknown): T[] {
  if (Array.isArray(payload)) return payload as T[];
  if (payload && typeof payload === "object") {
    const obj = payload as Record<string, unknown>;
    for (const key of ["data", "content", "items", "results"]) {
      const inner = obj[key];
      if (Array.isArray(inner)) return inner as T[];
      if (inner && typeof inner === "object") {
        const nested = (inner as Record<string, unknown>).content;
        if (Array.isArray(nested)) return nested as T[];
      }
    }
  }
  return [];
}

export function useRoutingProviderSearch(query: string) {
  const q = query.trim();
  return useQuery({
    queryKey: ["teleconsult-routing-providers", q],
    enabled: q.length >= 2,
    staleTime: 30_000,
    queryFn: async (): Promise<RoutingProviderHit[]> => {
      const res = await apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/teleconsult/routing/providers?q=${encodeURIComponent(q)}`,
      );
      return unwrapList<RoutingProviderHit>(res?.data ?? res);
    },
  });
}

export function useRoutingFacilitySearch(query: string) {
  const q = query.trim();
  return useQuery({
    queryKey: ["teleconsult-routing-facilities", q],
    enabled: q.length >= 2,
    staleTime: 30_000,
    queryFn: async (): Promise<RoutingFacilityHit[]> => {
      const res = await apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/teleconsult/routing/facilities?q=${encodeURIComponent(q)}`,
      );
      return unwrapList<RoutingFacilityHit>(res?.data ?? res);
    },
  });
}

export function useRoutingWorkspaces(facilityId: string | null) {
  return useQuery({
    queryKey: ["teleconsult-routing-workspaces", facilityId],
    enabled: !!facilityId,
    staleTime: 30_000,
    queryFn: async (): Promise<RoutingWorkspaceHit[]> => {
      if (!facilityId) return [];
      const res = await apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/teleconsult/routing/workspaces?facility_id=${encodeURIComponent(facilityId)}`,
      );
      return unwrapList<RoutingWorkspaceHit>(res?.data ?? res);
    },
  });
}
