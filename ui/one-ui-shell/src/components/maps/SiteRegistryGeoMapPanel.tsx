"use client";

import { useMemo } from "react";
import { OpsMapPanel } from "@/components/operations/OpsMapPanel";
import { useEnrichedOpsMarkers } from "@/hooks/useEnrichedOpsMarkers";
import { useSiteRegistrySites } from "@/hooks/queries/useSiteRegistry";

export function SiteRegistryGeoMapPanel() {
  const sitesQ = useSiteRegistrySites({ page: 0, size: 100 });

  const enrichableRows = useMemo(() => {
    return (sitesQ.data ?? []).map((row) => ({
      id: `site:${row.siteId}`,
      label: row.name,
      latitude: row.latitude,
      longitude: row.longitude,
      district: row.district,
      province: row.province,
      status: row.regulatoryStatus,
    }));
  }, [sitesQ.data]);

  const { markers, loading: enrichLoading, exact, approximate } = useEnrichedOpsMarkers(
    enrichableRows,
    { enabled: !sitesQ.isLoading, maxGeocode: 30, purposeOfUse: "PUBLIC_HEALTH_OPERATIONS" },
  );

  return (
    <OpsMapPanel
      title="Public health site map"
      subtitle={
        sitesQ.isLoading || enrichLoading
          ? "Indawo sites on Ndila · loading…"
          : `Indawo sites on Ndila · ${markers.length} on map (${exact} exact${approximate ? `, ${approximate} approx` : ""}) / ${sitesQ.data?.length ?? 0} listed`
      }
      markers={markers}
      emptyHint="Sites without coordinates — add geo on site profile or rely on district-level approximation."
      height={320}
    />
  );
}
