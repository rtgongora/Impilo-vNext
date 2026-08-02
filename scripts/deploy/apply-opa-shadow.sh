#!/usr/bin/env bash
# Deploy the OPA shadow evaluator into a preview namespace, additively.
#
# Checkpoint 4. This adds a NEW workload and touches no existing one. In particular it never runs
# `helm upgrade`: 14 services in impilo-full-preview run image digests the committed
# values-full-preview-digests.generated.yaml does not name, so a release upgrade would silently
# revert them (see docs/security/trust-audit/checkpoint-4/OPA_SHADOW.md). Objects are rendered and
# applied individually instead.
#
# The policy ConfigMap is built from infra/opa/authz/*.rego directly. There is deliberately no
# copy of the corpus inside the chart: a policy that exists in two places is a policy that drifts.
#
# Usage: scripts/deploy/apply-opa-shadow.sh [--namespace NS] [--dry-run]
set -euo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "$0")/../.." && pwd)}"
cd "$REPO_PATH"

NAMESPACE="impilo-full-preview"
DRY_RUN=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --namespace) NAMESPACE="$2"; shift 2 ;;
    --dry-run)   DRY_RUN="--dry-run=server"; shift ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

POLICY_DIR="infra/opa/authz"
CONFIGMAP="opa-authz-policy"
CHART="deploy/helm/impilo-vnext"

# The runtime must only ever load policy that passes its own tests. `opa test` here is not a
# convenience -- a corpus that fails its tests is a corpus whose verdicts mean nothing, and the
# shadow comparison would report the failure as PolicyEngine divergence.
echo "==> opa test $POLICY_DIR"
if ! command -v opa >/dev/null 2>&1; then
  echo "error: opa binary not found; refusing to deploy unverified policy" >&2
  exit 1
fi
opa test "$POLICY_DIR"

# Only the policy itself is shipped. *_test.rego stays out of the runtime bundle: test data in a
# decision path is a way for a fixture to become a rule.
mapfile -t POLICY_FILES < <(find "$POLICY_DIR" -name '*.rego' ! -name '*_test.rego' | sort)
if [[ ${#POLICY_FILES[@]} -eq 0 ]]; then
  echo "error: no policy files found in $POLICY_DIR -- refusing to deploy an empty policy" >&2
  exit 1
fi
echo "==> policy files: ${POLICY_FILES[*]}"

CHECKSUM=$(cat "${POLICY_FILES[@]}" | sha256sum | cut -c1-16)
echo "==> policy checksum: $CHECKSUM"

FROM_FILE_ARGS=()
for f in "${POLICY_FILES[@]}"; do
  FROM_FILE_ARGS+=(--from-file="$(basename "$f")=$f")
done

echo "==> applying ConfigMap/$CONFIGMAP"
kubectl create configmap "$CONFIGMAP" -n "$NAMESPACE" "${FROM_FILE_ARGS[@]}" \
  --dry-run=client -o yaml | kubectl apply -n "$NAMESPACE" $DRY_RUN -f -

echo "==> rendering OPA workload objects"
RENDERED=$(mktemp); trap 'rm -f "$RENDERED"' EXIT
helm template impilo-full-preview "$CHART" -n "$NAMESPACE" \
  -f "$CHART/values-full-preview.yaml" \
  -f "$CHART/values-full-preview-runtime.generated.yaml" \
  -f "$CHART/values-full-preview-bff-env.generated.yaml" \
  --set opa.enabled=true \
  --set opa.policyChecksum="$CHECKSUM" \
  > "$RENDERED"

# Extract ONLY the OPA objects. Applying the whole render would reconcile every workload in the
# namespace back to the chart's pinned digests -- the exact revert this script exists to avoid.
OPA_ONLY=$(mktemp); trap 'rm -f "$RENDERED" "$OPA_ONLY"' EXIT
python3 - "$RENDERED" "$OPA_ONLY" <<'PY'
import sys, yaml
src, dst = sys.argv[1], sys.argv[2]
keep = []
for doc in yaml.safe_load_all(open(src)):
    if not doc:
        continue
    name = doc.get("metadata", {}).get("name")
    if name == "opa":
        keep.append(doc)
if not keep:
    sys.exit("error: rendered no OPA objects -- refusing to proceed")
kinds = sorted(d["kind"] for d in keep)
print(f"    extracted {len(keep)} OPA object(s): {kinds}", file=sys.stderr)
yaml.safe_dump_all(keep, open(dst, "w"), sort_keys=False)
PY

kubectl apply -n "$NAMESPACE" $DRY_RUN -f "$OPA_ONLY"

if [[ -n "$DRY_RUN" ]]; then
  echo "==> dry run complete; nothing was changed"
  exit 0
fi

echo "==> waiting for rollout"
kubectl -n "$NAMESPACE" rollout status deploy/opa --timeout=120s

echo "==> proving the decision endpoint answers"
kubectl -n "$NAMESPACE" run opa-probe-$RANDOM --rm -i --restart=Never \
  --image=mirror.gcr.io/curlimages/curl:8.11.1 --quiet -- \
  -sS -X POST http://opa:8181/v1/data/impilo/authz/decision \
  -H 'Content-Type: application/json' \
  -d '{"input":{"actor_id":"probe","purpose":"TREATMENT","assurance_loa":3}}'
echo
echo "==> OPA shadow deployed. tshepo-authz still decides; set TSHEPO_AUTHZ_OPA_MODE=SHADOW to compare."
