import { describe, it, expect } from "vitest";
import {
  ROUTES,
  EXPECTED_ROUTE_COUNT,
  ROUTE_COUNT,
  ZONES,
  ZONE_COUNT,
  type RouteDefinition,
  type LayoutVariant,
  type SidebarContext,
  type GuardType,
} from "../routes";

const VALID_LAYOUTS: LayoutVariant[] = ["app", "ehr", "auth", "minimal"];
const VALID_SIDEBARS: SidebarContext[] = [
  "main", "facility", "workspace", "shift", "queue",
  "ehr", "admin", "registry", "marketplace", "finance", "settings",
];
const VALID_GUARDS: GuardType[] = ["none", "auth", "facility", "workspace", "shift", "role"];

describe("Route Registry", () => {
  it("has the expected number of routes", () => {
    expect(ROUTES).toHaveLength(EXPECTED_ROUTE_COUNT);
    expect(ROUTE_COUNT).toBe(EXPECTED_ROUTE_COUNT);
    expect(ROUTES).toHaveLength(135);
  });

  it("has no duplicate paths", () => {
    const paths = ROUTES.map((r) => r.path);
    const uniquePaths = new Set(paths);
    expect(uniquePaths.size).toBe(paths.length);
  });

  it("every route has all required fields", () => {
    for (const route of ROUTES) {
      expect(route.path).toBeTruthy();
      expect(route.zone).toBeTruthy();
      expect(route.layout).toBeTruthy();
      expect(route.sidebar).toBeTruthy();
      expect(route.guard).toBeTruthy();
      expect(route.pageTitle).toBeTruthy();
      expect(route.navLabel).toBeTruthy();
    }
  });

  it("all paths start with /", () => {
    for (const route of ROUTES) {
      expect(route.path).toMatch(/^\//);
    }
  });

  it("all layouts are valid", () => {
    for (const route of ROUTES) {
      expect(VALID_LAYOUTS).toContain(route.layout);
    }
  });

  it("all sidebar contexts are valid", () => {
    for (const route of ROUTES) {
      expect(VALID_SIDEBARS).toContain(route.sidebar);
    }
  });

  it("all guards are valid", () => {
    for (const route of ROUTES) {
      expect(VALID_GUARDS).toContain(route.guard);
    }
  });

  it("role-guarded routes have a requiredRole", () => {
    const roleRoutes = ROUTES.filter((r) => r.guard === "role");
    expect(roleRoutes.length).toBeGreaterThan(0);
    for (const route of roleRoutes) {
      expect(route.requiredRole).toBeTruthy();
    }
  });

  it("non-role-guarded routes do not require a role", () => {
    const nonRoleRoutes = ROUTES.filter((r) => r.guard !== "role");
    for (const route of nonRoleRoutes) {
      expect(route.requiredRole).toBeUndefined();
    }
  });

  it("ZONES array contains the expected zone count", () => {
    expect(ZONE_COUNT).toBe(15);
    expect(ZONES).toHaveLength(15);
  });

  it("all zones referenced by routes are in the ZONES array", () => {
    for (const route of ROUTES) {
      expect(ZONES).toContain(route.zone);
    }
  });

  it("navZone values are valid when present", () => {
    const validNavZones = ["work", "professional", "life"];
    for (const route of ROUTES) {
      if (route.navZone) {
        expect(validNavZones).toContain(route.navZone);
      }
    }
  });

  it("auth routes use auth layout", () => {
    const authRoutes = ROUTES.filter((r) => r.zone === "auth" && r.path.startsWith("/auth/"));
    expect(authRoutes.length).toBeGreaterThan(0);
    for (const route of authRoutes) {
      expect(route.layout).toBe("auth");
    }
  });

  it("EHR routes use ehr layout", () => {
    const ehrRoutes = ROUTES.filter((r) => r.zone === "ehr");
    expect(ehrRoutes.length).toBeGreaterThan(0);
    for (const route of ehrRoutes) {
      expect(route.layout).toBe("ehr");
    }
  });
});
