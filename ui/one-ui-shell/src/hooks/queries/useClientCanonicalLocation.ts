import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

/**
 * G4 — the canonical ndila location materialized from a client's delegated address.
 *
 * VITO delegates client addresses to ndila (the geography SoR) via an async best-effort seam; ndila
 * geocodes + normalizes them into an owner-keyed `ndila_locations` row. This hook reads that row
 * through the existing BFF proxy:
 *   GET /internal/v1/ndila/locations/by-owner/vito/{healthId}
 * An owner has at most one location, so we return the first (or null when not yet materialized).
 */
export interface CanonicalLocation {
  id: string;
  name?: string;
  locationType?: string;
  latitude?: number;
  longitude?: number;
  district?: string;
  province?: string;
  country?: string;
  verificationStatus?: string;
  sensitivityLevel?: string;
  active?: boolean;
}

function rec(v: unknown): Record<string, unknown> | undefined {
  return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : undefined;
}

/** Recursively unwrap nested `data` envelopes (api-client wrapper → BFF `{data}` → array). */
function unwrapList(raw: unknown): unknown[] {
  if (Array.isArray(raw)) return raw;
  const r = rec(raw);
  if (!r) return [];
  if (Array.isArray(r.items)) return r.items;
  if (Array.isArray(r.content)) return r.content;
  if (r.data != null) return unwrapList(r.data);
  return [];
}

function num(v: unknown): number | undefined {
  if (typeof v === "number" && Number.isFinite(v)) return v;
  if (typeof v === "string" && v.trim()) {
    const n = Number(v);
    if (Number.isFinite(n)) return n;
  }
  return undefined;
}

function str(v: unknown): string | undefined {
  return typeof v === "string" && v.length > 0 ? v : undefined;
}

function adapt(raw: unknown): CanonicalLocation | null {
  const node = rec(unwrapList(raw)[0]);
  if (!node) return null;
  const id = str(node.id);
  if (!id) return null;
  return {
    id,
    name: str(node.name),
    locationType: str(node.locationType),
    latitude: num(node.latitude),
    longitude: num(node.longitude),
    district: str(node.district),
    province: str(node.province),
    country: str(node.country),
    verificationStatus: str(node.verificationStatus),
    sensitivityLevel: str(node.sensitivityLevel),
    active: node.active === true,
  };
}

export function useClientCanonicalLocation(healthId: string | null | undefined) {
  return useQuery({
    queryKey: ["client-canonical-location", healthId],
    enabled: Boolean(healthId),
    queryFn: async (): Promise<CanonicalLocation | null> => {
      const res = await apiClient.get<unknown>(
        `/internal/v1/ndila/locations/by-owner/vito/${healthId}`,
      );
      return adapt(res);
    },
  });
}
