import { describe, expect, it } from "vitest";
import { matchesRequiredRole } from "../AuthGuardProvider";

describe("matchesRequiredRole", () => {
  it("ADMIN_OR_HIE allows HIE_ADMIN", () => {
    expect(matchesRequiredRole((r) => r === "HIE_ADMIN", "ADMIN_OR_HIE")).toBe(true);
  });

  it("ADMIN_OR_HIE allows FACILITY_ADMIN", () => {
    expect(matchesRequiredRole((r) => r === "FACILITY_ADMIN", "ADMIN_OR_HIE")).toBe(true);
  });

  it("REGISTRY_ADMIN allows HIE_ADMIN", () => {
    expect(matchesRequiredRole((r) => r === "HIE_ADMIN", "REGISTRY_ADMIN")).toBe(true);
  });

  it("REGISTRY_ADMIN denies FACILITY_ADMIN without registry roles", () => {
    expect(matchesRequiredRole((r) => r === "FACILITY_ADMIN", "REGISTRY_ADMIN")).toBe(false);
  });

  it("ORGANIZATION_ADMIN allows FINANCE", () => {
    expect(matchesRequiredRole((r) => r === "FINANCE", "ORGANIZATION_ADMIN")).toBe(true);
  });

  it("ORGANIZATION_ADMIN denies HIE_ADMIN-only principals", () => {
    expect(matchesRequiredRole((r) => r === "HIE_ADMIN", "ORGANIZATION_ADMIN")).toBe(false);
  });

  it("ORGANIZATION_ADMIN allows FACILITY_ADMIN", () => {
    expect(matchesRequiredRole((r) => r === "FACILITY_ADMIN", "ORGANIZATION_ADMIN")).toBe(true);
  });

  it("REGISTRY_ADMIN denies FINANCE-only operators", () => {
    expect(matchesRequiredRole((r) => r === "FINANCE", "REGISTRY_ADMIN")).toBe(false);
  });
});
