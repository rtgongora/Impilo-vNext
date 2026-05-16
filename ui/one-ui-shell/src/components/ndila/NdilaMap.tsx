"use client";

/**
 * NdilaMap — Ndila's canonical map surface.
 *
 * Provider doctrine: the tile provider is decided server-side by
 * NdilaProviderPolicyService. We fetch the tile config (which embeds a
 * policy-driven URL template + attribution) before rendering anything. If the
 * backend returns a `mock://...` template (the safe default for dev/test/CI)
 * we render a deterministic placeholder grid rather than reaching out to a
 * public tile server. This guarantees we never silently hit
 * tile.openstreetmap.org in production.
 *
 * This component is intentionally dependency-free (no Leaflet/MapLibre/Mapbox
 * imports). Down-stream pages embed it as the standard surface; the real
 * GL/raster renderer can be swapped in behind this component without
 * changing call sites.
 */

import { useEffect, useState } from "react";
import { Loader2, MapPin, ShieldAlert } from "lucide-react";
import {
  ndilaTileConfig,
  type NdilaCoordinate,
  type NdilaTileConfig,
} from "@/lib/ndila/ndila-client";

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
  height?: number;
  layers?: string[];
  mode?: "OPERATIONS" | "CITIZEN" | "DASHBOARD" | "FIELD";
}

export function NdilaMap({
  center, zoom = 12, markers = [], height = 320, layers = [], mode = "OPERATIONS",
}: NdilaMapProps) {
  const [tileCfg, setTileCfg] = useState<NdilaTileConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    ndilaTileConfig()
      .then((cfg) => { if (!cancelled) setTileCfg(cfg); })
      .catch(() => { if (!cancelled) setError("Tile config unavailable"); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const isMock = !tileCfg || tileCfg.tileUrlTemplate?.startsWith("mock://");
  const isUnsafe = !!tileCfg && /tile\.openstreetmap\.org/.test(tileCfg.tileUrlTemplate);

  return (
    <div className="rounded-lg border border-gray-200 overflow-hidden bg-slate-50" data-testid="ndila-map">
      <div className="flex items-center justify-between px-3 py-2 border-b border-gray-100 bg-white">
        <div className="flex items-center gap-2 text-xs text-gray-700">
          <MapPin className="h-4 w-4 text-indigo-600" />
          <span className="font-medium">Ndila Map</span>
          <span className="text-[10px] text-gray-500">
            · mode {mode} · zoom {zoom} · provider {tileCfg?.provider ?? "—"}
            {tileCfg?.supportsOffline ? " · offline-capable" : ""}
          </span>
        </div>
        {isUnsafe && (
          <span className="text-[10px] text-red-700 flex items-center gap-1">
            <ShieldAlert className="h-3 w-3" /> blocked: public OSM tiles in prod
          </span>
        )}
      </div>

      <div
        className="relative w-full"
        style={{ height }}
        aria-label="Ndila spatial canvas"
      >
        {loading ? (
          <div className="absolute inset-0 flex items-center justify-center text-xs text-gray-500 gap-2">
            <Loader2 className="h-4 w-4 animate-spin" /> Resolving Ndila tile provider…
          </div>
        ) : error ? (
          <div className="absolute inset-0 flex items-center justify-center text-xs text-amber-700">
            {error} · showing placeholder
          </div>
        ) : null}

        {/* Deterministic grid surface — replaced by a real tile renderer
            (Leaflet/MapLibre) when a non-mock provider is configured. */}
        <div
          className="absolute inset-0 opacity-90"
          style={{
            backgroundImage:
              "repeating-linear-gradient(0deg, rgba(99,102,241,0.06) 0 1px, transparent 1px 32px), " +
              "repeating-linear-gradient(90deg, rgba(99,102,241,0.06) 0 1px, transparent 1px 32px)",
            backgroundColor: isMock ? "#f8fafc" : "#eef2ff",
          }}
        />

        {markers.length > 0 && (
          <div className="absolute inset-0 p-3 overflow-y-auto">
            <div className="bg-white/90 backdrop-blur-sm border border-gray-200 rounded p-2 text-[11px] text-gray-700 max-w-sm">
              <div className="font-medium text-gray-900 mb-1">Markers · {markers.length}</div>
              <ul className="space-y-1 max-h-44 overflow-y-auto">
                {markers.map((m) => (
                  <li key={m.id} className="flex justify-between gap-3">
                    <span className="truncate">{m.label ?? m.id}</span>
                    <span className="text-gray-500 tabular-nums">
                      {m.latitude.toFixed(4)}, {m.longitude.toFixed(4)}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        )}

        <div className="absolute bottom-1 right-2 text-[9px] text-gray-500">
          center {center.latitude.toFixed(3)},{center.longitude.toFixed(3)}
          {layers.length ? ` · layers: ${layers.join(", ")}` : ""}
          {tileCfg?.attribution ? ` · ${tileCfg.attribution}` : ""}
        </div>
      </div>
    </div>
  );
}
