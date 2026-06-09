"use client";

import { useMemo } from "react";
import { Loader2, MapPin } from "lucide-react";
import { NdilaMap } from "@/components/ndila/NdilaMap";
import { computeMapCenter, DEFAULT_MAP_CENTER } from "@/lib/ndila/geo-map-markers";
import type { PublicHealthMapMarker } from "@/hooks/queries/usePublicHealth";

const MARKER_TYPE_BY_KIND: Record<string, string> = {
  outbreak: "task",
  field_task: "task",
  site: "site",
  complaint: "facility",
};

interface NdilaPublicHealthRiskMapProps {
  markers: PublicHealthMapMarker[];
  loading?: boolean;
  height?: number;
}

export function NdilaPublicHealthRiskMap({
  markers,
  loading = false,
  height = 224,
}: NdilaPublicHealthRiskMapProps) {
  const ndilaMarkers = useMemo(
    () =>
      markers
        .filter((m) => Number.isFinite(m.latitude) && Number.isFinite(m.longitude))
        .map((m) => ({
          id: `${m.markerType}-${m.id}`,
          label: m.label,
          latitude: m.latitude,
          longitude: m.longitude,
          markerType: MARKER_TYPE_BY_KIND[m.markerType] ?? "task",
          status: m.status,
        })),
    [markers],
  );

  const center = computeMapCenter(
    ndilaMarkers.map((m) => ({ id: m.id, label: m.label ?? m.id, latitude: m.latitude, longitude: m.longitude })),
    DEFAULT_MAP_CENTER,
  );

  if (loading) {
    return (
      <div className="flex items-center gap-2 text-sm text-gray-500 py-6 justify-center">
        <Loader2 className="h-4 w-4 animate-spin" /> Loading map data…
      </div>
    );
  }

  if (ndilaMarkers.length === 0) {
    return (
      <p className="text-xs text-gray-500 py-4 text-center">
        No geo-tagged outbreaks or field tasks yet. Record coordinates when submitting outbreaks or field activities.
      </p>
    );
  }

  return (
    <>
      <NdilaMap
        center={center}
        markers={ndilaMarkers}
        height={height}
        fitToMarkers
        mode="FIELD"
        layers={["public-health-ops"]}
      />
      <div className="space-y-2 max-h-40 overflow-y-auto mt-3">
        {ndilaMarkers.map((m) => (
          <div key={m.id} className="flex justify-between text-xs border border-gray-100 rounded p-2">
            <span className="font-medium text-gray-900 flex items-center gap-1">
              <MapPin className="h-3 w-3 text-indigo-600" />
              {m.label}
            </span>
            <span className="text-gray-500">
              {m.markerType} · {m.status} · {m.latitude.toFixed(4)}, {m.longitude.toFixed(4)}
            </span>
          </div>
        ))}
      </div>
    </>
  );
}
