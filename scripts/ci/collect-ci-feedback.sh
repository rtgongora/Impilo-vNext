#!/usr/bin/env bash
# Summarize latest GitHub Actions CI for current branch (uses gh or GitHub API).
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"
BRANCH="${1:-$(git branch --show-current)}"
REPO_SLUG="${GITHUB_REPOSITORY:-rtgongora/Impilo-vNext}"
HEAD_SHA="$(git rev-parse HEAD)"
DEPLOY_RECOMMENDED="no"
DEPLOY_BLOCKED="yes"
BLOCK_REASON="CI status unknown or failed"

echo "=== Impilo CI feedback ==="
echo "repo: $REPO_SLUG"
echo "branch: $BRANCH"
echo "commit: $HEAD_SHA ($(git rev-parse --short HEAD))"

if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
  echo "source: GitHub CLI"
  RUN_JSON="$(gh run list --repo "$REPO_SLUG" --branch "$BRANCH" --workflow ci.yml --limit 1 --json databaseId,status,conclusion,url,headSha 2>/dev/null || echo '[]')"
  RUN_ID="$(echo "$RUN_JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d[0]["databaseId"] if d else "")' 2>/dev/null || true)"
  CONCLUSION="$(echo "$RUN_JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d[0].get("conclusion") or d[0].get("status") or "unknown") if d else "none"' 2>/dev/null || true)"
  URL="$(echo "$RUN_JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d[0].get("url","")) if d else ""' 2>/dev/null || true)"
  echo "ci_run_id: ${RUN_ID:-none}"
  echo "ci_url: $URL"
  echo "ci_conclusion: $CONCLUSION"
  if [[ -n "$RUN_ID" ]]; then
    echo ""
    echo "=== Jobs ==="
    gh run view "$RUN_ID" --repo "$REPO_SLUG" 2>/dev/null || true
    FAILED_JOBS="$(gh run view "$RUN_ID" --repo "$REPO_SLUG" --json jobs --jq '.jobs[] | select(.conclusion=="failure") | .name' 2>/dev/null || true)"
    if [[ -n "$FAILED_JOBS" ]]; then
      echo "failed_jobs:"
      echo "$FAILED_JOBS"
    fi
  fi
  if [[ "$CONCLUSION" == "success" && "$(echo "$RUN_JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d[0].get("headSha","")[:7] if d else "")')" == "$(git rev-parse --short HEAD)" ]]; then
    DEPLOY_RECOMMENDED="yes"
    DEPLOY_BLOCKED="no"
    BLOCK_REASON="none"
  elif [[ "$CONCLUSION" == "failure" ]]; then
    BLOCK_REASON="CI failed for latest run"
  fi
else
  echo "source: GitHub API (unauthenticated)"
  echo "hint: run 'gh auth login' on the VM for richer logs"
  API="https://api.github.com/repos/${REPO_SLUG}/actions/workflows/ci.yml/runs?branch=${BRANCH//\//%2F}&per_page=1"
  JSON="$(curl -fsS "$API" 2>/dev/null || echo '{}')"
  RUN_ID="$(echo "$JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); r=d.get("workflow_runs",[]); print(r[0]["id"] if r else "")' 2>/dev/null || true)"
  CONCLUSION="$(echo "$JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); r=d.get("workflow_runs",[]); print(r[0].get("conclusion") or r[0].get("status") or "unknown") if r else "none"' 2>/dev/null || true)"
  RUN_SHA="$(echo "$JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); r=d.get("workflow_runs",[]); print(r[0].get("head_sha","") if r else "")' 2>/dev/null || true)"
  echo "ci_run_id: ${RUN_ID:-none}"
  echo "ci_conclusion: $CONCLUSION"
  echo "ci_head_sha: $RUN_SHA"
  if [[ "$CONCLUSION" == "success" && "$RUN_SHA" == "$HEAD_SHA" ]]; then
    DEPLOY_RECOMMENDED="yes"
    DEPLOY_BLOCKED="no"
    BLOCK_REASON="none"
  elif [[ "$CONCLUSION" == "failure" ]]; then
    BLOCK_REASON="CI failed for latest run"
  fi
  if [[ -n "$RUN_ID" ]]; then
    echo "ci_url: https://github.com/${REPO_SLUG}/actions/runs/${RUN_ID}"
    JOBS_API="https://api.github.com/repos/${REPO_SLUG}/actions/runs/${RUN_ID}/jobs?per_page=100"
    echo ""
    echo "=== Jobs ==="
    curl -fsS "$JOBS_API" 2>/dev/null | python3 -c '
import json,sys
d=json.load(sys.stdin)
for j in sorted(d.get("jobs",[]), key=lambda x: x["name"]):
  print(j.get("conclusion") or j.get("status"), j["name"])
' || true
  fi
fi

echo ""
echo "=== Deployment decision support ==="
echo "deploy_recommended: $DEPLOY_RECOMMENDED"
echo "deploy_blocked: $DEPLOY_BLOCKED"
echo "block_reason: $BLOCK_REASON"
echo ""
echo "User must explicitly authorize preview deploy (see docs/environment/HUMAN_AUTHORIZED_PREVIEW_DEPLOYMENT.md)"
echo "Manual deploy: bash scripts/deploy/manual-authorized-preview-deploy.sh"
echo "Collect again after push: bash scripts/ci/collect-ci-feedback.sh"
