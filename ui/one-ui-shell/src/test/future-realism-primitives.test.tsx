import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Button, Card, GlassSurface, resolveTier } from "shared-ui";

/**
 * Future-Realism primitives (style guide step 3): glass surface + neon glow variants + the
 * progressive-enhancement tier resolver. Asserts the doctrine invariants — glass always carries a
 * solid fallback, and constrained devices/preferences resolve to the accessible `baseline` tier.
 */
describe("GlassSurface", () => {
  it("renders the glass recipe AND an automatic solid fallback (AA-legible baseline)", () => {
    render(<GlassSurface data-testid="glass">hi</GlassSurface>);
    const el = screen.getByTestId("glass");
    // frosted-glass enhancement
    expect(el.className).toContain("bg-glass-fill");
    expect(el.className).toContain("backdrop-blur-glass");
    expect(el.className).toContain("border-glass-border");
    // solid fallback engages without backdrop-filter support AND under a .low-blur ancestor
    expect(el.className).toContain("supports-[not(backdrop-filter:blur(0px))]:bg-glass-fallback");
    expect(el.className).toContain("[.low-blur_&]:bg-glass-fallback");
    expect(el.className).toContain("[.low-blur_&]:backdrop-blur-none");
  });

  it("applies a single focal glow when requested", () => {
    render(<GlassSurface data-testid="glass" glow="danger" blur="strong">x</GlassSurface>);
    const el = screen.getByTestId("glass");
    expect(el.className).toContain("shadow-glow-danger");
    expect(el.className).toContain("backdrop-blur-glass-strong");
  });
});

describe("Card glass variant + Button glow", () => {
  it("Card surface=glass uses the glass fallback recipe, not the solid card bg", () => {
    render(
      <Card surface="glass" glow="teal">
        <span>c</span>
      </Card>,
    );
    const card = screen.getByText("c").parentElement as HTMLElement;
    expect(card.className).toContain("bg-glass-fill");
    expect(card.className).toContain("[.low-blur_&]:bg-glass-fallback");
    expect(card.className).toContain("shadow-glow-teal");
    expect(card.className).not.toContain("bg-card");
  });

  it("Button glow adds the neon shadow without changing variant/behaviour", () => {
    render(
      <Button glow="gold" data-testid="btn">
        go
      </Button>,
    );
    const btn = screen.getByTestId("btn");
    expect(btn.className).toContain("shadow-glow-gold");
    expect(btn.className).toContain("bg-primary"); // default variant unchanged
  });
});

describe("resolveTier (progressive enhancement)", () => {
  const origMatchMedia = window.matchMedia;
  const nav = navigator as unknown as { connection?: unknown; deviceMemory?: number };
  const origConn = nav.connection;
  const origMem = nav.deviceMemory;

  function setup(opts: {
    reducedMotion?: boolean;
    reducedTransparency?: boolean;
    saveData?: boolean;
    effectiveType?: string;
    deviceMemory?: number;
  }) {
    window.matchMedia = ((q: string) => ({
      matches:
        (opts.reducedMotion && q.includes("reduced-motion")) ||
        (opts.reducedTransparency && q.includes("reduced-transparency")) ||
        false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })) as unknown as typeof window.matchMedia;
    nav.connection = { saveData: opts.saveData, effectiveType: opts.effectiveType };
    nav.deviceMemory = opts.deviceMemory;
  }

  afterEach(() => {
    window.matchMedia = origMatchMedia;
    nav.connection = origConn;
    nav.deviceMemory = origMem;
  });

  it("reduced-motion → baseline", () => {
    setup({ reducedMotion: true, deviceMemory: 8, effectiveType: "4g" });
    expect(resolveTier()).toBe("baseline");
  });

  it("reduced-transparency → baseline", () => {
    setup({ reducedTransparency: true, deviceMemory: 8 });
    expect(resolveTier()).toBe("baseline");
  });

  it("save-data / slow net / low memory → baseline", () => {
    expect((setup({ saveData: true }), resolveTier())).toBe("baseline");
    expect((setup({ effectiveType: "2g" }), resolveTier())).toBe("baseline");
    expect((setup({ deviceMemory: 2 }), resolveTier())).toBe("baseline");
  });

  it("healthy device + fast net → cinematic; mid → enhanced", () => {
    setup({ deviceMemory: 8, effectiveType: "4g" });
    expect(resolveTier()).toBe("cinematic");
    setup({ deviceMemory: 4, effectiveType: "3g" });
    expect(resolveTier()).toBe("enhanced");
  });
});
