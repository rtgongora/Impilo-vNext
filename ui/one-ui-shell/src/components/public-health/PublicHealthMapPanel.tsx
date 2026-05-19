"use client";

import { Loader2, MapPin } from "lucide-react";
import { usePublicHealthMapMarkers } from "@/hooks/queries/usePublicHealth";

export function PublicHealthMapPanel() {
  const markersQ = usePublicHealthMapMarkers();
  const markers = markersQ.data ?? [];

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-5">
      <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2 mb-3">
        <MapPin className="h-4 w-4 text-indigo-600" /> Operations map
      </h3>
      <p className="text-[10px] text-gray-500 mb-3">
        Geo markers from outbreaks and field tasks with recorded coordinates (OpenStreetMap embed).
      </p>
      {markersQ.isPending ? (
        <div className="flex items-center gap-2 text-sm text-gray-500 py-6 justify-center">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading map data…
        </div>
      ) : markers.length === 0 ? (
        <p className="text-xs text-gray-500 py-4 text-center">
          No geo-tagged outbreaks or field tasks yet. Record coordinates when submitting outbreaks or field activities.
        </p>
      ) : (
        <>
          <div className="relative w-full h-56 rounded-lg border border-gray-200 overflow-hidden bg-slate-100 mb-3">
            <iframe
              title="Public health operations map"
              className="absolute inset-0 w-full h-full border-0"
              src={`https://www.openstreetmap.org/export/embed.html?bbox=${bboxForMarkers(markers)}&layer=mapnik&marker=${markers[0].latitude},${markers[0].longitude}`}
            />
          </div>
          <div className="space-y-2 max-h-40 overflow-y-auto">
            {markers.map((m) => (
              <div key={`${m.markerType}-${m.id}`} className="flex justify-between text-xs border border-gray-100 rounded p-2">
                <span className="font-medium text-gray-900">{m.label}</span>
                <span className="text-gray-500">
                  {m.markerType} · {m.status} · {m.latitude.toFixed(4)}, {m.longitude.toFixed(4)}
                </span>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

function bboxForMarkers(markers: { latitude: number; longitude: number }[]) {
  const lats = markers.map((m) => m.latitude);
  const lngs = markers.map((m) => m.longitude);
  const pad = 0.05;
  const minLat = Math.min(...lats) - pad;
  const maxLat = Math.max(...lats) + pad;
  const minLng = Math.min(...lngs) - pad;
  const maxLng = Math.max(...lngs) + pad;
  return `${minLng}%2C${minLat}%2C${maxLng}%2C${maxLat}`;
}
