#!/usr/bin/env node
/**
 * No-stub guard — enforces docs/frontend/GAP_CLOSURE_RULES.md
 *
 * Blocking:
 *   - All violations under src/app/nhume/** (canonical client + no JSON dumps)
 *   - Any violation in page.tsx files changed vs NO_STUB_BASE_SHA / GITHUB_BASE_SHA
 *   - All violations when NO_STUB_STRICT=1
 *
 * Legacy debt (unchanged pages outside nhume): reported as warnings only unless strict mode.
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { execSync } from "child_process";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SHELL_ROOT = path.resolve(__dirname, "..");
const APP_DIR = path.join(SHELL_ROOT, "src/app");
const STRICT = process.env.NO_STUB_STRICT === "1";

const FORBIDDEN_PAGE_PATTERNS = [
  {
    id: "json-dump-ui",
    re: /\{JSON\.stringify\s*\(/,
    message: "Primary UI must not render API payloads via JSON.stringify (use typed components).",
  },
  {
    id: "empty-onclick",
    re: /onClick=\{\s*\(\)\s*=>\s*\{\s*\}\s*\}/,
    message: "Empty onClick handler on a user action — wire or disable with reason.",
  },
  {
    id: "stub-marker",
    re: /\b(DEV_STUB|STUB_PAGE|MOCK_ONLY|STUB_UI)\b/,
    message: "Explicit stub marker found — full functionality required.",
  },
  {
    id: "lab-static-kpi-shell",
    re: /label:\s*"(?:Pending Collection|In Progress|Completed|Urgent)".*value:\s*"0"/s,
    message: "Lab worklist KPI shell with hardcoded zero counts — wire useLabWorklist.",
  },
];

function walk(dir, acc = []) {
  if (!fs.existsSync(dir)) return acc;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, acc);
    else if (entry.name === "page.tsx") acc.push(full);
  }
  return acc;
}

function rel(file) {
  return path.relative(SHELL_ROOT, file).replace(/\\/g, "/");
}

function isNhumePage(file) {
  return rel(file).startsWith("src/app/nhume/");
}

/** Navigation-only hub — no data fetch required. */
function isNhumeNavHub(file) {
  return rel(file) === "src/app/nhume/page.tsx";
}

function checkNhumeDomain(file, content, violations) {
  if (!isNhumePage(file) || isNhumeNavHub(file)) return;
  const r = rel(file);
  if (!/useNhume|@\/hooks\/useNhume|@\/lib\/nhume/.test(content)) {
    violations.push({
      file: r,
      rule: "nhume-canonical-client",
      message: "Nhume pages must use useNhume / lib/nhume (not generic dispatch ops hooks).",
    });
  }
  if (/from\s+["']@\/hooks\/queries\/useDispatch["']/.test(content)) {
    violations.push({
      file: r,
      rule: "nhume-no-dispatch",
      message: "Nhume zone must not import useDispatch — use useNhume.",
    });
  }
}

function checkPage(file, content) {
  const violations = [];
  const r = rel(file);
  for (const { id, re, message } of FORBIDDEN_PAGE_PATTERNS) {
    if (re.test(content)) {
      violations.push({ file: r, rule: id, message });
    }
  }
  checkNhumeDomain(file, content, violations);
  return violations;
}

function changedPageSet() {
  const base = process.env.NO_STUB_BASE_SHA || process.env.GITHUB_BASE_SHA;
  if (!base) return null;
  try {
    const out = execSync(`git diff --name-only ${base}...HEAD`, {
      encoding: "utf8",
      cwd: path.resolve(SHELL_ROOT, "../.."),
      stdio: ["pipe", "pipe", "pipe"],
    });
    const set = new Set();
    for (const line of out.split("\n")) {
      const trimmed = line.trim().replace(/\\/g, "/");
      if (!trimmed.endsWith("page.tsx")) continue;
      const abs = path.resolve(SHELL_ROOT, trimmed.replace(/^ui\/one-ui-shell\//, ""));
      if (fs.existsSync(abs)) set.add(rel(abs));
    }
    return set;
  } catch {
    return null;
  }
}

const allPages = walk(APP_DIR);
const changed = changedPageSet();

const blocking = [];
const legacy = [];

for (const file of allPages) {
  const content = fs.readFileSync(file, "utf8");
  const found = checkPage(file, content);
  if (found.length === 0) continue;

  const r = rel(file);
  const inDiff = changed?.has(r) ?? false;
  const nhume = isNhumePage(file);

  for (const v of found) {
    if (STRICT || nhume || inDiff) {
      blocking.push(v);
    } else {
      legacy.push(v);
    }
  }
}

function printViolations(title, list) {
  if (list.length === 0) return;
  console.error(`\n${title}\n`);
  for (const v of list) {
    console.error(`  [${v.rule}] ${v.file}`);
    console.error(`    ${v.message}`);
  }
}

if (blocking.length > 0) {
  console.error("\nNo-stub guard FAILED (blocking)\n");
  console.error("Policy: docs/frontend/GAP_CLOSURE_RULES.md\n");
  printViolations("Blocking violations:", blocking);
  if (legacy.length > 0) {
    console.error(`\nAlso ${legacy.length} legacy stub pattern(s) on unchanged pages — remediate in dedicated PRs.`);
  }
  process.exit(1);
}

console.log(`No-stub guard: OK (${allPages.length} pages scanned).`);
if (legacy.length > 0) {
  console.warn(`\nWarning: ${legacy.length} legacy stub pattern(s) on unchanged pages (not blocking this run).`);
  console.warn("Set NO_STUB_STRICT=1 to fail on all, or fix debt per GAP_CLOSURE_RULES.md §8.\n");
  if (legacy.length <= 15) {
    for (const v of legacy) {
      console.warn(`  - ${v.file} (${v.rule})`);
    }
  } else {
    console.warn(`  Run: NO_STUB_STRICT=1 npm run test:no-stubs  (lists all)`);
  }
}
if (changed?.size) {
  console.log(`  PR scope: ${changed.size} changed page(s) checked for new violations.`);
}
process.exit(0);
