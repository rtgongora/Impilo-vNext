#!/usr/bin/env bash
# Print comma-separated runtime k8s microservice ids (+ experience-bff, one-ui-shell).
set -euo pipefail
REPO="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
python3 - "$REPO" <<'PY'
import sys, yaml
repo = sys.argv[1]
doc = yaml.safe_load(open(f"{repo}/config/full-boot-service-classification.yml"))
ids = [e["id"] for e in doc["classifications"] if e.get("deployment_lane") == "runtime_k8s_microservice"]
for d in ("experience-bff", "one-ui-shell"):
    if d not in ids:
        ids.append(d)
print(",".join(sorted(set(ids))))
PY
