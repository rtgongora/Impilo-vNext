import { describe, expect, it } from "vitest";
import {
  glossRamp,
  glossFloor,
  glossButtonStops,
  rampColorAt,
  rampBands,
  lighten,
  mix,
} from "../gloss";

/**
 * The gloss ramp's whole contract is that it is anchored in **dp**, not fractions — the
 * web pass had to correct exactly this defect, where a percentage ramp slid its hand-over
 * zone through the text whenever content grew. These tests pin that behaviour so nobody
 * "simplifies" the offsets into 0..1 later.
 */
describe("gloss ramp", () => {
  it("is anchored in dp, not fractions", () => {
    // A fraction-based ramp would have every offset in 0..1. Ours must not.
    expect(glossRamp.every((s) => s.offset >= 0)).toBe(true);
    expect(glossRamp.some((s) => s.offset > 1)).toBe(true);
    expect(glossRamp[0].offset).toBe(0);
  });

  it("runs light at the top and dark at the floor", () => {
    const first = glossRamp[0].color.toLowerCase();
    const last = glossRamp[glossRamp.length - 1].color.toLowerCase();
    expect(first).toBe("#f7fcfa");
    expect(last).toBe("#03222a");
    expect(glossFloor.toLowerCase()).toBe(last);
  });

  it("has strictly increasing offsets so the gradient never doubles back", () => {
    for (let i = 1; i < glossRamp.length; i += 1) {
      expect(glossRamp[i].offset).toBeGreaterThan(glossRamp[i - 1].offset);
    }
  });
});

describe("rampColorAt", () => {
  it("returns the top colour at and above the surface top", () => {
    expect(rampColorAt(0).toLowerCase()).toBe("#f7fcfa");
    expect(rampColorAt(-50).toLowerCase()).toBe("#f7fcfa");
  });

  it("extends the floor below the last stop rather than wrapping", () => {
    // This is the dp-anchoring guarantee: growth extends the floor.
    expect(rampColorAt(620).toLowerCase()).toBe("#03222a");
    expect(rampColorAt(5000).toLowerCase()).toBe("#03222a");
  });

  it("darkens monotonically down the ramp", () => {
    const luminance = (hex: string) => {
      const n = parseInt(hex.slice(1), 16);
      return ((n >> 16) & 0xff) + ((n >> 8) & 0xff) + (n & 0xff);
    };
    const samples = [0, 100, 200, 300, 400, 500, 620].map((dp) => luminance(rampColorAt(dp)));
    for (let i = 1; i < samples.length; i += 1) {
      expect(samples[i]).toBeLessThan(samples[i - 1]);
    }
  });

  it("survives a nonsense offset without throwing", () => {
    expect(rampColorAt(Number.NaN).toLowerCase()).toBe("#f7fcfa");
  });
});

/**
 * Structure over hue: each app keeps its own accent, and only the *shape* of the
 * treatment (lighter-to-base + inner highlight) is shared. A gradient that hardcoded
 * emerald would repaint the provider app green, which is precisely what the mobile
 * lane's earlier finding-3 fix ("provider is generic Tailwind blue") set out to stop.
 */
describe("glossButtonStops", () => {
  it("keeps the citizen accent green", () => {
    const { from, to } = glossButtonStops("#009739");
    expect(to).toBe("#009739");
    expect(from).not.toBe(to);
    expect(from.toLowerCase()).toMatch(/^#[0-9a-f]{6}$/);
  });

  it("keeps the provider accent teal — it is not recoloured to citizen green", () => {
    const citizen = glossButtonStops("#009739");
    const provider = glossButtonStops("#0F766E");
    expect(provider.to).toBe("#0F766E");
    expect(provider.from).not.toBe(citizen.from);
  });

  it("derives a lighter top stop, so the gradient reads as lit from above", () => {
    const lum = (hex: string) => {
      const n = parseInt(hex.slice(1), 16);
      return ((n >> 16) & 0xff) + ((n >> 8) & 0xff) + (n & 0xff);
    };
    const { from, to } = glossButtonStops("#0F766E");
    expect(lum(from)).toBeGreaterThan(lum(to));
  });

  it("passes a non-hex accent through untouched rather than emitting garbage", () => {
    const { from, to } = glossButtonStops("rebeccapurple");
    expect(to).toBe("rebeccapurple");
    expect(from).toBe("rebeccapurple");
  });
});

describe("colour helpers", () => {
  it("lighten moves toward white and clamps", () => {
    expect(lighten("#000000", 1)).toBe("#ffffff");
    expect(lighten("#000000", 0)).toBe("#000000");
    expect(lighten("#000000", 5)).toBe("#ffffff");
  });

  it("mix interpolates between endpoints and clamps t", () => {
    expect(mix("#000000", "#ffffff", 0)).toBe("#000000");
    expect(mix("#000000", "#ffffff", 1)).toBe("#ffffff");
    expect(mix("#000000", "#ffffff", 0.5)).toBe("#808080");
    expect(mix("#000000", "#ffffff", -3)).toBe("#000000");
  });
});

/**
 * `rampBands` is what actually paints the canvas — RN has no gradient primitive and no
 * gradient library is usable in BOTH apps (react-native-svg is a citizen-app dependency
 * only, and this package is shared with provider-app). These guard the properties that
 * make a stack of flat views read as a ramp instead of as stripes.
 */
describe("rampBands", () => {
  it("covers the full height with no gaps", () => {
    const bands = rampBands(620, 48);
    expect(bands).toHaveLength(48);
    expect(bands[0].top).toBe(0);
    const last = bands[bands.length - 1];
    expect(last.top + last.height).toBeGreaterThanOrEqual(620);
  });

  it("overlaps adjacent bands so sub-pixel rounding cannot leave seams", () => {
    const bands = rampBands(620, 48);
    for (let i = 1; i < bands.length; i += 1) {
      const prevEnd = bands[i - 1].top + bands[i - 1].height;
      expect(prevEnd).toBeGreaterThan(bands[i].top);
    }
  });

  it("samples each band at its own midpoint, so no band is a colour the ramp never passes through", () => {
    const bands = rampBands(620, 4);
    bands.forEach((band) => {
      expect(band.color).toBe(rampColorAt(band.top + (620 / 4) / 2));
    });
  });

  it("darkens down the stack", () => {
    const lum = (hex: string) => {
      const n = parseInt(hex.slice(1), 16);
      return ((n >> 16) & 0xff) + ((n >> 8) & 0xff) + (n & 0xff);
    };
    const bands = rampBands(620, 12);
    for (let i = 1; i < bands.length; i += 1) {
      expect(lum(bands[i].color)).toBeLessThanOrEqual(lum(bands[i - 1].color));
    }
  });

  it("returns nothing rather than throwing on nonsense input", () => {
    expect(rampBands(0, 10)).toEqual([]);
    expect(rampBands(620, 0)).toEqual([]);
    expect(rampBands(-5, 10)).toEqual([]);
    expect(rampBands(Number.NaN, 10)).toEqual([]);
  });
});
