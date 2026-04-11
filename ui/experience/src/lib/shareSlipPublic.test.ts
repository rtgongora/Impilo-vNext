import { describe, expect, it } from "vitest";
import { normalizeShareSlipBaseUrl } from "./shareSlipPublic";

describe("normalizeShareSlipBaseUrl", () => {
  it("returns empty for nullish or blank", () => {
    expect(normalizeShareSlipBaseUrl(undefined)).toBe("");
    expect(normalizeShareSlipBaseUrl("")).toBe("");
    expect(normalizeShareSlipBaseUrl("   ")).toBe("");
  });

  it("trims and strips trailing slash", () => {
    expect(normalizeShareSlipBaseUrl("https://share.test/")).toBe("https://share.test");
    expect(normalizeShareSlipBaseUrl("  http://localhost:9123  ")).toBe("http://localhost:9123");
  });
});
