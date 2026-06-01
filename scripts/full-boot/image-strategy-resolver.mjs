/**
 * Canonical runtime image strategy resolution for full-boot pipeline.
 */
import fs from "node:fs";
import path from "node:path";

export const SHARED_TEMPLATE_PATH = "scripts/build/templates/impilo-jre-runtime.Dockerfile";

export const OFFICIAL_UPSTREAM = {
  postgres: { image: "postgres:16-alpine", chart: "bitnami/postgresql or deploy/helm/impilo-vnext" },
  redis: { image: "redis:7-alpine", chart: "bitnami/redis or subchart" },
  kafka: { image: "apache/kafka:3.7.1", chart: "strimzi or compose" },
  keycloak: { image: "quay.io/keycloak/keycloak:25.0", chart: "codecentric/keycloak or values.keycloak" },
  minio: { image: "minio/minio:latest", chart: "minio/minio" },
  envoy: { image: "envoyproxy/envoy:v1.31-latest", chart: "infra/envoy" },
  "hapi-fhir": { image: "hapiproject/hapi:v7.4.0", chart: "helm/butano" },
  opa: { image: "openpolicyagent/opa:0.68.0", chart: "none — sidecar in compose" },
};

export const UI_DEPLOYED = new Set(["one-ui-shell", "experience-bff"]);

export function runtimeKind(entry, classification) {
  const ct = entry.component_type;
  if (ct === "library") return "library";
  if (ct === "mobile_app") return "mobile";
  if (ct === "infrastructure") return "infrastructure";
  if (ct === "external_dependency") return "external";
  if (ct === "frontend_app") return "frontend";
  if (classification === "doctrine_only_future" || classification === "deprecated_retired") return "doctrine";
  return "kubernetes_service";
}

export function analyzeDockerfile(root, relPath) {
  if (!relPath || !fs.existsSync(path.join(root, relPath))) {
    return { present: false, runsMavenInside: false, copiesPrebuiltJar: false, isSharedTemplate: false };
  }
  const text = fs.readFileSync(path.join(root, relPath), "utf8");
  const runsMavenInside = /\bmvn\b/i.test(text) || /\bapk add\b.*\bmaven\b/i.test(text);
  const copiesPrebuiltJar = /COPY\s+.*target\/.*\.jar/i.test(text) || /COPY\s+--from=builder.*\.jar/i.test(text);
  const isSharedTemplate =
    /eclipse-temurin:21-jre/i.test(text) &&
    /COPY\s+target\/[^/]+-\*\.jar/i.test(text) &&
    !runsMavenInside &&
    !/AS builder/i.test(text);
  return { present: true, runsMavenInside, copiesPrebuiltJar, isSharedTemplate };
}

export function jarExists(root, mod) {
  const dir = path.join(root, "services", mod, "target");
  if (!fs.existsSync(dir)) return false;
  return fs.readdirSync(dir).some((f) => f.endsWith(".jar") && !f.includes("original"));
}

/**
 * @returns full image strategy record
 */
