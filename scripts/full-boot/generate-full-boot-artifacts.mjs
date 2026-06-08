#!/usr/bin/env node
/**
 * Generate full vNext boot readiness artifacts from canonical registry + repo scan.
 * Source of truth: docs/registry/services-registry.yaml (+ repo discovery overlay).
 */
import fs from "node:fs";
import path from "node:path";
import { execSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import yaml from "js-yaml";
import {
  resolveImageStrategy,
  jarExists,
  OFFICIAL_UPSTREAM,
  SHARED_TEMPLATE_PATH,
  isBlockingStrategy,
} from "./image-strategy-resolver.mjs";
import {
  deploymentLane,
  isRuntimeK8sMicroservice,
  isWaveRuntimeService,
  runNotApplicableReason,
} from "./full-boot-lanes.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../..");
const REGISTRY_PATH = path.join(ROOT, "docs/registry/services-registry.yaml");
const ARCH_REGISTRY_PATH = path.join(ROOT, "docs/architecture/services-registry.yaml");
const CLASSIFICATION_PATH = path.join(ROOT, "config/full-boot-service-classification.yml");
const REPORTS_DIR = path.join(ROOT, "reports/full-boot");

const PLANE_META = [
  ["trust", "Trust, Identity Assurance & Governance", "docs/architecture/planes/01-trust-identity-assurance-governance.md"],
  ["registry", "Registry & Sovereign Identity Spine", "docs/architecture/planes/02-registry-sovereign-identity-spine.md"],
  ["clinical", "Clinical Execution & Shared Health Record", "docs/architecture/planes/03-clinical-execution-shared-health-record.md"],
  ["data", "Data, Intelligence & Public Health", "docs/architecture/planes/04-data-intelligence-public-health.md"],
  ["integration", "Integration, Interoperability & Edge", "docs/architecture/planes/05-integration-interoperability-edge.md"],
  ["experience", "Experience, Workflow & Orchestration", "docs/architecture/planes/06-experience-workflow-orchestration.md"],
  ["enterprise", "Enterprise Resource & Market Operations", "docs/architecture/planes/07-enterprise-resource-market-operations.md"],
];

const SLICE_DEPLOYED = new Set(["one-ui-shell", "experience-bff", "postgres", "redis"]);

const REQUIRED_TRUST = new Set([
  "tshepo-authz-service",
  "tshepo-identity-service",
  "tshepo-consent-service",
  "tshepo-audit-service",
  "tshepo-keys-service",
]);
const REQUIRED_REGISTRY = new Set(["vito-service", "varapi-service", "tuso-service", "zibo-service", "ubomi-service"]);
const REQUIRED_EXPERIENCE = new Set(["experience-bff", "one-ui-shell"]);

/** Wave 6 program — RR-flagged sidecars absorbed into ui/one-ui-shell (build + preview helm retired). */
const WAVE_6_RETIRED_SIDECARS = new Set([
  "mushex-finance-console",
  "mushex-ops-console",
  "mushex-payer-portal",
  "ehr",
]);
const REQUIRED_INFRA = new Set([
  "postgres",
  "redis",
  "kafka",
  "keycloak",
  "envoy",
  "minio",
  "hapi-fhir",
]);


function ensureDir(p) {
  fs.mkdirSync(p, { recursive: true });
}

function exists(p) {
  return fs.existsSync(path.join(ROOT, p));
}

function readYaml(p) {
  return yaml.load(fs.readFileSync(p, "utf8"));
}

function toMdTable(headers, rows) {
  const head = `| ${headers.join(" | ")} |`;
  const sep = `|${headers.map(() => "---").join("|")}|`;
  const body = rows.map((r) => `| ${r.map((c) => String(c ?? "—").replace(/\|/g, "\\|")).join(" | ")} |`);
  return [head, sep, ...body].join("\n");
}

function scanRepoFacts() {
  const servicesDir = path.join(ROOT, "services");
  const dockerfiles = new Map();
  const poms = new Set();
  for (const ent of fs.readdirSync(servicesDir, { withFileTypes: true })) {
    if (!ent.isDirectory()) continue;
    const mod = ent.name;
    if (exists(`services/${mod}/pom.xml`)) poms.add(mod);
    if (exists(`services/${mod}/Dockerfile`)) dockerfiles.set(mod, `services/${mod}/Dockerfile`);
  }
  const uiWorkspaces = fs.readdirSync(path.join(ROOT, "ui"), { withFileTypes: true })
    .filter((d) => d.isDirectory() && exists(`ui/${d.name}/package.json`))
    .map((d) => d.name);
  const helmCharts = fs.readdirSync(path.join(ROOT, "helm"), { withFileTypes: true })
    .filter((d) => d.isDirectory() && exists(`helm/${d.name}/Chart.yaml`))
    .map((d) => d.name);
  const openapi = fs.readdirSync(path.join(ROOT, "contracts/openapi"))
    .filter((f) => f.endsWith(".yaml") || f.endsWith(".yml"));
  const uiDockerfiles = new Set();
  for (const ws of uiWorkspaces) {
    if (exists(`ui/${ws}/Dockerfile`)) uiDockerfiles.add(ws);
  }
  if (exists("services/experience-bff/Dockerfile")) uiDockerfiles.add("experience-bff");
  return { dockerfiles, poms, uiWorkspaces, uiDockerfiles, helmCharts, openapi };
}

function normalizePlane(p) {
  const map = {
    finance_resource: "enterprise",
    integration_ops: "integration",
    trust_identity: "trust",
  };
  return map[p] ?? p ?? "integration";
}

