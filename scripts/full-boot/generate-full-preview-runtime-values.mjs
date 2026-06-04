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
    };
  }
  if (serviceId === "vito-service") {
    return { VITO_HMAC_PEPPER: "preview-vito-hmac-pepper-change-me-0123456789" };
  }
  if (serviceId === "product-registry-service") {
    return {
      KEYCLOAK_ISSUER: "",
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: "",
      IMPILO_SECURITY_ALLOW_ANONYMOUS: "true",
    };
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
    const env = specialEnv(entry.id);
    if (env) block.env = env;
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
      initDatabases: [...initDatabases].sort(),
    },
  };

  const header = [
    "# AUTO-GENERATED — do not edit by hand.",
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
