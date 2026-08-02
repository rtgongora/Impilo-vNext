#!/usr/bin/env bash
# OPA policy drift gate (Checkpoint 4).
#
# The deployed policy lives in a ConfigMap built from infra/opa/authz/*.rego. Nothing stops
# someone editing that ConfigMap in place, and a policy edited in the cluster is a policy no
# review ever saw. This gate fails when the deployed content stops matching the repository.
#
# It also refuses to let *_test.rego reach the runtime bundle: test data inside a decision path
# is how a fixture becomes a rule.
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"

NAMESPACE="${IMPILO_NAMESPACE:-impilo-full-preview}"
CONFIGMAP="opa-authz-policy"
POLICY_DIR="infra/opa/authz"
FAIL=0

# Source-side check runs everywhere, cluster or not.
if command -v opa >/dev/null 2>&1; then
  if opa test "$POLICY_DIR" >/dev/null 2>&1; then
    guard_pass "policy corpus passes its own tests ($POLICY_DIR)"
  else
    guard_fail "policy corpus fails its own tests — its verdicts mean nothing" || true
    FAIL=1
  fi
else
  echo "SKIP  opa binary unavailable; corpus tests UNVERIFIED in this run (not a pass)."
fi

if ! kubectl get ns "$NAMESPACE" >/dev/null 2>&1; then
  echo "SKIP  deployed-policy drift: namespace '$NAMESPACE' unreachable."
  echo "      Drift is UNVERIFIED in this run -- this is not a pass."
  exit "$FAIL"
fi

if ! kubectl -n "$NAMESPACE" get configmap "$CONFIGMAP" >/dev/null 2>&1; then
  echo "SKIP  ConfigMap/$CONFIGMAP not deployed in $NAMESPACE; nothing to compare."
  exit "$FAIL"
fi

tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
kubectl -n "$NAMESPACE" get configmap "$CONFIGMAP" -o json \
  | python3 -c '
import json, sys, pathlib
data = json.load(sys.stdin).get("data", {}) or {}
out = pathlib.Path(sys.argv[1])
for name, body in data.items():
    (out / name).write_text(body)
print(len(data))
' "$tmp" > "$tmp/.count"

deployed_count=$(cat "$tmp/.count")
drift=0
for f in "$tmp"/*.rego; do
  [[ -e "$f" ]] || continue
  base=$(basename "$f")
  if [[ "$base" == *_test.rego ]]; then
    guard_fail "deployed bundle contains test policy $base — fixtures must not be loadable rules" || true
    drift=1
    continue
  fi
  src="$POLICY_DIR/$base"
  if [[ ! -f "$src" ]]; then
    guard_fail "deployed policy $base has no counterpart in $POLICY_DIR" || true
    drift=1
    continue
  fi
  if ! diff -q "$src" "$f" >/dev/null; then
    guard_fail "deployed policy $base differs from $src — the cluster was edited out of band" || true
    diff -u "$src" "$f" | head -20 | sed 's/^/    /'
    drift=1
  fi
done

# A source policy missing from the bundle is the more dangerous direction: the rule silently
# stops being evaluated while the file still sits in the repository looking authoritative.
for src in "$POLICY_DIR"/*.rego; do
  base=$(basename "$src")
  [[ "$base" == *_test.rego ]] && continue
  if [[ ! -f "$tmp/$base" ]]; then
    guard_fail "policy $base exists in $POLICY_DIR but is NOT deployed — the rule is not evaluated" || true
    drift=1
  fi
done

if [[ "$drift" -eq 0 ]]; then
  guard_pass "deployed policy matches $POLICY_DIR ($deployed_count file(s), no drift)"
else
  FAIL=1
fi

exit "$FAIL"
