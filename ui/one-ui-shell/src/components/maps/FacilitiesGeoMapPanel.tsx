"use client";

import { useMemo } from "react";
import { OpsMapPanel } from "@/components/operations/OpsMapPanel";
import { useEnrichedOpsMarkers } from "@/hooks/useEnrichedOpsMarkers";
import { useFacilities } from "@/hooks/queries/useFacilities";

interface FacilitiesGeoMapPanelProps {
  title?: string;
  subtitle?: string;
  search?: string;
  province?: string;
  status?: string;
  facilityType?: string;
  size?: number;
  enrichMissingCoordinates?: boolean;
  clusterMarkers?: boolean;
}

export function FacilitiesGeoMapPanel({
  title = "Facility map",
  subtitle = "Tuso facility registry on Ndila tiles",
  search,
  province,
  status,
  facilityType,
  size = 2000,
  enrichMissingCoordinates = true,
  clusterMarkers = true,
}: FacilitiesGeoMapPanelProps) {
  const facilitiesQ = useFacilities({ search, province, status, facilityType, page: 0, size });

  const enrichableRows = useMemo(() => {
    return (facilitiesQ.data?.data ?? []).map((facility) => ({
      id: `facility:${facility.id}`,
      label: facility.attributes.name,
      latitude: facility.attributes.latitude,
      longitude: facility.attributes.longitude,
      district: facility.attributes.district,
      province: facility.attributes.province,
      status: facility.attributes.status,
    }));
  }, [facilitiesQ.data?.data]);

  const { markers, loading: enrichLoading, exact, approximate } = useEnrichedOpsMarkers(
    enrichableRows,
    { enabled: enrichMissingCoordinates && !facilitiesQ.isLoading, maxGeocode: 30 },
  );

  const listed = facilitiesQ.data?.data?.length ?? 0;
  const subtitleText = facilitiesQ.isLoading || enrichLoading
    ? `${subtitle} · loading…`
    : `${subtitle} · ${markers.length} on map (${exact} exact${approximate ? `, ${approximate} approx` : ""}) / ${listed} listed`;

  return (
    <OpsMapPanel
      title={title}
      subtitle={subtitleText}
      markers={markers}
      emptyHint="No geo-tagged or geocodable facilities in this filter — capture location on facility registration."
      height={320}
      clusterMarkers={clusterMarkers}
      showZimbabweAdmin
      fitToMarkers={markers.length > 0}
    />
  );
}
