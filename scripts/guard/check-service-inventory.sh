#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
BASE="$(resolve_base_ref)"
FAIL=0

NEW_SERVICES=$(git diff --diff-filter=A --name-only "$BASE"...HEAD -- 'services/' 2>/dev/null \
  | guard_filter '^services/[^/]+/pom.xml$' | sed 's|services/||;s|/pom.xml||' | sort -u || true)

if [[ -z "$NEW_SERVICES" ]]; then
  guard_pass "no new Maven service modules"
  exit 0
fi

if git diff --name-only "$BASE"...HEAD | guard_filter -q 'docs/registry/services-registry.yaml|docs/architecture/SERVICE_INVENTORY.md'; then
  guard_pass "registry/inventory updated for new services"
else
  guard_fail "new service(s) detected but services-registry.yaml / SERVICE_INVENTORY.md not updated"
  echo "$NEW_SERVICES"
  FAIL=1
fi

exit "$FAIL"
