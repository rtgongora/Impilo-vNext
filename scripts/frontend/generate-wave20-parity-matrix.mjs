#!/usr/bin/env node
/**
 * Wave 20 — generates BACKEND_FRONTEND_PARITY_MATRIX and HEALTH_OS_PRODUCTION_DEMO_MAP
 * Run from Impilo-vNext root: node scripts/frontend/generate-wave20-parity-matrix.mjs
 */

import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "../..");
const OUT = join(ROOT, "docs/production-readiness/wave20");
const ARCH_REGISTRY = join(ROOT, "docs/architecture/services-registry.yaml");
const MAVEN_REGISTRY = join(ROOT, "docs/registry/services-registry.yaml");

const require = createRequire(import.meta.url);
const yaml = require(join(ROOT, "scripts/completeness/node_modules/js-yaml"));

/** @type {Array<Record<string, string>>} */
const WAVE20_CURATED_ROWS = [
  { plane: "Registry", domain: "VITO", capability: "Client search, register, profile", backend: "/internal/v1/identity/*", webRoute: "/registry/clients", frontEndStatus: "partial", dataSource: "live", priority: "P0", action: "Deepen issuance queue surfacing" },
  { plane: "Registry", domain: "VARAPI", capability: "Provider registry", backend: "/internal/v1/registry/providers", webRoute: "/registry/providers", frontEndStatus: "partial", dataSource: "live", priority: "P0", action: "Verification queue depth" },
  { plane: "Registry", domain: "TUSO", capability: "Facility registry", backend: "/internal/v1/facilities", webRoute: "/registry/facilities", frontEndStatus: "partial", dataSource: "live", priority: "P0", action: "Control tower readiness" },
  { plane: "Registry", domain: "Indawo", capability: "Public health site registry", backend: "/internal/v1/public-health/site-registry/*", webRoute: "/public-health/site-registry", frontEndStatus: "partial", dataSource: "live", priority: "P0", action: "Ndila map embed" },
  { plane: "Trust", domain: "TSHEPO", capability: "Trust admin, context", backend: "/internal/v1/admin/trust/*", webRoute: "/registry/trust", frontEndStatus: "partial", dataSource: "live", priority: "P0", action: "TrustContextBanner on transactions" },
  { plane: "Clinical", domain: "BUTANO", capability: "SHR summary", backend: "/internal/v1/summary/*", webRoute: "/ehr/[patientId]", frontEndStatus: "complete", dataSource: "live", priority: "P0", action: "Mobile parity" },
  { plane: "Clinical", domain: "Inpatient", capability: "Admissions, ward board, discharge", backend: "/internal/v1/inpatient/*", webRoute: "/clinical/inpatient/*", frontEndStatus: "partial", dataSource: "live", priority: "P0", action: "Nursing depth + mobile ward list" },
  { plane: "Clinical", domain: "Pharmacy", capability: "Rx, dispense", backend: "/internal/v1/pharmacy/*", webRoute: "/pharmacy/*", frontEndStatus: "partial", dataSource: "live", priority: "P0", action: "Core transaction linkage" },
  { plane: "Clinical", domain: "Rx Journey", capability: "Rx → MusheX → Nhume stepper", backend: "Multi-BFF composition", webRoute: "/pharmacy/transaction-journey", frontEndStatus: "partial", dataSource: "live", priority: "P0", action: "E2e with live BFF" },
  { plane: "Clinical", domain: "Telemedicine", capability: "Scheduling, records", backend: "/internal/v1/teleconsult/*", webRoute: "/telemedicine/*", frontEndStatus: "partial", dataSource: "live", priority: "P0", action: "RTC blocked label" },
  { plane: "Experience", domain: "Nompilo", capability: "Guidance, contextual assist", backend: "/internal/v1/guidance/*", webRoute: "/ask", frontEndStatus: "partial", dataSource: "live", priority: "P0", action: "Context panels on plane hubs" },
  { plane: "Experience", domain: "Fundo", capability: "Learning LMS (not lending)", backend: "/internal/v1/learning/*", webRoute: "/learning/*", frontEndStatus: "partial", dataSource: "live", priority: "P0", action: "Mobile learning depth" },
  { plane: "Enterprise", domain: "Msika", capability: "Marketplace", backend: "/internal/v1/marketplace/*", webRoute: "/marketplace/*", frontEndStatus: "partial", dataSource: "live", priority: "P0", action: "501 route honesty" },
  { plane: "Finance", domain: "MusheX", capability: "Payments via finance BFF", backend: "/internal/v1/finance/mushex-platform/*", webRoute: "/finance/mushex-platform", frontEndStatus: "partial", dataSource: "live", priority: "P0", action: "Mobile payment status" },
  { plane: "Integration", domain: "Ndila", capability: "Geospatial intelligence", backend: "/internal/v1/ndila/*", webRoute: "/ndila", frontEndStatus: "partial", dataSource: "partial", priority: "P0", action: "Full map client" },
  { plane: "Integration", domain: "Nhume", capability: "Dispatch, delivery", backend: "/internal/v1/mobile/*/nhume/*", webRoute: "/nhume/*", frontEndStatus: "partial", dataSource: "live", priority: "P0", action: "Unified operator UX" },
  { plane: "Enterprise", domain: "ERP", capability: "Inventory, HR, procurement", backend: "/internal/v1/*", webRoute: "/enterprise/*", frontEndStatus: "partial", dataSource: "live", priority: "P1", action: "Sub-module depth" },
  { plane: "Data", domain: "Reporting", capability: "Reports hub", backend: "/internal/v1/reports/*", webRoute: "/reports/*", frontEndStatus: "partial", dataSource: "live", priority: "P0", action: "Data-intelligence hub links" },
  { plane: "Data", domain: "Intelligence", capability: "Fused search, copilot", backend: "/internal/v1/intelligence/*", webRoute: "/intelligence", frontEndStatus: "partial", dataSource: "live", priority: "P0", action: "Dashboard drill-downs" },
  { plane: "Experience", domain: "Command Centre", capability: "Production discoverability", backend: "N/A (composition)", webRoute: "/production-command-centre", frontEndStatus: "complete", dataSource: "live", priority: "P0", action: "Keep tile registry synced" },
  { plane: "Data", domain: "Data Intelligence Hub", capability: "Unified data plane UX", backend: "Multiple BFF", webRoute: "/data-intelligence/*", frontEndStatus: "partial", dataSource: "live", priority: "P0", action: "Pipeline live metrics" },
];

