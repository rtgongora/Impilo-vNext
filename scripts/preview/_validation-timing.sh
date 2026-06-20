#!/usr/bin/env bash
# Emit ISO timestamps for validation timing capture.
# Usage: source scripts/preview/_validation-timing.sh && timing_mark "phase-name"
VALIDATION_TIMING_LOG="${VALIDATION_TIMING_LOG:-/tmp/impilo-validation-timing.log}"
timing_mark() {
  echo "[$(date -Iseconds)] $1" | tee -a "$VALIDATION_TIMING_LOG"
}
timing_elapsed_since() {
  local start="$1"
  local end
  end="$(date -Iseconds)"
  python3 - "$start" "$end" <<'PY'
import sys
from datetime import datetime
a, b = sys.argv[1], sys.argv[2]
fmt = "%Y-%m-%dT%H:%M:%S%z"
def parse(s):
    if s.endswith("Z"):
        s = s[:-1] + "+00:00"
    elif len(s) > 6 and s[-3] != ":" and (s[-5] in "+-"):
        s = s[:-2] + ":" + s[-2:]
    return datetime.fromisoformat(s)
try:
    da, db = parse(a), parse(b)
    print(int((db - da).total_seconds()))
except Exception:
    print("n/a")
PY
}
