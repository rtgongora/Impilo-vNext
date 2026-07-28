#!/usr/bin/env bash
# Classify changed files into touched areas and recommend VM verification gates.
set -euo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=scripts/absorption/_absorption-common.sh
source "$SCRIPT_DIR/_absorption-common.sh"

BASE_REF="${1:-}"
SOURCE_REF="${2:-}"

if [[ "$BASE_REF" == "-" && -n "$SOURCE_REF" && -f "$SOURCE_REF" ]]; then
  mapfile -t CHANGED < "$SOURCE_REF"
elif [[ -n "$BASE_REF" && -n "$SOURCE_REF" && "$BASE_REF" != "-" ]]; then
  absorption_ensure_repo
  absorption_resolve_context "$BASE_REF" "$SOURCE_REF"
  mapfile -t CHANGED < <(absorption_changed_files)
else
  absorption_die "Usage: classify-touched-areas.sh BASE SOURCE | classify-touched-areas.sh - FILES_LIST"
fi

AREAS_FILE="$(mktemp)"
GATES_FILE="$(mktemp)"
trap 'rm -f "$AREAS_FILE" "$GATES_FILE"' EXIT

: >"$AREAS_FILE"
: >"$GATES_FILE"

classify_file() {
  local f="$1"
  case "$f" in
    ui/one-ui-shell/*) echo frontend >>"$AREAS_FILE" ;;
    services/experience-bff/*) echo bff >>"$AREAS_FILE"; echo backend >>"$AREAS_FILE" ;;
    services/*) echo backend >>"$AREAS_FILE" ;;
    apps/mobile/*) echo mobile >>"$AREAS_FILE" ;;
    contracts/*) echo contracts >>"$AREAS_FILE" ;;
    ui/one-ui-shell/src/lib/routes.ts|ui/one-ui-shell/src/app/*) echo routes >>"$AREAS_FILE"; echo frontend >>"$AREAS_FILE" ;;
    docs/registry/*|config/full-boot*|docs/architecture/SERVICE*) echo service-registry >>"$AREAS_FILE" ;;
    config/parity-allowlist.yml) echo parity-config >>"$AREAS_FILE" ;;
    deploy/*|helm/*|docker-compose*) echo deployment-config >>"$AREAS_FILE" ;;
    docs/*) echo docs >>"$AREAS_FILE" ;;
    *test*|*Test*|tests/*|*.test.ts|*.test.tsx|*.spec.ts) echo tests >>"$AREAS_FILE" ;;
    scripts/guard/*) echo guards >>"$AREAS_FILE"; echo scripts >>"$AREAS_FILE" ;;
    scripts/*) echo scripts >>"$AREAS_FILE" ;;
    *) echo unknown >>"$AREAS_FILE" ;;
  esac
}

for f in "${CHANGED[@]}"; do
  [[ -n "$f" ]] && classify_file "$f"
done

sort -u "$AREAS_FILE" -o "$AREAS_FILE"

has_area() { grep -qx "$1" "$AREAS_FILE" 2>/dev/null; }

has_area frontend && echo "static/frontend|scripts/test/run-static-checks.sh; scripts/test/run-frontend-checks.sh" >>"$GATES_FILE"
has_area bff && echo "backend/bff|scripts/test/run-backend-checks.sh" >>"$GATES_FILE"
has_area backend && echo "backend|scripts/test/run-backend-checks.sh" >>"$GATES_FILE"
has_area mobile && echo "mobile|scripts/guard/check-mobile-parity.sh; scripts/test/run-mobile-checks.sh" >>"$GATES_FILE"
has_area contracts && echo "contracts|scripts/test/run-api-contract-checks.sh" >>"$GATES_FILE"
has_area frontend && echo "parity-web|scripts/guard/check-backend-frontend-parity.sh" >>"$GATES_FILE"
has_area parity-config && echo "parity-config|scripts/guard/check-backend-frontend-parity.sh" >>"$GATES_FILE"
has_area routes && echo "routes|ui/one-ui-shell npm run test:routes; scripts/guard/check-route-inventory.sh" >>"$GATES_FILE"
has_area service-registry && echo "registry|scripts/guard/check-service-inventory.sh" >>"$GATES_FILE"
has_area guards && echo "guards|bash -n scripts/guard/*.sh; scripts/guard/run-change-safety-gates.sh" >>"$GATES_FILE"
if has_area docs && [[ "$(wc -l <"$AREAS_FILE")" -eq 1 ]]; then
  echo "docs-only|Documentation review — no heavy gates by default" >>"$GATES_FILE"
fi
has_area deployment-config && echo "deploy-config|ADVISORY — do not run deploy scripts during intake" >>"$GATES_FILE"

PIPELINE_PHASES=()
has_area frontend && PIPELINE_PHASES+=(static frontend)
has_area routes && PIPELINE_PHASES+=(static)
has_area backend && PIPELINE_PHASES+=(backend)
has_area bff && PIPELINE_PHASES+=(backend)
has_area mobile && PIPELINE_PHASES+=(parity-mobile mobile)
has_area contracts && PIPELINE_PHASES+=(api-contracts)
has_area frontend && PIPELINE_PHASES+=(parity-web)
has_area parity-config && PIPELINE_PHASES+=(parity-web)
PIPELINE_PHASES+=(change-safety security)
PIPELINE_ONLY="$(printf '%s\n' "${PIPELINE_PHASES[@]}" | sort -u | paste -sd, -)"

OUT_JSON="${ABSORPTION_REPORT_DIR:-$REPO_PATH/reports/absorption}/touched-areas.json"
mkdir -p "$(dirname "$OUT_JSON")"

export OUT_JSON PIPELINE_ONLY CHANGED_COUNT="${#CHANGED[@]}"
export AREAS_FILE GATES_FILE

python3 <<'PY'
import json, os, pathlib

areas = [l.strip() for l in open(os.environ["AREAS_FILE"]) if l.strip()]
gates = {}
for line in open(os.environ["GATES_FILE"]):
    line = line.strip()
    if "|" in line:
        k, v = line.split("|", 1)
        gates[k] = v

doc = {
    "areas": {a: True for a in areas},
    "recommended_gates": gates,
    "pipeline_only": os.environ["PIPELINE_ONLY"],
    "changed_file_count": int(os.environ["CHANGED_COUNT"]),
}
pathlib.Path(os.environ["OUT_JSON"]).write_text(json.dumps(doc, indent=2) + "\n")
PY

{
  echo "## Touched areas"
  echo ""
  echo "| Area | Detected |"
  echo "| ---- | -------- |"
  while read -r a; do
    [[ -n "$a" ]] && echo "| $a | yes |"
  done <"$AREAS_FILE"
  echo ""
  echo "## Recommended verification gates"
  echo ""
  echo "| Gate key | Command / script |"
  echo "| -------- | ---------------- |"
  while IFS='|' read -r k v; do
    [[ -n "$k" ]] && echo "| $k | $v |"
  done <"$GATES_FILE"
  echo ""
  echo "**Suggested PIPELINE_ONLY:** \`$PIPELINE_ONLY\`"
  echo ""
  echo "JSON: \`$OUT_JSON\`"
}
