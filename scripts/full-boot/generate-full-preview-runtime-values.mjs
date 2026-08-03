#!/usr/bin/env node
/**
 * Generate deploy/helm/impilo-vnext/values-full-preview-runtime.generated.yaml
 * fullBootServices map for all runtime K8s microservices; enabled cumulatively by wave.
 *
 * Usage:
 *   node scripts/full-boot/generate-full-preview-runtime-values.mjs [--max-wave N]
 */
import fs from "node:fs";
import path from "node:path";
import yaml from "js-yaml";
import {
  deriveDatabaseName,
  isRuntimeK8sMicroservice,
  parsePortAllocation,
  resolveHttpPort,
} from "./full-boot-lanes.mjs";

const ROOT = process.env.REPO_PATH
  ? path.resolve(process.env.REPO_PATH)
  : path.resolve(path.dirname(new URL(import.meta.url).pathname), "../..");

const OUT = path.join(ROOT, "deploy/helm/impilo-vnext/values-full-preview-runtime.generated.yaml");
const CLS = path.join(ROOT, "config/full-boot-service-classification.yml");
const WAVES = path.join(ROOT, "config/full-boot-waves.yml");

function portFromApplicationConfig(serviceId) {
  const base = path.join(ROOT, "services", serviceId, "src/main/resources");
  const candidates = [
    path.join(base, "application.yml"),
    path.join(base, "application.yaml"),
    path.join(base, "application.properties"),
  ];
  for (const p of candidates) {
    if (!fs.existsSync(p)) continue;
    const text = fs.readFileSync(p, "utf8");
    let m = text.match(/server:\s*\n\s*port:\s*(\d+)/);
    if (!m) m = text.match(/^\s*port:\s*(\d+)\s*$/m);
    if (!m) m = text.match(/^\s*port:\s*\$\{[^:}]+:(\d+)\}/m);
    if (!m) m = text.match(/^server\.port\s*=\s*(\d+)\s*$/m);
    if (!m) m = text.match(/^server\.port\s*=\s*\$\{[^:}]+:(\d+)\}/m);
    if (m) return Number(m[1]);
  }
  return null;
}

function parseArgs() {
  let maxWave = null;
  for (let i = 2; i < process.argv.length; i++) {
    if (process.argv[i] === "--max-wave" && process.argv[i + 1]) {
      maxWave = Number(process.argv[++i]);
    }
  }
  return { maxWave };
}

function cumulativeWaveServices(wavesDoc, maxWave) {
  const enabled = new Set();
  for (const wave of wavesDoc.waves ?? []) {
    if (maxWave !== null && wave.id > maxWave) break;
    for (const sid of wave.services ?? []) enabled.add(sid);
  }
  return enabled;
}

