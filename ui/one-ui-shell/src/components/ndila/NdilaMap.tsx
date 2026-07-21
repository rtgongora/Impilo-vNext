"use client";

import dynamic from "next/dynamic";
import { Loader2, MapPin, ShieldAlert } from "lucide-react";
import { useNdilaTileConfig } from "@/hooks/queries/useNdila";
import type { NdilaCoordinate } from "@/lib/ndila/ndila-client";
import {
  isMockTileTemplate,
  isUnsafePublicOsmTemplate,
  isVectorTileTemplate,
} from "@/lib/ndila/build-ndila-map-style";
import type { NdilaGeoMarker } from "./NdilaMapLibre";

const NdilaMapLibre = dynamic(() => import("./NdilaMapLibre").then((mod) => mod.NdilaMapLibre), {
  ssr: false,
  loading: () => (
    <div className="flex h-full min-h-[12rem] items-center justify-center text-xs text-muted-foreground">
      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
      Loading interactive map…
    </div>
  ),
});

export interface NdilaMapMarker {
  id: string;
  label?: string;
  latitude: number;
  longitude: number;
  markerType?: string;
  status?: string;
}

interface NdilaMapProps {
  center: NdilaCoordinate;
  zoom?: number;
  markers?: NdilaMapMarker[];
  routeCoordinates?: NdilaCoordinate[];
  height?: number;
  layers?: string[];
  mode?: "OPERATIONS" | "CITIZEN" | "DASHBOARD" | "FIELD";
  fitToMarkers?: boolean;
  showZimbabweAdmin?: boolean;
  clusterMarkers?: boolean;
  showMapChrome?: boolean;
  flyToCenter?: boolean;
}

export function NdilaMap({
  center,
  zoom = 12,
  markers = [],
  routeCoordinates = [],
  height = 320,
  layers = [],
  mode = "OPERATIONS",
  fitToMarkers = false,
  showZimbabweAdmin = true,
  clusterMarkers = false,
  showMapChrome = true,
  flyToCenter = true,
}: NdilaMapProps) {
  const tileQ = useNdilaTileConfig();
  const tileCfg = tileQ.data;
  // A live vector (MVT) basemap counts as live tiles even though there is no raster template.
  const isMock =
    isMockTileTemplate(tileCfg?.tileUrlTemplate) && !isVectorTileTemplate(tileCfg?.vectorTileUrlTemplate);
  const isUnsafe = isUnsafePublicOsmTemplate(tileCfg?.tileUrlTemplate);

  const geoMarkers: NdilaGeoMarker[] = markers.map((marker) => ({
    id: marker.id,
    label: marker.label,
    latitude: marker.latitude,
    longitude: marker.longitude,
    markerType: marker.markerType,
    status: marker.status,
  }));

  return (
    <div className="overflow-hidden rounded-lg border border-border bg-background" data-testid="ndila-map">
      <div className="flex items-center justify-between border-b border-border bg-card px-3 py-2">
        <div className="flex items-center gap-2 text-xs text-foreground">
          <MapPin className="h-4 w-4 text-indigo-600" />
          <span className="font-medium">Ndila Map</span>
          <span className="text-[10px] text-muted-foreground">
            · mode {mode} · zoom {zoom} · provider {tileCfg?.provider ?? "—"}
            {isMock ? " · mock tiles (interactive canvas)" : " · live tiles"}
            {tileCfg?.supportsOffline ? " · offline-capable" : ""}
          </span>
        </div>
        {isUnsafe ? (
          <span className="flex items-center gap-1 text-[10px] text-danger">
            <ShieldAlert className="h-3 w-3" /> blocked: public OSM tiles in prod
          </span>
        ) : null}
      </div>

      <div className="relative w-full" style={{ height }} aria-label="Ndila spatial canvas">
        {tileQ.isLoading ? (
          <div className="absolute inset-0 z-10 flex items-center justify-center gap-2 bg-card/70 text-xs text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" />
            Resolving Ndila tile provider…
          </div>
        ) : null}

        <NdilaMapLibre
          center={center}
          zoom={zoom}
          markers={geoMarkers}
          routeCoordinates={routeCoordinates}
          height={height}
          tileConfig={tileCfg}
          fitToMarkers={fitToMarkers}
          showZimbabweAdmin={showZimbabweAdmin}
          clusterMarkers={clusterMarkers}
          showMapChrome={showMapChrome}
          flyToCenter={flyToCenter}
        />

        <div className="pointer-events-none absolute bottom-1 right-2 text-[9px] text-muted-foreground">
          center {center.latitude.toFixed(3)},{center.longitude.toFixed(3)}
          {layers.length ? ` · layers: ${layers.join(", ")}` : ""}
          {tileCfg?.attribution ? ` · ${tileCfg.attribution}` : ""}
        </div>
      </div>
    </div>
  );
}
