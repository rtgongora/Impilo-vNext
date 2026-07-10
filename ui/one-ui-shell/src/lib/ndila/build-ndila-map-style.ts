import type { LayerSpecification, StyleSpecification } from "maplibre-gl";

export interface NdilaTileConfigInput {
  provider?: string;
  tileUrlTemplate?: string;
  /** BFF-relative MVT template when the self-hosted street stack (Martin) is live. */
  vectorTileUrlTemplate?: string;
  maxZoom?: number;
  attribution?: string;
}

export function isMockTileTemplate(template?: string): boolean {
  return !template || template.startsWith("mock://");
}

export function isUnsafePublicOsmTemplate(template?: string): boolean {
  return !!template && /tile\.openstreetmap\.org/i.test(template);
}

export function isRasterTileTemplate(template?: string): boolean {
  if (!template || isMockTileTemplate(template) || isUnsafePublicOsmTemplate(template)) return false;
  return template.startsWith("http://") || template.startsWith("https://") || template.startsWith("/");
}

export function isVectorTileTemplate(template?: string): boolean {
  // Same sovereignty rules as raster: governed https or BFF-relative only.
  return isRasterTileTemplate(template);
}

// OpenMapTiles-schema street layers (tilemaker output). Deliberately label-free:
// text layers need a glyphs endpoint the street stack does not serve yet, and
// MapLibre hard-fails styles that use text-field without glyphs.
const STREETS_SOURCE = "ndila-streets";

function openMapTilesStreetLayers(): LayerSpecification[] {
  return [
    { id: "ndila-street-bg", type: "background", paint: { "background-color": "#f4f1ec" } },
    {
      id: "ndila-street-landcover",
      type: "fill",
      source: STREETS_SOURCE,
      "source-layer": "landcover",
      paint: {
        "fill-color": [
          "match",
          ["get", "class"],
          "wood", "#cfe0c4",
          "grass", "#ddead2",
          "farmland", "#e9e6d8",
          "sand", "#f0e7d0",
          "#e8e4da",
        ],
        "fill-opacity": 0.7,
      },
    },
    {
      id: "ndila-street-landuse",
      type: "fill",
      source: STREETS_SOURCE,
      "source-layer": "landuse",
      paint: {
        "fill-color": [
          "match",
          ["get", "class"],
          "residential", "#eae6e0",
          "hospital", "#f6e5e2",
          "school", "#efe9dc",
          "industrial", "#e5e2e8",
          "#ece9e2",
        ],
        "fill-opacity": 0.6,
      },
    },
    {
      id: "ndila-street-park",
      type: "fill",
      source: STREETS_SOURCE,
      "source-layer": "park",
      paint: { "fill-color": "#cfe4c2", "fill-opacity": 0.65 },
    },
    {
      id: "ndila-street-water",
      type: "fill",
      source: STREETS_SOURCE,
      "source-layer": "water",
      paint: { "fill-color": "#a8c8e4" },
    },
    {
      id: "ndila-street-waterway",
      type: "line",
      source: STREETS_SOURCE,
      "source-layer": "waterway",
      paint: { "line-color": "#a8c8e4", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 0.5, 14, 2] },
    },
    {
      id: "ndila-street-aeroway",
      type: "line",
      source: STREETS_SOURCE,
      "source-layer": "aeroway",
      paint: { "line-color": "#d8d4ce", "line-width": ["interpolate", ["linear"], ["zoom"], 10, 1, 15, 8] },
    },
    {
      id: "ndila-street-road-casing",
      type: "line",
      source: STREETS_SOURCE,
      "source-layer": "transportation",
      filter: ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary"], true, false],
      layout: { "line-cap": "round", "line-join": "round" },
      paint: {
        "line-color": "#d9d2c5",
        "line-width": ["interpolate", ["exponential", 1.4], ["zoom"], 6, 1.6, 12, 5, 16, 14],
      },
    },
    {
      id: "ndila-street-road-minor",
      type: "line",
      source: STREETS_SOURCE,
      "source-layer": "transportation",
      filter: [
        "match",
        ["get", "class"],
        ["minor", "tertiary", "service", "track", "path", "residential", "unclassified"],
        true,
        false,
      ],
      layout: { "line-cap": "round", "line-join": "round" },
      paint: {
        "line-color": "#ffffff",
        "line-width": ["interpolate", ["exponential", 1.4], ["zoom"], 11, 0.6, 14, 2, 16, 6],
      },
    },
    {
      id: "ndila-street-road-major",
      type: "line",
      source: STREETS_SOURCE,
      "source-layer": "transportation",
      filter: ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary"], true, false],
      layout: { "line-cap": "round", "line-join": "round" },
      paint: {
        "line-color": [
          "match",
          ["get", "class"],
          "motorway", "#f7c98b",
          "trunk", "#f9d9a0",
          "primary", "#fceebd",
          "#ffffff",
        ],
        "line-width": ["interpolate", ["exponential", 1.4], ["zoom"], 6, 1, 12, 3.5, 16, 10],
      },
    },
    {
      id: "ndila-street-rail",
      type: "line",
      source: STREETS_SOURCE,
      "source-layer": "transportation",
      filter: ["==", ["get", "class"], "rail"],
      paint: { "line-color": "#b8b2a8", "line-width": 1.2, "line-dasharray": [3, 3] },
    },
    {
      id: "ndila-street-building",
      type: "fill",
      source: STREETS_SOURCE,
      "source-layer": "building",
      minzoom: 13,
      paint: { "fill-color": "#ddd6ca", "fill-opacity": 0.75, "fill-outline-color": "#cfc7b8" },
    },
    {
      id: "ndila-street-boundary",
      type: "line",
      source: STREETS_SOURCE,
      "source-layer": "boundary",
      filter: ["<=", ["get", "admin_level"], 4],
      paint: { "line-color": "#9a8fa8", "line-width": 1, "line-dasharray": [2, 2] },
    },
  ];
}