function specialEnv(serviceId) {
  if (serviceId === "butano-service") {
    return { HAPI_FHIR_URL: "http://hapi-fhir:8090/fhir" };
  }
  if (serviceId === "fhir-gateway-service") {
    return { FHIR_BASE_URL: "http://hapi-fhir:8090/fhir" };
  }
  if (serviceId === "tshepo-authz-service") {
    return {
      TSHEPO_CONSENT_URL: "http://tshepo-consent-service:8182",
      TSHEPO_KEYS_URL: "http://tshepo-keys-service:8184",
      // Required for WORK_CONTEXT duty-token introspection (the PDP calls identity
      // /tokens/introspect). Without it the binding fails open and never fires.
      TSHEPO_IDENTITY_URL: "http://tshepo-identity-service:8181",
      // Duty-token binding mode: OFF | SHADOW | ENFORCE (default SHADOW — observe-only).
      TSHEPO_WORK_CONTEXT_MODE: "SHADOW",
    };
  }
  if (serviceId === "tshepo-audit-service") {
    return {
      IMPILO_KEYCLOAK_EVENTS_ENABLED: "true",
      IMPILO_KEYCLOAK_BASE_URL: "http://keycloak:8080",
      IMPILO_KEYCLOAK_REALM: "impilo",
      IMPILO_KEYCLOAK_EVENTS_CLIENT_ID: "impilo-event-reader",
      IMPILO_KEYCLOAK_EVENTS_OVERLAP_SECONDS: "120",
      IMPILO_KEYCLOAK_EVENTS_BATCH_SIZE: "200",
    };
  }
  if (serviceId === "vito-service") {
    // VITO_HMAC_PEPPER (PII pseudonymization pepper) is injected from Secret
    // impilo-app-secrets via specialSecretEnv — never committed. Rotating it is
    // data-affecting (invalidates existing HMACs); requires a migration.
    return null;
  }
  if (serviceId === "pacs-adapter-service") {
    // Chart-deployed Orthanc (templates/orthanc.yaml) is the preview DICOM backend.
    return { ORTHANC_BASE_URL: "http://orthanc:8042" };
  }
  if (serviceId === "abis-service") {
    // Chart-deployed matcher-engine (templates/matcher-engine.yaml) is the core-infra
    // biometric matching appliance; abis-service is its governance adapter.
    return { MATCHER_ENGINE_URL: "http://matcher-engine:9200" };
  }
  if (serviceId === "rtc-gateway-service") {
    // Chart-deployed LiveKit (templates/livekit.yaml). Browser signaling is
    // wss via Traefik (deploy/tls/mohcc-gov/livekit-ingressroute.yaml routes
    // /rtc on :443 -> livekit:7880) so it isn't mixed-content-blocked on the
    // https UI. Media still needs 7881/tcp + 7882/udp reachable at the VM
    // (off-LAN clients also need TURN / a public ICE candidate — see the
    // livekit-config use_external_ip note).
    return {
      LIVEKIT_ENABLED: "true",
      LIVEKIT_URL: "http://livekit:7880",
      LIVEKIT_CLIENT_URL: "wss://impilo.mohcc.gov.zw",
      LIVEKIT_API_KEY: "impilo-preview-key",
      // LIVEKIT_API_SECRET injected from Secret impilo-app-secrets (specialSecretEnv).
    };
  }
  if (serviceId === "dispatch-service") {
    return {
      DISPATCH_SECURITY_OAUTH2_ENABLED: "false",
    };
  }
  if (serviceId === "data-access-governance-service") {
    // Permit-token signing key is fail-closed (no insecure default); injected from
    // Secret impilo-app-secrets via specialSecretEnv — never committed.
    return null;
  }
  if (serviceId === "product-registry-service") {
    return {
      KEYCLOAK_ISSUER: "",
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: "",
      IMPILO_SECURITY_ALLOW_ANONYMOUS: "true",
    };
  }
  if (serviceId === "tuso-service") {
    return {
      // PIC eligibility assessments call VARAPI; the code default is
      // localhost:8083, which in-pod means "no assessments ever resolve".
      VARAPI_BASE_URL: "http://varapi-service:8083",
    };
  }
  if (serviceId === "pct-service") {
    return {
      // Blanking the issuer was the preview convention: an empty issuer made the service
      // skip its OAuth resource-server block and authenticate internal calls through the
      // trust-context filter. b12f5dcd3 ("remove oauth2-enabled permitAll off-switch from
      // 23 services") removed the escape hatch that made that safe — pct's SecurityConfig
      // now calls .oauth2ResourceServer(...) unconditionally, so a blank issuer leaves
      // Spring unable to build a JwtDecoder and the service FAILS TO START. Observed
      // 2026-08-03: pct + 5 others dead after the boot, "Parameter 0 of method
      // setFilterChains required a bean of type JwtDecoder".
      //
      // Use the cohort pattern already proven on tshepo-audit/tshepo-authz: the PUBLIC
      // issuer, because Keycloak advertises it even when reached in-cluster, paired with
      // the INTERNAL JWKS route, because this VM cannot reach its own public address.
      KEYCLOAK_ISSUER: "https://impilo.mohcc.gov.zw/realms/impilo",
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: "https://impilo.mohcc.gov.zw/realms/impilo",
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI:
        "http://keycloak:8080/realms/impilo/protocol/openid-connect/certs",
      // Queue materialisation pulls queue-definitions from TUSO; the code default
      // is localhost:8084, which in-pod means "no queues ever materialise".
      PCT_INTEGRATION_TUSO_BASE_URL: "http://tuso-service:8084",
      // IMAM admission routing and discharge readiness call the clinical knowledge platform.
      // Paired with KEYCLOAK_BACKEND_SECRET in specialSecretEnv below.
      PCT_INTEGRATION_CLINICAL_KNOWLEDGE_BASE_URL: "http://clinical-knowledge-platform-service:8270",
      KEYCLOAK_BACKEND_CLIENT_ID: "impilo-backend",
    };
  }
  if (serviceId === "oros-service") {
    // Was blanked on the same preview convention as pct-service, and broke the same way
    // once b12f5dcd3 removed the permitAll off-switch — see the pct-service note above.
    // The original concern (a DEFAULT localhost issuer 401ing every BFF order placement)
    // is not what this sets: naming the real issuer with the internal JWKS route means
    // Keycloak-minted tokens validate rather than being rejected.
    return {
      KEYCLOAK_ISSUER: "https://impilo.mohcc.gov.zw/realms/impilo",
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: "https://impilo.mohcc.gov.zw/realms/impilo",
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI:
        "http://keycloak:8080/realms/impilo/protocol/openid-connect/certs",
    };
  }
  if (serviceId === "ndila-service") {
    const streetStackEnabled = process.env.NDILA_STREET_STACK_ENABLED !== "false";
    return {
      NDILA_ALLOW_ANONYMOUS: "true",
      NDILA_ENVIRONMENT: "staging",
      NDILA_DEFAULT_TILES: streetStackEnabled ? "OSM_OSRM" : "PREVIEW_SOVEREIGN",
      NDILA_OSM_ENABLED: streetStackEnabled ? "true" : "false",
      NDILA_OSM_TILE_BASE_URL: streetStackEnabled
        ? "http://ndila-martin:3000/zimbabwe/{z}/{x}/{y}"
        : "",
      NDILA_OSM_PROXY_THROUGH_NDILA: "true",
      NDILA_PREVIEW_SOVEREIGN_TILES_ENABLED: "true",
      NDILA_POSTGIS_ENABLED: "false",
    };
  }
  if (serviceId === "mushex-service") {
    return {
      MUSHEX_SANDBOX_ENABLED: "true",
      MUSHEX_SANDBOX_BYPASS_CREDENTIAL_CHECK: "true",
      MUSHEX_SANDBOX_APPLY_SIMULATION_OUTCOME: "true",
    };
  }
  if (serviceId === "organization-registry-service") {
    // IATG Channel-C claim → adjudication journey. Adjudication is opt-in and,
    // when on, escalates to workflow-service (in-cluster URL required — app
    // default localhost:8250 is unreachable in the pod). Kafka events + the
    // AdjudicationDecisionConsumer @KafkaListener are enabled here;
    // SPRING_KAFKA_LISTENER_AUTO_STARTUP=true opts the listener in past the
    // template's default-off guard.
    return {
      ORG_REGISTRY_ADJUDICATION_ENABLED: "true",
      ORG_REGISTRY_KAFKA_EVENTS_ENABLED: "true",
      SPRING_KAFKA_LISTENER_AUTO_STARTUP: "true",
      WORKFLOW_SERVICE_URL: "http://workflow-service:8250",
      // application.yml resolves issuer-uri to ${KEYCLOAK_ISSUER:${KEYCLOAK_URL}/realms/impilo}.
      // With only KEYCLOAK_URL set it expected the INTERNAL issuer while every token Keycloak
      // mints carries the PUBLIC one, so service-originated calls failed "The iss claim is not
      // valid" — a 401 the BFF reported as a degraded upstream and the UI as "No work
      // assignment found". jwk-set-uri stays internal: the VM cannot reach its own public address.
      KEYCLOAK_ISSUER: "https://impilo.mohcc.gov.zw/realms/impilo",
    };
  }
  if (serviceId === "workforce-governance-service") {
    // DECISION-FEEDBACK arc — publish governance adjudication decision events
    // (impilo.governance.adjudication.decision.recorded) consumed by org-registry.
    return {
      GOVERNANCE_KAFKA_EVENTS_ENABLED: "true",
    };
  }
  return null;
}

