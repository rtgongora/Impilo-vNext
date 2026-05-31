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
CI_INFRA_FAILURE="no"
RUN_ID=""
CONCLUSION="none"
URL=""

echo "=== Impilo CI feedback ==="
echo "repo: $REPO_SLUG"
echo "branch: $BRANCH"
echo "commit: $HEAD_SHA ($(git rev-parse --short HEAD))"

analyze_jobs_json() {
  local jobs_json="$1"
  python3 <<'PY' "$jobs_json"
import json, sys
d = json.load(open(sys.argv[1]))
jobs = d.get("jobs", [])
if not jobs:
    print("ci_jobs_total: 0")
    sys.exit(0)
zero = [j for j in jobs if not j.get("steps")]
failed = [j for j in jobs if j.get("conclusion") == "failure"]
print(f"ci_jobs_total: {len(jobs)}")
print(f"ci_jobs_zero_steps: {len(zero)}")
print(f"ci_jobs_failed: {len(failed)}")
if jobs and len(zero) == len(jobs):
    print("ci_failure_mode: infrastructure_no_steps")
    print("ci_likely_cause: GitHub Actions billing lock or runner unavailable (jobs never started)")
elif failed:
    print("ci_failure_mode: job_failures")
    for j in sorted(failed, key=lambda x: x["name"])[:15]:
        steps = j.get("steps") or []
        failed_steps = [s["name"] for s in steps if s.get("conclusion") == "failure"]
        print(f"ci_failed_job: {j['name']} | failed_steps={failed_steps or ['(no steps — infra)']}")
PY
}

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
    JOBS_JSON="$(mktemp)"
    gh api "repos/${REPO_SLUG}/actions/runs/${RUN_ID}/jobs?per_page=100" > "$JOBS_JSON" 2>/dev/null || echo '{}' > "$JOBS_JSON"
    analyze_jobs_json "$JOBS_JSON"
    if grep -q 'ci_failure_mode: infrastructure_no_steps' <(analyze_jobs_json "$JOBS_JSON" 2>/dev/null); then
      CI_INFRA_FAILURE="yes"
      BLOCK_REASON="GitHub Actions did not start jobs (billing/runner lock suspected)"
    fi
    rm -f "$JOBS_JSON"
  fi
  if [[ "$CONCLUSION" == "success" && "$(echo "$RUN_JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d[0].get("headSha","")[:7] if d else "")')" == "$(git rev-parse --short HEAD)" ]]; then
    DEPLOY_RECOMMENDED="yes"
    DEPLOY_BLOCKED="no"
    BLOCK_REASON="none"
  elif [[ "$CONCLUSION" == "failure" && "$CI_INFRA_FAILURE" != "yes" ]]; then
    BLOCK_REASON="CI failed for latest run (see failed jobs above)"
  fi
else
  echo "source: GitHub API (unauthenticated)"
  echo "hint: install and auth GitHub CLI on the VM:"
  echo "  sudo apt install -y gh && gh auth login"
  API="https://api.github.com/repos/${REPO_SLUG}/actions/workflows/ci.yml/runs?branch=${BRANCH//\//%2F}&per_page=1"
  JSON="$(curl -fsS "$API" 2>/dev/null || echo '{}')"
  RUN_ID="$(echo "$JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); r=d.get("workflow_runs",[]); print(r[0]["id"] if r else "")' 2>/dev/null || true)"
  CONCLUSION="$(echo "$JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); r=d.get("workflow_runs",[]); print(r[0].get("conclusion") or r[0].get("status") or "unknown") if r else "none"' 2>/dev/null || true)"
  RUN_SHA="$(echo "$JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); r=d.get("workflow_runs",[]); print(r[0].get("head_sha","") if r else "")' 2>/dev/null || true)"
  echo "ci_run_id: ${RUN_ID:-none}"
  echo "ci_conclusion: $CONCLUSION"
  echo "ci_head_sha: $RUN_SHA"
  if [[ -n "$RUN_ID" ]]; then
    echo "ci_url: https://github.com/${REPO_SLUG}/actions/runs/${RUN_ID}"
    JOBS_API="https://api.github.com/repos/${REPO_SLUG}/actions/runs/${RUN_ID}/jobs?per_page=100"
    JOBS_JSON="$(mktemp)"
    curl -fsS "$JOBS_API" -o "$JOBS_JSON" 2>/dev/null || echo '{}' > "$JOBS_JSON"
    echo ""
    echo "=== Jobs ==="
    python3 -c '
import json,sys
d=json.load(open(sys.argv[1]))
for j in sorted(d.get("jobs",[]), key=lambda x: x["name"]):
  steps=len(j.get("steps") or [])
  print((j.get("conclusion") or j.get("status")), j["name"], f"(steps={steps})")
' "$JOBS_JSON"
    analyze_jobs_json "$JOBS_JSON" | tee /tmp/ci-job-analysis.txt
    if grep -q 'ci_failure_mode: infrastructure_no_steps' /tmp/ci-job-analysis.txt 2>/dev/null; then
      CI_INFRA_FAILURE="yes"
      BLOCK_REASON="GitHub Actions did not start jobs (billing/runner lock suspected)"
    fi
    rm -f "$JOBS_JSON" /tmp/ci-job-analysis.txt
  fi
  if [[ "$CONCLUSION" == "success" && "$RUN_SHA" == "$HEAD_SHA" ]]; then
    DEPLOY_RECOMMENDED="yes"
    DEPLOY_BLOCKED="no"
    BLOCK_REASON="none"
  elif [[ "$CONCLUSION" == "failure" && "$CI_INFRA_FAILURE" != "yes" ]]; then
    BLOCK_REASON="CI failed for latest run"
  fi
fi

echo ""
echo "=== Deployment decision support ==="
echo "deploy_recommended: $DEPLOY_RECOMMENDED"
echo "deploy_blocked: $DEPLOY_BLOCKED"
echo "block_reason: $BLOCK_REASON"
echo "ci_infra_failure: $CI_INFRA_FAILURE"
if [[ "$CI_INFRA_FAILURE" == "yes" ]]; then
  echo ""
  echo "ACTION REQUIRED: Resolve GitHub billing/runner issue in repo/org Settings → Billing."
  echo "CI annotations typically say: 'account is locked due to a billing issue'."
  echo "No application test executed; do not treat failures as code regressions until jobs have steps > 0."
fi
echo ""
echo "User must explicitly authorize preview deploy (see docs/environment/HUMAN_AUTHORIZED_PREVIEW_DEPLOYMENT.md)"
echo "Manual deploy: bash scripts/deploy/manual-authorized-preview-deploy.sh"
echo "Collect again after push: bash scripts/ci/collect-ci-feedback.sh"
