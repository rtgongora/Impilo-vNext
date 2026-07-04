import { beforeEach, describe, expect, it } from "vitest";
import {
  CLINICAL_GROUP_TYPES,
  GROUP_CREATION_BLOCKED_REASON,
  GROUP_GOVERNANCE_REQUIREMENTS,
} from "../clinical-groups";
import { getSessionMode } from "../session-modes";
import { addPin, isPinned, loadPins, removePin, type PinnedTarget } from "../pinning";

describe("clinical group taxonomy governance", () => {
  it("covers the required group types", () => {
    const types = CLINICAL_GROUP_TYPES.map((g) => g.type);
    expect(new Set(types).size).toBe(types.length);
    for (const required of [
      "SPECIALTY",
      "MDT_PANEL",
      "AUDIT_COMMITTEE",
      "SUPERVISION_LEARNING",
      "ON_CALL_POOL",
      "DIASPORA_SPECIALIST_POOL",
      "DISASTER_RESPONSE_POOL",
    ]) {
      expect(types).toContain(required);
    }
  });

  it("only references defined session modes", () => {
    for (const group of CLINICAL_GROUP_TYPES) {
      expect(group.allowedSessionModeIds.length).toBeGreaterThan(0);
      for (const modeId of group.allowedSessionModeIds) {
        expect(getSessionMode(modeId), `${group.type} → ${modeId}`).toBeDefined();
      }
    }
  });

  it("never grants panel/learning groups care-team-scoped patient data", () => {
    for (const type of ["MDT_PANEL", "AUDIT_COMMITTEE", "SUPERVISION_LEARNING", "DIASPORA_SPECIALIST_POOL"]) {
      const group = CLINICAL_GROUP_TYPES.find((g) => g.type === type);
      expect(group?.patientDataExposure).toBe("CASE_PRESENTATION_ONLY");
    }
  });

  it("states the fail-closed creation posture and governance requirements", () => {
    expect(GROUP_CREATION_BLOCKED_REASON).toMatch(/disabled/i);
    expect(GROUP_GOVERNANCE_REQUIREMENTS.length).toBeGreaterThanOrEqual(8);
  });
});

describe("routing pins (client-side discovery only)", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  const pin: Omit<PinnedTarget, "pinnedAt"> = {
    kind: "VIRTUAL_HOSPITAL",
    id: "vh-national-telemedicine",
    label: "National Telemedicine Hospital",
  };

  it("adds, persists, detects and removes pins", () => {
    let pins = loadPins();
    expect(pins).toHaveLength(0);

    pins = addPin(pins, pin);
    expect(isPinned(pins, "VIRTUAL_HOSPITAL", pin.id)).toBe(true);
    expect(loadPins()).toHaveLength(1);

    // Duplicate add is a no-op.
    pins = addPin(pins, pin);
    expect(pins).toHaveLength(1);

    pins = removePin(pins, "VIRTUAL_HOSPITAL", pin.id);
    expect(isPinned(pins, "VIRTUAL_HOSPITAL", pin.id)).toBe(false);
    expect(loadPins()).toHaveLength(0);
  });

  it("survives corrupted storage without throwing", () => {
    window.localStorage.setItem("impilo.telemedicine.routing-pins.v1", "{not json");
    expect(loadPins()).toEqual([]);
  });
});
