import { describe, expect, it } from "vitest";
import {
  QUICK_ACTION_IDS,
  SHELL_APPS,
  SHELL_COMMANDS,
  appVisibleForUser,
  findShellAppByCode,
  listQuickActions,
  listVisibleShellApps,
  visibleShellCommands,
} from "../app-registry";
import { ROLE_GROUPS } from "@/lib/auth/role-groups";
import type { AppDefinition } from "../types";

/** Persona role sets mirror docs/demo/persona-truth-pack.md — keep in sync. */
const PERSONAS: Record<string, string[]> = {
  "dr.mapfumo (doctor)": ["CLINICIAN"],
  "nurse.chienda (nurse)": ["NURSE"],
  "clerk.dube (records clerk)": ["SUPPORT_AGENT"],
  "admin.harare (facility admin)": ["FACILITY_ADMIN"],
  "pharm.tsuro (pharmacist)": ["PHARMACIST"],
  "trainer.chikafu (course author)": ["CLINICIAN", "TRAINER"],
  "citizen.moyo (citizen)": ["CITIZEN"],
  "admin.central (national admin)": ["SYSTEM_ADMIN"],
  "regulator.hpcz (regulator)": ["HIE_ADMIN"],
};

function rolesToHasRole(roles: string[]): (r: string) => boolean {
  return (r: string) => roles.includes(r);
}

describe("shell app-registry", () => {
  it("findShellAppByCode returns known system apps", () => {
    expect(findShellAppByCode("shell_file_manager")?.href).toBe("/shell/file-manager");
    expect(findShellAppByCode("home")?.href).toBe("/home");
    expect(findShellAppByCode("intelligence_hub")?.href).toBe("/intelligence");
    expect(findShellAppByCode("inventory")?.href).toBe("/inventory");
    expect(findShellAppByCode("enterprise")?.href).toBe("/enterprise");
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

  it("every requiredRole on apps and commands resolves to a role group (no dead gates)", () => {
    const gates = [
      ...SHELL_APPS.map((a) => a.requiredRole),
      ...SHELL_COMMANDS.map((c) => c.requiredRole),
    ].filter((g): g is string => !!g);
    const unknown = [...new Set(gates)].filter((g) => !ROLE_GROUPS[g]);
    // A gate outside ROLE_GROUPS falls through to hasRole(<literal>), which no
    // realm role satisfies unless it exactly matches — that is how dispatch_ops
    // became invisible to everyone. Keep this list empty.
    expect(unknown).toEqual([]);
  });

  it("named vNext services are present in the launcher", () => {
    for (const code of ["khuluma", "rito", "simba", "ubomi", "dura", "daidzai", "vashandi", "telemedicine"]) {
      expect(findShellAppByCode(code), `launcher app ${code}`).toBeDefined();
    }
  });

  it("quick actions all reference navigate commands that exist", () => {
    const byId = new Map(SHELL_COMMANDS.map((c) => [c.id, c]));
    for (const id of QUICK_ACTION_IDS) {
      const cmd = byId.get(id);
      expect(cmd, `quick action ${id}`).toBeDefined();
      expect(cmd?.action.type).toBe("navigate");
    }
  });

  it.each(Object.entries(PERSONAS))("persona visibility: %s", (_persona, roles) => {
    const hasRole = rolesToHasRole(roles);
    const appCodes = listVisibleShellApps(hasRole).map((a) => a.appCode);
    const commandIds = visibleShellCommands(hasRole).map((c) => c.id);

    // Ungated citizen surfaces are visible to everyone.
    expect(appCodes).toContain("home");
    expect(appCodes).toContain("simba");
    expect(appCodes).toContain("ubomi");

    if (roles.includes("CLINICIAN") || roles.includes("NURSE")) {
      expect(appCodes).toContain("clinical");
      expect(appCodes).toContain("telemedicine");
      expect(commandIds).toContain("cmd-register-patient");
      expect(commandIds).toContain("cmd-diagnostics-orders");
    }
    if (roles.includes("SUPPORT_AGENT")) {
      expect(appCodes).toContain("queue");
      expect(commandIds).toContain("cmd-register-patient");
      expect(appCodes).not.toContain("clinical");
    }
    if (roles.includes("TRAINER")) {
      expect(commandIds).toContain("cmd-new-course");
      expect(commandIds).toContain("cmd-course-studio");
    } else if (!roles.some((r) => ["FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"].includes(r))) {
      expect(commandIds).not.toContain("cmd-new-course");
    }
    if (roles.includes("HIE_ADMIN")) {
      expect(commandIds).toContain("cmd-registry-plane");
      expect(commandIds).toContain("cmd-issue-provider-id");
    }
    if (roles.includes("CITIZEN") && roles.length === 1) {
      expect(appCodes).not.toContain("clinical");
      expect(commandIds).not.toContain("cmd-issue-provider-id");
      expect(commandIds).toContain("cmd-claim-facility");
      expect(commandIds).toContain("cmd-request-provider-access");
    }
    if (roles.includes("SYSTEM_ADMIN") || roles.includes("FACILITY_ADMIN")) {
      expect(commandIds).toContain("cmd-workforce-intake");
    }

    // Quick actions never exceed the display cap and stay role-consistent.
    const quick = listQuickActions(hasRole);
    expect(quick.length).toBeLessThanOrEqual(8);
    for (const q of quick) {
      expect(commandIds).toContain(q.id);
    }
  });
});