function classifyEntry(entry) {
  const id = entry.id;
  const type = entry.component_type;
  if (type === "library") return "internal_package";
  if (type === "external_dependency") return "external_dependency";
  if (type === "infrastructure") {
    if (REQUIRED_INFRA.has(id)) return "required_full_boot";
    return "optional_full_boot";
  }
  if (type === "ui_workspace" || type === "mobile_app") {
    if (id === "one-ui-shell") return "required_full_boot";
    return "optional_full_boot";
  }
  if (REQUIRED_EXPERIENCE.has(id) || REQUIRED_TRUST.has(id) || REQUIRED_REGISTRY.has(id)) {
    return "required_full_boot";
  }
  if (entry.primary_plane === "clinical" && ["butano-service", "fhir-gateway-service", "pct-service"].includes(id)) {
    return "required_full_boot";
  }
  if (id === "tshepo-service" || WAVE_6_RETIRED_SIDECARS.has(id)) return "deprecated_retired";
  if (entry.module_path === "external" || entry.source_path?.startsWith("external")) {
    return "external_dependency";
  }
  if (!entry.buildable && entry.implementation_status === "not-implemented") {
    return "doctrine_only_future";
  }
  return "optional_full_boot";
}

function deployOrderGroup(entry, classification) {
  if (classification === "internal_package" || classification === "external_dependency") return "n/a";
  if (entry.component_type === "infrastructure") return "infrastructure";
  if (entry.primary_plane === "trust") return "identity_trust_policy";
  if (entry.primary_plane === "registry") return "registries";
  if (["kafka", "schema-registry-service"].includes(entry.id)) return "event_backbone";
  if (entry.primary_plane === "data" && entry.id?.includes("warehouse")) return "data_layer";
  if (entry.primary_plane === "experience") return "experience_layer";
  if (entry.component_type === "mobile_app") return "mobile_config";
  if (entry.id?.includes("observability")) return "observability";
  if (entry.primary_plane === "integration") return "platform_services";
  return "domain_services";
}

function buildUnifiedCatalog(registry, archRegistry, facts) {
  const byId = new Map();
  for (const lib of registry.libraries ?? []) {
    byId.set(lib.id, {
      id: lib.id,
      canonical_name: lib.id,
      maven_module: lib.maven_module,
      source_path: lib.path,
      primary_plane: "integration",
      domain: "shared-library",
      component_type: "library",
      buildable: true,
      build_tool: "maven",
      default_http_port: null,
      implementation_status: "library",
      production_status: "internal",
      frontend_wiring_status: "n/a",
      api_contract_status: "n/a",
      confidence: "certain",
    });
  }
  for (const s of registry.services ?? []) {
    const mod = s.maven_module ?? s.id;
    byId.set(s.id, {
      id: s.id,
      canonical_name: s.id,
      maven_module: mod,
      source_path: `services/${mod}`,
      primary_plane: s.primary_plane ?? "integration",
      domain: s.domain ?? "—",
      component_type: "backend_service",
      buildable: facts.poms.has(mod),
      build_tool: facts.poms.has(mod) ? "maven" : "unknown",
      default_http_port: s.default_http_port,
      implementation_status: s.implementation_status,
      production_status: s.production_status,
      frontend_wiring_status: s.frontend_wiring_status,
      api_contract_status: s.api_contract_status,
      consumes_from: s.consumes_from ?? [],
      exposes_to: s.exposes_to ?? [],
      confidence: "certain",
    });
  }
  for (const ws of facts.uiWorkspaces) {
    if (byId.has(ws)) continue;
    byId.set(ws, {
      id: ws,
      canonical_name: ws,
      maven_module: ws,
      source_path: `ui/${ws}`,
      primary_plane: ws === "one-ui-shell" ? "experience" : "experience",
      domain: "ui-workspace",
      component_type: "frontend_app",
      buildable: true,
      build_tool: "npm",
      default_http_port: null,
      implementation_status: "implemented-or-partial",
      production_status: "baseline-assessed",
      frontend_wiring_status: "wired",
      api_contract_status: "partial",
      confidence: "certain",
    });
  }
  for (const app of ["citizen-app", "provider-app"]) {
    byId.set(app, {
      id: app,
      canonical_name: app,
      maven_module: app,
      source_path: `apps/mobile/${app}`,
      primary_plane: "experience",
      domain: "mobile",
      component_type: "mobile_app",
      buildable: true,
      build_tool: "pnpm",
      default_http_port: null,
      implementation_status: "implemented-or-partial",
      production_status: "baseline-assessed",
      frontend_wiring_status: "partial",
      api_contract_status: "partial",
      confidence: "certain",
    });
  }
  for (const infra of [
    { id: "postgres", plane: "integration", port: 5432 },
    { id: "redis", plane: "integration", port: 6379 },
    { id: "kafka", plane: "integration", port: 9092 },
    { id: "keycloak", plane: "trust", port: 8080 },
    { id: "minio", plane: "integration", port: 9000 },
    { id: "envoy", plane: "trust", port: 10000 },
    { id: "hapi-fhir", plane: "clinical", port: 8090 },
    { id: "opa", plane: "trust", port: 8181 },
  ]) {
    if (!byId.has(infra.id)) {
      byId.set(infra.id, {
        id: infra.id,
        canonical_name: infra.id,
        source_path: "docker-compose.yml",
        primary_plane: infra.plane,
        domain: "infrastructure",
        component_type: "infrastructure",
        buildable: false,
        build_tool: "image-only",
        default_http_port: infra.port,
        implementation_status: "external-image",
        production_status: "required-infra",
        confidence: "certain",
      });
    }
  }
  for (const ext of archRegistry?.services ?? []) {
    const name = ext.canonical_name ?? ext.id;
    if (byId.has(name)) continue;
    if (ext.module_path !== "external" && ext.type !== "external_dependency") continue;
    byId.set(name, {
      id: name,
      canonical_name: name,
      source_path: ext.module_path ?? "external",
      primary_plane: normalizePlane(ext.primary_plane),
      domain: ext.category ?? "external",
      component_type: "external_dependency",
      buildable: false,
      build_tool: "n/a",
      default_http_port: ext.port,
      implementation_status: ext.implementation_status ?? "external",
      production_status: ext.readiness_status ?? "external",
      confidence: ext.confidence ?? "high",
    });
  }
  return [...byId.values()].sort((a, b) => a.id.localeCompare(b.id));
}

