#!/usr/bin/env bash
# Read-only conflict forecast via git merge-tree (no working tree mutation).
set -euo pipefail

REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=scripts/absorption/_absorption-common.sh
source "$SCRIPT_DIR/_absorption-common.sh"

BASE_REF="${1:-}"
SOURCE_REF="${2:-}"
OUT_MD="${3:-}"
OUT_JSON_FRAGMENT="${4:-}"

[[ -n "$BASE_REF" && -n "$SOURCE_REF" ]] || absorption_die "Usage: forecast-conflicts.sh BASE SOURCE [OUT_MD] [JSON_FRAGMENT]"

absorption_ensure_repo
absorption_resolve_context "$BASE_REF" "$SOURCE_REF"

absorption_info "Conflict forecast (read-only merge-tree)"

MERGE_TREE_OUT="$(mktemp)"
trap 'rm -f "$MERGE_TREE_OUT"' EXIT

if ! git merge-tree "$ABSORPTION_MERGE_BASE" "$ABSORPTION_BASE_COMMIT" "$ABSORPTION_SOURCE_COMMIT" >"$MERGE_TREE_OUT" 2>&1; then
  absorption_warn "git merge-tree returned non-zero (conflicts likely)"
fi

CONFLICT_FILES=()
while IFS= read -r line; do
  if [[ "$line" == *"CONFLICT"* ]] || [[ "$line" == *"changed in both"* ]]; then
    CONFLICT_FILES+=("$line")
  fi
done <"$MERGE_TREE_OUT"

# Parse changed-in-both from merge-tree output (Git 2.38+ format)
CHANGED_BOTH="$(grep -E 'changed in both|CONFLICT' "$MERGE_TREE_OUT" 2>/dev/null | head -80 || true)"
CONFLICT_COUNT=0
if [[ -n "$CHANGED_BOTH" ]]; then
  CONFLICT_COUNT="$(printf '%s\n' "$CHANGED_BOTH" | grep -c . || true)"
fi

RECOMMENDATIONS=()
if [[ "$CONFLICT_COUNT" -gt 0 ]]; then
  while IFS= read -r cf; do
    [[ -z "$cf" ]] && continue
    local_file="$(echo "$cf" | sed -n 's/.* \([^ ]*\)$/\1/p' | head -1)"
    [[ -z "$local_file" ]] && local_file="$cf"
    rec="NEEDS_PRODUCT_OWNER_DECISION"
    case "$local_file" in
      reports/*|deploy/helm/*values*.generated.yaml|ui/one-ui-shell/src/generated/*)
        rec="REJECT_SOURCE_CHANGE"
        ;;
      docs/*)
        rec="COMBINE_MANUALLY"
        ;;
      ui/one-ui-shell/src/app/*)
        rec="ACCEPT_WITH_ADAPTATION"
        ;;
      services/*)
        rec="COMBINE_MANUALLY"
        ;;
      *)
        rec="NEEDS_PRODUCT_OWNER_DECISION"
        ;;
    esac
    RECOMMENDATIONS+=("| \`$local_file\` | content | $rec | Review merge-tree output | medium | Auto-classified |")
  done <<< "$(echo "$CHANGED_BOTH" | sed 's/^/ /' | awk '{print $NF}' | sort -u | head -30)"
fi

{
  echo "## Conflict forecast"
  echo ""
  echo "- **Merge-base:** \`${ABSORPTION_MERGE_BASE:0:12}\`"
  echo "- **Likely conflict signals:** $CONFLICT_COUNT"
  echo ""
  if [[ "$CONFLICT_COUNT" -eq 0 ]]; then
    echo "No merge-tree conflict signals detected (clean forecast — still verify during absorb)."
  else
    echo "### Conflict signals (merge-tree excerpt)"
    echo '```'
    head -60 "$MERGE_TREE_OUT"
    echo '```'
    echo ""
    echo "### Recommended resolution posture"
    echo ""
    echo "| File / area | Type | Recommendation | Action | Risk | Reason |"
    echo "| ----------- | ---- | -------------- | ------ | ---- | ------ |"
    for r in "${RECOMMENDATIONS[@]}"; do
      echo "$r"
    done
  fi
} > "${OUT_MD:-/dev/stdout}"

if [[ -n "$OUT_JSON_FRAGMENT" ]]; then
  python3 - "$OUT_JSON_FRAGMENT" "$CONFLICT_COUNT" "$MERGE_TREE_OUT" <<'PY'
import json, pathlib, sys
out, count, mt_path = sys.argv[1], int(sys.argv[2]), sys.argv[3]
text = pathlib.Path(mt_path).read_text(errors="replace")
items = []
for line in text.splitlines():
    if "CONFLICT" in line or "changed in both" in line:
        items.append({"signal": line.strip()[:500]})
pathlib.Path(out).write_text(json.dumps(items[:50], indent=2))
PY
fi

exit 0
