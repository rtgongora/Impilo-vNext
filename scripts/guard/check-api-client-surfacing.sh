#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
source "$(dirname "$0")/_parity-common.sh"
cd "$REPO_PATH"
parity_ensure_full_catalog
BASE="$(resolve_base_ref)"
BLOCKING=0
ADVISORY=0

NEW_PAGES=$(git diff --diff-filter=A --name-only "$BASE"...HEAD -- 'ui/one-ui-shell/src/app/' 2>/dev/null \
  | guard_filter 'page\.tsx$' || true)

while IFS= read -r page; do
  [[ -z "$page" || ! -f "$page" ]] && continue
  parity_is_allowlisted "$page" && continue
  if ! grep -qE 'use[A-Z]|api-client|/lib/|fetch|useQuery' "$page" 2>/dev/null; then
    guard_fail "new page without API client/hook: $page"
    BLOCKING=1
  fi
done <<< "$NEW_PAGES"

NEW_HOOKS=$(git diff --diff-filter=A --name-only "$BASE"...HEAD -- \
  'ui/one-ui-shell/src/hooks/' 'ui/one-ui-shell/src/lib/' 2>/dev/null \
  | guard_filter '^ui/one-ui-shell/src/(hooks|lib)/.*\.ts$' || true)

while IFS= read -r f; do
  [[ -z "$f" ]] && continue
  base="$(basename "$f" .ts)"
  base="${base%.tsx}"
  [[ "$base" == use* ]] || continue
  if ! git grep -l "$base" -- 'ui/one-ui-shell/src/app' 'ui/one-ui-shell/src/features' 'ui/one-ui-shell/src/components' \
    >/dev/null 2>&1; then
    guard_warn "advisory: new hook $f not referenced in app/features yet"
    ADVISORY=1
  fi
done <<< "$NEW_HOOKS"

NEW_BFF=$(git diff --diff-filter=A --name-only "$BASE"...HEAD -- \
  'services/experience-bff/src/main/java' 2>/dev/null | guard_filter 'Controller\.java$' || true)
if [[ -n "$NEW_BFF" ]]; then
  SAME_COMMIT_UI=0
  if git diff --name-only "$BASE"...HEAD | guard_filter -q 'ui/one-ui-shell/src/|docs/frontend/BACKEND_CAPABILITY|docs/architecture/FRONTEND_BACKEND'; then
    SAME_COMMIT_UI=1
  fi
  ON_DISK_SURFACED=1
  if echo "$NEW_BFF" | grep -q 'HealthOsLauncherController'; then
    git grep -q '/internal/v1/launcher/apps' -- 'ui/one-ui-shell/src/' 'apps/mobile/' \
      || ON_DISK_SURFACED=0
  fi
  if echo "$NEW_BFF" | grep -q 'CitizenMonitoringDevicesController'; then
    git grep -q '/internal/v1/mobile/citizen/monitoring' -- 'ui/one-ui-shell/src/' 'apps/mobile/' \
      || ON_DISK_SURFACED=0
  fi
  if [[ "$SAME_COMMIT_UI" -eq 0 && "$ON_DISK_SURFACED" -eq 0 ]]; then
    guard_fail "new BFF controller without frontend surface/docs update"
    echo "$NEW_BFF"
    BLOCKING=1
  fi
fi

if [[ "$BLOCKING" -ne 0 ]]; then
  exit 1
fi
guard_pass "frontend API client surfacing check"
exit 0
