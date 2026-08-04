/**
 * App-router route coverage — every page must be reachable, and must be itself.
 *
 * WHY THIS EXISTS
 * ---------------
 * `AuthGuardProvider` denies by default (item 73): a pathname that `matchRouteDefinition` does not
 * resolve, and that is not on the public surface, renders the UnregisteredRouteDenied panel
 * instead of the page. That rule is correct — before it, an unregistered page ran with NO auth,
 * facility, role or citizen check at all — but it turns "forgot to register the route" from a
 * missing nav entry into a page nobody can open. 69 pages were in exactly that state, including
 * /professional/request-access, which /home links to.
 *
 * There is no local signal for it. The page builds, type-checks, and renders in isolation; only a
 * user opening it in the real shell sees the denial. So it is asserted here, in the suite CI runs.
 *
 * WHAT IT ASSERTS
 *   1. Every app-router page resolves to a registry entry OR sits on the public surface.
 *   2. Every page that has a registry entry resolves to ITS OWN entry — not to a sibling whose
 *      dynamic segment swallowed it. `matchRouteDefinition` anchors each pattern with ^…$ and
 *      returns the first match, so "/registry/facilities/[id]" registered above
 *      "/registry/facilities/new" makes the create page render as "Facility Profile".
 *   3. Every registry entry likewise resolves to itself, so no declared guard is silently
 *      replaced by an earlier entry's.
 *
 * Self-reach is asserted first: a walk that finds no pages, or a registry that resolves nothing,
 * reports the same "no findings" as a clean tree.
 */

import fs from "fs";
import path from "path";
import { describe, expect, it } from "vitest";
import { ROUTES, matchRouteDefinition } from "../routes";
import { isPublicSurface } from "../public-surface";

const APP_DIR = path.resolve(__dirname, "../../app");

/**
 * Route groups `(name)`, private folders `_name` and parallel-route slots `@slot` do not appear in
 * the URL. Treating them as segments would invent routes that do not exist and bury real findings.
 */
function toRoutePath(pageFile: string): string {
  const rel = path.relative(APP_DIR, path.dirname(pageFile));
  if (rel === "") return "/";
  const segments = rel
    .split(path.sep)
    .filter((s) => !(s.startsWith("(") && s.endsWith(")")))
    .filter((s) => !s.startsWith("_"))
    .filter((s) => !s.startsWith("@"));
  return "/" + segments.join("/");
}

function walk(dir: string, out: string[] = []): string[] {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "node_modules" || entry.name === ".next") continue;
      walk(full, out);
    } else if (entry.name === "page.tsx" || entry.name === "page.ts") {
      out.push(full);
    }
  }
  return out;
}

/** A concrete URL for a registered pattern, so the matcher can be asked to resolve it. */
function concreteForm(routePath: string): string {
  return routePath.replace(/\[(\w+)\]/g, "sample-id");
}

const pageFiles = walk(APP_DIR);
const pageRoutes = [...new Set(pageFiles.map(toRoutePath))].sort();
const registeredPaths = new Set(ROUTES.map((r) => r.path));

describe("app-router route coverage", () => {
  it("inspected something (self-reach)", () => {
    expect(pageFiles.length).toBeGreaterThan(500);
    expect(ROUTES.length).toBeGreaterThan(500);
    // Positive control on the instrument: a path that IS registered must resolve, and an
    // invented one must not. If these flip, the assertions below are measuring nothing.
    expect(matchRouteDefinition("/home")).not.toBeNull();
    expect(matchRouteDefinition("/definitely-not-a-route-zzz")).toBeNull();
  });

  it("every page resolves to a registry entry or the public surface", () => {
    const denied = pageRoutes.filter(
      (route) => !matchRouteDefinition(route) && !isPublicSurface(route),
    );
    expect(
      denied,
      `${denied.length} page(s) exist but resolve to nothing, so AuthGuardProvider renders ` +
        `"This page is not available" instead of the page. Add an entry to src/lib/routes.ts ` +
        `with a deliberate guard, or delete the page if it was superseded:\n  ` +
        denied.join("\n  "),
    ).toEqual([]);
  });

  it("no page renders under a sibling's route definition", () => {
    const shadowed = pageRoutes
      .filter((route) => !registeredPaths.has(route))
      .map((route) => ({ route, resolvesTo: matchRouteDefinition(route)?.path ?? null }))
      .filter((row) => row.resolvesTo !== null);
    expect(
      shadowed,
      `Page(s) with no entry of their own that a dynamic sibling swallowed. They render, but ` +
        `with another route's title, nav label and GUARD. Register each one ABOVE the dynamic ` +
        `sibling:\n  ` +
        shadowed.map((s) => `${s.route} -> ${s.resolvesTo}`).join("\n  "),
    ).toEqual([]);
  });

  it("every registry entry resolves to itself", () => {
    const shadowed = ROUTES.map((route) => ({
      path: route.path,
      resolvesTo: matchRouteDefinition(concreteForm(route.path))?.path ?? null,
    })).filter((row) => row.resolvesTo !== row.path);
    expect(
      shadowed,
      `Registry entr(ies) that an EARLIER entry matches first, so their declared guard and page ` +
        `identity never apply. matchRouteDefinition is first-match-wins: move each literal above ` +
        `its dynamic sibling:\n  ` +
        shadowed.map((s) => `${s.path} -> ${s.resolvesTo}`).join("\n  "),
    ).toEqual([]);
  });
});
