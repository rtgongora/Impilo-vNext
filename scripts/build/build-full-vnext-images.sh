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
    --full-estate)
      # Default behaviour: build the full deployable estate (all non not-required strategies).
      REQUIRED_ONLY=0
      shift
      ;;
    --debug-required-spine-only|--required-only)
      # DEBUG/partial mode: required spine only. Not the full vNext estate.
      REQUIRED_ONLY=1
      echo "[estate] WARN --required-only is a DEBUG mode (required spine only), not the full estate."
      echo "This is not the full vNext estate and is not valid for full product testing. All of vNext is vNext." >&2
      shift
      ;;
    --summary-only)
      SUMMARY_ONLY=1
      shift
      ;;
    *)
      echo "Unknown argument: $1"
      echo "Usage: bash scripts/build/build-full-vnext-images.sh [--full-estate] [--debug-required-spine-only] [--summary-only] [--wave N] [--only <service>]..."
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


def _git_commit():
    try:
        out = subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, capture_output=True, text=True)
        return out.stdout.strip() if out.returncode == 0 else ""
    except Exception:
        return ""


def _cache_bust(service_id: str) -> str:
    """Content-addressed cache bust for app Dockerfiles (commit + source fingerprint)."""
    import hashlib

    commit = _git_commit() or "unknown"
    h = hashlib.sha256()
    if service_id == "one-ui-shell":
        scan_roots = [
            root / "ui/one-ui-shell",
            root / "ui/shared-ui",
            root / "contracts",
            root / "registry-templates",
        ]
        for base in scan_roots:
            if not base.exists():
                continue
            if base.is_file():
                h.update(base.read_bytes())
                continue
            for f in sorted(base.rglob("*")):
                if not f.is_file():
                    continue
                rel = str(f.relative_to(root))
                if any(x in rel for x in ("node_modules", ".next-build", ".next/", "coverage/", "e2e/")):
                    continue
                h.update(rel.encode())
                h.update(str(f.stat().st_mtime_ns).encode())
    elif service_id == "experience-bff":
        jar_dir = root / "services/experience-bff/target"
        jars = [p for p in jar_dir.glob("*.jar") if "original" not in p.name] if jar_dir.is_dir() else []
        if jars:
            h.update(sorted(jars)[0].read_bytes())
        else:
            src = root / "services/experience-bff/src"
            if src.is_dir():
                for f in sorted(src.rglob("*")):
                    if f.is_file():
                        h.update(str(f.relative_to(root)).encode())
                        h.update(str(f.stat().st_mtime_ns).encode())
    else:
        return commit
    return f"{commit}:{h.hexdigest()[:16]}"


