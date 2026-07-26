#!/usr/bin/env node
/**
 * Query honesty guard — a read that failed is not an empty result.
 *
 * WHY THIS EXISTS
 *
 * The same defect has now been found four times, across three surfaces and two delivery lanes,
 * and in every case the BFF had *already* been hardened to return 502 rather than an empty list:
 *
 *   - the patient banner read "no conditions" for every patient in the estate
 *   - the conditions page did the same
 *   - the ED trackboard rendered "No active ED visits." on a failed read, telling a coordinator
 *     the department was empty
 *   - the emergency patient view rendered "None active." for medications, one line below an
 *     allergy query that already captured isError correctly
 *
 * A component that destructures only `data` and falls through to `?? []` converts a server-side
 * honesty fix back into a false reassurance. Hardening a server while a consumer discards the
 * error buys nothing. This is a house style, not a defect of any one surface, so the guard is
 * estate-wide rather than lane-local: the surfaces nobody has audited are exactly the ones nobody
 * will.
 *
 * THE TEST THAT MATTERS is not "is this list empty" but "what does a reader do differently
 * because it is empty". An empty medication list changes prescribing. An empty trackboard stops a
 * coordinator looking for waiting patients, and it makes that claim at the moment the system knows
 * least. Where the answer is genuinely "nothing", opt out explicitly and say why.
 *
 * HOW IT WORKS
 *
 * Flags a destructure that takes `data` without any error signal (`isError`, `error`, `status`,
 * `isLoadingError`) from either `useQuery`/`useInfiniteQuery` directly, or from a hook imported
 * from `@/hooks/queries/...` — because the ED trackboard bug was `useEdVisits`, a wrapper, not a
 * bare `useQuery`.
 *
 * The estate has a large pre-existing population of these, so the guard RATCHETS against a
 * committed baseline rather than hard-failing on day one: a new violation fails, and the baseline
 * may only shrink. Printing the outstanding count on every run is deliberate — a guard whose debt
 * is invisible stops being paid down.
 *
 * OPT-OUT
 *
 *   // query-honesty: <reason an empty result is not a clinical claim here>
 *
 * on the line immediately above the destructure. The reason is required; a bare marker fails.
 */

import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { readdir } from "node:fs/promises";
import { join, relative } from "node:path";

const SRC = "src";
const BASELINE_PATH = "scripts/query-honesty-baseline.json";
const ERROR_SIGNALS = /\b(isError|error|status|isLoadingError|isSuccess)\b/;
const OPT_OUT = /\/\/\s*query-honesty:\s*\S+/;

async function walk(dir, out = []) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "node_modules" || entry.name === "__tests__") continue;
      await walk(path, out);
    } else if (/\.tsx?$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name)) {
      out.push(path);
    }
  }
  return out;
}

/** Hook names imported from @/hooks/queries in this file — these wrap useQuery. */
function queryHookNames(source) {
  const names = new Set(["useQuery", "useInfiniteQuery"]);
  const importRe = /import\s*\{([^}]*)\}\s*from\s*["'][^"']*hooks\/queries[^"']*["']/g;
  let m;
  while ((m = importRe.exec(source))) {
    for (const raw of m[1].split(",")) {
      const name = raw.trim().split(/\s+as\s+/).pop()?.trim();
      if (name && /^use[A-Z]/.test(name)) names.add(name);
    }
  }
  return names;
}

function findViolations(source, hookNames) {
  const violations = [];
  const names = [...hookNames].join("|");
  if (!names) return violations;
  const re = new RegExp(`const\\s*\\{([^}]*)\\}\\s*=\\s*(${names})\\s*[(<]`, "g");
  let m;
  while ((m = re.exec(source))) {
    const bindings = m[1];
    if (!/\bdata\b/.test(bindings)) continue;
    if (ERROR_SIGNALS.test(bindings)) continue;

    const before = source.slice(0, m.index);
    const line = before.split("\n").length;
    const previousLine = before.split("\n").slice(-2, -1)[0] ?? "";
    if (OPT_OUT.test(previousLine)) continue;

    violations.push({ line, hook: m[2] });
  }
  return violations;
}

const files = await walk(SRC);
const counts = {};
const details = [];

for (const file of files) {
  const source = readFileSync(file, "utf8");
  const found = findViolations(source, queryHookNames(source));
  if (found.length === 0) continue;
  const key = relative(".", file).split("\\").join("/");
  counts[key] = found.length;
  for (const v of found) details.push(`${key}:${v.line}  ${v.hook}`);
}

const total = Object.values(counts).reduce((a, b) => a + b, 0);

if (process.argv.includes("--write-baseline")) {
  writeFileSync(BASELINE_PATH, `${JSON.stringify({ total, files: counts }, null, 2)}\n`);
  console.log(`Baseline written: ${total} outstanding across ${Object.keys(counts).length} files.`);
  process.exit(0);
}

if (!existsSync(BASELINE_PATH)) {
  console.error(`No baseline at ${BASELINE_PATH}. Run: npm run test:query-honesty -- --write-baseline`);
  process.exit(1);
}

const baseline = JSON.parse(readFileSync(BASELINE_PATH, "utf8"));
const regressions = [];

for (const [file, count] of Object.entries(counts)) {
  const allowed = baseline.files[file] ?? 0;
  if (count > allowed) {
    regressions.push(
      `  ${file}: ${count} unguarded query destructure(s), baseline allows ${allowed}`,
    );
  }
}

console.log("Query honesty guard");
console.log("===================");
console.log(`Outstanding (baseline): ${baseline.total}`);
console.log(`Outstanding (now):      ${total}`);

if (regressions.length > 0) {
  console.error("\nNEW unguarded query reads — a read that failed is not an empty result:\n");
  console.error(regressions.join("\n"));
  console.error(
    "\nDestructure an error signal and render 'could not be read' distinctly from 'none recorded'.",
  );
  console.error(
    "If an empty result genuinely is not a clinical claim here, opt out on the line above:",
  );
  console.error("  // query-honesty: <reason>\n");
  process.exit(1);
}

if (total < baseline.total) {
  console.log(
    `\n${baseline.total - total} fixed since the baseline. Re-baseline to lock the gain in:`,
  );
  console.log("  npm run test:query-honesty -- --write-baseline");
}

console.log("\nNo new unguarded query reads.");