function enrichEntry(entry, facts) {
  const mod = entry.maven_module ?? entry.id;
  const hasDocker = facts.dockerfiles.has(mod) || facts.uiDockerfiles?.has(entry.id);
  const helmName = mod?.replace(/-service$/, "") ?? mod;
  const hasHelm = facts.helmCharts.some((h) => h === helmName || h === mod || mod?.startsWith(h));
  const inSlice = SLICE_DEPLOYED.has(entry.id);
  const classification = classifyEntry(entry);
  const img = resolveImageStrategy(entry, facts, classification, ROOT);

  let current_status = "unknown";
  if (img.image_strategy_status === "not_required") current_status = "no_runtime_image_required";
  else if (inSlice) current_status = "deployed_and_healthy";
  else if (img.image_strategy_status === "valid") current_status = "image_strategy_defined";
  else if (img.image_strategy_status === "missing") current_status = "missing_required_image_strategy";
  else current_status = "unknown_needs_review";

  const blocker = isBlockingStrategy(img, classification)
    ? "missing_required_image_strategy"
    : classification === "required_full_boot" && !inSlice && img.image_required
      ? "not_deployed_in_preview"
      : "";

  let recommended_next_action = img.reason ?? "Verify after authorization";
  if (blocker === "not_deployed_in_preview") recommended_next_action = "Deploy in impilo-full-preview when authorized";

  const wave6RetireReason = WAVE_6_RETIRED_SIDECARS.has(entry.id)
    ? "Wave 6 sidecar retirement — canonical parity in one-ui-shell; not in preview helm; CI build removed"
    : null;

  return {
    ...entry,
    dockerfile_status: hasDocker ? "present" : entry.buildable ? "optional" : "n/a",
    helm_support: hasHelm ? "chart_in_helm/" : entry.id === "one-ui-shell" || entry.id === "experience-bff" ? "deploy/helm/impilo-vnext" : "none",
    full_boot_classification: classification,
    deploy_order_group: deployOrderGroup(entry, classification),
    ...img,
    reason: wave6RetireReason ?? img.reason,
    runtime_image_required: img.image_required,
    current_status,
    blocker,
    recommended_next_action,
  };
}

function writeSevenPlaneDoc(catalog) {
  const grouped = new Map(PLANE_META.map(([id]) => [id, []]));
  for (const e of catalog) {
    const p = e.primary_plane ?? "integration";
    if (!grouped.has(p)) grouped.set(p, []);
    grouped.get(p).push(e);
  }
  const lines = [
    "# vNext Seven-Plane Architecture",
    "",
    "> Generated from `docs/registry/services-registry.yaml` + repo scan.",
    "> Regenerate: `node scripts/full-boot/generate-full-boot-artifacts.mjs`",
    "",
    "**This document does not invent plane membership.** Uncertain entries are marked in the full catalog.",
    "",
    "## Canonical planes",
    "",
    toMdTable(
      ["Plane ID", "Name", "Evidence", "Component count"],
      PLANE_META.map(([id, name, ev]) => [id, name, `\`${ev}\``, String((grouped.get(id) ?? []).length)])
    ),
  ];
  for (const [id, name, ev] of PLANE_META) {
    const items = grouped.get(id) ?? [];
    lines.push("", `## ${name}`, "", `Evidence: [\`${ev}\`](${ev})`, "");
    lines.push(
      toMdTable(
        ["Component", "Type", "Classification", "Status", "Confidence"],
        items.slice(0, 120).map((e) => [
          `\`${e.id}\``,
          e.component_type,
          e.full_boot_classification,
          e.current_status,
          e.confidence ?? "certain",
        ])
      )
    );
    if (items.length > 120) lines.push("", `_… and ${items.length - 120} more (see FULL_VNEXT_SERVICE_CATALOG.md)._`);
  }
  fs.writeFileSync(path.join(ROOT, "docs/architecture/VNEXT_SEVEN_PLANE_ARCHITECTURE.md"), lines.join("\n") + "\n", "utf8");
}

