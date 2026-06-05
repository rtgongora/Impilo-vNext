#!/usr/bin/env node
/**
 * Product Truth Recovery Map generator — Phase 1 discovery scan.
 * Run from repo root: node scripts/product/generate-product-truth-recovery.mjs
 *
 * Outputs:
 *   docs/product/PRODUCT_TRUTH_RECOVERY_MAP.md
 *   docs/product/PRODUCT_TRUTH_RECOVERY_PHASE_REPORT.md
 *   reports/product/product-truth-recovery-map.json
 *   reports/product/product-truth-recovery-map.csv
 *   reports/product/product-truth-rollups.md
 *   reports/product/product-truth-rollups.json
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { CAPABILITIES } from "../frontend/generate-parity-docs.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../..");
const DATE = new Date().toISOString();
const DATE_SHORT = DATE.slice(0, 10);

const OUT_DOCS = path.join(ROOT, "docs/product");
const OUT_REPORTS = path.join(ROOT, "reports/product");

const CLASSIFICATIONS = [
  "backend-capability",
  "api-contract",
  "frontend-route",
  "mobile-screen",
  "bff-api-facade",
  "worker-job",
  "integration-adapter",
  "registry-service",
  "trust-security-service",
  "clinical-service",
  "fhir-shared-record-service",
  "finance-transaction-service",
  "document-imaging-service",
  "data-analytics-ai-service",
  "offline-federation-service",
  "infrastructure-dependency",
  "shared-internal-library",
  "generated-client",
  "doctrine-only",
  "deprecated-retired",
  "unknown-needs-review",
];

/** @type {Array<Record<string, string>>} */
const entries = [];

function parseYaml(filePath) {
  const script = `
import json, yaml, sys
with open(sys.argv[1], encoding="utf-8") as f:
    print(json.dumps(yaml.safe_load(f)))
`;
  const result = spawnSync("python3", ["-c", script, filePath], { encoding: "utf8" });
  if (result.status !== 0) {
    throw new Error(`Failed to parse YAML ${filePath}: ${result.stderr}`);
  }
  return JSON.parse(result.stdout);
}

function walkFiles(dir, filter = () => true, acc = []) {
  if (!fs.existsSync(dir)) return acc;
  for (const name of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, name.name);
    if (name.isDirectory()) {
      if (["node_modules", "target", ".git", ".next", "dist", "build"].includes(name.name)) continue;
      walkFiles(p, filter, acc);
    } else if (filter(p)) {
      acc.push(p);
    }
  }
  return acc;
}

function rel(p) {
  return path.relative(ROOT, p).replace(/\\/g, "/");
}

function readText(p) {
  try {
    return fs.readFileSync(p, "utf8");
  } catch {
    return "";
  }
}

function makeEntry(fields) {
  const entry = {
    canonicalName: "",
    aliases: "",
    sourcePath: "",
    componentType: "",
    serviceDomainPlane: "",
    capabilityDescription: "",
    backendApiContractStatus: "",
    frontendSurfaceExpected: "",
    currentFrontendRouteComponent: "",
    mobileSurfaceExpected: "",
    currentMobileScreenComponent: "",
    apiClientHook: "",
    workflowEventDependency: "",
    databaseMigrationDependency: "",
    trustSecurityDependency: "",
    relatedServices: "",
    currentCompletenessStatus: "",
    currentBlocker: "",
    recommendedClassification: "unknown-needs-review",
    ...fields,
  };
  entries.push(entry);
  return entry;
}

function classifyByPlaneAndModule(module, plane, domain) {
  const m = (module || "").toLowerCase();
  if (m.includes("tshepo") || m.includes("audit") || m.includes("identity-assurance") || m.includes("credential")) {
    return "trust-security-service";
  }
  if (m.includes("vito") || m.includes("varapi") || m.includes("tuso") || m.includes("zibo") || m.includes("product-registry") || m.includes("asset-registry")) {
    return "registry-service";
  }
  if (m.includes("butano") || m.includes("fhir") || m.includes("pct") || m.includes("pharmacy") || m.includes("inpatient") || m.includes("clinical")) {
    return "clinical-service";
  }
  if (m.includes("costa") || m.includes("costing") || m.includes("mushe") || m.includes("mushex") || m.includes("coverage") || m.includes("oros") || m.includes("msika") || m.includes("general-ledger") || m.includes("procurement") || m.includes("hr-payroll")) {
    return "finance-transaction-service";
  }
  if (m.includes("document") || m.includes("pacs") || m.includes("landela") || m.includes("imaging")) {
    return "document-imaging-service";
  }
  if (m.includes("offline") || m.includes("federation") || m.includes("connector")) {
    return "offline-federation-service";
  }
  if (plane === "data" || m.includes("analytics") || m.includes("warehouse") || m.includes("ndr") || m.includes("surveillance") || m.includes("ai-model") || m.includes("llm")) {
    return "data-analytics-ai-service";
  }
  if (m.includes("adapter") || m.includes("connector") || m.includes("gateway") || m.includes("hub")) {
    return "integration-adapter";
  }
  if (m.includes("jobs") || m.includes("worker") || m.includes("card-print")) {
    return "worker-job";
  }
  if (m === "shared-core" || m.includes("shared-") || m.includes("-sdk") || m.includes("-contracts")) {
    return "shared-internal-library";
  }
  if (plane === "experience" || m.includes("experience") || m.includes("wellness") || m.includes("guidance") || m.includes("search") || m.includes("workflow") || m.includes("community") || m.includes("notification")) {
    return "backend-capability";
  }
  return "backend-capability";
}

function hookForRoute(routePath) {
  const segment = routePath.split("/").filter(Boolean)[0] || "";
  const hooksDir = path.join(ROOT, "ui/one-ui-shell/src/hooks/queries");
  if (!fs.existsSync(hooksDir)) return "";
  const files = fs.readdirSync(hooksDir);
  const match = files.find((f) => {
    const base = f.replace(/^use/, "").replace(/\.ts$/, "").toLowerCase();
    return segment && base.includes(segment.toLowerCase());
  });
  return match ? `hooks/queries/${match}` : "";
}

function capabilityForDomain(domain) {
  return CAPABILITIES.find((c) => c.domain.toLowerCase() === (domain || "").toLowerCase());
}

// ── 1. Services registry ────────────────────────────────────────────────────
const registryPath = path.join(ROOT, "docs/registry/services-registry.yaml");
const registry = parseYaml(registryPath);
const serviceByModule = new Map();

