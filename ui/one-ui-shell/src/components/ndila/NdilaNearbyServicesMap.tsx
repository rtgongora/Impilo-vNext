"use client";

/**
 * NdilaNearbyServicesMap — composite "find services near me" map.
 *
 * Backed by `POST /api/v1/ndila/spatial/nearby`. Used by Citizen App,
 * Provider App "nearby clinics", Marketplace "nearby pharmacies", and
 * Emergency Ops "nearest responder" workflows.
 */

import { useEffect, useState } from "react";
import { ndilaNearby, type NdilaCoordinate, type NdilaLocation } from "@/lib/ndila/ndila-client";
import { NdilaMap, type NdilaMapMarker } from "./NdilaMap";

interface Props {
  origin: NdilaCoordinate;
  radiusMeters?: number;
  entityTypes?: string[];
  limit?: number;
  height?: number;
}

export function NdilaNearbyServicesMap({
  origin, radiusMeters = 10_000, entityTypes, limit = 25, height = 360,
}: Props) {
  const [items, setItems] = useState<NdilaLocation[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true); setError(null);
    ndilaNearby({ origin, radiusMeters, entityTypes, limit })
      .then((r) => { if (!cancelled) setItems(r.items ?? []); })
      .catch(() => { if (!cancelled) setError("Ndila nearby search unavailable"); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [origin.latitude, origin.longitude, radiusMeters, JSON.stringify(entityTypes ?? []), limit]);

  const markers: NdilaMapMarker[] = items
    .filter((i) => i.latitude !== undefined && i.longitude !== undefined)
    .map((i) => ({
      id: i.id,
      label: i.name,
      latitude: i.latitude!,
      longitude: i.longitude!,
      markerType: i.locationType,
    }));

  return (
    <div className="space-y-2">
      <NdilaMap center={origin} zoom={13} markers={markers} height={height} layers={["nearby-services"]} mode="CITIZEN" />
      <div className="text-[10px] text-gray-500">
        {loading ? "Searching nearby services…" : error ?? `${items.length} services within ${(radiusMeters / 1000).toFixed(0)}km`}
      </div>
    </div>
  );
}
