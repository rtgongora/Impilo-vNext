#!/usr/bin/env bash
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
NAMESPACE="${NAMESPACE:-impilo-preview}"
RELEASE="${RELEASE:-impilo-preview}"
CHART="$REPO_PATH/deploy/helm/impilo-vnext"
cd "$REPO_PATH"

# shellcheck source=scripts/deploy/_preview-deploy-metadata.sh
source "$REPO_PATH/scripts/deploy/_preview-deploy-metadata.sh"
resolve_preview_deploy_metadata

BRANCH="$PREVIEW_DEPLOY_BRANCH"
COMMIT="$PREVIEW_DEPLOY_COMMIT"
BUILD_DATE="$PREVIEW_DEPLOY_BUILD_DATE"
SHORT="$(git rev-parse --short "$COMMIT" 2>/dev/null || echo unknown)"

helm upgrade --install "$RELEASE" "$CHART" \
  --namespace "$NAMESPACE" \
  --create-namespace \
  -f "$CHART/values-preview.yaml" \
  --set global.gitBranch="$BRANCH" \
  --set global.gitCommit="$COMMIT" \
  --set global.buildDate="$BUILD_DATE" \
  --set images.experienceBff.tag="preview" \
  --set images.oneUiShell.tag="preview" \
  --set images.experienceBff.pullPolicy=Never \
  --set images.oneUiShell.pullPolicy=Never \
  --timeout 5m

echo "Deployed branch=$BRANCH commit=$SHORT"
kubectl get pods,svc,ingress -n "$NAMESPACE"
