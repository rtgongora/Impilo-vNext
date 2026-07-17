#!/usr/bin/env node
/**
 * Generate BFF downstream K8s service URLs for full-preview.
 * BFF defaults in application.yml use localhost — unusable inside the cluster.
 *
 * Usage:
 *   node scripts/full-boot/generate-full-preview-bff-downstream-env.mjs
 */
import fs from "node:fs";
import path from "node:path";
import yaml from "js-yaml";

const ROOT = process.env.REPO_PATH
  ? path.resolve(process.env.REPO_PATH)
  : path.resolve(path.dirname(new URL(import.meta.url).pathname), "../..");

const RUNTIME = path.join(ROOT, "deploy/helm/impilo-vnext/values-full-preview-runtime.generated.yaml");
const OUT = path.join(ROOT, "deploy/helm/impilo-vnext/values-full-preview-bff-env.generated.yaml");

/** Env var -> fullBootServices key (port read from runtime values). */
const SERVICE_ENV = [
  ["PCT_BASE_URL", "pct-service"],
  ["OROS_BASE_URL", "oros-service"],
  ["PHARMACY_BASE_URL", "pharmacy-service"],
  ["BUTANO_BASE_URL", "butano-service"],
  ["MSIKA_BASE_URL", "msika-service"],
  ["MSIKA_FLOW_BASE_URL", "msika-flow-service"],
  ["MUSHEX_BASE_URL", "mushex-service"],
  ["DISPATCH_BASE_URL", "dispatch-service"],
  ["NDILA_BASE_URL", "ndila-service"],
  ["NHUME_BASE_URL", "nhume-service"],
  ["NHUME_SERVICE_BASE_URL", "nhume-service"],
  ["MADI_BASE_URL", "madi-service"],
  ["MADI_SERVICE_BASE_URL", "madi-service"],
  ["LIVE_BASE_URL", "live-service"],
  ["IOT_INGESTION_BASE_URL", "iot-ingestion-service"],
  ["RTC_GATEWAY_BASE_URL", "rtc-gateway-service"],
  ["ANALYTICS_PIPELINE_BASE_URL", "analytics-pipeline-service"],
  ["VITO_BASE_URL", "vito-service"],
  ["TUSO_BASE_URL", "tuso-service"],
  ["VARAPI_BASE_URL", "varapi-service"],
  ["DOCUMENT_STORE_BASE_URL", "document-service"],
  ["COSTA_BASE_URL", "costing-engine-service"],
  ["COVERAGE_BASE_URL", "coverage-service"],
  ["SURVEILLANCE_BASE_URL", "surveillance-service"],
  ["CAMPAIGNS_BASE_URL", "campaigns-service"],
  ["NDR_NATIONAL_BASE_URL", "national-data-repository-service"],
  ["NDR_QUERY_BASE_URL", "ndr-service"],
  ["DATA_PIPELINE_BASE_URL", "data-pipeline-service"],
  ["REPORTING_BASE_URL", "reporting-service"],
  ["DATA_WAREHOUSE_BASE_URL", "data-warehouse-service"],
  ["DATA_INGESTION_BASE_URL", "data-ingestion-service"],
  ["INDAWO_BASE_URL", "indawo-service"],
  ["TSHEPO_IDENTITY_BASE_URL", "tshepo-identity-service"],
  ["TSHEPO_KEYS_BASE_URL", "tshepo-keys-service"],
  ["TSHEPO_OFFLINE_BASE_URL", "tshepo-offline-service"],
  ["TSHEPO_CONSENT_BASE_URL", "tshepo-consent-service"],
  ["TSHEPO_AUDIT_BASE_URL", "tshepo-audit-service"],
  ["ZIBO_BASE_URL", "zibo-service"],
  ["UBOMI_BASE_URL", "ubomi-service"],
  ["INPATIENT_BASE_URL", "inpatient-service"],
  ["PACS_BASE_URL", "pacs-adapter-service"],
  ["COMMUNITY_BASE_URL", "community-service"],
  ["SIMBA_BASE_URL", "simba-service"],
  ["MUSHE_WALLET_BASE_URL", "mushe-wallet-service"],
  ["INVENTORY_BASE_URL", "inventory-service"],
  ["ASSET_REGISTRY_BASE_URL", "asset-registry-service"],
  ["INVENTORY_ELMIS_BASE_URL", "inventory-elmis-adapter"],
  ["LANDELA_BASE_URL", "landela-adapter-service"],
  ["NOTIFICATION_BASE_URL", "notification-service"],
  ["CREDENTIAL_BASE_URL", "credential-verification-service"],
  ["FHIR_GATEWAY_BASE_URL", "fhir-gateway-service"],
  ["SEARCH_BASE_URL", "search-service"],
  ["FORMS_BASE_URL", "forms-service"],
  ["RULES_BASE_URL", "rules-service"],
  ["WORKFLOW_BASE_URL", "workflow-service"],
  ["GUIDANCE_BASE_URL", "guidance-service"],
  ["INTEGRATION_HUB_BASE_URL", "integration-hub"],
  ["DAGS_BASE_URL", "data-access-governance-service"],
  ["DATA_GOVERNANCE_BASE_URL", "data-governance-service"],
  ["TSHEPO_AUTHZ_BASE_URL", "tshepo-authz-service"],
  ["MVUMO_BASE_URL", "mvumo-service"],
  ["AI_MODEL_REGISTRY_BASE_URL", "ai-model-registry-service"],
  ["LLM_ORCHESTRATION_BASE_URL", "llm-orchestration-service"],
  ["GENERAL_LEDGER_BASE_URL", "general-ledger-service"],
  ["HR_PAYROLL_BASE_URL", "hr-payroll-service"],
  ["PROCUREMENT_BASE_URL", "procurement-service"],
  ["SCHEDULING_SERVICE_BASE_URL", "scheduling-service"],
  ["BOOKING_BASE_URL", "booking-service"],
  ["MSIKA_APPS_BASE_URL", "msika-apps-service"],
  ["LEARNING_SERVICE_BASE_URL", "learning-service"],
  ["CLINICAL_KNOWLEDGE_PLATFORM_BASE_URL", "clinical-knowledge-platform-service"],
  ["CHANNELS_BASE_URL", "channels-service"],
  ["SUPPORT_BASE_URL", "support-service"],
  ["WORKFORCE_GOVERNANCE_BASE_URL", "workforce-governance-service"],
  ["VASHANDI_BASE_URL", "vashandi-workforce-service"],
  // Wave-1 product completion — previously missing downstream mappings
  ["AUDIT_LEDGER_BASE_URL", "audit-ledger-service"],
  ["BUTANO_FHIR_BASE_URL", "butano-fhir"],
  ["CARD_PRINT_AGENT_BASE_URL", "card-print-agent"],
  ["CONNECTOR_FHIR_BASE_URL", "connector-fhir-adapter"],
  ["DEVELOPER_PORTAL_BASE_URL", "developer-portal-service"],
  ["IDENTITY_ASSURANCE_BASE_URL", "identity-assurance-service"],
  ["JOBS_SERVICE_BASE_URL", "jobs-service"],
  ["OBSERVABILITY_BASE_URL", "observability-service"],
  ["OFFLINE_EDGE_BASE_URL", "offline-edge-service"],
  ["OFFLINE_SYNC_BASE_URL", "offline-sync-service"],
  ["PHARMACY_ELMIS_BASE_URL", "pharmacy-elmis-adapter"],
  ["PRODUCT_REGISTRY_BASE_URL", "product-registry-service"],
  ["REFERRAL_SERVICE_BASE_URL", "referral-service"],
  ["SCHEMA_REGISTRY_BASE_URL", "schema-registry-service"],
  ["SECURITY_HARDENING_BASE_URL", "security-hardening-service"],
  ["SHARE_SLIP_BASE_URL", "share-slip-service"],
  // Newly wave-manifested services (2026-07-02) — BFF application.yml already
  // declares these base-url properties.
  ["DAIDZAI_BASE_URL", "daidzai-service"],
  ["KHULUMA_BASE_URL", "khuluma-service"],
  ["PATIENT_SAFETY_BASE_URL", "patient-safety-service"],
  ["RITO_BASE_URL", "rito-quality-safety-service"],
  ["PARTICIPATION_BASE_URL", "participation-service"],
  // Organization registry SoR — feeds BFF impilo.services.organization-registry-base-url
  // (OrganizationRegistryServiceClient + OrgRegistryFacilityAdminClient).
  ["ORGANIZATION_REGISTRY_BASE_URL", "organization-registry-service"],
];