function writeServiceCatalog(catalog) {
  const rows = catalog.map((e) => [
    `\`${e.id}\``,
    e.component_type,
    e.primary_plane,
    e.domain,
    `\`${e.source_path ?? "—"}\``,
    e.build_tool,
      e.image_strategy ?? "—",
      e.image_strategy_reclass ?? "—",
      e.image_strategy_status ?? "—",
      e.default_http_port ?? "—",
      e.full_boot_classification,
      e.current_status,
      e.blocker || "—",
      e.recommended_next_action,
    ]);
  const content = [
    "# Full vNext Service Catalog",
    "",
    "> **Source of truth:** [`docs/registry/services-registry.yaml`](../registry/services-registry.yaml)",
    "> Runtime images: see [`RUNTIME_IMAGE_STRATEGY_DOCTRINE.md`](../environment/RUNTIME_IMAGE_STRATEGY_DOCTRINE.md).",
    "",
    `**Total components:** ${catalog.length}`,
    "",
    toMdTable(
      [
        "Name",
        "Type",
        "Plane",
        "Domain",
        "Path",
        "Stack",
        "Image strategy",
        "Reclass",
        "Strategy status",
        "Port",
        "Full-boot class",
        "Status",
        "Blocker",
        "Next action",
      ],
      rows
    ),
  ].join("\n");
  fs.writeFileSync(path.join(ROOT, "docs/architecture/FULL_VNEXT_SERVICE_CATALOG.md"), content + "\n", "utf8");
}

function walkDir(dir, acc = []) {
  if (!fs.existsSync(dir)) return acc;
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, ent.name);
    if (ent.isDirectory()) walkDir(full, acc);
    else if (/\.(md|yaml|yml)$/.test(ent.name)) acc.push(full);
  }
  return acc;
}

function writeDoctrineIndex() {
  const files = [];
  for (const dir of ["docs/doctrine", "docs/architecture", "docs/environment", "docs/frontend"]) {
    for (const full of walkDir(path.join(ROOT, dir))) {
      const rel = path.relative(ROOT, full).replace(/\\/g, "/");
      if (/doctrine|journey|transaction|parity|experience|trust|registry|mobile|ui-experience|GAP_CLOSURE/i.test(rel)) {
        files.push(rel);
      }
    }
  }
  const unique = [...new Set(files)].sort();
  const rows = unique.map((f) => {
    const automatable = /parity|GAP_CLOSURE|check-|gate|inventory|registry/i.test(f);
    return [`\`${f}\``, automatable ? "partial-auto" : "human-review", "all planes", automatable ? "yes" : "advisory"];
  });
  const content = [
    "# vNext Doctrine Index",
    "",
    "> Regenerate: `node scripts/full-boot/generate-full-boot-artifacts.mjs`",
    "",
    toMdTable(["Document", "Automation", "Scope", "Blocking gate"], rows.slice(0, 200)),
  ].join("\n");
  fs.writeFileSync(path.join(ROOT, "docs/architecture/VNEXT_DOCTRINE_INDEX.md"), content + "\n", "utf8");
}

function writeDoctrineComplianceMatrix(catalog) {
  const rows = catalog
    .filter((e) => e.component_type !== "external_dependency")
    .map((e) => {
      const comp =
        e.current_status === "deployed_and_healthy"
          ? "partial"
          : e.full_boot_classification === "required_full_boot"
            ? "missing"
            : "unknown";
      return [
        `\`${e.id}\``,
        "health-os-doctrine, core-transaction (if domain)",
        e.full_boot_classification === "required_full_boot" ? "boot + surface" : "as classified",
        e.implementation_status ?? "—",
        e.frontend_wiring_status ?? "—",
        "see MOBILE_PARITY_MATRIX",
        e.api_contract_status ?? "—",
        comp,
        `\`${e.source_path}\``,
        e.blocker || "triage",
      ];
    });
  const content = [
    "# vNext Doctrine Compliance Matrix",
    "",
    "> Do not mark **compliant** without evidence. Generated baseline; human review required.",
    "",
    toMdTable(
      ["Component", "Doctrine(s)", "Expected", "Implementation", "Frontend", "Mobile", "API/contract", "Compliance", "Evidence", "Remediation"],
      rows
    ),
  ].join("\n");
  fs.writeFileSync(path.join(ROOT, "docs/architecture/VNEXT_DOCTRINE_COMPLIANCE_MATRIX.md"), content + "\n", "utf8");
}

function writeApiContractCatalog(facts) {
  const rows = facts.openapi.map((f) => {
    const base = f.replace(/\.(yaml|yml)$/, "");
    return [`\`${f}\``, "see contract", "contracts/openapi", "BFF + services", "one-ui-shell hooks", "mobile clients", "partial", "not_deployed"];
  });
  const content = [
    "# vNext API Contract Catalog",
    "",
    "> OpenAPI under `contracts/openapi/`. Extend with BFF route map and controller scan.",
    "",
    `**OpenAPI files:** ${facts.openapi.length}`,
    "",
    toMdTable(["Contract", "Plane", "Path", "Provider", "Web consumer", "Mobile consumer", "Tests", "Deployed"], rows),
    "",
    "See also: `docs/architecture/experience-bff-internal-routes.md`, `docs/architecture/kafka-event-catalog.md`.",
  ].join("\n");
  fs.writeFileSync(path.join(ROOT, "docs/architecture/VNEXT_API_CONTRACT_CATALOG.md"), content + "\n", "utf8");
}

