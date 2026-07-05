import { describe, expect, it } from "vitest";
import { CLINICAL_SESSION_MODES, getSessionMode } from "../session-modes";
import { PRIVACY_LEVELS } from "../privacy";

describe("clinical session-mode governance matrix", () => {
  it("defines the ten doctrine session modes", () => {
    expect(CLINICAL_SESSION_MODES).toHaveLength(10);
    const ids = CLINICAL_SESSION_MODES.map((m) => m.id);
    expect(new Set(ids).size).toBe(10);
    for (const required of [
      "direct-clinical-encounter",
      "hybrid-encounter-support",
      "provider-to-provider-advice",
      "mdt-case-review",
      "case-presentation",
      "case-notes-review",
      "audit-mortality-review",
      "teaching-supervision",
      "emergency-advisory",
      "diagnostics-review",
    ]) {
      expect(ids).toContain(required);
    }
  });

  it("only claims TEMPLATED status when a backing W0 template exists", () => {
    for (const mode of CLINICAL_SESSION_MODES) {
      if (mode.status === "TEMPLATED" || mode.status === "TEMPLATED_PARTIAL") {
        expect(mode.backingTemplate).not.toBeNull();
      } else {
        expect(mode.backingTemplate).toBeNull();
      }
      if (mode.status !== "TEMPLATED") {
        // Anything short of fully templated must state its gap honestly.
        expect(mode.gap, `${mode.id} must declare its gap`).toBeTruthy();
      }
    }
  });

  it("uses only defined privacy levels and keeps defaults within the allowed set", () => {
    for (const mode of CLINICAL_SESSION_MODES) {
      expect(mode.identityVisibility.length).toBeGreaterThan(0);
      for (const level of mode.identityVisibility) {
        expect(PRIVACY_LEVELS[level]).toBeDefined();
      }
      expect(mode.identityVisibility).toContain(mode.defaultIdentityVisibility);
    }
  });

  it("never defaults MDT/audit/teaching surfaces to full identity", () => {
    for (const id of ["mdt-case-review", "case-presentation", "audit-mortality-review", "teaching-supervision"]) {
      const mode = getSessionMode(id);
      expect(mode).toBeDefined();
      expect(mode?.defaultIdentityVisibility).not.toBe("FULL");
    }
  });

  it("keeps audit and emergency modes at strict audit depth", () => {
    expect(getSessionMode("audit-mortality-review")?.auditDepth).toBe("STRICT");
    expect(getSessionMode("emergency-advisory")?.auditDepth).toBe("STRICT");
  });

  it("keeps recording off by default for sensitive modes", () => {
    for (const id of ["case-notes-review", "audit-mortality-review", "emergency-advisory", "provider-to-provider-advice"]) {
      expect(getSessionMode(id)?.recordingDefault).toBe("OFF");
    }
  });
});
