#!/usr/bin/env bash
# Build container images for services with Dockerfiles; tag preview + preview-<sha>.
set -uo pipefail
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"
full_boot_ensure_artifacts

LOG_DIR="$FULL_BOOT_REPORTS/image-logs"
mkdir -p "$LOG_DIR"
TAG_SHA="$(full_boot_image_tag)"
SUMMARY_JSON="$FULL_BOOT_REPORTS/full-image-build-summary.json"
SUMMARY_MD="$FULL_BOOT_REPORTS/full-image-build-summary.md"

PASS=0
FAIL=0
MISSING=0
REQUIRED_FAIL=0
RESULTS=()

python3 <<'PY' >"$FULL_BOOT_REPORTS/image-targets.tsv"
import yaml, pathlib
root = pathlib.Path(".")
doc = yaml.safe_load((root / "config/full-boot-service-classification.yml").read_text())
for e in doc.get("classifications", []):
    c = e.get("classification", "")
    if c not in ("required_full_boot", "optional_full_boot"):
        continue
    if not e.get("container_required") and c != "required_full_boot":
        continue
    ct = e.get("component_type", "backend_service")
    if ct not in ("backend_service", "frontend_app"):
        continue
    mod = e.get("id", "")
    path = e.get("dockerfile_path") or ""
    print(f"{mod}\t{c}\t{path}")
PY

while IFS=$'\t' read -r id class dfpath; do
  [[ -z "$id" ]] && continue
  log="$LOG_DIR/${id}.log"
  ctx=""
  if [[ -n "$dfpath" && -f "$dfpath" ]]; then
    ctx="$(dirname "$dfpath")"
  elif [[ -f "services/$id/Dockerfile" ]]; then
    ctx="services/$id"
    dfpath="services/$id/Dockerfile"
  elif [[ -f "ui/$id/Dockerfile" ]]; then
    ctx="ui/$id"
    dfpath="ui/$id/Dockerfile"
  else
    echo "MISSING Dockerfile $id" | tee "$log"
    MISSING=$((MISSING + 1))
    RESULTS+=("{\"id\":\"$id\",\"status\":\"missing_dockerfile\",\"classification\":\"$class\"}")
    [[ "$class" == "required_full_boot" ]] && REQUIRED_FAIL=$((REQUIRED_FAIL + 1))
    continue
  fi
  img="impilo/${id}"
  if docker build -t "${img}:preview" -t "${img}:${TAG_SHA}" -f "$dfpath" "$ctx" >"$log" 2>&1; then
    echo "PASS $img" | tee -a "$log"
    PASS=$((PASS + 1))
    RESULTS+=("{\"id\":\"$id\",\"status\":\"pass\",\"image\":\"$img\"}")
  else
    echo "FAIL $img" | tee -a "$log"
    FAIL=$((FAIL + 1))
    RESULTS+=("{\"id\":\"$id\",\"status\":\"fail\",\"image\":\"$img\"}")
    [[ "$class" == "required_full_boot" ]] && REQUIRED_FAIL=$((REQUIRED_FAIL + 1))
  fi
done <"$FULL_BOOT_REPORTS/image-targets.tsv"

{
  echo "{"
  echo "  \"tag_sha\": \"$TAG_SHA\","
  echo "  \"pass\": $PASS,"
  echo "  \"fail\": $FAIL,"
  echo "  \"missing_dockerfile\": $MISSING,"
  echo "  \"required_fail\": $REQUIRED_FAIL"
  echo "}"
} >"$SUMMARY_JSON"

cat >"$SUMMARY_MD" <<EOF
# Full Image Build Summary

- Tags: \`preview\`, \`$TAG_SHA\`
- Pass: **$PASS**
- Fail: **$FAIL**
- Missing Dockerfile: **$MISSING**
- Required failures: **$REQUIRED_FAIL**
EOF

echo "Image summary: pass=$PASS fail=$FAIL missing=$MISSING required_fail=$REQUIRED_FAIL"
[[ $REQUIRED_FAIL -eq 0 ]] || exit 1
exit 0
