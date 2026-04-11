/**
 * One-shot / refresh helper: writes docs/registry/services-registry.yaml from structured data.
 * Run: node seed-registry.mjs   (from scripts/registry, after npm install)
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import yaml from "js-yaml";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../..");
const OUT = path.join(ROOT, "docs/registry/services-registry.yaml");

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

/** @type {Array<Record<string, unknown>>} */
const services = [
  // Trust — TSHEPO stack (sovereign 1)
  { id: "tshepo-service", maven_module: "tshepo-service", plane: "trust", sovereign: true, sovereign_group: "TSHEPO", primary_protocol: "rest", default_http_port: 8079, product_names: ["TSHEPO", "legacy monolith"] },
  { id: "tshepo-authz-service", maven_module: "tshepo-authz-service", plane: "trust", sovereign: true, sovereign_group: "TSHEPO", primary_protocol: "mixed", default_http_port: 8081, product_names: ["TSHEPO Authz"] },
  { id: "tshepo-identity-service", maven_module: "tshepo-identity-service", plane: "trust", sovereign: true, sovereign_group: "TSHEPO", primary_protocol: "rest", default_http_port: 8181, product_names: ["TSHEPO Identity"] },
  { id: "tshepo-consent-service", maven_module: "tshepo-consent-service", plane: "trust", sovereign: true, sovereign_group: "TSHEPO", primary_protocol: "rest", default_http_port: 8182, product_names: ["TSHEPO Consent"] },
  { id: "tshepo-audit-service", maven_module: "tshepo-audit-service", plane: "trust", sovereign: true, sovereign_group: "TSHEPO", primary_protocol: "rest", default_http_port: 8183, product_names: ["TSHEPO Audit"] },
  { id: "tshepo-keys-service", maven_module: "tshepo-keys-service", plane: "trust", sovereign: true, sovereign_group: "TSHEPO", primary_protocol: "rest", default_http_port: 8184, product_names: ["TSHEPO Keys"] },
  { id: "tshepo-offline-service", maven_module: "tshepo-offline-service", plane: "trust", sovereign: true, sovereign_group: "TSHEPO", primary_protocol: "rest", default_http_port: 8185, product_names: ["TSHEPO Offline"] },
  // Registry spine — sovereign 2–6, 8
  { id: "vito-service", maven_module: "vito-service", plane: "registry", sovereign: true, sovereign_group: "VITO", primary_protocol: "rest", default_http_port: 8082, product_names: ["VITO"] },
  { id: "varapi-service", maven_module: "varapi-service", plane: "registry", sovereign: true, sovereign_group: "VARAPI", primary_protocol: "rest", default_http_port: 8083, product_names: ["VARAPI"] },
  { id: "tuso-service", maven_module: "tuso-service", plane: "registry", sovereign: true, sovereign_group: "TUSO", primary_protocol: "rest", default_http_port: 8084, product_names: ["TUSO"] },
  { id: "zibo-service", maven_module: "zibo-service", plane: "registry", sovereign: true, sovereign_group: "ZIBO", primary_protocol: "rest", default_http_port: 8085, product_names: ["ZIBO"] },
  { id: "msika-service", maven_module: "msika-service", plane: "registry", sovereign: true, sovereign_group: "MSIKA", primary_protocol: "rest", default_http_port: 8086, product_names: ["MSIKA"] },
  { id: "ubomi-service", maven_module: "ubomi-service", plane: "registry", sovereign: true, sovereign_group: "UBOMI", primary_protocol: "rest", default_http_port: 8087, product_names: ["UBOMI"] },
  { id: "indawo-service", maven_module: "indawo-service", plane: "registry", sovereign: false, primary_protocol: "rest", default_http_port: 8150, product_names: ["INDAWO"] },
  { id: "product-registry-service", maven_module: "product-registry-service", plane: "registry", sovereign: false, primary_protocol: "rest", default_http_port: 8097, product_names: ["Product Registry"] },
  // BUTANO stack — sovereign 7
  { id: "butano-service", maven_module: "butano-service", plane: "clinical", sovereign: true, sovereign_group: "BUTANO", primary_protocol: "fhir", default_http_port: 8090, product_names: ["BUTANO", "HAPI SHR"] },
  { id: "butano-fhir", maven_module: "butano-fhir", plane: "clinical", sovereign: true, sovereign_group: "BUTANO", primary_protocol: "fhir", default_http_port: 8289, product_names: ["BUTANO FHIR"] },
  { id: "fhir-gateway-service", maven_module: "fhir-gateway-service", plane: "clinical", sovereign: true, sovereign_group: "BUTANO", primary_protocol: "fhir", default_http_port: 8091, product_names: ["FHIR Gateway"] },
  // Clinical execution
  { id: "pct-service", maven_module: "pct-service", plane: "clinical", sovereign: false, primary_protocol: "rest", default_http_port: 8088, product_names: ["PCT"] },
  { id: "oros-service", maven_module: "oros-service", plane: "clinical", sovereign: false, primary_protocol: "rest", default_http_port: 8089, product_names: ["OROS"] },
  { id: "pharmacy-service", maven_module: "pharmacy-service", plane: "clinical", sovereign: false, primary_protocol: "rest", default_http_port: 8096, product_names: ["Pharmacy"] },
  { id: "pharmacy-elmis-adapter", maven_module: "pharmacy-elmis-adapter", plane: "clinical", sovereign: false, primary_protocol: "rest", default_http_port: 8099, product_names: ["Pharmacy eLMIS"] },
  { id: "inpatient-service", maven_module: "inpatient-service", plane: "clinical", sovereign: false, primary_protocol: "rest", default_http_port: 8121, product_names: ["Inpatient"] },
  { id: "inventory-service", maven_module: "inventory-service", plane: "clinical", sovereign: false, primary_protocol: "rest", default_http_port: 8098, product_names: ["Inventory"] },
  { id: "inventory-elmis-adapter", maven_module: "inventory-elmis-adapter", plane: "clinical", sovereign: false, primary_protocol: "rest", default_http_port: 8108, product_names: ["Inventory eLMIS"] },
  { id: "document-service", maven_module: "document-service", plane: "clinical", sovereign: false, primary_protocol: "rest", default_http_port: 8093, product_names: ["Document Store"] },
  { id: "pacs-adapter-service", maven_module: "pacs-adapter-service", plane: "clinical", sovereign: false, primary_protocol: "rest", default_http_port: 8113, product_names: ["PACS Adapter"] },
  { id: "clinical-knowledge-platform-service", maven_module: "clinical-knowledge-platform-service", plane: "knowledge", sovereign: false, primary_protocol: "rest", default_http_port: 8270, product_names: ["Clinical Knowledge Platform"] },
  { id: "guidance-service", maven_module: "guidance-service", plane: "knowledge", sovereign: false, primary_protocol: "rest", default_http_port: 8260, product_names: ["Guidance"] },
  { id: "connector-fhir-adapter", maven_module: "connector-fhir-adapter", plane: "integration", sovereign: false, primary_protocol: "fhir", default_http_port: 8151, product_names: ["Connector FHIR"] },
  // Finance — sovereign 9
  { id: "mushex-service", maven_module: "mushex-service", plane: "finance", sovereign: true, sovereign_group: "MUSheX", primary_protocol: "rest", default_http_port: 8102, product_names: ["MUSheX"] },
  { id: "costing-engine-service", maven_module: "costing-engine-service", plane: "finance", sovereign: false, primary_protocol: "rest", default_http_port: 8101, product_names: ["COSTA"] },
  { id: "coverage-service", maven_module: "coverage-service", plane: "finance", sovereign: false, primary_protocol: "rest", default_http_port: 8140, product_names: ["Coverage"] },
  { id: "credential-verification-service", maven_module: "credential-verification-service", plane: "finance", sovereign: false, primary_protocol: "rest", default_http_port: 8094, product_names: ["Credential Verification"] },
  { id: "share-slip-service", maven_module: "share-slip-service", plane: "finance", sovereign: false, primary_protocol: "rest", default_http_port: 8104, product_names: ["Share Slip"] },
  // Marketplace
  { id: "msika-flow-service", maven_module: "msika-flow-service", plane: "marketplace", sovereign: false, primary_protocol: "rest", default_http_port: 8100, product_names: ["Msika Flow"] },
  // Integration / ops
  { id: "landela-adapter-service", maven_module: "landela-adapter-service", plane: "integration", sovereign: false, primary_protocol: "rest", default_http_port: 8092, product_names: ["Landela"] },
  { id: "integration-hub", maven_module: "integration-hub", plane: "integration", sovereign: false, primary_protocol: "rest", default_http_port: 8110, product_names: ["Integration Hub"] },
  { id: "notification-service", maven_module: "notification-service", plane: "integration", sovereign: false, primary_protocol: "rest", default_http_port: 8200, product_names: ["Notification"] },
  { id: "jobs-service", maven_module: "jobs-service", plane: "integration", sovereign: false, primary_protocol: "rest", default_http_port: 8109, product_names: ["Jobs"] },
  { id: "offline-sync-service", maven_module: "offline-sync-service", plane: "integration", sovereign: false, primary_protocol: "rest", default_http_port: 8095, product_names: ["Offline Sync"] },
  { id: "card-print-agent", maven_module: "card-print-agent", plane: "integration", sovereign: false, primary_protocol: "rest", default_http_port: 8291, product_names: ["Card Print Agent"] },
  { id: "channels-service", maven_module: "channels-service", plane: "integration", sovereign: false, primary_protocol: "rest", default_http_port: 8130, product_names: ["Channels"] },
  { id: "workflow-service", maven_module: "workflow-service", plane: "integration", sovereign: false, primary_protocol: "rest", default_http_port: 8250, product_names: ["Workflow"] },
  { id: "rules-service", maven_module: "rules-service", plane: "knowledge", sovereign: false, primary_protocol: "rest", default_http_port: 8241, product_names: ["Rules"] },
  { id: "forms-service", maven_module: "forms-service", plane: "knowledge", sovereign: false, primary_protocol: "rest", default_http_port: 8240, product_names: ["Forms"] },
  { id: "search-service", maven_module: "search-service", plane: "knowledge", sovereign: false, primary_protocol: "rest", default_http_port: 8230, product_names: ["Search"] },
  // Data platform
  { id: "data-pipeline-service", maven_module: "data-pipeline-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8215, product_names: ["Data Pipeline"] },
  { id: "national-data-repository-service", maven_module: "national-data-repository-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8152, product_names: ["National Data Repository"] },
  { id: "ndr-service", maven_module: "ndr-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8232, product_names: ["NDR"] },
  { id: "reporting-service", maven_module: "reporting-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8176, product_names: ["Reporting"] },
  { id: "data-warehouse-service", maven_module: "data-warehouse-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8233, product_names: ["Data Warehouse"] },
  { id: "data-ingestion-service", maven_module: "data-ingestion-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8210, product_names: ["Data Ingestion"] },
  { id: "data-governance-service", maven_module: "data-governance-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8220, product_names: ["Data Governance"] },
  { id: "data-access-governance-service", maven_module: "data-access-governance-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8170, product_names: ["DAGS"] },
  { id: "surveillance-service", maven_module: "surveillance-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8180, product_names: ["Surveillance"] },
  { id: "campaigns-service", maven_module: "campaigns-service", plane: "data", sovereign: false, primary_protocol: "rest", default_http_port: 8190, product_names: ["Campaigns"] },
  { id: "observability-service", maven_module: "observability-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8211, product_names: ["Observability"] },
  { id: "security-hardening-service", maven_module: "security-hardening-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8221, product_names: ["Security Hardening"] },
  { id: "identity-assurance-service", maven_module: "identity-assurance-service", plane: "trust", sovereign: false, primary_protocol: "rest", default_http_port: 8201, product_names: ["Identity Assurance"] },
  { id: "audit-ledger-service", maven_module: "audit-ledger-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8350, product_names: ["Audit Ledger"] },
  { id: "asset-registry-service", maven_module: "asset-registry-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8310, product_names: ["Asset Registry"] },
  { id: "dispatch-service", maven_module: "dispatch-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8320, product_names: ["Dispatch"] },
  { id: "iot-ingestion-service", maven_module: "iot-ingestion-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8330, product_names: ["IoT Ingestion"] },
  { id: "support-service", maven_module: "support-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8340, product_names: ["Support"] },
  { id: "offline-edge-service", maven_module: "offline-edge-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8360, product_names: ["Offline Edge"] },
  { id: "developer-portal-service", maven_module: "developer-portal-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8370, product_names: ["Developer Portal"] },
  { id: "schema-registry-service", maven_module: "schema-registry-service", plane: "ops", sovereign: false, primary_protocol: "rest", default_http_port: 8371, product_names: ["Schema Registry"] },
  // Experience
  { id: "experience-bff", maven_module: "experience-bff", plane: "experience", sovereign: false, primary_protocol: "rest", default_http_port: 8160, product_names: ["Experience BFF"] },
];

const doc = {
  registry_version: "1",
  schema: {
    id: "Stable kebab id (often equals maven_module without -service suffix)",
    maven_module: "Directory name under services/ (Maven artifact folder)",
    plane: "trust | registry | clinical | finance | marketplace | integration | knowledge | data | experience | ops",
    sovereign: "Member of a national dual-mode sovereign boundary (see TODO.md nine)",
    sovereign_group: "Logical sovereign name when multiple modules (TSHEPO, BUTANO, …)",
    primary_protocol: "rest | fhir | grpc | mixed",
    default_http_port: "Local default HTTP port (see docs/runbooks/port-allocation.md)",
    product_names: "Human-facing names",
    owner_team: "Owning squad — set to TBD until RACI assigned",
  },
  owner_team_default: "TBD",
  port_allocation_doc: "docs/runbooks/port-allocation.md",
  libraries,
  services: services.map((s) => ({
    ...s,
    owner_team: "TBD",
  })),
};

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(
  OUT,
  "# Impilo service registry (Phase A1)\n# Human-editable. Regenerate index: cd scripts/registry && npm run generate\n\n" +
    yaml.dump(doc, { lineWidth: 120, noRefs: true }),
  "utf8"
);
console.log("Wrote", path.relative(ROOT, OUT));
