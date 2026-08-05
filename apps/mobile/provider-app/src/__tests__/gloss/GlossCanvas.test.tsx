import { describe, expect, it } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { Text } from "react-native";
import { GlossCanvas, rampBands, rampColorAt } from "@impilo/mobile-design-system";

/**
 * The gloss ramp has no gradient primitive behind it. React Native has none, and no
 * gradient library is usable across BOTH apps — `react-native-svg` is a citizen-app
 * dependency only, and the design system is shared with this app — so the ramp is a stack
 * of flat views. That makes "did it lay down the bands at all?" a real question.
 *
 * Scope of this proof, stated so nobody reads more into a green run than is there:
 *   - PROVEN here: the component renders, its children survive, the floor exists, and it
 *     emits exactly one band per requested step.
 *   - PROVEN in `mobile-design-system/src/tokens/__tests__/gloss.test.ts`: every colour
 *     value, the dp anchoring, and the mid-ramp start.
 *   - NOT proven anywhere yet: how any of it looks on a device. There is no Redroid
 *     runtime proof in this window. Colours are asserted through the pure resolver
 *     rather than by reading inline styles, because this app's `react-native` test double
 *     does not implement `StyleSheet.absoluteFill` — asserting on those styles would be
 *     testing the double, not the component.
 */
function renderToDom(element: React.ReactElement) {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(element);
  });
  return container;
}

describe("GlossCanvas", () => {
  it("renders its children", () => {
    const c = renderToDom(
      <GlossCanvas>
        <Text>Impilo Provider</Text>
      </GlossCanvas>,
    );
    expect(c.textContent).toContain("Impilo Provider");
  });

  it("paints a floor, so content is never on nothing if a band fails to lay out", () => {
    const c = renderToDom(<GlossCanvas />);
    expect(c.querySelector('[data-testid="gloss-canvas-floor"]')).not.toBeNull();
  });

  it("emits exactly one band per requested step", () => {
    const c = renderToDom(<GlossCanvas bands={12} />);
    const ramp = c.querySelector('[data-testid="gloss-canvas-ramp"]');
    expect(ramp).not.toBeNull();
    expect(ramp?.children.length).toBe(12);
  });

  it("uses a distinct testID per canvas so two on one screen stay addressable", () => {
    const c = renderToDom(<GlossCanvas testID="login-hero-canvas" bands={4} />);
    expect(c.querySelector('[data-testid="login-hero-canvas-ramp"]')?.children.length).toBe(4);
    expect(c.querySelector('[data-testid="login-hero-canvas-floor"]')).not.toBeNull();
  });
});

/**
 * The mid-ramp start is the guard against reproducing the Wave-1 defect the PO rejected on
 * web: a light band that is decorative rather than load-bearing. A surface leading with
 * white ink must start dark.
 */
describe("mid-ramp start (the provider login hero)", () => {
  const lum = (hex: string) => {
    const n = parseInt(hex.slice(1), 16);
    return ((n >> 16) & 0xff) + ((n >> 8) & 0xff) + (n & 0xff);
  };

  it("gives the provider hero a dark ground throughout, so white ink stays legible", () => {
    // Exactly the values LoginScreen passes.
    const bands = rampBands(420, 48, 230);
    expect(bands.length).toBeGreaterThan(0);
    bands.forEach((band) => {
      // Every band darker than the mid-ramp teal — no near-white anywhere in this hero.
      expect(lum(band.color)).toBeLessThan(lum("#8FCDBD"));
    });
  });

  it("a brand surface, by contrast, does start on the light ground the mark needs", () => {
    const bands = rampBands(620, 48, 0);
    expect(lum(bands[0].color)).toBeGreaterThan(lum("#8FCDBD"));
    expect(bands[0].color).toBe(rampColorAt(620 / 48 / 2));
  });
});
