import { describe, expect, it } from "vitest";
import { deriveAvailableAppModes } from "./modeAvailability";
import type { ResolvedWorkContextView } from "@impilo/mobile-trust";

function ctx(overrides: Partial<ResolvedWorkContextView>): ResolvedWorkContextView {
  return {
    contextId: "ctx-1",
    contextKind: "facility",
    sourceSystem: "VASHANDI",
    availableModes: [],
    restrictions: [],
    label: "Context",
    groupHint: "regular",
    ...overrides,
  };
}

describe("deriveAvailableAppModes", () => {
  it("returns exactly the role-based modes when there are no resolved contexts", () => {
    expect(deriveAvailableAppModes(["outreach"], null)).toEqual(["outreach"]);
    expect(deriveAvailableAppModes(["outreach"], [])).toEqual(["outreach"]);
  });

  it("adds provider when a resolved context grants CLINICAL_CARE, without removing existing modes", () => {
    const result = deriveAvailableAppModes(["outreach"], [ctx({ availableModes: ["CLINICAL_CARE"] })]);
    expect(result).toContain("outreach");
    expect(result).toContain("provider");
  });

  it("adds provider when a resolved context grants VIRTUAL_CARE", () => {
    const result = deriveAvailableAppModes([], [ctx({ availableModes: ["VIRTUAL_CARE"] })]);
    expect(result).toContain("provider");
  });

  it("adds supervisor when a resolved context grants FACILITY_MANAGEMENT", () => {
    const result = deriveAvailableAppModes([], [ctx({ availableModes: ["FACILITY_MANAGEMENT"] })]);
    expect(result).toContain("supervisor");
  });

  it("adds supervisor for DEPARTMENT_MANAGEMENT, JURISDICTION_OPERATIONS, and PROGRAMME_MANAGEMENT alike", () => {
    expect(deriveAvailableAppModes([], [ctx({ availableModes: ["DEPARTMENT_MANAGEMENT"] })])).toContain("supervisor");
    expect(deriveAvailableAppModes([], [ctx({ availableModes: ["JURISDICTION_OPERATIONS"] })])).toContain("supervisor");
    expect(deriveAvailableAppModes([], [ctx({ availableModes: ["PROGRAMME_MANAGEMENT"] })])).toContain("supervisor");
  });

  it("does NOT add provider or supervisor for modes with no AppMode analogue (e.g. REGULATORY_OPERATIONS)", () => {
    const result = deriveAvailableAppModes([], [ctx({ availableModes: ["REGULATORY_OPERATIONS"] })]);
    expect(result).not.toContain("provider");
    expect(result).not.toContain("supervisor");
    expect(result).toEqual([]);
  });

  it("never removes a role-granted mode even when resolved contexts don't confirm it", () => {
    const result = deriveAvailableAppModes(["courier", "offline"], [ctx({ availableModes: ["CLINICAL_CARE"] })]);
    expect(result).toEqual(expect.arrayContaining(["courier", "offline", "provider"]));
  });

  it("does not duplicate a mode already present from the role-based list", () => {
    const result = deriveAvailableAppModes(["provider"], [ctx({ availableModes: ["CLINICAL_CARE"] })]);
    expect(result.filter((m) => m === "provider")).toHaveLength(1);
  });

  it("adds outreach when a resolved context grants COMMUNITY_OUTREACH", () => {
    const result = deriveAvailableAppModes([], [ctx({ availableModes: ["COMMUNITY_OUTREACH"] })]);
    expect(result).toContain("outreach");
  });

  it("adds courier when a resolved context grants SPECIMEN_TRANSPORT", () => {
    const result = deriveAvailableAppModes([], [ctx({ availableModes: ["SPECIMEN_TRANSPORT"] })]);
    expect(result).toContain("courier");
  });
});