const DEMO_MAP = [
  { plane: "Clinical", service: "Core clinical/Rx", demoRoute: "/pharmacy/transaction-journey (9-step stepper)", status: "partial", mockLive: "live/partial" },
  { plane: "Clinical", service: "Inpatient", demoRoute: "/clinical/inpatient/admissions", status: "partial", mockLive: "live" },
  { plane: "Wellness", service: "Citizen wellness", demoRoute: "/wellness", status: "partial", mockLive: "live" },
  { plane: "Enterprise", service: "Enterprise resources", demoRoute: "/enterprise", status: "partial", mockLive: "live" },
  { plane: "Clinical", service: "Telemedicine→dispatch", demoRoute: "/telemedicine → /nhume/deliveries/new", status: "partial", mockLive: "RTC blocked" },
  { plane: "Public Health", service: "PH + geo", demoRoute: "/public-health/site-registry → /ndila", status: "partial", mockLive: "partial" },
  { plane: "Data", service: "Data & intelligence", demoRoute: "/data-intelligence", status: "partial", mockLive: "live/partial" },
];

function mdTable(rows, cols) {
  const header = `| ${cols.join(" | ")} |`;
  const sep = `| ${cols.map(() => "---").join(" | ")} |`;
  const body = rows
    .map((r) => `| ${cols.map((c) => String(r[c] ?? "").replace(/\|/g, "\\|")).join(" | ")} |`)
    .join("\n");
  return [header, sep, body].join("\n");
}

function mapSurfacingQuality(value) {
  const v = String(value ?? "").toLowerCase();
  if (v.includes("complete") || v.includes("good")) return "complete";
  if (v.includes("not required") || v.includes("n/a")) return "not_required";
  if (v.includes("missing") || v.includes("none")) return "not_wired";
  if (v.includes("partial") || v.includes("thin")) return "partial";
  return v || "unknown";
}

function mapImplementationStatus(value) {
  const v = String(value ?? "").toLowerCase();
  if (v.includes("live") || v.includes("production")) return "live";
  if (v.includes("partial")) return "partial";
  if (v.includes("planned") || v.includes("stub")) return "blocked";
  return v || "unknown";
}

function mapPriority(service) {
  const backlog = String(service.backlog_priority ?? "").toLowerCase();
  if (backlog.includes("p0") || backlog.includes("critical")) return "P0";
  if (backlog.includes("p1") || backlog.includes("high")) return "P1";
  if (service.surfacing_quality === "Missing" || service.frontend_gap) return "P1";
  return "P2";
}

