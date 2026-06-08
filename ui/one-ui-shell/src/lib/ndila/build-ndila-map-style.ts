import type { StyleSpecification } from "maplibre-gl";

export interface NdilaTileConfigInput {
  provider?: string;
  tileUrlTemplate?: string;
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

/**
 * Build a MapLibre style from Ndila tile policy. Mock/unsafe templates use a
 * sovereign-safe blank canvas — markers and routes still pan/zoom interactively.
 */
export function buildNdilaMapStyle(tileConfig?: NdilaTileConfigInput | null): StyleSpecification {
  const template = tileConfig?.tileUrlTemplate;
  if (isRasterTileTemplate(template)) {
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
    layers: [{ id: "ndila-background", type: "background", paint: { "background-color": "#e8eef4" } }],
  };
}