/**
 * Enabled helm services intentionally not given a dedicated *_BASE_URL.
 * Each must have a documented reason (supporting component, alias, or infra path).
 */
const BFF_DOWNSTREAM_EXCLUDED = {
  "wellness-service":
    "Deprecated SoR; wellness runtime absorbed by simba-service. BFF uses SIMBA_BASE_URL (wellness-base-url alias in application.yml).",
};

/** Fixed URLs not tied to a single fullBootServices entry. */
const FIXED_ENV = {
  FHIR_BASE_URL: "http://hapi-fhir:8090/fhir",
  // Chart-deployed Orthanc (templates/orthanc.yaml) — DICOM upload/viewer proxy.
  ORTHANC_BASE_URL: "http://orthanc:8042",
  ORTHANC_DICOMWEB_URL: "http://orthanc:8042/dicom-web",
  ONE_UI_SHELL_BASE_URL: "http://one-ui-shell:3000",
  IMPILO_BFF_FACILITIES_MODE: "live",
  IMPILO_BFF_PROVIDER_HUBS_MODE: "live",
  IMPILO_BFF_PROVIDER_HUBS_FAILURE_POLICY: "stub_fallback",
  IMPILO_BFF_CITIZEN_LONGTAIL_MODE: "stub",
  IMPILO_BFF_CITIZEN_LONGTAIL_FAILURE_POLICY: "stub_fallback",
  // IATG employment-context matching (ProviderClaimController @Value
  // ${impilo.features.ec-matching}) — enabled for the preview journey.
  IMPILO_FEATURES_EC_MATCHING: "true",
};

