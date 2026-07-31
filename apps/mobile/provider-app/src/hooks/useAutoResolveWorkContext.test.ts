import { describe, expect, it } from "vitest";
import { selectPreferredWorkContext } from "./useAutoResolveWorkContext";
import type { ResolvedWorkContextsAttributes, ResolvedWorkContextView } from "@impilo/mobile-trust";

function ctx(overrides: Partial<ResolvedWorkContextView>): ResolvedWorkContextView {
  return {
    contextId: "ctx-default",
    contextKind: "facility",
    sourceSystem: "VASHANDI",
    availableModes: ["CLINICAL_CARE"],
    restrictions: [],
    label: "Context",
    groupHint: "regular",
    ...overrides,
  };
}

describe("selectPreferredWorkContext", () => {
  it("returns null when there are no resolved contexts", () => {
    const resolved: ResolvedWorkContextsAttributes = { contexts: [], sourceStatuses: [], requiresContextChooser: false };
    expect(selectPreferredWorkContext(resolved, "fac-1")).toBeNull();
  });

  it("prefers the context matching the app's currently selected facility", () => {
    const facMatch = ctx({ contextId: "ctx-fac", facilityId: "fac-1" });
    const recommended = ctx({ contextId: "ctx-rec", facilityId: "fac-2" });
    const resolved: ResolvedWorkContextsAttributes = {
      contexts: [recommended, facMatch],
      sourceStatuses: [],
      recommendedContextId: "ctx-rec",
      requiresContextChooser: false,
    };
    expect(selectPreferredWorkContext(resolved, "fac-1")?.contextId).toBe("ctx-fac");
  });

  it("falls back to recommendedContextId when no facility match exists", () => {
    const other = ctx({ contextId: "ctx-other", facilityId: "fac-9" });
    const recommended = ctx({ contextId: "ctx-rec", facilityId: "fac-2" });
    const resolved: ResolvedWorkContextsAttributes = {
      contexts: [other, recommended],
      sourceStatuses: [],
      recommendedContextId: "ctx-rec",
      requiresContextChooser: false,
    };
    expect(selectPreferredWorkContext(resolved, "fac-1")?.contextId).toBe("ctx-rec");
  });

  it("falls back to the first context when there is no facility match or recommendation", () => {
    const first = ctx({ contextId: "ctx-first" });
    const second = ctx({ contextId: "ctx-second" });
    const resolved: ResolvedWorkContextsAttributes = {
      contexts: [first, second],
      sourceStatuses: [],
      requiresContextChooser: false,
    };
    expect(selectPreferredWorkContext(resolved, null)?.contextId).toBe("ctx-first");
  });

  it("does not match a facility-less context against a null currentFacilityId", () => {
    const noFacility = ctx({ contextId: "ctx-nofac", facilityId: undefined });
    const resolved: ResolvedWorkContextsAttributes = {
      contexts: [noFacility],
      sourceStatuses: [],
      requiresContextChooser: false,
    };
    // Still resolves via the "first context" fallback — the point is the facility-match
    // branch itself must not throw/misbehave when currentFacilityId is null.
    expect(selectPreferredWorkContext(resolved, null)?.contextId).toBe("ctx-nofac");
  });
});

describe("useAutoResolveWorkContext remint contract", () => {
  it("remints on facility/workspace change using previousJti (no early-return on existing token)", () => {
    const { readFileSync } = require("node:fs") as typeof import("node:fs");
    const { join } = require("node:path") as typeof import("node:path");
    const src = readFileSync(join(__dirname, "useAutoResolveWorkContext.ts"), "utf8");
    expect(src).not.toContain("auth.session?.workContextToken || attemptedFor");
    expect(src).toContain("previousJti");
    expect(src).toContain("workContextJti");
    expect(src).toContain("mintWorkContextSession(preferred.contextId, workMode, previousJti)");
  });
});
