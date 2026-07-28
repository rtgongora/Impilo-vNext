#!/usr/bin/env bash
set -euo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

# shellcheck source=scripts/deploy/_k3s-import-preview-images.sh
source "$REPO_PATH/scripts/deploy/_k3s-import-preview-images.sh"
run_k3s_import_preview_images

# shellcheck source=scripts/deploy/_preview-deploy-metadata.sh
source "$REPO_PATH/scripts/deploy/_preview-deploy-metadata.sh"
resolve_preview_deploy_metadata
export DEPLOY_BRANCH="$PREVIEW_DEPLOY_BRANCH"
export DEPLOY_COMMIT_SHA="$PREVIEW_DEPLOY_COMMIT"

bash scripts/deploy/preview-deploy.sh
kubectl rollout restart deployment/experience-bff deployment/one-ui-shell -n impilo-preview || true
sleep 25
kubectl get pods -n impilo-preview
curl -s -o /dev/null -w "http_root=%{http_code}\n" http://127.0.0.1/ || true
curl -s http://127.0.0.1/health/version 2>/dev/null | head -c 300 || true
