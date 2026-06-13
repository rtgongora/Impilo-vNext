"use client";

import { MapPin } from "lucide-react";
import { usePublicHealthMapMarkers } from "@/hooks/queries/usePublicHealth";
import { NdilaPublicHealthRiskMap } from "./NdilaPublicHealthRiskMap";

export function PublicHealthMapPanel() {
  const markersQ = usePublicHealthMapMarkers();
  const markers = markersQ.data ?? [];

  return (
    <div className="bg-card rounded-lg border border-border p-5">
      <h3 className="text-sm font-semibold text-foreground flex items-center gap-2 mb-3">
        <MapPin className="h-4 w-4 text-indigo-600" /> Operations map
      </h3>
      <p className="text-[10px] text-muted-foreground mb-3">
        Geo markers from outbreaks and field tasks with recorded coordinates (Ndila MapLibre).
      </p>
      <NdilaPublicHealthRiskMap markers={markers} loading={markersQ.isPending} />
    </div>
  );
}
