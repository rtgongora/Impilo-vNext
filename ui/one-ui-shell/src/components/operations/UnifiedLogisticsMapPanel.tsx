"use client";

import { useMemo } from "react";
import { OpsMapPanel } from "@/components/operations/OpsMapPanel";
import { useDispatchDeliveries, useDispatchFleet } from "@/hooks/queries/useDispatchOps";
import { useNhumeDeliveries, useNhumeFleet } from "@/hooks/useNhume";
import {
  extractCollection,
  mergeLogisticsMarkers,
  rowsToOpsMapMarkers,
} from "@/lib/logistics/logistics-map-adapters";

interface UnifiedLogisticsMapPanelProps {
  title?: string;
  subtitle?: string;
  includeNhume?: boolean;
  includeDispatch?: boolean;
  nhumeDeliveryStatus?: string;
}

/**
 * Shared Ndila-backed map for Nhume (last-mile) and dispatch (task ops) surfaces.
 */
export function UnifiedLogisticsMapPanel({
  title = "Unified logistics map",
  subtitle = "Nhume deliveries and dispatch fleet on one Ndila tile layer",
  includeNhume = true,
  includeDispatch = true,
  nhumeDeliveryStatus,
}: UnifiedLogisticsMapPanelProps) {
  const nhumeDeliveries = useNhumeDeliveries(
    includeNhume ? { status: nhumeDeliveryStatus, size: 50 } : undefined,
  );
  const nhumeFleet = useNhumeFleet();
  const dispatchDeliveries = useDispatchDeliveries();
  const dispatchFleet = useDispatchFleet();

  const markers = useMemo(() => {
    const nhumeDeliveryRows = includeNhume ? extractCollection(nhumeDeliveries.data) : [];
    const nhumeFleetRows = includeNhume ? extractCollection(nhumeFleet.data) : [];
    const dispatchDeliveryRows = includeDispatch ? extractCollection(dispatchDeliveries.data) : [];
    const dispatchFleetRows = includeDispatch ? extractCollection(dispatchFleet.data) : [];

    return mergeLogisticsMarkers(
      rowsToOpsMapMarkers(nhumeDeliveryRows, { idPrefix: "nhume", kind: "delivery" }),
      rowsToOpsMapMarkers(nhumeFleetRows, { idPrefix: "nhume", kind: "fleet" }),
      rowsToOpsMapMarkers(dispatchDeliveryRows, { idPrefix: "dispatch", kind: "delivery" }),
      rowsToOpsMapMarkers(dispatchFleetRows, { idPrefix: "dispatch", kind: "fleet" }),
    );
  }, [
    includeDispatch,
    includeNhume,
    nhumeDeliveries.data,
    nhumeFleet.data,
    dispatchDeliveries.data,
    dispatchFleet.data,
  ]);

  const loading =
    (includeNhume && (nhumeDeliveries.isLoading || nhumeFleet.isLoading)) ||
    (includeDispatch && (dispatchDeliveries.isLoading || dispatchFleet.isLoading));

  return (
    <OpsMapPanel
      title={title}
      subtitle={loading ? `${subtitle} · loading…` : subtitle}
      markers={markers}
      emptyHint="No geo-tagged Nhume or dispatch assets yet. Deliveries without coordinates still appear in operator lists below."
    />
  );
}
