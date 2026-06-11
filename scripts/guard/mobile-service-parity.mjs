#!/usr/bin/env node
/**
 * Mobile service parity guard — ensures canonical sovereign services exist in mobile registry.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "../..");

const REQUIRED = [
  "vito", "varapi", "tuso", "tshepo", "butano", "ubomi", "zibo", "msika", "indawo",
  "pct", "costa", "mushex", "oros", "simba", "ndila", "nhume", "fundo", "nompilo",
  "madi", "pacs", "live",
];

const registryPath = path.join(
  repoRoot,
  "apps/mobile/packages/mobile-registry/src/registry.ts"
);
const wiringPath = path.join(
  repoRoot,
  "apps/mobile/packages/mobile-registry/src/wiring.ts"
);
const matrixPath = path.join(
  repoRoot,
  "docs/implementation/mobile-service-wiring-matrix.md"
);

let fail = 0;

function failMsg(msg) {
  console.error(`FAIL: ${msg}`);
  fail = 1;
}

function pass(msg) {
  console.log(`PASS: ${msg}`);
}

if (!fs.existsSync(registryPath)) {
  failMsg(`missing mobile registry: ${registryPath}`);
  process.exit(1);
}

const registrySrc = fs.readFileSync(registryPath, "utf8");
const wiringSrc = fs.readFileSync(wiringPath, "utf8");

for (const slug of REQUIRED) {
  if (!registrySrc.includes(`${slug}:`)) {
    failMsg(`registry missing canonical service: ${slug}`);
  }
  if (!wiringSrc.includes(`wire("${slug}"`)) {
    failMsg(`wiring missing canonical service: ${slug}`);
  }
}

for (const slug of REQUIRED) {
  const statusRe = new RegExp(`${slug}:\\s*\\{[\\s\\S]*?mobileStatus:\\s*"(native|deepLink|planned|blocked)"`);
  if (!statusRe.test(registrySrc)) {
    failMsg(`${slug} missing mobileStatus in registry`);
  }
}

if (!fs.existsSync(matrixPath)) {
  failMsg(`missing wiring matrix doc: ${matrixPath}`);
} else {
  const matrix = fs.readFileSync(matrixPath, "utf8");
  for (const slug of REQUIRED) {
    if (!matrix.toLowerCase().includes(slug)) {
      failMsg(`wiring matrix doc missing service: ${slug}`);
    }
  }
}

if (fail) {
  console.error("\nMobile service parity guard: FAILED");
  process.exit(1);
}

pass(`all ${REQUIRED.length} canonical services present in registry, wiring, and matrix doc`);
process.exit(0);
