#!/usr/bin/env bash
# resolve-image-digests.sh
#
# After images are pushed to the local registry, resolve each runtime service manifest
# digest and write deploy/helm/impilo-vnext/values-full-preview-digests.generated.yaml.
# Helm renders repo@sha256 so k3s is not left to infer from mutable :preview tags.
#
# Usage: bash scripts/full-boot/resolve-image-digests.sh [--tag preview] [--registry 127.0.0.1:5000]
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"

TAG="${FULL_BOOT_IMAGE_TAG:-preview}"
REGISTRY="${IMPILO_LOCAL_REGISTRY:-127.0.0.1:5000}"
OUT="$REPO_PATH/deploy/helm/impilo-vnext/values-full-preview-digests.generated.yaml"
RECORDS="$REPO_PATH/reports/full-boot/full-image-build-records.json"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag) TAG="${2:-preview}"; shift 2 ;;
    --registry) REGISTRY="${2:-127.0.0.1:5000}"; shift 2 ;;
    -h|--help)
      grep -E '^#( |$)' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

export REPO_PATH TAG REGISTRY OUT RECORDS

python3 <<'PY'
import json
import os
import pathlib
import subprocess
from datetime import datetime, timezone

try:
    import yaml
except ImportError as exc:
    raise SystemExit(f"PyYAML required: {exc}") from exc

root = pathlib.Path(os.environ["REPO_PATH"])
registry = os.environ["REGISTRY"]
tag = os.environ["TAG"]
out_path = pathlib.Path(os.environ["OUT"])
records_path = pathlib.Path(os.environ["RECORDS"])

# Load runtime services (same set as check-runtime-image-truth.sh).
classifications = yaml.safe_load(
    (root / "config/full-boot-service-classification.yml").read_text()
)["classifications"]
runtime_services = [
    e["id"] for e in classifications if e.get("deployment_lane") == "runtime_k8s_microservice"
]
for dedicated in ("experience-bff", "one-ui-shell"):
    if dedicated not in runtime_services:
        runtime_services.append(dedicated)
runtime_services = sorted(set(runtime_services))

# Skip explicit exemptions (infra pins, non-runtime).
exempt = set()
ex_path = root / "config/runtime-image-truth-exemptions.yml"
if ex_path.exists():
    ex_doc = yaml.safe_load(ex_path.read_text()) or {}
    exempt = {item["name"] for item in (ex_doc.get("exemptions") or [])}


def registry_digest(service_id: str) -> str:
    url = f"http://{registry}/v2/impilo/{service_id}/manifests/{tag}"
    result = subprocess.run(
        [
            "curl", "-sS", "-o", "/dev/null", "-D", "-",
            "-H", "Accept: application/vnd.docker.distribution.manifest.v2+json",
            "-H", "Accept: application/vnd.oci.image.index.v1+json",
            url,
        ],
        capture_output=True,
        text=True,
        timeout=20,
    )
    if result.returncode != 0:
        return ""
    for line in result.stdout.splitlines():
        if line.lower().startswith("docker-content-digest:"):
            return line.split(":", 1)[1].strip()
    return ""


full_boot = {}
images = {}
resolved = {}
missing = []

for sid in runtime_services:
    if sid in exempt:
        continue
    digest = registry_digest(sid)
    if not digest:
        missing.append(sid)
        continue
    resolved[sid] = digest
    if sid in ("experience-bff", "one-ui-shell"):
        key = "experienceBff" if sid == "experience-bff" else "oneUiShell"
        images[key] = {"digest": digest}
    else:
        full_boot[sid] = {"image": {"digest": digest}}

values = {
    "# AUTO-GENERATED": "do not edit by hand",
    "generated_at": datetime.now(timezone.utc).isoformat(),
    "source_tag": tag,
    "registry": registry,
    "digest_pin_count": len(resolved),
    "fullBootServices": full_boot,
    "images": images,
}

header = """# AUTO-GENERATED — do not edit by hand.
# Regenerate: bash scripts/full-boot/resolve-image-digests.sh
# Chart-native digest pinning: Helm renders registry/repo@sha256 for each runtime service.
"""
body = yaml.safe_dump(
    {k: v for k, v in values.items() if not str(k).startswith("#")},
    sort_keys=False,
    default_flow_style=False,
)
out_path.write_text(header + body)

# Stamp digests into build records (target digest set for truth guard).
records_doc = {"generated_at": values["generated_at"], "source_commit": "", "tag_sha": tag, "records": {}}
if records_path.exists():
    try:
        records_doc = json.loads(records_path.read_text())
    except Exception:
        pass
records = records_doc.setdefault("records", {})
for sid, digest in resolved.items():
    rec = records.setdefault(sid, {"service": sid})
    rec["registry_digest"] = digest
    rec["digest_pinned"] = True
records_doc["digest_pin_generated_at"] = values["generated_at"]
records_path.parent.mkdir(parents=True, exist_ok=True)
records_path.write_text(json.dumps(records_doc, indent=2))

print(f"Resolved {len(resolved)} digests -> {out_path}")
if missing:
    print(f"WARN: missing registry digest for {len(missing)} service(s): {', '.join(missing[:8])}{'...' if len(missing) > 8 else ''}")
    raise SystemExit(1 if len(resolved) == 0 else 0)
PY
