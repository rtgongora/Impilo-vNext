import { describe, it, expect } from "vitest";
import {
  evaluateRouteTrust,
  evaluateActionTrust,
  meetsAssurance,
  assuranceRank,
  ASSURANCE_UPGRADE_PATH,
  type AssuranceLevel,
} from "../action-trust-matrix";

/** The exact prefixes the former hard-coded HEALTH_RESTRICTED_PREFIXES array bounced. */
const LEGACY_HEALTH_PREFIXES = [
  "/ehr",
  "/clinical",
  "/pharmacy",
  "/lab",
  "/queue",
  "/scheduling",
  "/shift",
  "/telemedicine",
];

describe("action-trust-matrix — assurance ordering", () => {
  it("ranks the ladder low → high", () => {
    expect(assuranceRank("UNVERIFIED")).toBeLessThan(assuranceRank("TEMPORARY"));
    expect(assuranceRank("TEMPORARY")).toBeLessThan(assuranceRank("VERIFIED"));
  });

  it("meetsAssurance is inclusive of equal and higher levels", () => {
    expect(meetsAssurance("VERIFIED", "TEMPORARY")).toBe(true);
    expect(meetsAssurance("TEMPORARY", "TEMPORARY")).toBe(true);
    expect(meetsAssurance("UNVERIFIED", "TEMPORARY")).toBe(false);
    expect(meetsAssurance(null, "TEMPORARY")).toBe(false);
  });
});

describe("action-trust-matrix — route gate is a superset of the legacy bounce", () => {
  it("blocks UNVERIFIED on every legacy health prefix (behaviour preserved)", () => {
    for (const prefix of LEGACY_HEALTH_PREFIXES) {
      const decision = evaluateRouteTrust(`${prefix}/anything`, "UNVERIFIED");
      expect(decision, `expected a rule for ${prefix}`).not.toBeNull();
      expect(decision!.allowed).toBe(false);
      expect(decision!.needsUpgrade).toBe(true);
      expect(decision!.upgradePath).toBe(ASSURANCE_UPGRADE_PATH);
    }
  });

  it("allows TEMPORARY and VERIFIED on legacy health prefixes (no regression)", () => {
    for (const prefix of LEGACY_HEALTH_PREFIXES) {
      for (const level of ["TEMPORARY", "VERIFIED"] as AssuranceLevel[]) {
        const decision = evaluateRouteTrust(prefix, level);
        expect(decision!.allowed, `${level} should reach ${prefix}`).toBe(true);
      }
    }
  });

  it("returns null for unrestricted routes (no assurance dimension)", () => {
    expect(evaluateRouteTrust("/home", "UNVERIFIED")).toBeNull();
    expect(evaluateRouteTrust("/wellness", "UNVERIFIED")).toBeNull();
  });
});

describe("action-trust-matrix — sensitive actions", () => {
  it("requires an upgrade when assurance is below the action's requirement", () => {
    const d = evaluateActionTrust("claim.submit", "TEMPORARY");
    expect(d.needsUpgrade).toBe(true);
    expect(d.allowed).toBe(false);
    expect(d.upgradePath).toBe(ASSURANCE_UPGRADE_PATH);
  });

  it("requires a fresh step-up even when the level is met", () => {
    const d = evaluateActionTrust("claim.submit", "VERIFIED");
    expect(d.needsUpgrade).toBe(false);
    expect(d.needsStepUp).toBe(true);
    expect(d.allowed).toBe(false);
    expect(d.stepUpMethod).toBeTruthy();
  });

  it("allows when the level is met and step-up is fresh", () => {
    const d = evaluateActionTrust("claim.submit", "VERIFIED", true);
    expect(d.allowed).toBe(true);
    expect(d.needsStepUp).toBe(false);
  });

  it("allows a TEMPORARY-tier action at TEMPORARY with no step-up", () => {
    const d = evaluateActionTrust("appointment.book", "TEMPORARY");
    expect(d.allowed).toBe(true);
    expect(d.needsStepUp).toBe(false);
  });
});
