import { useEffect, useState } from "react";
import {
  enrichOpsMapMarkers,
  type EnrichableMapRow,
  type EnrichedOpsMapMarker,
} from "@/lib/ndila/enrich-geo-markers";

export function useEnrichedOpsMarkers(
  rows: EnrichableMapRow[],
  options?: { enabled?: boolean; maxGeocode?: number; purposeOfUse?: string },
) {
  const enabled = options?.enabled ?? true;
  const [markers, setMarkers] = useState<EnrichedOpsMapMarker[]>([]);
  const [loading, setLoading] = useState(false);

  const rowKey = JSON.stringify(rows);

  useEffect(() => {
    if (!enabled || rows.length === 0) {
      setMarkers([]);
      return;
    }

    let cancelled = false;
    setLoading(true);
    enrichOpsMapMarkers(rows, options)
      .then((next) => {
        if (!cancelled) setMarkers(next);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [enabled, rowKey, options?.maxGeocode, options?.purposeOfUse]);

  const exact = markers.filter((m) => m.coordinateSource === "RECORD").length;
  const approximate = markers.filter((m) => m.coordinateSource === "GEOCODED").length;

  return { markers, loading, exact, approximate };
}
