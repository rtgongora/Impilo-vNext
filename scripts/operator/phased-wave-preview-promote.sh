#!/usr/bin/env bash
# Promote impilo-full-preview cumulatively wave-by-wave to avoid pod/memory crashes.
# Usage:
#   bash scripts/operator/phased-wave-preview-promote.sh [START_WAVE] [END_WAVE]
# Defaults: START_WAVE=1 END_WAVE=8
set -euo pipefail

REPO="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO"
NS="${IMPILO_FULLBOOT_NS:-impilo-full-preview}"
START_WAVE="${1:-1}"
END_WAVE="${2:-8}"
WAIT_TIMEOUT="${PHASED_WAVE_TIMEOUT:-420}"
POD_CEILING="${PHASED_WAVE_POD_CEILING:-108}"
PAUSE_SEC="${PHASED_WAVE_PAUSE_SEC:-30}"
CHART="deploy/helm/impilo-vnext"
RELEASE="impilo-full-preview"
BFF_IMAGE_TAG="${IMPILO_BFF_IMAGE_TAG:-preview}"
SHELL_IMAGE_TAG="${IMPILO_SHELL_IMAGE_TAG:-preview}"

# shellcheck source=scripts/deploy/_preview-deploy-metadata.sh
source "$REPO/scripts/deploy/_preview-deploy-metadata.sh"

wait_for_pod_ceiling() {
  local i=0
  while [[ $i -lt 90 ]]; do
    local n
    n="$(kubectl get pods -n "$NS" --no-headers 2>/dev/null | wc -l | tr -d ' ')"
    if [[ "$n" -le "$POD_CEILING" ]]; then
      echo "  pod count OK: $n (ceiling $POD_CEILING)"
      return 0
    fi
    echo "  waiting: $n pods > ceiling $POD_CEILING ..."
    sleep 10
    i=$((i + 1))
  done
  echo "WARN: pod count still above ceiling — continuing cautiously" >&2
  return 0
}

wave_service_ids() {
  local wave="$1"
  python3 - "$REPO" "$wave" <<'PY'
import sys, yaml
from pathlib import Path
repo, wave_id = Path(sys.argv[1]), int(sys.argv[2])
waves = yaml.safe_load((repo / "config/full-boot-waves.yml").read_text())
for w in waves.get("waves", []):
    if w["id"] == wave_id:
        for s in w.get("services", []):
            print(s)
        break
PY
}

wait_wave_deployments() {
  local wave="$1"
  local fail=0
  while read -r svc; do
    [[ -n "$svc" ]] || continue
    if ! kubectl get deploy "$svc" -n "$NS" >/dev/null 2>&1; then
      echo "  WARN: deploy/$svc not created yet"
      fail=1
      continue
    fi
    if kubectl rollout status "deployment/$svc" -n "$NS" --timeout="${WAIT_TIMEOUT}s" 2>/dev/null; then
      echo "  ready: $svc"
    else
      echo "  WARN: $svc not ready within ${WAIT_TIMEOUT}s"
      fail=1
    fi
  done < <(wave_service_ids "$wave")
  return "$fail"
}

helm_upgrade_wave() {
  local wave="$1"
  export FULL_BOOT_MAX_WAVE="$wave"
  resolve_preview_deploy_metadata
  node "$REPO/scripts/full-boot/generate-full-preview-runtime-values.mjs" --max-wave "$wave" >/dev/null
  node "$REPO/scripts/full-boot/generate-full-preview-bff-downstream-env.mjs" >/dev/null
  local enabled
  enabled="$(python3 -c "import yaml; d=yaml.safe_load(open('$CHART/values-full-preview-runtime.generated.yaml')); print(sum(1 for v in d['fullBootServices'].values() if v.get('enabled')))")"
  echo "  helm upgrade (cumulative through wave $wave, $enabled microservices enabled)"
  # Apply manifests without --wait: one CrashLoop pod must not block the whole wave.
  # Skip post-upgrade hooks on wave promotes — postgres-init reruns are slow and can timeout.
  helm upgrade --install "$RELEASE" "$CHART" -n "$NS" \
    -f "$CHART/values-full-preview.yaml" \
    -f "$CHART/values-full-preview-runtime.generated.yaml" \
    -f "$CHART/values-full-preview-bff-env.generated.yaml" \
    --set global.gitBranch="$PREVIEW_DEPLOY_BRANCH" \
    --set global.gitCommit="$PREVIEW_DEPLOY_COMMIT" \
    --set global.buildDate="$PREVIEW_DEPLOY_BUILD_DATE" \
    --set global.imageTag=preview \
    --set global.imagePullPolicy=Always \
    --set images.experienceBff.tag="$BFF_IMAGE_TAG" \
    --set images.oneUiShell.tag="$SHELL_IMAGE_TAG" \
    --no-hooks \
    2>&1 | tail -8
}

