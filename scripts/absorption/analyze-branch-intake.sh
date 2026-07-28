#!/usr/bin/env bash
# Read-only branch intake analysis vs fixed Product Truth branch.
set -euo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=scripts/absorption/_absorption-common.sh
source "$SCRIPT_DIR/_absorption-common.sh"

BASE_INPUT="${1:-$DEFAULT_PRODUCT_TRUTH_BRANCH}"
SOURCE_INPUT="${2:?source required}"

ABSORPTION_MODE="analysis"
ABSORPTION_STARTED_AT="${ABSORPTION_STARTED_AT:-$(absorption_now)}"
absorption_ensure_report_dir

MD_OUT="$(absorption_report_path latest-analysis.md)"
JSON_OUT="$(absorption_report_path latest-analysis.json)"
CONFLICT_MD="$(mktemp)"
CONFLICT_JSON="$(mktemp)"
CLASSIFY_JSON="$(mktemp)"
INFER_JSON="$(mktemp)"
DECISION_JSON="$(mktemp)"
GATES_JSON="$(mktemp)"
GATES_MD="$(mktemp)"
trap 'rm -f "$CONFLICT_MD" "$CONFLICT_JSON" "$CLASSIFY_JSON" "$INFER_JSON" "$DECISION_JSON" "$GATES_JSON" "$GATES_MD"' EXIT

absorption_check_branch_safety "GATE 1 — Branch safety"
absorption_resolve_context "$BASE_INPUT" "$SOURCE_INPUT"

mapfile -t CHANGED < <(absorption_changed_files)
mapfile -t DELETED < <(absorption_deleted_files)
mapfile -t ADDED < <(absorption_added_files)
UNIQUE_COMMITS="$(absorption_unique_commits)"
DIFF_STAT="$(absorption_diff_stat)"
RENAMED="$(absorption_renamed_files)"

# GATE 2 — Difference discovery + purpose inference
export ABSORPTION_SOURCE_REF ABSORPTION_BASE_REF ABSORPTION_SOURCE_GROUP ABSORPTION_EXPECTED_VALUE
export REPO_PATH ABSORPTION_BASE_COMMIT ABSORPTION_SOURCE_COMMIT
python3 >"$INFER_JSON" <<'PY'
import json, os, subprocess, re
from collections import Counter

repo = os.environ["REPO_PATH"]
base = os.environ["ABSORPTION_BASE_COMMIT"]
source = os.environ["ABSORPTION_SOURCE_COMMIT"]
source_ref = os.environ["ABSORPTION_SOURCE_REF"]
expected = os.environ.get("ABSORPTION_EXPECTED_VALUE", "").strip()
source_group = os.environ.get("ABSORPTION_SOURCE_GROUP", "").strip()

def sh(*args):
    return subprocess.check_output(args, cwd=repo, text=True, stderr=subprocess.DEVNULL)

commits = sh("git", "log", "--format=%h %s", f"{base}..{source}").strip().splitlines()
changed = sh("git", "diff", "--name-only", f"{base}...{source}").strip().splitlines()
deleted = sh("git", "diff", "--diff-filter=D", "--name-only", f"{base}...{source}").strip().splitlines()
added = sh("git", "diff", "--diff-filter=A", "--name-only", f"{base}...{source}").strip().splitlines()
renamed = sh("git", "diff", "--diff-filter=R", "--name-status", f"{base}...{source}").strip().splitlines()

dirs = Counter()
services = set()
routes = set()
mobile = set()
contracts = set()
ui_pages = set()
deploy_touch = []
generated = []

for f in changed:
    parts = f.split("/")
    if parts:
        dirs[parts[0]] += 1
        if len(parts) > 1:
            dirs[f"{parts[0]}/{parts[1]}"] += 1
    if f.startswith("services/"):
        services.add(f.split("/")[1])
    if f.startswith("ui/one-ui-shell/src/app/") and f.endswith("page.tsx"):
        ui_pages.add(f)
    if f.startswith("apps/mobile/"):
        mobile.add("/".join(f.split("/")[:3]))
    if f.startswith("contracts/"):
        contracts.add(f)
    if any(x in f for x in ("deploy/", "helm/", "docker-compose", "values-full-preview", ".generated.")):
        deploy_touch.append(f)
    if "generated" in f or f.endswith(".generated.yaml"):
        generated.append(f)

