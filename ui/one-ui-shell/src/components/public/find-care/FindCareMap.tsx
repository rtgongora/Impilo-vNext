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
      // Fills the wrapper, which owns the height — a fixed height here made the map
      // visibly jump from 360px to the real height the moment the bundle landed.
      <div
        className="grid h-full min-h-[12rem] w-full place-items-center rounded-lg bg-slate-100 text-slate-500"
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
  /**
   * Map height. A number is px (default 360); pass "100%" to fill a flex parent
   * that has a definite height — the hero surface does this so the map reaches the
   * bottom of the column instead of leaving dead space under it.
   */
  height?: number | string;
  /** Hide the textual caption below the map (the hero surface shows its own). */
  hideCaption?: boolean;
  /** Pre-built markers (e.g. from unified discovery). When set, `results` are ignored for markers. */
  geoMarkers?: NdilaGeoMarker[];
  /** Collapse the map's layer picker to one button — for narrow embedded maps. */
  compactChrome?: boolean;
  /**
   * Explicit view. Supply this when the caller knows the locality the person chose:
   * fitting to markers instead would zoom out to whatever happens to have coordinates,
   * which re-creates a national view the moment the only mapped results are far away.
   */
  view?: { latitude: number; longitude: number; zoom: number } | null;
}

export function FindCareMap({
  results,
  origin,
  selectedFacilityId,
  height = 360,
  hideCaption = false,
  geoMarkers,
  compactChrome = false,
  view = null,
}: FindCareMapProps) {
  const markers = useMemo<NdilaGeoMarker[]>(() => {
    const facilityMarkers: NdilaGeoMarker[] = geoMarkers
      ? [...geoMarkers]
      : results
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
  }, [results, origin, geoMarkers]);

  const center = useMemo(() => {
    if (view) return { latitude: view.latitude, longitude: view.longitude };
    if (origin) return { latitude: origin.lat, longitude: origin.lng };
    const first = markers.find((m) => m.id !== "you");
    if (first) return { latitude: first.latitude, longitude: first.longitude };
    return { latitude: ZIMBABWE_DEFAULT_CENTER.latitude, longitude: ZIMBABWE_DEFAULT_CENTER.longitude };
  }, [markers, origin, view]);

  const mappable = markers.filter((m) => m.id !== "you").length;

  // The wrapper owns the height so the dynamic-import skeleton occupies exactly the
  // space the map will, and so a filling map has a definite box to resolve against.
  const fills = height === "100%";

  return (
    <div className={fills ? "flex h-full min-h-0 flex-col" : undefined}>
      <div
        className={fills ? "min-h-0 flex-1" : undefined}
        style={fills ? undefined : { height }}
      >
        <NdilaMapLibre
          center={center}
          zoom={view ? view.zoom : mappable > 0 ? 9 : ZIMBABWE_DEFAULT_ZOOM}
          markers={markers}
          tileConfig={{
            provider: "OSM_OSRM",
            vectorTileUrlTemplate: "/internal/v1/public/gateway/map/tiles/{z}/{x}/{y}.mvt",
            maxZoom: 14,
            attribution: "© OpenMapTiles © OpenStreetMap contributors",
          }}
          fitToMarkers={!view && mappable > 0}
          clusterMarkers
          showNavigation
          compactChrome={compactChrome}
          height="100%"
          flyToCenter={false}
        />
      </div>
      {!hideCaption && (
        <p className="mt-2 text-xs text-slate-500">
          {mappable === 0
            ? "None of these results have mapped coordinates yet, so no pins are shown. Use the list below."
            : `${mappable} of ${results.length} result${results.length === 1 ? "" : "s"} have map coordinates. Full details and directions are in the cards below.`}
          {selectedFacilityId != null && " The facility you opened is highlighted in the list."}
        </p>
      )}
    </div>
  );
}