function writeClassificationYaml(catalog) {
  const entries = catalog.map((e) => ({
    id: e.id,
    canonical_name: e.canonical_name ?? e.id,
    plane: e.primary_plane,
    domain: e.domain,
    component_type: e.component_type,
    full_boot_classification: e.full_boot_classification,
    classification: e.full_boot_classification,
    runtime_kind: e.runtime_kind ?? null,
    deploy_order_group: e.deploy_order_group,
    build_required: ["required_full_boot", "optional_full_boot"].includes(e.full_boot_classification) && e.buildable,
    image_required: !!e.image_required,
    runtime_image_required: !!e.image_required,
    image_strategy: e.image_strategy,
    image_strategy_reclass: e.image_strategy_reclass,
    image_strategy_status: e.image_strategy_status,
    image_build_command: e.image_build_command ?? null,
    image_name: e.image_name ?? `impilo/${e.id}`,
    official_image: e.official_image ?? null,
    official_chart: e.official_chart ?? null,
    dockerfile_path: e.dockerfile_path ?? null,
    shared_template: e.shared_template ?? null,
    jib_module: e.jib_module ?? null,
    buildpack_builder: e.buildpack_builder ?? null,
    container_required: !!e.image_required,
    helm_required: e.full_boot_classification === "required_full_boot",
    health_check_required: e.full_boot_classification === "required_full_boot",
    contract_check_required: e.component_type === "backend_service",
    frontend_surface_expected: e.component_type === "backend_service" && e.exposes_to?.includes?.("experience-bff"),
    mobile_surface_expected: e.component_type === "mobile_app",
    doctrine_compliance_required: e.full_boot_classification === "required_full_boot",
    source_path: e.source_path,
    build_tool: e.build_tool,
    default_http_port: e.default_http_port ?? null,
    priority: e.full_boot_classification === "required_full_boot" ? "P0" : e.blocker ? "P1" : "P2",
    blocker: e.blocker || null,
    reason: e.reason ?? `Runtime image strategy: ${e.image_strategy}`,
    classification_confidence: e.classification_confidence ?? "certain",
    deployment_lane: deploymentLane(e),
    run_applicable: deploymentLane(e).startsWith("runtime_"),
    run_not_applicable_reason: runNotApplicableReason(e),
  }));
  const doc = {
    metadata: {
      generated_at: new Date().toISOString(),
      source: "docs/registry/services-registry.yaml",
      doctrine: "docs/environment/RUNTIME_IMAGE_STRATEGY_DOCTRINE.md",
      total_entries: entries.length,
    },
    classifications: entries,
  };
  fs.writeFileSync(CLASSIFICATION_PATH, yaml.dump(doc, { lineWidth: 120 }), "utf8");
}

function writeMatrices(catalog, facts) {
  const imageSummaryPath = path.join(ROOT, "reports/full-boot/full-image-build-summary.json");
  const imageSummary = exists(path.relative(ROOT, imageSummaryPath)) ? JSON.parse(fs.readFileSync(imageSummaryPath, "utf8")) : {};
  const perService = imageSummary.per_service ?? {};
  const normalizeBuildStatus = (id, e) => {
    const r = perService[id];
    if (!e.image_required) return "not_required";
    if (!r) return "unknown";
    if (r === "missing_strategy") return "missing_required_strategy";
    if (r === "unknown") return "unknown";
    if (r.startsWith("fail")) return "fail";
    if (r === "official_ok" || r.startsWith("pass")) return "pass";
    if (r === "skip" || r === "skipped") return e.image_required ? "unknown" : "not_required";
    return "unknown";
  };
  const buildRows = catalog
    .filter((e) => e.buildable)
    .map((e) => [
      e.id,
      e.primary_plane,
      e.source_path,
      e.build_tool,
      e.build_tool === "maven" ? `cd services/${e.maven_module} && ./mvnw -q package -DskipTests` : e.build_tool === "npm" ? `cd ui/${e.id} && npm run build` : "—",
      "jar|dist",
      "not_run",
      "—",
      "—",
    ]);
  fs.writeFileSync(
    path.join(ROOT, "docs/environment/FULL_BUILD_MATRIX.md"),
    ["# Full Build Matrix", "", `> ${buildRows.length} buildable targets. Regenerate after classification.`, "", toMdTable(["Service", "Plane", "Path", "Tool", "Command", "Artifact", "Status", "Failure", "Log"], buildRows)].join("\n") + "\n",
    "utf8"
  );

  const containerRows = catalog.map((e) => [
    e.id,
    e.primary_plane,
    e.component_type,
    e.full_boot_classification,
    e.image_required ? "yes" : "no",
    e.image_strategy ?? "—",
    e.image_strategy_status ?? "—",
    e.dockerfile_path ?? "—",
    e.shared_template ?? "—",
    e.jib_module ?? "—",
    e.buildpack_builder ?? "—",
    e.official_image ?? e.official_chart ?? "—",
    e.image_name ?? `impilo/${e.id}`,
    e.image_build_command ?? "—",
    normalizeBuildStatus(e.id, e),
    e.blocker || "—",
    e.recommended_next_action ?? "—",
  ]);
  const strategyCounts = catalog.reduce((acc, e) => {
    const k = e.image_strategy ?? "unknown";
    acc[k] = (acc[k] ?? 0) + 1;
    return acc;
  }, {});
  fs.writeFileSync(
    path.join(ROOT, "docs/environment/FULL_CONTAINERIZATION_MATRIX.md"),
    [
      "# Full Containerization / Runtime Image Matrix",
      "",
      "> **Doctrine:** [`RUNTIME_IMAGE_STRATEGY_DOCTRINE.md`](RUNTIME_IMAGE_STRATEGY_DOCTRINE.md) — Dockerfile is not the only valid strategy.",
      "",
      "## Strategy counts",
      "",
      ...Object.entries(strategyCounts)
        .sort((a, b) => b[1] - a[1])
        .map(([k, n]) => `- **${k}**: ${n}`),
      "",
      toMdTable(
        [
          "Component",
          "Plane",
          "Type",
          "Full-boot class",
          "Image required",
          "Strategy",
          "Status",
          "Dockerfile",
          "Shared template",
          "Jib module",
          "Buildpack",
          "Official image/chart",
          "Image name",
          "Build command",
          "Build status",
          "Blocker",
          "Remediation",
        ],
        containerRows
      ),
    ].join("\n") + "\n",
    "utf8"
  );

  const helmRows = catalog.map((e) => [
    e.id,
    e.primary_plane,
    e.helm_support !== "none" ? "yes" : "no",
    e.helm_support,
    e.full_boot_classification === "required_full_boot" ? "required" : "optional",
    e.helm_support === "none" ? "not_deployable" : "partially_deployable",
    e.helm_support === "none" ? "no chart" : "—",
    "add subchart or impilo-vnext template",
  ]);
  fs.writeFileSync(
    path.join(ROOT, "docs/environment/FULL_HELM_DEPLOYABILITY_MATRIX.md"),
    ["# Full Helm Deployability Matrix", "", toMdTable(["Service", "Plane", "Helm", "Location", "Ingress", "Deployability", "Blocker", "Next"], helmRows)].join("\n") + "\n",
    "utf8"
  );
}