def _extract_ui_bundle_hash(image_ref: str) -> str:
    import re

    cmd = [
        "docker", "run", "--rm", "--entrypoint", "sh", image_ref, "-c",
        "ls /app/one-ui-shell/.next-build/static/chunks/app/layout-*.js 2>/dev/null | head -1",
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    if result.returncode != 0 or not result.stdout.strip():
        return ""
    match = re.search(r"layout-([a-f0-9]+)\.js", result.stdout)
    return match.group(1) if match else ""


def _ui_sources_changed() -> bool:
    for cmd in (
        ["git", "diff", "--name-only", "HEAD", "--", "ui/one-ui-shell", "ui/shared-ui"],
        ["git", "status", "--porcelain", "--", "ui/one-ui-shell", "ui/shared-ui"],
    ):
        try:
            out = subprocess.run(cmd, cwd=root, capture_output=True, text=True)
            if out.returncode == 0 and out.stdout.strip():
                return True
        except Exception:
            pass
    return False


def _verify_ui_bundle_after_build(image_ref: str, log_path: pathlib.Path):
    bundle_hash = _extract_ui_bundle_hash(image_ref)
    if not bundle_hash:
        append_log(log_path, "FAIL UI bundle verification: no layout-*.js hash found in image\n")
        return False, ""
    bff_url = os.environ.get("NEXT_PUBLIC_BFF_URL", "")
    if not bff_url:
        probe = subprocess.run(
            [
                "docker", "run", "--rm", "--entrypoint", "sh", image_ref, "-c",
                "grep -rl 'localhost:8160' /app/one-ui-shell/.next-build/static/chunks/ 2>/dev/null | wc -l",
            ],
            capture_output=True,
            text=True,
            timeout=120,
        )
        hits = probe.stdout.strip() if probe.returncode == 0 else "?"
        if hits not in ("0", ""):
            append_log(
                log_path,
                f"FAIL UI bundle verification: localhost:8160 baked into {hits} chunk(s); "
                "rebuild with empty NEXT_PUBLIC_BFF_URL for same-origin preview\n",
            )
            return False, bundle_hash
    prev_path = reports / "ui-bundle-hash.txt"
    prev_hash = prev_path.read_text().strip() if prev_path.exists() else ""
    if prev_hash and _ui_sources_changed() and bundle_hash == prev_hash:
        append_log(
            log_path,
            f"FAIL UI bundle verification: hash unchanged ({bundle_hash}) after UI source changes\n",
        )
        return False, bundle_hash
    prev_path.write_text(bundle_hash + "\n")
    meta = {
        "bundle_hash": bundle_hash,
        "source_commit": _git_commit(),
        "built_at": datetime.now(timezone.utc).isoformat(),
    }
    (reports / "ui-bundle-build-meta.json").write_text(json.dumps(meta, indent=2))
    append_log(log_path, f"UI bundle hash verified: layout-{bundle_hash}.js\n")
    return True, bundle_hash


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
        return False, None

    context = docker_context_for(str(dockerfile.relative_to(root)), service_id)
    image = f"impilo/{service_id}"
    source_commit = _git_commit() or "unknown"
    cache_bust = _cache_bust(service_id)
    build_cmd = [
        "docker", "build",
        "-t", f"{image}:preview",
        "-t", f"{image}:{tag}",
        "--build-arg", f"SOURCE_COMMIT={source_commit}",
        "--build-arg", f"CACHE_BUST={cache_bust}",
        "-f", str(dockerfile),
        str(context),
    ]
    if service_id == "one-ui-shell":
        bff_url = os.environ.get("NEXT_PUBLIC_BFF_URL", "")
        gateway_url = os.environ.get("NEXT_PUBLIC_API_GATEWAY_URL", "")
        build_cmd[6:6] = [
            "--build-arg", f"NEXT_PUBLIC_BFF_URL={bff_url}",
            "--build-arg", f"NEXT_PUBLIC_API_GATEWAY_URL={gateway_url}",
        ]
    if os.environ.get("IMPILO_IMAGE_NO_CACHE") == "1":
        build_cmd.insert(2, "--no-cache")
    append_log(
        log_path,
        f"STRATEGY dockerfile\nCOMMAND {' '.join(shlex.quote(c) for c in build_cmd)}\nLOG {log_path}\n",
    )
    ok = run_command(build_cmd, log_path)
    ui_bundle_hash = ""
    if ok and service_id == "one-ui-shell":
        verified, ui_bundle_hash = _verify_ui_bundle_after_build(f"{image}:preview", log_path)
        if not verified:
            ok = False
    append_log(log_path, f"{'PASS' if ok else 'FAIL'} {image} (dockerfile)\n")
    return ok, ui_bundle_hash if service_id == "one-ui-shell" else None


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
ui_bundle_hashes = {}

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
        built_ok, ui_hash = build_dockerfile(service_id, target.get("dockerfile_path"), log_path)
        if ui_hash:
            ui_bundle_hashes[service_id] = ui_hash
        if built_ok:
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
        built_ok, ui_hash = build_dockerfile(service_id, target.get("dockerfile_path"), log_path)
        if ui_hash:
            ui_bundle_hashes[service_id] = ui_hash
        if built_ok:
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

# Runtime image truth: per-service build records. The "target digest set" for a deploy is
# computed from these records (service, source_commit, image_tag, local_docker_image_id,
# registry_digest, build_timestamp). registry_digest is best-effort here (populated after
# push-images-to-local-registry.sh and resolve-image-digests.sh); the truth guard re-resolves.
def _docker_image_id(image_ref):
    try:
        out = subprocess.run(["docker", "image", "inspect", image_ref, "--format", "{{.Id}}"],
                             capture_output=True, text=True)
        return out.stdout.strip() if out.returncode == 0 else ""
    except Exception:
        return ""

def _repo_digest(image_ref):
    try:
        out = subprocess.run(["docker", "image", "inspect", image_ref, "--format", "{{join .RepoDigests \",\"}}"],
                             capture_output=True, text=True)
        return out.stdout.strip() if out.returncode == 0 else ""
    except Exception:
        return ""

source_commit = _git_commit()
build_ts = datetime.now(timezone.utc).isoformat()
build_records = {}
for service_id, status in per_service.items():
    if not (str(status).startswith("pass") or status == "official_ok"):
        continue
    image_ref = f"impilo/{service_id}:preview"
    record = {
        "service": service_id,
        "source_commit": source_commit,
        "image_tag": "preview",
        "image_tag_sha": tag,
        "cache_bust": _cache_bust(service_id) if status != "official_ok" else "",
        "local_docker_image_id": _docker_image_id(image_ref) if status != "official_ok" else "",
        "registry_digest": _repo_digest(image_ref) if status != "official_ok" else "",
        "build_timestamp": build_ts,
        "build_status": status,
    }
    if service_id in ui_bundle_hashes:
        record["ui_bundle_hash"] = ui_bundle_hashes[service_id]
    build_records[service_id] = record

(reports / "full-image-build-records.json").write_text(json.dumps({
    "generated_at": build_ts,
    "source_commit": source_commit,
    "tag_sha": tag,
    "records": build_records,
}, indent=2))

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
    "build_records": build_records,
    "source_commit": source_commit,
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
