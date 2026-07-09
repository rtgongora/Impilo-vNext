"use client";

/**
 * NdilaRoutePreview — quick route + ETA preview between two coordinates.
 *
 * Used by Nhume/Dispatch ("show route to delivery"), Emergency Ops ("nearest
 * responder ETA"), Telehealth field outreach planning, and Citizen
 * "directions to nearest clinic". The provider is policy-driven; the
 * component never assumes OSRM/Mapbox/Google — Ndila decides.
 */

import { useEffect, useState } from "react";
import { Loader2, Navigation } from "lucide-react";
import { ndilaRoute, type NdilaCoordinate, type NdilaRouteResponse, type NdilaTravelMode } from "@/lib/ndila/ndila-client";
import { routePolylineToCoordinates } from "@/lib/ndila/ndila-route-geometry";

interface Props {
  origin: NdilaCoordinate;
  destination: NdilaCoordinate;
  mode?: NdilaTravelMode;
  optimizeFor?: "FASTEST" | "SHORTEST" | "SAFEST";
  showDistance?: boolean;
  showEta?: boolean;
  onRouteCoordinates?: (coordinates: NdilaCoordinate[]) => void;
}

function formatDuration(seconds?: number): string {
  if (!seconds || seconds <= 0) return "—";
  const h = Math.floor(seconds / 3600);
  const m = Math.round((seconds % 3600) / 60);
  return h > 0 ? `${h}h ${m}m` : `${m}m`;
}

function formatDistance(meters?: number): string {
  if (!meters || meters <= 0) return "—";
  if (meters >= 1000) return `${(meters / 1000).toFixed(1)} km`;
  return `${Math.round(meters)} m`;
}

export function NdilaRoutePreview({
  origin, destination, mode = "MOTORBIKE", optimizeFor = "FASTEST",
  showDistance = true, showEta = true, onRouteCoordinates,
}: Props) {
  const [route, setRoute] = useState<NdilaRouteResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true); setError(null);
    ndilaRoute({ origin, destination, mode, optimizeFor, includeGeometry: true })
      .then((r) => {
        if (cancelled) return;
        if (!r.success) setError(r.denialReason || "Routing denied");
        setRoute(r);
        const coords = routePolylineToCoordinates(r.polyline, { origin, destination });
        onRouteCoordinates?.(coords);
      })
      .catch(() => { if (!cancelled) setError("Ndila routing unreachable"); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [origin.latitude, origin.longitude, destination.latitude, destination.longitude, mode, optimizeFor, onRouteCoordinates]);

  return (
    <div className="rounded-lg border border-border p-3 bg-card">
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2 text-sm text-foreground">
          <Navigation className="h-4 w-4 text-indigo-600" />
          <span className="font-medium">Route preview</span>
        </div>
        <span className="text-[10px] text-muted-foreground">{mode} · {optimizeFor}</span>
      </div>
      {loading ? (
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <Loader2 className="h-3.5 w-3.5 animate-spin" /> Computing route via Ndila…
        </div>
      ) : error ? (
        <p className="text-xs text-red-600">{error}</p>
      ) : route ? (
        <div className="space-y-1 text-xs text-foreground">
          <div className="flex gap-4 tabular-nums">
            {showDistance && (
              <span><span className="text-muted-foreground">Distance</span>{" "}<span className="font-semibold">{formatDistance(route.distanceMeters)}</span></span>
            )}
            {showEta && (
              <span><span className="text-muted-foreground">ETA</span>{" "}<span className="font-semibold">{formatDuration(route.durationSeconds)}</span></span>
            )}
          </div>
          <div className="text-[10px] text-muted-foreground">
            provider {route.provider ?? "—"}{route.fallbackUsed ? " · fallback" : ""}{route.servedFromCache ? " · cache" : ""}
            {route.confidence !== undefined ? ` · confidence ${(route.confidence * 100).toFixed(0)}%` : ""}
          </div>
          {route.warnings?.length ? (
            <ul className="mt-1 text-[10px] text-warning-foreground">
              {route.warnings.map((w, i) => <li key={i}>⚠ {w}</li>)}
            </ul>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
