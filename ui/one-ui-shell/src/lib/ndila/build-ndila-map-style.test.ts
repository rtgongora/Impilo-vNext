import { describe, expect, it } from "vitest";
import {
  buildNdilaMapStyle,
  isMockTileTemplate,
  isRasterTileTemplate,
  isUnsafePublicOsmTemplate,
  isVectorTileTemplate,
  streetsTilesAvailable,
} from "./build-ndila-map-style";

describe("buildNdilaMapStyle", () => {
  it("uses raster source for governed https templates", () => {
    const style = buildNdilaMapStyle({
      tileUrlTemplate: "https://tiles.example/{z}/{x}/{y}.png",
      maxZoom: 16,
    });
    expect(style.sources?.["ndila-raster"]).toBeDefined();
  });

  it("treats BFF-relative sovereign preview tiles as raster", () => {
    const style = buildNdilaMapStyle({
      tileUrlTemplate: "/internal/v1/ndila/tiles/{z}/{x}/{y}.png",
      provider: "PREVIEW_SOVEREIGN",
    });
    expect(style.sources?.["ndila-raster"]).toBeDefined();
  });

  it("treats OSM_OSRM BFF-relative tiles as raster", () => {
    const style = buildNdilaMapStyle({
      tileUrlTemplate: "/internal/v1/ndila/tiles/{z}/{x}/{y}.png",
      provider: "OSM_OSRM",
    });
    expect(style.sources?.["ndila-raster"]).toBeDefined();
  });

  it("uses blank canvas for mock templates", () => {
    expect(isMockTileTemplate("mock://tiles/{z}/{x}/{y}")).toBe(true);
    const style = buildNdilaMapStyle({ tileUrlTemplate: "mock://tiles/{z}/{x}/{y}" });
    expect(style.layers?.[0]?.type).toBe("background");
  });

  it("blocks public osm templates from raster mode", () => {
    const template = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
    expect(isUnsafePublicOsmTemplate(template)).toBe(true);
    expect(isRasterTileTemplate(template)).toBe(false);
  });

  it("prefers the MVT street source when a vector template is present", () => {
    const style = buildNdilaMapStyle({
      tileUrlTemplate: "/internal/v1/ndila/tiles/{z}/{x}/{y}.png",
      vectorTileUrlTemplate: "/internal/v1/ndila/tiles/{z}/{x}/{y}.mvt",
      provider: "OSM_OSRM",
    });
    const source = style.sources?.["ndila-streets"];
    expect(source).toBeDefined();
    expect((source as { type?: string }).type).toBe("vector");
    expect(style.sources?.["ndila-raster"]).toBeUndefined();
    expect(style.layers?.some((l) => l.id === "ndila-street-road-major")).toBe(true);
  });

  it("labels the vector street style via self-hosted glyphs", () => {
    const style = buildNdilaMapStyle({
      vectorTileUrlTemplate: "/internal/v1/public/gateway/map/tiles/{z}/{x}/{y}.mvt",
      provider: "OSM_OSRM",
    });
    // Symbol layers hard-require a style glyphs endpoint — the committed Noto Sans PBFs.
    expect(style.glyphs).toBe("http://localhost:3000/map/glyphs/{fontstack}/{range}.pbf");
    const symbolLayers = style.layers?.filter((l) => l.type === "symbol") ?? [];
    expect(symbolLayers.length).toBeGreaterThan(0);
    expect(symbolLayers.some((l) => l.id === "ndila-street-place-city")).toBe(true);
    expect(symbolLayers.some((l) => l.id === "ndila-street-road-name")).toBe(true);
    expect(symbolLayers.some((l) => l.id === "ndila-street-water-name")).toBe(true);
  });

  it("keeps self-hosted glyphs available for shared admin labels in fallback styles", () => {
    // Shared Zimbabwe admin overlays add symbol layers after style creation, so
    // every style branch must provide the sovereign glyph endpoint.
    const blank = buildNdilaMapStyle(null);
    expect(blank.glyphs).toBe("http://localhost:3000/map/glyphs/{fontstack}/{range}.pbf");
    expect(blank.layers?.some((l) => l.type === "symbol")).toBe(false);

    const raster = buildNdilaMapStyle({
      tileUrlTemplate: "/internal/v1/ndila/tiles/{z}/{x}/{y}.png",
      provider: "PREVIEW_SOVEREIGN",
    });
    expect(raster.glyphs).toBe("http://localhost:3000/map/glyphs/{fontstack}/{range}.pbf");
    expect(raster.layers?.some((l) => l.type === "symbol")).toBe(false);
  });

  it("falls back to raster when the vector template is mock or absent", () => {
    const style = buildNdilaMapStyle({
      tileUrlTemplate: "/internal/v1/ndila/tiles/{z}/{x}/{y}.png",
      vectorTileUrlTemplate: "mock://tiles/{z}/{x}/{y}",
      provider: "OSM_OSRM",
    });
    expect(isVectorTileTemplate("mock://tiles/{z}/{x}/{y}")).toBe(false);
    expect(style.sources?.["ndila-raster"]).toBeDefined();
  });

  it("counts vector-only configs as street-capable", () => {
    expect(
      streetsTilesAvailable({
        tileUrlTemplate: "mock://tiles/{z}/{x}/{y}",
        vectorTileUrlTemplate: "/internal/v1/ndila/tiles/{z}/{x}/{y}.mvt",
      }),
    ).toBe(true);
  });
});
