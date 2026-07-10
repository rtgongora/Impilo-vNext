"use client";

/**
 * Human-friendly nearby facilities: name, kind, distance and a search hand-off,
 * powered by the governed Ndila spatial index (all 1,800+ registry sites).
 */

import { useQuery } from "@tanstack/react-query";
import { MapPin } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import type { NdilaCoordinate } from "@/lib/ndila/ndila-client";

interface NearbyMatch {
  id: string;
  name: string;
  locationType?: string;
  distanceMeters: number;
  latitude?: number;
  longitude?: number;
}

function useNearbyFacilities(origin: NdilaCoordinate, radiusMeters: number) {
  return useQuery({
    queryKey: ["ndila", "nearby-facilities", origin.latitude, origin.longitude, radiusMeters],
    queryFn: async () => {
      const res = await apiClient.post<{ data?: { matches?: NearbyMatch[] } }>(
        "/internal/v1/ndila/spatial/nearby",
        {
          origin: { latitude: origin.latitude, longitude: origin.longitude },
          radiusMeters,
          entityTypes: ["FACILITY", "CLINIC", "HOSPITAL", "PHARMACY"],
          limit: 12,
        },
      );
      return res.data?.matches ?? [];
    },
    staleTime: 30_000,
  });
}

function km(meters: number) {
  return meters >= 1000 ? `${(meters / 1000).toFixed(1)} km` : `${Math.round(meters)} m`;
}

export function NearbyFacilitiesList({
  origin,
  radiusMeters = 25_000,
  onPickFacility,
}: {
  origin: NdilaCoordinate;
  radiusMeters?: number;
  onPickFacility?: (name: string) => void;
}) {
  const { data: matches = [], isLoading, isError } = useNearbyFacilities(origin, radiusMeters);

  if (isLoading) return <p className="text-sm text-muted-foreground">Finding facilities near you…</p>;
  if (isError) {
    return (
      <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-warning-foreground">
        The location service is unreachable right now — the registry list on the right still works.
      </p>
    );
  }
  if (matches.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No registered facilities within {km(radiusMeters)} of this point.
      </p>
    );
  }

  return (
    <ul className="divide-y divide-slate-100 rounded-xl border border-border bg-card" data-testid="nearby-facilities-list">
      {matches.map((m) => (
        <li key={m.id} className="flex items-center justify-between gap-3 px-3 py-2.5">
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-foreground">{m.name}</p>
            <p className="text-xs text-muted-foreground">
              {(m.locationType ?? "facility").replace(/_/g, " ").toLowerCase()} · {km(m.distanceMeters)} away
            </p>
          </div>
          <button
            type="button"
            onClick={() => onPickFacility?.(m.name)}
            data-testid={`nearby-details-${m.id}`}
            className="inline-flex shrink-0 items-center gap-1 rounded-lg border border-border px-2 py-1 text-xs font-medium text-foreground hover:bg-background"
          >
            <MapPin className="h-3 w-3" />
            Details
          </button>
        </li>
      ))}
    </ul>
  );
}
