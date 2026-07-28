#!/usr/bin/env node

/**
 * Route Parity Test Script
 *
 * Asserts that the Next.js App Router file tree contains page files
 * for all routes defined in the route registry (src/lib/routes.ts).
 *
 * Paths are extracted from routes.ts at runtime — do not maintain a duplicate list.
 */

import { execSync } from "child_process";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const APP_DIR = path.resolve(__dirname, "../src/app");
const ROUTES_TS = path.resolve(__dirname, "../src/lib/routes.ts");
const ADMIN_ROUTES_TS = path.resolve(__dirname, "../src/lib/administration-governance/route-registry.ts");

function extractPaths(source) {
  return [...source.matchAll(/path:\s*"([^"]+)"/g)].map((m) => m[1]);
}

const routesSource = fs.readFileSync(ROUTES_TS, "utf8");
const adminRoutesSource = fs.existsSync(ADMIN_ROUTES_TS) ? fs.readFileSync(ADMIN_ROUTES_TS, "utf8") : "";
const EXPECTED_ROUTES = [...extractPaths(routesSource), ...extractPaths(adminRoutesSource)];

const countMatch = routesSource.match(/export const EXPECTED_ROUTE_COUNT = (\d+)/);
const expectedCount = countMatch ? Number(countMatch[1]) : null;

if (expectedCount !== null && EXPECTED_ROUTES.length !== expectedCount) {
  const extracted = EXPECTED_ROUTES.length;
  const drift = extracted - expectedCount;

  console.error(
    `\nRoute count mismatch: routes.ts + the admin registry contain ${extracted} paths, ` +
      `but EXPECTED_ROUTE_COUNT says ${expectedCount}.`,
  );

  // Naming the likely cause matters more than reporting the delta. The obvious response to a bare
  // mismatch is to bump the number until it agrees — which is right when you added a route and
  // forgot, and catastrophic when a route went missing, because it converts a lost route into a
  // green check.
  //
  // The case that produced this guidance (2026-07-28): two lanes each added one route to their own
  // branch and each set the count to 810. Git saw an IDENTICAL line on both sides and auto-merged
  // it without conflict, while the merged ROUTES array had gained BOTH entries — really 811. The
  // count survived the merge looking untouched while what it counts changed underneath. It was
  // caught only because an adjacent comment block happened to collide.
  if (drift > 0) {
    console.error(
      `\n  ${drift} more route(s) exist than are declared. If you have just merged, this is the` +
        `\n  signature of two lanes independently adding routes and independently setting the same` +
        `\n  count: git merges the identical line cleanly and both additions survive.`,
    );
    console.error(
      `\n  Re-derive the number from the file — do not carry your pre-merge value across a merge.`,
    );
  } else {
    console.error(
      `\n  ${-drift} declared route(s) are missing from the file. Something was REMOVED. Do not` +
        `\n  lower the count to make this pass until you know which routes went and why — a route` +
        `\n  dropped in a merge looks exactly like this, and lowering the number hides it for good.`,
    );
  }

  // The decisive help: which routes actually arrived, versus the merge base.
  try {
    const base = execSync("git merge-base HEAD @{u} 2>/dev/null || git rev-parse HEAD~1", {
      cwd: path.resolve(__dirname, "../../.."),
      encoding: "utf8",
    }).trim();
    const before = execSync(`git show ${base}:ui/one-ui-shell/src/lib/routes.ts`, {
      cwd: path.resolve(__dirname, "../../.."),
      encoding: "utf8",
      maxBuffer: 20 * 1024 * 1024,
    });
    const beforePaths = new Set(extractPaths(before));
    const added = extractPaths(routesSource).filter((r) => !beforePaths.has(r));
    if (added.length > 0) {
      console.error(`\n  Routes present now that were not at ${base.slice(0, 9)}:`);
      for (const r of added.slice(0, 20)) console.error(`    + ${r}`);
      if (added.length > 20) console.error(`    ... and ${added.length - 20} more`);
      console.error(`\n  Set EXPECTED_ROUTE_COUNT = ${extracted} only if every one of those is intended.`);
    }
  } catch {
    // No git, no upstream, or a shallow clone — the guidance above stands without the diff.
  }

  process.exit(1);
}

function routeToPagePath(route) {
  if (route === "/") return path.join(APP_DIR, "page.tsx");
  return path.join(APP_DIR, ...route.split("/").filter(Boolean), "page.tsx");
}

let passed = 0;
let failed = 0;
const missing = [];

for (const route of EXPECTED_ROUTES) {
  const pagePath = routeToPagePath(route);
  if (fs.existsSync(pagePath)) {
    passed++;
  } else {
    failed++;
    missing.push(route);
  }
}

console.log(`\nRoute Parity Check Results`);
console.log(`========================`);
console.log(`Total expected routes: ${EXPECTED_ROUTES.length}`);
console.log(`Pages found:          ${passed}`);
console.log(`Pages missing:        ${failed}`);

if (missing.length > 0) {
  console.log(`\nMissing routes:`);
  for (const route of missing) {
    console.log(`  - ${route}`);
  }
  process.exit(1);
} else {
  console.log(`\nAll routes have corresponding page files.`);
  process.exit(0);
}