function strategyCounts(catalog) {
  const keys = [
    "dockerfile",
    "shared-dockerfile-template",
    "jib",
    "buildpacks",
    "official-upstream-image",
    "official-helm-chart",
    "not-required-internal-package",
    "not-required-mobile-artifact",
    "not-required-generated-client",
    "not-required-doctrine-only-component",
    "unknown-needs-review",
    "missing-required-image-strategy",
  ];
  const acc = Object.fromEntries(keys.map((k) => [k, 0]));
  for (const e of catalog) {
    const k = e.image_strategy ?? "unknown-needs-review";
    acc[k] = (acc[k] ?? 0) + 1;
  }
  return acc;
}

function writeEnvironmentStrategyDocs(catalog, facts) {
  const counts = strategyCounts(catalog);
  const buildpackUi = catalog.filter((e) => e.image_strategy === "buildpacks");
  const javaServices = catalog.filter((e) => e.component_type === "backend_service" && e.build_tool === "maven");
  const infra = catalog.filter((e) => e.component_type === "infrastructure");

  const prevBuild = { pass: 75, fail: 11, missingDockerfile: 23, duration: "~2h39m", note: "Legacy pipeline treated missing Dockerfile as failure" };

  fs.writeFileSync(
    path.join(ROOT, "docs/environment/IMAGE_STRATEGY_RECLASSIFICATION_PLAN.md"),
    [
      "# Runtime Image Strategy Reclassification Plan",
      "",
      "> **Doctrine:** Dockerfile is not the doctrine. Repeatable **runtime image strategy** is the doctrine.",
      "",
      "## Previous full image build (legacy pipeline)",
      "",
      `| Metric | Value |`,
      `|--------|-------|`,
      `| Duration | ${prevBuild.duration} |`,
      `| Image builds passed | ~${prevBuild.pass} |`,
      `| Image builds failed | ~${prevBuild.fail} |`,
      `| Missing Dockerfile findings | ~${prevBuild.missingDockerfile} |`,
      `| Exit | 127 (summary script bug after last MISSING) |`,
      "",
      prevBuild.note + ".",
      "",
      "## Why missing Dockerfile is not always a failure",
      "",
      "- UI workspaces (`ui/*-web`, `ehr`, `portal`, etc.) are often **bundled** into `one-ui-shell` — not independent K8s deployments.",
      "- **Internal packages** (`shared-core`, `shared-ui`) are libraries, not runtime workloads.",
      "- **Mobile apps** ship as Expo artifacts, not container images.",
      "- **Generated clients** and **external dependencies** are contract-only.",
      "- **Infrastructure** (Postgres, Redis, Kafka, Keycloak, OPA, Envoy) should use **official upstream images or Helm charts**, not repo Dockerfiles.",
      "",
      "## Canonical image strategies",
      "",
      "| Strategy | Meaning |",
      "|----------|---------|",
      "| `dockerfile` | Dedicated Dockerfile + `docker build` |",
      "| `shared-dockerfile-template` | Pre-built JAR + `scripts/build/templates/impilo-jre-runtime.Dockerfile` |",
      "| `jib` | Maven Jib (preferred over Maven-in-Alpine Dockerfiles) |",
      "| `buildpacks` | Optional `pack build` for standalone UI (usually skipped) |",
      "| `official-upstream-image` | Pull upstream image (no local build) |",
      "| `official-helm-chart` | Deploy via Helm chart reference |",
      "| `not-required-*` | Non-runtime component — skip image build |",
      "| `unknown-needs-review` | Advisory until classified |",
      "| `missing-required-image-strategy` | **Blocking** for required full-boot services |",
      "",
      "## Blocking vs advisory",
      "",
      "**Blocking:** required runtime service lacks valid strategy; strategy build fails; required official image/chart undefined.",
      "",
      "**Advisory:** internal package, mobile, generated client, doctrine-only, bundled UI workspace, optional service with valid skip reason.",
      "",
      "## Current classification snapshot",
      "",
      `**Total components:** ${catalog.length}`,
      "",
      ...Object.entries(counts)
        .filter(([, n]) => n > 0)
        .sort((a, b) => b[1] - a[1])
        .map(([k, n]) => `- **${k}**: ${n}`),
      "",
      `**Runtime image required:** ${catalog.filter((c) => c.image_required).length}`,
      "",
      `**Missing required strategy:** ${catalog.filter((c) => c.image_strategy === "missing-required-image-strategy").length}`,
      "",
      "Regenerate: `node scripts/full-boot/generate-full-boot-artifacts.mjs`",
    ].join("\n") + "\n",
    "utf8"
  );

  const missingDockerRows = buildpackUi.map((e) => [
    e.id,
    e.source_path ?? `ui/${e.id}`,
    e.primary_plane,
    e.component_type,
    "MISSING Dockerfile (legacy)",
    e.image_strategy,
    e.blocker ? "yes" : "no",
    e.reason ?? "Bundled UI — buildpacks optional",
    "No Dockerfile unless independent deploy required",
  ]);
  fs.writeFileSync(
    path.join(ROOT, "docs/environment/MISSING_DOCKERFILE_RECLASSIFICATION.md"),
    [
      "# Missing Dockerfile Reclassification",
      "",
      "> Former **~23 missing Dockerfile** findings from the legacy image build — reclassified below.",
      "",
      "These are primarily **frontend workspaces** without `ui/<name>/Dockerfile`. They are **not** blocking full boot.",
      "",
      toMdTable(
        ["Item", "Path", "Plane", "Type", "Previous finding", "New strategy", "Blocking", "Reason", "Next action"],
        missingDockerRows
      ),
      "",
      `**Count:** ${missingDockerRows.length} UI/buildpack workspaces reclassified as non-blocking.`,
    ].join("\n") + "\n",
    "utf8"
  );

  const javaRows = javaServices.map((e) => {
    const df = e.dockerfile_path ?? "—";
    const rec =
      e.image_strategy === "jib"
        ? "prebuilt-jar-runtime-image or jib"
        : e.image_strategy === "shared-dockerfile-template"
          ? "prebuilt-jar-runtime-image (current)"
          : e.image_strategy === "dockerfile"
            ? "current Dockerfile acceptable"
            : "needs review";
    return [
      e.id,
      e.source_path,
      e.full_boot_classification === "required_full_boot" ? "required" : "optional",
      jarExists(ROOT, e.maven_module ?? e.id) ? "yes (if reactor built)" : "no",
      df,
      e.image_strategy,
      rec,
      e.reason ?? "—",
      e.blocker ? e.recommended_next_action : "—",
    ];
  });
  fs.writeFileSync(
    path.join(ROOT, "docs/environment/JAVA_SERVICE_IMAGE_STRATEGY_REVIEW.md"),
    [
      "# Java Service Image Strategy Review",
      "",
      "> Prefer **pre-built JAR + shared JRE template** or **Jib**. Avoid Maven inside Alpine runtime Dockerfiles.",
      "",
      `**Maven services in catalog:** ${javaRows.length}`,
      "",
      toMdTable(
        ["Service", "Path", "Full-boot", "JAR after build", "Dockerfile", "Strategy", "Recommended", "Reason", "Changes"],
        javaRows
      ),
    ].join("\n") + "\n",
    "utf8"
  );

  const infraRows = infra.map((e) => [
    e.id,
    e.official_image ?? "—",
    e.official_chart ?? "—",
    "pin in values/compose",
    e.official_chart ? "Helm subchart or impilo-vnext values" : "compose/k3s manifest",
    "no",
    e.reason ?? "Official upstream",
    "Use pinned versions in deploy; no custom Dockerfile unless fork required",
  ]);
  fs.writeFileSync(
    path.join(ROOT, "docs/environment/INFRASTRUCTURE_IMAGE_STRATEGY_REVIEW.md"),
    [
      "# Infrastructure Image Strategy Review",
      "",
      "> Identity, policy, database, cache, broker, object storage, ingress — **official images/charts** unless custom implementation required.",
      "",
      toMdTable(
        ["Component", "Official image", "Helm chart", "Version strategy", "Helm release", "Local build", "Reason", "Notes"],
        infraRows
      ),
      "",
      "See also: `compose/`, `deploy/helm/impilo-vnext/values.yaml`, `docs/environment/FULL_BOOT_INFRASTRUCTURE_PLAN.md`.",
    ].join("\n") + "\n",
    "utf8"
  );
}