// Secret-backed env: ENV_NAME -> { name, key } rendered by templates/microservice.yaml
// as valueFrom.secretKeyRef. Signing keys/secrets come from the out-of-band Secret
// `impilo-app-secrets` (provisioned by scripts/secrets/bootstrap-secrets.sh), never
// from committed values. See docs/security/secrets-management-migration-plan.md.
function specialSecretEnv(serviceId) {
  const SECRET = "impilo-app-secrets";
  if (serviceId === "vito-service") {
    // Cryptographic seeds are fail-closed (no DEV fallback). Shared card-print
    // seed (or derived public key) lets CardAssertionVerifier match printed cards.
    return {
      VITO_HMAC_PEPPER: { name: SECRET, key: "vito-hmac-pepper" },
      VITO_IMPILO_ID_ENCRYPTION_KEY: { name: SECRET, key: "vito-impilo-id-encryption-key" },
      VITO_QR_SIGNING_KEY_SEED: { name: SECRET, key: "vito-qr-signing-key-seed" },
      CARD_PRINT_QR_SIGNING_KEY_SEED: { name: SECRET, key: "card-print-qr-signing-key-seed" },
      CARD_PRINT_QR_PUBLIC_KEY: { name: SECRET, key: "card-print-qr-public-key" },
    };
  }
  if (serviceId === "card-print-agent") {
    return {
      CARD_PRINT_QR_SIGNING_KEY_SEED: { name: SECRET, key: "card-print-qr-signing-key-seed" },
    };
  }
  if (serviceId === "coverage-service") {
    return {
      RUVIMBO_TOKEN_SECRET: { name: SECRET, key: "ruvimbo-token-secret" },
    };
  }
  if (serviceId === "mushe-wallet-service") {
    return {
      WALLET_CARD_ENCRYPTION_MASTER_KEY: { name: SECRET, key: "wallet-card-encryption-master-key" },
    };
  }
  if (serviceId === "tshepo-keys-service") {
    return {
      TSHEPO_KEYS_KEK: { name: SECRET, key: "tshepo-keys-kek" },
    };
  }
  if (serviceId === "tshepo-audit-service") {
    return {
      IMPILO_KEYCLOAK_EVENTS_CLIENT_SECRET: {
        name: SECRET,
        key: "keycloak-event-reader-secret",
      },
    };
  }
  if (serviceId === "rtc-gateway-service") {
    return { LIVEKIT_API_SECRET: { name: SECRET, key: "livekit-api-secret" } };
  }
  if (serviceId === "data-access-governance-service") {
    return { DAGS_ENFORCEMENT_SIGNING_KEY: { name: SECRET, key: "dags-signing-key" } };
  }
  if (serviceId === "nhume-service") {
    return { NHUME_WEBHOOK_SECRET: { name: SECRET, key: "nhume-webhook-secret" } };
  }
  if (serviceId === "mushex-service") {
    // MUSHEX_HMAC_PEPPER is an HMAC pepper (data-affecting) — preserve, don't rotate.
    return { MUSHEX_HMAC_PEPPER: { name: SECRET, key: "mushex-hmac-pepper" } };
  }
  if (serviceId === "pct-service") {
    // pct-service calls the clinical knowledge platform for IMAM admission routing and discharge
    // readiness. That service is an OAuth2 resource server and authenticates services as well as
    // users, so the call needs a service token of its own — the user's cannot be borrowed, since the
    // same evaluation runs from Kafka consumers and scheduled jobs with no user present.
    //
    // This lived only as a hand edit in the generated file, so regenerating silently deleted it and
    // broke that call. The file even carried a warning saying so. Encoded here instead, which is
    // what "do not edit by hand" requires of anything the generator must keep.
    return { KEYCLOAK_BACKEND_SECRET: { name: SECRET, key: "keycloak-backend-secret" } };
  }
  return null;
}

