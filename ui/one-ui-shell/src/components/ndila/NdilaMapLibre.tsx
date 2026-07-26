"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import maplibregl, {
  type GeoJSONSource,
  type LngLatBoundsLike,
  type Map as MapLibreMap,
  type Marker,
  type RequestParameters,
} from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import {
  buildNdilaMapStyle,
  streetsTilesAvailable,
  type NdilaTileConfigInput,
} from "@/lib/ndila/build-ndila-map-style";
import {
  ZIMBABWE_ADMIN_GEOJSON_URL,
  ZIMBABWE_BOUNDS,
  ZIMBABWE_PLACES_GEOJSON_URL,
} from "@/lib/ndila/zimbabwe-admin";
import { NdilaMapChrome, type NdilaMapLayerMode } from "./NdilaMapChrome";

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
  /**
   * Shell height. A number is px; a string is passed through as a CSS length, so
   * "100%" lets the map fill a flex/grid parent that has a definite height. A
   * percentage only resolves if that parent is definite — otherwise the shell
   * collapses to 0 and the canvas paints nothing.
   */
  height?: number | string;
  tileConfig?: NdilaTileConfigInput | null;
  fitToMarkers?: boolean;
  showNavigation?: boolean;
  showZimbabweAdmin?: boolean;
  showMapChrome?: boolean;
  clusterMarkers?: boolean;
  flyToCenter?: boolean;
  className?: string;
  /**
   * Plain-map click → coordinate (e.g. drop/adjust a location pin). Fires for clicks on
   * the base map; cluster clicks keep their zoom-in behaviour.
   */
  onMapClick?: (coordinate: NdilaMapCoordinate) => void;
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
  if (url.includes("/internal/v1/ndila/tiles/") && (url.endsWith(".png") || url.endsWith(".mvt"))) {
    // MapLibre fetches tiles itself, bypassing api-client — synthesize the
    // mandatory v1.1 trust headers or the BFF header filter 400s every tile.
    const headers: Record<string, string> = {
      "X-Tenant-ID": sessionStorage.getItem("exp:tenant_id") ?? "00000000-0000-4000-8000-000000000001",
      "X-Pod-ID": sessionStorage.getItem("exp:pod_id") ?? "national-spine",
      "X-Request-ID": crypto.randomUUID(),
      "X-Correlation-ID": sessionStorage.getItem("exp:correlation_id") ?? crypto.randomUUID(),
    };
    const token = readAuthToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    return { url, headers, credentials: "include" };
  }
  return { url };
}

function fitZimbabweBounds(map: MapLibreMap, animate = true) {
  map.fitBounds(
    [
      [ZIMBABWE_BOUNDS.west, ZIMBABWE_BOUNDS.south],
      [ZIMBABWE_BOUNDS.east, ZIMBABWE_BOUNDS.north],
    ],
    { padding: 36, duration: animate ? 700 : 0 },
  );
}

function ensureRouteLayer(map: MapLibreMap) {
  if (!map.getSource("ndila-route")) {
    map.addSource("ndila-route", {
      type: "geojson",
      data: { type: "FeatureCollection", features: [] },
    });
    map.addLayer({
      id: "ndila-route-casing",
      type: "line",
      source: "ndila-route",
      paint: { "line-color": "#ffffff", "line-width": 7, "line-opacity": 0.95 },
    });
    map.addLayer({
      id: "ndila-route-line",
      type: "line",
      source: "ndila-route",
      paint: { "line-color": "#1d4ed8", "line-width": 4, "line-opacity": 0.92 },
    });
  }
}

