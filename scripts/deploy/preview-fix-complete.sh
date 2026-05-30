#!/usr/bin/env bash
# Complete preview fix: rebuild BFF with preview patches, import images, clean Helm, deploy, smoke test.
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
SUDO_PASS="${SUDO_PASS:-}"
if [[ -n "$SUDO_PASS" ]]; then echo "$SUDO_PASS" | sudo -S -v; fi

cd "$REPO_PATH"
export SUDO_PASS

log() { echo "[$(date '+%H:%M:%S')] $*"; }

log "Rebuild experience-bff (includes preview security + /health/version)"
cd "$REPO_PATH/services"
mvn -q package -pl experience-bff -am -DskipTests

log "Build and import container images"
bash "$REPO_PATH/scripts/dev/build-images.sh"

log "Clear stuck Helm release if needed"
helm uninstall impilo-preview -n impilo-preview 2>/dev/null || true
kubectl delete secret -n impilo-preview -l owner=helm 2>/dev/null || true
sleep 3

log "Deploy preview Helm chart"
bash "$REPO_PATH/scripts/deploy/preview-deploy.sh"

log "Wait for pods"
kubectl rollout status deployment/experience-bff -n impilo-preview --timeout=180s
kubectl rollout status deployment/one-ui-shell -n impilo-preview --timeout=120s

log "Pod status"
kubectl get pods,svc,ingress -n impilo-preview

log "Smoke tests"
bash "$REPO_PATH/scripts/deploy/preview-smoke-test.sh" || SMOKE=$?

log "Done (smoke exit=${SMOKE:-0})"
