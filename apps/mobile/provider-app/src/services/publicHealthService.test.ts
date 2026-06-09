import { describe, expect, it } from "vitest";
import { nextFieldTaskStatus } from "./publicHealthService";

describe("publicHealthService", () => {
  it("advances field task lifecycle", () => {
    expect(nextFieldTaskStatus("PLANNED")).toBe("ASSIGNED");
    expect(nextFieldTaskStatus("IN_PROGRESS")).toBe("COMPLETED");
  });

  it("exports createFieldTask for outreach parity", async () => {
    const mod = await import("./publicHealthService");
    expect(typeof mod.createFieldTask).toBe("function");
    expect(typeof mod.fetchSiteRegistrySites).toBe("function");
    expect(typeof mod.fetchMapMarkers).toBe("function");
    expect(typeof mod.fetchInvestigations).toBe("function");
    expect(typeof mod.submitFieldOperation).toBe("function");
  });
});