function validateCoverage(services) {
  const enabled = Object.entries(services)
    .filter(([, v]) => v?.enabled)
    .map(([id]) => id);
  const mapped = new Set(SERVICE_ENV.map(([, id]) => id));
  const excluded = new Set(Object.keys(BFF_DOWNSTREAM_EXCLUDED));
  const gaps = enabled.filter((id) => !mapped.has(id) && !excluded.has(id));
  if (gaps.length) {
    console.error("BFF downstream gap — enabled services without mapping or exclusion:");
    for (const id of gaps) console.error(`  - ${id}`);
    process.exit(1);
  }
  return { enabled: enabled.length, mapped: mapped.size, excluded: excluded.size };
}

function main() {
  const runtime = yaml.load(fs.readFileSync(RUNTIME, "utf8"));
  const services = runtime.fullBootServices ?? {};
  const coverage = validateCoverage(services);
  const env = { ...FIXED_ENV };

  for (const [varName, serviceId] of SERVICE_ENV) {
    const svc = services[serviceId];
    if (!svc?.port) {
      console.warn(`skip ${varName}: unknown service ${serviceId}`);
      continue;
    }
    env[varName] = `http://${serviceId}:${svc.port}`;
  }

  const doc = {
    "# AUTO-GENERATED": "node scripts/full-boot/generate-full-preview-bff-downstream-env.mjs",
    experienceBff: { env },
  };
  const header = [
    "# AUTO-GENERATED — do not edit by hand.",
    "# Regenerate: node scripts/full-boot/generate-full-preview-bff-downstream-env.mjs",
    `# Downstream env vars: ${Object.keys(env).length}`,
    "",
  ].join("\n");
  fs.writeFileSync(OUT, header + yaml.dump({ experienceBff: { env } }), "utf8");
  console.log(
    `Wrote ${path.relative(ROOT, OUT)} (${Object.keys(env).length} env vars; ` +
      `coverage ${coverage.mapped} mapped + ${coverage.excluded} excluded / ${coverage.enabled} enabled)`
  );
}

main();
