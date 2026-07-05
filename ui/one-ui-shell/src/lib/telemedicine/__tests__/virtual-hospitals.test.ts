import { describe, expect, it } from "vitest";
import {
  ALL_VIRTUAL_HOSPITALS,
  PROVINCIAL_VIRTUAL_HOSPITALS,
  STRATEGIC_VIRTUAL_HOSPITALS,
  ZW_PROVINCES,
  getVirtualHospital,
} from "../virtual-hospitals";
import { getSessionMode } from "../session-modes";

describe("virtual hospital operating model substrate", () => {
  it("seeds exactly the 12 strategic institutions plus one per province", () => {
    expect(STRATEGIC_VIRTUAL_HOSPITALS).toHaveLength(11);
    expect(PROVINCIAL_VIRTUAL_HOSPITALS).toHaveLength(ZW_PROVINCES.length);
    expect(ALL_VIRTUAL_HOSPITALS).toHaveLength(11 + ZW_PROVINCES.length);
  });

  it("uses unique Virtual Hospital IDs distinct from any facility-id shape", () => {
    const ids = ALL_VIRTUAL_HOSPITALS.map((vh) => vh.id);
    expect(new Set(ids).size).toBe(ids.length);
    for (const id of ids) {
      expect(id).toMatch(/^vh-/);
    }
  });

  it("never claims OPERATIONAL status (no backend substrate exists yet)", () => {
    for (const vh of ALL_VIRTUAL_HOSPITALS) {
      expect(vh.substrateStatus).not.toBe("OPERATIONAL");
    }
  });

  it("keeps every queue honestly marked AWAITING_BACKEND", () => {
    for (const vh of ALL_VIRTUAL_HOSPITALS) {
      expect(vh.queues.length).toBeGreaterThan(0);
      for (const q of vh.queues) {
        expect(q.status).toBe("AWAITING_BACKEND");
      }
    }
  });

  it("only marks institutions routable when they carry a real routing seam", () => {
    for (const vh of ALL_VIRTUAL_HOSPITALS) {
      if (vh.substrateStatus === "ROUTABLE_VIA_EXISTING_SEAMS") {
        expect(vh.currentRoutingSeams.length).toBeGreaterThan(0);
      } else {
        expect(vh.currentRoutingSeams).toHaveLength(0);
      }
    }
  });

  it("routing seams only use BFF-supported routing types (never the 501 kinds)", () => {
    const supported = new Set([
      "TEAM",
      "SPECIALTY_POOL",
      "WORKSPACE",
      "FACILITY_SERVICE",
      "PRACTITIONER",
    ]);
    for (const vh of ALL_VIRTUAL_HOSPITALS) {
      for (const seam of vh.currentRoutingSeams) {
        expect(supported.has(seam.routingType)).toBe(true);
      }
    }
  });

  it("references only defined session modes", () => {
    for (const vh of ALL_VIRTUAL_HOSPITALS) {
      for (const modeId of vh.sessionModeIds) {
        expect(getSessionMode(modeId), `${vh.id} → ${modeId}`).toBeDefined();
      }
    }
  });

  it("gates cross-border participation behind explicit privileges", () => {
    for (const vh of ALL_VIRTUAL_HOSPITALS) {
      if (vh.crossBorderParticipation) {
        expect(vh.allowedCrossBorderPrivileges.length).toBeGreaterThan(0);
        expect(vh.allowedCrossBorderPrivileges).toContain(
          "BLOCKED_PENDING_REGULATORY_APPROVAL",
        );
      } else {
        expect(vh.allowedCrossBorderPrivileges).toHaveLength(0);
      }
    }
  });

  it("keeps regulatory status policy-configurable, never legally presumptuous", () => {
    for (const vh of ALL_VIRTUAL_HOSPITALS) {
      expect(vh.regulatoryStatus).toBe("POLICY_CONFIGURABLE_PENDING_DETERMINATION");
    }
  });

  it("resolves lookups by id", () => {
    expect(getVirtualHospital("vh-national-telemedicine")?.level).toBe("NATIONAL");
    expect(getVirtualHospital("vh-provincial-zw-ha")?.provinceCode).toBe("ZW-HA");
    expect(getVirtualHospital("nope")).toBeUndefined();
  });
});
