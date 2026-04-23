import { describe, expect, it } from "vitest";
import { resolveIndexHitHref } from "../search-hit-routing";

describe("resolveIndexHitHref", () => {
  it("uses contentJson href when present", () => {
    expect(
      resolveIndexHitHref({
        entityType: "unknown",
        entityId: "",
        contentJson: { href: "/custom/path" },
      }),
    ).toBe("/custom/path");
  });

  it("routes patient-like entities to EHR summary", () => {
    expect(
      resolveIndexHitHref({
        entityType: "patient",
        entityId: "P-123",
      }),
    ).toBe("/ehr/P-123/summary");
  });

  it("routes facilities to registry", () => {
    expect(
      resolveIndexHitHref({
        entityType: "facility",
        entityId: "F-1",
      }),
    ).toBe("/registry/facilities/F-1");
  });
});
