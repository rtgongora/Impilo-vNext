#!/usr/bin/env node
/**
 * Local fallback lint for learning OpenAPI contract.
 *
 * Purpose:
 * - Validate core structural expectations without relying on remote npm installs
 * - Catch common regressions (missing v1.1 endpoints, duplicate operationIds)
 *
 * This is intentionally lightweight and repo-local. It does not replace Redocly.
 */

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const currentDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(currentDir, "..", "..");
const specPath = path.join(repoRoot, "contracts", "openapi", "learning.openapi.yaml");

function fail(message) {
  console.error(`FAIL: ${message}`);
  process.exitCode = 1;
}

function pass(message) {
  console.log(`PASS: ${message}`);
}

if (!fs.existsSync(specPath)) {
  fail(`Spec file not found: ${specPath}`);
  process.exit(1);
}

const raw = fs.readFileSync(specPath, "utf8");

const requiredSnippets = [
  { label: "OpenAPI version", pattern: /^openapi:\s*3\./m },
  { label: "Info block", pattern: /^info:\s*$/m },
  { label: "Paths block", pattern: /^paths:\s*$/m },
  { label: "v1.1 catalog read path", pattern: /^\s*\/internal\/v1\/learning\/v11\/catalog:\s*$/m },
  { label: "v1.1 structure read path", pattern: /^\s*\/internal\/v1\/learning\/v11\/courses\/\{courseId\}\/structure:\s*$/m },
  { label: "v1.1 authoring catalog path", pattern: /^\s*\/internal\/v1\/learning\/v11\/catalog:\s*$/m },
  { label: "v1.1 assessments path", pattern: /^\s*\/internal\/v1\/learning\/v11\/assessments:\s*$/m },
  { label: "v1.1 report path", pattern: /^\s*\/internal\/v1\/learning\/v11\/reports\/cohort-completions:\s*$/m },
  { label: "v1.1 studio dashboard path", pattern: /^\s*\/internal\/v1\/learning\/v11\/studio\/dashboard:\s*$/m },
  { label: "v1.1 AI generate path", pattern: /^\s*\/internal\/v1\/learning\/v11\/ai\/generate:\s*$/m },
  { label: "v1.1 notifications path", pattern: /^\s*\/internal\/v1\/learning\/v11\/notifications:\s*$/m },
  { label: "v1.1 interactive responses path", pattern: /^\s*\/internal\/v1\/learning\/v11\/interactive\/activities\/\{activityId\}\/responses:\s*$/m },
];

for (const check of requiredSnippets) {
  if (!check.pattern.test(raw)) {
    fail(`Missing ${check.label}`);
  } else {
    pass(check.label);
  }
}

const operationIdMatches = [...raw.matchAll(/^\s*operationId:\s*([A-Za-z0-9_.-]+)/gm)];
const operationIds = operationIdMatches.map((m) => m[1]);
const duplicateOperationIds = [...new Set(operationIds.filter((id, i) => operationIds.indexOf(id) !== i))];

if (operationIds.length === 0) {
  fail("No operationId values found");
} else {
  pass(`operationId count: ${operationIds.length}`);
}

if (duplicateOperationIds.length > 0) {
  fail(`Duplicate operationId values: ${duplicateOperationIds.join(", ")}`);
} else {
  pass("No duplicate operationId values");
}

if (/\t/.test(raw)) {
  fail("Tab characters found; YAML should use spaces");
} else {
  pass("No tab indentation");
}

if (process.exitCode && process.exitCode !== 0) {
  process.exit(process.exitCode);
}

console.log("OpenAPI fallback lint passed.");
