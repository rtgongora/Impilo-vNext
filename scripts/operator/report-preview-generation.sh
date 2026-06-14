#!/usr/bin/env bash
# Report the active public preview generation and assert a single public stack.
#
# Highest-Validated-Stack-Wins: the public IP must be served by exactly one stack —
# the latest validated generation (today: the full stack in impilo-full-preview).
# Lower-count generations (e.g. the legacy 4-service impilo-preview slice) may keep
# running as a rollback fallback but MUST NOT serve public traffic (no ingress).
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"

FULL_NS="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
SLICE_NS="${SLICE_NAMESPACE:-impilo-preview}"
OUT_DIR="reports/full-boot"
mkdir -p "$OUT_DIR"
JSON="$OUT_DIR/preview-generation.json"
MD="$OUT_DIR/preview-generation.md"

ready_count() {
  kubectl get deploy -n "$1" -o json 2>/dev/null | python3 -c "
import json,sys
try:
    d=json.load(sys.stdin)
except Exception:
    print('0 0'); sys.exit(0)
items=d.get('items',[])
ready=sum(1 for x in items if (x.get('status',{}).get('readyReplicas') or 0)>=(x['spec'].get('replicas',1) or 1))
print(f'{ready} {len(items)}')
" 2>/dev/null || echo "0 0"
}

read -r FULL_READY FULL_TOTAL <<<"$(ready_count "$FULL_NS")"
read -r SLICE_READY SLICE_TOTAL <<<"$(ready_count "$SLICE_NS")"

# Public ingress inventory: standard Ingress + Traefik IngressRoute (k3s preview uses IngressRoute).
PUBLIC_INGRESSES="$(kubectl get ingress -A --no-headers 2>/dev/null | awk '{print $1"/"$2}' | sort || true)"
PUBLIC_INGRESSROUTES="$(kubectl get ingressroute -A --no-headers 2>/dev/null | awk '{print $1"/"$2}' | sort || true)"
PUBLIC_INGRESS_ALL="$(printf '%s\n%s\n' "$PUBLIC_INGRESSES" "$PUBLIC_INGRESSROUTES" | grep -v '^$' | sort -u)"
PUBLIC_INGRESS_COUNT="$(printf '%s\n' "$PUBLIC_INGRESS_ALL" | grep -c . || true)"
FULL_HAS_INGRESS="no"; SLICE_HAS_INGRESS="no"
printf '%s\n' "$PUBLIC_INGRESS_ALL" | grep -q "^$FULL_NS/" && FULL_HAS_INGRESS="yes"
printf '%s\n' "$PUBLIC_INGRESS_ALL" | grep -q "^$SLICE_NS/" && SLICE_HAS_INGRESS="yes"

# Single-public-stack invariant: the full stack owns the ingress and the slice does not.
SINGLE_PUBLIC_STACK="yes"
REASONS=()
if [[ "$FULL_HAS_INGRESS" != "yes" ]]; then
  SINGLE_PUBLIC_STACK="no"; REASONS+=("full stack ($FULL_NS) has no public ingress")
fi
if [[ "$SLICE_HAS_INGRESS" == "yes" ]]; then
  SINGLE_PUBLIC_STACK="no"; REASONS+=("lower-count slice ($SLICE_NS) is still serving public traffic")
fi

COMMIT="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
NOW="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
REASON_STR="$(IFS='; '; echo "${REASONS[*]:-ok}")"

# Registry coverage from classification + optional non-runtime report.
read -r RUNTIME_DEPLOYABLE RUNTIME_K8S ENABLED_GEN MAX_WAVE NON_RUNTIME_TOTAL NON_RUNTIME_PASS <<<"$(python3 <<'PY'
import json, yaml
from pathlib import Path
root = Path(".")
cls = yaml.safe_load((root / "config/full-boot-service-classification.yml").read_text())
runtime_deployable = wave_runtime = k8s = 0
for e in cls["classifications"]:
    s = e.get("image_strategy", "")
    lane = e.get("deployment_lane", "")
    if lane == "runtime_k8s_microservice":
        k8s += 1
    if s in ("shared-dockerfile-template", "dockerfile", "jib", "buildpacks") and not e.get("official_image"):
        if not s.startswith("not-required"):
            wave_runtime += 1
    if lane.startswith("runtime_"):
        runtime_deployable += 1
