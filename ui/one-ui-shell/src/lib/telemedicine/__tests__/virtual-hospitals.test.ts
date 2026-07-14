/**
 * Structural-integrity invariants for the canonical virtual-hospital seed.
 *
 * The virtual-hospital directory became sovereign in TUSO in Wave 2 and the UI's
 * ALL_VIRTUAL_HOSPITALS data constant was deleted (the UI now reads the BFF via
 * useVirtualHospitals()). These invariants still matter for the SEED document —
 * the fallback the BFF serves when TUSO is unreachable and the source the TUSO
 * V023 seed is generated from — so they now run against the canonical contract
 * `contracts/telemedicine/virtual-hospitals.json` instead of the deleted constant.
 *
 * Note: `substrateStatus` and per-queue `status` in this document are SEED values
 * (honest placeholders); at runtime the BFF overlays a computed live status per
 * pool. These tests assert the seed's honesty, not the runtime state.
 */

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { ZW_PROVINCES } from "../virtual-hospitals";
import { getSessionMode } from "../session-modes";

const CONTRACT_PATH = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../../../../../contracts/telemedicine/virtual-hospitals.json",
);

interface SeedVirtualHospital {
  id: string;
  level: string;
  provinceCode?: string;
  substrateStatus: string;
  currentRoutingSeams: Array<{ routingType: string }>;
  queues: Array<{ status: string }>;
  sessionModeIds: string[];
  crossBorderParticipation: boolean;
  allowedCrossBorderPrivileges: string[];
  regulatoryStatus: string;
}

const ALL: SeedVirtualHospital[] = (
  JSON.parse(readFileSync(CONTRACT_PATH, "utf8")) as { virtualHospitals: SeedVirtualHospital[] }
).virtualHospitals;

const PROVINCIAL = ALL.filter((vh) => vh.provinceCode);
const STRATEGIC = ALL.filter((vh) => !vh.provinceCode);

describe("virtual hospital operating model substrate (canonical seed)", () => {
  it("seeds exactly the 11 strategic institutions plus one per province", () => {
    expect(STRATEGIC).toHaveLength(11);
    expect(PROVINCIAL).toHaveLength(ZW_PROVINCES.length);
    expect(ALL).toHaveLength(11 + ZW_PROVINCES.length);
  });

  it("uses unique Virtual Hospital IDs distinct from any facility-id shape", () => {
    const ids = ALL.map((vh) => vh.id);
    expect(new Set(ids).size).toBe(ids.length);
    for (const id of ids) {
      expect(id).toMatch(/^vh-/);
    }
  });

  it("only marks institutions routable when they carry a real routing seam", () => {
    for (const vh of ALL) {
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
    for (const vh of ALL) {
      for (const seam of vh.currentRoutingSeams) {
        expect(supported.has(seam.routingType)).toBe(true);
      }
    }
  });

  it("references only defined session modes", () => {
    for (const vh of ALL) {
      for (const modeId of vh.sessionModeIds) {
        expect(getSessionMode(modeId), `${vh.id} → ${modeId}`).toBeDefined();
      }
    }
  });

  it("gates cross-border participation behind explicit privileges", () => {
    for (const vh of ALL) {
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
    for (const vh of ALL) {
      expect(vh.regulatoryStatus).toBe("POLICY_CONFIGURABLE_PENDING_DETERMINATION");
    }
  });

  it("resolves lookups by id from the seed", () => {
    const byId = new Map(ALL.map((vh) => [vh.id, vh]));
    expect(byId.get("vh-national-telemedicine")?.level).toBe("NATIONAL");
    expect(byId.get("vh-provincial-zw-ha")?.provinceCode).toBe("ZW-HA");
    expect(byId.get("nope")).toBeUndefined();
  });
});
