#!/usr/bin/env node

/**
 * Orphan page check — the direction route-parity-check.mjs cannot see.
 *
 * WHY THIS EXISTS
 * ---------------
 * route-parity-check.mjs asserts registry -> filesystem: every route declared in routes.ts has a
 * page file. That catches a registered route with no page. It is structurally blind to the
 * opposite: a page that exists, renders, and is reachable by nothing, because it was never added
 * to the registry and so has no nav entry or sidebar link.
 *
 * That failure is invisible from every angle a developer normally looks from. The page builds. It
 * type-checks. Visiting the URL directly works, which is exactly what its author does while
 * building it. Only a user trying to NAVIGATE to it discovers it does not exist — and by then it
 * has usually shipped.
 *
 * Found by the Adult Medicine lane on their own work: `ehr/[patientId]/programmes` was built,
 * complete, and unreachable. `workspace/[specialty]` is in the same state. Their observation was
 * that a parity check running in one direction cannot report the other, which is right, and is why
 * this is a separate script rather than a flag on the existing one.
 *
 * WHAT IT DOES
 *   Walks src/app for page.tsx files, converts each to its route path, and fails on any that the
 *   registry does not declare.
 *
 * It asserts self-reach: finding zero pages, or zero registered routes, means the path assumptions
 * have drifted and the check has inspected nothing. That fails rather than passing quietly — a
 * check that matches nothing reports the same "no findings" as a clean tree.
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { ORPHAN_PAGE_BASELINE } from "./orphan-page-baseline.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const APP_DIR = path.resolve(__dirname, "../src/app");
const ROUTES_TS = path.resolve(__dirname, "../src/lib/routes.ts");
const ADMIN_ROUTES_TS = path.resolve(__dirname, "../src/lib/administration-governance/route-registry.ts");

/**
 * Route groups `(name)` and private folders `_name` do not appear in the URL; `@slot` is a parallel
 * route and has no URL of its own. Treating any of these as path segments would invent routes that
 * do not exist and bury the real findings.
 */
function toRoutePath(pageFile) {
  const rel = path.relative(APP_DIR, path.dirname(pageFile));
  if (rel === "") return "/";
  const segments = rel
    .split(path.sep)
    .filter((s) => !(s.startsWith("(") && s.endsWith(")")))
    .filter((s) => !s.startsWith("_"))
    .filter((s) => !s.startsWith("@"));
  return "/" + segments.join("/");
}

function walk(dir, out = []) {
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

function extractPaths(source) {
  return [...source.matchAll(/path:\s*"([^"]+)"/g)].map((m) => m[1]);
}

/**
 * Public pages have a different reachability mechanism: middleware.ts declares them in PUBLIC_PREFIXES, and the public
 * header/footer link them. They are deliberately absent from the app route registry, so measuring
 * them against it reports ~50 findings that are not defects — and a check that cries wolf is one
 * people learn to skip. The list is READ from middleware rather than copied here; a duplicate would
 * drift and this check would start lying in the other direction.
 */
const MIDDLEWARE_TS = path.resolve(__dirname, "../src/middleware.ts");
function publicPrefixes() {
  if (!fs.existsSync(MIDDLEWARE_TS)) return [];
  const src = fs.readFileSync(MIDDLEWARE_TS, "utf8");
  const block = src.match(/PUBLIC_PREFIXES\s*(?::[^=]*)?=\s*\[([\s\S]*?)\]/);
  if (!block) return [];
  return [...block[1].matchAll(/"(\/[^"]*)"/g)].map((m) => m[1]);
}

const routesSource = fs.readFileSync(ROUTES_TS, "utf8");
const adminSource = fs.existsSync(ADMIN_ROUTES_TS) ? fs.readFileSync(ADMIN_ROUTES_TS, "utf8") : "";
const registered = new Set([...extractPaths(routesSource), ...extractPaths(adminSource)]);

const pages = walk(APP_DIR);
const pageRoutes = pages.map(toRoutePath);

// Self-reach before any verdict.
if (pages.length === 0 || registered.size === 0) {
  console.error(
    `ORPHAN-PAGE CHECK FAILED: scanned ${pages.length} pages against ${registered.size} registered routes.`,
  );
  console.error("  Matching nothing is a broken check, not a clean tree.");
  process.exit(1);
}

/**
 * Routes that are legitimately unregistered. Each needs a reason, not just a path — an allowlist
 * without reasons becomes the place findings go to be forgotten.
 */
const ALLOWED = [
  { path: "/", reason: "root redirect; the registry describes destinations, not the entry point" },
];
const allowedPaths = new Set(ALLOWED.map((a) => a.path));

const PUBLIC = publicPrefixes();
if (PUBLIC.length === 0) {
  console.error("ORPHAN-PAGE CHECK FAILED: could not read PUBLIC_PREFIXES from middleware.ts.");
  console.error("  Without it every public page reports as an orphan; failing rather than guessing.");
  process.exit(1);
}
const isPublic = (route) => PUBLIC.some((p) => route === p || route.startsWith(p + "/"));

const baseline = new Set(ORPHAN_PAGE_BASELINE);
const orphans = [];
const known = [];
for (let i = 0; i < pages.length; i++) {
  const route = pageRoutes[i];
  if (registered.has(route) || allowedPaths.has(route) || isPublic(route)) continue;
  if (baseline.has(route)) { known.push(route); continue; }
  orphans.push({ route, file: path.relative(path.resolve(__dirname, ".."), pages[i]) });
}

// A baseline entry that is no longer an orphan has been fixed — drop it, so the list can only
// shrink and cannot quietly start covering something new.
const stale = ORPHAN_PAGE_BASELINE.filter((r) => !known.includes(r));
if (stale.length > 0) {
  console.log(`ORPHAN-PAGE CHECK: ${stale.length} baseline entr(ies) now registered — remove from`);
  console.log(`  scripts/orphan-page-baseline.mjs: ${stale.join(", ")}`);
}

if (orphans.length > 0) {
  console.error(
    `ORPHAN-PAGE CHECK FAILED: ${orphans.length} page(s) exist but are not in the route registry.`,
  );
  console.error("They build, they type-check, and visiting the URL directly works —");
  console.error("but nothing links to them, so no user can navigate there.\n");
  for (const o of orphans.slice(0, 40)) {
    console.error(`  ${o.route}`);
    console.error(`      ${o.file}`);
  }
  if (orphans.length > 40) console.error(`  ... and ${orphans.length - 40} more`);
  console.error("\nRegister each in src/lib/routes.ts (path, zone, layout, sidebar, guard,");
  console.error("pageTitle, navLabel), or delete the page if it was superseded.");
  process.exit(1);
}

console.log(
  `ORPHAN-PAGE CHECK PASSED (${pages.length} pages; ${known.length} known-unregistered in the ` +
    `2026-07-28 baseline, 0 new)`,
);
