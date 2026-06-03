#!/usr/bin/env bash
# Per-service Ready matrix for full-boot namespace (wave expansion / phase 3).
set -euo pipefail
REPO="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
NS="${IMPILO_FULLBOOT_NS:-impilo-full-preview}"
OUT="${REPO}/reports/full-boot/service-ready-matrix.md"
mkdir -p "$(dirname "$OUT")"

{
  echo "# Full boot service Ready matrix"
  echo ""
  echo "Namespace: \`$NS\`"
  echo "Generated: $(date -Is)"
  echo ""
  echo "| Deployment | Ready | Available | Notes |"
  echo "|------------|-------|-----------|-------|"
  kubectl get deploy -n "$NS" -o json 2>/dev/null | python3 <<'PY' || echo "| (namespace missing) | — | — | — |"
import json, sys
doc = json.load(sys.stdin)
for d in sorted(doc.get("items", []), key=lambda x: x["metadata"]["name"]):
    name = d["metadata"]["name"]
    spec = d["spec"].get("replicas", 1)
    st = d.get("status", {})
    ready = st.get("readyReplicas", 0) or 0
    avail = st.get("availableReplicas", 0) or 0
    note = "OK" if ready >= spec and avail >= spec else "NOT_READY"
    print(f"| {name} | {ready}/{spec} | {avail}/{spec} | {note} |")
PY
} >"$OUT"
echo "Wrote $OUT"
cat "$OUT" | head -40
