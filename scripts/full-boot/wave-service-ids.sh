#!/usr/bin/env bash
# Print comma-separated service ids for cumulative waves 0..MAX (inclusive).
set -euo pipefail
REPO="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
MAX="${1:-0}"
python3 - "$REPO" "$MAX" <<'PY'
import sys, yaml
from pathlib import Path
repo, max_w = Path(sys.argv[1]), int(sys.argv[2])
waves = yaml.safe_load((repo / "config/full-boot-waves.yml").read_text())
ids = []
for w in waves.get("waves", []):
    if w["id"] > max_w:
        break
    ids.extend(w.get("services", []))
print(",".join(ids))
PY
