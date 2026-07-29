#!/usr/bin/env bash
# Validate config/full-boot-waves.yml covers every runtime image service exactly once.
set -euo pipefail
# Script-relative, never a hardcoded checkout: this default sent the guard into
# /opt/impilo/repos/Impilo-vNext and audited that tree instead of the one under review, so a
# worktree run reported on somebody else's working copy and called it a pass.
REPO="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO"
bash scripts/full-boot/generate-artifacts.sh >/dev/null 2>&1 || true
python3 <<'PY'
import sys, yaml
from pathlib import Path
repo = Path(".")
doc = yaml.safe_load((repo / "config/full-boot-service-classification.yml").read_text())
waves = yaml.safe_load((repo / "config/full-boot-waves.yml").read_text())
runtime = set()
for e in doc["classifications"]:
    s = e.get("image_strategy", "")
    if s.startswith("not-required") or s in ("official-helm-chart", "unknown-needs-review"):
        continue
    if s in ("not-required-generated-client", "not-required-mobile-artifact",
             "not-required-internal-package", "not-required-doctrine-only-component"):
        continue
    if e.get("official_image"):
        continue
    if s in ("shared-dockerfile-template", "dockerfile", "jib", "buildpacks"):
        runtime.add(e["id"])
assigned = []
for w in waves.get("waves", []):
    assigned.extend(w.get("services", []))
retired = {
    e["id"]
    for e in doc["classifications"]
    if e.get("full_boot_classification") == "deprecated_retired"
}
missing = sorted(runtime - set(assigned))
extra = sorted(set(assigned) - runtime - retired)
dupes = [x for x in assigned if assigned.count(x) > 1]
if dupes:
    print("FAIL: duplicate wave assignments:", sorted(set(dupes)))
    sys.exit(1)
if missing:
    print("FAIL: waves missing runtime services:", missing[:20], f"... ({len(missing)} total)" if len(missing) > 20 else "")
    sys.exit(1)
if extra:
    print("FAIL: waves contain non-runtime ids:", extra[:20])
    sys.exit(1)
print(f"PASS: full-boot-waves.yml covers {len(runtime)} runtime services in {len(waves.get('waves', []))} waves")
PY
