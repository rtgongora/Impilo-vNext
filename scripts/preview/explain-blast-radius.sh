#!/usr/bin/env bash
# Dry-run: explain blast radius — which services rebuild, which tests run, full boot required?
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"
source scripts/preview/_preview-common.sh

BASE_REF=""
FILES_CSV=""

usage() {
  cat <<EOF
Usage: $0 [--base REF] [--files path1,path2]

Shows which services would be rebuilt, which pipeline phases would run,
whether full boot is required, and why. Does not build or deploy.

Writes: reports/audits/blast-radius-explain-<timestamp>.md
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base) BASE_REF="${2:-}"; shift 2 ;;
    --files) FILES_CSV="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1"; usage; exit 2 ;;
  esac
done

preview_ensure_audit_dir
BASE="$(preview_resolve_base_ref "$BASE_REF")"

RESOLVER_ARGS=(--base "$BASE")
if [[ -n "$FILES_CSV" ]]; then
  RESOLVER_ARGS+=(--files "$FILES_CSV")
fi

BLAST_JSON="$(preview_run_blast_resolver "${RESOLVER_ARGS[@]}")"
if [[ ! -f "$BLAST_JSON" ]]; then
  echo "FAIL: blast radius resolver did not produce output" >&2
  exit 1
fi

TS="$(date -u +%Y%m%dT%H%M%SZ)"
EXPLAIN_MD="$PREVIEW_AUDIT_DIR/blast-radius-explain-${TS}.md"

python3 - "$BLAST_JSON" "$EXPLAIN_MD" "$BASE" <<'PY'
import json, sys, pathlib
from datetime import datetime, timezone

blast_path, out_path, base = sys.argv[1], sys.argv[2], sys.argv[3]
d = json.load(open(blast_path))

def yn(v):
    return "yes" if v else "no"

lines = [
    "# Blast Radius Explanation",
    "",
    f"- **Generated:** {datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}",
    f"- **Base ref:** `{base}`",
    f"- **HEAD:** `{d.get('short_sha', '?')}`",
    f"- **Changed files:** {d.get('changed_file_count', 0)}",
    "",
    "## Decision",
    "",
    f"| Question | Answer |",
    f"| -------- | ------ |",
    f"| Change class | **{d.get('change_class', '?')}** ({', '.join(d.get('change_classes', []))}) |",
    f"| Full boot required? | **{yn(d.get('full_boot_required'))}** |",
    f"| Targeted deploy allowed? | **{yn(d.get('targeted_deploy_allowed'))}** |",
    "",
    "## Services",
    "",
    f"- **Direct:** {', '.join(d.get('direct_services', [])) or 'none'}",
    f"- **Expanded:** {', '.join(d.get('expanded_services', [])) or 'none'}",
    f"- **Images to build:** {', '.join(d.get('images_to_build', [])) or 'none'}",
    "",
    "## Pipeline phases",
    "",
    f"`PIPELINE_ONLY={d.get('pipeline_only', '')}`",
    "",
    "Plus mandatory guardrails: `security`, `change-safety` (always).",
    "",
]

if d.get("refusal_reasons"):
    lines += ["## Refusal reasons (targeted deploy)", ""]
    for r in d["refusal_reasons"]:
        lines.append(f"- {r}")
    lines.append("")

if d.get("rationale"):
    lines += ["## Rationale", ""]
    for r in d["rationale"]:
        lines.append(f"- {r}")
    lines.append("")

changed = d.get("changed_files") or []
if changed:
    lines += ["## Changed files (sample)", ""]
    for f in changed[:30]:
        lines.append(f"- `{f}`")
    if len(changed) > 30:
        lines.append(f"- ... and {len(changed) - 30} more")
    lines.append("")

if d.get("full_boot_required"):
    lines += [
        "## Recommended command",
        "",
        "```bash",
        "bash scripts/preview/full-boot.sh",
        "# Type: AUTHORIZE FULL BOOT PREVIEW DEPLOY",
        "```",
    ]
elif d.get("targeted_deploy_allowed"):
    lines += [
        "## Recommended command",
        "",
        "```bash",
        "bash scripts/preview/targeted-deploy.sh --dry-run",
        "bash scripts/preview/targeted-deploy.sh --execute",
        "# Type: AUTHORIZE TARGETED PREVIEW DEPLOY",
        "```",
    ]
else:
    lines += ["## Recommended command", "", "Resolve blockers above before deploy.", ""]

lines += ["", f"JSON: `{blast_path}`", ""]
pathlib.Path(out_path).write_text("\n".join(lines))
print(out_path)
PY

echo ""
echo "=== Blast Radius Summary ==="
python3 -c "
import json
d=json.load(open('$BLAST_JSON'))
print('Change class:', d.get('change_class'), '(' + '+'.join(d.get('change_classes',[])) + ')')
print('Full boot required:', 'YES' if d.get('full_boot_required') else 'NO')
print('Targeted deploy allowed:', 'YES' if d.get('targeted_deploy_allowed') else 'NO')
print('Images to build:', ', '.join(d.get('images_to_build',[])) or 'none')
print('Pipeline phases:', d.get('pipeline_only',''))
if d.get('refusal_reasons'):
    print('Refusal reasons:')
    for r in d['refusal_reasons']:
        print(' -', r)
"

echo ""
echo "Report: $EXPLAIN_MD"
echo "JSON:   $BLAST_JSON"
echo ""
echo "PASS: explain complete"
exit 0
