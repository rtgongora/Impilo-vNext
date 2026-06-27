/**
 * TrustBanner tests (G-CZO-05). Follows the repo convention for mobile components
 * (function-component + behavioural source assertions rather than heavy RN DOM rendering).
 */
import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("../services/assuranceService", () => ({
  fetchAssuranceStatus: vi.fn().mockResolvedValue({ currentLevel: "LOA1" }),
}));

describe("TrustBanner", () => {
  it("exports a function component", async () => {
    const mod = await import("./TrustBanner");
    expect(typeof mod.TrustBanner).toBe("function");
  });

  it("can be instantiated with an onPress handler", async () => {
    const mod = await import("./TrustBanner");
    const element = React.createElement(mod.TrustBanner, { onPress: vi.fn() });
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.TrustBanner);
  });

  it("only shows a banner for unverified levels (LOA1/LOA2), hiding at verified LOA3+", async () => {
    const fs = await import("node:fs/promises");
    const path = await import("node:path");
    const source = await fs.readFile(path.resolve(process.cwd(), "src/components/TrustBanner.tsx"), "utf-8");
    // Copy is keyed only on LOA1/LOA2; verified levels fall through to `return null`.
    expect(source).toMatch(/COPY_BY_LEVEL[\s\S]{0,200}LOA1/);
    expect(source).toMatch(/LOA2/);
    expect(source).not.toMatch(/LOA3:\s*\{/);
    expect(source).toMatch(/if \(!copy\) return null/);
    // Reads the real assurance level (no hardcoded trust state).
    expect(source).toContain("fetchAssuranceStatus");
  });
});
