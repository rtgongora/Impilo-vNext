#!/usr/bin/env bash
# Summarize GitHub Actions CI + VM local pipeline evidence for deploy decisions.
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
CODE_TEST_RESULT="unknown"
GH_AUTH="no"
FALLBACK_AVAILABLE="yes"
VM_PIPELINE_PASSED="unknown"
VM_REPORT="$REPO_PATH/reports/pipeline/latest-summary.json"

echo "=== Impilo CI feedback ==="
echo "repo: $REPO_SLUG"
echo "branch: $BRANCH"
echo "commit: $HEAD_SHA ($(git rev-parse --short HEAD))"

if [[ -f "$VM_REPORT" ]]; then
  read -r VM_REPORT_COMMIT VM_REPORT_VERDICT VM_PIPELINE_PASSED < <(python3 -c "
import json
d=json.load(open('$VM_REPORT'))
print(d.get('commit',''), d.get('verdict',''), 'true' if d.get('vm_pipeline_passed') else 'false')
" 2>/dev/null || echo "  unknown false")
  if [[ "$VM_REPORT_COMMIT" == "$HEAD_SHA" ]]; then
    echo "vm_local_pipeline_verdict: $VM_REPORT_VERDICT"
    echo "vm_local_pipeline_passed: $VM_PIPELINE_PASSED"
  else
    echo "vm_local_pipeline: stale (report is for ${VM_REPORT_COMMIT:-unknown}, HEAD is $HEAD_SHA)"
    VM_PIPELINE_PASSED="stale"
  fi
else
  echo "vm_local_pipeline: no report — run: bash scripts/pipeline/run-local-quality-gates.sh"
fi

analyze_jobs_json() {
  local jobs_json="$1"
  python3 -c '
import json, sys
d = json.load(open(sys.argv[1]))
jobs = d.get("jobs", [])
if not jobs:
    print("ci_jobs_total: 0")
    print("ci_failure_mode: infrastructure_no_steps")
    print("ci_likely_cause: billing_lock_or_runner_unavailable")
    raise SystemExit(0)
zero = [j for j in jobs if not j.get("steps")]
skipped = [j for j in jobs if j.get("conclusion") == "skipped"]
failed = [j for j in jobs if j.get("conclusion") == "failure"]
print(f"ci_jobs_total: {len(jobs)}")
print(f"ci_jobs_zero_steps: {len(zero)}")
print(f"ci_jobs_skipped: {len(skipped)}")
print(f"ci_jobs_failed: {len(failed)}")
if jobs and len(zero) == len(jobs):
    print("ci_failure_mode: infrastructure_no_steps")
    print("ci_likely_cause: billing_lock_or_runner_unavailable")
elif failed and all(not j.get("steps") for j in failed):
    print("ci_failure_mode: infrastructure_no_steps")
    print("ci_likely_cause: billing_lock_or_runner_unavailable")
elif failed:
    print("ci_failure_mode: test_or_build_failures")
    for j in sorted(failed, key=lambda x: x["name"])[:20]:
        steps = j.get("steps") or []
        failed_steps = [s["name"] for s in steps if s.get("conclusion") == "failure"]
        fs = failed_steps or ["(no steps — infra)"]
        print("ci_failed_job:", j.get("name"), "| failed_steps=", fs)
' "$jobs_json"
}

if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
  GH_AUTH="yes"
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
    JOB_ANALYSIS="$(analyze_jobs_json "$JOBS_JSON" 2>/dev/null || true)"
    echo "$JOB_ANALYSIS"
    if echo "$JOB_ANALYSIS" | grep -q 'ci_failure_mode: infrastructure_no_steps'; then
      CI_INFRA_FAILURE="yes"
      CODE_TEST_RESULT="unknown"
      BLOCK_REASON="GitHub Actions infrastructure failure (billing/runner lock)"
    elif echo "$JOB_ANALYSIS" | grep -q 'ci_failure_mode: test_or_build_failures'; then
      CODE_TEST_RESULT="fail"
      BLOCK_REASON="GitHub CI test/build failures"
    fi
    rm -f "$JOBS_JSON"
  fi
  if [[ "$CONCLUSION" == "success" && "$(echo "$RUN_JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d[0].get("headSha","")[:7] if d else "")')" == "$(git rev-parse --short HEAD)" ]]; then
    CODE_TEST_RESULT="pass"
    DEPLOY_RECOMMENDED="yes"
    DEPLOY_BLOCKED="no"
    BLOCK_REASON="none"
  fi
else
  echo "source: GitHub API (unauthenticated)"
  echo "gh_authenticated: no"
  echo "hint: sudo apt install -y gh && gh auth login"
  API="https://api.github.com/repos/${REPO_SLUG}/actions/workflows/ci.yml/runs?branch=${BRANCH//\//%2F}&per_page=1"
  JSON="$(curl -fsS "$API" 2>/dev/null || echo '{}')"
  if echo "$JSON" | grep -qi 'rate limit'; then
    echo "ci_api_error: rate_limit"
    BLOCK_REASON="GitHub API rate limit"
  fi
  RUN_ID="$(echo "$JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); r=d.get("workflow_runs",[]); print(r[0]["id"] if r else "")' 2>/dev/null || true)"
  CONCLUSION="$(echo "$JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); r=d.get("workflow_runs",[]); print(r[0].get("conclusion") or r[0].get("status") or "unknown") if r else "none"' 2>/dev/null || true)"
  RUN_SHA="$(echo "$JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); r=d.get("workflow_runs",[]); print(r[0].get("head_sha","") if r else "")' 2>/dev/null || true)"
  echo "ci_run_id: ${RUN_ID:-none}"
  echo "ci_conclusion: $CONCLUSION"
  echo "ci_head_sha: $RUN_SHA"
  if [[ -n "$RUN_ID" ]]; then
    echo "ci_url: https://github.com/${REPO_SLUG}/actions/runs/${RUN_ID}"
    JOBS_JSON="$(mktemp)"
    curl -fsS "https://api.github.com/repos/${REPO_SLUG}/actions/runs/${RUN_ID}/jobs?per_page=100" -o "$JOBS_JSON" 2>/dev/null || echo '{}' > "$JOBS_JSON"
    echo ""
    echo "=== Jobs ==="
    python3 -c '
