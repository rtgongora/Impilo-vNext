import { describe, it, expect } from "vitest";
import { matchRouteDefinition } from "../routes";

/**
 * Phase 1 — Impilo Fundo workspace registration.
 *
 * The /learning hub previously had a page file but no entry in the canonical
 * route registry, which meant `AuthGuardProvider` and breadcrumbs silently
 * skipped it. This test pins the new registry entries so that regression
 * is caught immediately.
 */
describe("Impilo Fundo route registration", () => {
  it("registers /learning under the professional navZone with Impilo Fundo branding", () => {
    const route = matchRouteDefinition("/learning");
    expect(route).not.toBeNull();
    expect(route?.navZone).toBe("professional");
    expect(route?.pageTitle).toBe("Impilo Fundo");
    expect(route?.navLabel).toBe("Impilo Fundo");
    expect(route?.guard).toBe("auth");
    expect(route?.layout).toBe("app");
  });

  it("registers /learning/catalog as a non-null route under the professional navZone", () => {
    const route = matchRouteDefinition("/learning/catalog");
    expect(route).not.toBeNull();
    expect(route?.navZone).toBe("professional");
    expect(route?.pageTitle).toBe("Impilo Fundo Catalogue");
    expect(route?.guard).toBe("auth");
  });

  it("does not assign requiredRole to the Impilo Fundo routes (guard 'auth' only)", () => {
    const learning = matchRouteDefinition("/learning");
    const catalog = matchRouteDefinition("/learning/catalog");
    expect(learning?.requiredRole).toBeUndefined();
    expect(catalog?.requiredRole).toBeUndefined();
  });
});
