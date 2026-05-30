#!/usr/bin/env bash
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
NAMESPACE="${NAMESPACE:-impilo-preview}"
RELEASE="${RELEASE:-impilo-preview}"
CHART="$REPO_PATH/deploy/helm/impilo-vnext"
cd "$REPO_PATH"

BRANCH="$(git branch --show-current 2>/dev/null || echo unknown)"
COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
SHORT="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

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
