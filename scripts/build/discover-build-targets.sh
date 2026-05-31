#!/usr/bin/env bash
# Emit build targets from full-boot classification (TSV: id, tool, path, classification).
set -euo pipefail
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"
full_boot_ensure_artifacts

OUT="${1:-$FULL_BOOT_REPORTS/build-targets.tsv}"
mkdir -p "$(dirname "$OUT")"

python3 <<PY
import yaml, pathlib, sys
root = pathlib.Path("$REPO_PATH")
doc = yaml.safe_load((root / "config/full-boot-service-classification.yml").read_text())
skip = {"internal_package", "external_dependency", "doctrine_only_future", "deprecated_retired", "unknown_needs_review"}
rows = []
for e in doc.get("classifications", []):
    c = e.get("classification", "")
    if c in skip:
        continue
    if not e.get("build_required"):
        continue
    rows.append((e["id"], e.get("build_tool", ""), e.get("source_path", ""), c))
rows.sort()
out = pathlib.Path("$OUT")
out.parent.mkdir(parents=True, exist_ok=True)
with out.open("w") as f:
    f.write("id\tbuild_tool\tsource_path\tclassification\n")
    for r in rows:
        f.write("\t".join(str(x) for x in r) + "\n")
print(f"Wrote {len(rows)} build targets to {out}")
PY
