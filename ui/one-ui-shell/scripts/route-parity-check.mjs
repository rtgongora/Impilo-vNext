#!/usr/bin/env node

/**
 * Route Parity Test Script
 *
 * Asserts that the Next.js App Router file tree contains page files
 * for all routes defined in the route registry (src/lib/routes.ts).
 *
 * Paths are extracted from routes.ts at runtime — do not maintain a duplicate list.
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const APP_DIR = path.resolve(__dirname, "../src/app");
const ROUTES_TS = path.resolve(__dirname, "../src/lib/routes.ts");

const routesSource = fs.readFileSync(ROUTES_TS, "utf8");
const EXPECTED_ROUTES = [...routesSource.matchAll(/path:\s*"([^"]+)"/g)].map((m) => m[1]);

const countMatch = routesSource.match(/export const EXPECTED_ROUTE_COUNT = (\d+)/);
const expectedCount = countMatch ? Number(countMatch[1]) : null;

if (expectedCount !== null && EXPECTED_ROUTES.length !== expectedCount) {
  console.error(
    `\nRoute count mismatch: extracted ${EXPECTED_ROUTES.length} paths from routes.ts ` +
      `but EXPECTED_ROUTE_COUNT is ${expectedCount}.`,
  );
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
