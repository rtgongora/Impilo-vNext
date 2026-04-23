import { describe, expect, it } from "vitest";
import { appVisibleForUser, findShellAppByCode, listVisibleShellApps } from "../app-registry";
import type { AppDefinition } from "../types";

describe("shell app-registry", () => {
  it("findShellAppByCode returns known system apps", () => {
    expect(findShellAppByCode("shell_file_manager")?.href).toBe("/shell/file-manager");
    expect(findShellAppByCode("home")?.href).toBe("/home");
    expect(findShellAppByCode("intelligence_hub")?.href).toBe("/intelligence");
    expect(findShellAppByCode("inventory")?.href).toBe("/inventory");
  });

  it("listVisibleShellApps filters by role", () => {
    const citizenOnly = (role: string) => role === "CITIZEN";
    const apps = listVisibleShellApps(citizenOnly);
    expect(apps.some((a) => a.appCode === "home")).toBe(true);
    expect(apps.some((a) => a.appCode === "clinical")).toBe(false);
  });

  it("appVisibleForUser respects requiredRole groups", () => {
    const admin: AppDefinition = {
      id: "x",
      appCode: "x",
      name: "X",
      description: "",
      icon: "Shield",
      category: "system",
      href: "/x",
      activeFlag: true,
      requiredRole: "ADMIN",
      systemAppFlag: true,
    };
    expect(appVisibleForUser(admin, () => false)).toBe(false);
    expect(appVisibleForUser(admin, (r) => r === "FACILITY_ADMIN")).toBe(true);
  });
});