function writeRepoScanList(facts) {
  ensureDir(REPORTS_DIR);
  const lines = [
    ...[...facts.poms].map((m) => `services/${m}/pom.xml`),
    ...[...facts.dockerfiles.values()],
    ...facts.uiWorkspaces.map((w) => `ui/${w}/package.json`),
    ...facts.openapi.map((o) => `contracts/openapi/${o}`),
  ];
  fs.writeFileSync(path.join(REPORTS_DIR, "repo-deployable-candidate-files.txt"), lines.sort().join("\n") + "\n", "utf8");
}

function validateRegistryInventoryContract(catalog, registry) {
  const regServices = registry.services ?? [];
  const regIds = new Set(regServices.map((s) => s.id));
  const clsById = new Map(catalog.map((c) => [c.id, c]));
  const missing = [...regIds].filter((id) => !clsById.has(id));
  const byLane = {};
  for (const e of catalog) {
    const lane = deploymentLane(e);
    byLane[lane] = (byLane[lane] ?? 0) + 1;
  }
  const runtimeK8s = catalog.filter(isRuntimeK8sMicroservice).length;
  const buildValidate = catalog.filter((c) => deploymentLane(c) === "build_validate_only").length;
  const inv = {
    generated_at: new Date().toISOString(),
    registry_service_count: regIds.size,
    classification_total: catalog.length,
    registry_services_missing_from_classification: missing,
    unclassified_registry_gaps: missing.length,
    runtime_k8s_microservice_count: runtimeK8s,
    wave_runtime_service_count: catalog.filter(isWaveRuntimeService).length,
    build_validate_only_count: buildValidate,
    by_deployment_lane: byLane,
  };
  ensureDir(REPORTS_DIR);
  fs.writeFileSync(
    path.join(REPORTS_DIR, "registry-inventory-contract.json"),
    JSON.stringify(inv, null, 2),
    "utf8"
  );
  if (missing.length) {
    throw new Error(
      `Registry inventory gap: ${missing.length} service(s) missing from classification: ${missing.slice(0, 8).join(", ")}`
    );
  }
  console.log(
    `registry inventory OK: ${regIds.size} registry services, ${catalog.length} classifications, runtime_k8s=${runtimeK8s}`
  );
}

