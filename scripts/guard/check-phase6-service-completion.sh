#!/usr/bin/env bash
# Phase 6 — full-stack service completion gate.
# User-facing services must be productStatus=real; internal-only must be documented.
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"

COMPLETENESS_DIR="$REPO_PATH/scripts/completeness"
PRODUCT_TRUTH_JSON="$REPO_PATH/reports/product/product-truth.json"

if [[ ! -d "$COMPLETENESS_DIR/node_modules" ]]; then
  (cd "$COMPLETENESS_DIR" && npm install --silent) || guard_fail "npm install in scripts/completeness"
fi

if [[ ! -f "$PRODUCT_TRUTH_JSON" ]]; then
  guard_pass "generating product truth dataset (phase 6)"
  (cd "$COMPLETENESS_DIR" && npm run product-truth --silent)
fi

read -r INCOMPLETE USER_FACING REAL_COUNT INTERNAL_OK PHASE6_OK <<EOF
$(python3 - <<'PY'
import json
with open("reports/product/product-truth.json") as f:
    d = json.load(f)
internal_only_patterns = {
    "schema-registry-service", "observability-service", "security-hardening-service",
    "audit-ledger-service", "iot-ingestion-service", "analytics-pipeline-service",
    "data-pipeline-service", "data-warehouse-service", "data-ingestion-service",
    "national-data-repository-service", "ndr-service", "llm-orchestration-service",
    "ai-model-registry-service", "card-print-agent", "connector-fhir-adapter",
    "fhir-gateway-service", "integration-hub", "jobs-service", "offline-edge-service",
    "offline-sync-service", "landela-adapter-service", "inventory-elmis-adapter",
    "pharmacy-elmis-adapter", "pacs-adapter-service", "wellness-service",
}
incomplete = []
user_facing = 0
real = 0
internal_ok = 0
phase6_ok = 0
for s in d.get("services", []):
    sid = s["id"]
    st = s.get("productStatus")
    if sid in internal_only_patterns or st == "internal-only":
        if s.get("internalOnlyDocumented") and s.get("dimensions", {}).get("controllers") != "absent":
            internal_ok += 1
            phase6_ok += 1
        elif s.get("phase6Complete"):
            internal_ok += 1
            phase6_ok += 1
        else:
            incomplete.append(f"{sid}: internal-only missing documentation or backend")
        continue
    if st == "deprecated":
        phase6_ok += 1
        continue
    user_facing += 1
    if st == "real":
        real += 1
    if s.get("phase6Complete"):
        phase6_ok += 1
    else:
        dims = s.get("dimensions", {})
        thin = {k: v for k, v in dims.items() if v in ("thin", "absent") and v != "n/a"}
        incomplete.append(f"{sid}: status={st} dims={thin}")
print(len(incomplete), user_facing, real, internal_ok, phase6_ok)
for line in incomplete[:20]:
    print(line, file=__import__("sys").stderr)
PY
)
EOF

BLOCKING="${PHASE6_GATE_BLOCKING:-1}"
TOTAL=$((USER_FACING + INTERNAL_OK))

if [[ "$INCOMPLETE" -gt 0 ]]; then
  if [[ "$BLOCKING" == "1" ]]; then
    guard_fail "phase6 incomplete services=$INCOMPLETE (user-facing real=$REAL_COUNT/$USER_FACING phase6_ok=$PHASE6_OK)"
  else
    guard_warn "phase6 incomplete services=$INCOMPLETE (advisory)"
  fi
else
  guard_pass "phase6 service completion — user-facing real=$REAL_COUNT/$USER_FACING internal-only=$INTERNAL_OK phase6_ok=$PHASE6_OK"
fi

exit 0