for (const lib of registry.libraries || []) {
  serviceByModule.set(lib.maven_module || lib.id, lib);
  makeEntry({
    canonicalName: lib.id,
    aliases: lib.maven_module || "",
    sourcePath: rel(path.join(ROOT, lib.path || `libs/${lib.maven_module}`)),
    componentType: "shared-library",
    serviceDomainPlane: "platform",
    capabilityDescription: `Shared internal library: ${lib.id}`,
    backendApiContractStatus: "n/a",
    frontendSurfaceExpected: "no",
    currentFrontendRouteComponent: "",
    mobileSurfaceExpected: "no",
    currentMobileScreenComponent: "",
    apiClientHook: "",
    workflowEventDependency: "",
    databaseMigrationDependency: "none",
    trustSecurityDependency: "indirect",
    relatedServices: "all services",
    currentCompletenessStatus: "library",
    currentBlocker: "",
    recommendedClassification: "shared-internal-library",
  });
}

for (const svc of registry.services || []) {
  serviceByModule.set(svc.maven_module || svc.id, svc);
  const modulePath = path.join(ROOT, "services", svc.maven_module);
  const hasOpenApi = walkFiles(path.join(ROOT, "contracts/openapi"), (p) =>
    p.endsWith(".yaml") && readText(p).includes(svc.maven_module)
  ).length > 0;
  const openApiFile = walkFiles(path.join(ROOT, "contracts/openapi"), (p) => {
    if (!p.endsWith(".yaml")) return false;
    const stem = path.basename(p, ".openapi.yaml").replace(".openapi", "");
    return stem === svc.id.replace(/-service$/, "") || stem === svc.maven_module.replace(/-service$/, "");
  })[0];
  const migrations = walkFiles(modulePath, (p) => /db\/migration\/V\d+__/.test(p));
  const controllers = walkFiles(modulePath, (p) => p.endsWith("Controller.java"));
  const cap = CAPABILITIES.find(
    (c) => c.domain.toLowerCase() === (svc.product_names?.[0] || svc.id).split(" ")[0].toLowerCase()
  );

  makeEntry({
    canonicalName: svc.id,
    aliases: (svc.product_names || []).join("; "),
    sourcePath: rel(modulePath),
    componentType: "backend-service",
    serviceDomainPlane: `${svc.primary_plane || svc.plane}/${svc.domain || "general"}`,
    capabilityDescription:
      (svc.system_of_record_for || []).join("; ") ||
      `Backend service module ${svc.maven_module}`,
    backendApiContractStatus: openApiFile
      ? `contract: ${rel(openApiFile)}; status=${svc.api_contract_status || "unknown"}`
      : `no-matched-openapi; status=${svc.api_contract_status || "unknown"}`,
    frontendSurfaceExpected: svc.frontend_wiring_status === "wired" ? "yes" : "partial",
    currentFrontendRouteComponent: cap?.webRoute || "",
    mobileSurfaceExpected: cap?.mobile === "yes" || cap?.mobile === "partial" ? cap.mobile : "unknown",
    currentMobileScreenComponent: cap?.mobile === "yes" ? "apps/mobile/*" : "",
    apiClientHook: cap?.webClient || "",
    workflowEventDependency: (svc.consumes_from || []).join("; "),
    databaseMigrationDependency: migrations.length
      ? `${migrations.length} migration(s) in ${svc.maven_module}`
      : "none detected",
    trustSecurityDependency: "TSHEPO ext_authz via Envoy",
    relatedServices: [...(svc.consumes_from || []), ...(svc.exposes_to || [])].join("; "),
    currentCompletenessStatus: `production=${svc.production_status || "?"}; impl=${svc.implementation_status || "?"}; frontend=${svc.frontend_wiring_status || "?"}`,
    currentBlocker: cap?.gap || "",
    recommendedClassification: classifyByPlaneAndModule(svc.maven_module, svc.primary_plane, svc.domain),
  });

  for (const ctrl of controllers) {
    const text = readText(ctrl);
    const mapping = text.match(/@RequestMapping\(\s*"([^"]+)"/)?.[1] || "/v1";
    const className = path.basename(ctrl, ".java");
    makeEntry({
      canonicalName: `${svc.id}:${className}`,
      aliases: mapping,
      sourcePath: rel(ctrl),
      componentType: "backend-controller",
      serviceDomainPlane: `${svc.primary_plane}/${svc.domain}`,
      capabilityDescription: `REST controller for ${svc.id}`,
      backendApiContractStatus: openApiFile ? rel(openApiFile) : "contract-unmatched",
      frontendSurfaceExpected: "via-bff",
      currentFrontendRouteComponent: "",
      mobileSurfaceExpected: "via-bff-mobile",
      currentMobileScreenComponent: "",
      apiClientHook: "",
      workflowEventDependency: "",
      databaseMigrationDependency: "",
      trustSecurityDependency: "TSHEPO ext_authz",
      relatedServices: svc.id,
      currentCompletenessStatus: svc.implementation_status || "unknown",
      currentBlocker: "",
      recommendedClassification:
        svc.maven_module === "experience-bff" ? "bff-api-facade" : classifyByPlaneAndModule(svc.maven_module, svc.primary_plane, svc.domain),
    });
  }
}

// ── 2. OpenAPI contracts ────────────────────────────────────────────────────
const openApiFiles = walkFiles(path.join(ROOT, "contracts/openapi"), (p) => p.endsWith(".yaml") || p.endsWith(".yml"));
for (const f of openApiFiles) {
  const text = readText(f);
  const title = text.match(/title:\s*(.+)/)?.[1]?.trim() || path.basename(f);
  const pathCount = (text.match(/^\s{2}\/[^\s:]+:/gm) || []).length;
  const opCount = (text.match(/operationId:/g) || []).length;
  makeEntry({
    canonicalName: path.basename(f),
    aliases: title,
    sourcePath: rel(f),
    componentType: "openapi-contract",
    serviceDomainPlane: "contracts",
    capabilityDescription: `OpenAPI contract with ~${pathCount} paths, ~${opCount} operations`,
    backendApiContractStatus: `paths=${pathCount}; operations=${opCount}`,
    frontendSurfaceExpected: "via-generated-client-or-bff",
    currentFrontendRouteComponent: "",
    mobileSurfaceExpected: "via-bff-mobile",
    currentMobileScreenComponent: "",
    apiClientHook: "",
    workflowEventDependency: "",
    databaseMigrationDependency: "",
    trustSecurityDependency: "contract-defined",
    relatedServices: "",
    currentCompletenessStatus: pathCount > 0 ? "documented" : "empty-or-stub",
    currentBlocker: pathCount === 0 ? "empty contract" : "",
    recommendedClassification: "api-contract",
  });
}

