#!/usr/bin/env bash
# Shared helpers for VM local quality pipeline and report generation.
set -euo pipefail

REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
PIPELINE_REPORT_DIR="${PIPELINE_REPORT_DIR:-$REPO_PATH/reports/pipeline}"
PIPELINE_LOG_DIR="${PIPELINE_LOG_DIR:-/tmp/impilo-pipeline-gates}"
mkdir -p "$PIPELINE_REPORT_DIR" "$PIPELINE_LOG_DIR"

PIPELINE_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
PIPELINE_COMMIT="$(git -C "$REPO_PATH" rev-parse HEAD 2>/dev/null || echo unknown)"
PIPELINE_COMMIT_SHORT="$(git -C "$REPO_PATH" rev-parse --short HEAD 2>/dev/null || echo unknown)"
PIPELINE_BRANCH="$(git -C "$REPO_PATH" branch --show-current 2>/dev/null || echo unknown)"

PIPELINE_FAILED=()
PIPELINE_PASSED=()
PIPELINE_ADVISORY=()
PIPELINE_FAIL=0

pipeline_phase_pass() { PIPELINE_PASSED+=("$1"); }
pipeline_phase_fail() { PIPELINE_FAILED+=("$1"); PIPELINE_FAIL=1; }
pipeline_phase_advisory() { PIPELINE_ADVISORY+=("$1"); }

pipeline_should_skip() {
  local phase="$1"
  local only="${PIPELINE_ONLY:-}"
  local skip="${PIPELINE_SKIP:-}"
  if [[ -n "$only" ]] && ! echo ",$only," | grep -q ",$phase,"; then
    return 0
  fi
  if [[ -n "$skip" ]] && echo ",$skip," | grep -q ",$phase,"; then
    return 0
  fi
  return 1
}

pipeline_run_phase() {
  local phase="$1"
  local label="$2"
  local blocking="${3:-1}"
  shift 3
  if pipeline_should_skip "$phase"; then
    echo "SKIP phase: $label ($phase)"
    return 0
  fi
  local log="$PIPELINE_LOG_DIR/${phase}.log"
  echo ""
  echo "========== PHASE: $label =========="
  if "$@" >"$log" 2>&1; then
    pipeline_phase_pass "$label"
    echo "PASS  $label"
    echo "LOG: $log"
    return 0
  fi
  echo "FAIL  $label (see $log)"
  tail -n 30 "$log" 2>/dev/null || true
  if [[ "$blocking" == "1" ]]; then
    pipeline_phase_fail "$label"
  else
    pipeline_phase_advisory "$label"
  fi
  return 0
}

