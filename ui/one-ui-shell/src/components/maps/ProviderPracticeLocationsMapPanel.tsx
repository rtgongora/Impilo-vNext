"use client";

import { useMemo } from "react";
import { OpsMapPanel } from "@/components/operations/OpsMapPanel";
import { useFacilities } from "@/hooks/queries/useFacilities";
import { facilityResourceToGeoMarker } from "@/lib/ndila/geo-map-markers";

interface ProviderPracticeLocationsMapPanelProps {
  facilityIds: string[];
}

export function ProviderPracticeLocationsMapPanel({ facilityIds }: ProviderPracticeLocationsMapPanelProps) {
  const facilitiesQ = useFacilities({ size: 200 });
  const markers = useMemo(() => {
    const idSet = new Set(facilityIds);
    return (facilitiesQ.data?.data ?? [])
      .filter((facility) => idSet.has(facility.id))
      .map((facility) => facilityResourceToGeoMarker(facility))
      .filter((marker): marker is NonNullable<typeof marker> => marker !== null)
      .map((marker) => ({
        id: `practice:${marker.id}`,
        label: marker.label ?? marker.id,
        latitude: marker.latitude,
        longitude: marker.longitude,
        status: marker.status,
      }));
  }, [facilitiesQ.data?.data, facilityIds]);

  return (
    <OpsMapPanel
      title="Practice locations"
      subtitle={
        facilitiesQ.isLoading
          ? "Varapi practice pins on Ndila · loading…"
          : `Varapi practice pins on Ndila · ${markers.length} mapped / ${facilityIds.length} affiliated`
      }
      markers={markers}
      emptyHint="Affiliated facilities without coordinates — capture location on the Tuso facility profile."
      height={280}
      fitToMarkers={markers.length > 0}
    />
  );
}
