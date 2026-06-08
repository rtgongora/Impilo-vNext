import { describe, expect, it } from "vitest";
import {
  buildNdilaMapStyle,
  isMockTileTemplate,
  isRasterTileTemplate,
  isUnsafePublicOsmTemplate,
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
});
