import { describe, expect, it } from "vitest";
import { mapRegistryWiringToUiMaturity, uiMaturityForService } from "@/lib/maturity";

describe("registry maturity mapping", () => {
  it("maps wired registry status to live UI label", () => {
    expect(mapRegistryWiringToUiMaturity("wired")).toBe("live");
  });

  it("maps unknown-or-partial to partial", () => {
    expect(mapRegistryWiringToUiMaturity("unknown-or-partial")).toBe("partial");
  });

  it("exposes learning-service as live from generated registry snapshot", () => {
    expect(uiMaturityForService("learning-service")).toBe("live");
  });
});