function ensureZimbabweAdminLayers(map: MapLibreMap) {
  if (map.getSource("ndila-zw-admin")) return;

  map.addSource("ndila-zw-admin", { type: "geojson", data: ZIMBABWE_ADMIN_GEOJSON_URL });
  map.addSource("ndila-zw-places", { type: "geojson", data: ZIMBABWE_PLACES_GEOJSON_URL });

  map.addLayer({
    id: "ndila-zw-country-fill",
    type: "fill",
    source: "ndila-zw-admin",
    filter: ["==", ["get", "adminLevel"], "country"],
    paint: { "fill-color": "#e0edf8", "fill-opacity": 0.55 },
  });
  map.addLayer({
    id: "ndila-zw-province-fill",
    type: "fill",
    source: "ndila-zw-admin",
    filter: ["==", ["get", "adminLevel"], "province"],
    paint: { "fill-color": "#c7daf0", "fill-opacity": 0.45 },
  });
  map.addLayer({
    id: "ndila-zw-admin-outline",
    type: "line",
    source: "ndila-zw-admin",
    paint: { "line-color": "#475569", "line-width": 1.4, "line-opacity": 0.9 },
  });
  map.addLayer({
    id: "ndila-zw-province-labels",
    type: "symbol",
    source: "ndila-zw-admin",
    filter: ["==", ["get", "adminLevel"], "province"],
    layout: {
      "text-field": ["get", "name"],
      "text-size": 11,
      "text-font": ["Noto Sans Regular"],
    },
    paint: { "text-color": "#1e293b", "text-halo-color": "#f8fafc", "text-halo-width": 1.4 },
  });
  map.addLayer({
    id: "ndila-zw-place-labels",
    type: "symbol",
    source: "ndila-zw-places",
    layout: {
      "text-field": ["get", "name"],
      "text-size": ["match", ["get", "kind"], "city", 12, 10],
      "text-font": ["Noto Sans Bold"],
      "text-offset": [0, 0.8],
    },
    paint: { "text-color": "#0f172a", "text-halo-color": "#ffffff", "text-halo-width": 1.5 },
  });
  map.addLayer({
    id: "ndila-zw-place-dots",
    type: "circle",
    source: "ndila-zw-places",
    paint: {
      "circle-radius": ["match", ["get", "kind"], "city", 4, 3],
      "circle-color": "#334155",
      "circle-stroke-color": "#ffffff",
      "circle-stroke-width": 1.5,
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
      clusterRadius: 52,
    });
    map.addLayer({
      id: "ndila-clusters",
      type: "circle",
      source: "ndila-markers",
      filter: ["has", "point_count"],
      paint: {
        "circle-color": ["step", ["get", "point_count"], "#6366f1", 20, "#4f46e5", 100, "#4338ca"],
        "circle-radius": ["step", ["get", "point_count"], 18, 20, 24, 100, 30],
        "circle-stroke-width": 2,
        "circle-stroke-color": "#ffffff",
        "circle-opacity": 0.92,
      },
    });
    map.addLayer({
      id: "ndila-cluster-count",
      type: "symbol",
      source: "ndila-markers",
      filter: ["has", "point_count"],
      layout: {
        "text-field": ["get", "point_count_abbreviated"],
        "text-size": 12,
        "text-font": ["Noto Sans Bold"],
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
        "circle-radius": 7,
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

function registerClusterHandlers(map: MapLibreMap) {
  map.on("click", "ndila-clusters", (event) => {
    const features = map.queryRenderedFeatures(event.point, { layers: ["ndila-clusters"] });
    const cluster = features[0];
    if (!cluster?.geometry || cluster.geometry.type !== "Point") return;
    const coordinates = cluster.geometry.coordinates as [number, number];
    map.easeTo({ center: coordinates, zoom: map.getZoom() + 2, duration: 500 });
  });
  map.on("mouseenter", "ndila-clusters", () => {
    map.getCanvas().style.cursor = "pointer";
  });
  map.on("mouseleave", "ndila-clusters", () => {
    map.getCanvas().style.cursor = "";
  });
}

export function NdilaMapLibre({
  center,
  zoom = 12,
  markers = [],
  routeCoordinates = [],
  height = 320,
  tileConfig,
  fitToMarkers = false,
  showNavigation = false,
  showZimbabweAdmin = true,
  showMapChrome = true,
  clusterMarkers = false,
  flyToCenter = true,
  className,
  onMapClick,
}: NdilaMapLibreProps) {
  const shellRef = useRef<HTMLDivElement | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markerRefs = useRef<Marker[]>([]);
  const markersRef = useRef(markers);
  const routeRef = useRef(routeCoordinates);
  const fitRef = useRef(fitToMarkers);
  const clusterRef = useRef(clusterMarkers);
  const adminRef = useRef(showZimbabweAdmin);
  const handlersRegistered = useRef(false);
  const onMapClickRef = useRef(onMapClick);
  onMapClickRef.current = onMapClick;

  const [layerMode, setLayerMode] = useState<NdilaMapLayerMode>("admin");
  const [isFullscreen, setIsFullscreen] = useState(false);

  const streetsAvailable = streetsTilesAvailable(tileConfig);

  useEffect(() => {
    if (streetsAvailable && tileConfig?.provider === "OSM_OSRM") {
      setLayerMode("streets");
    }
  }, [streetsAvailable, tileConfig?.provider]);

  markersRef.current = markers;
  routeRef.current = routeCoordinates;
  fitRef.current = fitToMarkers;
  clusterRef.current = clusterMarkers;
  adminRef.current = showZimbabweAdmin;

  const style = useMemo(
    () => buildNdilaMapStyle(tileConfig, { includeRaster: layerMode === "streets" && streetsAvailable }),
    [tileConfig, layerMode, streetsAvailable],
  );

  const paintOverlay = useCallback(() => {
    const map = mapRef.current;
    if (!map || !map.isStyleLoaded()) return;

    if (adminRef.current) ensureZimbabweAdminLayers(map);
    ensureRouteLayer(map);

    markerRefs.current.forEach((marker) => marker.remove());
    markerRefs.current = [];

    if (clusterRef.current && markersRef.current.length > 12) {
      removeClusterLayers(map);
      ensureClusterLayers(map, markersRef.current);
      if (!handlersRegistered.current) {
        registerClusterHandlers(map);
        handlersRegistered.current = true;
      }
    } else {
      removeClusterLayers(map);
      for (const marker of markersRef.current) {
        const element = document.createElement("button");
        element.type = "button";
        element.className = "ndila-map-marker";
        element.title = marker.label ?? marker.id;
        element.style.width = "16px";
        element.style.height = "16px";
        element.style.borderRadius = "9999px";
        element.style.border = "2px solid #fff";
        element.style.background = markerColor(marker);
        element.style.boxShadow = "0 2px 6px rgba(15,23,42,0.35)";
        element.style.cursor = "pointer";

        const popup = marker.label
          ? new maplibregl.Popup({ offset: 14, closeButton: true, maxWidth: "240px" }).setHTML(
              `<div class="text-xs"><div class="font-semibold">${marker.label}</div>${
                marker.status ? `<div class="text-muted-foreground mt-0.5">${marker.status}</div>` : ""
              }</div>`,
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
      map.fitBounds(bounds as LngLatBoundsLike, { padding: 56, maxZoom: 14, duration: 600 });
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
      map.addControl(new maplibregl.NavigationControl({ showCompass: false }), "bottom-right");
    }
    if (tileConfig?.attribution) {
      map.addControl(new maplibregl.AttributionControl({ compact: true }), "bottom-left");
    }

    map.on("load", () => {
      if (showZimbabweAdmin && markers.length === 0 && !fitToMarkers) {
        fitZimbabweBounds(map, false);
      }
      paintOverlay();
    });
    // Pin-drop support: forward base-map clicks as coordinates. Ref-routed so the
    // consumer's latest handler wins without re-creating the map.
    map.on("click", (event) => {
      onMapClickRef.current?.({
        latitude: event.lngLat.lat,
        longitude: event.lngLat.lng,
      });
    });
    map.on("styledata", () => {
      if (map.isStyleLoaded()) paintOverlay();
    });

    return () => {
      markerRefs.current.forEach((marker) => marker.remove());
      markerRefs.current = [];
      handlersRegistered.current = false;
      map.remove();
      mapRef.current = null;
    };
  }, []);

  // MapLibre's own trackResize only listens to window resize. A shell sized by its
  // parent (height="100%" inside a flex column) also changes when sibling content
  // grows or a tab switches — without this the canvas keeps stale bounds and paints
  // into the wrong box. Observing the shell covers both cases.
  useEffect(() => {
    const shell = shellRef.current;
    if (!shell || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(() => mapRef.current?.resize());
    observer.observe(shell);
    return () => observer.disconnect();
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
    if (flyToCenter) {
      map.flyTo({ center: [center.longitude, center.latitude], zoom, duration: 700, essential: true });
    } else {
      map.setCenter([center.longitude, center.latitude]);
      if (!fitToMarkers) map.setZoom(zoom);
    }
  }, [center.latitude, center.longitude, zoom, fitToMarkers, flyToCenter]);

  useEffect(() => {
    paintOverlay();
  }, [markers, routeCoordinates, fitToMarkers, showZimbabweAdmin, clusterMarkers, paintOverlay]);

  const handleZoomIn = () => mapRef.current?.zoomIn({ duration: 250 });
  const handleZoomOut = () => mapRef.current?.zoomOut({ duration: 250 });
  const handleFitCountry = () => {
    if (mapRef.current) fitZimbabweBounds(mapRef.current);
  };
  const handleLocate = () => {
    if (!navigator.geolocation || !mapRef.current) return;
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        mapRef.current?.flyTo({
          center: [pos.coords.longitude, pos.coords.latitude],
          zoom: 14,
          duration: 800,
        });
      },
      () => undefined,
      { enableHighAccuracy: true, timeout: 8000 },
    );
  };
  const handleToggleFullscreen = useCallback(async () => {
    const shell = shellRef.current;
    if (!shell) return;
    if (!document.fullscreenElement) {
      await shell.requestFullscreen?.();
      setIsFullscreen(true);
    } else {
      await document.exitFullscreen?.();
      setIsFullscreen(false);
    }
  }, []);

  useEffect(() => {
    const onFullscreenChange = () => setIsFullscreen(Boolean(document.fullscreenElement));
    document.addEventListener("fullscreenchange", onFullscreenChange);
    return () => document.removeEventListener("fullscreenchange", onFullscreenChange);
  }, []);

  return (
    <div
      ref={shellRef}
      className={className ?? "relative w-full overflow-hidden rounded-lg bg-[#c7d9ea]"}
      style={{ height }}
      data-testid="ndila-maplibre-shell"
    >
      <div
        ref={containerRef}
        className="absolute inset-0"
        // Inline because maplibre-gl.css sets `.maplibregl-map { position: relative }`,
        // which beats the Tailwind `absolute` by cascade order — the container then
        // collapses to zero height, the canvas is sized from stale bounds and overflows
        // the shell, and pointer hit-testing on the map breaks (pin-drop clicks landed
        // on the shell). An inline style always wins.
        style={{ position: "absolute", inset: 0 }}
        data-testid="ndila-maplibre-canvas"
        aria-label="Interactive Ndila map"
      />
      {showMapChrome ? (
        <NdilaMapChrome
          layerMode={layerMode}
          streetsAvailable={streetsAvailable}
          isFullscreen={isFullscreen}
          onLayerModeChange={setLayerMode}
          onZoomIn={handleZoomIn}
          onZoomOut={handleZoomOut}
          onLocate={handleLocate}
          onFitCountry={handleFitCountry}
          onToggleFullscreen={handleToggleFullscreen}
        />
      ) : null}
    </div>
  );
}
