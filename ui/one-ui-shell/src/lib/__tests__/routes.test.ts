import { describe, it, expect } from "vitest";
import {
  ROUTES,
  EXPECTED_ROUTE_COUNT,
  ROUTE_COUNT,
  ZONES,
  ZONE_COUNT,
  buildBreadcrumbTrail,
  type LayoutVariant,
  type SidebarContext,
  type GuardType,
} from "../routes";

const VALID_LAYOUTS: LayoutVariant[] = ["app", "ehr", "auth", "minimal"];
const VALID_SIDEBARS: SidebarContext[] = [
  "main", "facility", "workspace", "shift", "queue",
  "ehr", "admin", "registry", "marketplace", "finance", "settings",
];
const VALID_GUARDS: GuardType[] = ["none", "auth", "facility", "workspace", "shift", "role", "provider"];

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
    const uniqueZones = new Set(ROUTES.map((r) => r.zone));
    expect(ZONE_COUNT).toBe(uniqueZones.size);
    expect(ZONES).toHaveLength(uniqueZones.size);
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
    const paths = [
      "/organization-admin",
      "/organization-admin/facility",
      "/organization-admin/staffing",
      "/organization-admin/governance",
      "/organization-admin/governance/[id]",
    ];
    for (const p of paths) {
      const route = ROUTES.find((r) => r.path === p);
      expect(route?.guard).toBe("role");
      expect(route?.requiredRole).toBe("ORGANIZATION_ADMIN");
    }
  });

  it("registers the IPS chart route with ehr layout and facility guard", () => {
    const route = ROUTES.find((r) => r.path === "/ehr/[patientId]/ips");
    expect(route?.layout).toBe("ehr");
    expect(route?.guard).toBe("facility");
  });

  it("registers commerce integrations and sidecar retirement in canonical shared routing", () => {
    const financeRoute = ROUTES.find((r) => r.path === "/finance/commerce-integrations");
    expect(financeRoute?.guard).toBe("role");
    expect(financeRoute?.requiredRole).toBe("FINANCE");

    const ledgerRoute = ROUTES.find((r) => r.path === "/admin/sidecar-retirement");
    expect(ledgerRoute?.guard).toBe("role");
    expect(ledgerRoute?.requiredRole).toBe("ADMIN");

    const curationRoute = ROUTES.find((r) => r.path === "/admin/clinical-curation");
    expect(curationRoute?.guard).toBe("role");
    expect(curationRoute?.requiredRole).toBe("ADMIN");
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
    const financePaths: Array<[string, string]> = [
      ["/finance/msika-governance", "MSIKA_GOVERNANCE"],
      ["/finance/settlements", "FINANCE"],
      ["/finance/remittances", "FINANCE"],
      ["/finance/reconciliation", "PAYER_OPS"],
      ["/finance/refunds", "FINANCE"],
      ["/finance/payer-ops", "PAYER_OPS"],
      ["/finance/ledger", "FINANCE"],
      ["/finance/payer-claims", "PAYER_OPS"],
      ["/finance/payer-claims/[claimId]", "PAYER_OPS"],
    ];

    for (const [path, role] of financePaths) {
      const route = ROUTES.find((r) => r.path === path);
      expect(route?.guard).toBe("role");
      expect(route?.requiredRole).toBe(role);
      expect(route?.navZone).toBe("work");
    }

    const marketplacePaths = [
      "/marketplace/catalog",
      "/marketplace/orders/[id]",
      "/marketplace/ops",
      "/marketplace/vendor",
      "/marketplace/vendor/orders",
      "/marketplace/cart",
      "/marketplace/substitutions",
      "/marketplace/pickup",
    ];

    for (const path of marketplacePaths) {
      const route = ROUTES.find((r) => r.path === path);
      expect(route?.guard).toBe("role");
      expect(route?.requiredRole).toBe("COMMERCE");
      expect(route?.navZone).toBe("work");
    }
  });

  it("registers maternity monitoring as a first-class EHR route", () => {
    const route = ROUTES.find((r) => r.path === "/ehr/[patientId]/maternity");
    expect(route?.layout).toBe("ehr");
    expect(route?.guard).toBe("facility");
    expect(route?.navZone).toBe("work");
  });

  it("scheduling hub, roster, on-call, and noticeboard use workspace guard", () => {
    for (const p of ["/scheduling", "/scheduling/roster", "/scheduling/on-call", "/scheduling/noticeboard"]) {
      const route = ROUTES.find((r) => r.path === p);
      expect(route?.guard).toBe("workspace");
    }
  });

  it("canonical COSTA hub and MusheX platform admin pages are registered with FINANCE role (Stage 3.4C / audit gaps G-1, G-2)", () => {
    // These two pages were added to `one-ui-shell` in Stage 3.2 / 3.3 but only
    // entered this central registry in Stage 3.4C housekeeping. They must
    // appear under the same FINANCE role / finance sidebar pattern as the
    // surrounding pages so that breadcrumbs and the role-guard pick them up.
    for (const path of ["/finance/costa", "/finance/mushex-platform"]) {
      const route = ROUTES.find((r) => r.path === path);
      expect(route, `route ${path} is missing from ROUTES`).toBeTruthy();
      expect(route?.guard).toBe("role");
      expect(route?.requiredRole).toBe("FINANCE");
      expect(route?.sidebar).toBe("finance");
      expect(route?.layout).toBe("app");
      expect(route?.navZone).toBe("work");
    }
  });

  it("custodial wallet detail page is registered with FINANCE role (Phase 4 / MusheX platform read-to-detail)", () => {
    // Phase 4 adds a read-only drill-down at /finance/mushex-platform/wallets/[walletId].
    // The route lives under the same FINANCE role/sidebar pattern as its parent
    // hub so guards, breadcrumbs, and the route-parity check resolve it correctly.
    const route = ROUTES.find((r) => r.path === "/finance/mushex-platform/wallets/[walletId]");
    expect(route, "custodial-wallet detail route is missing from ROUTES").toBeTruthy();
    expect(route?.guard).toBe("role");
    expect(route?.requiredRole).toBe("FINANCE");
    expect(route?.sidebar).toBe("finance");
    expect(route?.layout).toBe("app");
    expect(route?.navZone).toBe("work");
    expect(route?.pageTitle).toBe("Custodial wallet");
  });

  it("MusheX remittance/card/reversal detail pages are registered with FINANCE role (Phase 4 follow-on)", () => {
    const cases: Array<[string, string]> = [
      ["/finance/mushex-platform/remittance/[transferId]", "Remittance transfer"],
      ["/finance/mushex-platform/cards/[cardId]", "Card profile"],
      ["/finance/mushex-platform/reversals/[reversalId]", "Reversal record"],
    ];
    for (const [path, title] of cases) {
      const route = ROUTES.find((r) => r.path === path);
      expect(route, `route ${path} is missing from ROUTES`).toBeTruthy();
      expect(route?.guard).toBe("role");
      expect(route?.requiredRole).toBe("FINANCE");
      expect(route?.sidebar).toBe("finance");
      expect(route?.layout).toBe("app");
      expect(route?.navZone).toBe("work");
      expect(route?.pageTitle).toBe(title);
    }
  });

  it("COSTA encounter timeline page is registered with FINANCE role (Phase 5 / COSTA workflow maturity)", () => {
    // Phase 5 adds a read-only chronological view at /finance/costa/encounter/[encounterId].
    // The route lives under the same FINANCE role/sidebar pattern as the COSTA
    // hub so guards, breadcrumbs, and the route-parity check resolve it correctly.
    const route = ROUTES.find((r) => r.path === "/finance/costa/encounter/[encounterId]");
    expect(route, "COSTA encounter timeline route is missing from ROUTES").toBeTruthy();
    expect(route?.guard).toBe("role");
    expect(route?.requiredRole).toBe("FINANCE");
    expect(route?.sidebar).toBe("finance");
    expect(route?.layout).toBe("app");
    expect(route?.navZone).toBe("work");
    expect(route?.pageTitle).toBe("COSTA encounter timeline");
  });

  it("registers all Fundo Studio and Library routes with expected guards", () => {
    const roleProtectedStudioRoutes = [
      "/learning/studio",
      "/learning/studio/courses",
      "/learning/studio/courses/new",
      "/learning/studio/courses/[courseId]",
      "/learning/studio/courses/[courseId]/builder",
      "/learning/studio/library",
      "/learning/studio/media",
      "/learning/studio/media/recordings",
      "/learning/studio/media/scripts",
      "/learning/studio/media/voiceovers",
      "/learning/studio/media/[mediaId]",
      "/learning/studio/assessments",
      "/learning/studio/surveys",
      "/learning/studio/ai",
      "/learning/studio/publish",
      "/learning/studio/analytics",
    ];
    for (const path of roleProtectedStudioRoutes) {
      const route = ROUTES.find((r) => r.path === path);
      expect(route, `missing route ${path}`).toBeTruthy();
      expect(route?.guard).toBe("role");
      expect(route?.requiredRole).toBe("ADMIN_OR_HIE");
    }

    const learnerAccessibleRoutes = [
      "/learning/library",
      "/learning/library/resources",
      "/learning/library/uploads",
      "/learning/library/[resourceId]",
      "/learning/notifications",
      "/learning/surveys/[surveyId]",
      "/learning/surveys/[surveyId]/respond",
      "/learning/feedback/course/[courseId]",
    ];
    for (const path of learnerAccessibleRoutes) {
      const route = ROUTES.find((r) => r.path === path);
      expect(route, `missing route ${path}`).toBeTruthy();
      expect(route?.guard).toBe("auth");
    }
  });
});

describe("buildBreadcrumbTrail", () => {
  it("returns Home only for root paths", () => {
    expect(buildBreadcrumbTrail("/")).toEqual([{ href: "/home", label: "Home" }]);
    expect(buildBreadcrumbTrail("/home")).toEqual([{ href: "/home", label: "Home" }]);
  });

  it("builds a chain for nested registered paths", () => {
    const trail = buildBreadcrumbTrail("/queue/triage");
    expect(trail[0]).toEqual({ href: "/home", label: "Home" });
    expect(trail.map((t) => t.href)).toContain("/queue");
    expect(trail[trail.length - 1].href).toBe("/queue/triage");
  });
});
