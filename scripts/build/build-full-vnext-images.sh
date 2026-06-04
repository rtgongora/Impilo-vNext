#!/usr/bin/env bash
# Build runtime images per declared image_strategy (not Dockerfile-only doctrine).
set -euo pipefail
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"
full_boot_ensure_artifacts

ONLY_SERVICES=()
REQUIRED_ONLY=0
SUMMARY_ONLY=0
WAVE_MAX=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --only)
      [[ $# -ge 2 ]] || { echo "--only requires a service name"; exit 2; }
      ONLY_SERVICES+=("$2")
      shift 2
      ;;
    --wave)
      [[ $# -ge 2 ]] || { echo "--wave requires a number"; exit 2; }
      WAVE_MAX="$2"
      shift 2
      ;;
    --required-only)
      REQUIRED_ONLY=1
      shift
      ;;
    --summary-only)
      SUMMARY_ONLY=1
      shift
      ;;
    *)
      echo "Unknown argument: $1"
      echo "Usage: bash scripts/build/build-full-vnext-images.sh [--required-only] [--summary-only] [--wave N] [--only <service>]..."
      exit 2
      ;;
  esac
done

if [[ -n "$WAVE_MAX" ]]; then
  IFS=',' read -ra WAVE_IDS <<<"$(bash scripts/full-boot/wave-service-ids.sh "$WAVE_MAX")"
  for sid in "${WAVE_IDS[@]}"; do
    [[ -n "$sid" ]] && ONLY_SERVICES+=("$sid")
  done
fi

LOG_DIR="$FULL_BOOT_REPORTS/image-logs"
mkdir -p "$LOG_DIR"
TAG_SHA="$(full_boot_image_tag)"

bash scripts/build/discover-build-targets.sh >/dev/null

