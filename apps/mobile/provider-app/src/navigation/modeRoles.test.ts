/**
 * Guards the realm-role vocabulary the ModeSwitcher matches against.
 *
 * The vocabulary here is measured, not assumed: the live preview realm holds 66
 * realm roles, every one UPPERCASE, and the `impilo-mobile-provider` Keycloak
 * client carries a realm-role mapper with `userinfo.token.claim=true`, so
 * `auth.user.realm_access.roles` (which the app reads from `/userinfo`) carries
 * those names verbatim. Seeded users measured directly: `dr.mapfumo` →
 * `CLINICIAN`, `nurse.chienda` → `NURSE`, `courier.banda` → `COURIER`.
 *
 * Every assertion below fails against the previous lowercase `MODE_ROLES`
 * constants (`provider`, `clinician`, `community_health_worker`, `courier`,
 * `driver`, `facility_runner`, `supervisor`, `facility_manager`, `admin`),
 * which matched no realm role in any environment.
 */
import { describe, expect, it } from "vitest";
import { getAvailableModes, MODE_ROLE_GROUPS } from "./modeRoles";
import { ROLE_GROUPS } from "../lib/roleGroups";
import type { AppMode } from "../types";

/** Realm roles as actually held by the seeded preview users. */
const CLINICIAN = ["CLINICIAN", "default-roles-impilo"];
const NURSE = ["NURSE", "default-roles-impilo"];
const COURIER = ["COURIER", "default-roles-impilo"];
const CHW = ["CHW", "default-roles-impilo"];
const FACILITY_ADMIN = ["FACILITY_ADMIN", "default-roles-impilo"];
const DISPATCH_COORDINATOR = ["DISPATCH_COORDINATOR", "default-roles-impilo"];
const CITIZEN = ["CITIZEN", "default-roles-impilo"];

describe("getAvailableModes — real realm role vocabulary", () => {
  it("gives a CLINICIAN more than the bare provider default", () => {
    // The lowercase constants returned exactly ["provider"] here, for everyone.
    const modes = getAvailableModes(CLINICIAN);
    expect(modes).toEqual(expect.arrayContaining(["provider", "outreach", "offline"]));
    expect(modes.length).toBeGreaterThan(1);
  });

  it("gives a NURSE the clinical modes", () => {
    expect(getAvailableModes(NURSE)).toEqual(expect.arrayContaining(["provider", "outreach", "offline"]));
  });

  it("gives a COURIER courier mode and NOT provider", () => {
    const modes = getAvailableModes(COURIER);
    expect(modes).toContain("courier");
    expect(modes).not.toContain("provider");
    expect(modes).not.toContain("supervisor");
  });

  it("gives a DISPATCH_COORDINATOR courier mode", () => {
    expect(getAvailableModes(DISPATCH_COORDINATOR)).toContain("courier");
  });

  it("gives a CHW outreach and offline, but not provider or supervisor", () => {
    const modes = getAvailableModes(CHW);
    expect(modes).toEqual(expect.arrayContaining(["outreach", "offline"]));
    expect(modes).not.toContain("provider");
    expect(modes).not.toContain("supervisor");
  });

  it("gives a FACILITY_ADMIN supervisor mode", () => {
    expect(getAvailableModes(FACILITY_ADMIN)).toContain("supervisor");
  });

  it("gives SUPER_ADMIN every mode, via the platform-override expansion", () => {
    const modes = getAvailableModes(["SUPER_ADMIN"]);
    expect(new Set(modes)).toEqual(new Set(Object.keys(MODE_ROLE_GROUPS) as AppMode[]));
  });

  it("does not match a lowercase spelling of a real role", () => {
    // The bug was a vocabulary mismatch, not a casing convenience. A token that
    // really did carry "clinician" is not a CLINICIAN, and must not be treated
    // as one — it would mean the trust plane changed under us unannounced.
    expect(getAvailableModes(["clinician"])).toEqual(["provider"]);
    expect(getAvailableModes(["courier"])).toEqual(["provider"]);
    expect(getAvailableModes(["community_health_worker"])).toEqual(["provider"]);
  });

  it("falls back to the ungranted provider starting point for a CITIZEN and for no roles", () => {
    // provider is a governed mode: showing the button asserts nothing until
    // useSwitchAppMode mints a duty token against a resolved work context.
    expect(getAvailableModes(CITIZEN)).toEqual(["provider"]);
    expect(getAvailableModes([])).toEqual(["provider"]);
  });
});

describe("MODE_ROLE_GROUPS", () => {
  it("names only groups that exist, and every group resolves to uppercase realm roles", () => {
    for (const [mode, groups] of Object.entries(MODE_ROLE_GROUPS)) {
      expect(groups.length, `${mode} has no role groups`).toBeGreaterThan(0);
      for (const group of groups) {
        const roles = ROLE_GROUPS[group];
        expect(roles, `${mode} references unknown role group ${group}`).toBeDefined();
        expect(roles.length).toBeGreaterThan(0);
        for (const role of roles) {
          expect(role, `${group} carries non-uppercase role ${role}`).toBe(role.toUpperCase());
        }
      }
    }
  });

  it("covers every AppMode", () => {
    const modes: AppMode[] = ["provider", "outreach", "supervisor", "offline", "courier"];
    expect(Object.keys(MODE_ROLE_GROUPS).sort()).toEqual([...modes].sort());
  });
});