/** @param {Record<string, unknown>} service */
function architectureRow(service) {
  const gap = String(service.frontend_gap || service.implementation_gap || service.documentation_gap || "").trim();
  return {
    canonicalName: String(service.canonical_name ?? ""),
    plane: String(service.primary_plane_display || service.primary_plane || ""),
    domain: String(service.category_display || service.category || ""),
    capability: String(service.display_name || service.canonical_name || ""),
    backend: String(service.api_surface || service.module_path || ""),
    webRoute: String(service.frontend_surface || ""),
    frontEndStatus: mapSurfacingQuality(service.surfacing_quality),
    dataSource: mapImplementationStatus(service.implementation_status),
    priority: mapPriority(service),
    action: gap || String(service.remediation_notes || ""),
    integrationStatus: String(service.integration_status || ""),
    remediationStatus: String(service.remediation_status || ""),
  };
}

/** @param {Record<string, unknown>} service */
function mavenRow(service) {
  return {
    canonicalName: String(service.id ?? service.maven_module ?? ""),
    plane: String(service.primary_plane || service.plane || ""),
    domain: String(service.domain || ""),
    capability: (service.product_names && service.product_names[0]) || service.maven_module || service.id || "",
    backend: service.maven_module ? `services/${service.maven_module}` : "",
    webRoute: "",
    frontEndStatus: mapSurfacingQuality(service.frontend_wiring_status),
    dataSource: mapImplementationStatus(service.implementation_status || service.production_status),
    priority: "P2",
    action: "",
    integrationStatus: "",
    remediationStatus: "",
  };
}

function loadRegistryRows() {
  const archDoc = yaml.load(readFileSync(ARCH_REGISTRY, "utf8"));
  const archRows = (archDoc.services || []).map(architectureRow);

  const mavenDoc = yaml.load(readFileSync(MAVEN_REGISTRY, "utf8"));
  const archNames = new Set(archRows.map((r) => r.canonicalName.toLowerCase()));
  const supplemental = (mavenDoc.services || [])
    .map(mavenRow)
    .filter((row) => row.canonicalName && !archNames.has(row.canonicalName.toLowerCase()));

  return [...archRows, ...supplemental].sort((a, b) =>
    `${a.plane}:${a.capability}`.localeCompare(`${b.plane}:${b.capability}`),
  );
}

mkdirSync(OUT, { recursive: true });

const registryRows = loadRegistryRows();
const parityCols = ["plane", "domain", "capability", "backend", "webRoute", "frontEndStatus", "dataSource", "priority", "action"];
const fullCols = [...parityCols, "canonicalName", "integrationStatus", "remediationStatus"];

const generatedAt = new Date().toISOString();

writeFileSync(
  join(OUT, "BACKEND_FRONTEND_PARITY_MATRIX.json"),
  JSON.stringify(
    {
      generated: generatedAt,
      curatedP0Count: WAVE20_CURATED_ROWS.length,
      registryRowCount: registryRows.length,
      curatedRows: WAVE20_CURATED_ROWS,
    },
    null,
    2,
  ),
);

writeFileSync(
  join(OUT, "BACKEND_FRONTEND_PARITY_MATRIX.md"),
  `# Backend-to-Frontend Parity Matrix (Wave 20)

> Generated: ${generatedAt.slice(0, 10)} · Registry rows: **${registryRows.length}** · Curated P0 highlights: **${WAVE20_CURATED_ROWS.length}**

## Curated P0 highlights

${mdTable(WAVE20_CURATED_ROWS, parityCols)}

## Full registry-derived matrix

See [\`REGISTRY_PARITY_MATRIX.md\`](./REGISTRY_PARITY_MATRIX.md) for all ${registryRows.length} architecture + maven registry entries.
`,
);

writeFileSync(
  join(OUT, "REGISTRY_PARITY_MATRIX.json"),
  JSON.stringify({ generated: generatedAt, rowCount: registryRows.length, rows: registryRows }, null, 2),
);

writeFileSync(
  join(OUT, "REGISTRY_PARITY_MATRIX.md"),
  `# Registry Parity Matrix (auto-generated)

> Source: \`docs/architecture/services-registry.yaml\` + supplemental \`docs/registry/services-registry.yaml\`
> Generated: ${generatedAt.slice(0, 10)} · Rows: **${registryRows.length}**

${mdTable(registryRows, fullCols)}
`,
);

writeFileSync(join(OUT, "HEALTH_OS_PRODUCTION_DEMO_MAP.json"), JSON.stringify({ generated: generatedAt, journeys: DEMO_MAP }, null, 2));
writeFileSync(
  join(OUT, "HEALTH_OS_PRODUCTION_DEMO_MAP.md"),
  `# Health OS Production Demo Map\n\n${mdTable(DEMO_MAP, ["plane", "service", "demoRoute", "status", "mockLive"])}\n`,
);

console.log(`Wrote wave20 parity (${registryRows.length} registry rows) and demo map`);
