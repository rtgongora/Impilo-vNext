"use client";

/**
 * Map overview for find-care results.
 *
 * Reuses the shared Ndila MapLibre component. The public lane serves self-hosted
 * street tiles (Martin MVT via the BFF public gateway passthrough,
 * /internal/v1/public/gateway/map/tiles) with labels from committed Noto Sans
 * glyphs — no CDN, no auth, no PII. When the street stack is absent or a tile
 * fetch fails, MapLibre simply skips those tiles and the bundled Zimbabwe
 * admin-boundary layers (drawn on top of the base style) keep the map readable —
 * graceful degrade, never blank. It is a spatial overview only: full parity of
 * actions is guaranteed by rendering the same result cards beneath the map (see
 * FindCareExperience), so nothing on the map is a dead end and the list remains
 * the low-bandwidth path.
 */

import { useMemo } from "react";
import dynamic from "next/dynamic";
import { Loader2 } from "lucide-react";
import type { CareResult } from "@/lib/find-care/types";
import type { NdilaGeoMarker } from "@/components/ndila/NdilaMapLibre";
import {
  ZIMBABWE_DEFAULT_CENTER,
  ZIMBABWE_DEFAULT_ZOOM,
} from "@/lib/ndila/zimbabwe-admin";

// Lazy-load MapLibre so the (heavier) map bundle is fetched only when a person
// switches to map view — the list stays fast on low bandwidth.
const NdilaMapLibre = dynamic(
  () => import("@/components/ndila/NdilaMapLibre").then((m) => m.NdilaMapLibre),
  {
    ssr: false,
    loading: () => (
      <div
        className="grid h-[360px] w-full place-items-center rounded-lg bg-slate-100 text-slate-500"
        role="status"
      >
        <span className="flex items-center gap-2 text-sm">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
          Loading map…
        </span>
      </div>
    ),
  },
);

interface FindCareMapProps {
  results: CareResult[];
  /** The person's shared origin, if any (rendered as a distinct pin). */
  origin?: { lat: number; lng: number } | null;
  selectedFacilityId?: number | null;
}

export function FindCareMap({ results, origin, selectedFacilityId }: FindCareMapProps) {
  const markers = useMemo<NdilaGeoMarker[]>(() => {
    const facilityMarkers: NdilaGeoMarker[] = results
      .filter((r) => r.latitude != null && r.longitude != null && r.facilityId != null)
      .map((r) => ({
        id: String(r.facilityId),
        label: r.name ?? "Facility",
        latitude: r.latitude as number,
        longitude: r.longitude as number,
        markerType: "facility",
        status: r.serviceMatch ? "Offers this service" : "In directory",
      }));

    if (origin) {
      facilityMarkers.unshift({
        id: "you",
        label: "Your location",
        latitude: origin.lat,
        longitude: origin.lng,
        markerType: "origin",
      });
    }
    return facilityMarkers;
  }, [results, origin]);

  const center = useMemo(() => {
    if (origin) return { latitude: origin.lat, longitude: origin.lng };
    const first = markers.find((m) => m.id !== "you");
    if (first) return { latitude: first.latitude, longitude: first.longitude };
    return { latitude: ZIMBABWE_DEFAULT_CENTER.latitude, longitude: ZIMBABWE_DEFAULT_CENTER.longitude };
  }, [markers, origin]);

  const mappable = markers.filter((m) => m.id !== "you").length;

  return (
    <div>
      <NdilaMapLibre
        center={center}
        zoom={mappable > 0 ? 9 : ZIMBABWE_DEFAULT_ZOOM}
        markers={markers}
        tileConfig={{
          provider: "OSM_OSRM",
          vectorTileUrlTemplate: "/internal/v1/public/gateway/map/tiles/{z}/{x}/{y}.mvt",
          maxZoom: 14,
          attribution: "© OpenStreetMap contributors",
        }}
        fitToMarkers={mappable > 0}
        clusterMarkers
        showNavigation
        height={360}
        flyToCenter={false}
      />
      <p className="mt-2 text-xs text-slate-500">
        {mappable === 0
          ? "None of these results have mapped coordinates yet, so no pins are shown. Use the list below."
          : `${mappable} of ${results.length} result${results.length === 1 ? "" : "s"} have map coordinates. Full details and directions are in the cards below.`}
        {selectedFacilityId != null && " The facility you opened is highlighted in the list."}
      </p>
    </div>
  );
}
