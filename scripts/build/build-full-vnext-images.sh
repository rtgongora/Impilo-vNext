#!/usr/bin/env bash
# Build runtime images per declared image_strategy (not Dockerfile-only doctrine).
set -uo pipefail
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"
full_boot_ensure_artifacts

LOG_DIR="$FULL_BOOT_REPORTS/image-logs"
mkdir -p "$LOG_DIR"
TAG_SHA="$(full_boot_image_tag)"
SUMMARY_JSON="$FULL_BOOT_REPORTS/full-image-build-summary.json"
SUMMARY_MD="$FULL_BOOT_REPORTS/full-image-build-summary.md"
STRATEGY_JSON="$FULL_BOOT_REPORTS/image-strategy-summary.json"
STRATEGY_MD="$FULL_BOOT_REPORTS/image-strategy-summary.md"

bash scripts/build/discover-build-targets.sh >/dev/null

export TAG_SHA LOG_DIR REPO_PATH
python3 <<'PY'
import json, os, subprocess, pathlib, yaml
from datetime import datetime, timezone

root = pathlib.Path(os.environ.get("REPO_PATH", "."))
log_dir = pathlib.Path(os.environ["LOG_DIR"])
tag = os.environ["TAG_SHA"]
reports = root / "reports/full-boot"

targets = json.loads((reports / "image-strategy-targets.json").read_text())["targets"]
entries = yaml.safe_load((root / "config/full-boot-service-classification.yml").read_text())["classifications"]

def count_strategy(exact=None, prefix=None):
    c = 0
    for e in entries:
        s = e.get("image_strategy", "")
        if exact and s == exact:
            c += 1
        elif prefix and s.startswith(prefix):
            c += 1
    return c

pass_n = fail_n = skip_n = official_ok = strategy_missing = unknown_review = required_fail = blocking = 0
per_service = {}

def is_required(t):
    return t.get("image_required") or t.get("classification") == "required_full_boot"

def build_dockerfile(sid, dfpath, log):
    ctx = root / pathlib.Path(dfpath).parent if dfpath else root / f"services/{sid}"
    if dfpath and (root / dfpath).is_file():
        df = root / dfpath
    elif (root / f"services/{sid}/Dockerfile").is_file():
        df = root / f"services/{sid}/Dockerfile"
        ctx = root / f"services/{sid}"
    elif (root / f"ui/{sid}/Dockerfile").is_file():
        df = root / f"ui/{sid}/Dockerfile"
        ctx = root / f"ui/{sid}"
    else:
        return False
    img = f"impilo/{sid}"
    r = subprocess.run(
        ["docker", "build", "-t", f"{img}:preview", "-t", f"{img}:{tag}", "-f", str(df), str(ctx)],
        capture_output=True, text=True,
    )
    log.write_text((log.read_text() if log.exists() else "") + r.stdout + r.stderr)
    if r.returncode == 0:
        log.write_text(log.read_text() + f"\nPASS {img} (dockerfile)\n")
        return True
    log.write_text(log.read_text() + f"\nFAIL {img} (dockerfile)\n")
    return False

for t in sorted(targets, key=lambda x: x["id"]):
    if not is_required(t):
        continue
    sid = t["id"]
    strat = t.get("image_strategy", "")
    log = log_dir / f"{sid}.log"
    req = is_required(t)

    if strat == "missing-required-image-strategy":
        log.write_text(f"BLOCK missing-required-image-strategy {sid}\n")
        strategy_missing += 1
        required_fail += 1
        blocking += 1
        per_service[sid] = "missing_strategy"
        continue

    if strat == "unknown-needs-review":
        log.write_text(f"ADVISORY unknown-needs-review {sid}\n")
        unknown_review += 1
        skip_n += 1
        per_service[sid] = "unknown"
        continue

    if strat.startswith("not-required") or strat == "buildpacks":
        log.write_text(f"SKIP {sid} ({strat})\n")
        skip_n += 1
        per_service[sid] = "skip"
        continue

    if strat in ("official-upstream-image", "official-helm-chart"):
        official = t.get("official_image") or t.get("official_chart") or ""
        if official:
            log.write_text(f"PASS {sid} ({strat} {official})\n")
            pass_n += 1
            official_ok += 1
            per_service[sid] = "official_ok"
        else:
            log.write_text(f"FAIL {sid} (no official image/chart ref)\n")
            fail_n += 1
            if req:
                required_fail += 1
                blocking += 1
            per_service[sid] = "official_missing"
        continue

    if strat == "dockerfile":
        if build_dockerfile(sid, t.get("dockerfile_path"), log):
            pass_n += 1
            per_service[sid] = "pass"
        else:
            fail_n += 1
            if req:
                required_fail += 1
                blocking += 1
            per_service[sid] = "fail"
        continue

    if strat in ("shared-dockerfile-template", "jib"):
        r = subprocess.run(
            ["bash", "scripts/build/build-runtime-image-from-jar.sh", sid],
            capture_output=True, text=True, cwd=root,
        )
        log.write_text(r.stdout + r.stderr)
        if r.returncode == 0:
            pass_n += 1
            per_service[sid] = "pass_shared_template"
            continue
        if build_dockerfile(sid, t.get("dockerfile_path"), log):
            pass_n += 1
            per_service[sid] = "pass_dockerfile_fallback"
        else:
            fail_n += 1
            if req:
                required_fail += 1
                blocking += 1
            per_service[sid] = "fail"
        continue

    log.write_text(f"SKIP {sid} (strategy={strat})\n")
    skip_n += 1
    per_service[sid] = "skipped"

summary = {
    "generated_at": datetime.now(timezone.utc).isoformat(),
    "tag_sha": tag,
    "doctrine": "runtime_image_strategy_required",
    "total_components": len(entries),
    "runtime_image_required_count": sum(1 for e in entries if e.get("image_required")),
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

md = f"""# Full Image Build Summary

> Doctrine: runtime image strategy required (not Dockerfile-only).

- Tag: `{tag}`
- Total components: **{summary['total_components']}**
- Runtime image required: **{summary['runtime_image_required_count']}**
- Pass: **{pass_n}** | Fail: **{fail_n}** | Skip: **{skip_n}**
- Official validated: **{official_ok}**
- Missing required strategy: **{strategy_missing}**
- Required failures: **{required_fail}**
- Blocking failures: **{blocking}**
"""
(reports / "full-image-build-summary.md").write_text(md)
(reports / "image-strategy-summary.md").write_text("# Image Strategy Summary\n\n" + md.split("\n", 1)[1])

print(f"Image summary: pass={pass_n} fail={fail_n} skip={skip_n} official={official_ok} "
      f"missing_strategy={strategy_missing} required_fail={required_fail} blocking={blocking}")
raise SystemExit(1 if blocking else 0)
PY
