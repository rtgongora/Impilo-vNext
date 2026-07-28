#!/usr/bin/env node

/**
 * Decorative-control check — a <button> that does nothing when clicked.
 *
 * WHY THIS EXISTS
 * ---------------
 * no-stub-guard.mjs already catches an EMPTY handler, `onClick={() => {}}`. It does not catch the
 * more common shape: a `<button>` with no handler at all. Those are worse, because an empty handler
 * is at least visible as a decision someone made, while a missing one looks like finished markup.
 *
 * They are indistinguishable from working controls to the person using them. They have hover
 * states. They have labels like "Save Draft". They sit next to controls that do work. A clinician
 * clicks, sees the hover, gets no error — and reasonably concludes the draft was saved.
 *
 * Found by the Adult Medicine lane in the specialty workspace: order-set buttons with hover states
 * and zero onClick anywhere on the page. They fixed theirs by making them links, which is the right
 * answer when the control navigates — a thing that goes somewhere should be an anchor.
 *
 * WHAT COUNTS AS WIRED
 *   onClick / onMouseDown / onKeyDown  — has behaviour
 *   type="submit" or form=             — submits a form, behaviour lives there
 *   disabled                           — honestly unavailable, which is a valid state
 *   {...props} / {...rest}             — a handler may be injected by the caller
 *
 * Anything else does nothing at all when clicked.
 *
 * It asserts self-reach: scanning zero files means the path assumptions drifted and the check has
 * inspected nothing, which fails rather than reporting a clean tree.
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { DECORATIVE_CONTROL_BASELINE } from "./decorative-control-baseline.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SRC = path.resolve(__dirname, "../src");

const WIRED = [
  "onClick",
  "onMouseDown",
  "onKeyDown",
  "onPointerDown",
  'type="submit"',
  "type={'submit'}",
  'type="reset"',
  "disabled",
  "form=",
  "{...",
];

function walk(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "node_modules" || entry.name === ".next") continue;
      walk(full, out);
    } else if (entry.name.endsWith(".tsx") && !entry.name.endsWith(".test.tsx")) {
      out.push(full);
    }
  }
  return out;
}

const files = walk(SRC);
if (files.length === 0) {
  console.error("DECORATIVE-CONTROL CHECK FAILED: scanned 0 .tsx files.");
  console.error("  Matching nothing is a broken check, not a clean tree.");
  process.exit(1);
}

const baseline = new Set(DECORATIVE_CONTROL_BASELINE);
const found = [];
for (const file of files) {
  // Strip comments first. A <button> inside a JSDoc usage example or a commented-out block is
  // documentation, not markup — FrictionGate's own header shows `<button>Submit Clinical Note</button>`
  // as an example of how to wrap it, and reporting that as a dead control would be a false alarm on
  // the most alarming-sounding label in the codebase.
  const src = fs
    .readFileSync(file, "utf8")
    .replace(/\/\*[\s\S]*?\*\//g, "")   // block comments, incl. JSDoc and {/* ... */} interiors
    .replace(/^\s*\/\/.*$/gm, "");     // whole-line // comments
  const rel = path.relative(path.resolve(__dirname, ".."), file);
  for (const m of src.matchAll(/<button\b([^>]*)>/gs)) {
    const attrs = m.group ? m.group(1) : m[1];
    if (WIRED.some((k) => attrs.includes(k))) continue;
    const line = src.slice(0, m.index).split("\n").length;
    // A label makes the finding legible — "Save Draft" reads very differently from an icon button.
    const after = src.slice(m.index + m[0].length, m.index + m[0].length + 80);
    const label = (after.match(/^\s*([^<{]{2,40})/) || [, ""])[1].trim();
    found.push({ key: `${rel}:${line}`, rel, line, label });
  }
}

const fresh = found.filter((f) => !baseline.has(f.key));
const known = found.filter((f) => baseline.has(f.key));

const stale = [...baseline].filter((k) => !found.some((f) => f.key === k));
if (stale.length > 0) {
  console.log(`DECORATIVE-CONTROL CHECK: ${stale.length} baseline entr(ies) no longer apply.`);
  console.log(`  Remove from scripts/decorative-control-baseline.mjs: ${stale.slice(0, 5).join(", ")}`);
}

if (fresh.length > 0) {
  console.error(
    `\nDECORATIVE-CONTROL CHECK FAILED: ${fresh.length} new <button>(s) do nothing when clicked.`,
  );
  console.error("They have hover states and labels. To a user they are indistinguishable from");
  console.error("controls that work — which is why they ship.\n");
  for (const f of fresh.slice(0, 30)) {
    console.error(`  ${f.rel}:${f.line}${f.label ? `   "${f.label}"` : ""}`);
  }
  if (fresh.length > 30) console.error(`  ... and ${fresh.length - 30} more`);
  console.error("\nWire it, make it a link if it navigates, or disable it with a visible reason.");
  console.error("A control that is honestly unavailable is fine; one that silently does nothing is not.");
  process.exit(1);
}

console.log(
  `DECORATIVE-CONTROL CHECK PASSED (${files.length} components; ${known.length} known in the ` +
    `2026-07-28 baseline, 0 new)`,
);
