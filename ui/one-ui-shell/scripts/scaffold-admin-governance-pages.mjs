#!/usr/bin/env node
/**
 * Scaffolds thin Administration & Governance page files for route parity.
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const APP = path.resolve(__dirname, "../src/app");
const ROUTE_REGISTRY = path.resolve(__dirname, "../src/lib/administration-governance/route-registry.ts");

const SURFACE_PAGES = [
  { route: "/work/hsc", surfaceId: "hsc" },
  { route: "/work/hsc/users", surfaceId: "hsc_users" },
  { route: "/work/hsc/establishment", surfaceId: "hsc" },
  { route: "/work/hsc/posts", surfaceId: "hsc" },
  { route: "/work/hsc/appointments", surfaceId: "hsc" },
  { route: "/work/hsc/transfers", surfaceId: "hsc" },
  { route: "/work/hsc/postings", surfaceId: "hsc" },
  { route: "/work/hsc/discipline", surfaceId: "hsc" },
  { route: "/work/hsc/audit", surfaceId: "hsc" },
  { route: "/work/hsc/scope", surfaceId: "hsc_users" },
  { route: "/work/regulators", surfaceId: "regulators" },
  { route: "/work/madi/admin", surfaceId: "madi_admin" },
  { route: "/work/madi/users", surfaceId: "madi_admin" },
  { route: "/work/madi/blood-banks", surfaceId: "madi_admin" },
  { route: "/work/madi/assignments", surfaceId: "madi_admin" },
  { route: "/work/madi/audit", surfaceId: "madi_admin" },
  { route: "/work/marketplace/organisations", surfaceId: "marketplace_orgs" },
  { route: "/work/payers", surfaceId: "payers" },
  { route: "/work/partners", surfaceId: "partners" },
  { route: "/work/system-admin", surfaceId: "system_admin" },
  { route: "/work/system-admin/users", surfaceId: "system_admin" },
  { route: "/work/system-admin/scopes", surfaceId: "system_admin" },
  { route: "/work/system-admin/keycloak", surfaceId: "system_admin" },
  { route: "/work/system-admin/tshepo", surfaceId: "system_admin" },
  { route: "/work/system-admin/opa", surfaceId: "system_admin" },
  { route: "/work/system-admin/integrations", surfaceId: "system_admin" },
  { route: "/work/system-admin/break-glass", surfaceId: "system_admin" },
  { route: "/work/system-admin/audit", surfaceId: "system_admin" },
  { route: "/work/administration-governance/access-requests", surfaceId: "access_requests" },
  { route: "/work/administration-governance/audit", surfaceId: "audit" },
  { route: "/work/administration-governance/access-review", surfaceId: "access_review" },
  { route: "/work/administration-governance/municipal", surfaceId: "municipal" },
  { route: "/work/administration-governance/private-facility", surfaceId: "private_facility" },
  { route: "/work/administration-governance/organisations", surfaceId: "organisations" },
  { route: "/work/administration-governance/organisations/new", surfaceId: "organisations" },
  { route: "/work/facility/staff-access", surfaceId: "facility_staff_access" },
];

const ONBOARD_FLOWS = [
  "citizen",
  "provider-worker",
  "public-sector-worker",
  "hsc-user",
  "municipal-user",
  "private-facility-user",
  "regulator-user",
  "madi-user",
  "marketplace-user",
  "payer-user",
  "training-user",
  "external-partner-user",
  "system-admin",
];

function routeToDir(route) {
  return path.join(APP, ...route.split("/").filter(Boolean));
}

function writeSurfacePage(dir, surfaceId) {
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, "page.tsx");
  if (fs.existsSync(file)) return;
  fs.writeFileSync(
    file,
    `"use client";

import { ScopedAdministrationSurface } from "@/components/administration-governance/ScopedAdministrationSurface";

export default function Page() {
  return <ScopedAdministrationSurface surfaceId="${surfaceId}" />;
}
`,
  );
  console.log("wrote", file);
}

function writeOnboardFlowPage(dir, flow) {
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, "page.tsx");
  if (fs.existsSync(file)) return;
  fs.writeFileSync(
    file,
    `"use client";

import { OnboardFlowWizard } from "@/components/administration-governance/OnboardFlowWizard";

export default function Page() {
  return <OnboardFlowWizard flow="${flow}" />;
}
`,
  );
  console.log("wrote", file);
}

for (const { route, surfaceId } of SURFACE_PAGES) {
  writeSurfacePage(routeToDir(route), surfaceId);
}

for (const flow of ONBOARD_FLOWS) {
  writeOnboardFlowPage(routeToDir(`/work/administration-governance/onboard/${flow}`), flow);
}

// Dynamic org/regulator/facility/marketplace routes
const dynamicPages = [
  { route: "/work/administration-governance/organisations/[orgId]", surfaceId: "organisations" },
  { route: "/work/administration-governance/organisations/[orgId]/users", surfaceId: "organisations" },
  { route: "/work/administration-governance/organisations/[orgId]/verification", surfaceId: "organisations" },
  { route: "/work/administration-governance/organisations/[orgId]/contracts", surfaceId: "organisations" },
  { route: "/work/administration-governance/organisations/[orgId]/data-sharing", surfaceId: "organisations" },
  { route: "/work/administration-governance/organisations/[orgId]/api-access", surfaceId: "organisations" },
  { route: "/work/administration-governance/organisations/[orgId]/audit", surfaceId: "organisations" },
  { route: "/work/administration-governance/access-review/[subjectId]", surfaceId: "access_review" },
  { route: "/work/regulators/[regulatorId]", surfaceId: "regulators" },
  { route: "/work/regulators/[regulatorId]/users", surfaceId: "regulators" },
  { route: "/work/regulators/[regulatorId]/cases", surfaceId: "regulators" },
  { route: "/work/regulators/[regulatorId]/audit", surfaceId: "regulators" },
  { route: "/work/regulators/[regulatorId]/registration-review", surfaceId: "regulators" },
  { route: "/work/regulators/[regulatorId]/licence-review", surfaceId: "regulators" },
  { route: "/work/regulators/[regulatorId]/cpd-review", surfaceId: "regulators" },
  { route: "/work/regulators/[regulatorId]/restrictions", surfaceId: "regulators" },
  { route: "/work/regulators/[regulatorId]/disciplinary", surfaceId: "regulators" },
  { route: "/work/marketplace/organisations/[orgId]/users", surfaceId: "marketplace_orgs" },
  { route: "/work/marketplace/organisations/[orgId]/api-access", surfaceId: "marketplace_orgs" },
  { route: "/work/marketplace/organisations/[orgId]/products", surfaceId: "marketplace_orgs" },
  { route: "/work/marketplace/organisations/[orgId]/compliance", surfaceId: "marketplace_orgs" },
  { route: "/work/marketplace/organisations/[orgId]/audit", surfaceId: "marketplace_orgs" },
  { route: "/work/payers/[orgId]/users", surfaceId: "payers" },
  { route: "/work/payers/[orgId]/claims-access", surfaceId: "payers" },
  { route: "/work/payers/[orgId]/api-access", surfaceId: "payers" },
  { route: "/work/payers/[orgId]/compliance", surfaceId: "payers" },
  { route: "/work/fundo/admin/partners", surfaceId: "training" },
  { route: "/work/fundo/admin/partners/[orgId]/users", surfaceId: "training" },
  { route: "/work/fundo/admin/courses", surfaceId: "training" },
  { route: "/work/fundo/admin/accreditation", surfaceId: "training" },
  { route: "/work/partners/[orgId]/users", surfaceId: "partners" },
  { route: "/work/partners/[orgId]/data-access", surfaceId: "partners" },
  { route: "/work/partners/[orgId]/projects", surfaceId: "partners" },
  { route: "/work/partners/[orgId]/audit", surfaceId: "partners" },
  { route: "/work/facility/[facilityId]/staff-access", surfaceId: "facility_staff_access" },
  { route: "/work/facility/[facilityId]/staff-access/add", surfaceId: "facility_staff_access" },
  { route: "/work/facility/[facilityId]/staff-access/[assignmentId]", surfaceId: "facility_staff_access" },
  { route: "/work/facility/[facilityId]/departments/[departmentId]/staff", surfaceId: "facility_staff_access" },
  { route: "/work/facility/[facilityId]/audit/access", surfaceId: "facility_staff_access" },
];

for (const { route, surfaceId } of dynamicPages) {
  writeSurfacePage(routeToDir(route), surfaceId);
}

const MAIN_ROUTES = [
  "/work/administration-governance",
  "/work/administration-governance/onboard",
];

function routeLabel(route) {
  const segments = route.split("/").filter(Boolean);
  const last = segments[segments.length - 1] ?? "page";
  if (last.startsWith("[") && segments.length > 1) {
    return segments[segments.length - 2]
      .split("-")
      .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
      .join(" ");
  }
  return last
    .replace(/^\[|\]$/g, "")
    .split("-")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

function routeTitle(route) {
  if (route === "/work/administration-governance") return "Administration & Governance";
  if (route === "/work/administration-governance/onboard") return "Onboard New Actor";
  if (route.startsWith("/work/administration-governance/onboard/")) {
    return `Onboard ${routeLabel(route)}`;
  }
  return routeLabel(route);
}

const allRoutes = [
  ...MAIN_ROUTES,
  ...SURFACE_PAGES.map((p) => p.route),
  ...ONBOARD_FLOWS.map((flow) => `/work/administration-governance/onboard/${flow}`),
  ...dynamicPages.map((p) => p.route),
];

/**
 * Order matters: matchRouteDefinition compiles `[param]` to `[^/]+` and returns the FIRST entry
 * that matches, so a dynamic segment registered above its literal siblings swallows them. Plain
 * `.sort()` put "[orgId]" before "new" and "[assignmentId]" before "add", which is exactly how
 * two routes ended up rendering under a sibling's title and nav label. Sort segment by segment
 * with literals ahead of dynamic segments so the emitted order is matchable.
 */
