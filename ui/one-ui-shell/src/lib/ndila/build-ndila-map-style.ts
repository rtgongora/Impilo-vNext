import type { ExpressionSpecification, LayerSpecification, StyleSpecification } from "maplibre-gl";

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

// OpenMapTiles-schema street layers (tilemaker/planetiler output). Labels are
// rendered from self-hosted Noto Sans glyph PBFs committed under
// ui/one-ui-shell/public/map/glyphs (fetched by scripts/ndila/fetch-map-glyphs.sh
// — Latin ranges only; no CDN at runtime). The glyphs endpoint is declared ONLY
// on the vector street style: the admin-boundary fallback style must keep
// working without it.
const STREETS_SOURCE = "ndila-streets";

/** Self-hosted glyph endpoint (shell public assets — sovereign, no CDN). */
export const NDILA_GLYPHS_URL = "/map/glyphs/{fontstack}/{range}.pbf";

/**
 * MapLibre does not resolve root-relative tile/glyph templates against the page
 * origin (they silently never fetch) — absolutize them in the browser. SSR keeps
 * the relative form; the style is only consumed client-side.
 */
function absolutize(url: string): string {
  if (typeof window !== "undefined" && url.startsWith("/")) {
    return window.location.origin + url;
  }
  return url;
}

/** OpenMapTiles carries transliterated names; prefer Latin, fall back to local. */
const NAME_FIELD: ExpressionSpecification = ["coalesce", ["get", "name:latin"], ["get", "name"]];

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
 * Label layers over the street layers. These require the style-level `glyphs`
 * endpoint (self-hosted Noto Sans PBFs) — only the vector street style declares
 * it, so these layers are only ever added there.
 */
function openMapTilesLabelLayers(): LayerSpecification[] {
  return [
    {
      id: "ndila-street-water-name",
      type: "symbol",
      source: STREETS_SOURCE,
      "source-layer": "water_name",
      layout: {
        "text-field": NAME_FIELD,
        "text-font": ["Noto Sans Regular"],
        "text-size": 11,
      },
      paint: { "text-color": "#3b6d99", "text-halo-color": "#eaf2f9", "text-halo-width": 1.2 },
    },
    {
      id: "ndila-street-road-name",
      type: "symbol",
      source: STREETS_SOURCE,
      "source-layer": "transportation_name",
      minzoom: 11,
      layout: {
        "symbol-placement": "line",
        "text-field": NAME_FIELD,
        "text-font": ["Noto Sans Regular"],
        "text-size": ["interpolate", ["linear"], ["zoom"], 11, 9, 16, 12],
      },
      paint: { "text-color": "#5d5347", "text-halo-color": "#ffffff", "text-halo-width": 1.3 },
    },
    {
      id: "ndila-street-place-minor",
      type: "symbol",
      source: STREETS_SOURCE,
      "source-layer": "place",
      filter: ["match", ["get", "class"], ["town", "village"], true, false],
      minzoom: 8,
      layout: {
        "text-field": NAME_FIELD,
        "text-font": ["Noto Sans Regular"],
        "text-size": ["match", ["get", "class"], "town", 12, 10.5],
      },
      paint: { "text-color": "#1e293b", "text-halo-color": "#f8fafc", "text-halo-width": 1.4 },
    },
    {
      id: "ndila-street-place-city",
      type: "symbol",
      source: STREETS_SOURCE,
      "source-layer": "place",
      filter: ["==", ["get", "class"], "city"],
      layout: {
        "text-field": NAME_FIELD,
        "text-font": ["Noto Sans Bold"],
        "text-size": ["interpolate", ["linear"], ["zoom"], 4, 11, 10, 16],
      },
      paint: { "text-color": "#0f172a", "text-halo-color": "#ffffff", "text-halo-width": 1.6 },
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
      glyphs: absolutize(NDILA_GLYPHS_URL),
      sources: {
        [STREETS_SOURCE]: {
          type: "vector",
          tiles: [absolutize(vectorTemplate!)],
          // tilemaker/OpenMapTiles pyramids top out at z14; MapLibre overzooms beyond.
          maxzoom: 14,
          attribution: tileConfig?.attribution,
        },
      },
      layers: [...openMapTilesStreetLayers(), ...openMapTilesLabelLayers()],
    };
  }

  const template = tileConfig?.tileUrlTemplate;
  if (includeRaster && isRasterTileTemplate(template)) {
    return {
      version: 8,
      // Glyphs are declared on every branch: the shared Zimbabwe admin overlay
      // adds symbol (label) layers in every mode, and a style without "glyphs"
      // hard-errors on addLayer — the live blank-map failure of 2026-07-23.
      glyphs: absolutize(NDILA_GLYPHS_URL),
      sources: {
        "ndila-raster": {
          type: "raster",
          tiles: [absolutize(template!)],
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
    glyphs: absolutize(NDILA_GLYPHS_URL),
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
