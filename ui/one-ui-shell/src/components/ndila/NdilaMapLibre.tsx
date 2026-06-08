"use client";

import { useCallback, useEffect, useMemo, useRef } from "react";
import maplibregl, { type LngLatBoundsLike, type Map as MapLibreMap, type Marker } from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import { buildNdilaMapStyle } from "@/lib/ndila/build-ndila-map-style";

export interface NdilaGeoMarker {
  id: string;
  label?: string;
  latitude: number;
  longitude: number;
  markerType?: string;
  status?: string;
}

export interface NdilaMapCoordinate {
  latitude: number;
  longitude: number;
}

export interface NdilaMapLibreProps {
  center: NdilaMapCoordinate;
  zoom?: number;
  markers?: NdilaGeoMarker[];
  routeCoordinates?: NdilaMapCoordinate[];
  height?: number;
  tileConfig?: {
    provider?: string;
    tileUrlTemplate?: string;
    maxZoom?: number;
    attribution?: string;
  } | null;
  fitToMarkers?: boolean;
  showNavigation?: boolean;
  className?: string;
}

const MARKER_COLORS: Record<string, string> = {
  facility: "#4f46e5",
  site: "#d97706",
  delivery: "#e11d48",
  fleet: "#0d9488",
  courier: "#7c3aed",
  task: "#0369a1",
  wellness: "#059669",
  origin: "#2563eb",
  destination: "#dc2626",
};

function markerColor(marker: NdilaGeoMarker): string {
  if (marker.markerType && MARKER_COLORS[marker.markerType]) return MARKER_COLORS[marker.markerType];
  return "#e11d48";
}

function routeGeoJson(routeCoordinates: NdilaMapCoordinate[]) {
  return {
    type: "Feature" as const,
    geometry: {
      type: "LineString" as const,
      coordinates: routeCoordinates.map((point) => [point.longitude, point.latitude]),
    },
    properties: {},
  };
}

function ensureRouteLayer(map: MapLibreMap) {
  if (!map.getSource("ndila-route")) {
    map.addSource("ndila-route", {
      type: "geojson",
      data: { type: "FeatureCollection", features: [] },
    });
    map.addLayer({
      id: "ndila-route-line",
      type: "line",
      source: "ndila-route",
      paint: { "line-color": "#0d9488", "line-width": 3, "line-opacity": 0.85 },
    });
  }
}

export function NdilaMapLibre({
  center,
  zoom = 12,
  markers = [],
  routeCoordinates = [],
  height = 320,
  tileConfig,
  fitToMarkers = false,
  showNavigation = true,
  className,
}: NdilaMapLibreProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markerRefs = useRef<Marker[]>([]);
  const markersRef = useRef(markers);
  const routeRef = useRef(routeCoordinates);
  const fitRef = useRef(fitToMarkers);

  markersRef.current = markers;
  routeRef.current = routeCoordinates;
  fitRef.current = fitToMarkers;

  const style = useMemo(() => buildNdilaMapStyle(tileConfig), [tileConfig]);

  const paintOverlay = useCallback(() => {
    const map = mapRef.current;
    if (!map || !map.isStyleLoaded()) return;

    ensureRouteLayer(map);

    markerRefs.current.forEach((marker) => marker.remove());
    markerRefs.current = [];

    for (const marker of markersRef.current) {
      const element = document.createElement("button");
      element.type = "button";
      element.className = "ndila-map-marker";
      element.title = marker.label ?? marker.id;
      element.style.width = "14px";
      element.style.height = "14px";
      element.style.borderRadius = "9999px";
      element.style.border = "2px solid #fff";
      element.style.background = markerColor(marker);
      element.style.boxShadow = "0 1px 4px rgba(15,23,42,0.35)";
      element.style.cursor = "pointer";

      const popup = marker.label
        ? new maplibregl.Popup({ offset: 12, closeButton: false }).setText(
            marker.status ? `${marker.label} · ${marker.status}` : marker.label,
          )
        : undefined;

      const mapMarker = new maplibregl.Marker({ element })
        .setLngLat([marker.longitude, marker.latitude])
        .addTo(map);
      if (popup) mapMarker.setPopup(popup);
      markerRefs.current.push(mapMarker);
    }

    const route = routeRef.current;
    const source = map.getSource("ndila-route") as maplibregl.GeoJSONSource | undefined;
    if (route.length >= 2) {
      source?.setData(routeGeoJson(route));
    } else {
      source?.setData({ type: "FeatureCollection", features: [] });
    }

    if (fitRef.current && markersRef.current.length > 0) {
      const bounds = new maplibregl.LngLatBounds();
      markersRef.current.forEach((marker) => bounds.extend([marker.longitude, marker.latitude]));
      route.forEach((point) => bounds.extend([point.longitude, point.latitude]));
      map.fitBounds(bounds as LngLatBoundsLike, { padding: 48, maxZoom: 14, duration: 0 });
    }
  }, []);

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;

    const map = new maplibregl.Map({
      container: containerRef.current,
      style,
      center: [center.longitude, center.latitude],
      zoom,
      attributionControl: false,
    });
    mapRef.current = map;

    if (showNavigation) {
      map.addControl(new maplibregl.NavigationControl({ showCompass: false }), "top-right");
    }
    if (tileConfig?.attribution) {
      map.addControl(new maplibregl.AttributionControl({ compact: true }), "bottom-right");
    }

    map.on("load", paintOverlay);
    map.on("styledata", () => {
      if (map.isStyleLoaded()) paintOverlay();
    });

    return () => {
      markerRefs.current.forEach((marker) => marker.remove());
      markerRefs.current = [];
      map.remove();
      mapRef.current = null;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    if (map.isStyleLoaded()) {
      map.setStyle(style, { diff: false });
    } else {
      map.once("load", () => map.setStyle(style, { diff: false }));
    }
  }, [style]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    map.setCenter([center.longitude, center.latitude]);
    if (!fitToMarkers) map.setZoom(zoom);
  }, [center.latitude, center.longitude, zoom, fitToMarkers]);

  useEffect(() => {
    paintOverlay();
  }, [markers, routeCoordinates, fitToMarkers, paintOverlay]);

  return (
    <div
      ref={containerRef}
      className={className ?? "w-full rounded-lg overflow-hidden"}
      style={{ height }}
      data-testid="ndila-maplibre-canvas"
      aria-label="Interactive Ndila map"
    />
  );
}
