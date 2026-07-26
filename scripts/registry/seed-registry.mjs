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
    ["live-service", { id: "live-service", plane: "experience", sovereign: true, sovereign_group: "LIVE", primary_protocol: "rest", default_http_port: 8380, product_names: ["Impilo Live"] }],
    ["rito-quality-safety-service", { id: "rito-quality-safety-service", plane: "experience", sovereign: false, primary_protocol: "rest", default_http_port: 8391, product_names: ["Rito"] }],
  ]
);

const DOCTRINE_OVERRIDES = new Map(
  [
    [
      "pct-service",
      {
        // Care Continuum doctrine (CC-8) + full hand-curated arrays: overrides use
        // spread semantics, so every array here must be COMPLETE, not a delta. This
        // mirror exists so a future regeneration cannot destroy the hand-edited YAML.
        primary_plane: "clinical",
        plane: "clinical",
        domain: "care-delivery",
        secondary_planes: [],
        continuum: "care",
        continuum_role: "owner",
        continuum_parent: null,
        system_of_record_for: [
          "The person Care Continuum \u2014 cradle-to-grave composition of visit journeys, encounters, problems, care plans, allergies, growth, immunisations, birth and death pathways, referrals and community care context (care-continuum-doctrine.md CC-1)",
          "Pct canonical records",
          "Encounter cadre decision (Java CadreEngine; GAP-4 unify-with-scope-rules resolved via FormScopeEngine composition)",
          "Encounter form responses (structured data-entry responses, resolver decisions, extraction provenance)",
          "Front-door sorting desk (visit-type sort; GAP-11 unifying session pending)",
          "Clinical problems list",
          "OPD care plans and goals",
          "Community households, visits and screenings context",
          "Child growth measurement registry (anthropometry with measurement conditions; WHO z-scores stamped at write with standard and engine version)",
          "Immunisation administered-dose registry (dose facts only; the national schedule and the due/overdue forecast are not owned here)",
          "Newborn clinical birth summary and neonatal episodes (child-anchored; civil birth notification stays ubomi-service, identity and mother-baby linkage stay vito-service)",
        ],
        consumes_from: [
          "tshepo-authz-service",
        ],
        exposes_to: [
          "experience-bff",
          "integration-hub",
        ],
        forbidden_responsibilities: [
          "must-not-act-as-identity-source-of-record",
          "must-not-own-enterprise-ledgering",
          "must-not-own-form-definitions",
          "must-not-own-immunisation-schedule-or-forecast-rules",
          "must-not-own-civil-birth-registration",
          "must-not-own-wellness-continuum",
          "must-not-duplicate-longitudinal-SHR",
        ],
      },
    ],
    [
      "simba-service",
      {
        // Care Continuum doctrine (CC-8) + full hand-curated arrays: overrides use
        // spread semantics, so every array here must be COMPLETE, not a delta. This
        // mirror exists so a future regeneration cannot destroy the hand-edited YAML.
        primary_plane: "enterprise",
        plane: "enterprise",
        domain: "wellness-personal-health-data",
        secondary_planes: [
          "experience",
          "data",
          "integration",
          "registry",
          "trust",
        ],
        continuum: "wellness",
        continuum_role: "owner",
        continuum_parent: null,
        system_of_record_for: [
          "The person Wellness Continuum \u2014 peer in rank to the Care Continuum owned by pct-service (care-continuum-doctrine.md CC-1)",
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
        consumes_from: [
          "tshepo-authz-service",
        ],
        exposes_to: [
          "experience-bff",
          "integration-hub",
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
      "butano-service",
      {
        // Care Continuum doctrine (CC-8) + full hand-curated arrays: overrides use
        // spread semantics, so every array here must be COMPLETE, not a delta. This
        // mirror exists so a future regeneration cannot destroy the hand-edited YAML.
        primary_plane: "clinical",
        plane: "clinical",
        domain: "care-delivery",
        secondary_planes: [],
        continuum: "care",
        continuum_role: "record-authority",
        continuum_parent: null,
        system_of_record_for: [
          "Butano canonical records",
        ],
        consumes_from: [
          "tshepo-authz-service",
        ],
        exposes_to: [
          "experience-bff",
          "integration-hub",
        ],
        forbidden_responsibilities: [
          "must-not-act-as-identity-source-of-record",
          "must-not-own-enterprise-ledgering",
        ],
      },
    ],
    [
      "butano-fhir",
      {
        // Care Continuum doctrine (CC-8) + full hand-curated arrays: overrides use
        // spread semantics, so every array here must be COMPLETE, not a delta. This
        // mirror exists so a future regeneration cannot destroy the hand-edited YAML.
        primary_plane: "clinical",
        plane: "clinical",
        domain: "care-delivery",
        secondary_planes: [],
        continuum: "care",
        continuum_role: "record-authority",
        continuum_parent: null,
        system_of_record_for: [
          "Butano Fhir canonical records",
        ],
        consumes_from: [
          "tshepo-authz-service",
        ],
        exposes_to: [
          "experience-bff",
          "integration-hub",
        ],
        forbidden_responsibilities: [
          "must-not-act-as-identity-source-of-record",
          "must-not-own-enterprise-ledgering",
        ],
      },
    ],
    [
      "inpatient-service",
      {
        // Care Continuum doctrine (CC-8) + full hand-curated arrays: overrides use
        // spread semantics, so every array here must be COMPLETE, not a delta. This
        // mirror exists so a future regeneration cannot destroy the hand-edited YAML.
        primary_plane: "clinical",
        plane: "clinical",
        domain: "care-delivery",
        secondary_planes: [],
        continuum: "care",
        continuum_role: "component",
        continuum_parent: "pct-service",
        system_of_record_for: [
          "Inpatient canonical records",
          "Early warning scores (server-computed NEWS2 and age-banded paediatric scores; thresholds versioned as content)",
          "Neonatal admissions arising from theatre handover to neonatal care",
        ],
        consumes_from: [
          "tshepo-authz-service",
        ],
        exposes_to: [
          "experience-bff",
          "integration-hub",
        ],
        forbidden_responsibilities: [
          "must-not-act-as-identity-source-of-record",
          "must-not-own-enterprise-ledgering",
          "must-not-own-longitudinal-child-health-registry",
        ],
      },
    ],
    [
      "oros-service",
      {
        // Care Continuum doctrine (CC-8) + full hand-curated arrays: overrides use
        // spread semantics, so every array here must be COMPLETE, not a delta. This
        // mirror exists so a future regeneration cannot destroy the hand-edited YAML.
        primary_plane: "clinical",
        plane: "clinical",
        domain: "care-delivery",
        secondary_planes: [],
        continuum: "care",
        continuum_role: "component",
        continuum_parent: "pct-service",
        system_of_record_for: [
          "Oros canonical records",
        ],
        consumes_from: [
          "tshepo-authz-service",
        ],
        exposes_to: [
          "experience-bff",
          "integration-hub",
        ],
        forbidden_responsibilities: [
          "must-not-act-as-identity-source-of-record",
          "must-not-own-enterprise-ledgering",
        ],
      },
    ],
    [
      "booking-service",
      {
        // Care Continuum doctrine (CC-8) + full hand-curated arrays: overrides use
        // spread semantics, so every array here must be COMPLETE, not a delta. This
        // mirror exists so a future regeneration cannot destroy the hand-edited YAML.
        primary_plane: "experience",
        plane: "experience",
        domain: "workflow-orchestration",
        secondary_planes: [],
        continuum: "care",
        continuum_role: "component",
        continuum_parent: "pct-service",
        system_of_record_for: [
          "Booking canonical records (booking/appointment transaction data \u2014 a component of the Care Continuum, never a container of care; the continuum links journeys to appointments via pct V031, not the reverse \u2014 care-continuum-doctrine.md CC-7)",
        ],
        consumes_from: [
          "tshepo-authz-service",
        ],
        exposes_to: [
          "experience-bff",
          "integration-hub",
        ],
        forbidden_responsibilities: [
          "must-not-become-system-of-record-for-clinical-or-finance",
          "must-not-embed-actor-facing-business-workflows",
          "must-not-contain-care-journeys",
        ],
      },
    ],
    [
      "procedures-service",
      {
        // Care Continuum doctrine (CC-2/CC-8) + full hand-curated arrays: overrides use
        // spread semantics, so every array here must be COMPLETE, not a delta. This
        // mirror exists so a future regeneration cannot destroy the hand-edited YAML.
        primary_plane: "clinical",
        plane: "clinical",
        domain: "care-delivery",
        secondary_planes: [],
        continuum: "care",
        continuum_role: "component",
        continuum_parent: "pct-service",
        system_of_record_for: [
          "Canonical procedure catalogue \u2014 procedure definitions, their requirements, versions and approval state (governed national content: a change to what a procedure requires is a content release, not a deployment)",
          "Appropriateness and duplication detection for procedure requests",
          "Competence and privilege resolution over VARAPI registration and Vashandi assignment \u2014 including supervision requirement, trainee status and countersignature",
          "Procedure readiness evaluation and safety-pause templates (ENGINE-NOT-STORE: this service evaluates, the executing service persists the verdict \u2014 inpatient.procedure_readiness_check and procedure_checklist_item remain the record of truth)",
          "Sedation and anaesthesia requirement profiles (requirements side; the anaesthesia record stays inpatient)",
          "Aftercare template generation",
          "Procedure execution index \u2014 the request-to-executor correlation spine, so a request that never reached an executor is a query rather than an archaeology exercise",
        ],
        consumes_from: [
          "tshepo-authz-service",
          "oros-service",
          "inpatient-service",
          "varapi-service",
          "vashandi-workforce-service",
          "tuso-service",
          "zibo-service",
          "clinical-knowledge-platform-service",
          "pct-service",
        ],
        exposes_to: [
          "experience-bff",
          "inpatient-service",
          "surgery-service",
          "oros-service",
        ],
        forbidden_responsibilities: [
          "must-not-own-the-procedure-request-record",
          "must-not-own-the-procedure-execution-record",
          "must-not-store-readiness-verdicts-or-checklist-completions",
          "must-not-own-consent",
          "must-not-own-terminology",
          "must-not-own-clinical-decision-logic",
          "must-not-own-person-level-longitudinal-clinical-registries",
          "must-not-own-the-clinical-decision-that-opens-or-closes-a-phase-of-care",
        ],
      },
    ],
    [
      "telemonitoring-service",
      {
        // Care Continuum doctrine (CC-8) + full hand-curated arrays: overrides use
        // spread semantics, so every array here must be COMPLETE, not a delta. This
        // mirror exists so a future regeneration cannot destroy the hand-edited YAML.
        primary_plane: "clinical",
        plane: "clinical",
        domain: "care-delivery",
        secondary_planes: [],
        continuum: "care",
        continuum_role: "component",
        continuum_parent: "pct-service",
        system_of_record_for: [
          "MonitoringPlan / ThresholdProfile / AlertRule / AlertEpisode lifecycle (Vol II \u00a714; alerts land with OF-B26)",
          "DeviceAssignmentId (clinical-assignment truth of the three-way device split; OF-B24)",
          "Single designated writer of monitoring-band Observations to BUTANO via fhir-gateway (OF-B25)",
        ],
        consumes_from: [
          "tshepo-authz-service",
          "oros-service",
          "iot-ingestion-service",
        ],
        exposes_to: [
          "experience-bff",
          "pct-service",
        ],
        forbidden_responsibilities: [
          "must-not-talk-to-devices-directly",
          "must-not-duplicate-surveillance-domain",
          "must-not-duplicate-wellness-domain",
          "must-not-own-task-source-of-truth",
        ],
      },
    ],
    [
      "referral-service",
      {
        // Care Continuum doctrine (CC-8) + full hand-curated arrays: overrides use
        // spread semantics, so every array here must be COMPLETE, not a delta. This
        // mirror exists so a future regeneration cannot destroy the hand-edited YAML.
        primary_plane: "integration",
        plane: "integration",
        domain: "platform-ops",
        secondary_planes: [],
        continuum: "care",
        continuum_role: "component",
        continuum_parent: "pct-service",
        system_of_record_for: [
          "Referral experience stub only \u2014 referral SoR lives in pct-service (migrations V008/V021/V032/V033/V045-V050, incl. the transition ledger and offline store-and-forward); disposition retire-vs-read-model is an open PO decision (care-continuum-doctrine.md CC-6)",
        ],
        consumes_from: [
          "tshepo-authz-service",
        ],
        exposes_to: [
          "experience-bff",
          "integration-hub",
        ],
        forbidden_responsibilities: [
          "must-not-become-system-of-record-for-clinical-or-finance",
          "must-not-embed-actor-facing-business-workflows",
          "must-not-claim-referral-canonical-records",
        ],
      },
    ],
    [
      "community-service",
      {
        // Care Continuum doctrine (CC-8) + full hand-curated arrays: overrides use
        // spread semantics, so every array here must be COMPLETE, not a delta. This
        // mirror exists so a future regeneration cannot destroy the hand-edited YAML.
        primary_plane: "experience",
        plane: "experience",
        domain: "workflow-orchestration",
        secondary_planes: [],
        continuum: "care",
        continuum_role: "component",
        continuum_parent: "pct-service",
        system_of_record_for: [
          "Community experience surfaces only \u2014 community households, visits and screenings context is pct-service SoR (V019/V027); disposition is an open PO decision (care-continuum-doctrine.md CC-6)",
        ],
        consumes_from: [
          "tshepo-authz-service",
          "multiple-domain-services-via-bff",
        ],
        exposes_to: [
          "web-mobile-experience",
        ],
        forbidden_responsibilities: [
          "must-not-own-domain-source-data",
          "must-not-bypass-bff-authz-audit-controls",
          "must-not-fork-community-care-context",
        ],
      },
    ],
    [
      "forms-service",
      {
        // Care Continuum doctrine (CC-8) + full hand-curated arrays: overrides use
        // spread semantics, so every array here must be COMPLETE, not a delta. This
        // mirror exists so a future regeneration cannot destroy the hand-edited YAML.
        primary_plane: "clinical",
        plane: "clinical",
        domain: "clinical-knowledge",
        secondary_planes: [],
        continuum: "care",
        continuum_role: "component",
        continuum_parent: "pct-service",
        system_of_record_for: [
          "Forms canonical records",
          "Encounter form definitions and versions (clinical DAK metadata; immutable version snapshots)",
        ],
        consumes_from: [
          "tshepo-authz-service",
        ],
        exposes_to: [
          "experience-bff",
          "integration-hub",
        ],
        forbidden_responsibilities: [
          "must-not-act-as-identity-source-of-record",
          "must-not-own-enterprise-ledgering",
          "must-not-own-clinical-encounters",
          "must-not-own-form-responses",
        ],
      },
    ],
    [
      "madi-service",
      {
        // Care Continuum doctrine (CC-8) + full hand-curated arrays: overrides use
        // spread semantics, so every array here must be COMPLETE, not a delta. This
        // mirror exists so a future regeneration cannot destroy the hand-edited YAML.
        primary_plane: "clinical",
        plane: "integration",
        domain: "platform-ops",
        secondary_planes: [],
        continuum: "care",
        continuum_role: "component",
        continuum_parent: "pct-service",
        system_of_record_for: [
          "Madi canonical records",
        ],
        consumes_from: [
          "tshepo-authz-service",
        ],
        exposes_to: [
          "experience-bff",
          "integration-hub",
        ],
        forbidden_responsibilities: [
          "must-not-become-system-of-record-for-clinical-or-finance",
          "must-not-embed-actor-facing-business-workflows",
        ],
      },
    ],
    [
      "daidzai-service",
      {
        // Care Continuum doctrine (CC-8) + full hand-curated arrays: overrides use
        // spread semantics, so every array here must be COMPLETE, not a delta. This
        // mirror exists so a future regeneration cannot destroy the hand-edited YAML.
        primary_plane: "experience",
        plane: "experience",
        domain: "workflow-orchestration",
        secondary_planes: [],
        continuum: "care",
        continuum_role: "correlator",
        continuum_parent: "pct-service",
        system_of_record_for: [
          "Daidzai emergency/disaster response command canonical records",
          "emergency_request, emergency_incident, mission status timeline, resource_request, affected_site",
          "assistance_request (crowdfunding case aggregate, verification lifecycle, timeline; money in mushe, attestation in credential-verification \u2014 referenced by id)",
          "EMS clinical dispatch \u2014 ems_mission crew/vehicle clinical state machine (CREATED..HANDOVER) and prehospital ePCR (patient vitals/GCS/interventions time-series), bound to the trauma incident and trauma_episode_id. This is the clinical dispatch of a crew to a patient, distinct from nhume logistics (parcel) dispatch; crew resolves from vashandi, routing/ETA from ndila, ambulance is an asset-registry Object ID.",
          "delegated trauma_episode correlation spine operated on behalf of the Care Continuum (pct-service) for the prehospital/multi-facility window (trauma_episode + trauma_episode_phase read-model); phase-owner SoR rows (pct/inpatient/madi) carry trauma_episode_id and remain owned by their services; episodes must become resolvable to a PCT anchor on facility arrival (care-continuum-doctrine.md CC-4)",
        ],
        consumes_from: [
          "tshepo-authz-service",
          "nhume-service",
          "ndila-service",
          "pct-service",
          "tuso-service",
          "indawo-service",
          "khuluma-service",
          "rito-quality-safety-service",
        ],
        exposes_to: [
          "experience-bff",
        ],
        forbidden_responsibilities: [
          "must-not-own-logistics-dispatch-execution",
          "must-not-own-maps-routing",
          "must-not-own-clinical-record-source-of-truth",
          "must-not-bypass-bff-authz-audit-controls",
          "must-not-own-care-continuum",
        ],
      },
    ],
    [
      "wellness-service",
      {
        // Care Continuum doctrine (CC-8) + full hand-curated arrays: overrides use
        // spread semantics, so every array here must be COMPLETE, not a delta. This
        // mirror exists so a future regeneration cannot destroy the hand-edited YAML.
        primary_plane: "enterprise",
        plane: "enterprise",
        domain: "wellness-compatibility-alias",
        secondary_planes: [
          "experience",
          "data",
          "registry",
          "trust",
        ],
        continuum: "wellness",
        continuum_role: "component",
        continuum_parent: "simba-service",
        system_of_record_for: [],
        consumes_from: [
          "simba-service",
        ],
        exposes_to: [
          "experience-bff",
          "integration-hub",
        ],
        forbidden_responsibilities: [
          "must-not-own-public-health-surveillance-source-of-truth",
          "must-not-own-clinical-encounter-lifecycle",
          "must-not-own-marketplace-or-payment-ledgers",
          "must-not-own-patient-identity-source-of-truth",
          "must-not-own-provider-identity-source-of-truth",
          "must-not-own-facility-registry",
          "must-not-fork-wellness-continuum",
        ],
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
      "live-service",
      {
        primary_plane: "experience",
        plane: "experience",
        domain: "live-events-broadcast",
        secondary_planes: ["integration", "data", "registry", "trust", "clinical"],
        system_of_record_for: [
          "live events and webinars",
          "live event registrations",
          "live event attendance",
          "live event interactions",
          "live event certificates",
          "live event analytics snapshots",
        ],
        consumes_from: [
          "vito-service",
          "varapi-service",
          "tuso-service",
          "tshepo-authz-service",
          "learning-service",
          "madi-service",
          "notification-service",
          "rtc-gateway-service",
        ],
        exposes_to: ["experience-bff", "web-mobile-experience"],
        forbidden_responsibilities: [
          "must-not-act-as-identity-source-of-record",
          "must-not-own-clinical-encounter-lifecycle",
          "must-not-own-learning-course-content",
          "must-not-own-blood-donor-registry",
          "must-not-bypass-tshepo-authz",
        ],
        frontend_wiring_status: "wired",
        implementation_status: "implemented-or-partial",
        api_contract_status: "partial",
        authz_audit_status: "partial",
        observability_status: "partial",
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
    [
      "ndila-service",
      {
        primary_plane: "integration",
        plane: "integration",
        domain: "interoperability",
        secondary_planes: ["registry", "experience", "data", "trust", "clinical", "enterprise"],
        system_of_record_for: [
          "canonical geospatial location registry",
          "routing, ETA, and distance matrix orchestration",
          "geofencing and catchment boundary operations",
          "tracking asset telemetry normalization",
          "spatial search and geospatial intelligence context",
        ],
        consumes_from: ["tshepo-authz-service", "tuso-service", "indawo-service", "varapi-service", "surveillance-service"],
        exposes_to: ["experience-bff", "integration-hub", "nhume-service", "dispatch-service"],
        forbidden_responsibilities: [
          "must-not-authorize-access-decisions",
          "must-not-own-patient-identity-source-of-truth",
          "must-not-own-provider-identity-source-of-truth",
          "must-not-store-clinical-source-of-truth-outside-governed-clinical-shr-boundaries",
        ],
      },
    ],
    [
      "nhume-service",
      {
        primary_plane: "integration",
        plane: "integration",
        domain: "interoperability",
        secondary_planes: ["experience", "clinical", "registry", "enterprise", "trust", "data"],
        system_of_record_for: [
          "dispatch request and assignment lifecycle",
          "courier and fleet operational registry",
          "last-mile tracking and proof-of-delivery telemetry",
          "delivery chain-of-custody and exception workflow",
        ],
        consumes_from: [
          "tshepo-authz-service",
          "ndila-service",
          "vito-service",
          "varapi-service",
          "tuso-service",
          "indawo-service",
          "msika-service",
          "mushex-service",
        ],
        exposes_to: ["experience-bff", "integration-hub"],
        forbidden_responsibilities: [
          "must-not-own-clinical-record-source-of-truth",
          "must-not-own-patient-identity-source-of-truth",
          "must-not-own-provider-identity-source-of-truth",
          "must-not-own-consent-policy-authority",
          "must-not-own-payment-ledgers",
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
    continuum: "care | wellness — which person continuum the service participates in (care-continuum-doctrine.md CC-8)",
    continuum_role: "owner | component | correlator | record-authority",
    continuum_parent: "The continuum owner a component/correlator is subordinate to; null for owners and record-authorities",
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
