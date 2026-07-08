#!/usr/bin/env bash
# Per-wave promotion gates before treating a generation as latest public preview.
set -euo pipefail
REPO="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO"
WAVE_N="${1:-}"
FULL_BOOT_MAX_WAVE="${FULL_BOOT_MAX_WAVE:-$WAVE_N}"
export FULL_BOOT_NAMESPACE="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
export FULL_BOOT_MAX_WAVE

if [[ -z "$WAVE_N" ]]; then
  echo "Usage: bash scripts/operator/wave-gates.sh <wave-number>" >&2
  exit 2
fi

GATES_SKIP="${FULLBOOT_SKIP_GATES:-0}"
FAIL=0
REPORT="$REPO/reports/full-boot/wave-${WAVE_N}-gates.json"
mkdir -p "$(dirname "$REPORT")"

note() { echo "$1"; }
gate() {
  local name="$1"
  shift
  if [[ "$GATES_SKIP" == "1" ]]; then
    note "SKIP gate $name (FULLBOOT_SKIP_GATES=1)"
    return 0
  fi
  note "--- gate: $name ---"
  if "$@"; then
    note "PASS: $name"
    return 0
  fi
  note "FAIL: $name"
  FAIL=1
  return 1
}

gate diagnose bash "$REPO/scripts/operator/fullboot.sh" diagnose || true
gate smoke bash "$REPO/scripts/test/run-full-boot-smoke-tests.sh" || true
gate completeness bash "$REPO/scripts/guard/check-full-boot-runtime-completeness.sh" || true

if [[ "$GATES_SKIP" != "1" ]]; then
  note "--- gate: browser / public IP ---"
  PREVIEW_URL="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"
  if curl -sf -m 15 "$PREVIEW_URL/health/version" | python3 -c "
import json,sys
d=json.load(sys.stdin)
assert d.get('environment')=='full-preview', d.get('environment')
print('environment', d.get('environment'), 'commit', (d.get('commit') or '')[:8])
" 2>/dev/null; then
    note "PASS: public /health/version (full-preview)"
  else
    note "FAIL: public /health/version"
    FAIL=1
  fi
fi

bash "$REPO/scripts/operator/wave-ready-matrix.sh" "$WAVE_N" || true
bash "$REPO/scripts/operator/report-preview-generation.sh" || true

python3 - "$REPORT" "$WAVE_N" "$FAIL" <<'PY'
import json, sys
from datetime import datetime, timezone
wave, fail = sys.argv[2], sys.argv[3] == "1"
json.dump({
    "generated_at": datetime.now(timezone.utc).isoformat(),
    "wave": int(wave),
    "passed": not fail,
    "gates": ["diagnose", "smoke", "completeness", "public_health_version"],
}, open(sys.argv[1], "w"), indent=2)
PY

if [[ "$FAIL" -eq 0 ]]; then
  echo "WAVE_GATES_PASS: wave $WAVE_N"
  exit 0
fi
echo "WAVE_GATES_FAIL: wave $WAVE_N (see $REPORT)"
exit 1
