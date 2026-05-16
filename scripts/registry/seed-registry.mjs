/**
 * Canonical registry hardener: writes docs/registry/services-registry.yaml using
 * the seven-plane production model and Maven reactor discovery.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import yaml from "js-yaml";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../..");
const OUT = path.join(ROOT, "docs/registry/services-registry.yaml");
const POM = path.join(ROOT, "services/pom.xml");

const libraries = [
  { id: "shared-kernel-java", maven_module: "shared-kernel-java", path: "libs/shared-kernel-java" },
  { id: "security-baseline", maven_module: "security-baseline", path: "libs/security-baseline" },
  { id: "shared-core", maven_module: "shared-core", path: "services/shared-core" },
  { id: "tshepo-contracts", maven_module: "tshepo-contracts", path: "libs/tshepo-contracts" },
  { id: "tshepo-sdk", maven_module: "tshepo-sdk", path: "libs/tshepo-sdk" },
  { id: "tech-companion", maven_module: "tech-companion", path: "libs/tech-companion" },
  { id: "federation-connector", maven_module: "federation-connector", path: "libs/federation-connector" },
  { id: "tech-companion-harness", maven_module: "tech-companion-harness", path: "libs/tech-companion-harness" },
  { id: "tech-companion-mock", maven_module: "tech-companion-mock", path: "libs/tech-companion-mock" },
  { id: "ops-instrumentation", maven_module: "ops-instrumentation", path: "libs/ops-instrumentation" },
  { id: "offline-sdk", maven_module: "offline-sdk", path: "libs/offline-sdk" },
  { id: "contract-tests", maven_module: "contract-tests", path: "libs/contract-tests" },
];

const LEGACY = new Map(
  [
    ["tshepo-service", { id: "tshepo-service", plane: "trust", sovereign: true, sovereign_group: "TSHEPO", primary_protocol: "rest", default_http_port: 8079, product_names: ["TSHEPO", "legacy monolith"] }],
    ["tshepo-authz-service", { id: "tshepo-authz-service", plane: "trust", sovereign: true, sovereign_group: "TSHEPO", primary_protocol: "mixed", default_http_port: 8081, product_names: ["TSHEPO Authz"] }],
    ["tshepo-identity-service", { id: "tshepo-identity-service", plane: "trust", sovereign: true, sovereign_group: "TSHEPO", primary_protocol: "rest", default_http_port: 8181, product_names: ["TSHEPO Identity"] }],
    ["tshepo-consent-service", { id: "tshepo-consent-service", plane: "trust", sovereign: true, sovereign_group: "TSHEPO", primary_protocol: "rest", default_http_port: 8182, product_names: ["TSHEPO Consent"] }],
    ["tshepo-audit-service", { id: "tshepo-audit-service", plane: "trust", sovereign: true, sovereign_group: "TSHEPO", primary_protocol: "rest", default_http_port: 8183, product_names: ["TSHEPO Audit"] }],
    ["tshepo-keys-service", { id: "tshepo-keys-service", plane: "trust", sovereign: true, sovereign_group: "TSHEPO", primary_protocol: "rest", default_http_port: 8184, product_names: ["TSHEPO Keys"] }],
    ["tshepo-offline-service", { id: "tshepo-offline-service", plane: "trust", sovereign: true, sovereign_group: "TSHEPO", primary_protocol: "rest", default_http_port: 8185, product_names: ["TSHEPO Offline"] }],
    ["vito-service", { id: "vito-service", plane: "registry", sovereign: true, sovereign_group: "VITO", primary_protocol: "rest", default_http_port: 8082, product_names: ["VITO"] }],
    ["varapi-service", { id: "varapi-service", plane: "registry", sovereign: true, sovereign_group: "VARAPI", primary_protocol: "rest", default_http_port: 8083, product_names: ["VARAPI"] }],
    ["tuso-service", { id: "tuso-service", plane: "registry", sovereign: true, sovereign_group: "TUSO", primary_protocol: "rest", default_http_port: 8084, product_names: ["TUSO"] }],
    ["zibo-service", { id: "zibo-service", plane: "registry", sovereign: true, sovereign_group: "ZIBO", primary_protocol: "rest", default_http_port: 8085, product_names: ["ZIBO"] }],
    ["msika-service", { id: "msika-service", plane: "registry", sovereign: true, sovereign_group: "MSIKA", primary_protocol: "rest", default_http_port: 8086, product_names: ["MSIKA"] }],
    ["ubomi-service", { id: "ubomi-service", plane: "registry", sovereign: true, sovereign_group: "UBOMI", primary_protocol: "rest", default_http_port: 8087, product_names: ["UBOMI"] }],
    ["indawo-service", { id: "indawo-service", plane: "registry", sovereign: false, primary_protocol: "rest", default_http_port: 8150, product_names: ["INDAWO"] }],
    ["product-registry-service", { id: "product-registry-service", plane: "registry", sovereign: false, primary_protocol: "rest", default_http_port: 8097, product_names: ["Product Registry"] }],
    ["butano-service", { id: "butano-service", plane: "clinical", sovereign: true, sovereign_group: "BUTANO", primary_protocol: "fhir", default_http_port: 8090, product_names: ["BUTANO", "HAPI SHR"] }],
    ["butano-fhir", { id: "butano-fhir", plane: "clinical", sovereign: true, sovereign_group: "BUTANO", primary_protocol: "fhir", default_http_port: 8289, product_names: ["BUTANO FHIR"] }],
    ["fhir-gateway-service", { id: "fhir-gateway-service", plane: "clinical", sovereign: true, sovereign_group: "BUTANO", primary_protocol: "fhir", default_http_port: 8091, product_names: ["FHIR Gateway"] }],
    ["pct-service", { id: "pct-service", plane: "clinical", sovereign: false, primary_protocol: "rest", default_http_port: 8088, product_names: ["PCT"] }],
    ["oros-service", { id: "oros-service", plane: "clinical", sovereign: false, primary_protocol: "rest", default_http_port: 8089, product_names: ["OROS"] }],
    ["pharmacy-service", { id: "pharmacy-service", plane: "clinical", sovereign: false, primary_protocol: "rest", default_http_port: 8096, product_names: ["Pharmacy"] }],
    ["pharmacy-elmis-adapter", { id: "pharmacy-elmis-adapter", plane: "clinical", sovereign: false, primary_protocol: "rest", default_http_port: 8099, product_names: ["Pharmacy eLMIS"] }],
    ["inpatient-service", { id: "inpatient-service", plane: "clinical", sovereign: false, primary_protocol: "rest", default_http_port: 8121, product_names: ["Inpatient"] }],
    ["inventory-service", { id: "inventory-service", plane: "clinical", sovereign: false, primary_protocol: "rest", default_http_port: 8098, product_names: ["Inventory"] }],
    ["inventory-elmis-adapter", { id: "inventory-elmis-adapter", plane: "clinical", sovereign: false, primary_protocol: "rest", default_http_port: 8108, product_names: ["Inventory eLMIS"] }],
    ["document-service", { id: "document-service", plane: "clinical", sovereign: false, primary_protocol: "rest", default_http_port: 8093, product_names: ["Document Store"] }],
    ["pacs-adapter-service", { id: "pacs-adapter-service", plane: "clinical", sovereign: false, primary_protocol: "rest", default_http_port: 8113, product_names: ["PACS Adapter"] }],
    ["clinical-knowledge-platform-service", { id: "clinical-knowledge-platform-service", plane: "knowledge", sovereign: false, primary_protocol: "rest", default_http_port: 8270, product_names: ["Clinical Knowledge Platform"] }],
    ["guidance-service", { id: "guidance-service", plane: "knowledge", sovereign: false, primary_protocol: "rest", default_http_port: 8260, product_names: ["Guidance"] }],
    ["connector-fhir-adapter", { id: "connector-fhir-adapter", plane: "integration", sovereign: false, primary_protocol: "fhir", default_http_port: 8151, product_names: ["Connector FHIR"] }],
    ["mushex-service", { id: "mushex-service", plane: "finance", sovereign: true, sovereign_group: "MUSheX", primary_protocol: "rest", default_http_port: 8102, product_names: ["MUSheX"] }],
    ["costing-engine-service", { id: "costing-engine-service", plane: "finance", sovereign: false, primary_protocol: "rest", default_http_port: 8101, product_names: ["COSTA"] }],
    ["coverage-service", { id: "coverage-service", plane: "finance", sovereign: false, primary_protocol: "rest", default_http_port: 8140, product_names: ["Coverage"] }],
    ["credential-verification-service", { id: "credential-verification-service", plane: "finance", sovereign: false, primary_protocol: "rest", default_http_port: 8094, product_names: ["Credential Verification"] }],
    ["share-slip-service", { id: "share-slip-service", plane: "finance", sovereign: false, primary_protocol: "rest", default_http_port: 8104, product_names: ["Share Slip"] }],
    ["msika-flow-service", { id: "msika-flow-service", plane: "marketplace", sovereign: false, primary_protocol: "rest", default_http_port: 8100, product_names: ["Msika Flow"] }],
    ["landela-adapter-service", { id: "landela-adapter-service", plane: "integration", sovereign: false, primary_protocol: "rest", default_http_port: 8092, product_names: ["Landela"] }],
    ["integration-hub", { id: "integration-hub", plane: "integration", sovereign: false, primary_protocol: "rest", default_http_port: 8110, product_names: ["Integration Hub"] }],
    ["notification-service", { id: "notification-service", plane: "integration", sovereign: false, primary_protocol: "rest", default_http_port: 8200, product_names: ["Notification"] }],
    ["jobs-service", { id: "jobs-service", plane: "integration", sovereign: false, primary_protocol: "rest", default_http_port: 8109, product_names: ["Jobs"] }],
    ["offline-sync-service", { id: "offline-sync-service", plane: "integration", sovereign: false, primary_protocol: "rest", default_http_port: 8095, product_names: ["Offline Sync"] }],
    ["card-print-agent", { id: "card-print-agent", plane: "integration", sovereign: false, primary_protocol: "rest", default_http_port: 8291, product_names: ["Card Print Agent"] }],
    ["channels-service", { id: "channels-service", plane: "integration", sovereign: false, primary_protocol: "rest", default_http_port: 8130, product_names: ["Channels"] }],
    ["workflow-service", { id: "workflow-service", plane: "integration", sovereign: false, primary_protocol: "rest", default_http_port: 8250, product_names: ["Workflow"] }],
    ["rules-service", { id: "rules-service", plane: "knowledge", sovereign: false, primary_protocol: "rest", default_http_port: 8241, product_names: ["Rules"] }],
    ["forms-service", { id: "forms-service", plane: "knowledge", sovereign: false, primary_protocol: "rest", default_http_port: 8240, product_names: ["Forms"] }],
    ["search-service", { id: "search-service", plane: "knowledge", sovereign: false, primary_protocol: "rest", default_http_port: 8230, product_names: ["Search"] }],
    ["data-pipeline-service", { id: "data-pipeline-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8215, product_names: ["Data Pipeline"] }],
    ["national-data-repository-service", { id: "national-data-repository-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8152, product_names: ["National Data Repository"] }],
    ["ndr-service", { id: "ndr-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8232, product_names: ["NDR"] }],
    ["reporting-service", { id: "reporting-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8176, product_names: ["Reporting"] }],
    ["data-warehouse-service", { id: "data-warehouse-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8233, product_names: ["Data Warehouse"] }],
    ["data-ingestion-service", { id: "data-ingestion-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8210, product_names: ["Data Ingestion"] }],
    ["data-governance-service", { id: "data-governance-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8220, product_names: ["Data Governance"] }],
    ["data-access-governance-service", { id: "data-access-governance-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8170, product_names: ["DAGS"] }],
    ["surveillance-service", { id: "surveillance-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8180, product_names: ["Surveillance"] }],
    ["campaigns-service", { id: "campaigns-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8190, product_names: ["Campaigns"] }],
    ["observability-service", { id: "observability-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8211, product_names: ["Observability"] }],
    ["security-hardening-service", { id: "security-hardening-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8221, product_names: ["Security Hardening"] }],
    ["identity-assurance-service", { id: "identity-assurance-service", plane: "trust", sovereign: false, primary_protocol: "rest", default_http_port: 8201, product_names: ["Identity Assurance"] }],
    ["audit-ledger-service", { id: "audit-ledger-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8350, product_names: ["Audit Ledger"] }],
    ["asset-registry-service", { id: "asset-registry-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8310, product_names: ["Asset Registry"] }],
    ["dispatch-service", { id: "dispatch-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8320, product_names: ["Dispatch"] }],
    ["iot-ingestion-service", { id: "iot-ingestion-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8330, product_names: ["IoT Ingestion"] }],
    ["support-service", { id: "support-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8340, product_names: ["Support"] }],
    ["offline-edge-service", { id: "offline-edge-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8360, product_names: ["Offline Edge"] }],
    ["developer-portal-service", { id: "developer-portal-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8370, product_names: ["Developer Portal"] }],
    ["schema-registry-service", { id: "schema-registry-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8371, product_names: ["Schema Registry"] }],
    ["experience-bff", { id: "experience-bff", plane: "experience", sovereign: false, primary_protocol: "rest", default_http_port: 8160, product_names: ["Experience BFF"] }],
  ]
);

const DOCTRINE_OVERRIDES = new Map(
  [
    [
      "simba-service",
      {
        primary_plane: "enterprise",
        plane: "enterprise",
        domain: "wellness-personal-health-data",
        secondary_planes: ["experience", "data", "integration", "registry", "trust"],
        system_of_record_for: [
          "wellness journeys",
          "lifestyle plans",
          "self-care plans",
          "preventive care workflows",
          "wellness goals",
          "habit tracking workflows",
          "coaching and nudge workflows",
          "wellness programme participation",
          "longitudinal wellness progress",
          "connected source registry and permissions",
          "personal wellness readings and manual entries",
          "wellness remote monitoring alerts",
        ],
        forbidden_responsibilities: [
          "must-not-own-clinical-encounter-lifecycle",
          "must-not-own-acute-care-orders-or-results",
          "must-not-own-prescription-dispensing",
          "must-not-own-inpatient-care-state",
          "must-not-own-patient-identity-source-of-truth",
          "must-not-own-provider-identity-source-of-truth",
          "must-not-own-facility-registry",
          "must-not-own-consent-policy-authority",
          "must-not-own-payment-ledgers",
          "must-not-own-public-health-surveillance-source-of-truth",
        ],
      },
    ],
    [
      "wellness-service",
      {
        primary_plane: "enterprise",
        plane: "enterprise",
        domain: "wellness-compatibility-alias",
        secondary_planes: ["experience", "data", "registry", "trust"],
        system_of_record_for: [],
        consumes_from: ["simba-service"],
        exposes_to: ["experience-bff", "integration-hub"],
        forbidden_responsibilities: [
          "must-not-own-public-health-surveillance-source-of-truth",
          "must-not-own-clinical-encounter-lifecycle",
          "must-not-own-marketplace-or-payment-ledgers",
          "must-not-own-patient-identity-source-of-truth",
          "must-not-own-provider-identity-source-of-truth",
          "must-not-own-facility-registry",
        ],
        frontend_wiring_status: "unknown-or-partial",
      },
    ],
    [
      "surveillance-service",
      {
        domain: "public-health-surveillance",
        secondary_planes: ["clinical", "experience", "integration", "registry", "trust"],
        system_of_record_for: [
          "public-health surveillance signals and case aggregates",
          "surveillance alert definitions and epidemiological counters",
          "notifiable event monitoring telemetry",
        ],
        forbidden_responsibilities: [
          "must-not-own-individual-clinical-encounter-record",
          "must-not-own-patient-identity-source-of-truth",
          "must-not-bypass-data-governance-or-consent-policy",
          "must-not-store-clinical-source-of-truth-outside-governed-clinical-shr-boundaries",
        ],
      },
    ],
    [
      "campaigns-service",
      {
        domain: "public-health-campaigns",
        secondary_planes: ["clinical", "experience", "integration", "registry", "trust"],
        system_of_record_for: [
          "public-health campaign definitions",
          "campaign outreach plans and schedules",
          "campaign execution state and coverage metrics",
        ],
        forbidden_responsibilities: [
          "must-not-own-individual-clinical-encounter-record",
          "must-not-own-patient-identity-source-of-truth",
          "must-not-bypass-data-governance-or-consent-policy",
          "must-not-store-clinical-source-of-truth-outside-governed-clinical-shr-boundaries",
        ],
      },
    ],
  ]
);

function parseServiceModules() {
  const pomText = fs.readFileSync(POM, "utf8");
  const modules = [...pomText.matchAll(/<module>([^<]+)<\/module>/g)].map((m) => m[1].trim());
  const fromPom = modules
    .filter((m) => !m.startsWith("../"))
    .filter((m) => m !== "shared-core")
    .sort((a, b) => a.localeCompare(b));
  const servicesDir = path.join(ROOT, "services");
  const fromFs = fs
    .readdirSync(servicesDir, { withFileTypes: true })
    .filter((d) => d.isDirectory())
    .map((d) => d.name)
    .filter((m) => m !== "shared-core")
    .filter((m) => fs.existsSync(path.join(servicesDir, m, "pom.xml")));
  return [...new Set([...fromPom, ...fromFs])].sort((a, b) => a.localeCompare(b));
}

function toTitle(module) {
  return module
    .replace(/-service$/, "")
    .split("-")
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
    .join(" ");
}

function mapPlaneDomain(legacyPlane, module) {
  if (module.includes("mvumo") || module.includes("identity-assurance")) {
    return { primary_plane: "trust", domain: "identity-governance" };
  }
  if (module.includes("general-ledger") || module.includes("hr-payroll") || module.includes("procurement")) {
    return { primary_plane: "enterprise", domain: "enterprise-resource" };
  }
  if (module.includes("coverage") || module.includes("mushex") || module.includes("wallet") || module.includes("cost")) {
    return { primary_plane: "enterprise", domain: "finance" };
  }
  if (module.includes("msika")) {
    return { primary_plane: "enterprise", domain: "marketplace" };
  }
  if (module.includes("ai-model") || module.includes("report") || module.includes("search")) {
    return { primary_plane: "data", domain: "intelligence" };
  }
  if (module.includes("community") || module.includes("learning") || module.includes("wellness")) {
    return { primary_plane: "experience", domain: "workflow-orchestration" };
  }
  if (module.includes("simba") || module.includes("scheduling")) {
    return { primary_plane: "clinical", domain: "care-delivery" };
  }
  if (module.includes("workforce-governance")) {
    return { primary_plane: "enterprise", domain: "workforce-operations" };
  }

  const isClinicalKnowledge =
    module.includes("guidance") || module.includes("rules") || module.includes("forms") || module.includes("clinical-knowledge");
  const isTerminology = module.includes("zibo");
  const isAnalyticsKnowledge = module.includes("search") || module.includes("ai-model");
  if (legacyPlane === "trust") return { primary_plane: "trust", domain: "identity-governance" };
  if (legacyPlane === "registry") return { primary_plane: "registry", domain: isTerminology ? "terminology" : "registry-spine" };
  if (legacyPlane === "clinical") return { primary_plane: "clinical", domain: "care-delivery" };
  if (legacyPlane === "data") return { primary_plane: "data", domain: "intelligence" };
  if (legacyPlane === "integration") return { primary_plane: "integration", domain: "interoperability" };
  if (legacyPlane === "experience") return { primary_plane: "experience", domain: "workflow-orchestration" };
  if (legacyPlane === "finance") return { primary_plane: "enterprise", domain: "finance" };
  if (legacyPlane === "marketplace") return { primary_plane: "enterprise", domain: "marketplace" };
  if (legacyPlane === "ops") return { primary_plane: "integration", domain: "platform-ops" };
  if (legacyPlane === "knowledge") {
    if (isClinicalKnowledge) return { primary_plane: "clinical", domain: "clinical-knowledge" };
    if (isTerminology) return { primary_plane: "registry", domain: "terminology" };
    if (isAnalyticsKnowledge) return { primary_plane: "data", domain: "intelligence" };
    return { primary_plane: "clinical", domain: "clinical-knowledge" };
  }
  return { primary_plane: "integration", domain: "platform-ops" };
}

function defaultForbidden(plane) {
  const map = {
    trust: ["must-not-own-clinical-record-content", "must-not-own-billing-ledgers"],
    registry: ["must-not-authorize-access-decisions", "must-not-own-clinical-encounters"],
    clinical: ["must-not-act-as-identity-source-of-record", "must-not-own-enterprise-ledgering"],
    data: ["must-not-handle-care-transaction-orchestration", "must-not-bypass-consent-governance"],
    integration: ["must-not-become-system-of-record-for-clinical-or-finance", "must-not-embed-actor-facing-business-workflows"],
    experience: ["must-not-own-domain-source-data", "must-not-bypass-bff-authz-audit-controls"],
    enterprise: ["must-not-store-clinical-records-as-source-of-truth", "must-not-own-identity-assurance-policy"],
  };
  return map[plane] ?? ["requires-architectural-decision"];
}

function enrichService(module) {
  const legacy = LEGACY.get(module) ?? {};
  const legacyPlane = legacy.plane ?? "ops";
  const mapping = mapPlaneDomain(legacyPlane, module);
  const productNames = legacy.product_names ?? [toTitle(module)];
  const id = legacy.id ?? module;
  const systemOfRecord = [`${toTitle(module)} canonical records`];
  const consumesFrom = [];
  if (mapping.primary_plane !== "trust") consumesFrom.push("tshepo-authz-service");
  if (mapping.primary_plane === "experience") consumesFrom.push("multiple-domain-services-via-bff");
  const exposesTo = mapping.primary_plane === "experience" ? ["web-mobile-experience"] : ["experience-bff", "integration-hub"];

  const base = {
    id,
    maven_module: module,
    primary_plane: mapping.primary_plane,
    plane: mapping.primary_plane,
    domain: mapping.domain,
    secondary_planes: [],
    sovereign: Boolean(legacy.sovereign),
    sovereign_group: legacy.sovereign_group ?? null,
    primary_protocol: legacy.primary_protocol ?? "rest",
    default_http_port: legacy.default_http_port ?? null,
    product_names: productNames,
    owner_team: "TBD",
    system_of_record_for: systemOfRecord,
    consumes_from: consumesFrom,
    exposes_to: exposesTo,
    forbidden_responsibilities: defaultForbidden(mapping.primary_plane),
    production_status: "baseline-assessed",
    implementation_status: "implemented-or-partial",
    frontend_wiring_status: mapping.primary_plane === "experience" ? "wired" : "unknown-or-partial",
    api_contract_status: "partial",
    authz_audit_status: "partial",
    observability_status: "partial",
  };
  const override = DOCTRINE_OVERRIDES.get(module);
  return override ? { ...base, ...override } : base;
}

const services = parseServiceModules().map(enrichService);

const doc = {
  registry_version: "2",
  schema: {
    id: "Stable service id",
    maven_module: "Directory name under services/",
    primary_plane: "trust | registry | clinical | data | integration | experience | enterprise",
    plane: "Compatibility alias generated from primary_plane",
    domain: "Business/domain ownership under the primary plane",
    secondary_planes: "Optional integration touch-points (not ownership)",
    sovereign: "Member of a sovereign boundary",
    sovereign_group: "Sovereign product grouping",
    primary_protocol: "rest | fhir | grpc | mixed",
    default_http_port: "Default local HTTP port",
    product_names: "Human-facing names",
    owner_team: "Owning squad",
    system_of_record_for: "Authoritative record responsibilities",
    consumes_from: "Upstream authoritative dependencies",
    exposes_to: "Downstream consumers",
    forbidden_responsibilities: "Explicit anti-responsibilities",
    production_status: "production readiness disposition",
    implementation_status: "implementation depth",
    frontend_wiring_status: "experience wiring depth",
    api_contract_status: "contract readiness",
    authz_audit_status: "authz and audit readiness",
    observability_status: "metrics/logs/tracing readiness",
  },
  owner_team_default: "TBD",
  port_allocation_doc: "docs/runbooks/port-allocation.md",
  libraries,
  services,
};

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(
  OUT,
  "# Impilo service registry (Production Architecture Baseline)\n" +
    "# Generated by scripts/registry/seed-registry.mjs\n" +
    "# Source inputs: services/pom.xml + curated legacy metadata map\n\n" +
    yaml.dump(doc, { lineWidth: 120, noRefs: true }),
  "utf8"
);
console.log("Wrote", path.relative(ROOT, OUT));
