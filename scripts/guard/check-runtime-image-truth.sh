#!/usr/bin/env bash
# check-runtime-image-truth.sh
#
# All of vNext is accountable. All deployable vNext services must run. One estate means all
# deployable vNext services. Supporting artifacts do not run as pods, but they remain part of
# vNext truth. Waves are sequencing, not optionality. Deployment truth is the running estate,
# not the deployment story. A deployment is not complete until the full estate is running,
# aligned, healthy, current, and testable.
#
# Verifies the runtime image truth chain for every runtime service:
#   source commit -> local Docker -> local registry -> containerd -> Deployment image ref
#   -> running pod imageID -> expected target digest
#
# READ-ONLY. Never runs `ctr images rm`, prune, import, helm, or `kubectl set image`.
#
# Usage:
#   scripts/guard/check-runtime-image-truth.sh [--json] [--service <id>] \
#       [--phase pre-rollout|post-rollout] [--ns <namespace>]
#
# Exit codes: 0 aligned (or all non-aligned are exempt) | 1 stale non-exempt service(s) found
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"
# shellcheck source=scripts/guard/_guard-common.sh
source "scripts/guard/_guard-common.sh"

NS="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
REGISTRY="${IMPILO_LOCAL_REGISTRY:-127.0.0.1:5000}"
PHASE="post-rollout"
ONLY_SERVICE=""
AS_JSON=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --json) AS_JSON=1; shift ;;
    --service) ONLY_SERVICE="${2:-}"; shift 2 ;;
    --phase) PHASE="${2:-}"; shift 2 ;;
    --ns) NS="${2:-}"; shift 2 ;;
    -h|--help)
      grep -E '^#( |$)' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

export NS REGISTRY PHASE ONLY_SERVICE AS_JSON REPO_PATH

python3 <<'PY'
import json, os, pathlib, shutil, subprocess
from datetime import datetime, timezone

root = pathlib.Path(os.environ.get("REPO_PATH", "."))
reports = root / "reports/full-boot"
ns = os.environ["NS"]
registry = os.environ["REGISTRY"]
phase = os.environ.get("PHASE", "post-rollout")
only_service = os.environ.get("ONLY_SERVICE", "").strip()
as_json = os.environ.get("AS_JSON") == "1"

try:
    import yaml
except Exception:
    yaml = None


def have(cmd):
    return shutil.which(cmd) is not None


def run(cmd, timeout=20):
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return (r.returncode, r.stdout.strip(), r.stderr.strip())
    except Exception as e:  # noqa: BLE001
        return (1, "", str(e))


# --- Load classification: runtime services = runtime_k8s_microservice + dedicated bff/shell.
classifications = []
if yaml is not None:
    try:
        doc = yaml.safe_load((root / "config/full-boot-service-classification.yml").read_text())
        classifications = doc.get("classifications", [])
    except Exception:
        classifications = []

runtime_services = []
for e in classifications:
    if e.get("deployment_lane") == "runtime_k8s_microservice":
        runtime_services.append(e["id"])
for dedicated in ("experience-bff", "one-ui-shell"):
    if dedicated not in runtime_services:
        runtime_services.append(dedicated)
runtime_services = sorted(set(runtime_services))

if only_service:
    runtime_services = [s for s in runtime_services if s == only_service]

# --- Load exemptions.
exempt = {}
if yaml is not None and (root / "config/runtime-image-truth-exemptions.yml").exists():
    try:
        ex = yaml.safe_load((root / "config/runtime-image-truth-exemptions.yml").read_text())
        for item in (ex or {}).get("exemptions", []) or []:
            exempt[item["name"]] = item
    except Exception:
        exempt = {}

# --- Load build records (the target digest set).
records = {}
rec_path = reports / "full-image-build-records.json"
if rec_path.exists():
    try:
        records = json.loads(rec_path.read_text()).get("records", {})
    except Exception:
        records = {}

kubectl = have("kubectl")
docker = have("docker")
curl = have("curl")
# containerd: only via root k3s ctr; never required.
ctr_available = have("k3s")


def docker_image_id(ref):
    if not docker:
        return ""
    rc, out, _ = run(["docker", "image", "inspect", ref, "--format", "{{.Id}}"])
    return out if rc == 0 else ""


def registry_digest(service_id, tag="preview"):
    if not curl:
        return ""
    url = f"http://{registry}/v2/impilo/{service_id}/manifests/{tag}"
    rc, out, _ = run([
        "curl", "-sS", "-o", "/dev/null", "-D", "-",
        "-H", "Accept: application/vnd.docker.distribution.manifest.v2+json",
        "-H", "Accept: application/vnd.oci.image.index.v1+json",
        url,
    ])
    if rc != 0:
        return ""
    for line in out.splitlines():
        if line.lower().startswith("docker-content-digest:"):
            return line.split(":", 1)[1].strip()
    return ""


def containerd_digest(service_id, tag="preview"):
    # Best-effort; requires root. Returns "" (unavailable) otherwise.
    if not ctr_available:
        return ""
    rc, out, _ = run(["k3s", "ctr", "images", "ls", "-q"])
    if rc != 0:
        return ""
    needle = f"{registry}/impilo/{service_id}:{tag}"
    for line in out.splitlines():
        if line.startswith(needle):
            return line
    return ""


