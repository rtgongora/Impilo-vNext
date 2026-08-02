#!/usr/bin/env bash
# Workload RBAC subject gate (Checkpoint 4).
#
# Doctrine: "Network position / namespace / internal header is not identity"
# (tshepo-trust-plane-doctrine §2). Binding a Role to the group
# `system:serviceaccounts:<namespace>` grants it to EVERY ServiceAccount in that
# namespace -- including `default`, which 125 of 129 workloads still run as. That is
# namespace membership conferring authority, and it is exactly what the doctrine forbids.
#
# A workload that needs a cluster permission gets its own RoleBinding naming its own
# ServiceAccount. This gate fails if a group subject reappears, and fails if a workload
# ServiceAccount is created with the API token automounted.
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"

FAIL=0

# 1. No namespace-group subjects in any RBAC manifest or chart template.
# grep -rn prefixes each hit with `path:line:`, so a comment test must skip that prefix
# rather than anchoring at ^ -- anchoring matched nothing and the gate flagged its own
# explanatory comments as violations.
group_hits=$(grep -rn "system:serviceaccounts:" \
  --include='*.yaml' --include='*.yml' --include='*.tpl' \
  infra/ deploy/ helm/ services/ 2>/dev/null \
  | grep -vE '^[^:]+:[0-9]+:[[:space:]]*#' || true)

if [[ -n "$group_hits" ]]; then
  guard_fail "RBAC binds to a namespace ServiceAccount group -- namespace membership is not identity:" || true
  echo "$group_hits" | sed 's/^/    /'
  FAIL=1
else
  guard_pass "no RBAC binding grants authority by namespace membership"
fi

# 2. Every workload ServiceAccount the chart renders must refuse the ambient API token.
#    A workload authenticates to Tshepo with an audience-restricted projected token; the
#    API-server token is a different credential it has no reason to hold.
sa_template="deploy/helm/impilo-vnext/templates/serviceaccount.yaml"
if [[ -f "$sa_template" ]]; then
  declared=$(grep -c 'kind: ServiceAccount' "$sa_template" || true)
  refused=$(grep -c 'automountServiceAccountToken: false' "$sa_template" || true)
  if [[ "$declared" -ne "$refused" ]]; then
    guard_fail "serviceaccount.yaml declares $declared ServiceAccount block(s) but only $refused refuse the API token" || true
    FAIL=1
  else
    guard_pass "every rendered workload ServiceAccount refuses the ambient API token ($declared blocks)"
  fi
else
  guard_fail "missing $sa_template" || true
  FAIL=1
fi

exit "$FAIL"
