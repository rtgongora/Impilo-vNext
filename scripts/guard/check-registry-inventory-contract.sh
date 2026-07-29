#!/usr/bin/env bash
# Every registry service must appear in classification with a deployment_lane.
set -euo pipefail
# Script-relative, never a hardcoded checkout: this default sent the guard into
# /opt/impilo/repos/Impilo-vNext and audited that tree instead of the one under review, so a
# worktree run reported on somebody else's working copy and called it a pass.
REPO="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO"
bash scripts/full-boot/generate-artifacts.sh >/dev/null
python3 <<'PY'
import json, sys, yaml
from pathlib import Path

repo = Path(".")
reg = yaml.safe_load((repo / "docs/registry/services-registry.yaml").read_text())
cls = yaml.safe_load((repo / "config/full-boot-service-classification.yml").read_text())
inv_path = repo / "reports/full-boot/registry-inventory-contract.json"
if not inv_path.exists():
    print("FAIL: missing registry-inventory-contract.json — run scripts/full-boot/generate-artifacts.sh")
    sys.exit(1)
inv = json.loads(inv_path.read_text())
if inv.get("registry_services_missing_from_classification"):
    print("FAIL: registry services missing from classification:", inv["registry_services_missing_from_classification"][:10])
    sys.exit(1)
if inv.get("unclassified_registry_gaps", 0) > 0:
    print("FAIL: unclassified registry gaps:", inv["unclassified_registry_gaps"])
    sys.exit(1)
lanes = inv.get("by_deployment_lane", {})
print(
    f"PASS: registry inventory contract — {inv.get('classification_total')} classifications, "
    f"registry services {inv.get('registry_service_count')}, "
    f"runtime_k8s={lanes.get('runtime_k8s_microservice', 0)} build_validate={lanes.get('build_validate_only', 0)}"
)
PY
