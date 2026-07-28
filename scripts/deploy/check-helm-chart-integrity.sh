#!/usr/bin/env bash
# Abort full boot deploy if Helm chart is truncated or cannot render required workloads.
set -euo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
CHART_DIR="$REPO_PATH/deploy/helm/impilo-vnext"
VALUES_FILE="$CHART_DIR/values-full-preview.yaml"
RUNTIME_VALUES="$CHART_DIR/values-full-preview-runtime.generated.yaml"
# shellcheck source=scripts/full-boot/_estate-guard.sh
source "$REPO_PATH/scripts/full-boot/_estate-guard.sh"
FULL_BOOT_MAX_WAVE="$(estate_normalize_max_wave "${FULL_BOOT_MAX_WAVE:-all}")"
MIN_TEMPLATES="${MIN_HELM_TEMPLATES:-12}"
REQUIRED_DEPLOYMENTS="${REQUIRED_FULL_BOOT_DEPLOYMENTS:-22}"

fail() {
  echo "CHART_INTEGRITY_FAIL: $*"
  echo "Chart integrity failed. Do not deploy."
  exit 1
}

[[ -d "$CHART_DIR" ]] || fail "missing chart dir $CHART_DIR"
[[ -f "$VALUES_FILE" ]] || fail "missing $VALUES_FILE"
[[ -f "$CHART_DIR/Chart.yaml" ]] || fail "missing Chart.yaml"

REQUIRED_TEMPLATES=(
  microservice.yaml
  postgres.yaml
  postgres-init-databases.yaml
  redis.yaml
  kafka.yaml
  keycloak.yaml
  hapi-fhir.yaml
  envoy.yaml
  minio.yaml
  experience-bff.yaml
  one-ui-shell.yaml
  _helpers.tpl
)

for t in "${REQUIRED_TEMPLATES[@]}"; do
  [[ -f "$CHART_DIR/templates/$t" ]] || fail "missing template templates/$t"
done

tpl_count="$(find "$CHART_DIR/templates" -maxdepth 1 \( -name '*.yaml' -o -name '*.tpl' \) | wc -l | tr -d ' ')"
if [[ "$tpl_count" -lt "$MIN_TEMPLATES" ]]; then
  fail "template count $tpl_count < minimum $MIN_TEMPLATES (chart may be truncated)"
fi

grep -q 'fullBootServices' "$CHART_DIR/templates/microservice.yaml" \
  || fail "microservice.yaml missing fullBootServices loop"

HELM_VAL_ARGS=(-f "$VALUES_FILE")
if [[ -f "$RUNTIME_VALUES" ]]; then
  if [[ "$FULL_BOOT_MAX_WAVE" == "all" ]]; then
    node "$REPO_PATH/scripts/full-boot/generate-full-preview-runtime-values.mjs" >/dev/null 2>&1 || true
  else
    node "$REPO_PATH/scripts/full-boot/generate-full-preview-runtime-values.mjs" --max-wave "$FULL_BOOT_MAX_WAVE" >/dev/null 2>&1 || true
  fi
  HELM_VAL_ARGS+=(-f "$RUNTIME_VALUES")
else
  echo "WARN: missing $RUNTIME_VALUES — microservice deployments may be absent"
fi

rendered="$(helm template impilo-chart-integrity "$CHART_DIR" "${HELM_VAL_ARGS[@]}" -n impilo-full-preview 2>/dev/null)" \
  || fail "helm template failed"

dep_count="$(echo "$rendered" | awk '/^kind: Deployment$/{c++} END{print c+0}')"
if [[ "$dep_count" -lt "$REQUIRED_DEPLOYMENTS" ]]; then
  fail "helm template produced $dep_count deployments (expected >= $REQUIRED_DEPLOYMENTS)"
fi

echo "CHART_INTEGRITY_PASS templates=$tpl_count deployments=$dep_count"