function specialProbes(serviceId) {
  if (serviceId === "butano-service") {
    return { readinessInitialDelay: 60, livenessInitialDelay: 120 };
  }
  return null;
}

function specialResources(serviceId) {
  if (serviceId === "butano-service") {
    return {
      requests: { cpu: "200m", memory: "1Gi" },
      limits: { cpu: "2", memory: "2Gi" },
    };
  }
  return null;
}

function main() {
  const { maxWave } = parseArgs();
  const cls = yaml.load(fs.readFileSync(CLS, "utf8"));
  const wavesDoc = fs.existsSync(WAVES) ? yaml.load(fs.readFileSync(WAVES, "utf8")) : { waves: [] };
  const waveEnabled =
    maxWave === null
      ? cumulativeWaveServices(wavesDoc, null)
      : cumulativeWaveServices(wavesDoc, maxWave);

  const portMap = parsePortAllocation(ROOT);
  const fullBootServices = {};
  const initDatabases = new Set([
    "hapi",
    "vito",
    "varapi",
    "tuso",
    "ubomi",
    "zibo",
    "tshepo_authz",
    "tshepo_identity",
    "tshepo_consent",
    "tshepo_audit",
    "tshepo_keys",
    "pct",
    "butano",
    "fhir_gateway",
  ]);

  const microEntries = cls.classifications.filter(isRuntimeK8sMicroservice).sort((a, b) => a.id.localeCompare(b.id));

  for (const entry of microEntries) {
    let port = resolveHttpPort(entry, portMap) ?? portFromApplicationConfig(entry.id);
    if (!port) {
      let h = 0;
      for (const ch of entry.id) h = (h * 31 + ch.charCodeAt(0)) % 90;
      port = 8310 + h;
      console.warn(`WARN: no port for ${entry.id} — using preview fallback ${port}`);
    }
    const db = deriveDatabaseName(entry.id);
    initDatabases.add(db);
    const enabled = waveEnabled.has(entry.id);
    const block = {
      enabled,
      port,
      database: db,
    };
    // Every service's application.yml resolves issuer-uri to
    // ${KEYCLOAK_ISSUER:${KEYCLOAK_URL}/realms/impilo}. With only KEYCLOAK_URL set that
    // expects the INTERNAL issuer, while every token Keycloak mints carries the PUBLIC
    // one — so the service rejects it with "The iss claim is not valid". Measured
    // 2026-08-03: 28 deployed services were exposed. vashandi-workforce-service and
    // organization-registry-service were the two that happened to surface, as a 401 the
    // BFF reported as a degraded upstream and the UI as "No work assignment found";
    // the other 26 carried the identical latent fault.
    //
    // Defaulted here rather than per service so a NEW service inherits it. Only applied
    // when the service has not deliberately set its own value — product-registry-service
    // sets KEYCLOAK_ISSUER:"" on purpose to run anonymous, and must keep it.
    //
    // jwk-set-uri deliberately stays internal via KEYCLOAK_URL: this VM cannot reach its
    // own public address (hairpin NAT), so verification must not go out and back.
    const env = specialEnv(entry.id) ?? {};
    if (!("KEYCLOAK_ISSUER" in env)) {
      env.KEYCLOAK_ISSUER = "https://impilo.mohcc.gov.zw/realms/impilo";
    }
    // KEYCLOAK_ISSUER alone is not enough. 20 deployed services build issuer-uri from
    // KEYCLOAK_URL and read no issuer override at all, and have no jwk-set-uri — so Spring
    // performs OIDC DISCOVERY against http://keycloak:8080/realms/impilo, gets back the
    // PUBLIC issuer Keycloak advertises, and refuses to build a decoder:
    //   IllegalStateException: The Issuer "https://impilo.mohcc.gov.zw/realms/impilo"
    //   provided in the configuration did not match the requested issuer "http://keycloak:8080/..."
    // That surfaces as 500 (tuso, which builds eagerly) or 401 (vito/varapi, where
    // SupplierJwtDecoder defers the failure to first use) — the same fault wearing two
    // status codes, which is why a status-code survey mis-groups them.
    //
    // Setting jwk-set-uri makes Spring use JwkSetUriJwtDecoderBuilder directly and SKIP
    // discovery, so the public issuer is asserted for validation while keys are fetched
    // internally — the VM cannot reach its own public address (hairpin NAT).
    if (!("SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI" in env)) {
      env.SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI = "https://impilo.mohcc.gov.zw/realms/impilo";
      env.SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI = "http://keycloak:8080/realms/impilo/protocol/openid-connect/certs";
    }
    if (Object.keys(env).length > 0) block.env = env;
    const secretEnv = specialSecretEnv(entry.id);
    if (secretEnv) block.secretEnv = secretEnv;
    const probes = specialProbes(entry.id);
    if (probes) Object.assign(block, probes);
    const res = specialResources(entry.id);
    if (res) block.resources = res;
    fullBootServices[entry.id] = block;
  }

  const enabledCount = Object.values(fullBootServices).filter((s) => s.enabled).length;
  const doc = {
    "# Generated — do not edit by hand": null,
    _generated: {
      at: new Date().toISOString(),
      source: "scripts/full-boot/generate-full-preview-runtime-values.mjs",
      max_wave: maxWave,
      microservice_count: microEntries.length,
      enabled_count: enabledCount,
    },
    fullBootServices,
    postgres: {
      maxConnections: 600,
      sharedBuffers: "256MB",
      initDatabases: [...initDatabases].sort(),
    },
  };

  const header = [
    "# AUTO-GENERATED — do not edit by hand.",
    "# Per-service overrides belong in specialEnv/specialSecretEnv/specialProbes/specialResources in",
    "# the generator, never here: a hand edit survives only until the next run. pct-service's",
    "# KEYCLOAK_BACKEND_SECRET was lost exactly that way.",
    `# Regenerate: node scripts/full-boot/generate-full-preview-runtime-values.mjs${maxWave !== null ? ` --max-wave ${maxWave}` : ""}`,
    `# Microservices: ${microEntries.length} | enabled: ${enabledCount}${maxWave !== null ? ` (waves 0–${maxWave})` : " (all waves)"}`,
    "",
  ].join("\n");

  const body = yaml.dump(
    { fullBootServices: doc.fullBootServices, postgres: doc.postgres },
    { lineWidth: 120 }
  );
  fs.writeFileSync(OUT, header + body, "utf8");
  console.log(`Wrote ${OUT} (${enabledCount}/${microEntries.length} enabled)`);
}

main();
