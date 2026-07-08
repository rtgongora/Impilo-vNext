#!/usr/bin/env bash
# User-authorized manual preview deploy. Accepts GitHub CI and/or VM local pipeline evidence.
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"

BRANCH="${DEPLOY_BRANCH:-$(git branch --show-current)}"
EXPECTED_SHA="${DEPLOY_COMMIT_SHA:-$(git rev-parse HEAD)}"
BYPASS_CI="${BYPASS_CI:-0}"
BYPASS_REASON="${BYPASS_REASON:-}"
PREVIEW_URL="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"
# Runs ON the VM, where the PUBLIC URL hairpin-NATs. Verify the live estate via the node-local
# ingress; PREVIEW_URL stays the documented external URL.
PREVIEW_INTERNAL_URL="${PREVIEW_INTERNAL_URL:-http://127.0.0.1}"
NON_INTERACTIVE="${NON_INTERACTIVE:-0}"
VM_REPORT="$REPO_PATH/reports/pipeline/latest-summary.json"

echo "=== Manual authorized preview deploy ==="
echo "Repo: $REPO_PATH"
echo "Branch: $BRANCH"
echo "Expected commit: $EXPECTED_SHA"
echo "Preview: $PREVIEW_URL"
echo ""

CI_INFRA="no"
CODE_TEST="unknown"
DEPLOY_BLOCKED="yes"
VM_PASSED="no"

if [[ "$BYPASS_CI" != "1" ]]; then
  FB="$(mktemp)"
  bash scripts/ci/collect-ci-feedback.sh "$BRANCH" | tee "$FB"
  grep -q "ci_infra_failure: yes" "$FB" && CI_INFRA="yes"
  grep -q "code_test_result: pass" "$FB" && CODE_TEST="pass"
  grep -q "code_test_result: fail" "$FB" && CODE_TEST="fail"
  grep -q "deploy_blocked: no" "$FB" && DEPLOY_BLOCKED="no"
  rm -f "$FB"
else
  echo "WARN: BYPASS_CI=1 — $BYPASS_REASON"
  DEPLOY_BLOCKED="no"
fi

if [[ -f "$VM_REPORT" ]]; then
  VM_OK="$(python3 -c "
import json,sys
d=json.load(open('$VM_REPORT'))
print('yes' if d.get('commit')==sys.argv[1] and d.get('vm_pipeline_passed') and not d.get('blocking_failure') else 'no')
" "$EXPECTED_SHA" 2>/dev/null || echo no)"
  [[ "$VM_OK" == "yes" ]] && VM_PASSED="yes"
  echo "VM local pipeline report: $VM_REPORT ($([[ "$VM_OK" == yes ]] && echo PASS || echo not valid for commit))"
fi

AUTH_MODE=""

if [[ "$BYPASS_CI" == "1" ]]; then
  AUTH_MODE="risk_override"
elif [[ "$CODE_TEST" == "pass" && "$DEPLOY_BLOCKED" == "no" ]]; then
  AUTH_MODE="github_ci"
elif [[ "$CI_INFRA" == "yes" && "$VM_PASSED" == "yes" ]]; then
  AUTH_MODE="vm_gates"
elif [[ "$DEPLOY_BLOCKED" == "yes" ]]; then
  echo ""
  echo "BLOCKED: GitHub CI not green and VM local gates not passed for $EXPECTED_SHA."
  echo "Fix failures, run: bash scripts/pipeline/run-local-quality-gates.sh"
  echo "Or set BYPASS_CI=1 BYPASS_REASON='...' with explicit user approval."
  exit 1
else
  echo "BLOCKED: insufficient pre-deploy evidence."
  exit 1
fi

if [[ "$NON_INTERACTIVE" != "1" ]]; then
  echo ""
  case "$AUTH_MODE" in
    github_ci)
      echo "GitHub CI passed for this commit. Type AUTHORIZE DEPLOY to continue:"
      read -r ans
      [[ "$ans" == "AUTHORIZE DEPLOY" ]] || { echo "Aborted."; exit 1; }
      ;;
    vm_gates)
      echo "GitHub Actions did not run due to infrastructure/billing issue."
      echo "VM local quality gates passed for commit $EXPECTED_SHA."
      echo "Type AUTHORIZE DEPLOY WITH VM GATES to continue:"
      read -r ans
      [[ "$ans" == "AUTHORIZE DEPLOY WITH VM GATES" ]] || { echo "Aborted."; exit 1; }
      ;;
    risk_override)
      echo "Risk override active. Type AUTHORIZE DEPLOY to continue:"
      read -r ans
      [[ "$ans" == "AUTHORIZE DEPLOY" ]] || { echo "Aborted."; exit 1; }
      ;;
  esac
fi

export DEPLOY_BRANCH="$BRANCH"
export DEPLOY_COMMIT_SHA="$EXPECTED_SHA"
bash scripts/deploy/github-actions-remote-preview-deploy.sh

DEPLOYED_SHA="$(git rev-parse HEAD)"
if [[ "$DEPLOYED_SHA" != "$EXPECTED_SHA" ]]; then
  echo "FAIL: deployed SHA $DEPLOYED_SHA != expected $EXPECTED_SHA"
  exit 1
fi

echo ""
echo "=== Post-deploy verification ==="
export PREVIEW_HOST="${PREVIEW_HOST:-127.0.0.1}"
bash scripts/deploy/preview-smoke-test.sh

VERSION_JSON="$(curl -sf "${PREVIEW_INTERNAL_URL}/health/version" || true)"
echo "health/version: $VERSION_JSON"
LIVE_SHA="$(echo "$VERSION_JSON" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("commit",""))' 2>/dev/null || true)"
if [[ "$LIVE_SHA" != "$EXPECTED_SHA" ]]; then
  echo "FAIL: /health/version commit $LIVE_SHA != expected $EXPECTED_SHA"
  exit 1
fi

LIVE_BRANCH="$(echo "$VERSION_JSON" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("branch",""))' 2>/dev/null || true)"
LIVE_ENV="$(echo "$VERSION_JSON" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("environment",""))' 2>/dev/null || true)"
if [[ "$LIVE_ENV" != "preview" ]]; then
  echo "FAIL: /health/version environment $LIVE_ENV != preview"
  exit 1
fi
if [[ -z "$LIVE_BRANCH" || "$LIVE_BRANCH" == "unknown" ]]; then
  echo "FAIL: /health/version branch is empty or unknown (expected $BRANCH)"
  exit 1
fi
if [[ "$LIVE_BRANCH" != "$BRANCH" ]]; then
  echo "FAIL: /health/version branch $LIVE_BRANCH != expected $BRANCH"
  exit 1
fi

kubectl get pods -n impilo-preview 2>/dev/null || true
echo ""
echo "SUCCESS: preview at $PREVIEW_URL reflects commit $EXPECTED_SHA (auth: $AUTH_MODE)"
