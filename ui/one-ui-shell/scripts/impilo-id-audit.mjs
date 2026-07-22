#!/usr/bin/env node
/**
 * Impilo ID copy audit (TM-B13 / TM-B3).
 *
 * Citizen-facing product-name migration guard: the person-facing identifier is
 * branded "Impilo ID", never "Health ID". This walks the citizen route trees and
 * citizen component trees and fails (exit 1) if the literal "Health ID" appears in
 * any user-visible string.
 *
 * Modelled on scripts/no-stub-guard.mjs.
 *
 * NOT a violation (ignored):
 *   - // line comments, block comments, and {/* ... *\/} JSX comments (copy notes, not UI)
 *   - *.test.tsx / *.spec.tsx files (assertions track the visible copy separately)
 *   - the code identifier `healthId` and URL slugs like /citizen/health-id (no space)
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SHELL_ROOT = path.resolve(__dirname, "..");

// Citizen surfaces only — provider/ops surfaces are out of scope for this term.
const SCAN_TARGETS = [
  "src/app/citizen",
  "src/app/welcome",
  "src/app/discover",
  "src/app/my",
  "src/app/wallet",
  "src/app/auth/register",
  "src/app/auth/login",
  "src/app/wellness",
  "src/components/citizen",
  "src/components/onboarding",
  "src/components/wallet",
  "src/components/home",
  "src/components/shell/HealthIdStatusChip.tsx",
  "src/lib/i18n/locales/en.json",
];

const CODE_EXT = new Set([".tsx", ".ts", ".jsx", ".js", ".json"]);
const FORBIDDEN = /Health ID/;

function isTestFile(file) {
  return /\.(test|spec)\.[cm]?[jt]sx?$/.test(file);
}

/** Strip comments so copy notes / JSDoc don't count as user-visible copy. */
function stripComments(src) {
  return src
    .replace(/\{\/\*[\s\S]*?\*\/\}/g, "") // {/* JSX comment */}
    .replace(/\/\*[\s\S]*?\*\//g, "") // /* block comment */
    .replace(/(^|[^:])\/\/[^\n]*/g, "$1"); // // line comment (keep http:// etc.)
}

function walk(target, acc = []) {
  const abs = path.join(SHELL_ROOT, target);
  if (!fs.existsSync(abs)) return acc;
  const stat = fs.statSync(abs);
  if (stat.isFile()) {
    if (CODE_EXT.has(path.extname(abs))) acc.push(abs);
    return acc;
  }
  for (const entry of fs.readdirSync(abs, { withFileTypes: true })) {
    const child = path.join(target, entry.name);
    if (entry.isDirectory()) walk(child, acc);
    else if (CODE_EXT.has(path.extname(entry.name))) acc.push(path.join(SHELL_ROOT, child));
  }
  return acc;
}

function rel(file) {
  return path.relative(SHELL_ROOT, file).replace(/\\/g, "/");
}

const files = [];
for (const target of SCAN_TARGETS) walk(target, files);

const violations = [];
for (const file of files) {
  if (isTestFile(file)) continue;
  const raw = fs.readFileSync(file, "utf8");
  const scrubbed = stripComments(raw);
  const rawLines = raw.split("\n");
  const scrubbedLines = scrubbed.split("\n");
  for (let i = 0; i < scrubbedLines.length; i++) {
    if (FORBIDDEN.test(scrubbedLines[i])) {
      violations.push({ file: rel(file), line: i + 1, text: rawLines[i].trim() });
    }
  }
}

if (violations.length > 0) {
  console.error(`\n✖ impilo-id-audit: ${violations.length} citizen-facing "Health ID" string(s) found — use "Impilo ID":\n`);
  for (const v of violations) {
    console.error(`  ${v.file}:${v.line}  ${v.text}`);
  }
  console.error("");
  process.exit(1);
}

console.log(`✓ impilo-id-audit: no citizen-facing "Health ID" copy in ${files.length} scanned file(s).`);
