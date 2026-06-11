#!/usr/bin/env node
/**
 * Mobile no-mock guard — scans production mobile routes for hardcoded mock/demo data.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "../..");

const mobileRoot = path.join(repoRoot, "apps/mobile");
const BLOCKING_PATTERNS = [
  /mockData/i,
  /fakeResponse/i,
  /demoPatient/i,
  /lorem ipsum/i,
  /placeholder screen/i,
  /coming soon/i,
  /under construction/i,
];

const ALLOWLIST = [
  "demoJourneyService.ts",
  "__tests__",
  ".test.ts",
  ".test.tsx",
  "fallback.ts",
  "SpecialtyWorkspacePanel.tsx",
  "BarcodePanel",
];

let fail = 0;
let scanned = 0;

function failMsg(msg) {
  console.error(`FAIL: ${msg}`);
  fail = 1;
}

function pass(msg) {
  console.log(`PASS: ${msg}`);
}

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "node_modules") continue;
      walk(full, files);
    } else if (/\.(tsx?|jsx?)$/.test(entry.name)) {
      files.push(full);
    }
  }
  return files;
}

const files = walk(mobileRoot).filter((f) => {
  const rel = path.relative(mobileRoot, f);
  return !ALLOWLIST.some((a) => rel.includes(a));
});

for (const file of files) {
  const rel = path.relative(repoRoot, file);
  const src = fs.readFileSync(file, "utf8");
  scanned += 1;

  for (const pattern of BLOCKING_PATTERNS) {
    if (pattern.test(src)) {
      const isScreen = rel.includes("/screens/") && !rel.includes("__tests__");
      const isService = rel.includes("/services/") && !rel.endsWith(".test.ts");
      if (isScreen || isService) {
        failMsg(`${rel} contains blocked pattern ${pattern}`);
      }
    }
  }

  if (rel.includes("/screens/") && /onPress=\{\s*\(\)\s*=>\s*\{\s*\}\s*\}/.test(src)) {
    failMsg(`${rel} contains empty onPress handler in production screen`);
  }
}

if (fail) {
  console.error("\nMobile no-mock guard: FAILED");
  process.exit(1);
}

pass(`scanned ${scanned} mobile source files — no blocking mock patterns in production routes`);
process.exit(0);
