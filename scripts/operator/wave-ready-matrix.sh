#!/usr/bin/env bash
# Per-service Ready matrix for full-boot namespace (wave expansion / phase 3).
set -euo pipefail
REPO="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
NS="${IMPILO_FULLBOOT_NS:-impilo-full-preview}"
WAVE_N="${1:-}"
OUT="${REPO}/reports/full-boot/service-ready-matrix.md"
STATUS_JSON="${REPO}/reports/full-boot/wave-status.json"
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

python3 - "$REPO" "$NS" "$WAVE_N" "$STATUS_JSON" <<'PY'
import json, subprocess, yaml
from datetime import datetime, timezone
from pathlib import Path
import sys
repo, ns, wave_s, out = Path(sys.argv[1]), sys.argv[2], sys.argv[3], Path(sys.argv[4])
waves = yaml.safe_load((repo / "config/full-boot-waves.yml").read_text())
try:
    raw = subprocess.check_output(["kubectl", "get", "deploy", "-n", ns, "-o", "json"], text=True)
    deploys = {d["metadata"]["name"]: d for d in json.loads(raw).get("items", [])}
except Exception:
    deploys = {}
rows = []
for w in waves.get("waves", []):
    for sid in w.get("services", []):
        d = deploys.get(sid)
        ready = False
        if d:
            spec = d["spec"].get("replicas", 1) or 1
            st = d.get("status", {})
            ready = (st.get("readyReplicas") or 0) >= spec
        rows.append({"wave": w["id"], "service": sid, "deployed": d is not None, "ready": ready})
wave_filter = int(wave_s) if wave_s.isdigit() else None
if wave_filter is not None:
    rows = [r for r in rows if r["wave"] <= wave_filter]
ready_n = sum(1 for r in rows if r["ready"])
out.write_text(json.dumps({
    "generated_at": datetime.now(timezone.utc).isoformat(),
    "namespace": ns,
    "max_wave": wave_filter,
    "assigned": len(rows),
    "ready": ready_n,
    "services": rows,
}, indent=2))
PY

echo "Wrote $OUT"
echo "Wrote $STATUS_JSON"
cat "$OUT" | head -40