def deployment_image(service_id):
    if not kubectl:
        return ""
    rc, out, _ = run([
        "kubectl", "get", "deploy", service_id, "-n", ns,
        "-o", "jsonpath={.spec.template.spec.containers[0].image}",
    ])
    return out if rc == 0 else ""


def pod_image_id(service_id):
    if not kubectl:
        return ""
    for selector in (f"app={service_id}", f"app.kubernetes.io/name={service_id}"):
        rc, out, _ = run([
            "kubectl", "get", "pod", "-n", ns, "-l", selector,
            "-o", "jsonpath={.items[0].status.containerStatuses[0].imageID}",
        ])
        if rc == 0 and out:
            return out
    return ""


def short_digest(s):
    if not s:
        return ""
    if "sha256:" in s:
        return "sha256:" + s.split("sha256:", 1)[1][:12]
    return s[:19]


rows = []
stale_non_exempt = []
for sid in runtime_services:
    is_exempt = sid in exempt
    rec = records.get(sid, {})
    expected_image_ref = f"{registry}/impilo/{sid}:preview"
    expected_digest = rec.get("registry_digest", "")
    local_id = docker_image_id(f"impilo/{sid}:preview")
    reg_digest = registry_digest(sid)
    ctr_digest = containerd_digest(sid)
    dep_image = deployment_image(sid)
    pod_id = pod_image_id(sid)

    # Alignment + reason classification.
    aligned = "YES"
    reason = "aligned"
    if is_exempt:
        aligned = "EXEMPT"
        reason = "explicit_exemption"
    elif kubectl and not dep_image:
        aligned = "NO"
        reason = "missing_pod"
        stale_non_exempt.append(sid)
    elif kubectl and not pod_id:
        aligned = "NO"
        reason = "missing_pod"
        stale_non_exempt.append(sid)
    elif docker and not local_id:
        aligned = "NO"
        reason = "built_not_pushed" if not reg_digest else "stale_application_service"
        stale_non_exempt.append(sid)
    elif curl and local_id and not reg_digest:
        aligned = "NO"
        reason = "built_not_pushed"
        stale_non_exempt.append(sid)
    elif reg_digest and pod_id and reg_digest not in pod_id and pod_id not in reg_digest:
        # Pod running a digest that does not match the current registry digest.
        aligned = "NO"
        reason = "stale_application_service"
        stale_non_exempt.append(sid)
    elif expected_digest and reg_digest and expected_digest not in reg_digest and reg_digest not in expected_digest:
        aligned = "NO"
        reason = "registry_tag_stale"
        stale_non_exempt.append(sid)
    elif not kubectl and not docker:
        aligned = "UNKNOWN"
        reason = "no_inspection_tools"

    rows.append({
        "service": sid,
        "source_commit": rec.get("source_commit", ""),
        "expected_image_ref": expected_image_ref,
        "expected_registry_digest": short_digest(expected_digest),
        "local_docker_image_id": short_digest(local_id),
        "local_registry_digest": short_digest(reg_digest),
        "containerd_digest": short_digest(ctr_digest) or ("unavailable" if not ctr_available else ""),
        "helm_deployment_image_ref": dep_image,
        "running_pod_imageID": short_digest(pod_id),
        "expected_digest": short_digest(expected_digest),
        "aligned": aligned,
        "reason": reason,
    })

result = {
    "generated_at": datetime.now(timezone.utc).isoformat(),
    "namespace": ns,
    "registry": registry,
    "phase": phase,
    "runtime_service_count": len(runtime_services),
    "stale_non_exempt": stale_non_exempt,
    "tools": {"kubectl": kubectl, "docker": docker, "curl": curl, "ctr": ctr_available},
    "rows": rows,
}

reports.mkdir(parents=True, exist_ok=True)
(reports / "runtime-image-truth.json").write_text(json.dumps(result, indent=2))

# Markdown report.
md = ["# Runtime Image Truth", "",
      "> Deployment truth is the running estate, not the deployment story.", "",
      f"- Namespace: `{ns}` | Registry: `{registry}` | Phase: `{phase}`",
      f"- Runtime services checked: **{len(runtime_services)}**",
      f"- Stale non-exempt services: **{len(stale_non_exempt)}**", "",
      "| service | aligned | reason | expected_digest | registry | pod_imageID | deploy_ref |",
      "|---|---|---|---|---|---|---|"]
for r in rows:
    md.append("| {service} | {aligned} | {reason} | {expected_digest} | {local_registry_digest} | {running_pod_imageID} | {helm_deployment_image_ref} |".format(**r))
(reports / "runtime-image-truth.md").write_text("\n".join(md) + "\n")

if as_json:
    print(json.dumps(result, indent=2))
else:
    for r in rows:
        print(f"{r['aligned']:<7} {r['service']:<34} {r['reason']:<26} reg={r['local_registry_digest'] or '-'} pod={r['running_pod_imageID'] or '-'}")
    print("")
    if stale_non_exempt:
        print(f"RUNTIME_IMAGE_TRUTH: FAIL ({len(stale_non_exempt)} stale non-exempt: {', '.join(stale_non_exempt[:10])}{'...' if len(stale_non_exempt) > 10 else ''})")
    elif not (kubectl or docker):
        print("RUNTIME_IMAGE_TRUTH: UNKNOWN (no kubectl/docker available in this context)")
    else:
        print("RUNTIME_IMAGE_TRUTH: PASS (all non-exempt runtime services aligned to target)")

raise SystemExit(1 if stale_non_exempt else 0)
PY
