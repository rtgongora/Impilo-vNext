import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import yaml from "js-yaml";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../..");
const REGISTRY = path.join(ROOT, "docs/registry/services-registry.yaml");
const DOCS_REGISTRY = path.join(ROOT, "docs/registry");
const DOCS_PLANES = path.join(ROOT, "docs/architecture/planes");
const DEPLOYMENT_MATRIX = path.join(ROOT, "docs/deployment/service-classification-matrix.md");

const PLANE_META = [
  ["trust", "Trust, Identity Assurance & Governance Plane", "TSHEPO policy, authorisation, consent, audit, keys, identity assurance, session and device risk.", "01-trust-identity-assurance-governance.md"],
  ["registry", "Registry & Sovereign Identity Spine", "VITO, VARAPI, TUSO, ZIBO, UBOMI, INDAWO and authoritative registries.", "02-registry-sovereign-identity-spine.md"],
  ["clinical", "Clinical Execution & Shared Health Record Plane", "BUTANO/FHIR, patient care workflows, encounters, orders, results, pharmacy and inpatient capabilities.", "03-clinical-execution-shared-health-record.md"],
  ["data", "Data, Intelligence & Public Health Plane", "NDR, warehousing, analytics, surveillance, indicators, search and public health intelligence.", "04-data-intelligence-public-health.md"],
  ["integration", "Integration, Interoperability & Edge Plane", "Integration hub, adapters, offline sync, jobs, notifications and channel/edge workflows.", "05-integration-interoperability-edge.md"],
  ["experience", "Experience, Workflow & Orchestration Plane", "one-ui-shell, experience-bff, provider/citizen/admin journeys and orchestration.", "06-experience-workflow-orchestration.md"],
  ["enterprise", "Enterprise Resource & Market Operations Plane", "MusheX, COSTA, coverage, claims, billing, marketplace and enterprise operations.", "07-enterprise-resource-market-operations.md"],
];

function ensureDir(p) {
  fs.mkdirSync(p, { recursive: true });
}

function readRegistry() {
  return yaml.load(fs.readFileSync(REGISTRY, "utf8"));
}

function toMdTable(headers, rows) {
  const head = `| ${headers.join(" | ")} |`;
  const sep = `|${headers.map(() => "---").join("|")}|`;
  const body = rows.map((r) => `| ${r.join(" | ")} |`);
  return [head, sep, ...body].join("\n");
}

function fmtList(v) {
  return (v ?? []).length ? v.join(", ") : "—";
}

function byPlane(services) {
  const map = new Map();
  for (const [plane] of PLANE_META) map.set(plane, []);
  for (const s of services) {
    const p = s.primary_plane ?? "integration";
    if (!map.has(p)) map.set(p, []);
    map.get(p).push(s);
  }
  for (const items of map.values()) {
    items.sort((a, b) => (a.maven_module ?? "").localeCompare(b.maven_module ?? ""));
  }
  return map;
}

function writePlaneDoctrine(services) {
  ensureDir(DOCS_PLANES);
  const grouped = byPlane(services);
  const doctrine = [
    "# Production Plane Doctrine",
    "",
    "This document establishes the canonical seven-plane model for Impilo vNext production architecture.",
    "",
    "## Canonical Planes",
    "",
    toMdTable(
      ["Plane ID", "Name", "Scope"],
      PLANE_META.map(([id, name, scope]) => [id, name, scope])
    ),
    "",
    "## Governance Rules",
    "",
    "- Do not invent new plane names.",
    "- Every service must declare exactly one `primary_plane` and one `domain`.",
    "- `secondary_planes` denote integration touchpoints only and never transfer ownership.",
    "- Deployment namespaces are infrastructure placement and must not be interpreted as ownership planes.",
    "- Backend capability is incomplete until wired via BFF/API contracts into the applicable experience layer.",
    "- Frontend capability is incomplete until backed by real APIs and production service logic.",
  ].join("\n");
  fs.writeFileSync(path.join(DOCS_PLANES, "00-production-plane-doctrine.md"), doctrine, "utf8");

  for (const [id, name, scope, filename] of PLANE_META) {
    const file = path.join(DOCS_PLANES, filename);
    const rows = grouped.get(id).map((s) => [
      `\`${s.id}\``,
      `\`${s.maven_module}\``,
      s.domain ?? "—",
      fmtList(s.system_of_record_for),
      fmtList(s.forbidden_responsibilities),
    ]);
    const content = [
      `# ${name}`,
      "",
      scope,
      "",
      "## Service Ownership",
      "",
      toMdTable(["Service ID", "Maven module", "Domain", "System of record", "Forbidden responsibilities"], rows),
      "",
      "## Operating Guardrails",
      "",
      "- Authz, audit and observability controls are mandatory on production routes.",
      "- No new mock or stub path is allowed in production execution routes.",
      "- Contract and integration changes must be reflected in registry and cross-plane maps.",
    ].join("\n");
    fs.writeFileSync(file, content, "utf8");
  }
}

