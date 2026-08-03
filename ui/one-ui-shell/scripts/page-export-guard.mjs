#!/usr/bin/env node
/**
 * Fails when an App Router page/layout module exports something Next.js does not allow.
 *
 * Why this exists: a page file that exports an ordinary constant type-checks cleanly, passes
 * every unit test, and then fails `next build` with
 *
 *     Type error: Page "src/app/x/page.tsx" does not match the required types of a Next.js Page.
 *       "MY_CONST" is not a valid Page export field.
 *
 * `tsc --noEmit` cannot see it — the constraint is imposed by Next's generated route types, not
 * by the app's own tsconfig. So the only thing that catches it is a full production build, which
 * is minutes long and usually the last step before a deploy. This check is a second.
 *
 * The fix is always the same: move the shared value into `src/lib/**` and import it from both
 * the page and its consumer.
 */

import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const APP_DIR = join(fileURLToPath(new URL("../src/app", import.meta.url)));
const ROOT = join(fileURLToPath(new URL("..", import.meta.url)));

/** Exports Next.js recognises on a page/layout/route segment module. */
const ALLOWED = new Set([
  "default",
  "dynamic", "dynamicParams", "revalidate", "fetchCache", "runtime", "preferredRegion",
  "maxDuration", "metadata", "generateMetadata", "generateStaticParams", "generateViewport",
  "viewport", "experimental_ppr", "config",
  // route.ts handlers live under the same walk; they legitimately export HTTP verbs.
  "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS",
]);

const SEGMENT_FILE = /^(page|layout|template|default|route|error|loading|not-found|global-error)\.tsx?$/;

/** `export const A`, `export function A`, `export class A`, `export async function A`. */
const NAMED_DECL = /^export\s+(?:async\s+)?(?:const|let|var|function|class)\s+([A-Za-z0-9_$]+)/gm;
/** `export { A, B as C }` — the exported name is what matters, so read past `as`. */
const NAMED_LIST = /^export\s*\{([^}]*)\}/gm;

function* walk(dir) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) { yield* walk(full); continue; }
    if (SEGMENT_FILE.test(entry)) yield full;
  }
}

const violations = [];
for (const file of walk(APP_DIR)) {
  // Type-only exports are erased before Next ever sees them.
  const source = readFileSync(file, "utf8").replace(/^export\s+type\s.*$/gm, "");
  const names = [];
  for (const m of source.matchAll(NAMED_DECL)) names.push(m[1]);
  for (const m of source.matchAll(NAMED_LIST)) {
    for (const part of m[1].split(",")) {
      const name = part.trim().split(/\s+as\s+/).pop()?.trim();
      if (name && name !== "type") names.push(name);
    }
  }
  for (const name of names) {
    if (!ALLOWED.has(name)) violations.push({ file: relative(ROOT, file), name });
  }
}

if (violations.length > 0) {
  console.error("Invalid Next.js route-segment exports — these fail `next build`:\n");
  for (const v of violations) console.error(`  ${v.file}  →  export "${v.name}"`);
  console.error("\nMove the value into src/lib/** and import it from both sides.");
  process.exit(1);
}

console.log("page-export-guard: no invalid route-segment exports");
