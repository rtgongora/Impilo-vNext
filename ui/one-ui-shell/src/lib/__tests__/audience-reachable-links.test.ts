import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { matchRouteDefinition } from "../routes";
import { matchesRequiredRole } from "../auth/role-groups";

/**
 * A link is a promise that the destination will open.
 *
 * `AuthGuardProvider` answers a failed `role` guard with `router.replace("/home")` — no
 * message, no DENIED state. So a surface that offers an audience a route their roles do not
 * admit does not show them an error; it shows them a flash and returns them where they
 * started, which reads as "the page is broken".
 *
 * That is exactly how the citizen home rail linked "Coverage" at `/coverage` — the ADMIN
 * payer console. Every citizen who clicked it was bounced home.
 *
 * Each surface below is pinned to the audience that actually reaches it — taken from that
 * surface's own guard in `routes.ts`, or from the component's render condition — and every
 * internal link it offers must be reachable by that audience.
 *
 * A link may be exempted only for a stated reason. Two are legitimate:
 *   - the surface filters its own list at render (`reachableByRole`), which this test then
 *     verifies is actually imported rather than taking the claim on trust;
 *   - the individual link sits behind a role condition in JSX that this static scan cannot see.
 */

const SHELL_SRC = resolve(__dirname, "../..");

interface Surface {
  /** Path relative to src/. */
  file: string;
  /** Why this audience — the guard or render condition that admits them. */
  because: string;
  /** Concrete Keycloak roles a member of this audience may hold; each is probed alone. */
  audience: string[];
  /** href → why it is allowed to be unreachable by part of the audience. */
  exempt?: Record<string, string>;
  /** True when the surface filters its destination list at render. Verified, not trusted. */
  filtersAtRender?: boolean;
}

const SURFACES: Surface[] = [
  {
    file: "components/home/CitizenQuickAccessRail.tsx",
    because: "the person's own home rail — a citizen with no work roles",
    audience: ["CITIZEN"],
  },
  {
    file: "components/clinical/ClinicalFinanceContextStrip.tsx",
    because: "renders when isClinical || isFinance || isAdmin",
    audience: ["CLINICIAN", "NURSE", "FINANCE"],
    exempt: {
      "/finance/workspace": "rendered only inside {isFinance && …}",
    },
  },
  {
    file: "app/enterprise/oversight/page.tsx",
    because: '/enterprise/oversight carries guard "auth" — every signed-in person',
    audience: ["CITIZEN", "CLINICIAN", "FINANCE"],
    filtersAtRender: true,
    exempt: {
      "/public-health": "OVERSIGHT_LINKS filtered by reachableByRole",
      "/public-health/surveillance": "OVERSIGHT_LINKS filtered by reachableByRole",
      "/operations/facility-operations/district-view": "OVERSIGHT_LINKS filtered by reachableByRole",
    },
  },
  {
    file: "components/enterprise/NationalRevenueOversightPanel.tsx",
    because: 'hosted on /enterprise/oversight (guard "auth")',
    audience: ["CITIZEN", "CLINICIAN", "FINANCE"],
    filtersAtRender: true,
    exempt: {
      "/finance/reports": "FINANCE_LINKS filtered by reachableByRole",
      "/finance/settlements": "FINANCE_LINKS filtered by reachableByRole",
    },
  },
  {
    file: "app/finance/payer-ops/page.tsx",
    because: "/finance/payer-ops requires role group PAYER_OPS",
    audience: ["FINANCE"],
  },
  // Deliberately NOT covered here: app/api/mobile/provider/hubs/[hub]/route.ts. It is a
  // multi-hub catalogue whose hubs have different audiences, so "which audience is offered this
  // link" has no single answer to assert against.
  //
  // Its links ARE now filtered by role, but not here and not in this language: that handler is
  // shadowed by the `/api/:path*` rewrite in next.config.mjs and never executes. The catalogue
  // the mobile app receives is composed in the experience BFF, which filters it through
  // RouteGuardRegistry against `route-guards.generated.json` — a generated projection of this
  // same route registry. The guard that keeps that projection honest is
  // `npm run test:route-guard-export`; the filtering itself is covered by
  // RouteGuardRegistryTest and ProviderMobileHubServiceRoleFilterTest in services/experience-bff.
];

/** `href="/x"`, `href: "/x"` and `web_path: "/x"` — the three forms the shell uses. */
const LINK_PATTERN = /(?:href|web_path)\s*[:=]\s*"(\/[^"?#]*)/g;

function sourceOf(file: string): string {
  return readFileSync(resolve(SHELL_SRC, file), "utf8");
}

function internalLinks(source: string): string[] {
  return [...new Set([...source.matchAll(LINK_PATTERN)].map((m) => m[1]))];
}

/** The role dimension only — this is what AuthGuardProvider's `case "role"` enforces. */
function roleGuardRefuses(href: string, role: string): string | null {
  const route = matchRouteDefinition(href);
  if (!route || route.guard !== "role" || !route.requiredRole) return null;
  return matchesRequiredRole((r) => r === role, route.requiredRole) ? null : route.requiredRole;
}

describe("links resolve for the audience that is offered them", () => {
  for (const surface of SURFACES) {
    it(`${surface.file} — ${surface.because}`, () => {
      const source = sourceOf(surface.file);
      const unreachable: string[] = [];

      for (const href of internalLinks(source)) {
        if (surface.exempt?.[href]) continue;
        for (const role of surface.audience) {
          const required = roleGuardRefuses(href, role);
          if (required) unreachable.push(`${href} requires ${required}, refused for ${role}`);
        }
      }

      expect(unreachable, "these links bounce this audience back to /home").toEqual([]);

      // An exemption that claims render-time filtering has to actually do it.
      if (surface.filtersAtRender) {
        expect(source, `${surface.file} claims to filter but does not`).toContain("reachableByRole");
      }
    });
  }

  it("fails when the original defect is reintroduced", () => {
    // The negative control: this is the exact shape of the bug that was shipped — a citizen
    // surface offering the ADMIN payer console. If this stops being refused, the guard above
    // has gone blind and every assertion in this file is worthless.
    expect(roleGuardRefuses("/coverage", "CITIZEN")).toBe("ADMIN");
    expect(roleGuardRefuses("/coverage", "CLINICIAN")).toBe("ADMIN");

    // …and the replacements admit the audiences they are now offered to.
    expect(roleGuardRefuses("/ruvimbo/member", "CITIZEN")).toBeNull();
    expect(roleGuardRefuses("/ruvimbo", "CLINICIAN")).toBeNull();
    expect(roleGuardRefuses("/ruvimbo/payer", "FINANCE")).toBeNull();
    expect(roleGuardRefuses("/ruvimbo/provider", "CLINICIAN")).toBeNull();
  });
});