/**
 * Build a MapLibre style from Ndila tile policy. Preference order:
 * 1. Self-hosted MVT street tiles (Martin) rendered natively as vectors.
 * 2. Governed raster pyramids (sovereign preview PNGs or https rasters).
 * 3. Sovereign-safe blank canvas — markers and routes still pan/zoom.
 */
export function buildNdilaMapStyle(
  tileConfig?: NdilaTileConfigInput | null,
  opts?: { includeRaster?: boolean },
): StyleSpecification {
  const includeRaster = opts?.includeRaster !== false;
  const vectorTemplate = tileConfig?.vectorTileUrlTemplate;
  if (includeRaster && isVectorTileTemplate(vectorTemplate)) {
    return {
      version: 8,
      sources: {
        [STREETS_SOURCE]: {
          type: "vector",
          tiles: [vectorTemplate!],
          // tilemaker/OpenMapTiles pyramids top out at z14; MapLibre overzooms beyond.
          maxzoom: 14,
          attribution: tileConfig?.attribution,
        },
      },
      layers: openMapTilesStreetLayers(),
    };
  }

  const template = tileConfig?.tileUrlTemplate;
  if (includeRaster && isRasterTileTemplate(template)) {
    return {
      version: 8,
      sources: {
        "ndila-raster": {
          type: "raster",
          tiles: [template!],
          tileSize: 256,
          maxzoom: tileConfig?.maxZoom ?? 18,
          attribution: tileConfig?.attribution,
        },
      },
      layers: [{ id: "ndila-raster-layer", type: "raster", source: "ndila-raster" }],
    };
  }

  return {
    version: 8,
    sources: {},
    layers: [{ id: "ndila-background", type: "background", paint: { "background-color": "#c7d9ea" } }],
  };
}

export function streetsTilesAvailable(tileConfig?: NdilaTileConfigInput | null): boolean {
  return (
    isVectorTileTemplate(tileConfig?.vectorTileUrlTemplate) ||
    isRasterTileTemplate(tileConfig?.tileUrlTemplate)
  );
}
