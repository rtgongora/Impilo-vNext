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

  it("PAYER_OPS excludes FACILITY_ADMIN but includes FINANCE", () => {
    expect(matchesRequiredRole((r) => r === "FACILITY_ADMIN", "PAYER_OPS")).toBe(false);
    expect(matchesRequiredRole((r) => r === "FINANCE", "PAYER_OPS")).toBe(true);
  });

  it("MSIKA_GOVERNANCE allows FACILITY_ADMIN and FINANCE", () => {
    expect(matchesRequiredRole((r) => r === "FACILITY_ADMIN", "MSIKA_GOVERNANCE")).toBe(true);
    expect(matchesRequiredRole((r) => r === "FINANCE", "MSIKA_GOVERNANCE")).toBe(true);
  });

  it("COMMERCE still allows cross-functional operators like SUPPORT_AGENT", () => {
    expect(matchesRequiredRole((r) => r === "SUPPORT_AGENT", "COMMERCE")).toBe(true);
  });

  it("PUBLIC_HEALTH allows PUBLIC_HEALTH_OFFICER and governed DEVELOPER", () => {
    expect(matchesRequiredRole((r) => r === "PUBLIC_HEALTH_OFFICER", "PUBLIC_HEALTH")).toBe(true);
    expect(matchesRequiredRole((r) => r === "DEVELOPER", "PUBLIC_HEALTH")).toBe(true);
    expect(matchesRequiredRole((r) => r === "FINANCE", "PUBLIC_HEALTH")).toBe(false);
  });
});
