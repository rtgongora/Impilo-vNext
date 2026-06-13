"use client";

import { useMemo } from "react";
import { MapPin } from "lucide-react";
import { NdilaMap } from "@/components/ndila/NdilaMap";
import { useEnrichedOpsMarkers } from "@/hooks/useEnrichedOpsMarkers";
import { useSiteRegistrySites } from "@/hooks/queries/useSiteRegistry";
import { computeMapCenter, DEFAULT_MAP_CENTER } from "@/lib/ndila/geo-map-markers";

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

  const ndilaMarkers = useMemo(
    () =>
      markers
        .filter((marker) => marker.latitude != null && marker.longitude != null)
        .map((marker) => ({
          id: marker.id,
          label: marker.label,
          latitude: marker.latitude!,
          longitude: marker.longitude!,
          markerType: "PUBLIC_HEALTH_SITE",
          status: marker.status,
        })),
    [markers],
  );

  const center = computeMapCenter(
    ndilaMarkers.map((marker) => ({
      id: marker.id,
      label: marker.label ?? marker.id,
      latitude: marker.latitude,
      longitude: marker.longitude,
    })),
    DEFAULT_MAP_CENTER,
  );

  return (
    <section className="rounded-xl border border-border bg-card p-4">
      <div className="mb-3 flex items-center gap-2">
        <MapPin className="h-4 w-4 text-rose-600" />
        <div>
          <h3 className="text-sm font-semibold text-foreground">Public health site map</h3>
          <p className="text-xs text-muted-foreground">
            {sitesQ.isLoading || enrichLoading
              ? "Indawo sites on Ndila · loading…"
              : `Indawo sites on Ndila · ${ndilaMarkers.length} on map (${exact} exact${approximate ? `, ${approximate} approx` : ""}) / ${sitesQ.data?.length ?? 0} listed`}
          </p>
        </div>
      </div>

      <NdilaMap
        center={center}
        zoom={ndilaMarkers.length > 1 ? 10 : 12}
        markers={ndilaMarkers}
        height={320}
        mode="DASHBOARD"
        layers={["public-health-sites"]}
        fitToMarkers={ndilaMarkers.length > 0}
      />

      {markers.length === 0 ? (
        <p className="mt-2 text-xs text-muted-foreground">
          Sites without coordinates — add geo on site profile or rely on district-level approximation.
        </p>
      ) : (
        <ul className="mt-3 max-h-48 space-y-2 overflow-y-auto text-xs">
          {markers.map((marker) => (
            <li key={marker.id} className="flex justify-between gap-2 rounded-lg bg-background px-2 py-1.5">
              <span className="font-medium text-foreground">{marker.label}</span>
              <span className="text-muted-foreground">
                {marker.latitude != null && marker.longitude != null
                  ? `${marker.latitude.toFixed(3)}, ${marker.longitude.toFixed(3)}`
                  : (marker.status ?? "no coordinates")}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
