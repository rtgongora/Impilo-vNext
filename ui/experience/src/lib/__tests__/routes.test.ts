import { describe, it, expect } from "vitest";
import {
  ROUTES,
  EXPECTED_ROUTE_COUNT,
  ROUTE_COUNT,
  ZONES,
  ZONE_COUNT,
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

  it("registry administration plane entry routes require REGISTRY_ADMIN", () => {
    const paths = ["/registry-admin", "/registry/clients", "/registry/trust"];
    for (const p of paths) {
      const route = ROUTES.find((r) => r.path === p);
      expect(route?.guard).toBe("role");
      expect(route?.requiredRole).toBe("REGISTRY_ADMIN");
    }
  });

  it("identity and federation admin surfaces use ADMIN_OR_HIE for HIE reachability", () => {
    const paths = ["/id-services", "/admin/federation", "/admin/keys", "/admin/consent"];
    for (const p of paths) {
      const route = ROUTES.find((r) => r.path === p);
      expect(route?.guard).toBe("role");
      expect(route?.requiredRole).toBe("ADMIN_OR_HIE");
    }
  });

  it("organization administration hubs require ORGANIZATION_ADMIN", () => {
    const paths = ["/organization-admin", "/organization-admin/facility", "/organization-admin/staffing"];
    for (const p of paths) {
      const route = ROUTES.find((r) => r.path === p);
      expect(route?.guard).toBe("role");
      expect(route?.requiredRole).toBe("ORGANIZATION_ADMIN");
    }
  });

  it("registers the IPS chart route with ehr layout and shift guard", () => {
    const route = ROUTES.find((r) => r.path === "/ehr/[patientId]/ips");
    expect(route?.layout).toBe("ehr");
    expect(route?.guard).toBe("shift");
  });

  it("registers commerce integrations and sidecar retirement in canonical shared routing", () => {
    const financeRoute = ROUTES.find((r) => r.path === "/finance/commerce-integrations");
    expect(financeRoute?.guard).toBe("role");
    expect(financeRoute?.requiredRole).toBe("FINANCE");

    const ledgerRoute = ROUTES.find((r) => r.path === "/admin/sidecar-retirement");
    expect(ledgerRoute?.guard).toBe("role");
    expect(ledgerRoute?.requiredRole).toBe("ADMIN");
  });

  it("registers citizen self-service and public share claim routes in canonical shared routing", () => {
    const citizenPaths = [
      "/citizen",
      "/citizen/health-id/qr",
      "/citizen/health-id/request",
      "/citizen/id-recovery",
      "/citizen/delegated-pickup",
    ];

    for (const p of citizenPaths) {
      const route = ROUTES.find((r) => r.path === p);
      expect(route?.layout).toBe("app");
      expect(route?.guard).toBe("auth");
      expect(route?.navZone).toBe("life");
    }

    const shareClaimRoute = ROUTES.find((r) => r.path === "/share/claim");
    expect(shareClaimRoute?.layout).toBe("app");
    expect(shareClaimRoute?.guard).toBe("none");
    expect(shareClaimRoute?.navZone).toBe("life");

    const verifyCredentialRoute = ROUTES.find((r) => r.path === "/verify/credential");
    expect(verifyCredentialRoute?.layout).toBe("app");
    expect(verifyCredentialRoute?.guard).toBe("none");
    expect(verifyCredentialRoute?.navZone).toBe("life");
  });

  it("registers absorbed marketplace and finance operations routes in canonical shared routing", () => {
    const financePaths = [
      "/finance/settlements",
      "/finance/reconciliation",
      "/finance/refunds",
      "/finance/payer-ops",
    ];

    for (const p of financePaths) {
      const route = ROUTES.find((r) => r.path === p);
      expect(route?.guard).toBe("role");
      expect(route?.requiredRole).toBe("FINANCE");
      expect(route?.navZone).toBe("work");
    }

    const marketplacePaths = [
      "/marketplace/ops",
      "/marketplace/vendor",
      "/marketplace/vendor/orders",
    ];

    for (const p of marketplacePaths) {
      const route = ROUTES.find((r) => r.path === p);
      expect(route?.guard).toBe("role");
      expect(route?.requiredRole).toBe("FINANCE");
      expect(route?.navZone).toBe("work");
    }
  });

  it("scheduling hub, roster, on-call, and noticeboard use workspace guard", () => {
    for (const p of ["/scheduling", "/scheduling/roster", "/scheduling/on-call", "/scheduling/noticeboard"]) {
      const route = ROUTES.find((r) => r.path === p);
      expect(route?.guard).toBe("workspace");
    }
  });
});