export TAG_SHA LOG_DIR REPO_PATH REQUIRED_ONLY SUMMARY_ONLY
if [[ ${#ONLY_SERVICES[@]} -gt 0 ]]; then
  export ONLY_SERVICES_CSV="$(IFS=,; echo "${ONLY_SERVICES[*]}")"
else
  export ONLY_SERVICES_CSV=""
fi

python3 <<'PY'
import json
import os
import pathlib
import shlex
import subprocess
from datetime import datetime, timezone

import yaml

root = pathlib.Path(os.environ.get("REPO_PATH", "."))
reports = root / "reports/full-boot"
log_dir = pathlib.Path(os.environ["LOG_DIR"])
tag = os.environ["TAG_SHA"]
required_only = os.environ.get("REQUIRED_ONLY") == "1"
summary_only = os.environ.get("SUMMARY_ONLY") == "1"
only_csv = os.environ.get("ONLY_SERVICES_CSV", "").strip()
only_services = [s.strip() for s in only_csv.split(",") if s.strip()] if only_csv else []

targets = json.loads((reports / "image-strategy-targets.json").read_text())["targets"]
entries = yaml.safe_load((root / "config/full-boot-service-classification.yml").read_text())["classifications"]


def is_required(target):
    return bool(target.get("image_required") or target.get("classification") == "required_full_boot")


def count_strategy(exact=None, prefix=None):
    total = 0
    for entry in entries:
        strategy = entry.get("image_strategy", "")
        if exact and strategy == exact:
            total += 1
        elif prefix and strategy.startswith(prefix):
            total += 1
    return total


def docker_context_for(dockerfile_path: str, service_id: str) -> pathlib.Path:
    df = root / dockerfile_path
    if service_id == "one-ui-shell" or "COPY ui/" in df.read_text() or "COPY contracts" in df.read_text():
        return root
    if dockerfile_path.startswith("services/"):
        return root / f"services/{service_id}"
    if dockerfile_path.startswith("ui/"):
        return root
    return df.parent


def append_log(log_path: pathlib.Path, message: str):
    existing = log_path.read_text(errors="ignore") if log_path.exists() else ""
    log_path.write_text(existing + message)


def run_command(command, log_path: pathlib.Path, cwd: pathlib.Path = root):
    result = subprocess.run(command, cwd=cwd, capture_output=True, text=True)
    append_log(log_path, result.stdout + result.stderr)
    return result.returncode == 0


def build_dockerfile(service_id: str, dockerfile_path: str | None, log_path: pathlib.Path):
    dockerfile = None
    if dockerfile_path and (root / dockerfile_path).is_file():
        dockerfile = root / dockerfile_path
    elif (root / f"services/{service_id}/Dockerfile").is_file():
        dockerfile = root / f"services/{service_id}/Dockerfile"
    elif (root / f"ui/{service_id}/Dockerfile").is_file():
        dockerfile = root / f"ui/{service_id}/Dockerfile"
    if dockerfile is None:
        append_log(log_path, f"FAIL impilo/{service_id} (Dockerfile not found)\n")
        return False

    context = docker_context_for(str(dockerfile.relative_to(root)), service_id)
    image = f"impilo/{service_id}"
    append_log(
        log_path,
        f"STRATEGY dockerfile\nCOMMAND docker build -f {dockerfile} {context}\nLOG {log_path}\n",
    )
    ok = run_command(
        ["docker", "build", "-t", f"{image}:preview", "-t", f"{image}:{tag}", "-f", str(dockerfile), str(context)],
        log_path,
    )
    append_log(log_path, f"{'PASS' if ok else 'FAIL'} {image} (dockerfile)\n")
    return ok


selected = []
available_ids = {target["id"] for target in targets}
if only_services:
    missing = [service for service in only_services if service not in available_ids]
    if missing:
        print(f"ERROR: target(s) not found in image-strategy-targets.json: {', '.join(missing)}")
        raise SystemExit(2)
    requested = set(only_services)
    selected = [target for target in targets if target["id"] in requested]
elif required_only:
    selected = [target for target in targets if is_required(target)]
else:
    selected = [
        target
        for target in targets
        if not str(target.get("image_strategy", "")).startswith("not-required")
    ]

pass_n = fail_n = skip_n = official_ok = strategy_missing = unknown_review = required_fail = blocking = 0
per_service = {}

for target in sorted(selected, key=lambda item: item["id"]):
    service_id = target["id"]
    strategy = target.get("image_strategy", "")
    log_path = log_dir / f"{service_id}.log"
    log_path.write_text("")
    req = is_required(target)

    print(f"[{service_id}] strategy={strategy} required={req} log={log_path}")

    if summary_only:
        append_log(log_path, f"SUMMARY_ONLY {service_id} strategy={strategy}\n")
        per_service[service_id] = "summary_only"
        skip_n += 1
        continue

    if strategy == "missing-required-image-strategy":
        append_log(log_path, f"BLOCK missing-required-image-strategy {service_id}\n")
        strategy_missing += 1
        required_fail += 1
        blocking += 1
        per_service[service_id] = "missing_strategy"
        continue

    if strategy == "unknown-needs-review":
        append_log(log_path, f"ADVISORY unknown-needs-review {service_id}\n")
        unknown_review += 1
        skip_n += 1
        per_service[service_id] = "unknown"
        continue

    if strategy.startswith("not-required") or strategy == "buildpacks":
        append_log(log_path, f"SKIP {service_id} ({strategy})\n")
        skip_n += 1
        per_service[service_id] = "skip"
        continue

    if strategy in ("official-upstream-image", "official-helm-chart"):
        official_ref = target.get("official_image") or target.get("official_chart") or ""
        if official_ref:
            append_log(log_path, f"STRATEGY {strategy}\nPASS {service_id} ({strategy} {official_ref})\n")
            pass_n += 1
            official_ok += 1
            per_service[service_id] = "official_ok"
        else:
            append_log(log_path, f"FAIL {service_id} (no official image/chart ref)\n")
            fail_n += 1
            if req:
                required_fail += 1
                blocking += 1
            per_service[service_id] = "official_missing"
        continue

    if strategy in ("shared-dockerfile-template", "jib"):
        append_log(log_path, f"STRATEGY {strategy}\nCOMMAND bash scripts/build/build-runtime-image-from-jar.sh {service_id}\n")
        ok = run_command(["bash", "scripts/build/build-runtime-image-from-jar.sh", service_id], log_path)
        if ok:
            pass_n += 1
            per_service[service_id] = "pass_shared_template"
            continue
        if build_dockerfile(service_id, target.get("dockerfile_path"), log_path):
            pass_n += 1
            per_service[service_id] = "pass_dockerfile_fallback"
        else:
            fail_n += 1
            if req:
                required_fail += 1
                blocking += 1
            per_service[service_id] = "fail"
        continue

    if strategy == "dockerfile":
        if build_dockerfile(service_id, target.get("dockerfile_path"), log_path):
            pass_n += 1
            per_service[service_id] = "pass"
        else:
            fail_n += 1
            if req:
                required_fail += 1
                blocking += 1
            per_service[service_id] = "fail"
        continue

    append_log(log_path, f"SKIP {service_id} (strategy={strategy})\n")
    skip_n += 1
    per_service[service_id] = "skipped"

summary = {
    "generated_at": datetime.now(timezone.utc).isoformat(),
    "tag_sha": tag,
    "doctrine": "runtime_image_strategy_required",
    "mode": "summary_only" if summary_only else ("required_only" if required_only and not only_services else "only" if only_services else "all_non_not_required"),
    "only_services": only_services,
    "selected_target_count": len(selected),
    "total_components": len(entries),
    "runtime_image_required_count": sum(1 for entry in entries if entry.get("image_required")),
    "dockerfile_count": count_strategy(exact="dockerfile"),
    "shared_template_count": count_strategy(exact="shared-dockerfile-template"),
    "jib_count": count_strategy(exact="jib"),
    "buildpacks_count": count_strategy(exact="buildpacks"),
    "official_image_count": count_strategy(exact="official-upstream-image"),
    "official_chart_count": count_strategy(exact="official-helm-chart"),
    "not_required_count": count_strategy(prefix="not-required"),
    "unknown_needs_review_count": count_strategy(exact="unknown-needs-review"),
    "missing_required_image_strategy_count": count_strategy(exact="missing-required-image-strategy"),
    "image_build_pass_count": pass_n,
    "image_build_fail_count": fail_n,
    "blocking_failure_count": blocking,
    "advisory_count": skip_n + unknown_review,
    "pass": pass_n,
    "fail": fail_n,
    "skip": skip_n,
    "official_validated": official_ok,
    "missing_required_image_strategy": strategy_missing,
    "required_fail": required_fail,
    "per_service": per_service,
}

for name in ("full-image-build-summary.json", "image-strategy-summary.json"):
    (reports / name).write_text(json.dumps(summary, indent=2))

markdown = f"""# Full Image Build Summary

> Doctrine: runtime image strategy required (not Dockerfile-only).

- Tag: `{tag}`
- Mode: **{summary['mode']}**
- Selected targets: **{len(selected)}**
- Runtime image required (catalog): **{summary['runtime_image_required_count']}**
- Pass: **{pass_n}** | Fail: **{fail_n}** | Skip: **{skip_n}**
- Official validated: **{official_ok}**
- Missing required strategy: **{strategy_missing}**
- Required failures: **{required_fail}**
- Blocking failures: **{blocking}**
"""
(reports / "full-image-build-summary.md").write_text(markdown)
(reports / "image-strategy-summary.md").write_text("# Image Strategy Summary\n\n" + markdown.split("\n", 1)[1])

print(
    f"Image summary: pass={pass_n} fail={fail_n} skip={skip_n} official={official_ok} "
    f"missing_strategy={strategy_missing} required_fail={required_fail} blocking={blocking}"
)
raise SystemExit(1 if blocking else 0)
PY
