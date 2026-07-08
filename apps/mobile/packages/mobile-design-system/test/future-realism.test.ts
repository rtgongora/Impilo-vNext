import { describe, it, expect } from "vitest";
import { resolveGlassStyle } from "../src/components/glassStyle";
import { resolveMobileTier } from "../src/hooks/tier";
import { glass, neon } from "../src/tokens";

/**
 * Pure-logic coverage for the mobile Future-Realism primitives (the RN wrappers aren't rendered in
 * this env — Expo can't build/run here). Asserts the doctrine invariants: baseline = solid fallback,
 * and any a11y/device constraint collapses to the accessible baseline tier.
 */
describe("resolveGlassStyle", () => {
  it("baseline tier paints a SOLID fallback (not the translucent fill)", () => {
    expect(resolveGlassStyle("dark", "none", "baseline").backgroundColor).toBe(glass.fallbackDark);
    expect(resolveGlassStyle("light", "none", "baseline").backgroundColor).toBe(glass.fallbackLight);
  });

  it("enhanced/cinematic tiers use the translucent glass fill", () => {
    expect(resolveGlassStyle("dark", "none", "enhanced").backgroundColor).toBe(glass.fillDark);
    expect(resolveGlassStyle("light", "none", "cinematic").backgroundColor).toBe(glass.fillLight);
  });

  it("carries the light-catch border on every tier", () => {
    for (const tier of ["baseline", "enhanced", "cinematic"] as const) {
      const s = resolveGlassStyle("dark", "none", tier);
      expect(s.borderWidth).toBe(1);
      expect(s.borderColor).toBe(glass.border);
    }
  });

  it("applies a neon glow only above baseline; suppressed at baseline", () => {
    const enhanced = resolveGlassStyle("dark", "teal", "enhanced");
    expect(enhanced.shadowColor).toBe(neon.teal);
    expect(enhanced.shadowOpacity).toBeGreaterThan(0.3);

    const baseline = resolveGlassStyle("dark", "teal", "baseline");
    expect(baseline.shadowColor).not.toBe(neon.teal); // no glow when we've dropped to the solid baseline
  });

  it("danger glow is a distinct reserved colour", () => {
    expect(resolveGlassStyle("dark", "danger", "enhanced").shadowColor).toBe("#EF3340");
  });
});

describe("resolveMobileTier", () => {
  it("reduce-motion / reduce-transparency / low device → baseline", () => {
    expect(resolveMobileTier({ reduceMotion: true, reduceTransparency: false })).toBe("baseline");
    expect(resolveMobileTier({ reduceMotion: false, reduceTransparency: true })).toBe("baseline");
    expect(resolveMobileTier({ reduceMotion: false, reduceTransparency: false, lowTier: true })).toBe("baseline");
  });

  it("no constraints → enhanced", () => {
    expect(resolveMobileTier({ reduceMotion: false, reduceTransparency: false })).toBe("enhanced");
    expect(resolveMobileTier({ reduceMotion: false, reduceTransparency: false, lowTier: false })).toBe("enhanced");
  });
});