function writeRegistryViews(services) {
  ensureDir(DOCS_REGISTRY);
  const sorted = [...services].sort((a, b) => (a.maven_module ?? "").localeCompare(b.maven_module ?? ""));
  const grouped = byPlane(sorted);

  const planesIndex = [
    "# Planes Index",
    "",
    "Canonical seven-plane ownership index generated from `services-registry.yaml`.",
    "",
    toMdTable(
      ["Plane", "Name", "Service count"],
      PLANE_META.map(([id, name]) => [id, name, String((grouped.get(id) ?? []).length)])
    ),
  ].join("\n");
  fs.writeFileSync(path.join(DOCS_REGISTRY, "planes-index.md"), planesIndex, "utf8");

  const ownershipRows = sorted.map((s) => [
    `\`${s.id}\``,
    `\`${s.maven_module}\``,
    s.primary_plane ?? "—",
    s.domain ?? "—",
    s.sovereign_group ?? "—",
    fmtList(s.system_of_record_for),
    fmtList(s.consumes_from),
    fmtList(s.exposes_to),
    fmtList(s.forbidden_responsibilities),
  ]);
  const ownership = [
    "# Service Ownership Matrix",
    "",
    toMdTable(
      ["Service ID", "Maven module", "Primary plane", "Domain", "Sovereign group", "System of record", "Consumes from", "Exposes to", "Forbidden responsibilities"],
      ownershipRows
    ),
  ].join("\n");
  fs.writeFileSync(path.join(DOCS_REGISTRY, "service-ownership-matrix.md"), ownership, "utf8");

  const readinessRows = sorted.map((s) => [
    `\`${s.id}\``,
    s.implementation_status ?? "—",
    s.api_contract_status ?? "—",
    s.frontend_wiring_status ?? "—",
    "requires-audit",
    s.authz_audit_status ?? "—",
    s.observability_status ?? "—",
    "requires-audit",
    s.production_status ?? "—",
    "see mock-and-stub-register.md and gap-remediation-plan.md",
  ]);
  const readiness = [
    "# Service Readiness Register",
    "",
    toMdTable(
      ["Service ID", "Implementation", "API Contract", "Frontend Wiring", "Mock/Stub", "Security/Audit", "Observability", "Test Status", "Production readiness", "Blockers"],
      readinessRows
    ),
  ].join("\n");
  fs.writeFileSync(path.join(DOCS_REGISTRY, "service-readiness-register.md"), readiness, "utf8");

  const planeMap = [
    "# Service Plane Map",
    "",
    toMdTable(
      ["Service ID", "Maven module", "Primary plane", "Domain", "Secondary planes"],
      sorted.map((s) => [`\`${s.id}\``, `\`${s.maven_module}\``, s.primary_plane ?? "—", s.domain ?? "—", fmtList(s.secondary_planes)])
    ),
  ].join("\n");
  fs.writeFileSync(path.join(DOCS_REGISTRY, "service-plane-map.md"), planeMap, "utf8");

  const sorMap = [
    "# System of Record Map",
    "",
    toMdTable(
      ["Service ID", "Primary plane", "System-of-record responsibilities"],
      sorted.map((s) => [`\`${s.id}\``, s.primary_plane ?? "—", fmtList(s.system_of_record_for)])
    ),
  ].join("\n");
  fs.writeFileSync(path.join(DOCS_REGISTRY, "system-of-record-map.md"), sorMap, "utf8");

  const forbMap = [
    "# Forbidden Responsibilities Map",
    "",
    toMdTable(
      ["Service ID", "Primary plane", "Forbidden responsibilities"],
      sorted.map((s) => [`\`${s.id}\``, s.primary_plane ?? "—", fmtList(s.forbidden_responsibilities)])
    ),
  ].join("\n");
  fs.writeFileSync(path.join(DOCS_REGISTRY, "forbidden-responsibilities-map.md"), forbMap, "utf8");

  const contractMap = [
    "# Cross-Plane Contract Map",
    "",
    toMdTable(
      ["Service ID", "Primary plane", "Consumes from", "Exposes to"],
      sorted.map((s) => [`\`${s.id}\``, s.primary_plane ?? "—", fmtList(s.consumes_from), fmtList(s.exposes_to)])
    ),
  ].join("\n");
  fs.writeFileSync(path.join(DOCS_REGISTRY, "cross-plane-contract-map.md"), contractMap, "utf8");
}

function writeClassificationMatrix(services) {
  const rows = [...services]
    .sort((a, b) => (a.maven_module ?? "").localeCompare(b.maven_module ?? ""))
    .map((s) => [
      `\`${s.maven_module}\``,
      s.primary_plane ?? "—",
      s.domain ?? "—",
      "unchanged-existing",
      "to-be-profiled",
      "high",
      "unknown",
      "tbd",
      "tbd",
      "internal-gateway",
      s.authz_audit_status ?? "partial",
      s.observability_status ?? "partial",
      "hpa-latency-and-queue-based",
      s.production_status ?? "baseline-assessed",
    ]);

  const content = [
    "# Impilo vNext Service Classification Matrix",
    "",
    "Production hardening baseline. Architectural ownership uses canonical planes and is intentionally decoupled from deployment namespace naming.",
    "",
    toMdTable(
      [
        "Service",
        "Primary Plane",
        "Domain",
        "Deployment Namespace",
        "Runtime Criticality",
        "Data Sensitivity",
        "Stateful/Stateless",
        "DB",
        "Kafka/Event Topics",
        "Ingress",
        "Authz/Audit Requirements",
        "Observability Requirements",
        "Scaling Behaviour",
        "Production Readiness Status",
      ],
      rows
    ),
    "",
    "Namespace preservation rule: keep existing namespace decisions unless an explicit impact-analysis-approved rename is accepted.",
  ].join("\n");
  fs.writeFileSync(DEPLOYMENT_MATRIX, content, "utf8");
}

function main() {
  const registry = readRegistry();
  const services = registry.services ?? [];
  writePlaneDoctrine(services);
  writeRegistryViews(services);
  writeClassificationMatrix(services);
  console.log("Wrote plane doctrine, registry views, and classification matrix.");
}

main();
