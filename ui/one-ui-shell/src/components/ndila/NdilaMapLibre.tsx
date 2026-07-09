"use client";

import { useCallback, useEffect, useMemo, useRef } from "react";
import maplibregl, {
  type GeoJSONSource,
  type LngLatBoundsLike,
  type Map as MapLibreMap,
  type Marker,
  type RequestParameters,
} from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import { buildNdilaMapStyle } from "@/lib/ndila/build-ndila-map-style";
import { ZIMBABWE_ADMIN_GEOJSON_URL, ZIMBABWE_BOUNDS } from "@/lib/ndila/zimbabwe-admin";

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
  showZimbabweAdmin?: boolean;
  clusterMarkers?: boolean;
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
  HEALTH_FACILITY: "#4f46e5",
  FACILITY: "#4f46e5",
};

function markerColor(marker: NdilaGeoMarker): string {
  const key = marker.markerType?.toLowerCase() ?? marker.markerType;
  if (key && MARKER_COLORS[key]) return MARKER_COLORS[key];
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

function markersFeatureCollection(markers: NdilaGeoMarker[]) {
  return {
    type: "FeatureCollection" as const,
    features: markers.map((marker) => ({
      type: "Feature" as const,
      geometry: {
        type: "Point" as const,
        coordinates: [marker.longitude, marker.latitude],
      },
      properties: {
        id: marker.id,
        label: marker.label ?? marker.id,
        markerType: marker.markerType ?? "default",
        color: markerColor(marker),
      },
    })),
  };
}

function readAuthToken(): string | null {
  if (typeof window === "undefined") return null;
  return sessionStorage.getItem("exp:auth_token");
}

function ndilaTileTransformRequest(url: string): RequestParameters {
  if (url.includes("/internal/v1/ndila/tiles/") && url.endsWith(".png")) {
    const token = readAuthToken();
    const headers: Record<string, string> = {};
    if (token) headers.Authorization = `Bearer ${token}`;
    return { url, headers, credentials: "include" };
  }
  return { url };
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

function ensureZimbabweAdminLayers(map: MapLibreMap) {
  if (map.getSource("ndila-zw-admin")) return;

  map.addSource("ndila-zw-admin", {
    type: "geojson",
    data: ZIMBABWE_ADMIN_GEOJSON_URL,
  });

  map.addLayer({
    id: "ndila-zw-country-fill",
    type: "fill",
    source: "ndila-zw-admin",
    filter: ["==", ["get", "adminLevel"], "country"],
    paint: { "fill-color": "#dbeafe", "fill-opacity": 0.35 },
  });

  map.addLayer({
    id: "ndila-zw-province-fill",
    type: "fill",
    source: "ndila-zw-admin",
    filter: ["==", ["get", "adminLevel"], "province"],
    paint: { "fill-color": "#bfdbfe", "fill-opacity": 0.22 },
  });

  map.addLayer({
    id: "ndila-zw-admin-outline",
    type: "line",
    source: "ndila-zw-admin",
    paint: { "line-color": "#64748b", "line-width": 1.2, "line-opacity": 0.85 },
  });

  map.addLayer({
    id: "ndila-zw-province-labels",
    type: "symbol",
    source: "ndila-zw-admin",
    filter: ["==", ["get", "adminLevel"], "province"],
    layout: {
      "text-field": ["get", "name"],
      "text-size": 11,
      "text-font": ["Open Sans Regular", "Arial Unicode MS Regular"],
    },
    paint: {
      "text-color": "#334155",
      "text-halo-color": "#f8fafc",
      "text-halo-width": 1.2,
    },
  });
}

function ensureClusterLayers(map: MapLibreMap, markers: NdilaGeoMarker[]) {
  const source = map.getSource("ndila-markers") as GeoJSONSource | undefined;
  const data = markersFeatureCollection(markers);
  if (!source) {
    map.addSource("ndila-markers", {
      type: "geojson",
      data,
      cluster: true,
      clusterMaxZoom: 14,
      clusterRadius: 48,
    });
    map.addLayer({
      id: "ndila-clusters",
      type: "circle",
      source: "ndila-markers",
      filter: ["has", "point_count"],
      paint: {
        "circle-color": "#4f46e5",
        "circle-radius": ["step", ["get", "point_count"], 16, 20, 22, 100, 28],
        "circle-opacity": 0.85,
      },
    });
    map.addLayer({
      id: "ndila-cluster-count",
      type: "symbol",
      source: "ndila-markers",
      filter: ["has", "point_count"],
      layout: {
        "text-field": ["get", "point_count_abbreviated"],
        "text-size": 11,
        "text-font": ["Open Sans Bold", "Arial Unicode MS Bold"],
      },
      paint: { "text-color": "#ffffff" },
    });
    map.addLayer({
      id: "ndila-unclustered-point",
      type: "circle",
      source: "ndila-markers",
      filter: ["!", ["has", "point_count"]],
      paint: {
        "circle-color": ["get", "color"],
        "circle-radius": 6,
        "circle-stroke-width": 2,
        "circle-stroke-color": "#ffffff",
      },
    });
  } else {
    source.setData(data);
  }
}

function removeClusterLayers(map: MapLibreMap) {
  for (const id of ["ndila-unclustered-point", "ndila-cluster-count", "ndila-clusters"]) {
    if (map.getLayer(id)) map.removeLayer(id);
  }
  if (map.getSource("ndila-markers")) map.removeSource("ndila-markers");
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
  showZimbabweAdmin = true,
  clusterMarkers = false,
  className,
}: NdilaMapLibreProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markerRefs = useRef<Marker[]>([]);
  const markersRef = useRef(markers);
  const routeRef = useRef(routeCoordinates);
  const fitRef = useRef(fitToMarkers);
  const clusterRef = useRef(clusterMarkers);
  const adminRef = useRef(showZimbabweAdmin);

  markersRef.current = markers;
  routeRef.current = routeCoordinates;
  fitRef.current = fitToMarkers;
  clusterRef.current = clusterMarkers;
  adminRef.current = showZimbabweAdmin;

  const style = useMemo(() => buildNdilaMapStyle(tileConfig), [tileConfig]);
  const useClusterLayer = clusterMarkers && markers.length > 12;

  const paintOverlay = useCallback(() => {
    const map = mapRef.current;
    if (!map || !map.isStyleLoaded()) return;

    if (adminRef.current) {
      ensureZimbabweAdminLayers(map);
    }

    ensureRouteLayer(map);

    markerRefs.current.forEach((marker) => marker.remove());
    markerRefs.current = [];

    if (clusterRef.current && markersRef.current.length > 12) {
      removeClusterLayers(map);
      ensureClusterLayers(map, markersRef.current);
    } else {
      removeClusterLayers(map);
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
    }

    const route = routeRef.current;
    const source = map.getSource("ndila-route") as GeoJSONSource | undefined;
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
    } else if (adminRef.current && markersRef.current.length === 0 && !fitRef.current) {
      map.fitBounds(
        [
          [ZIMBABWE_BOUNDS.west, ZIMBABWE_BOUNDS.south],
          [ZIMBABWE_BOUNDS.east, ZIMBABWE_BOUNDS.north],
        ],
        { padding: 24, duration: 0 },
      );
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
      transformRequest: ndilaTileTransformRequest,
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
  }, [markers, routeCoordinates, fitToMarkers, showZimbabweAdmin, useClusterLayer, paintOverlay]);

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
