import { describe, expect, it } from "vitest";
import { isFocusedWorkspaceRoute, resolveNavRailMode } from "./workspace-context";

describe("isFocusedWorkspaceRoute", () => {
  it("treats navigation/landing surfaces as NOT focused work", () => {
    for (const path of [
      "/",
      "/home",
      "/home/profile",
      "/discover",
      "/welcome/find-care",
      "/citizen",
      "/professional",
      "/clinical",
      "/learning",
      "/pharmacy",
      "/wellness",
    ]) {
      expect(isFocusedWorkspaceRoute(path), path).toBe(false);
    }
  });

  it("treats opened applications, records, and drill-downs as focused work", () => {
    for (const path of [
      "/learning/studio",
      "/learning/courses/abc",
      "/ehr/patient-1",
      "/clinical/emergency",
      "/pharmacy/dispense",
      "/telemedicine/session/xyz",
      "/operations/vito/registration",
      "/wellness/goals",
      "/finance/billing",
      "/work/administration-governance",
    ]) {
      expect(isFocusedWorkspaceRoute(path), path).toBe(true);
    }
  });

  it("normalizes trailing slashes and query strings", () => {
    expect(isFocusedWorkspaceRoute("/learning/")).toBe(false);
    expect(isFocusedWorkspaceRoute("/learning/studio?tab=modules")).toBe(true);
  });
});

describe("resolveNavRailMode", () => {
  it("auto: expanded while navigating, compact inside focused work", () => {
    expect(resolveNavRailMode({ pathname: "/home", pref: "auto", focusMode: false })).toBe("expanded");
    expect(resolveNavRailMode({ pathname: "/learning/studio", pref: "auto", focusMode: false })).toBe("compact");
  });

  it("manual preference wins over route context", () => {
    expect(resolveNavRailMode({ pathname: "/learning/studio", pref: "expanded", focusMode: false })).toBe("expanded");
    expect(resolveNavRailMode({ pathname: "/home", pref: "compact", focusMode: false })).toBe("compact");
  });

  it("focus mode hides the rail regardless of preference", () => {
    expect(resolveNavRailMode({ pathname: "/home", pref: "expanded", focusMode: true })).toBe("hidden");
  });
});
