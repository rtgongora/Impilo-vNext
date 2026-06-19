import { describe, expect, it } from "vitest";
import { hasMobileVashandiEntry, resolveMobileVashandiNav } from "../src/vashandiNav";

describe("mobile vashandi workforce navigation", () => {
  it("hides all routes when no vashandi workspaces are visible", () => {
    expect(
      resolveMobileVashandiNav({
        workTabVisible: true,
        visibleVashandiWorkspaces: [],
      }),
    ).toHaveLength(0);
    expect(hasMobileVashandiEntry({ workTabVisible: true, visibleVashandiWorkspaces: [] })).toBe(false);
  });

  it("shows self-service routes for worker workspaces", () => {
    const items = resolveMobileVashandiNav({
      workTabVisible: true,
      visibleVashandiWorkspaces: [
        "vashandi.my_roster",
        "vashandi.my_attendance",
        "vashandi.leave_availability",
      ],
    });
    expect(items.some((i) => i.id === "my_roster")).toBe(true);
    expect(items.some((i) => i.id === "my_attendance")).toBe(true);
    expect(items.some((i) => i.id === "my_availability")).toBe(true);
    expect(items.some((i) => i.id === "facility_staff")).toBe(false);
  });

  it("shows facility staff route for facility manager workspace", () => {
    const items = resolveMobileVashandiNav({
      workTabVisible: true,
      visibleVashandiWorkspaces: ["vashandi.facility_staff", "vashandi.rosters"],
    });
    expect(items.some((i) => i.id === "facility_staff")).toBe(true);
    expect(items.some((i) => i.id === "my_roster")).toBe(true);
  });

  it("does not expose routes when Work tab is hidden", () => {
    expect(
      resolveMobileVashandiNav({
        workTabVisible: false,
        visibleVashandiWorkspaces: ["vashandi.my_roster"],
      }),
    ).toHaveLength(0);
  });
});
