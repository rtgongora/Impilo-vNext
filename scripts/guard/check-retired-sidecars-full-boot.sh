#!/usr/bin/env bash
# Ensure Wave 6 retired sidecars stay out of full-boot required builds.
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"

RETIRED=(
  mushex-finance-console
  mushex-ops-console
  mushex-payer-portal
  ehr
)

FAIL=0
CLS="config/full-boot-service-classification.yml"
BUILD_JSON="reports/full-boot/build-targets.json"

python3 <<'PY' || FAIL=1
import json, sys, yaml
from pathlib import Path

retired = {
    "mushex-finance-console",
    "mushex-ops-console",
    "mushex-payer-portal",
    "ehr",
}
root = Path(".")
cls = yaml.safe_load((root / "config/full-boot-service-classification.yml").read_text())
by_id = {e["id"]: e for e in cls.get("classifications", [])}
errors = []
for sid in sorted(retired):
    e = by_id.get(sid)
    if not e:
        errors.append(f"missing classification entry: {sid}")
        continue
    if e.get("build_required"):
        errors.append(f"{sid}: build_required must be false")
    if e.get("full_boot_classification") != "deprecated_retired":
        errors.append(f"{sid}: expected full_boot_classification=deprecated_retired, got {e.get('full_boot_classification')}")
build_path = root / "reports/full-boot/build-targets.json"
if build_path.exists():
    targets = {t["id"] for t in json.loads(build_path.read_text()).get("targets", [])}
    for sid in retired:
        if sid in targets:
            errors.append(f"{sid}: still in build-targets.json")
else:
    print("GUARD WARN  build-targets.json missing — run scripts/build/discover-build-targets.sh")
pkg = (root / "ui/package.json").read_text()
for ws in retired:
    if f'"{ws}"' in pkg:
        errors.append(f"{ws}: still listed in ui/package.json workspaces")
if errors:
    for err in errors:
        print(f"GUARD FAIL  {err}")
    sys.exit(1)
print("GUARD PASS  Wave 6 retired sidecars excluded from full-boot builds and npm workspaces")
PY

exit "${FAIL:-0}"
