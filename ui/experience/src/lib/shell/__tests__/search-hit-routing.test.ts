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

  it("uses nested BFF routing href when present", () => {
    expect(
      resolveIndexHitHref({
        entityType: "Thing",
        entityId: "x",
        contentJson: { bff: { href: "/queue/waiting" } },
      }),
    ).toBe("/queue/waiting");
  });

  it("uses deep_link and experience route hints", () => {
    expect(
      resolveIndexHitHref({
        entityType: "x",
        entityId: "",
        contentJson: { deep_link: "/shell/task-manager" },
      }),
    ).toBe("/shell/task-manager");
    expect(
      resolveIndexHitHref({
        entityType: "x",
        entityId: "",
        contentJson: { experience: { path: "/home/notifications" } },
      }),
    ).toBe("/home/notifications");
  });

  it("routes patient + encounter ids from contentJson", () => {
    expect(
      resolveIndexHitHref({
        entityType: "lab_result",
        entityId: "LR-1",
        contentJson: { patientId: "P-9", encounterId: "E-2" },
      }),
    ).toBe("/ehr/P-9/encounter/E-2");
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

  it("uses resourceType hint for provider registry", () => {
    expect(
      resolveIndexHitHref({
        entityType: "indexed_entity",
        entityId: "PRV-88",
        contentJson: { resourceType: "Provider" },
      }),
    ).toBe("/registry/providers/PRV-88");
  });
});
