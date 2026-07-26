import { describe, it, expect } from "vitest";
import { ROUTES, matchRouteDefinition } from "../routes";

/**
 * The ED child routes must resolve to a route definition, because that is what guards them.
 *
 * `AuthGuardProvider` calls `matchRouteDefinition(pathname)` and **returns early when it is null** —
 * no facility check, no role check, no consent gate. `matchRouteDefinition` anchors every pattern
 * with `^...$`, so the registered `/clinical/emergency` never matched
 * `/clinical/emergency/resus/<id>`. All three ED child routes therefore shipped with no guard at
 * all: the resuscitation workspace, the cross-service episode timeline and the ED visit journey.
 *
 * An unregistered route is an unguarded route, and it fails silently in the direction that matters —
 * the page renders, so nothing looks broken.
 */
describe("emergency route registration", () => {
  const CHILD_ROUTES = [
    { pathname: "/clinical/emergency/resus/act-123", expectedPath: "/clinical/emergency/resus/[activationId]" },
    { pathname: "/clinical/emergency/episode/ep-456", expectedPath: "/clinical/emergency/episode/[episodeId]" },
    { pathname: "/clinical/emergency/visit-789", expectedPath: "/clinical/emergency/[visitId]" },
  ];

  it.each(CHILD_ROUTES)("$pathname resolves to $expectedPath", ({ pathname, expectedPath }) => {
    const match = matchRouteDefinition(pathname);
    expect(match, `${pathname} must resolve to a route definition or it runs unguarded`).not.toBeNull();
    expect(match?.path).toBe(expectedPath);
  });

  it.each(CHILD_ROUTES)("$pathname is guarded, not open", ({ pathname }) => {
    const match = matchRouteDefinition(pathname);
    // A clinical workspace showing patient data must at minimum require an established facility
    // context. "none" or "auth" alone would be a regression.
    expect(match?.guard).toBe("facility");
  });

  it("the ED trackboard itself still resolves", () => {
    expect(matchRouteDefinition("/clinical/emergency")?.path).toBe("/clinical/emergency");
  });

  /**
   * The ordering trap. A dynamic segment compiles to `[^/]+`, so `/clinical/emergency/[visitId]`
   * matches ANY single child segment. `matchRouteDefinition` returns the first match in array order,
   * so a literal child registered after it would be swallowed and silently inherit the ED-visit
   * page title, nav label and guard. This pack will add `/clinical/emergency/offline`, so the
   * invariant is pinned now rather than discovered then.
   */
  it("every literal emergency child is registered above the [visitId] catch-all", () => {
    const indexOf = (path: string) => ROUTES.findIndex((r) => r.path === path);
    const visitIdIndex = indexOf("/clinical/emergency/[visitId]");
    expect(visitIdIndex).toBeGreaterThan(-1);

    const literalChildren = ROUTES.filter(
      (r) =>
        r.path.startsWith("/clinical/emergency/") &&
        r.path !== "/clinical/emergency/[visitId]" &&
        !r.path.slice("/clinical/emergency/".length).includes("/") &&
        !r.path.includes("["),
    );

    for (const child of literalChildren) {
      expect(
        indexOf(child.path),
        `${child.path} is registered after /clinical/emergency/[visitId] and will be swallowed by it`,
      ).toBeLessThan(visitIdIndex);
    }
  });

  it("a two-segment child is not swallowed by the single-segment [visitId] pattern", () => {
    // Guards the regex itself: [^/]+ must not cross a slash, or resus/<id> would resolve to the
    // ED-visit page and the resuscitation workspace would be unreachable through the registry.
    expect(matchRouteDefinition("/clinical/emergency/resus/act-1")?.path).toBe(
      "/clinical/emergency/resus/[activationId]",
    );
  });
});