// ── 3. AsyncAPI / event contracts ───────────────────────────────────────────
const asyncApiFiles = walkFiles(path.join(ROOT, "contracts/asyncapi"), (p) => p.endsWith(".yaml") || p.endsWith(".yml"));
for (const f of asyncApiFiles) {
  const text = readText(f);
  const channels = (text.match(/^\s{2}[^\s#][^:]+:/gm) || []).length;
  makeEntry({
    canonicalName: path.basename(f),
    aliases: "",
    sourcePath: rel(f),
    componentType: "asyncapi-contract",
    serviceDomainPlane: "events",
    capabilityDescription: `AsyncAPI event contract (~${channels} channel definitions)`,
    backendApiContractStatus: "event-contract",
    frontendSurfaceExpected: "indirect",
    currentFrontendRouteComponent: "",
    mobileSurfaceExpected: "indirect",
    currentMobileScreenComponent: "",
    apiClientHook: "",
    workflowEventDependency: `kafka/outbox; channels~${channels}`,
    databaseMigrationDependency: "event_outbox tables",
    trustSecurityDependency: "audit-trail",
    relatedServices: "",
    currentCompletenessStatus: "documented",
    currentBlocker: "",
    recommendedClassification: "api-contract",
  });
}

// ── 4. BFF route prefixes (deduplicated) ────────────────────────────────────
const bffRoot = path.join(ROOT, "services/experience-bff");
const bffControllers = walkFiles(bffRoot, (p) => p.endsWith("Controller.java"));
const bffPrefixes = new Set();
for (const ctrl of bffControllers) {
  const text = readText(ctrl);
  const mappings = [...text.matchAll(/@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]+)"/g)].map((m) => m[1]);
  for (const mapping of mappings) {
    bffPrefixes.add(mapping);
  }
  const className = path.basename(ctrl, ".java");
  const prefix = mappings[0] || "/internal/v1";
  makeEntry({
    canonicalName: `bff:${className}`,
    aliases: prefix,
    sourcePath: rel(ctrl),
    componentType: "bff-controller",
    serviceDomainPlane: "experience/bff",
    capabilityDescription: `Experience BFF controller at ${prefix}`,
    backendApiContractStatus: "experience-bff.openapi.yaml (partial baseline)",
    frontendSurfaceExpected: "yes",
    currentFrontendRouteComponent: "",
    mobileSurfaceExpected: prefix.includes("/mobile/") ? "yes" : "partial",
    currentMobileScreenComponent: prefix.includes("/mobile/") ? "apps/mobile/*" : "",
    apiClientHook: "",
    workflowEventDependency: "",
    databaseMigrationDependency: "",
    trustSecurityDependency: "TSHEPO session + ext_authz",
    relatedServices: "experience-bff",
    currentCompletenessStatus: "implemented",
    currentBlocker: "BFF OpenAPI documents only ~189 of ~1406 handlers",
    recommendedClassification: "bff-api-facade",
  });
}
for (const prefix of [...bffPrefixes].sort()) {
  makeEntry({
    canonicalName: `bff-route:${prefix}`,
    aliases: "",
    sourcePath: "services/experience-bff",
    componentType: "bff-route-prefix",
    serviceDomainPlane: "experience/bff",
    capabilityDescription: `BFF HTTP prefix ${prefix}`,
    backendApiContractStatus: "runtime-implemented",
    frontendSurfaceExpected: "yes",
    currentFrontendRouteComponent: "",
    mobileSurfaceExpected: prefix.includes("/mobile/") ? "yes" : "unknown",
    currentMobileScreenComponent: "",
    apiClientHook: "",
    workflowEventDependency: "",
    databaseMigrationDependency: "",
    trustSecurityDependency: "TSHEPO",
    relatedServices: "experience-bff",
    currentCompletenessStatus: "implemented",
    currentBlocker: "",
    recommendedClassification: "bff-api-facade",
  });
}

// ── 5. Frontend routes (routes.ts registry) ─────────────────────────────────
const routesTs = readText(path.join(ROOT, "ui/one-ui-shell/src/lib/routes.ts"));
const routeMatches = [...routesTs.matchAll(/\{\s*path:\s*"([^"]+)"[^}]*zone:\s*"([^"]+)"[^}]*pageTitle:\s*"([^"]+)"/g)];
const registeredPaths = new Set();
for (const [, routePath, zone, pageTitle] of routeMatches) {
  registeredPaths.add(routePath);
  const pagePath = path.join(ROOT, "ui/one-ui-shell/src/app", routePath.replace(/^\//, ""), "page.tsx");
  const altPage = path.join(ROOT, "ui/one-ui-shell/src/app", routePath.replace(/^\//, "") + "/page.tsx");
  const exists = fs.existsSync(pagePath) || fs.existsSync(altPage);
  const hook = hookForRoute(routePath);
  makeEntry({
    canonicalName: routePath,
    aliases: pageTitle,
    sourcePath: exists ? rel(fs.existsSync(pagePath) ? pagePath : altPage) : "ui/one-ui-shell/src/lib/routes.ts",
    componentType: "frontend-route",
    serviceDomainPlane: `experience/${zone}`,
    capabilityDescription: `Registered web route: ${pageTitle}`,
    backendApiContractStatus: "via-bff",
    frontendSurfaceExpected: "yes",
    currentFrontendRouteComponent: routePath,
    mobileSurfaceExpected: "parity-expected",
    currentMobileScreenComponent: "",
    apiClientHook: hook,
    workflowEventDependency: "",
    databaseMigrationDependency: "",
    trustSecurityDependency: "session + trust headers",
    relatedServices: "experience-bff",
    currentCompletenessStatus: exists ? "routed-with-page" : "registered-no-page",
    currentBlocker: exists ? "" : "page.tsx missing on disk",
    recommendedClassification: "frontend-route",
  });
}

// ── 6. On-disk pages not in registry ────────────────────────────────────────
const pageFiles = walkFiles(path.join(ROOT, "ui/one-ui-shell/src/app"), (p) => p.endsWith("page.tsx"));
for (const page of pageFiles) {
  const appRel = rel(page).replace("ui/one-ui-shell/src/app/", "");
  const routePath = "/" + appRel.replace(/\/page\.tsx$/, "").replace(/\[([^\]]+)\]/g, "[$1]");
  const normalized = routePath === "/" ? "/" : routePath.replace(/\/$/, "") || "/";
  if (registeredPaths.has(normalized) || registeredPaths.has(routePath)) continue;
  makeEntry({
    canonicalName: routePath,
    aliases: "unregistered",
    sourcePath: rel(page),
    componentType: "frontend-page-unregistered",
    serviceDomainPlane: "experience/unregistered",
    capabilityDescription: "On-disk page not in routes.ts registry",
    backendApiContractStatus: "unknown",
    frontendSurfaceExpected: "review",
    currentFrontendRouteComponent: routePath,
    mobileSurfaceExpected: "unknown",
    currentMobileScreenComponent: "",
    apiClientHook: hookForRoute(routePath),
    workflowEventDependency: "",
    databaseMigrationDependency: "",
    trustSecurityDependency: "unknown",
    relatedServices: "",
    currentCompletenessStatus: "unregistered",
    currentBlocker: "not in routes.ts — guard/sidebar coverage gap",
    recommendedClassification: "unknown-needs-review",
  });
}

// ── 7. Query hooks / API clients ────────────────────────────────────────────
const hookFiles = walkFiles(path.join(ROOT, "ui/one-ui-shell/src/hooks/queries"), (p) => p.endsWith(".ts"));
for (const hook of hookFiles) {
  const text = readText(hook);
  const bffPaths = [...text.matchAll(/["'](\/internal\/v1\/[^"']+)["']/g)].map((m) => m[1]);
  const uniquePaths = [...new Set(bffPaths)].slice(0, 5).join("; ");
  makeEntry({
    canonicalName: path.basename(hook, ".ts"),
    aliases: uniquePaths,
    sourcePath: rel(hook),
    componentType: "api-query-hook",
    serviceDomainPlane: "experience/web-client",
    capabilityDescription: `TanStack Query hook calling BFF (${bffPaths.length} endpoint refs)`,
    backendApiContractStatus: "bff-proxied",
    frontendSurfaceExpected: "yes",
    currentFrontendRouteComponent: "",
    mobileSurfaceExpected: "no",
    currentMobileScreenComponent: "",
    apiClientHook: rel(hook),
    workflowEventDependency: "",
    databaseMigrationDependency: "",
    trustSecurityDependency: "api-client trust headers",
    relatedServices: "experience-bff",
    currentCompletenessStatus: bffPaths.length > 0 ? "wired" : "no-bff-paths-detected",
    currentBlocker: bffPaths.length === 0 ? "no /internal/v1 references" : "",
    recommendedClassification: bffPaths.length > 0 ? "generated-client" : "unknown-needs-review",
  });
}

// ── 8. Mobile screens ─────────────────────────────────────────────────────────
for (const app of ["citizen-app", "provider-app"]) {
  const screensRoot = path.join(ROOT, "apps/mobile", app, "src/screens");
  const screens = walkFiles(screensRoot, (p) => p.endsWith(".tsx") && !p.includes(".test."));
  for (const screen of screens) {
    const screenRel = rel(screen);
    const text = readText(screen);
    const bffPaths = [...text.matchAll(/["'](\/internal\/v1\/[^"']+)["']/g)].map((m) => m[1]);
    const serviceImports = [...text.matchAll(/from\s+["']@\/services\/([^"']+)["']/g)].map((m) => m[1]);
    makeEntry({
      canonicalName: `${app}:${path.basename(screen, ".tsx")}`,
      aliases: screenRel,
      sourcePath: screenRel,
      componentType: "mobile-screen",
      serviceDomainPlane: `experience/mobile/${app.replace("-app", "")}`,
      capabilityDescription: `Mobile screen in ${app}`,
      backendApiContractStatus: "bff-mobile-proxied",
      frontendSurfaceExpected: "web-parity-expected",
      currentFrontendRouteComponent: "",
      mobileSurfaceExpected: "yes",
      currentMobileScreenComponent: screenRel,
      apiClientHook: serviceImports.join("; ") || "@impilo/mobile-api-client",
      workflowEventDependency: "",
      databaseMigrationDependency: "",
      trustSecurityDependency: "mobile-trust headers",
      relatedServices: "experience-bff",
      currentCompletenessStatus: bffPaths.length + serviceImports.length > 0 ? "wired" : "ui-only",
      currentBlocker: "",
      recommendedClassification: "mobile-screen",
    });
  }
}

// ── 9. Mobile shared packages ───────────────────────────────────────────────
const mobilePackages = walkFiles(path.join(ROOT, "apps/mobile/packages"), (p) => p.endsWith("package.json"));
for (const pkg of mobilePackages) {
  const name = JSON.parse(readText(pkg)).name || path.basename(path.dirname(pkg));
  makeEntry({
    canonicalName: name,
    aliases: "",
    sourcePath: rel(path.dirname(pkg)),
    componentType: "mobile-shared-package",
    serviceDomainPlane: "experience/mobile/shared",
    capabilityDescription: `Mobile shared package ${name}`,
    backendApiContractStatus: "n/a",
    frontendSurfaceExpected: "no",
    currentFrontendRouteComponent: "",
    mobileSurfaceExpected: "yes",
    currentMobileScreenComponent: "",
    apiClientHook: name === "@impilo/mobile-api-client" ? "mobile-api-client" : "",
    workflowEventDependency: "",
    databaseMigrationDependency: "",
    trustSecurityDependency: name.includes("trust") ? "trust-headers" : "",
    relatedServices: "experience-bff",
    currentCompletenessStatus: "package",
    currentBlocker: "",
    recommendedClassification: "shared-internal-library",
  });
}

// ── 10. Generated clients ───────────────────────────────────────────────────
const generatedDirs = [
  path.join(ROOT, "ui/one-ui-shell/src/generated"),
  path.join(ROOT, "contracts/typescript"),
];
for (const dir of generatedDirs) {
  const files = walkFiles(dir, () => true);
  for (const f of files) {
    makeEntry({
      canonicalName: path.basename(f),
      aliases: "",
      sourcePath: rel(f),
      componentType: "generated-client",
      serviceDomainPlane: "contracts/generated",
      capabilityDescription: "Generated TypeScript client or registry artifact",
      backendApiContractStatus: "generated-from-contract",
      frontendSurfaceExpected: "indirect",
      currentFrontendRouteComponent: "",
      mobileSurfaceExpected: "no",
      currentMobileScreenComponent: "",
      apiClientHook: rel(f),
      workflowEventDependency: "",
      databaseMigrationDependency: "",
      trustSecurityDependency: "",
      relatedServices: "",
      currentCompletenessStatus: "generated",
      currentBlocker: "",
      recommendedClassification: "generated-client",
    });
  }
}

// ── 11. Database migrations ─────────────────────────────────────────────────
const migrationFiles = walkFiles(path.join(ROOT, "services"), (p) => /db\/migration\/V\d+__/.test(p));
const migrationsByService = new Map();
for (const m of migrationFiles) {
  const parts = rel(m).split("/");
  const svc = parts[1] || "unknown";
  if (!migrationsByService.has(svc)) migrationsByService.set(svc, []);
  migrationsByService.get(svc).push(m);
}
for (const [svc, files] of migrationsByService) {
  makeEntry({
    canonicalName: `${svc}:migrations`,
    aliases: `${files.length} files`,
    sourcePath: rel(path.dirname(files[0])),
    componentType: "database-migration-set",
    serviceDomainPlane: serviceByModule.get(svc)?.primary_plane || "unknown",
    capabilityDescription: `Flyway migrations for ${svc}`,
    backendApiContractStatus: "n/a",
    frontendSurfaceExpected: "no",
    currentFrontendRouteComponent: "",
    mobileSurfaceExpected: "no",
    currentMobileScreenComponent: "",
    apiClientHook: "",
    workflowEventDependency: "event_outbox tables likely",
    databaseMigrationDependency: files.map(rel).join("; "),
    trustSecurityDependency: "",
    relatedServices: svc,
    currentCompletenessStatus: `${files.length} migrations`,
    currentBlocker: "",
    recommendedClassification: "backend-capability",
  });
}

// ── 12. Doctrine documents ────────────────────────────────────────────────────
const doctrinePaths = [
  ...walkFiles(path.join(ROOT, "docs/doctrine"), (p) => p.endsWith(".md")),
  ...walkFiles(path.join(ROOT, "docs/product"), (p) => p.endsWith(".md")),
  path.join(ROOT, "CLAUDE.md"),
  path.join(ROOT, "AGENTS.md"),
].filter((p) => fs.existsSync(p));
for (const doc of doctrinePaths) {
  makeEntry({
    canonicalName: path.basename(doc, path.extname(doc)),
    aliases: "",
    sourcePath: rel(doc),
    componentType: "doctrine-document",
    serviceDomainPlane: "doctrine",
    capabilityDescription: "Product/architecture doctrine document",
    backendApiContractStatus: "n/a",
    frontendSurfaceExpected: "guides-implementation",
    currentFrontendRouteComponent: "",
    mobileSurfaceExpected: "guides-implementation",
    currentMobileScreenComponent: "",
    apiClientHook: "",
    workflowEventDependency: "",
    databaseMigrationDependency: "",
    trustSecurityDependency: doc.includes("trust") || doc.includes("doctrine") ? "governance" : "",
    relatedServices: "",
    currentCompletenessStatus: "documented",
    currentBlocker: "",
    recommendedClassification: "doctrine-only",
  });
}

// ── 13. Infrastructure / deploy ─────────────────────────────────────────────
const dockerfiles = walkFiles(ROOT, (p) => path.basename(p) === "Dockerfile" || path.basename(p).startsWith("Dockerfile."));
for (const df of dockerfiles) {
  makeEntry({
    canonicalName: `dockerfile:${rel(df)}`,
    aliases: "",
    sourcePath: rel(df),
    componentType: "dockerfile",
    serviceDomainPlane: "infrastructure",
    capabilityDescription: "Container image build definition",
    backendApiContractStatus: "n/a",
    frontendSurfaceExpected: "no",
    currentFrontendRouteComponent: "",
    mobileSurfaceExpected: "no",
    currentMobileScreenComponent: "",
    apiClientHook: "",
    workflowEventDependency: "",
    databaseMigrationDependency: "",
    trustSecurityDependency: "",
    relatedServices: "",
    currentCompletenessStatus: "build-artifact",
    currentBlocker: "",
    recommendedClassification: "infrastructure-dependency",
  });
}

const helmFiles = walkFiles(path.join(ROOT, "deploy/helm"), (p) => p.endsWith(".yaml") || p.endsWith(".yml") || p.endsWith(".tpl"));
for (const hf of helmFiles.slice(0, 200)) {
  makeEntry({
    canonicalName: path.basename(hf),
    aliases: rel(path.dirname(hf)),
    sourcePath: rel(hf),
    componentType: "helm-artifact",
    serviceDomainPlane: "infrastructure/deploy",
    capabilityDescription: "Helm chart template or values",
    backendApiContractStatus: "n/a",
    frontendSurfaceExpected: "no",
    currentFrontendRouteComponent: "",
    mobileSurfaceExpected: "no",
    currentMobileScreenComponent: "",
    apiClientHook: "",
    workflowEventDependency: "",
    databaseMigrationDependency: "",
    trustSecurityDependency: "",
    relatedServices: "",
    currentCompletenessStatus: "deploy-config",
    currentBlocker: "",
    recommendedClassification: "infrastructure-dependency",
  });
}

const workflows = walkFiles(path.join(ROOT, ".github/workflows"), (p) => p.endsWith(".yml") || p.endsWith(".yaml"));
for (const wf of workflows) {
  makeEntry({
    canonicalName: path.basename(wf),
    aliases: "",
    sourcePath: rel(wf),
    componentType: "ci-workflow",
    serviceDomainPlane: "infrastructure/ci",
    capabilityDescription: "GitHub Actions workflow",
    backendApiContractStatus: "n/a",
    frontendSurfaceExpected: "no",
    currentFrontendRouteComponent: "",
    mobileSurfaceExpected: "no",
    currentMobileScreenComponent: "",
    apiClientHook: "",
    workflowEventDependency: "",
    databaseMigrationDependency: "",
    trustSecurityDependency: "",
    relatedServices: "",
    currentCompletenessStatus: "ci-pipeline",
    currentBlocker: "",
    recommendedClassification: "infrastructure-dependency",
  });
}

// ── 14. UI workspaces ───────────────────────────────────────────────────────
const uiPackage = JSON.parse(readText(path.join(ROOT, "ui/package.json")));
for (const ws of uiPackage.workspaces || []) {
  const wsPath = path.join(ROOT, "ui", ws);
  const deprecated = fs.existsSync(path.join(wsPath, "DEPRECATED.md"));
  makeEntry({
    canonicalName: ws,
    aliases: deprecated ? "deprecated" : "",
    sourcePath: rel(wsPath),
    componentType: "ui-workspace",
    serviceDomainPlane: "experience/ui",
    capabilityDescription: deprecated ? `Deprecated UI workspace: ${ws}` : `UI workspace: ${ws}`,
    backendApiContractStatus: "n/a",
    frontendSurfaceExpected: ws === "one-ui-shell" ? "canonical" : "sidecar-or-legacy",
    currentFrontendRouteComponent: ws === "one-ui-shell" ? "418 routes" : "",
    mobileSurfaceExpected: "no",
    currentMobileScreenComponent: "",
    apiClientHook: "",
    workflowEventDependency: "",
    databaseMigrationDependency: "",
    trustSecurityDependency: "",
    relatedServices: ws === "one-ui-shell" ? "experience-bff" : "",
    currentCompletenessStatus: deprecated ? "deprecated" : "active",
    currentBlocker: deprecated ? "migrated to one-ui-shell" : "",
    recommendedClassification: deprecated ? "deprecated-retired" : ws === "one-ui-shell" ? "frontend-route" : "unknown-needs-review",
  });
}

// ── 15. CAPABILITIES reconciliation (canonical product truth) ───────────────
for (const cap of CAPABILITIES) {
  makeEntry({
    canonicalName: `${cap.domain}: ${cap.capability}`,
    aliases: cap.backend,
    sourcePath: "scripts/frontend/generate-parity-docs.mjs",
    componentType: "canonical-capability",
    serviceDomainPlane: `${cap.plane}/${cap.domain}`,
    capabilityDescription: cap.capability,
    backendApiContractStatus: cap.contract,
    frontendSurfaceExpected: cap.web === "yes" ? "yes" : cap.web,
    currentFrontendRouteComponent: cap.webRoute,
    mobileSurfaceExpected: cap.mobile,
    currentMobileScreenComponent: "",
    apiClientHook: cap.webClient,
    workflowEventDependency: "",
    databaseMigrationDependency: "",
    trustSecurityDependency: cap.plane === "Trust" ? "critical" : "standard",
    relatedServices: cap.domain,
    currentCompletenessStatus: cap.maturity,
    currentBlocker: cap.gap,
    recommendedClassification: "backend-capability",
  });
}

// ── Rollups ─────────────────────────────────────────────────────────────────
function countByClassification() {
  const counts = {};
  for (const c of CLASSIFICATIONS) counts[c] = 0;
  for (const e of entries) {
    const k = e.recommendedClassification || "unknown-needs-review";
    counts[k] = (counts[k] || 0) + 1;
  }
  return counts;
}

function countByComponentType() {
  const counts = {};
  for (const e of entries) {
    counts[e.componentType] = (counts[e.componentType] || 0) + 1;
  }
  return counts;
}

const rollups = {
  generatedAt: DATE,
  branch: "claude/staging-ux-orchestration-remediation-Yypyl",
  totalDiscoveredItems: entries.length,
  backendServicesDiscovered: (registry.services || []).length,
  sharedLibrariesDiscovered: (registry.libraries || []).length,
  backendControllersDiscovered: entries.filter((e) => e.componentType === "backend-controller").length,
  apisContractsDiscovered: openApiFiles.length + asyncApiFiles.length,
  openapiContracts: openApiFiles.length,
  asyncapiContracts: asyncApiFiles.length,
  frontendRoutesDiscovered: entries.filter((e) => e.componentType === "frontend-route").length,
  frontendPagesUnregistered: entries.filter((e) => e.componentType === "frontend-page-unregistered").length,
  mobileScreensDiscovered: entries.filter((e) => e.componentType === "mobile-screen").length,
  bffControllersDiscovered: bffControllers.length,
  bffRoutePrefixesDiscovered: bffPrefixes.size,
  apiQueryHooksDiscovered: hookFiles.length,
  generatedClientsDiscovered: entries.filter((e) => e.recommendedClassification === "generated-client").length,
  workersJobsDiscovered: entries.filter((e) => e.recommendedClassification === "worker-job").length,
  workflowsEventsDiscovered: asyncApiFiles.length + workflows.length,
  databaseMigrationSets: migrationsByService.size,
  doctrineDocumentsDiscovered: doctrinePaths.length,
  dockerfilesDiscovered: dockerfiles.length,
  helmArtifactsDiscovered: Math.min(helmFiles.length, 200),
  ciWorkflowsDiscovered: workflows.length,
  canonicalCapabilities: CAPABILITIES.length,
  unknownNeedsReview: entries.filter((e) => e.recommendedClassification === "unknown-needs-review").length,
  classificationCounts: countByClassification(),
  componentTypeCounts: countByComponentType(),
  majorHiddenBackendAreas: CAPABILITIES.filter((c) => c.maturity === "Not Wired" || c.maturity === "Fixture").map(
    (c) => `${c.domain}: ${c.capability} (${c.maturity})`
  ),
  majorFrontendMobileGaps: CAPABILITIES.filter((c) => c.web !== "yes" || c.mobile === "no" || c.mobile === "partial").map(
    (c) => `${c.domain}: web=${c.web} mobile=${c.mobile} gap=${c.gap}`
  ),
};

// ── CSV ─────────────────────────────────────────────────────────────────────
const CSV_COLUMNS = [
  "canonicalName",
  "aliases",
  "sourcePath",
  "componentType",
  "serviceDomainPlane",
  "capabilityDescription",
  "backendApiContractStatus",
  "frontendSurfaceExpected",
  "currentFrontendRouteComponent",
  "mobileSurfaceExpected",
  "currentMobileScreenComponent",
  "apiClientHook",
  "workflowEventDependency",
  "databaseMigrationDependency",
  "trustSecurityDependency",
  "relatedServices",
  "currentCompletenessStatus",
  "currentBlocker",
  "recommendedClassification",
];

function csvEscape(val) {
  const s = String(val ?? "").replace(/"/g, '""');
  return `"${s}"`;
}

const csvLines = [CSV_COLUMNS.join(",")];
for (const e of entries) {
  csvLines.push(CSV_COLUMNS.map((c) => csvEscape(e[c])).join(","));
}

// ── Markdown helpers ──────────────────────────────────────────────────────────
function mdTable(rows, columns, maxRows = 50) {
  const slice = rows.slice(0, maxRows);
  const header = `| ${columns.join(" | ")} |`;
  const sep = `| ${columns.map(() => "---").join(" | ")} |`;
  const body = slice
    .map((r) => `| ${columns.map((c) => String(r[c] ?? "").replace(/\|/g, "\\|").slice(0, 80)).join(" | ")} |`)
    .join("\n");
  const more = rows.length > maxRows ? `\n\n_…and ${rows.length - maxRows} more rows in JSON/CSV._\n` : "";
  return `${header}\n${sep}\n${body}${more}`;
}

// ── Write outputs ───────────────────────────────────────────────────────────
fs.mkdirSync(OUT_DOCS, { recursive: true });
fs.mkdirSync(OUT_REPORTS, { recursive: true });

fs.writeFileSync(
  path.join(OUT_REPORTS, "product-truth-recovery-map.json"),
  JSON.stringify({ generatedAt: DATE, rollups, entries }, null, 2)
);
fs.writeFileSync(path.join(OUT_REPORTS, "product-truth-recovery-map.csv"), csvLines.join("\n") + "\n");
fs.writeFileSync(path.join(OUT_REPORTS, "product-truth-rollups.json"), JSON.stringify(rollups, null, 2));

const rollupsMd = `# Product Truth Rollups

> Generated: ${DATE}
> Branch: \`claude/staging-ux-orchestration-remediation-Yypyl\`
> Regenerate: \`node scripts/product/generate-product-truth-recovery.mjs\`

## Summary counts

| Metric | Count |
|--------|------:|
| Total discovered items | ${rollups.totalDiscoveredItems} |
| Backend services (registry) | ${rollups.backendServicesDiscovered} |
| Shared libraries | ${rollups.sharedLibrariesDiscovered} |
| Backend controllers | ${rollups.backendControllersDiscovered} |
| OpenAPI contracts | ${rollups.openapiContracts} |
| AsyncAPI contracts | ${rollups.asyncapiContracts} |
| APIs/contracts total | ${rollups.apisContractsDiscovered} |
| Frontend routes (registered) | ${rollups.frontendRoutesDiscovered} |
| Frontend pages (unregistered) | ${rollups.frontendPagesUnregistered} |
| Mobile screens | ${rollups.mobileScreensDiscovered} |
| BFF controllers | ${rollups.bffControllersDiscovered} |
| BFF route prefixes | ${rollups.bffRoutePrefixesDiscovered} |
| API query hooks | ${rollups.apiQueryHooksDiscovered} |
| Generated clients | ${rollups.generatedClientsDiscovered} |
| Workers/jobs | ${rollups.workersJobsDiscovered} |
| Workflows/events | ${rollups.workflowsEventsDiscovered} |
| Database migration sets | ${rollups.databaseMigrationSets} |
| Doctrine documents | ${rollups.doctrineDocumentsDiscovered} |
| Dockerfiles | ${rollups.dockerfilesDiscovered} |
| Helm artifacts (capped) | ${rollups.helmArtifactsDiscovered} |
| CI workflows | ${rollups.ciWorkflowsDiscovered} |
| Canonical capabilities | ${rollups.canonicalCapabilities} |
| Unknown-needs-review | ${rollups.unknownNeedsReview} |

## Classification breakdown

${mdTable(
  Object.entries(rollups.classificationCounts)
    .filter(([, n]) => n > 0)
    .map(([classification, count]) => ({ classification, count }))
    .sort((a, b) => b.count - a.count),
  ["classification", "count"],
  30
)}

## Component type breakdown

${mdTable(
  Object.entries(rollups.componentTypeCounts)
    .map(([componentType, count]) => ({ componentType, count }))
    .sort((a, b) => b.count - a.count),
  ["componentType", "count"],
  30
)}
`;
fs.writeFileSync(path.join(OUT_REPORTS, "product-truth-rollups.md"), rollupsMd);

const capabilityRows = entries
  .filter((e) => e.componentType === "canonical-capability")
  .map((e) => ({
    capability: e.canonicalName,
    plane: e.serviceDomainPlane,
    web: e.frontendSurfaceExpected,
    mobile: e.mobileSurfaceExpected,
    maturity: e.currentCompletenessStatus,
    blocker: e.currentBlocker,
  }));

const mapMd = `# Product Truth Recovery Map

> Generated: ${DATE}
> Branch: \`claude/staging-ux-orchestration-remediation-Yypyl\`
> Regenerate: \`node scripts/product/generate-product-truth-recovery.mjs\`

## Purpose

Authoritative Phase 1 discovery map reconciling registry, contracts, BFF, backend controllers, web routes, mobile screens, hooks, events, migrations, doctrine, and infrastructure into one product-truth inventory.

**Exhaustive machine-readable exports:**
- [product-truth-recovery-map.json](../../reports/product/product-truth-recovery-map.json) — ${rollups.totalDiscoveredItems} entries
- [product-truth-recovery-map.csv](../../reports/product/product-truth-recovery-map.csv) — same data, CSV
- [product-truth-rollups.md](../../reports/product/product-truth-rollups.md) — summary counts

## Executive summary

| Dimension | Discovered |
|-----------|----------:|
| Total items | ${rollups.totalDiscoveredItems} |
| Backend services | ${rollups.backendServicesDiscovered} |
| APIs/contracts | ${rollups.apisContractsDiscovered} |
| Frontend routes | ${rollups.frontendRoutesDiscovered} |
| Mobile screens | ${rollups.mobileScreensDiscovered} |
| BFF route prefixes | ${rollups.bffRoutePrefixesDiscovered} |
| Unknown-needs-review | ${rollups.unknownNeedsReview} |

## Canonical capabilities (embedded registry)

${mdTable(capabilityRows, ["capability", "plane", "web", "mobile", "maturity", "blocker"], 60)}

## Backend services (registry)

${mdTable(
  entries
    .filter((e) => e.componentType === "backend-service")
    .map((e) => ({
      service: e.canonicalName,
      plane: e.serviceDomainPlane,
      status: e.currentCompletenessStatus,
      contract: e.backendApiContractStatus.slice(0, 60),
      frontend: e.frontendSurfaceExpected,
      blocker: e.currentBlocker,
    })),
  ["service", "plane", "status", "contract", "frontend", "blocker"],
  40
)}

## Unregistered frontend pages (needs review)

${mdTable(
  entries
    .filter((e) => e.componentType === "frontend-page-unregistered")
    .map((e) => ({ route: e.canonicalName, path: e.sourcePath, blocker: e.currentBlocker })),
  ["route", "path", "blocker"],
  40
)}

## Entry schema

Each entry in JSON/CSV contains:

\`canonicalName\`, \`aliases\`, \`sourcePath\`, \`componentType\`, \`serviceDomainPlane\`, \`capabilityDescription\`, \`backendApiContractStatus\`, \`frontendSurfaceExpected\`, \`currentFrontendRouteComponent\`, \`mobileSurfaceExpected\`, \`currentMobileScreenComponent\`, \`apiClientHook\`, \`workflowEventDependency\`, \`databaseMigrationDependency\`, \`trustSecurityDependency\`, \`relatedServices\`, \`currentCompletenessStatus\`, \`currentBlocker\`, \`recommendedClassification\`

Classification vocabulary: ${CLASSIFICATIONS.map((c) => `\`${c}\``).join(", ")}.
`;
fs.writeFileSync(path.join(OUT_DOCS, "PRODUCT_TRUTH_RECOVERY_MAP.md"), mapMd);

const phaseReport = `# Product Truth Recovery — Phase Report

> Generated: ${DATE}
> Branch: \`claude/staging-ux-orchestration-remediation-Yypyl\`
> Phase: **1 — Discovery & Documentation Only**

## 1. What was scanned

| Domain | Paths scanned |
|--------|---------------|
| Backend services | \`services/*\` (${rollups.backendServicesDiscovered} registry services, ${rollups.backendControllersDiscovered} controllers) |
| Experience BFF | \`services/experience-bff\` (${rollups.bffControllersDiscovered} controllers, ${rollups.bffRoutePrefixesDiscovered} route prefixes) |
| API contracts | \`contracts/openapi/\` (${rollups.openapiContracts}), \`contracts/asyncapi/\` (${rollups.asyncapiContracts}) |
| Web experience | \`ui/one-ui-shell/src/app/\`, \`routes.ts\`, \`hooks/queries/\` |
| Mobile | \`apps/mobile/citizen-app\`, \`apps/mobile/provider-app\`, \`apps/mobile/packages/\` |
| Registry & doctrine | \`docs/registry/\`, \`docs/doctrine/\`, \`docs/product/\`, \`CLAUDE.md\`, \`AGENTS.md\` |
| Infrastructure | Dockerfiles (${rollups.dockerfilesDiscovered}), \`deploy/helm/\`, \`.github/workflows/\` |
| Canonical capabilities | \`scripts/frontend/generate-parity-docs.mjs\` (${rollups.canonicalCapabilities} capabilities) |
| Database | Flyway migrations across ${rollups.databaseMigrationSets} service modules |

## 2. Total components discovered

**${rollups.totalDiscoveredItems}** entries in the Product Truth Recovery Map.

## 3. Total backend capabilities

- **${rollups.backendServicesDiscovered}** registry backend services
- **${rollups.sharedLibrariesDiscovered}** shared libraries
- **${rollups.backendControllersDiscovered}** backend REST controllers
- **${rollups.bffControllersDiscovered}** BFF controllers composing sovereign services

## 4. Total APIs/contracts

- **${rollups.openapiContracts}** OpenAPI specifications
- **${rollups.asyncapiContracts}** AsyncAPI event contracts
- **${rollups.apisContractsDiscovered}** total API/event contracts

## 5. Total frontend routes

- **${rollups.frontendRoutesDiscovered}** registered routes in \`routes.ts\`
- **${rollups.frontendPagesUnregistered}** on-disk pages not in registry (guard/sidebar gap)
- **${rollups.apiQueryHooksDiscovered}** TanStack Query hooks

## 6. Total mobile screens

- **${rollups.mobileScreensDiscovered}** mobile screens (citizen + provider apps)

## 7. Major hidden backend capability areas

Backend-rich domains with partial or missing experience surfacing (from canonical capability registry):

${rollups.majorHiddenBackendAreas.length ? rollups.majorHiddenBackendAreas.map((a) => `- ${a}`).join("\n") : "_None flagged as Not Wired/Fixture._"}

Additional signal: BFF implements ~${rollups.bffRoutePrefixesDiscovered} route prefixes but \`experience-bff.openapi.yaml\` documents only a baseline subset — significant contract-runtime drift.

## 8. Major frontend/mobile visibility gaps

${rollups.majorFrontendMobileGaps.slice(0, 25).map((g) => `- ${g}`).join("\n")}

${rollups.majorFrontendMobileGaps.length > 25 ? `\n_…and ${rollups.majorFrontendMobileGaps.length - 25} more in rollups JSON._\n` : ""}

## 9. Unknown-needs-review items

**${rollups.unknownNeedsReview}** entries classified \`unknown-needs-review\`, primarily:
- Unregistered frontend pages (${rollups.frontendPagesUnregistered})
- Query hooks without detected BFF paths
- Non-canonical UI workspaces

See [product-truth-recovery-map.json](../../reports/product/product-truth-recovery-map.json) filtered by \`recommendedClassification=unknown-needs-review\`.

## 10. Recommended next phase

**Phase 2 — Core Transaction Mapping**

Using this recovery map as input:
1. Map each canonical capability to \`CoreTransactionType\` and lifecycle stage per [CORE_TRANSACTION_FEATURE_ALIGNMENT_CHECKLIST.md](../templates/CORE_TRANSACTION_FEATURE_ALIGNMENT_CHECKLIST.md)
2. Run \`scripts/architecture/audit-core-transaction-compliance.py\` and extend with actor-context matrix (citizen, provider, device, job, etc.)
3. Produce transaction spine document: actor → context → transaction → cooperating services → web/mobile journey → next action
4. Do **not** implement broad UI rewrites until transaction map is approved

## Artifacts

| Artifact | Path |
|----------|------|
| Recovery map (human) | [PRODUCT_TRUTH_RECOVERY_MAP.md](./PRODUCT_TRUTH_RECOVERY_MAP.md) |
| Recovery map (JSON) | [product-truth-recovery-map.json](../../reports/product/product-truth-recovery-map.json) |
| Recovery map (CSV) | [product-truth-recovery-map.csv](../../reports/product/product-truth-recovery-map.csv) |
| Rollups | [product-truth-rollups.md](../../reports/product/product-truth-rollups.md) |
| Generator | [generate-product-truth-recovery.mjs](../../scripts/product/generate-product-truth-recovery.mjs) |
`;
fs.writeFileSync(path.join(OUT_DOCS, "PRODUCT_TRUTH_RECOVERY_PHASE_REPORT.md"), phaseReport);

console.log("Product Truth Recovery Map generated");
console.log(`  Total entries: ${rollups.totalDiscoveredItems}`);
console.log(`  Backend services: ${rollups.backendServicesDiscovered}`);
console.log(`  APIs/contracts: ${rollups.apisContractsDiscovered}`);
console.log(`  Frontend routes: ${rollups.frontendRoutesDiscovered}`);
console.log(`  Mobile screens: ${rollups.mobileScreensDiscovered}`);
console.log(`  Unknown-needs-review: ${rollups.unknownNeedsReview}`);
console.log(`  Wrote docs/product/ and reports/product/`);
