#!/usr/bin/env bash
# Shared helpers for VM local quality pipeline and report generation.
set -euo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
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

# Structured per-gate record. The phase-name arrays above answer "what failed";
# they cannot answer "did this gate run at all, against which commit, with what
# command, and where is the evidence". A gate that never ran and a gate that
# passed are indistinguishable in a name list — which is how a realm guard sat
# unwired for months while the summary said PASS.
PIPELINE_GATE_RECORDS=()

_pipeline_record_gate() {
  # id label applicability command started finished exit_code log status
  local rec
  rec=$(ID="$1" LABEL="$2" APPLIC="$3" CMD="$4" START="$5" FINISH="$6" \
        RC="$7" LOG="$8" STATUS="$9" COMMIT="$PIPELINE_COMMIT" python3 -c '
import json, os
print(json.dumps({
  "gate": os.environ["ID"],
  "label": os.environ["LABEL"],
  "applicability": os.environ["APPLIC"],
  "command": os.environ["CMD"],
  "started_at": os.environ["START"] or None,
  "finished_at": os.environ["FINISH"] or None,
  "exit_code": (int(os.environ["RC"]) if os.environ["RC"] != "" else None),
  "log": os.environ["LOG"] or None,
  "status": os.environ["STATUS"],
  "commit": os.environ["COMMIT"],
}))')
  PIPELINE_GATE_RECORDS+=("$rec")
}

# Record a gate that is deliberately out of scope for this run (path-selected or
# filtered). NOT_APPLICABLE is a distinct outcome from PASS: it asserts nothing
# about the domain, and the summary must never let it read as proof.
pipeline_gate_not_applicable() {
  local phase="$1" label="$2" reason="${3:-not applicable to the changed paths}"
  echo "N/A   $label ($reason)"
  _pipeline_record_gate "$phase" "$label" "$reason" "" "" "" "" "" "NOT_APPLICABLE"
}

pipeline_run_phase() {
  local phase="$1"
  local label="$2"
  local blocking="${3:-1}"
  shift 3
  if pipeline_should_skip "$phase"; then
    echo "SKIP phase: $label ($phase)"
    pipeline_gate_not_applicable "$phase" "$label" "filtered by PIPELINE_ONLY/PIPELINE_SKIP"
    return 0
  fi
  local log="$PIPELINE_LOG_DIR/${phase}.log"
  local cmd="$*"
  local started finished rc
  started="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo ""
  echo "========== PHASE: $label =========="
  if "$@" >"$log" 2>&1; then
    rc=0
    finished="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    pipeline_phase_pass "$label"
    _pipeline_record_gate "$phase" "$label" "applicable" "$cmd" "$started" "$finished" "$rc" "$log" "PASS"
    echo "PASS  $label"
    echo "LOG: $log"
    return 0
  fi
  rc=$?
  finished="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "FAIL  $label (see $log)"
  tail -n 30 "$log" 2>/dev/null || true
  if [[ "$blocking" == "1" ]]; then
    pipeline_phase_fail "$label"
  else
    pipeline_phase_advisory "$label"
  fi
  _pipeline_record_gate "$phase" "$label" "applicable" "$cmd" "$started" "$finished" "$rc" "$log" "FAIL"
  return 0
}

# Mandatory gates must each produce a record. A gate that silently never ran is
# indistinguishable from a passing one unless something asserts its presence —
# so this asserts it, and its absence is a pipeline failure.
PIPELINE_MANDATORY_GATES=(
  workspace tools security static frontend backend
  keycloak-realm-import change-safety repo-integrity
  # Promoted from advisory to blocking 2026-08-04, all measured passing on main.
  # Blocking alone is not enough: a blocking gate that silently stops being invoked
  # still reports nothing, so each is asserted present as well.
  runtime-truth-heuristics
  full-boot-discover full-boot-targets full-boot-doctrine
  full-boot-runtime full-boot-waves full-boot-inventory
)

pipeline_assert_mandatory_gates_ran() {
  local missing=()
  local ids
  ids="$(printf '%s\n' "${PIPELINE_GATE_RECORDS[@]-}" \
        | python3 -c 'import sys,json; print(" ".join(json.loads(l)["gate"] for l in sys.stdin if l.strip()))' 2>/dev/null || echo "")"
  # A gate the operator explicitly filtered out is not "missing" — it is recorded
  # NOT_APPLICABLE above and remains visible as such.
  for g in "${PIPELINE_MANDATORY_GATES[@]}"; do
    [[ " $ids " == *" $g "* ]] || missing+=("$g")
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    echo ""
    echo "PIPELINE FAIL  mandatory gate(s) produced no result: ${missing[*]}"
    echo "  A gate that did not run proves nothing. Treating absence as failure."
    PIPELINE_FAIL=1
    for g in "${missing[@]}"; do
      pipeline_phase_fail "$g (no result recorded)"
      _pipeline_record_gate "$g" "$g" "mandatory" "" "" "" "" "" "MISSING"
    done
  fi
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
      echo "- Run: bash scripts/full-boot/generate-artifacts.sh"
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
  printf '%s\n' "${PIPELINE_GATE_RECORDS[@]-}" >"$PIPELINE_REPORT_DIR/latest-gates.jsonl"
  export GATES_JSONL="$PIPELINE_REPORT_DIR/latest-gates.jsonl"
  export MANDATORY="$(IFS='|'; echo "${PIPELINE_MANDATORY_GATES[*]-}")"
  python3 <<'PY'
import json, os
passed = [x for x in os.environ.get("PASSED", "").split("|") if x]
failed = [x for x in os.environ.get("FAILED", "").split("|") if x]
advisory = [x for x in os.environ.get("ADVISORY", "").split("|") if x]
blocking = os.environ.get("BLOCKING") == "1"
gates = []
try:
    with open(os.environ["GATES_JSONL"]) as fh:
        gates = [json.loads(l) for l in fh if l.strip()]
except Exception:
    gates = []
_mandatory = [x for x in os.environ.get("MANDATORY", "").split("|") if x]
_status = {g.get("gate"): g.get("status") for g in gates}
_mandatory_not_passed = [g for g in _mandatory if _status.get(g) != "PASS"]
_mandatory_all_passed = not _mandatory_not_passed
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
  # Deploy eligibility is NOT the same as "nothing failed". A filtered run
  # (PIPELINE_ONLY=…) legitimately exits 0 having tested almost nothing — that is
  # a developer convenience, not a deploy credential. require-fresh-gates.sh reads
  # deploy_recommended, so every mandatory gate must actually have PASSED before
  # this is true. Otherwise `PIPELINE_ONLY=workspace` would authorise a deploy.
  "deploy_recommended": (not blocking) and _mandatory_all_passed,
  "vm_pipeline_passed": (not blocking) and _mandatory_all_passed,
  "mandatory_gates": _mandatory,
  "mandatory_not_passed": _mandatory_not_passed,
  # Per-gate truth: what ran, against which commit, with what command, where the
  # evidence is, and what the outcome was. NOT_APPLICABLE and MISSING are first
  # class here precisely so neither can be mistaken for a pass.
  "gates": gates,
  "gate_counts": {
      s: sum(1 for g in gates if g.get("status") == s)
      for s in ("PASS", "FAIL", "NOT_APPLICABLE", "MISSING")
  },
}
open(os.environ["JSON"], "w").write(json.dumps(doc, indent=2))
PY

  echo "Reports: $md"
  echo "         $json"
}