branch_name = source_ref.split("/")[-1].lower()
signals = []
if "lovable" in branch_name or "ux" in branch_name or "ui" in branch_name:
    signals.append("UX/shell surfacing")
if "mobile" in branch_name or mobile:
    signals.append("mobile parity/surfacing")
if "bff" in branch_name or "experience-bff" in " ".join(services):
    signals.append("BFF/API orchestration")
if "parity" in branch_name:
    signals.append("parity closure")
if "fix" in branch_name or "regression" in branch_name:
    signals.append("regression fix")
if "surfac" in branch_name:
    signals.append("frontend surfacing")
if contracts:
    signals.append("contract/API changes")
if deploy_touch:
    signals.append("deployment/config (review carefully — do not blind absorb)")

msg_blob = " ".join(commits).lower()
for kw, label in [
    ("stub", "stub/no-mock closure"),
    ("route", "routing"),
    ("registry", "registry plane"),
    ("trust", "trust plane"),
    ("login", "login/auth"),
    ("parity", "parity"),
    ("mobile", "mobile"),
    ("lovable", "Lovable fidelity"),
]:
    if kw in msg_blob:
        signals.append(label)

signals = list(dict.fromkeys(signals))
if expected:
    inferred = f"Operator stated: {expected}"
    inferred_kind = "provided"
else:
    inferred = "; ".join(signals) if signals else "General maintenance — review diff manually"
    inferred_kind = "inferred"

risk = "low"
if deleted:
    risk = "high"
elif deploy_touch or generated:
    risk = "medium"
elif len(changed) > 80:
    risk = "medium"

print(json.dumps({
    "unique_commits": commits[:100],
    "unique_commit_count": len(commits),
    "changed_files": changed,
    "changed_count": len(changed),
    "deleted_files": deleted,
    "added_files": added,
    "renamed_files": renamed,
    "directory_impact": dict(dirs.most_common(20)),
    "affected_services": sorted(services),
    "affected_ui_pages": sorted(ui_pages)[:50],
    "affected_mobile": sorted(mobile),
    "affected_contracts": sorted(contracts)[:30],
    "deployment_config_touched": deploy_touch,
    "generated_artifacts_touched": generated,
    "inferred_source_purpose": inferred,
    "inferred_kind": inferred_kind,
    "estimated_risk": risk,
    "source_group": source_group or None,
}, indent=2))
PY

ABSORPTION_INFERRED_PURPOSE="$(python3 -c "import json; print(json.load(open('$INFER_JSON'))['inferred_source_purpose'])")"

bash "$SCRIPT_DIR/forecast-conflicts.sh" "$BASE_INPUT" "$SOURCE_INPUT" "$CONFLICT_MD" "$CONFLICT_JSON" >/dev/null
bash "$SCRIPT_DIR/classify-touched-areas.sh" "$BASE_INPUT" "$SOURCE_INPUT" > /tmp/classify-md.txt 2>&1 || true
cp "${ABSORPTION_REPORT_DIR}/touched-areas.json" "$CLASSIFY_JSON" 2>/dev/null || echo '{}' >"$CLASSIFY_JSON"

# GATE 3-8 heuristic assessments
export INFER_JSON
python3 >"$DECISION_JSON" <<'PY'
import json, os, re, subprocess

repo = os.environ["REPO_PATH"]
base = os.environ["ABSORPTION_BASE_COMMIT"]
source = os.environ["ABSORPTION_SOURCE_COMMIT"]
infer = json.load(open(os.environ["INFER_JSON"]))

def diff_text(path):
    try:
        return subprocess.check_output(
            ["git", "diff", f"{base}...{source}", "--", path],
            cwd=repo, text=True, stderr=subprocess.DEVNULL, timeout=30)
    except Exception:
        return ""

assessments = {
    "product_truth_alignment": [],
    "real_wiring": [],
    "regression_risk": [],
    "duplicate_implementation": [],
    "ux_lovable_fidelity": [],
    "mobile_parity": [],
}

