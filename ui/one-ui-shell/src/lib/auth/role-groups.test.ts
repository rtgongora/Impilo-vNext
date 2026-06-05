import { describe, expect, it } from "vitest";
import { matchesRequiredRole, ROLE_GROUPS } from "./role-groups";

describe("role-groups", () => {
  it("grants ADMIN group to SUPER_ADMIN", () => {
    const hasRole = (role: string) => role === "SUPER_ADMIN";
    expect(matchesRequiredRole(hasRole, "ADMIN")).toBe(true);
  });

  it("includes SUPER_ADMIN in finance and clinical groups", () => {
    expect(ROLE_GROUPS.FINANCE).toContain("SUPER_ADMIN");
    expect(ROLE_GROUPS.CLINICAL).toContain("SUPER_ADMIN");
  });
});
