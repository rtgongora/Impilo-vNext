"use client";

import { useMemo } from "react";
import { OpsMapPanel } from "@/components/operations/OpsMapPanel";
import { trackingRowsToRoute } from "@/lib/ndila/geo-map-markers";
import { rowToOpsMapMarker as logisticsRowToMarker } from "@/lib/logistics/logistics-map-adapters";

interface DeliveryTrackMapPanelProps {
  delivery?: Record<string, unknown> | null;
  trackingPoints?: unknown[];
}

export function DeliveryTrackMapPanel({ delivery, trackingPoints = [] }: DeliveryTrackMapPanelProps) {
  const markers = useMemo(() => {
    const rows: Array<{ id: string; label: string; latitude: number; longitude: number; status?: string }> = [];
    if (delivery) {
      const destination = logisticsRowToMarker(delivery, { idPrefix: "delivery", kind: "delivery" });
      if (destination?.latitude != null && destination?.longitude != null) {
        rows.push({
          id: destination.id,
          label: destination.label,
          latitude: destination.latitude,
          longitude: destination.longitude,
          status: destination.status,
        });
      }
    }
    const latest = trackingPoints[trackingPoints.length - 1];
    if (latest && typeof latest === "object") {
      const courier = logisticsRowToMarker(latest as Record<string, unknown>, { idPrefix: "courier", kind: "fleet" });
      if (courier?.latitude != null && courier?.longitude != null) {
        rows.push({
          id: courier.id,
          label: "Latest courier position",
          latitude: courier.latitude,
          longitude: courier.longitude,
          status: courier.status,
        });
      }
    }
    return rows;
  }, [delivery, trackingPoints]);

  const route = useMemo(() => trackingRowsToRoute(trackingPoints), [trackingPoints]);

  return (
    <OpsMapPanel
      title="Delivery map"
      subtitle="Privacy-safe track — route from governed location updates"
      markers={markers}
      routeCoordinates={route}
      emptyHint="No geo-tagged tracking points yet — timeline updates still appear below."
      height={300}
      fitToMarkers={markers.length > 0 || route.length > 1}
    />
  );
}
