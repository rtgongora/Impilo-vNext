import { describe, it, expect } from "vitest";
import {
  buildAvailableOperationalModes,
  isOperationalMode,
  isOrganizationAdminSurface,
  operationalModeExpectsFacilityWorkSequence,
  OPERATIONAL_CONTEXT_SESSION_KEYS,
  principalHasOrganizationAdminPlane,
  principalHasRegistryAdminPlane,
  resolvePostLoginOperationalContext,
  suggestDefaultOperationalMode,
} from "../operational-context";

describe("operational-context", () => {
  it("isOperationalMode validates known ids", () => {
    expect(isOperationalMode("facility_work")).toBe(true);
    expect(isOperationalMode("bogus")).toBe(false);
  });

  it("isOrganizationAdminSurface validates known surfaces", () => {
    expect(isOrganizationAdminSurface("facility_admin")).toBe(true);
    expect(isOrganizationAdminSurface("bogus")).toBe(false);
  });

  it("operationalModeExpectsFacilityWorkSequence is true only for facility_work", () => {
    expect(operationalModeExpectsFacilityWorkSequence("facility_work")).toBe(true);
    expect(operationalModeExpectsFacilityWorkSequence("registry_admin")).toBe(false);
    expect(operationalModeExpectsFacilityWorkSequence("my_life")).toBe(false);
  });

  it("buildAvailableOperationalModes returns my_life for null user", () => {
    expect(buildAvailableOperationalModes(null)).toEqual([]);
  });

  it("clinical user gets facility_work and my_professional", () => {
    const modes = buildAvailableOperationalModes({
      roles: ["CLINICIAN"],
      actorType: "PROVIDER",
    });
    expect(modes).toContain("my_life");
    expect(modes).toContain("my_professional");
    expect(modes).toContain("facility_work");
  });

  it("SYSTEM_ADMIN unlocks registry_admin and organization_admin", () => {
    const modes = buildAvailableOperationalModes({
      roles: ["SYSTEM_ADMIN"],
      actorType: "OPERATOR",
    });
    expect(modes).toContain("registry_admin");
    expect(modes).toContain("organization_admin");
  });

  it("FACILITY_ADMIN unlocks organization_admin and facility_work (facility-tied role)", () => {
    const modes = buildAvailableOperationalModes({
      roles: ["FACILITY_ADMIN"],
      actorType: "OPERATOR",
    });
    expect(modes).toContain("organization_admin");
    expect(modes).toContain("facility_work");
  });

  it("suggestDefaultOperationalMode prefers facility_work for clinicians", () => {
    expect(
      suggestDefaultOperationalMode({ roles: ["NURSE"], actorType: "PROVIDER" }),
    ).toBe("facility_work");
  });

  it("suggestDefaultOperationalMode uses my_life for citizen", () => {
    expect(
      suggestDefaultOperationalMode({ roles: ["CITIZEN"], actorType: "CITIZEN" }),
    ).toBe("my_life");
  });

  it("resolvePostLoginOperationalContext bundles availability and suggestion", () => {
    const user = { roles: ["PHARMACIST"], actorType: "PROVIDER" as const };
    const r = resolvePostLoginOperationalContext(user);
    expect(r.available).toContain("facility_work");
    expect(r.suggested).toBe("facility_work");
  });

  it("OPERATIONAL_CONTEXT_SESSION_KEYS lists session keys", () => {
    expect(OPERATIONAL_CONTEXT_SESSION_KEYS).toContain("exp:operational_mode");
    expect(OPERATIONAL_CONTEXT_SESSION_KEYS).toContain("exp:organization_admin_surface");
  });

  it("principalHasRegistryAdminPlane detects SYSTEM_ADMIN and HIE_ADMIN", () => {
    expect(principalHasRegistryAdminPlane({ roles: ["SYSTEM_ADMIN"], actorType: "OPERATOR" })).toBe(true);
    expect(principalHasRegistryAdminPlane({ roles: ["HIE_ADMIN"], actorType: "OPERATOR" })).toBe(true);
    expect(principalHasRegistryAdminPlane({ roles: ["FACILITY_ADMIN"], actorType: "OPERATOR" })).toBe(false);
  });

  it("principalHasOrganizationAdminPlane includes finance operators", () => {
    expect(
      principalHasOrganizationAdminPlane({ roles: ["FINANCE"], actorType: "OPERATOR" }),
    ).toBe(true);
    expect(
      principalHasOrganizationAdminPlane({ roles: ["FACILITY_ADMIN"], actorType: "OPERATOR" }),
    ).toBe(true);
  });
});
