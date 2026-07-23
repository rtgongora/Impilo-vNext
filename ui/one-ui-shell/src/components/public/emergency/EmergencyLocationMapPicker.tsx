"use client";

/**
 * Map-pin location picker for the emergency triage: tap the map to drop or
 * adjust the pin. Uses the public Ndila street-tile lane (no auth) and the
 * shared NdilaMapLibre component's onMapClick seam. Loaded on demand from the
 * triage location step so the map bundle never weighs on the critical path.
 */

import { useMemo, useState } from "react";
import dynamic from "next/dynamic";
import type { NdilaMapCoordinate } from "@/components/ndila/NdilaMapLibre";

const NdilaMapLibre = dynamic(
  () => import("@/components/ndila/NdilaMapLibre").then((m) => m.NdilaMapLibre),
  {
    ssr: false,
    loading: () => (
      <div className="grid h-[260px] place-items-center rounded-lg bg-slate-100 text-sm text-slate-500">
        Loading map…
      </div>
    ),
  },
);

const ZIMBABWE_CENTER = { latitude: -19.0154, longitude: 29.1549 };

export function EmergencyLocationMapPicker({
  lat,
  lng,
  onPick,
}: {
  lat?: number;
  lng?: number;
  onPick: (coordinate: NdilaMapCoordinate) => void;
}) {
  const [picked, setPicked] = useState<NdilaMapCoordinate | null>(
    lat !== undefined && lng !== undefined ? { latitude: lat, longitude: lng } : null,
  );

  const markers = useMemo(
    () =>
      picked
        ? [{ id: "emergency-pin", label: "Patient location", latitude: picked.latitude, longitude: picked.longitude, markerType: "destination" }]
        : [],
    [picked],
  );

  return (
    <div data-testid="triage-map-picker">
      <NdilaMapLibre
        center={picked ?? ZIMBABWE_CENTER}
        zoom={picked ? 14 : 6}
        markers={markers}
        tileConfig={{
          provider: "OSM_OSRM",
          vectorTileUrlTemplate: "/internal/v1/public/gateway/map/tiles/{z}/{x}/{y}.mvt",
          maxZoom: 14,
          attribution: "© OpenMapTiles © OpenStreetMap contributors",
        }}
        height={260}
        showNavigation
        flyToCenter={false}
        onMapClick={(coordinate) => {
          setPicked(coordinate);
          onPick(coordinate);
        }}
      />
      <p className="mt-1.5 text-xs text-slate-600">
        {picked
          ? "Pin set — tap the map again to adjust it."
          : "Tap the map where the person is."}
      </p>
    </div>
  );
}
