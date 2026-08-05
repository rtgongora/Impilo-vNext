#!/usr/bin/env node

/**
 * No route handler may be shadowed by a proxy rewrite.
 *
 * Next resolves in this order:
 *
 *   headers -> redirects -> beforeFiles -> filesystem routes -> afterFiles
 *     -> DYNAMIC routes -> fallback
 *
 * A catch-all proxy rewrite such as `/api/:path*` placed in `afterFiles` therefore beats every
 * DYNAMIC route below it, while leaving static routes alone. That is not a visible failure: the
 * handler still compiles, still ships in the image, and simply never runs. Requests go to the
 * proxy target instead and come back with whatever that target says.
 *
 * It happened here. `app/api/mobile/provider/hubs/[hub]/route.ts` was shadowed for its entire
 * life. The experience BFF fetched it, this shell proxied the request straight back to the BFF,
 * the BFF had no controller on that path and returned 401, and `stub_fallback` swallowed the
 * error and served a stale local copy. Mobile users got the stale copy. `/api/health/version`
 * kept working throughout, because a static route resolves before `afterFiles` — so the bug
 * looked like it did not exist.
 *
 * This check reads the real `rewrites()` output and the real app tree, and fails if any dynamic
 * route handler sits under a prefix that an earlier-phase rewrite would capture.
 *
 *   node scripts/api-route-shadowing-check.mjs
 */

import fs from "fs";
import path from "path";
import { fileURLToPath, pathToFileURL } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SHELL = path.resolve(__dirname, "..");
const APP_DIR = path.join(SHELL, "src/app");

function fail(lines) {
  console.error(`\n[api-route-shadowing] ${lines.join("\n  ")}\n`);
  process.exit(1);
}

/** Every route handler in the app tree, as the URL path it is meant to serve. */
function routeHandlers(dir, segments = []) {
  const found = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      // Route groups (folder) do not contribute a path segment.
      const next = /^\(.*\)$/.test(entry.name) ? segments : [...segments, entry.name];
      found.push(...routeHandlers(full, next));
    } else if (entry.name === "route.ts" || entry.name === "route.js") {
      found.push({
        urlPath: `/${segments.join("/")}`,
        // `[hub]` and `[...slug]` only resolve at the dynamic phase, after afterFiles rewrites.
        isDynamic: segments.some((s) => s.startsWith("[")),
        file: path.relative(SHELL, full),
      });
    }
  }
  return found;
}

/** `/api/:path*` -> a matcher for the paths that rewrite would capture. */
function rewriteMatcher(source) {
  const pattern = source
    .replace(/\/:[A-Za-z0-9_]+\*/g, "(?:/.*)?")
    .replace(/:[A-Za-z0-9_]+/g, "[^/]+");
  return new RegExp(`^${pattern}$`);
}

const config = (await import(pathToFileURL(path.join(SHELL, "next.config.mjs")).href)).default;

if (typeof config.rewrites !== "function") {
  fail(["next.config.mjs exports no rewrites() — this check can no longer see what it guards."]);
}

// The config throws in production without upstreams; these only shape the destinations, which
// this check does not inspect.
process.env.API_GATEWAY_URL ??= "http://check.invalid";
process.env.BFF_URL ??= "http://check.invalid";

const rewrites = await config.rewrites();

if (Array.isArray(rewrites)) {
  fail([
    "rewrites() returns a bare ARRAY, which Next treats entirely as `afterFiles`.",
    "afterFiles runs BEFORE dynamic routes, so every dynamic route handler under a",
    "catch-all proxy rewrite is shadowed — it compiles, ships, and never executes.",
    "",
    "Return { beforeFiles, afterFiles, fallback } and put catch-all proxy rewrites",
    "in `fallback`, which runs AFTER dynamic routes.",
  ]);
}

// Only phases that precede the dynamic-route step can shadow a handler.
const shadowing = [...(rewrites.beforeFiles ?? []), ...(rewrites.afterFiles ?? [])];
const handlers = routeHandlers(APP_DIR);
const dynamicHandlers = handlers.filter((h) => h.isDynamic);

const shadowed = [];
for (const handler of dynamicHandlers) {
  for (const rewrite of shadowing) {
    if (rewriteMatcher(rewrite.source).test(handler.urlPath)) {
      shadowed.push({ handler, rewrite });
    }
  }
}

if (shadowed.length > 0) {
  fail([
    `${shadowed.length} dynamic route handler(s) are shadowed by a rewrite and will never run:`,
    "",
    ...shadowed.map(
      ({ handler, rewrite }) =>
        `${handler.file}\n    serves ${handler.urlPath}, captured by "${rewrite.source}" ` +
        `-> ${rewrite.destination}`,
    ),
    "",
    "Move that rewrite to `fallback` so dynamic routes resolve first.",
  ]);
}

// A guard that finds nothing because it looked at nothing is worse than no guard. This is the
// positive control: the shadowing-prone shape must actually be present for the check to mean
// anything, and today it is (the mobile provider hub catalogue).
if (dynamicHandlers.length === 0) {
  fail([
    "Found no dynamic route handlers at all. Either the app tree moved, or this check is",
    "walking the wrong directory — it is not proving anything in its current state.",
  ]);
}

console.log(
  `[api-route-shadowing] ok — ${dynamicHandlers.length} dynamic route handler(s) reachable, ` +
    `${shadowing.length} pre-dynamic rewrite(s) checked`,
);
