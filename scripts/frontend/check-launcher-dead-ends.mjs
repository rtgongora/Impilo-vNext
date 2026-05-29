#!/usr/bin/env node
/**
 * Fail CI when shell launchers or command-centre tiles point at routes without page.tsx.
 * Complements route-parity-check.mjs (routes.ts → pages) by guarding curated hrefs.
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), "../..");
const APP_DIR = path.join(ROOT, "ui/one-ui-shell/src/app");
const APP_REGISTRY = path.join(ROOT, "ui/one-ui-shell/src/lib/shell/app-registry.ts");
const TILE_REGISTRY = path.join(
  ROOT,
  "ui/one-ui-shell/src/features/production-command-centre/tile-registry.ts",
);

function routeToPagePath(route) {
  if (route === "/") return path.join(APP_DIR, "page.tsx");
  const clean = route.split("?")[0].split("#")[0];
  return path.join(APP_DIR, ...clean.split("/").filter(Boolean), "page.tsx");
}

function extractHrefs(source, pattern) {
  return [...source.matchAll(pattern)].map((m) => m[1]);
}

function isDynamicRoute(route) {
  return route.includes("[") || route.includes("]");
}

const deadEnds = [];

for (const file of [APP_REGISTRY, TILE_REGISTRY]) {
  const source = fs.readFileSync(file, "utf8");
  const hrefs = extractHrefs(source, /href:\s*"([^"]+)"/g);
  for (const href of hrefs) {
    if (isDynamicRoute(href)) continue;
    const pagePath = routeToPagePath(href);
    if (!fs.existsSync(pagePath)) {
      deadEnds.push({ href, pagePath, source: path.relative(ROOT, file) });
    }
  }
}

if (deadEnds.length > 0) {
  console.error("\nLauncher dead-end check failed:");
  for (const item of deadEnds) {
    console.error(`  ${item.href} (${item.source}) → missing ${path.relative(ROOT, item.pagePath)}`);
  }
  process.exit(1);
}

console.log("Launcher dead-end check passed (app-registry + command-centre tiles).");
