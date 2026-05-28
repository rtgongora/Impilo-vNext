import { describe, expect, it } from "vitest";
import { filterCommandCentreSections, COMMAND_CENTRE_SECTIONS } from "@/features/production-command-centre/tile-registry";

describe("production command centre tile registry", () => {
  it("includes backbone and clinical sections", () => {
    const ids = COMMAND_CENTRE_SECTIONS.map((s) => s.id);
    expect(ids).toContain("backbone");
    expect(ids).toContain("clinical");
    expect(ids).toContain("data-intelligence");
  });

  it("filters by P0 priority", () => {
    const filtered = filterCommandCentreSections("", "P0");
    expect(filtered.length).toBeGreaterThan(0);
    for (const section of filtered) {
      for (const tile of section.tiles) {
        expect(tile.priority).toBe("P0");
      }
    }
  });

  it("filters by search query", () => {
    const filtered = filterCommandCentreSections("fundo", undefined);
    const titles = filtered.flatMap((s) => s.tiles.map((t) => t.title.toLowerCase()));
    expect(titles.some((t) => t.includes("fundo"))).toBe(true);
  });

  it("labels Fundo as learning not lending", () => {
    const fundo = COMMAND_CENTRE_SECTIONS.flatMap((s) => s.tiles).find((t) => t.id === "fundo");
    expect(fundo?.description.toLowerCase()).toContain("learning");
    expect(fundo?.description.toLowerCase()).not.toMatch(/\blending\b/);
    expect(fundo?.title.toLowerCase()).not.toMatch(/\blending\b/);
  });
});
