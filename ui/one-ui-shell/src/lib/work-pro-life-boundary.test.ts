import { describe, expect, it } from "vitest";
import {
  ehrSubjectFromPath,
  evaluateClinicalWorkAccess,
  isSelfSubject,
  type BoundaryActor,
} from "./work-pro-life-boundary";

const provider: BoundaryActor = { healthId: "HID-ABC-001", providerActivated: true };
const citizen: BoundaryActor = { healthId: "HID-ABC-001", providerActivated: false };

describe("ehrSubjectFromPath", () => {
  it("extracts the patient id from an /ehr root path", () => {
    expect(ehrSubjectFromPath("/ehr/HID-PT-999")).toBe("HID-PT-999");
  });
  it("extracts the patient id from a deep /ehr sub-route", () => {
    expect(ehrSubjectFromPath("/ehr/HID-PT-999/medications")).toBe("HID-PT-999");
  });
  it("strips query strings", () => {
    expect(ehrSubjectFromPath("/ehr/HID-PT-999?tab=labs")).toBe("HID-PT-999");
  });
  it("decodes encoded ids", () => {
    expect(ehrSubjectFromPath("/ehr/HID%2DPT%2D999")).toBe("HID-PT-999");
  });
  it("returns null for non-ehr paths", () => {
    expect(ehrSubjectFromPath("/home")).toBeNull();
    expect(ehrSubjectFromPath("/queue")).toBeNull();
    expect(ehrSubjectFromPath("/ehr")).toBeNull();
  });
});

describe("isSelfSubject", () => {
  it("matches the actor's own health id (case/space-insensitive)", () => {
    expect(isSelfSubject(provider, "HID-ABC-001")).toBe(true);
    expect(isSelfSubject(provider, "  hid-abc-001 ")).toBe(true);
  });
  it("does not match another person", () => {
    expect(isSelfSubject(provider, "HID-PT-999")).toBe(false);
  });
  it("is false when either side is blank", () => {
    expect(isSelfSubject({ ...provider, healthId: null }, "HID-ABC-001")).toBe(false);
    expect(isSelfSubject(provider, "")).toBe(false);
    expect(isSelfSubject(provider, null)).toBe(false);
  });
});

describe("evaluateClinicalWorkAccess — WORK-PRO-LIFE-ISOLATION / SELF-TREATMENT-BLOCK", () => {
  it("allows a provider to open another person's EHR", () => {
    expect(evaluateClinicalWorkAccess("/ehr/HID-PT-999/vitals", provider)).toEqual({
      allowed: true,
    });
  });

  it("blocks a provider opening their OWN EHR in the work shell (self-treatment)", () => {
    const d = evaluateClinicalWorkAccess("/ehr/HID-ABC-001/medications", provider);
    expect(d.allowed).toBe(false);
    if (!d.allowed) {
      expect(d.reason).toBe("SELF_TREATMENT_BLOCKED");
      expect(d.redirectTo).toBe("/home");
    }
  });

  it("blocks self-treatment regardless of provider activation (the record is My-Life)", () => {
    const d = evaluateClinicalWorkAccess("/ehr/HID-ABC-001", citizen);
    expect(d.allowed).toBe(false);
    if (!d.allowed) expect(d.reason).toBe("SELF_TREATMENT_BLOCKED");
  });

  it("requires provider activation to transact clinically on another person", () => {
    const d = evaluateClinicalWorkAccess("/ehr/HID-PT-999", citizen);
    expect(d.allowed).toBe(false);
    if (!d.allowed) {
      expect(d.reason).toBe("WORK_REQUIRES_PROVIDER");
      expect(d.redirectTo).toContain("/provider/activate");
    }
  });

  it("is a no-op for non-clinical paths (other guards apply)", () => {
    expect(evaluateClinicalWorkAccess("/home", provider)).toEqual({ allowed: true });
    expect(evaluateClinicalWorkAccess("/professional", citizen)).toEqual({ allowed: true });
  });
});