build_wave_images() {
  local wave="$1"
  echo "--- build images for wave $wave services only ---"
  while read -r svc; do
    [[ -n "$svc" ]] || continue
    echo "  build: $svc"
    bash "$REPO/scripts/build/build-full-vnext-images.sh" --only "$svc" 2>&1 | tail -4 || {
      echo "  WARN: build $svc had errors"
    }
  done < <(wave_service_ids "$wave")
}

import_wave_images() {
  local wave="$1"
  local only
  only="$(wave_service_ids "$wave" | paste -sd, -)"
  [[ -n "$only" ]] || return 0
  if [[ -x /usr/local/sbin/impilo-k3s-import-images ]]; then
    echo "--- import k3s images for wave $wave services ---"
    sudo -n /usr/local/sbin/impilo-k3s-import-images preview "$REPO" --runtime-only --only "$only" --force 2>&1 | tail -10 || {
      echo "WARN: k3s import wave $wave had errors"
    }
  else
    echo "WARN: impilo-k3s-import-images helper missing — relying on registry pull"
  fi
}

echo "=== Phased full-preview promote: waves $START_WAVE..$END_WAVE ==="
echo "Namespace: $NS | pod ceiling: $POD_CEILING | pause: ${PAUSE_SEC}s"
kubectl get deploy -n "$NS" --no-headers 2>/dev/null | wc -l | xargs -I{} echo "Deployments before: {}"

for wave in $(seq "$START_WAVE" "$END_WAVE"); do
  echo ""
  echo "========== WAVE $wave =========="
  wait_for_pod_ceiling

  build_wave_images "$wave"
  echo "--- push registry images for wave $wave (missing only) ---"
  IMPILO_PUSH_WAVE="$wave" bash "$REPO/scripts/build/push-images-to-local-registry.sh" wave "$wave" 2>&1 | tail -8 || {
    echo "WARN: push wave $wave had errors — continuing if images already in registry"
  }
  import_wave_images "$wave"

  echo "--- helm upgrade through wave $wave ---"
  helm_upgrade_wave "$wave" || {
    echo "FAIL: helm upgrade wave $wave"
    exit 1
  }

  echo "--- wait new wave deployments ---"
  wait_wave_deployments "$wave" || echo "WARN: some wave $wave deployments not ready (see kubectl get pods)"

  dep_count="$(kubectl get deploy -n "$NS" --no-headers 2>/dev/null | wc -l | tr -d ' ')"
  pod_count="$(kubectl get pods -n "$NS" --no-headers 2>/dev/null | wc -l | tr -d ' ')"
  echo "After wave $wave: deployments=$dep_count pods=$pod_count"
  bash "$REPO/scripts/operator/report-preview-generation.sh" 2>&1 | head -3 || true

  if [[ "$wave" -lt "$END_WAVE" ]]; then
    echo "Pausing ${PAUSE_SEC}s before next wave..."
    sleep "$PAUSE_SEC"
  fi
done

echo ""
echo "=== Phased promote complete (waves $START_WAVE..$END_WAVE) ==="
bash "$REPO/scripts/operator/report-preview-generation.sh"

# Final wave must reach the full estate. Waves are sequencing, not optionality.
MAX_WAVE_DEFINED="$(python3 - "$REPO" <<'PY' 2>/dev/null || echo 8
import sys, yaml
d = yaml.safe_load(open(f"{sys.argv[1]}/config/full-boot-waves.yml"))
print(max(w.get("id", 0) for w in d.get("waves", [])))
PY
)"
if [[ "$END_WAVE" -ge "$MAX_WAVE_DEFINED" ]]; then
  echo ""
  echo "--- Final wave reached: asserting full estate runtime image truth ---"
  if bash "$REPO/scripts/guard/check-runtime-image-truth.sh"; then
    echo "FINAL STATE: full_estate (all non-exempt runtime services digest-aligned)."
  else
    echo "FINAL STATE: NOT full_estate - runtime image truth FAILED. Some pods are stale."
    echo "DEPLOYMENT TRUTH FAILURE: Helm metadata is newer than running images. k3s is still serving stale runtime. Push/import/pin images and reroll."
    exit 1
  fi
else
  echo "FINAL STATE: partial_wave (stopped at wave $END_WAVE of $MAX_WAVE_DEFINED) - NOT full estate. All of vNext is vNext."
fi
