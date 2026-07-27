#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
FAIL=0

# New service folders on this branch
BASE="$(resolve_base_ref)"
ADDED=$(git diff --diff-filter=A --name-only "$BASE" HEAD -- 'services/' 2>/dev/null \
  | guard_filter '^services/[^/]+/' | sed 's|/.*||' | sort -u || true)

declare -A CANONICAL=(
  [vito-service]="client registry (Vito)"
  [varapi-service]="provider registry (Varapi)"
  [tuso-service]="facility registry (Tuso)"
  [tshepo-authz-service]="trust authz (Tshepo)"
  [butano-service]="shared health record (Butano)"
  [zibo-service]="terminology (Zibo)"
  [ndila-service]="maps/geospatial (Ndila)"
  [dispatch-service]="dispatch (Nhume)"
  [mushex-service]="payments (MusheX)"
  [learning-service]="learning (Fundo)"
  [llm-orchestration-service]="Nompilo orchestration"
)

for dir in $ADDED; do
  module="${dir#services/}"
  module="${module%/}"
  for key in "${!CANONICAL[@]}"; do
    if [[ "$module" != "$key" && "$module" == *"${key%-service}"* ]]; then
      guard_warn "new module $module may duplicate ${CANONICAL[$key]} ($key) — update SERVICE_INVENTORY.md"
    fi
  done
done

# Block obvious duplicate folder names
for pair in "social-service:community-service"; do
  new="${pair%%:*}"
  existing="${pair##*:}"
  if [[ -d "services/$new" ]]; then
    guard_fail "forbidden duplicate service folder services/$new (use $existing)"; FAIL=1
  fi
done

guard_pass "duplicate service scan complete"
exit "$FAIL"