pipeline_write_reports() {
  local verdict="$1"
  local md="$PIPELINE_REPORT_DIR/latest-summary.md"
  local json="$PIPELINE_REPORT_DIR/latest-summary.json"
  local fail_md="$PIPELINE_REPORT_DIR/latest-failures.md"
  local change_md="$PIPELINE_REPORT_DIR/latest-change-summary.md"

  local changed_files
  changed_files="$(git -C "$REPO_PATH" diff --name-only HEAD~1...HEAD 2>/dev/null | tr '\n' ' ' || true)"

  if [[ -f /tmp/impilo-change-summary.txt ]]; then
    cp /tmp/impilo-change-summary.txt "$change_md" 2>/dev/null || true
  else
    echo "(no change summary)" >"$change_md"
  fi

  {
    echo "# Pipeline summary"
    echo ""
    echo "- **Verdict:** $verdict"
    echo "- **Started:** $PIPELINE_STARTED_AT"
    echo "- **Finished:** $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "- **Branch:** $PIPELINE_BRANCH"
    echo "- **Commit:** $PIPELINE_COMMIT_SHORT (\`$PIPELINE_COMMIT\`)"
    echo "- **Mode:** ${PIPELINE_CI_MODE:-vm}"
    echo ""
    echo "## Passed (${#PIPELINE_PASSED[@]})"
    for p in "${PIPELINE_PASSED[@]}"; do echo "- $p"; done
    echo ""
    echo "## Blocking failures (${#PIPELINE_FAILED[@]})"
    for p in "${PIPELINE_FAILED[@]}"; do echo "- $p"; done
    [[ ${#PIPELINE_FAILED[@]} -eq 0 ]] && echo "- none"
    echo ""
    echo "## Advisory (${#PIPELINE_ADVISORY[@]})"
    for p in "${PIPELINE_ADVISORY[@]}"; do echo "- $p"; done
    [[ ${#PIPELINE_ADVISORY[@]} -eq 0 ]] && echo "- none"
    echo ""
    echo "## Deploy recommendation"
    if [[ "$PIPELINE_FAIL" -ne 0 ]]; then
      echo "BLOCKED — fix blocking failures before preview deploy."
    elif [[ ${#PIPELINE_ADVISORY[@]} -gt 0 ]]; then
      echo "PASS WITH ADVISORY WARNINGS — user may authorize deploy after review."
    else
      echo "PASS — user may authorize preview deploy after explicit approval."
    fi
    echo ""
    echo "## Full vNext runtime truth"
    if [[ -f "$REPO_PATH/reports/full-boot/discovery-summary.json" ]]; then
      python3 -c "
import json, pathlib
p = pathlib.Path('$REPO_PATH/reports/full-boot/discovery-summary.json')
if p.exists():
    d = json.loads(p.read_text())
    print(f\"- Discovered components: **{d.get('total', '?')}**\")
    for plane, n in sorted((d.get('by_plane') or {}).items()):
        print(f\"  - {plane}: {n}\")
    for c, n in sorted((d.get('by_classification') or {}).items()):
        print(f\"  - {c}: {n}\")
    print(f\"- Runtime image required: {d.get('runtime_image_required_count', '?')}\")
    print(f\"- Missing required strategy: {d.get('missing_required_image_strategy', '?')}\")
    isc = d.get('image_strategy') or {}
    if isc:
        top = sorted(isc.items(), key=lambda x: -x[1])[:6]
        print(\"- Top image strategies: \" + \", \".join(f\"{k}={v}\" for k,v in top))
" 2>/dev/null || echo "- (discovery summary parse failed)"
    else
      echo "- Run: node scripts/full-boot/generate-full-boot-artifacts.mjs"
    fi
    if [[ -f "$REPO_PATH/reports/full-boot/full-boot-runtime-report.json" ]]; then
      python3 -c "
import json, pathlib
r = json.loads(pathlib.Path('$REPO_PATH/reports/full-boot/full-boot-runtime-report.json').read_text())
print(f\"- Full boot status: **{r.get('full_boot_status')}**\")
print(f\"- Required full boot: {r.get('required_full_boot')}\")
print(f\"- Build pass/fail: {r.get('build_pass')}/{r.get('build_fail')}\")
print(f\"- Runtime images required: {r.get('runtime_image_required_count', '?')}\")
print(f\"- Image pass / fail / blocking: {r.get('image_pass')}/{r.get('image_fail')}/{r.get('blocking_failure_count', 0)}\")
print(f\"- Missing required strategy: {r.get('missing_required_image_strategy_count', 0)}\")
if r.get('image_strategy_summary'):
    s = r['image_strategy_summary']
    print(f\"- Dockerfile / shared / jib: {s.get('dockerfile_count','?')}/{s.get('shared_template_count','?')}/{s.get('jib_count','?')}\")
    print(f\"- Official image+chart / not-required: {s.get('official_image_count',0)+s.get('official_chart_count',0)}/{s.get('not_required_count','?')}\")
" 2>/dev/null || true
    fi
    echo "- Full boot deploy (separate namespace): \`bash scripts/deploy/full-boot-preview-deploy.sh\`"
    echo "- Slice preview preserved: namespace \`impilo-preview\`"
    echo ""
    echo "## Next commands"
    echo '```bash'
    echo "bash scripts/pipeline/cursor-local-feedback.sh"
    echo "bash scripts/ci/collect-ci-feedback.sh"
    echo "bash scripts/deploy/manual-authorized-preview-deploy.sh"
    echo "bash scripts/build/build-full-vnext.sh"
    echo "bash scripts/guard/check-full-boot-runtime-completeness.sh"
    echo '```'
  } >"$md"

  {
    echo "# Pipeline failures"
    echo ""
    for p in "${PIPELINE_FAILED[@]}"; do
      echo "## $p"
      local slug
      slug="$(echo "$p" | tr ' /' '__' | tr '[:upper:]' '[:lower:]')"
      local log="$PIPELINE_LOG_DIR/${slug}.log"
      [[ -f "$log" ]] && tail -n 50 "$log" | sed 's/^/    /' || echo "    (see phase logs under $PIPELINE_LOG_DIR)"
      echo ""
    done
    [[ ${#PIPELINE_FAILED[@]} -eq 0 ]] && echo "No blocking failures."
  } >"$fail_md"

  export VERDICT="$verdict" BRANCH="$PIPELINE_BRANCH" COMMIT="$PIPELINE_COMMIT"
  export COMMIT_SHORT="$PIPELINE_COMMIT_SHORT" STARTED="$PIPELINE_STARTED_AT"
  export FINISHED="$(date -u +%Y-%m-%dT%H:%M:%SZ)" MODE="${PIPELINE_CI_MODE:-vm}"
  export PASSED="$(IFS='|'; echo "${PIPELINE_PASSED[*]-}")"
  export FAILED="$(IFS='|'; echo "${PIPELINE_FAILED[*]-}")"
  export ADVISORY="$(IFS='|'; echo "${PIPELINE_ADVISORY[*]-}")"
  export BLOCKING="$PIPELINE_FAIL" JSON="$json"
  python3 <<'PY'
import json, os
passed = [x for x in os.environ.get("PASSED", "").split("|") if x]
failed = [x for x in os.environ.get("FAILED", "").split("|") if x]
advisory = [x for x in os.environ.get("ADVISORY", "").split("|") if x]
blocking = os.environ.get("BLOCKING") == "1"
doc = {
  "verdict": os.environ["VERDICT"],
  "branch": os.environ["BRANCH"],
  "commit": os.environ["COMMIT"],
  "commit_short": os.environ["COMMIT_SHORT"],
  "started_at": os.environ["STARTED"],
  "finished_at": os.environ["FINISHED"],
  "mode": os.environ.get("MODE", "vm"),
  "passed_phases": passed,
  "failed_phases": failed,
  "advisory_phases": advisory,
  "blocking_failure": blocking,
  "deploy_recommended": not blocking,
  "vm_pipeline_passed": not blocking,
}
open(os.environ["JSON"], "w").write(json.dumps(doc, indent=2))
PY

  echo "Reports: $md"
  echo "         $json"
}