import json,sys
d=json.load(open(sys.argv[1]))
for j in sorted(d.get("jobs",[]), key=lambda x: x["name"]):
  steps=len(j.get("steps") or [])
  print((j.get("conclusion") or j.get("status")), j["name"], f"(steps={steps})")
' "$JOBS_JSON"
    JOB_ANALYSIS="$(analyze_jobs_json "$JOBS_JSON" 2>/dev/null || true)"
    echo "$JOB_ANALYSIS"
    if echo "$JOB_ANALYSIS" | grep -q 'ci_failure_mode: infrastructure_no_steps'; then
      CI_INFRA_FAILURE="yes"
      CODE_TEST_RESULT="unknown"
      BLOCK_REASON="GitHub Actions infrastructure failure (billing/runner lock)"
    elif echo "$JOB_ANALYSIS" | grep -q 'ci_failure_mode: test_or_build_failures'; then
      CODE_TEST_RESULT="fail"
      BLOCK_REASON="GitHub CI test/build failures"
    fi
    rm -f "$JOBS_JSON"
  fi
  if [[ "$CONCLUSION" == "success" && "$RUN_SHA" == "$HEAD_SHA" ]]; then
    CODE_TEST_RESULT="pass"
    DEPLOY_RECOMMENDED="yes"
    DEPLOY_BLOCKED="no"
    BLOCK_REASON="none"
  fi
fi

# VM local pipeline can authorize deploy when GitHub CI is infra-blocked
if [[ "$CI_INFRA_FAILURE" == "yes" && "$VM_PIPELINE_PASSED" == "true" ]]; then
  DEPLOY_RECOMMENDED="conditional"
  DEPLOY_BLOCKED="no"
  BLOCK_REASON="GitHub CI unavailable; VM local gates passed for this commit"
fi

echo ""
echo "=== Deployment decision support ==="
echo "deploy_recommended: $DEPLOY_RECOMMENDED"
echo "deploy_blocked: $DEPLOY_BLOCKED"
echo "block_reason: $BLOCK_REASON"
echo "ci_infra_failure: $CI_INFRA_FAILURE"
echo "code_test_result: $CODE_TEST_RESULT"
echo "gh_authenticated: $GH_AUTH"
echo "fallback_available: $FALLBACK_AVAILABLE"
echo "vm_local_pipeline_command: bash scripts/pipeline/run-local-quality-gates.sh"
echo "cursor_feedback_command: bash scripts/pipeline/cursor-local-feedback.sh"

if [[ "$CI_INFRA_FAILURE" == "yes" ]]; then
  echo ""
  echo "ACTION: GitHub Actions did not run application tests (billing/runner lock or 0-step jobs)."
  echo "Do NOT treat as code failure. Run VM local pipeline:"
  echo "  bash scripts/pipeline/run-local-quality-gates.sh"
fi

echo ""
echo "Manual deploy: bash scripts/deploy/manual-authorized-preview-deploy.sh"
