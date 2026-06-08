/**
 * Canonical deployment lanes for full-registry coverage.
 * - runtime_k8s_microservice: Helm microservice.yaml + container image
 * - runtime_dedicated: experience-bff, one-ui-shell (dedicated templates)
 * - runtime_official: postgres, redis, kafka, etc. (upstream images in values)
 * - build_validate_only: internal packages, mobile, bundled UI, generated clients
 * - doctrine_only: non-deployable doctrine entries
 */
import fs from "node:fs";
import path from "node:path";

export const DEDICATED_RUNTIME = new Set([
  "experience-bff",
  "one-ui-shell",
]);

export const OFFICIAL_INFRA = new Set([
  "postgres",
  "redis",
  "kafka",
  "keycloak",
  "envoy",
  "minio",
  "hapi-fhir",
  "opa",
]);

const DEPLOYABLE_STRATEGIES = new Set([
  "shared-dockerfile-template",
  "dockerfile",
  "jib",
  "buildpacks",
]);

const NON_RUNTIME_STRATEGY_PREFIX = "not-required";

export function isNonRuntimeStrategy(strategy = "") {
  return (
    strategy.startsWith(NON_RUNTIME_STRATEGY_PREFIX) ||
    strategy === "official-helm-chart" ||
    strategy === "unknown-needs-review"
  );
}

/** Services counted in wave YAML (image build + optional helm / build lane). */
export function isWaveRuntimeService(entry) {
  const strategy = entry.image_strategy ?? entry.image_strategy ?? "";
  if (isNonRuntimeStrategy(strategy)) return false;
  if (entry.official_image) return false;
  return DEPLOYABLE_STRATEGIES.has(strategy);
}

/** Spring/Java (etc.) workloads deployed via templates/microservice.yaml. */
export function isRuntimeK8sMicroservice(entry) {
  if (DEDICATED_RUNTIME.has(entry.id)) return false;
  if (OFFICIAL_INFRA.has(entry.id)) return false;
  if (entry.official_image) return false;
  if (entry.component_type === "frontend_app" || entry.component_type === "mobile_app") return false;
  const strategy = entry.image_strategy ?? "";
  if (!["shared-dockerfile-template", "dockerfile", "jib"].includes(strategy)) return false;
  if (entry.component_type === "backend_service" || entry.runtime_kind === "kubernetes_service") {
    return true;
  }
  return !!(entry.image_required || entry.runtime_image_required);
}

export function deploymentLane(entry) {
  const id = entry.id;
  const strategy = entry.image_strategy ?? "";
  if (
    entry.full_boot_classification === "doctrine_only_future" ||
    entry.full_boot_classification === "deprecated_retired" ||
    entry.component_type === "external_dependency"
  ) {
    return "doctrine_only";
  }
  if (OFFICIAL_INFRA.has(id) || strategy === "official-upstream-image") return "runtime_official";
  if (DEDICATED_RUNTIME.has(id)) return "runtime_dedicated";
  if (isRuntimeK8sMicroservice(entry)) return "runtime_k8s_microservice";
  if (isNonRuntimeStrategy(strategy)) return "build_validate_only";
  if (isWaveRuntimeService(entry)) return "build_validate_only";
  return "build_validate_only";
}

export function runNotApplicableReason(entry) {
  const lane = deploymentLane(entry);
  if (lane === "runtime_k8s_microservice" || lane === "runtime_dedicated" || lane === "runtime_official") {
    return null;
  }
  return entry.reason ?? `lane=${lane} strategy=${entry.image_strategy ?? "unknown"}`;
}

export function parsePortAllocation(root) {
  const mdPath = path.join(root, "docs/runbooks/port-allocation.md");
  const ports = new Map();
  if (!fs.existsSync(mdPath)) return ports;
  const text = fs.readFileSync(mdPath, "utf8");
  for (const m of text.matchAll(/\|\s*\*?\*?(\d{4,5})\*?\*?\s*\|\s*`([^`]+)`/g)) {
    ports.set(m[2], Number(m[1]));
  }
  return ports;
}

export function resolveHttpPort(entry, portMap) {
  if (entry.default_http_port) return entry.default_http_port;
  return portMap.get(entry.id) ?? null;
}

export function deriveDatabaseName(serviceId) {
  const base = serviceId.replace(/-service$/, "");
  return base.replace(/-/g, "_");
}