function compareRoutes(a, b) {
  const as = a.split("/");
  const bs = b.split("/");
  for (let i = 0; i < Math.max(as.length, bs.length); i++) {
    const x = as[i];
    const y = bs[i];
    if (x === undefined) return -1;
    if (y === undefined) return 1;
    if (x === y) continue;
    const xd = x.startsWith("[");
    const yd = y.startsWith("[");
    if (xd !== yd) return xd ? 1 : -1;
    return x < y ? -1 : 1;
  }
  return 0;
}

const uniqueRoutes = [...new Set(allRoutes)].sort(compareRoutes);

/**
 * Regeneration must be lossless.
 *
 * This file is spread into `ROUTES` (`lib/routes.ts`), and `AuthGuardProvider` denies any
 * pathname the registry does not resolve — so a route dropped from here stops rendering and
 * shows "This page is not available". Twelve entries had been hand-added to the generated
 * file (the ten `/work/vashandi/*` routes, `…/gdhcn-readiness` and
 * `…/organisations/[orgId]/data-uploads`) and were NOT derivable from the lists above, so
 * running the documented regenerate command deleted them.
 *
 * That deletion was never silent — `app-router-route-coverage` and the `EXPECTED_ROUTE_COUNT`
 * assertion both go red, and CI runs them. But a generator whose own documented command
 * produces a state the test suite rejects is a trap: the person who runs it gets a red build
 * and no explanation. So entries that exist in the current file but are not generated are
 * carried forward verbatim and reported, rather than dropped.
 */
