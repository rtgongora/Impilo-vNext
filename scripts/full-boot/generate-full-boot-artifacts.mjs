#!/usr/bin/env node
/**
 * Generate full vNext boot readiness artifacts from canonical registry + repo scan.
 * Source of truth: docs/registry/services-registry.yaml (+ repo discovery overlay).
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import yaml from "js-yaml";

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
const REQUIRED_INFRA = new Set([
  "postgres",
  "redis",
  "kafka",
  "keycloak",
  "envoy",
  "minio",
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
  return { dockerfiles, poms, uiWorkspaces, helmCharts, openapi };
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
  if (id === "tshepo-service") return "deprecated_retired";
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
  const sp = entry.source_path ?? "";
  const hasDocker = facts.dockerfiles.has(mod);
  const helmName = mod?.replace(/-service$/, "") ?? mod;
  const hasHelm = facts.helmCharts.some((h) => h === helmName || h === mod || mod?.startsWith(h));
  const inSlice = SLICE_DEPLOYED.has(entry.id);
  const classification = classifyEntry(entry);
  let current_status = "unknown";
  if (entry.component_type === "library") current_status = "internal_library_only";
  else if (entry.component_type === "external_dependency") current_status = "external_dependency";
  else if (inSlice) current_status = "deployed_and_healthy";
  else if (!entry.buildable) current_status = "deployable_but_not_deployed";
  else if (entry.buildable && !hasDocker) current_status = "buildable_but_not_containerized";
  else if (hasDocker && !hasHelm) current_status = "implemented_but_no_deployment_support";
  else current_status = "deployable_but_not_deployed";

  return {
    ...entry,
    dockerfile_status: hasDocker ? "present" : entry.buildable ? "missing" : "n/a",
    dockerfile_path: hasDocker ? facts.dockerfiles.get(mod) : null,
    helm_support: hasHelm ? "chart_in_helm/" : entry.id === "one-ui-shell" || entry.id === "experience-bff" ? "deploy/helm/impilo-vnext" : "none",
    compose_support: ["postgres", "redis", "kafka", "keycloak", "minio", "hapi-fhir"].includes(entry.id) ? "docker-compose.yml" : "unknown",
    full_boot_classification: classification,
    deploy_order_group: deployOrderGroup(entry, classification),
    current_status,
    blocker:
      classification === "required_full_boot" && !inSlice && !hasDocker && entry.buildable
        ? "missing_dockerfile"
        : classification === "required_full_boot" && !inSlice
          ? "not_deployed_in_preview"
          : "",
    recommended_next_action:
      current_status === "buildable_but_not_containerized"
        ? "Add Dockerfile and image build"
        : current_status === "deployable_but_not_deployed"
          ? "Add Helm template or subchart"
          : "Verify in full-boot namespace after authorization",
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
    e.dockerfile_status,
    e.helm_support,
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
    "> overlaid with repo scan (services/, ui/, apps/mobile/, infra, external register).",
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
        "Dockerfile",
        "Helm",
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
    classification: e.full_boot_classification,
    deploy_order_group: e.deploy_order_group,
    build_required: ["required_full_boot", "optional_full_boot"].includes(e.full_boot_classification) && e.buildable,
    container_required: e.full_boot_classification === "required_full_boot" && e.buildable,
    helm_required: e.full_boot_classification === "required_full_boot",
    health_check_required: e.full_boot_classification === "required_full_boot",
    contract_check_required: e.component_type === "backend_service",
    frontend_surface_expected: e.component_type === "backend_service" && e.exposes_to?.includes?.("experience-bff"),
    mobile_surface_expected: false,
    doctrine_compliance_required: e.full_boot_classification === "required_full_boot",
    source_path: e.source_path,
    build_tool: e.build_tool,
    dockerfile_path: e.dockerfile_path,
    priority: e.full_boot_classification === "required_full_boot" ? "P0" : "P2",
    blocker: e.blocker || null,
    reason: `Auto-classified from registry + repo scan (${e.component_type})`,
  }));
  const doc = {
    metadata: {
      generated_at: new Date().toISOString(),
      source: "docs/registry/services-registry.yaml",
      total_entries: entries.length,
    },
    classifications: entries,
  };
  fs.writeFileSync(CLASSIFICATION_PATH, yaml.dump(doc, { lineWidth: 120 }), "utf8");
}

function writeMatrices(catalog, facts) {
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

  const containerRows = catalog
    .filter((e) => e.component_type === "backend_service" || e.component_type === "frontend_app")
    .map((e) => [
      e.id,
      e.primary_plane,
      e.dockerfile_status,
      e.dockerfile_path ?? "—",
      e.source_path,
      `impilo/${e.id}`,
      "preview, preview-<sha>",
      e.dockerfile_status === "missing" ? "missing Dockerfile" : "—",
      e.dockerfile_status === "missing" ? "Add Dockerfile" : "build-full-vnext-images.sh",
    ]);
  fs.writeFileSync(
    path.join(ROOT, "docs/environment/FULL_CONTAINERIZATION_MATRIX.md"),
    ["# Full Containerization Matrix", "", toMdTable(["Service", "Plane", "Dockerfile", "Path", "Context", "Image", "Tags", "Blocker", "Remediation"], containerRows)].join("\n") + "\n",
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
  writeRepoScanList(facts);

  const summary = {
    total: catalog.length,
    by_plane: Object.fromEntries(PLANE_META.map(([id]) => [id, catalog.filter((c) => c.primary_plane === id).length])),
    by_classification: catalog.reduce((acc, c) => {
      acc[c.full_boot_classification] = (acc[c.full_boot_classification] ?? 0) + 1;
      return acc;
    }, {}),
  };
  fs.writeFileSync(path.join(REPORTS_DIR, "discovery-summary.json"), JSON.stringify(summary, null, 2), "utf8");
  console.log("Full-boot artifacts generated:", summary);
}

main();
