#!/usr/bin/env bash
# Workload identity registry coverage gate (Checkpoint 4).
#
# A registry that silently misses a workload is worse than no registry: it reads as
# "every workload is accounted for" while a workload with no identity plan runs
# unlisted. This gate fails when the live namespace holds a workload the registry
# does not name, when the registry names a workload that no longer exists, or when
# the registry carries secret material.
#
# It is skipped (exit 0, loudly) when no cluster is reachable, so it never blocks a
# source-only CI run -- but it must never pass silently in that case.
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"

NAMESPACE="${IMPILO_NAMESPACE:-impilo-full-preview}"
REGISTRY="docs/security/trust-audit/checkpoint-4/workload-identity-registry.yaml"
FAIL=0

if [[ ! -f "$REGISTRY" ]]; then
  guard_fail "workload identity registry missing: $REGISTRY" || true
  exit 1
fi

if ! kubectl get ns "$NAMESPACE" >/dev/null 2>&1; then
  echo "SKIP  workload identity registry coverage: namespace '$NAMESPACE' unreachable."
  echo "      Coverage is UNVERIFIED in this run -- this is not a pass."
  exit 0
fi

live=$(mktemp); registered=$(mktemp)
trap 'rm -f "$live" "$registered"' EXIT

for kind in deploy sts cronjob job; do
  kubectl -n "$NAMESPACE" get "$kind" -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' 2>/dev/null || true
done | grep -v '^$' | sort -u > "$live"

python3 - "$REGISTRY" > "$registered" <<'PY'
import sys, yaml
doc = yaml.safe_load(open(sys.argv[1])) or {}
for row in doc.get("workloads", []) or []:
    print(row["workload"])
PY
sort -u -o "$registered" "$registered"

missing=$(comm -23 "$live" "$registered" || true)
if [[ -n "$missing" ]]; then
  guard_fail "live workloads with no registry row:" || true
  echo "$missing" | sed 's/^/    /'
  FAIL=1
else
  guard_pass "every live workload in $NAMESPACE has a registry row ($(wc -l < "$live") workloads)"
fi

stale=$(comm -13 "$live" "$registered" || true)
if [[ -n "$stale" ]]; then
  guard_fail "registry rows for workloads that no longer exist:" || true
  echo "$stale" | sed 's/^/    /'
  FAIL=1
else
  guard_pass "no stale registry rows"
fi

# The registry records credential *references*, never credential material.
if grep -qE '[A-Za-z0-9+/]{28,}={0,2}|-----BEGIN' "$REGISTRY"; then
  guard_fail "registry contains what looks like secret material" || true
  FAIL=1
else
  guard_pass "registry carries no secret material"
fi

exit "$FAIL"
