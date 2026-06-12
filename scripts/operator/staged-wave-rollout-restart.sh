#!/usr/bin/env bash
# Staged rollout restart for impilo-full-preview — recycle pods wave-by-wave without exceeding pod limits.
set -euo pipefail
REPO="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
NS="${IMPILO_FULLBOOT_NS:-impilo-full-preview}"
MAX_WAVE="${1:-8}"
WAIT_TIMEOUT="${STAGED_ROLLOUT_TIMEOUT:-300}"
POD_CEILING="${STAGED_ROLLOUT_POD_CEILING:-108}"

cd "$REPO"

wait_for_pod_ceiling() {
  local i=0
  while [[ $i -lt 60 ]]; do
    local n
    n="$(kubectl get pods -n "$NS" --no-headers 2>/dev/null | wc -l | tr -d ' ')"
    if [[ "$n" -le "$POD_CEILING" ]]; then
      return 0
    fi
    echo "  waiting for pod count ($n) <= $POD_CEILING ..."
    sleep 10
    i=$((i + 1))
  done
  echo "WARN: pod count still above ceiling after wait" >&2
  return 0
}

wait_wave_ready() {
  local wave="$1"
  local -a svcs
  mapfile -t svcs < <(python3 - "$REPO" "$wave" <<'PY'
import sys, yaml
from pathlib import Path
repo, max_w = Path(sys.argv[1]), int(sys.argv[2])
waves = yaml.safe_load((repo / "config/full-boot-waves.yml").read_text())
ids = []
for w in waves.get("waves", []):
    if w["id"] > max_w:
        break
    ids.extend(w.get("services", []))
for s in ids:
    print(s)
PY
)
  local fail=0
  for svc in "${svcs[@]}"; do
    if ! kubectl get deploy "$svc" -n "$NS" >/dev/null 2>&1; then
      continue
    fi
    if ! kubectl rollout status "deployment/$svc" -n "$NS" --timeout="${WAIT_TIMEOUT}s" 2>/dev/null; then
      echo "WARN: $svc not ready within ${WAIT_TIMEOUT}s" >&2
      fail=1
    fi
  done
  return "$fail"
}

for wave in $(seq 0 "$MAX_WAVE"); do
  echo "=== Staged rollout: wave $wave ==="
  mapfile -t wave_svcs < <(python3 - "$REPO" "$wave" <<'PY'
import sys, yaml
from pathlib import Path
repo, wave_id = Path(sys.argv[1]), int(sys.argv[2])
waves = yaml.safe_load((repo / "config/full-boot-waves.yml").read_text())
for w in waves.get("waves", []):
    if w["id"] == wave_id:
        for s in w.get("services", []):
            print(s)
        break
PY
)
  wait_for_pod_ceiling
  for svc in "${wave_svcs[@]}"; do
    if kubectl get deploy "$svc" -n "$NS" >/dev/null 2>&1; then
      kubectl rollout restart "deployment/$svc" -n "$NS" >/dev/null
      echo "  restarted $svc"
    fi
  done
  echo "--- waiting cumulative waves 0..$wave ready ---"
  wait_wave_ready "$wave" || true
  bash "$REPO/scripts/operator/report-preview-generation.sh" 2>/dev/null | tail -1 || true
  sleep 5
done

echo "STAGED_ROLLOUT_COMPLETE: waves 0..$MAX_WAVE"