for f in infer["changed_files"]:
    if "social-service" in f:
        assessments["product_truth_alignment"].append(f"WARN: social-service path — use community-service ({f})")
    if "ui/experience/" in f:
        assessments["product_truth_alignment"].append(f"BLOCK: retired ui/experience path touched ({f})")
    if f.startswith("deploy/") or ".generated." in f:
        assessments["product_truth_alignment"].append(f"WARN: generated/deploy artifact — do not blind absorb ({f})")

for f in infer["changed_files"]:
    if not f.endswith((".tsx", ".ts")) or "one-ui-shell" not in f:
        continue
    d = diff_text(f)
    if re.search(r"coming soon|under construction|mockData|fakeResponse|onClick=\{\(\) => \{\}\}", d, re.I):
        assessments["real_wiring"].append(f"REJECT signal in {f}: placeholder/mock/dead handler")
    if re.search(r"useQuery|useMutation|apiClient|api-client", d):
        assessments["real_wiring"].append(f"PASS signal in {f}: API wiring present")

for f in infer["deleted_files"]:
    assessments["regression_risk"].append(f"DELETE: {f} — requires explicit approval")

for f in infer["changed_files"]:
    if re.search(r"copy|duplicate|legacy|old-|v2-", f, re.I):
        assessments["duplicate_implementation"].append(f"Review parallel implementation: {f}")

if infer["affected_ui_pages"]:
    assessments["ux_lovable_fidelity"].append("UX pages touched — Lovable fidelity must strengthen wired product")

if infer["affected_mobile"]:
    assessments["mobile_parity"].append("Mobile paths touched — verify citizen/provider parity")

decisions = []
for f in infer["changed_files"][:200]:
    if f in infer.get("generated_artifacts_touched", []) or ".generated." in f or "reports/full-boot" in f:
        cls, action, risk, reason = "REJECT", "Do not absorb generated artifact", "high", "Generated artifact"
    elif f in infer["deleted_files"]:
        cls, action, risk, reason = "NEEDS_PRODUCT_OWNER_DECISION", "Explicit approval for deletion", "high", "Deletion"
    elif f.startswith("ui/one-ui-shell/"):
        cls, action, risk, reason = "ACCEPT_WITH_ADAPTATION", "Review wiring + no-stub gates", "medium", "UI"
    elif f.startswith("services/experience-bff/"):
        cls, action, risk, reason = "ACCEPT_WITH_ADAPTATION", "BFF + parity verification", "medium", "BFF"
    elif f.startswith("apps/mobile/"):
        cls, action, risk, reason = "ACCEPT_WITH_ADAPTATION", "Mobile parity verification", "medium", "Mobile"
    elif f.startswith("contracts/"):
        cls, action, risk, reason = "ACCEPT_WITH_ADAPTATION", "API contract checks", "medium", "Contracts"
    elif f.startswith("deploy/") or f.startswith("helm/"):
        cls, action, risk, reason = "REJECT", "Do not absorb deploy config", "high", "Deploy config"
    else:
        cls, action, risk, reason = "NEEDS_PRODUCT_OWNER_DECISION", "Manual review", infer["estimated_risk"], "Unclassified"

    decisions.append({
        "area": "/".join(f.split("/")[:2]) if "/" in f else f,
        "files": f,
        "candidate_change": f"Changes in {f}",
        "classification": cls,
        "recommended_action": action,
        "risk": risk,
        "reason": reason,
    })

reject_count = sum(1 for d in decisions if d["classification"] == "REJECT")
if len(infer["changed_files"]) == 0:
    final = "REJECT_BRANCH"
