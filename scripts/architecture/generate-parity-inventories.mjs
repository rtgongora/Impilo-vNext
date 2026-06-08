#!/usr/bin/env node
/**
 * Generates architecture parity inventories from canonical CAPABILITIES registry.
 * Run: node scripts/architecture/generate-parity-inventories.mjs
 */
import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { CAPABILITIES } from "../frontend/generate-parity-docs.mjs";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "../..");
const ARCH = join(ROOT, "docs/architecture");
const DATE = new Date().toISOString().slice(0, 10);

function mdTable(rows, columns) {
  const header = `| ${columns.join(" | ")} |`;
  const sep = `| ${columns.map(() => "---").join(" | ")} |`;
  const body = rows
    .map((r) => `| ${columns.map((c) => String(r[c] ?? "").replace(/\|/g, "\\|")).join(" | ")} |`)
    .join("\n");
  return `${header}\n${sep}\n${body}`;
}

function webStatus(c) {
  if (c.maturity === "Live" || c.web === "yes") return "fully surfaced";
  if (c.maturity === "Not Wired" || c.web === "no") return "missing";
  if (c.web === "partial") return "partially surfaced";
  return "uncertain";
}

function mobileStatus(c) {
  if (c.mobile === "yes" || c.mobile === "Live") return "complete";
  if (c.mobile === "n/a" || c.mobile === "no") return "intentionally deferred";
  if (c.mobile === "partial") return "partial";
  return "unknown";
}

function mobileExpected(c) {
  if (c.mobile === "n/a") return "no";
  if (c.plane === "Platform" && c.domain.includes("Admin")) return "partial";
  return "yes";
}

mkdirSync(ARCH, { recursive: true });

const backendRows = CAPABILITIES.map((c) => ({
  service: c.domain,
  plane: c.plane,
  path: c.backend,
  purpose: c.capability,
  apis: c.backend,
  userFacing: c.maturity === "Not Wired" ? "uncertain" : "yes",
  webRoute: c.webRoute,
  mobileExpectation: mobileExpected(c),
  frontendStatus: webStatus(c),
}));

writeFileSync(
  join(ARCH, "BACKEND_CAPABILITY_INVENTORY.md"),
  `# Backend capability inventory

> Generated: ${DATE}. Regenerate: \`node scripts/architecture/generate-parity-inventories.mjs\`

Canonical matrix: [BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md](../frontend/BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md)

${mdTable(backendRows, [
  "service",
  "plane",
  "path",
  "purpose",
  "apis",
  "userFacing",
  "webRoute",
  "mobileExpectation",
  "frontendStatus",
])}

## Internal / non-user-facing

Platform infra (Kafka, Postgres, Redis, observability) is not listed here — see \`docs/registry/services-registry.yaml\`.
`
);

const feRows = CAPABILITIES.map((c) => ({
  capability: `${c.domain}: ${c.capability}`,
  endpoint: c.backend,
  webRoute: c.webRoute,
  webClient: c.webClient,
  realData: c.maturity === "Live" ? "yes" : c.maturity === "Fixture" ? "mock-risk" : "partial",
  mockRisk: c.maturity === "Fixture" ? "yes" : "no",
  parity: c.maturity === "Live" ? "complete" : c.maturity === "Not Wired" ? "missing" : "partial",
  priority: c.priority,
  remediation: c.action,
  gate: c.maturity === "Live" ? "existing" : "advisory",
}));

writeFileSync(
  join(ARCH, "FRONTEND_BACKEND_PARITY_MATRIX.md"),
  `# Frontend ↔ backend parity matrix

> Generated: ${DATE}. Regenerate: \`node scripts/architecture/generate-parity-inventories.mjs\`

${mdTable(feRows, [
  "capability",
  "endpoint",
  "webRoute",
  "webClient",
  "realData",
  "mockRisk",
  "parity",
  "priority",
  "remediation",
  "gate",
])}
`
);

const mobileRows = CAPABILITIES.map((c) => ({
  capability: c.capability,
  domain: c.domain,
  backend: c.backend,
  webRoute: c.webRoute,
  android:
    c.mobile === "yes" || c.mobile === "Live"
      ? "screens (citizen/provider)"
      : c.mobile === "partial"
        ? "partial screens"
        : "missing",
  ios: c.mobile === "n/a" ? "not supported by platform" : "Expo (citizen + provider)",
  mobileClient: c.mobileClient ?? "apps/mobile/citizen-app, apps/mobile/provider-app",
  realData: c.mobile === "yes" || c.mobile === "Live" ? "yes" : "partial",
  parity: mobileStatus(c),
  gate: c.mobile === "yes" || c.mobile === "partial" ? "advisory" : "advisory",
  remediation: c.action,
}));

writeFileSync(
  join(ARCH, "MOBILE_PARITY_MATRIX.md"),
  `# Mobile parity matrix

> Generated: ${DATE}. Regenerate: \`node scripts/architecture/generate-parity-inventories.mjs\`

Apps: **citizen-app**, **provider-app** (Expo, pnpm workspace). See [MOBILE_APP_INVENTORY.md](./MOBILE_APP_INVENTORY.md).

Tier matrices: \`docs/mobile/full-mobile-parity-matrix.md\` (from \`node tools/parity/generate-mobile-parity-matrix.mjs\`).

${mdTable(mobileRows, [
  "capability",
  "domain",
  "backend",
  "webRoute",
  "android",
  "ios",
  "mobileClient",
  "realData",
  "parity",
  "gate",
  "remediation",
])}

## Required domains (classification)

Vito, Varapi, Tuso, Tshepo (user-facing), Butano, Zibo (admin), Fundo, Nompilo, Ndila, Nhume, MusheX, clinical, enterprise, data/intelligence, telemedicine, notifications — all rows above.
`
);

console.log("Wrote docs/architecture parity inventories");
