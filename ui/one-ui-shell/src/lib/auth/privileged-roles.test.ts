import { describe, expect, it } from "vitest";
import { expandRoleGroup, hasPlatformOverride, PLATFORM_OVERRIDE_ROLES } from "./privileged-roles";

describe("privileged-roles", () => {
  it("expands SUPER_ADMIN into SYSTEM_ADMIN override groups", () => {
    const expanded = expandRoleGroup(["SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER"]);
    expect(expanded).toContain("SUPER_ADMIN");
    expect(expanded).toContain("SYSTEM_ADMIN");
  });

  it("does not expand groups without platform override roles", () => {
    const expanded = expandRoleGroup(["CLINICIAN", "NURSE"]);
    expect(expanded).not.toContain("SUPER_ADMIN");
  });

  it("detects platform override membership", () => {
    expect(hasPlatformOverride(["CITIZEN", "SUPER_ADMIN"])).toBe(true);
    expect(hasPlatformOverride(["CITIZEN"])).toBe(false);
  });

  it("lists canonical override roles", () => {
    expect(PLATFORM_OVERRIDE_ROLES).toContain("SUPER_ADMIN");
  });
});