elif reject_count > max(1, len(decisions) // 3):
    final = "NEEDS_PRODUCT_OWNER_DECISION"
else:
    final = "ABSORB_SELECTIVELY"

print(json.dumps({
    "assessments": assessments,
    "decisions": decisions,
    "final_branch_decision": final,
    "verification_plan": [
        f"bash scripts/absorption/run-change-absorption.sh absorb --source {os.environ['ABSORPTION_SOURCE_REF']} --approval-file reports/absorption/approved-plan.json",
        f"bash scripts/absorption/run-change-absorption.sh verify --source {os.environ['ABSORPTION_SOURCE_REF']} --run-gates",
        "bash scripts/pipeline/run-local-quality-gates.sh  # PIPELINE_ONLY from touched-areas.json",
    ],
}, indent=2))
PY

FINAL_DECISION="$(python3 -c "import json; print(json.load(open('$DECISION_JSON'))['final_branch_decision'])")"
INFER_KIND="$(python3 -c "import json; print(json.load(open('$INFER_JSON'))['inferred_kind'])")"

# GATE — Product Value / Improvement + Accepted Change Completion / Enablement
python3 -c "
import json
infer = json.load(open('$INFER_JSON'))
infer['expected_value'] = '''${ABSORPTION_EXPECTED_VALUE}'''.strip() or None
json.dump(infer, open('$INFER_JSON', 'w'), indent=2)
" 2>/dev/null || true

python3 "$SCRIPT_DIR/_product-gates.py" analyze \
  --infer "$INFER_JSON" \
  --decisions "$DECISION_JSON" \
  --out "$GATES_JSON" \
  --md-out "$GATES_MD"

{
  echo "# Change Absorption — Analysis Report"
  echo ""
  echo "- **Generated:** $(absorption_now)"
  echo "- **Mode:** analysis (read-only)"
  echo ""
  cat "$GATES_MD"
  echo ""
  echo "---"
  echo ""
  echo "## Technical analysis detail"
  echo ""
  echo "## 1. Product Truth Branch"
  echo "\`$ABSORPTION_BASE_REF\` @ \`${ABSORPTION_BASE_COMMIT:0:12}\`"
  echo ""
  echo "## 2. Source Branch"
  echo "\`$ABSORPTION_SOURCE_REF\` @ \`${ABSORPTION_SOURCE_COMMIT:0:12}\`"
  echo ""
  echo "## 3. Base mode"
  echo "**$ABSORPTION_BASE_MODE**"
  echo ""
  echo "## 4. Operator expected value"
  if [[ -n "$ABSORPTION_EXPECTED_VALUE" ]]; then
    echo "$ABSORPTION_EXPECTED_VALUE"
  else
    echo "_(not provided)_"
  fi
  echo ""
  echo "## 5. Inferred source branch purpose ($INFER_KIND)"
  echo "$ABSORPTION_INFERRED_PURPOSE"
  [[ -n "$ABSORPTION_SOURCE_GROUP" ]] && echo "" && echo "**Source group:** $ABSORPTION_SOURCE_GROUP"
  echo ""
  echo "## 6. Branch safety result"
  echo "PASS — read-only analysis; no merge/commit/deploy/cluster mutation"
  echo ""
  echo "## 7. Difference discovery"
  echo '```'
  echo "$DIFF_STAT"
  echo '```'
  echo ""
  echo "## 8. Unique commits in source ($(python3 -c "import json; print(json.load(open('$INFER_JSON'))['unique_commit_count'])"))"
  echo '```'
  echo "$UNIQUE_COMMITS" | head -40
  echo '```'
  echo ""
  echo "## 9–16. File / directory impact"
  python3 -c "
import json
d=json.load(open('$INFER_JSON'))
print('**Changed:**', d['changed_count'])
print('**Deleted:**', len(d['deleted_files']))
print('**Added:**', len(d['added_files']))
print('**Services:**', ', '.join(d['affected_services'][:20]) or 'none')
print('**Mobile:**', ', '.join(d['affected_mobile'][:10]) or 'none')
print('**Contracts:**', len(d['affected_contracts']))
print('**Deploy/config touched:**', len(d['deployment_config_touched']))
print('**Generated artifacts in diff:**', len(d['generated_artifacts_touched']))
"
  echo ""
  cat "$CONFLICT_MD"
  echo ""
  echo "## Assessments"
  python3 -c "
import json
a=json.load(open('$DECISION_JSON'))['assessments']
for k,v in a.items():
    print(f'### {k.replace(\"_\",\" \").title()}')
    if v:
        for x in v[:15]: print(f'- {x}')
    else:
        print('- _(no signals)_')
    print()
"
  echo "## 24. Per-file technical classification table"
  echo ""
  echo "| Area | Files | Candidate change | Classification | Recommended action | Risk | Reason |"
  echo "| ---- | ----- | ---------------- | -------------- | ------------------ | ---- | ------ |"
  python3 -c "
import json
for d in json.load(open('$GATES_JSON')).get('decisions_enriched', json.load(open('$DECISION_JSON'))['decisions'])[:80]:
    print(f\"| {d['area']} | \`{d['files']}\` | {d['candidate_change'][:40]} | {d['classification']} | {d['recommended_action'][:40]} | {d['risk']} | {d['reason']} |\")
"
  echo ""
  echo "## 24b. Per-file product value + completion metadata"
  echo ""
  echo "| File | Product value | Acceptability | Modification | Completion needs |"
  echo "| ---- | ------------- | ------------- | ------------ | ---------------- |"
  python3 -c "
import json
for d in json.load(open('$GATES_JSON')).get('decisions_enriched', [])[:80]:
    cn = ', '.join(d.get('completion_needs', []))
    print(f\"| \`{d['files']}\` | {d.get('product_value_judgement','?')} | {d.get('acceptability','')[:40]} | {d.get('modification_required','')[:40]} | {cn[:50]} |\")
"
  echo ""
  echo "## 25. Verification plan"
  python3 -c "import json; [print(f'- {x}') for x in json.load(open('$DECISION_JSON'))['verification_plan']]"
  echo ""
  echo "## 26. Final recommendation"
  echo "**$FINAL_DECISION**"
} >"$MD_OUT"

export INFER_JSON DECISION_JSON CONFLICT_JSON CLASSIFY_JSON GATES_JSON
export JSON_OUT="$JSON_OUT"
export ABSORPTION_BASE_REF ABSORPTION_SOURCE_REF ABSORPTION_BASE_MODE
export ABS_JSON_SOURCE_GROUP="$ABSORPTION_SOURCE_GROUP"
export ABS_JSON_EXPECTED="$ABSORPTION_EXPECTED_VALUE"
export ABS_JSON_INFERRED="$ABSORPTION_INFERRED_PURPOSE"
export ABS_JSON_STARTED="$ABSORPTION_STARTED_AT"
export ABS_JSON_FINAL="$FINAL_DECISION"
python3 <<'PY'
import json, os, pathlib
from datetime import datetime, timezone

decision = json.load(open(os.environ["DECISION_JSON"]))
infer = json.load(open(os.environ["INFER_JSON"]))
gates = json.load(open(os.environ["GATES_JSON"]))
try:
    conflicts = json.load(open(os.environ["CONFLICT_JSON"]))
except Exception:
    conflicts = []
try:
    touched = json.load(open(os.environ["CLASSIFY_JSON"]))
except Exception:
    touched = {}

doc = {
    "mode": "analysis",
    "product_truth_branch": os.environ.get("ABSORPTION_BASE_REF"),
    "source_branch": os.environ.get("ABSORPTION_SOURCE_REF"),
    "base_mode": os.environ.get("ABSORPTION_BASE_MODE"),
    "source_group": os.environ.get("ABS_JSON_SOURCE_GROUP") or None,
    "expected_value": os.environ.get("ABS_JSON_EXPECTED") or None,
    "inferred_source_purpose": os.environ.get("ABS_JSON_INFERRED"),
    "started_at": os.environ.get("ABS_JSON_STARTED"),
    "finished_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "verdict": os.environ.get("ABS_JSON_FINAL"),
    "checks_run": [
        "branch-safety", "difference-discovery", "conflict-forecast",
        "product-truth-heuristics", "product-value-gate", "completion-enablement-gate",
        "decision-table",
    ],
    "decisions": gates.get("decisions_enriched", decision["decisions"]),
    "product_value_gate": gates.get("product_value_gate", {}),
    "completion_gate": gates.get("completion_gate", {}),
    "executive_summary": gates.get("executive_summary", {}),
    "deferred_items": gates.get("deferred_items", []),
    "conflicts_forecast": conflicts,
    "verification": {"plan": decision["verification_plan"]},
    "product_owner_decisions": gates.get("deferred_items", []),
    "final_recommendation": gates.get("executive_summary", {}).get(
        "8_final_product_owner_recommendation", os.environ.get("ABS_JSON_FINAL")
    ),
    "difference": infer,
    "assessments": decision["assessments"],
    "touched_areas": touched,
}
pathlib.Path(os.environ["JSON_OUT"]).write_text(json.dumps(doc, indent=2) + "\n")
PY

absorption_info "Analysis complete"
echo "Report: $MD_OUT"
echo "JSON:   $JSON_OUT"
absorption_print_next_steps analysis
