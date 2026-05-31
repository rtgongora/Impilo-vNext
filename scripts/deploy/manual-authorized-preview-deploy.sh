#!/usr/bin/env bash
# User-authorized manual preview deploy. Refuses by default if CI failed/unknown.
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"

BRANCH="${DEPLOY_BRANCH:-$(git branch --show-current)}"
EXPECTED_SHA="${DEPLOY_COMMIT_SHA:-$(git rev-parse HEAD)}"
BYPASS_CI="${BYPASS_CI:-0}"
BYPASS_REASON="${BYPASS_REASON:-}"
PREVIEW_URL="${PREVIEW_URL:-http://41.57.127.235}"
NON_INTERACTIVE="${NON_INTERACTIVE:-0}"

echo "=== Manual authorized preview deploy ==="
echo "Repo: $REPO_PATH"
echo "Branch: $BRANCH"
echo "Expected commit: $EXPECTED_SHA"
echo "Preview: $PREVIEW_URL"
echo ""

if [[ "$BYPASS_CI" != "1" ]]; then
  FB="$(mktemp)"
  if bash scripts/ci/collect-ci-feedback.sh "$BRANCH" | tee "$FB"; then
    if grep -q "deploy_blocked: yes" "$FB"; then
      echo ""
      echo "BLOCKED: CI not green for this branch/commit."
      echo "Fix CI or set BYPASS_CI=1 BYPASS_REASON='...' with explicit user approval."
      exit 1
    fi
  else
    echo "BLOCKED: could not determine CI status."
    exit 1
  fi
else
  echo "WARN: BYPASS_CI=1 — $BYPASS_REASON"
fi

if [[ "$NON_INTERACTIVE" != "1" ]]; then
  echo "Type AUTHORIZE DEPLOY to continue:"
  read -r ans
  if [[ "$ans" != "AUTHORIZE DEPLOY" ]]; then
    echo "Aborted."
    exit 1
  fi
fi

export DEPLOY_BRANCH="$BRANCH"
bash scripts/deploy/github-actions-remote-preview-deploy.sh

DEPLOYED_SHA="$(git rev-parse HEAD)"
if [[ "$DEPLOYED_SHA" != "$EXPECTED_SHA" ]]; then
  echo "FAIL: deployed SHA $DEPLOYED_SHA != expected $EXPECTED_SHA"
  exit 1
fi

echo ""
echo "=== Post-deploy verification ==="
export PREVIEW_HOST="${PREVIEW_HOST:-41.57.127.235}"
bash scripts/deploy/preview-smoke-test.sh

VERSION_JSON="$(curl -sf "${PREVIEW_URL}/health/version" || true)"
echo "health/version: $VERSION_JSON"
LIVE_SHA="$(echo "$VERSION_JSON" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("commit",""))' 2>/dev/null || true)"
if [[ "$LIVE_SHA" != "$EXPECTED_SHA" ]]; then
  echo "FAIL: /health/version commit $LIVE_SHA != expected $EXPECTED_SHA"
  exit 1
fi

kubectl get pods -n impilo-preview 2>/dev/null || true
echo ""
echo "SUCCESS: preview at $PREVIEW_URL reflects commit $EXPECTED_SHA"
