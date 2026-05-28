#!/usr/bin/env node
/**
 * Wave 20 Phase 13 — export production-readiness parity matrix (JSON + Markdown).
 *
 * Usage (from Impilo-vNext root):
 *   node scripts/production-readiness/generate-wave20-parity-matrix.mjs
 */

import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "../..");
const WAVE20 = join(ROOT, "docs/production-readiness/wave20");
const DEMO_MAP = JSON.parse(
  readFileSync(join(WAVE20, "HEALTH_OS_PRODUCTION_DEMO_MAP.json"), "utf8"),
);

const SOVEREIGN_DOMAINS = [
  {
    domain_id: "pharmacy",
    plane: "Clinical / Rx",
    backend: "pharmacy-service",
    bff_probe: "/internal/v1/pharmacy/prescriptions?patient_id=CPID-ZW-00001",
    web_route: "/pharmacy/transaction-journey?patientId=CPID-ZW-00001",
    mobile_surface: "provider:pharmacy / core_transaction",
    compose: "docker-compose.sovereign.yml",
    maturity: "partial",
  },
  {
    domain_id: "costa",
    plane: "Finance",
    backend: "costing-engine-service",
    bff_probe: "/internal/v1/finance/billing?page=0&size=1",
    web_route: "/finance/billing",
    mobile_surface: "provider:finance / billing",
    compose: "docker-compose.sovereign.yml",
    maturity: "partial",
  },
  {
    domain_id: "mushex",
    plane: "Finance",
    backend: "mushex-service",
    bff_probe: "/internal/v1/finance/payer-ops/payment-intents?sourceType=ADHOC&sourceId=CPID-ZW-00001",
    web_route: "/finance/payer-ops",
    mobile_surface: "provider:finance / billing",
    compose: "docker-compose.sovereign.yml",
    maturity: "partial",
  },
  {
    domain_id: "dispatch",
    plane: "Logistics",
    backend: "dispatch-service",
    bff_probe: "/internal/v1/dispatch/deliveries",
    web_route: "/pharmacy/transaction-journey",
    mobile_surface: "provider:workflow_dispatch",
    compose: "docker-compose.sovereign.yml",
    maturity: "partial",
  },
  {
    domain_id: "nhume",
    plane: "Logistics",
    backend: "nhume-service (host)",
    bff_probe: "/internal/v1/nhume/deliveries",
    web_route: "/nhume",
    mobile_surface: "citizen:nhume-track",
    compose: "host :8210",
    maturity: "not_wired",
  },
  {
    domain_id: "ndila",
    plane: "Public health + geo",
    backend: "ndila-service (host)",
    bff_probe: "/internal/v1/ndila/tiles/config",
    web_route: "/ndila",
    mobile_surface: "provider:ph_field_tasks",
    compose: "host :8155",
    maturity: "not_wired",
  },
  {
    domain_id: "inventory",
    plane: "Enterprise",
    backend: "inventory-service (host)",
    bff_probe: "/internal/v1/inventory/requisitions?facility_id=a1b2c3d4-0001-4000-8000-000000000001",
    web_route: "/enterprise",
    mobile_surface: "provider:facility",
    compose: "host",
    maturity: "partial",
  },
  {
    domain_id: "pct",
    plane: "Clinical",
    backend: "pct-service",
    bff_probe: "/internal/v1/queue/entries?facility_id=f1000000-0000-0000-0000-000000000001",
    web_route: "/queue",
    mobile_surface: "provider:queue",
    compose: "docker-compose.yml",
    maturity: "live",
  },
  {
    domain_id: "integration-hub",
    plane: "Data & intelligence",
    backend: "integration-hub",
    bff_probe: "/internal/v1/integration-hub/routes",
    web_route: "/data-intelligence/pipelines",
    mobile_surface: "provider:ops_reports",
    compose: "docker-compose.yml",
    maturity: "live",
  },
  {
    domain_id: "inpatient",
    plane: "Inpatient",
    backend: "inpatient-service",
    bff_probe: "/internal/v1/inpatient/admissions",
    web_route: "/clinical/inpatient/admissions",
    mobile_surface: "provider:inpatient",
    compose: "docker-compose.yml",
    maturity: "partial",
  },
  {
    domain_id: "wellness",
    plane: "Wellness",
    backend: "wellness-service",
    bff_probe: "/internal/v1/wellness/challenges",
    web_route: "/wellness",
    mobile_surface: "citizen:wellness",
    compose: "docker-compose.yml",
    maturity: "partial",
  },
];

const matrix = {
  wave: "20",
  slice: 13,
  generated_at: new Date().toISOString(),
  golden_patient: DEMO_MAP.golden_patient,
  demo_journeys: DEMO_MAP.journeys,
  sovereign_domains: SOVEREIGN_DOMAINS,
  parity_summary: {
    journey_count: DEMO_MAP.journeys.length,
    sovereign_domain_count: SOVEREIGN_DOMAINS.length,
    compose_live: SOVEREIGN_DOMAINS.filter(
      (d) => d.compose.includes("docker-compose.yml") && !d.compose.includes("sovereign"),
    ).length,
    sovereign_overlay: SOVEREIGN_DOMAINS.filter((d) => d.compose.includes("sovereign")).length,
    host_only: SOVEREIGN_DOMAINS.filter((d) => d.compose.startsWith("host")).length,
  },
};

mkdirSync(WAVE20, { recursive: true });

const jsonPath = join(WAVE20, "BACKEND_FRONTEND_PARITY_MATRIX.json");
writeFileSync(jsonPath, `${JSON.stringify(matrix, null, 2)}\n`);

const mdRows = SOVEREIGN_DOMAINS.map(
  (d) =>
    `| ${d.domain_id} | ${d.plane} | ${d.backend} | ${d.bff_probe} | ${d.web_route} | ${d.mobile_surface} | ${d.compose} | ${d.maturity} |`,
);
const journeyRows = DEMO_MAP.journeys.map(
  (j) =>
    `| ${j.number} | ${j.title} | ${j.web_route} | ${j.mobile_tab} | ${j.bff_probe} | ${j.maturity} |`,
);

const md = `# Backend ↔ Frontend Parity Matrix (Wave 20)

> Generated: ${matrix.generated_at.slice(0, 10)} · \`node scripts/production-readiness/generate-wave20-parity-matrix.mjs\`

Golden patient: **${DEMO_MAP.golden_patient}**

## Seven demo journeys

| # | Journey | Web | Mobile tab | BFF probe | Maturity |
|---|---------|-----|------------|-----------|----------|
${journeyRows.join("\n")}

## Sovereign domains (Rx-path + enterprise)

| Domain | Plane | Backend | BFF probe | Web | Mobile | Compose | Maturity |
|--------|-------|---------|-----------|-----|--------|---------|----------|
${mdRows.join("\n")}

## Compose profiles

| Profile | Command | Rx-path probes |
|---------|---------|----------------|
| Experience (default) | \`tools/dev/up.ps1\` | WARN on pharmacy/finance/dispatch |
| + Sovereign overlay | \`tools/dev/up.ps1 -SovereignHost\` | PASS when pharmacy, MusheX, dispatch healthy |

See [SOVEREIGN_HOST_PORT_MATRIX.md](./SOVEREIGN_HOST_PORT_MATRIX.md).
`;

writeFileSync(join(WAVE20, "BACKEND_FRONTEND_PARITY_MATRIX.md"), md);
console.log(`Wrote ${jsonPath}`);
console.log(`Wrote ${join(WAVE20, "BACKEND_FRONTEND_PARITY_MATRIX.md")}`);
