#!/usr/bin/env bash
# A5 — programme_id / department_id reference classification guard.
#
# Not every column called programme_id is a reference to the programme registry. The three
# kinds look identical in a grep and must be treated completely differently:
#
#   CANONICAL   UUID with a foreign key to wgv_programme or tuso.facility_department.
#               Already correct; the database enforces it.
#   AUDIT       A record of what a request actually presented (policy_decision_log).
#               MUST NOT be validated or rewritten — an audit row that says a caller sent
#               an unresolvable programme is the evidence, and "fixing" it destroys it.
#   REFERENCE   Free-text naming something in another service's registry, with nothing
#               checking it resolves. This is the A5 surface.
#
# The guard's job is to stop the REFERENCE set growing silently. Every free-text
# programme_id / department_id column must be listed in the register with a disposition,
# so a new one is a deliberate decision rather than an accident nobody sees.
#
# Register: docs/architecture/REFERENCE_ID_A5_REGISTER.md
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"

REGISTER="docs/architecture/REFERENCE_ID_A5_REGISTER.md"

if [[ ! -f "$REGISTER" ]]; then
  guard_fail "missing $REGISTER"
  exit 1
fi

# Free-text (non-UUID) programme/department columns, as "service:column".
mapfile -t FREE_TEXT < <(
  for dir in services/*/src/main/resources/db/migration; do
    [[ -d "$dir" ]] || continue
    svc=$(printf '%s' "$dir" | sed 's|services/||;s|/src.*||')
    # `|| true`: grep exits 1 for a service with no such column, and under `set -o pipefail`
    # that aborts the whole loop at the first one — which made this guard report zero
    # columns and pass. A guard that finds nothing must be treated as broken, hence the
    # emptiness check below as well.
    { grep -rhoE "[a-z_]*(programme_id|department_id)[[:space:]]+(VARCHAR|TEXT|CHAR)" "$dir" 2>/dev/null \
      | awk -v s="$svc" '{print s ":" $1}'; } || true
  done | sort -u
)

if [[ ${#FREE_TEXT[@]} -eq 0 ]]; then
  guard_fail "found no free-text programme/department columns anywhere — the scan is broken, not the schema"
  exit 1
fi

MISSING=()
for entry in "${FREE_TEXT[@]}"; do
  svc="${entry%%:*}"
  col="${entry##*:}"
  # A register row must name both the service and the column, on the same line.
  if ! grep -q "$svc" "$REGISTER" || ! grep "$svc" "$REGISTER" | grep -q "$col"; then
    MISSING+=("$entry")
  fi
done

FAILED=0
if [[ ${#MISSING[@]} -gt 0 ]]; then
  echo "GUARD FAIL  free-text programme/department columns with no entry in $REGISTER:"
  for m in "${MISSING[@]}"; do echo "              - $m"; done
  echo "            Each needs a disposition. An unresolvable reference is remediation work,"
  echo "            never something to guess a canonical value for or quietly drop."
  FAILED=1
else
  guard_pass "all ${#FREE_TEXT[@]} free-text programme/department columns are classified in the A5 register"
fi

exit "$FAILED"