function validateFullBootWaves(catalog) {
  const wavesPath = path.join(ROOT, "config/full-boot-waves.yml");
  if (!fs.existsSync(wavesPath)) {
    throw new Error("Missing config/full-boot-waves.yml — run wave generator or commit waves file");
  }
  const wavesDoc = readYaml(wavesPath);
  const clsById = new Map(catalog.map((c) => [c.id, c]));
  const runtimeIds = new Set(catalog.filter(isWaveRuntimeService).map((c) => c.id));
  const assigned = new Set();
  const dupes = [];
  const unknown = [];
  for (const wave of wavesDoc.waves ?? []) {
    for (const sid of wave.services ?? []) {
      if (!clsById.has(sid)) unknown.push(sid);
      if (assigned.has(sid)) dupes.push(sid);
      assigned.add(sid);
    }
  }
  if (dupes.length) {
    throw new Error(`full-boot-waves.yml duplicate service ids: ${[...new Set(dupes)].join(", ")}`);
  }
  if (unknown.length) {
    throw new Error(
      `full-boot-waves.yml unknown ids (not in classification): ${unknown.slice(0, 12).join(", ")}${unknown.length > 12 ? "…" : ""}`
    );
  }
  const missing = [...runtimeIds].filter((id) => !assigned.has(id));
  const extra = [...assigned].filter((id) => !runtimeIds.has(id));
  if (missing.length) {
    throw new Error(
      `full-boot-waves.yml missing ${missing.length} runtime service(s): ${missing.slice(0, 12).join(", ")}${missing.length > 12 ? "…" : ""}`
    );
  }
  if (extra.length) {
    throw new Error(
      `full-boot-waves.yml extra ids (not wave-runtime): ${extra.slice(0, 12).join(", ")}${extra.length > 12 ? "…" : ""}`
    );
  }
  console.log(
    `full-boot-waves.yml OK: ${wavesDoc.waves?.length ?? 0} waves, ${assigned.size} services assigned`
  );
}

function main() {
  const registry = readYaml(REGISTRY_PATH);
  const archRegistry = exists("docs/architecture/services-registry.yaml") ? readYaml(ARCH_REGISTRY_PATH) : { services: [] };
  const facts = scanRepoFacts();
  const raw = buildUnifiedCatalog(registry, archRegistry, facts);
  const catalog = raw.map((e) => enrichEntry(e, facts));

  ensureDir(REPORTS_DIR);
  writeSevenPlaneDoc(catalog);
  writeServiceCatalog(catalog);
  writeDoctrineIndex();
  writeDoctrineComplianceMatrix(catalog);
  writeApiContractCatalog(facts);
  writeClassificationYaml(catalog);
  writeMatrices(catalog, facts);
  writeEnvironmentStrategyDocs(catalog, facts);
  writeRepoScanList(facts);
  validateRegistryInventoryContract(catalog, registry);
  validateFullBootWaves(catalog);

  // Regenerate runtime values overlay. Do NOT default to --max-wave 0 (that disables 71
  // microservices and clobbers the committed wave-7 overlay). Set FULL_BOOT_MAX_WAVE=N
  // only when intentionally generating a partial wave slice.
  try {
    const maxWave = process.env.FULL_BOOT_MAX_WAVE;
    const waveArg =
      maxWave !== undefined && String(maxWave).trim() !== ""
        ? ` --max-wave ${String(maxWave).trim()}`
        : "";
    execSync(`node scripts/full-boot/generate-full-preview-runtime-values.mjs${waveArg}`, {
      cwd: ROOT,
      stdio: ["ignore", "inherit", "inherit"],
    });
  } catch (e) {
    console.warn("WARN: runtime values generation failed:", e?.message ?? e);
  }

  const counts = strategyCounts(catalog);
  const summary = {
    total: catalog.length,
    by_plane: Object.fromEntries(PLANE_META.map(([id]) => [id, catalog.filter((c) => c.primary_plane === id).length])),
    by_classification: catalog.reduce((acc, c) => {
      acc[c.full_boot_classification] = (acc[c.full_boot_classification] ?? 0) + 1;
      return acc;
    }, {}),
    image_strategy: counts,
    runtime_image_required_count: catalog.filter((c) => c.image_required).length,
    dockerfile_count: counts["dockerfile"] ?? 0,
    shared_template_count: counts["shared-dockerfile-template"] ?? 0,
    jib_count: counts["jib"] ?? 0,
    buildpacks_count: counts["buildpacks"] ?? 0,
    official_image_count: counts["official-upstream-image"] ?? 0,
    official_chart_count: counts["official-helm-chart"] ?? 0,
    not_required_count: Object.entries(counts)
      .filter(([k]) => k.startsWith("not-required"))
      .reduce((s, [, n]) => s + n, 0),
    unknown_needs_review_count: counts["unknown-needs-review"] ?? 0,
    missing_required_image_strategy: counts["missing-required-image-strategy"] ?? 0,
  };
  fs.writeFileSync(path.join(REPORTS_DIR, "discovery-summary.json"), JSON.stringify(summary, null, 2), "utf8");
  console.log("Full-boot artifacts generated:", summary);
}

main();