export function resolveImageStrategy(entry, facts, classification, root) {
  const id = entry.id;
  const ct = entry.component_type;
  const mod = entry.maven_module ?? id;
  const required = classification === "required_full_boot";
  const rk = runtimeKind(entry, classification);

  const base = {
    runtime_kind: rk,
    image_required: false,
    image_strategy: "unknown-needs-review",
    image_strategy_reclass: "unknown-needs-review",
    image_strategy_status: "not_required",
    image_build_command: null,
    image_name: `impilo/${id}`,
    official_image: null,
    official_chart: null,
    dockerfile_path: null,
    shared_template: null,
    jib_module: null,
    buildpack_builder: null,
    classification_confidence: entry.confidence ?? "certain",
  };

  if (classification === "internal_package") {
    return { ...base, image_strategy: "not-required-internal-package", image_strategy_reclass: "not-required-internal-package", image_strategy_status: "not_required", reason: "Maven/npm library — not a K8s workload" };
  }
  if (classification === "external_dependency") {
    return { ...base, image_strategy: "not-required-generated-client", image_strategy_reclass: "not-required-generated-client", image_strategy_status: "not_required", reason: "External or contract-only dependency" };
  }
  if (classification === "deprecated_retired" || classification === "doctrine_only_future") {
    return { ...base, image_strategy: "not-required-doctrine-only-component", image_strategy_reclass: "not-required-doctrine-only-component", image_strategy_status: "not_required", reason: "Deprecated or future-only" };
  }
  if (ct === "mobile_app") {
    return { ...base, image_strategy: "not-required-mobile-artifact", image_strategy_reclass: "not-required-mobile-artifact", image_strategy_status: "not_required", reason: "Expo/mobile artifact — not K8s service image" };
  }

  if (ct === "infrastructure") {
    const up = OFFICIAL_UPSTREAM[id];
    if (!up && required) {
      return { ...base, image_required: true, image_strategy: "missing-required-image-strategy", image_strategy_reclass: "missing-required-image-strategy", image_strategy_status: "missing", reason: "Required infra without official image mapping" };
    }
    return {
      ...base,
      image_required: required,
      image_strategy: up?.chart ? "official-helm-chart" : "official-upstream-image",
      image_strategy_reclass: "official-image-candidate",
      image_strategy_status: up ? "valid" : "missing",
      official_image: up?.image ?? null,
      official_chart: up?.chart ?? null,
      image_build_command: "no local build — pull/chart deploy",
      image_name: up?.image ?? id,
      reason: "Infrastructure uses upstream image/chart",
    };
  }

  if (ct === "frontend_app") {
    const dfPath = fs.existsSync(path.join(root, `ui/${id}/Dockerfile`)) ? `ui/${id}/Dockerfile` : fs.existsSync(path.join(root, `services/${id}/Dockerfile`)) ? `services/${id}/Dockerfile` : null;
    if (dfPath && (required || UI_DEPLOYED.has(id))) {
      return {
        ...base,
        image_required: required,
        image_strategy: "dockerfile",
        image_strategy_reclass: "dockerfile",
        image_strategy_status: "valid",
        dockerfile_path: dfPath,
        image_build_command: id === "one-ui-shell" ? `docker build -f ${dfPath} .` : `docker build -f ${dfPath}`,
        reason: "Experience layer with dedicated Dockerfile",
      };
    }
    return {
      ...base,
      image_required: false,
      image_strategy: "buildpacks",
      image_strategy_reclass: "buildpack-candidate",
      image_strategy_status: "not_required",
      buildpack_builder: "paketobuildpacks/nodejs",
      image_build_command: "pack build (optional) or bundled in one-ui-shell",
      reason: "UI workspace not independently deployed — not a missing Dockerfile failure",
    };
  }

  if (ct === "backend_service" && entry.build_tool === "maven") {
    const dfPath = facts.dockerfiles.get(mod) ?? null;
    const analysis = analyzeDockerfile(root, dfPath);
    const hasJar = jarExists(root, mod);

    if (analysis.present && analysis.runsMavenInside) {
      return {
        ...base,
        image_required: required,
        image_strategy: "jib",
        image_strategy_reclass: "jib-candidate",
        image_strategy_status: "valid",
        dockerfile_path: dfPath,
        jib_module: `services/${mod}`,
        image_build_command: hasJar ? `bash scripts/build/build-runtime-image-from-jar.sh ${id}` : `cd services/${mod} && mvn install -DskipTests && build-runtime-image-from-jar`,
        shared_template: SHARED_TEMPLATE_PATH,
        reason: "Dockerfile runs Maven in container — prefer Jib or pre-built JAR + shared template",
      };
    }
    if (analysis.present && analysis.isSharedTemplate) {
      return {
        ...base,
        image_required: required,
        image_strategy: "shared-dockerfile-template",
        image_strategy_reclass: "shared-template-candidate",
        image_strategy_status: "valid",
        dockerfile_path: dfPath,
        shared_template: SHARED_TEMPLATE_PATH,
        jib_module: `services/${mod}`,
        image_build_command: `docker build -f ${dfPath} OR build-runtime-image-from-jar.sh ${id}`,
        reason: "Thin JAR-copy Dockerfile — equivalent to shared template",
      };
    }
    if (analysis.present && analysis.copiesPrebuiltJar) {
      return {
        ...base,
        image_required: required,
        image_strategy: "dockerfile",
        image_strategy_reclass: "dockerfile",
        image_strategy_status: "valid",
        dockerfile_path: dfPath,
        image_build_command: `docker build -f ${dfPath} services/${mod}`,
        reason: "Dockerfile copies pre-built JAR",
      };
    }
    if (required && !hasJar) {
      return {
        ...base,
        image_required: true,
        image_strategy: "shared-dockerfile-template",
        image_strategy_reclass: "jib-candidate",
        image_strategy_status: "valid",
        jib_module: `services/${mod}`,
        shared_template: SHARED_TEMPLATE_PATH,
        image_build_command: `mvn package then build-runtime-image-from-jar.sh ${id}`,
        reason: "No Dockerfile — use reactor JAR + shared template",
      };
    }
    return {
      ...base,
      image_required: required,
      image_strategy: "shared-dockerfile-template",
      image_strategy_reclass: "shared-template-candidate",
      image_strategy_status: "valid",
      jib_module: `services/${mod}`,
      shared_template: SHARED_TEMPLATE_PATH,
      image_build_command: `build-runtime-image-from-jar.sh ${id}`,
      reason: "Java service — default shared JRE template after mvn install",
    };
  }

  if (ct === "backend_service" && !entry.buildable) {
    return { ...base, image_strategy: "not-required-doctrine-only-component", image_strategy_status: "not_required", reason: "Non-buildable backend placeholder" };
  }

  return {
    ...base,
    image_required: required,
    image_strategy: required ? "missing-required-image-strategy" : "unknown-needs-review",
    image_strategy_reclass: "unknown-needs-review",
    image_strategy_status: required ? "missing" : "unknown",
    reason: "Unclassified component",
  };
}

export function isBlockingStrategy(record, classification) {
  if (record.image_strategy === "missing-required-image-strategy") return true;
  if (record.image_strategy_status === "missing" && classification === "required_full_boot") return true;
  if (record.image_strategy_status === "failing" && classification === "required_full_boot") return true;
  return false;
}
