import { describe, expect, it } from "vitest";
import { getModuleCategories, type WorkSurfaceRoleFlags } from "./workSurfaceModules";
import { matchRouteDefinition } from "@/lib/routes";

/**
 * workSurfaceModules.ts holds label/icon/route PRESENTATION for /home's navigation grid — the
 * role flags gating which modules appear (roles.isClinical etc.) come from useRoleGroup(),
 * itself derived from the authenticated JWT's roles (useAuthStore().hasRole), not anything a
 * client can edit. This test is the concrete proof of the doctrine claim "editing the client
 * registry can never create access" (Phase F7): every module's href must resolve to a REAL,
 * GUARDED route in routes.ts — this file cannot itself open a door, because every door here is
 * independently locked downstream (routes.ts guard + AuthGuardProvider + backend authz).
 *
 * A full capability-ID-driven rearchitecture (TSHEPO issuing the authorised module list, this
 * file resolving presentation only — the same pattern lib/work-home/section-registry.ts already
 * uses) needs a new BFF surface and touches /home/page.tsx, the highest-risk file in the app
 * (2093 lines, the most-hit route). That is deliberately not attempted here — this test is the
 * safe, verifiable substitute: it proves the CURRENT architecture's safety claim rather than
 * assuming it.
 */

const ALL_ROLE_COMBINATIONS: WorkSurfaceRoleFlags[] = (() => {
  const combos: WorkSurfaceRoleFlags[] = [];
  for (let bits = 0; bits < 16; bits++) {
    combos.push({
      isClinical: !!(bits & 1),
      isAdmin: !!(bits & 2),
      isFinance: !!(bits & 4),
      isDispenser: !!(bits & 8),
    });
  }
  return combos;
})();

function allHrefsAcrossEveryRoleCombination(): Set<string> {
  const hrefs = new Set<string>();
  for (const roles of ALL_ROLE_COMBINATIONS) {
    for (const category of getModuleCategories(roles)) {
      for (const item of category.modules) {
        hrefs.add(item.href);
      }
    }
  }
  return hrefs;
}

describe("workSurfaceModules — presentation only, never an access grant (Phase F7)", () => {
  it("has at least one module across the role combinations (sanity: the enumeration itself works)", () => {
    expect(allHrefsAcrossEveryRoleCombination().size).toBeGreaterThan(10);
  });

  it("every module href resolves to a route registered in routes.ts", () => {
    const unmatched: string[] = [];
    for (const href of allHrefsAcrossEveryRoleCombination()) {
      const pathname = href.split("?")[0];
      if (!matchRouteDefinition(pathname)) {
        unmatched.push(href);
      }
    }
    expect(unmatched).toEqual([]);
  });

  it("no module href resolves to an unguarded (guard: \"none\") route", () => {
    // guard:"none" is reserved for public/pre-auth surfaces (login, welcome, etc.) — a work
    // surface module pointing at one would mean /home is presenting a "capability" that isn't
    // actually protected by anything, the exact failure mode this file must never cause.
    const openRoutes: string[] = [];
    for (const href of allHrefsAcrossEveryRoleCombination()) {
      const pathname = href.split("?")[0];
      const route = matchRouteDefinition(pathname);
      if (route?.guard === "none") {
        openRoutes.push(`${href} -> ${route.path} (guard: none)`);
      }
    }
    expect(openRoutes).toEqual([]);
  });

  it("a role-gated module (e.g. Admin Console) disappears entirely when the role flag is false — not merely relabelled", () => {
    const withAdmin = getModuleCategories({ isClinical: false, isAdmin: true, isFinance: false, isDispenser: false });
    const withoutAdmin = getModuleCategories({ isClinical: false, isAdmin: false, isFinance: false, isDispenser: false });

    const adminHrefs = new Set(withAdmin.flatMap((c) => c.modules.map((m) => m.href)));
    const noAdminHrefs = new Set(withoutAdmin.flatMap((c) => c.modules.map((m) => m.href)));

    expect(adminHrefs.has("/admin")).toBe(true);
    expect(noAdminHrefs.has("/admin")).toBe(false);
  });
});
