import { describe, expect, it } from "vitest";
import { resolveMobileAdministrationNav } from "../administrationGovernanceNav";

describe("mobile administration governance navigation", () => {
  it("hides all admin routes for citizens without management workspaces", () => {
    expect(
      resolveMobileAdministrationNav({
        workTabVisible: true,
        visibleManagementWorkspaces: [],
        visibleWorkspaces: [],
      }),
    ).toHaveLength(0);
  });

  it("shows contract-filtered items for municipal admin", () => {
    const items = resolveMobileAdministrationNav({
      workTabVisible: true,
      visibleManagementWorkspaces: ["municipal_user_management"],
      visibleWorkspaces: ["municipal_workspace"],
    });
    expect(items.some((i) => i.id === "administration_governance")).toBe(true);
    expect(items.some((i) => i.id === "organisation_users")).toBe(true);
    expect(items.some((i) => i.id === "madi_users")).toBe(false);
  });

  it("does not expose routes when Work tab is hidden", () => {
    expect(
      resolveMobileAdministrationNav({
        workTabVisible: false,
        visibleManagementWorkspaces: ["hsc_user_management"],
      }),
    ).toHaveLength(0);
  });
});
