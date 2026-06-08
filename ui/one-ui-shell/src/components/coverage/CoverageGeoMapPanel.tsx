"use client";

import { useMemo } from "react";
import { Loader2, MapPin } from "lucide-react";
import { NdilaMap } from "@/components/ndila/NdilaMap";
import { useNdilaTileConfig } from "@/hooks/queries/useNdila";
import { useFacilities } from "@/hooks/queries/useFacilities";

const DEFAULT_CENTER = { latitude: -17.8292, longitude: 31.0522 };

export function CoverageGeoMapPanel() {
  const ndilaQ = useNdilaTileConfig();
  const facilitiesQ = useFacilities({ status: "ACTIVE", page: 0, size: 120 });

  const markers = useMemo(() => {
    return (facilitiesQ.data?.data ?? [])
      .filter((f) => f.attributes.latitude != null && f.attributes.longitude != null)
      .map((f) => ({
        id: `coverage-facility:${f.id}`,
        label: f.attributes.name,
        latitude: Number(f.attributes.latitude),
        longitude: Number(f.attributes.longitude),
        markerType: "FACILITY",
        status: f.attributes.status,
      }));
  }, [facilitiesQ.data?.data]);

  const center = markers[0]
    ? { latitude: markers[0].latitude, longitude: markers[0].longitude }
    : DEFAULT_CENTER;

  return (
    <section className="rounded-xl border border-violet-200 bg-white p-4" data-testid="coverage-geo-map-panel">
      <div className="flex items-center gap-2 text-sm font-semibold text-gray-900">
        <MapPin className="h-4 w-4 text-violet-600" />
        Coverage geography (Ndila)
      </div>
      <p className="mt-1 text-xs text-gray-500">
        Contracted facility footprint for payer network planning — tiles via{" "}
        <code className="text-[10px]">GET /internal/v1/ndila/tiles/config</code>, sites from Tuso facility registry.
      </p>

      {ndilaQ.isLoading || facilitiesQ.isLoading ? (
        <div className="mt-4 flex items-center gap-2 py-8 text-sm text-gray-500">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading map context…
        </div>
      ) : ndilaQ.isError ? (
        <p className="mt-4 text-xs text-red-600">Ndila tile config unavailable.</p>
      ) : (
        <div className="mt-4 overflow-hidden rounded-xl border border-gray-200">
          <NdilaMap center={center} zoom={6} markers={markers} height={360} mode="DASHBOARD" fitToMarkers />
        </div>
      )}

      <p className="mt-2 text-[11px] text-gray-500">
        {markers.length} geo-tagged facilities on map · provider contracting detail on the Contracts tab.
      </p>
    </section>
  );
}