gen_path = root / "deploy/helm/impilo-vnext/values-full-preview-runtime.generated.yaml"
enabled = 0
max_wave = ""
if gen_path.exists():
    import re
    text = gen_path.read_text()
    enabled = len(re.findall(r"enabled: true", text))
    m = re.search(r"max_wave: (\d+)", text) or re.search(r"waves 0–(\d+)", text)
    if m:
        max_wave = m.group(1)
nr_total = nr_pass = 0
nr_path = root / "reports/full-boot/non-runtime-lane-report.json"
if nr_path.exists():
    nr = json.loads(nr_path.read_text())
    nr_total = nr.get("total_non_runtime", 0)
    nr_pass = nr.get("pass", 0)
print(runtime_deployable, k8s, enabled, max_wave or "0", nr_total, nr_pass)
PY
)"

GENERATION="${FULL_READY}/${FULL_TOTAL} deployments ready (namespace $FULL_NS); helm enabled ${ENABLED_GEN}/${RUNTIME_K8S} microservices (wave≤${MAX_WAVE})"
COVERAGE="runtime ${ENABLED_GEN}/${RUNTIME_K8S} k8s microservices | registry runtime lanes ${RUNTIME_DEPLOYABLE} | non-runtime validate ${NON_RUNTIME_PASS}/${NON_RUNTIME_TOTAL}"

python3 - "$JSON" <<PY
import json,sys
json.dump({
  "generated_at": "$NOW",
  "commit": "$COMMIT",
  "public_generation": "$GENERATION",
  "coverage_summary": "$COVERAGE",
  "runtime": {
    "deployments_ready": $FULL_READY,
    "deployments_total": $FULL_TOTAL,
    "k8s_microservice_catalog": $RUNTIME_K8S,
    "helm_enabled_microservices": $ENABLED_GEN,
    "max_wave": int("${MAX_WAVE:-0}" or 0),
    "wave_runtime_catalog": $RUNTIME_DEPLOYABLE,
  },
  "non_runtime": {
    "lane_total": $NON_RUNTIME_TOTAL,
    "lane_pass": $NON_RUNTIME_PASS,
    "run_not_applicable": True,
  },
  "full_stack": {"namespace": "$FULL_NS", "ready": $FULL_READY, "total": $FULL_TOTAL, "has_public_ingress": "$FULL_HAS_INGRESS" == "yes"},
  "fallback_slice": {"namespace": "$SLICE_NS", "ready": $SLICE_READY, "total": $SLICE_TOTAL, "has_public_ingress": "$SLICE_HAS_INGRESS" == "yes"},
  "public_ingress_count": $PUBLIC_INGRESS_COUNT,
  "single_public_stack": "$SINGLE_PUBLIC_STACK" == "yes",
  "reason": "$REASON_STR",
}, open(sys.argv[1],"w"), indent=2)
PY

{
  echo "# Public Preview Generation"
  echo ""
  echo "- Generated: $NOW"
  echo "- Commit: \`$COMMIT\`"
  echo "- Active public generation: **$GENERATION**"
  echo "- Coverage: **$COVERAGE**"
  echo "- Full stack public ingress: $FULL_HAS_INGRESS"
  echo "- Fallback slice ($SLICE_NS): $SLICE_READY/$SLICE_TOTAL ready, public ingress: $SLICE_HAS_INGRESS"
  echo "- Public ingress objects: $PUBLIC_INGRESS_COUNT"
  echo "- Single public stack (Highest-Validated-Stack-Wins): **$SINGLE_PUBLIC_STACK** ($REASON_STR)"
} > "$MD"

echo "PREVIEW_GENERATION: $GENERATION"
echo "COVERAGE: $COVERAGE"
echo "SINGLE_PUBLIC_STACK: $SINGLE_PUBLIC_STACK ($REASON_STR)"
echo "Report: $JSON"
[[ "$SINGLE_PUBLIC_STACK" == "yes" ]]