function readExistingEntries() {
  if (!fs.existsSync(ROUTE_REGISTRY)) return new Map();
  const src = fs.readFileSync(ROUTE_REGISTRY, "utf8");
  const entries = new Map();
  for (const line of src.split("\n")) {
    const match = line.match(/^\s*\{ path: "([^"]+)".*\},\s*$/);
    if (match) entries.set(match[1], line.trimEnd());
  }
  return entries;
}

const existingEntries = readExistingEntries();
const generated = new Set(uniqueRoutes);
const preserved = [...existingEntries.keys()].filter((p) => !generated.has(p));

const lineFor = (route) => {
  const title = routeTitle(route);
  return `  { path: "${route}", zone: "operations", layout: "app", sidebar: "admin", guard: "auth", pageTitle: "${title}", navLabel: "${title}", navZone: "work" },`;
};

// Preserved entries keep their hand-written guard, title and nav label — re-emitting them from
// `routeTitle` would silently rewrite an authored `guard` back to "auth".
const allEmitted = [...uniqueRoutes, ...preserved].sort(compareRoutes);
const registryLines = allEmitted.map((route) =>
  generated.has(route) ? lineFor(route) : existingEntries.get(route),
);

fs.writeFileSync(
  ROUTE_REGISTRY,
  `/**
 * Auto-generated Administration & Governance route registry.
 * Regenerate: node scripts/scaffold-admin-governance-pages.mjs
 *
 * Entries not derivable from the scaffold lists are preserved verbatim on regeneration
 * (see "Regeneration must be lossless" in the script). Removing a route means removing it
 * from this file AND from the scaffold source, not just re-running the generator.
 */

export const ADMINISTRATION_GOVERNANCE_ROUTES = [
${registryLines.join("\n")}
] as const;

export const ADMINISTRATION_GOVERNANCE_ROUTE_COUNT = ${allEmitted.length};
`,
);

console.log(`Route registry: ${allEmitted.length} routes → ${ROUTE_REGISTRY}`);
if (preserved.length) {
  console.log(`  preserved ${preserved.length} hand-added route(s) not in the scaffold lists:`);
  for (const p of preserved) console.log(`    ${p}`);
}
console.log("Scaffold complete.");
