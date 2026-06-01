#!/usr/bin/env bash
# Discover build targets and runtime image strategy targets from classification.
set -euo pipefail
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"
full_boot_ensure_artifacts

REPORTS="${FULL_BOOT_REPORTS:-$REPO_PATH/reports/full-boot}"
mkdir -p "$REPORTS"

python3 <<'PY'
import json, pathlib, yaml

root = pathlib.Path(".")
doc = yaml.safe_load((root / "config/full-boot-service-classification.yml").read_text())
entries = doc.get("classifications", [])
reports = root / "reports/full-boot"

build_targets = []
image_targets = []
non_runtime = []

for e in entries:
    base = {
        "id": e["id"],
        "path": e.get("source_path", ""),
        "component_name": e.get("canonical_name", e["id"]),
        "type": e.get("component_type", ""),
        "plane": e.get("plane"),
        "classification": e.get("full_boot_classification", e.get("classification", "")),
        "build_required": bool(e.get("build_required")),
        "image_required": bool(e.get("image_required", e.get("runtime_image_required"))),
        "image_strategy": e.get("image_strategy", ""),
        "image_strategy_status": e.get("image_strategy_status", ""),
        "reason": e.get("reason", ""),
        "classification_confidence": e.get("classification_confidence", "certain"),
    }
    strat = e.get("image_strategy", "")
    if strat.startswith("not-required"):
        non_runtime.append({**base, "skip_image_build": True})
        continue
    if base["image_required"] or strat in (
        "dockerfile",
        "shared-dockerfile-template",
        "jib",
        "buildpacks",
        "official-upstream-image",
        "official-helm-chart",
        "missing-required-image-strategy",
        "unknown-needs-review",
    ):
        image_targets.append(
            {
                **base,
                "dockerfile_path": e.get("dockerfile_path"),
                "shared_template": e.get("shared_template"),
                "jib_module": e.get("jib_module"),
                "official_image": e.get("official_image"),
                "official_chart": e.get("official_chart"),
                "image_build_command": e.get("image_build_command"),
                "image_name": e.get("image_name", f"impilo/{e['id']}"),
            }
        )
    if base["build_required"]:
        build_targets.append(base)

# TSV legacy output
out_tsv = reports / "build-targets.tsv"
out_tsv.parent.mkdir(parents=True, exist_ok=True)
with out_tsv.open("w") as f:
    f.write("id\tbuild_tool\tsource_path\tclassification\n")
    for b in sorted(build_targets, key=lambda x: x["id"]):
        f.write(f"{b['id']}\t\t{b['path']}\t{b['classification']}\n")

(reports / "build-targets.json").write_text(
    json.dumps({"generated_at": doc["metadata"]["generated_at"], "targets": build_targets}, indent=2)
)
(reports / "image-strategy-targets.json").write_text(
    json.dumps({"generated_at": doc["metadata"]["generated_at"], "targets": image_targets}, indent=2)
)
(reports / "non-runtime-components.json").write_text(
    json.dumps({"generated_at": doc["metadata"]["generated_at"], "components": non_runtime}, indent=2)
)

print(f"build_targets={len(build_targets)} image_targets={len(image_targets)} non_runtime={len(non_runtime)}")
print(f"Wrote {reports}/build-targets.json")
print(f"Wrote {reports}/image-strategy-targets.json")
print(f"Wrote {reports}/non-runtime-components.json")
PY
