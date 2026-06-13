"use client";

import dynamic from "next/dynamic";
import { Loader2, MapPin } from "lucide-react";
import { useNdilaTileConfig } from "@/hooks/queries/useNdila";
import {
  computeMapCenter,
  DEFAULT_MAP_CENTER,
  opsMarkersToGeoMarkers,
  type OpsMapMarker,
} from "@/lib/ndila/geo-map-markers";

export type { OpsMapMarker };

const NdilaMapLibre = dynamic(() => import("../ndila/NdilaMapLibre").then((mod) => mod.NdilaMapLibre), {
  ssr: false,
  loading: () => (
    <div className="flex h-44 items-center justify-center text-xs text-muted-foreground">
      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
      Loading map…
    </div>
  ),
});

interface OpsMapPanelProps {
  title: string;
  subtitle?: string;
  markers: OpsMapMarker[];
  routeCoordinates?: Array<{ latitude: number; longitude: number }>;
  emptyHint?: string;
  height?: number;
  fitToMarkers?: boolean;
}

export function OpsMapPanel({
  title,
  subtitle,
  markers,
  routeCoordinates = [],
  emptyHint,
  height = 280,
  fitToMarkers = true,
}: OpsMapPanelProps) {
  const tilesQ = useNdilaTileConfig();
  const geoMarkers = opsMarkersToGeoMarkers(markers);
  const center = computeMapCenter(geoMarkers, DEFAULT_MAP_CENTER);
  const geoOnly = markers.filter((marker) => marker.latitude != null && marker.longitude != null);

  return (
    <section className="rounded-xl border border-border bg-card p-4">
      <div className="mb-3 flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <MapPin className="h-4 w-4 text-rose-600" />
          <div>
            <h3 className="text-sm font-semibold text-foreground">{title}</h3>
            {subtitle ? <p className="text-xs text-muted-foreground">{subtitle}</p> : null}
          </div>
        </div>
        <span className="text-[10px] text-muted-foreground">
          {geoOnly.length}/{markers.length} geo-tagged
        </span>
      </div>

      <NdilaMapLibre
        center={center}
        zoom={geoMarkers.length > 1 ? 10 : 12}
        markers={geoMarkers}
        routeCoordinates={routeCoordinates}
        height={height}
        tileConfig={tilesQ.data}
        fitToMarkers={fitToMarkers && geoMarkers.length > 0}
      />

      {tilesQ.isError ? (
        <p className="mt-2 text-xs text-warning-foreground">
          Ndila tiles unavailable — interactive canvas with geo markers only until geospatial service recovers.
        </p>
      ) : null}

      {markers.length === 0 ? (
        <p className="mt-2 text-xs text-muted-foreground">{emptyHint ?? "No geo-tagged assets for this view yet."}</p>
      ) : (
        <ul className="mt-3 max-h-48 space-y-2 overflow-y-auto text-xs">
          {markers.map((marker) => (
            <li key={marker.id} className="flex justify-between gap-2 rounded-lg bg-background px-2 py-1.5">
              <span className="font-medium text-foreground">{marker.label}</span>
              <span className="text-muted-foreground">
                {marker.latitude != null && marker.longitude != null
                  ? `${marker.latitude.toFixed(3)}, ${marker.longitude.toFixed(3)}`
                  : (marker.status ?? "no coordinates")}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
