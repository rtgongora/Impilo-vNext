"use client";

import { useMemo } from "react";
import { OpsMapPanel } from "@/components/operations/OpsMapPanel";
import { rowToGeoMarker, trackingRowsToRoute } from "@/lib/ndila/geo-map-markers";

function extractTrackingRows(payload: unknown): unknown[] {
  if (!payload || typeof payload !== "object") return [];
  const record = payload as Record<string, unknown>;
  if (Array.isArray(record.data)) return record.data;
  if (Array.isArray(record.events)) return record.events;
  if (Array.isArray(record.points)) return record.points;
  if (Array.isArray(record.tracking)) return record.tracking;
  if (Array.isArray(record.locations)) return record.locations;
  return [];
}

interface CommerceDeliveryMapPanelProps {
  order?: Record<string, unknown> | null;
  trackingPayload?: unknown;
}

export function CommerceDeliveryMapPanel({ order, trackingPayload }: CommerceDeliveryMapPanelProps) {
  const trackingPoints = useMemo(() => extractTrackingRows(trackingPayload), [trackingPayload]);

  const markers = useMemo(() => {
    const rows: Array<{ id: string; label: string; latitude: number; longitude: number; status?: string }> = [];
    if (order) {
      const destination = rowToGeoMarker(order, {
        id: "order-destination",
        label: String(order.destination_label ?? order.pickup_location ?? "Delivery destination"),
        markerType: "delivery",
        status: String(order.status ?? ""),
      });
      if (destination) {
        rows.push({
          id: destination.id,
          label: destination.label ?? destination.id,
          latitude: destination.latitude,
          longitude: destination.longitude,
          status: destination.status,
        });
      }
    }
    const latest = trackingPoints[trackingPoints.length - 1];
    if (latest && typeof latest === "object") {
      const courier = rowToGeoMarker(latest as Record<string, unknown>, {
        id: "commerce-courier",
        label: "Latest fulfilment position",
        markerType: "fleet",
      });
      if (courier) {
        rows.push({
          id: courier.id,
          label: courier.label ?? courier.id,
          latitude: courier.latitude,
          longitude: courier.longitude,
          status: courier.status,
        });
      }
    }
    return rows;
  }, [order, trackingPoints]);

  const route = useMemo(() => trackingRowsToRoute(trackingPoints), [trackingPoints]);

  return (
    <OpsMapPanel
      title="Fulfilment map"
      subtitle="Marketplace delivery route and pickup geography"
      markers={markers}
      routeCoordinates={route}
      emptyHint="No geo-tagged fulfilment updates yet — order status still appears in the timeline."
      height={300}
      fitToMarkers={markers.length > 0 || route.length > 1}
    />
  );
}
