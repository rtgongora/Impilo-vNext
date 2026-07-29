#!/usr/bin/env bash
# Verify required full-boot images in k3s/containerd (image presence ≠ runtime readiness).
set -uo pipefail
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"
cd "$REPO_PATH"
full_boot_ensure_artifacts

NS="${NS:-${FULL_BOOT_NAMESPACE:-impilo-full-preview}}"
TAG="${1:-preview}"
REPORT="${REPORT:-$FULL_BOOT_REPORTS/k3s-image-verify-preview.log}"
mkdir -p "$(dirname "$REPORT")"

run_verify() {
echo "=== Full boot image presence verification ==="
echo "Namespace (test pods only): $NS"
echo "Tag: $TAG"
echo "Time: $(date -Is)"
echo ""
echo "NOTE: v-* pods are temporary image-check pods, not full-boot deployments."
echo "Runtime readiness is NOT APPLICABLE until Helm deploy into $NS."
echo ""

# --- A. containerd image list (authoritative) ---
HELPER_LIST="/usr/local/sbin/impilo-k3s-list-images"
REPO_ROOT="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
CTR_IMAGES=""
if [[ -x "$HELPER_LIST" ]] && sudo -n -l 2>/dev/null | grep -q 'impilo-k3s-list-images'; then
  set +e
  sudo -n "$HELPER_LIST" "$TAG" "$REPO_ROOT" 2>&1 | tee "$REPORT"
  helper_exit=${PIPESTATUS[0]}
  set -e
  echo ""
  echo "Log: $REPORT"
  exit "$helper_exit"
fi

if sudo -n k3s ctr images list -q >/dev/null 2>&1; then
  CTR_IMAGES="$(sudo -n k3s ctr images list -q 2>/dev/null || true)"
elif [[ -n "${SUDO_PASS:-}" ]]; then
  CTR_IMAGES="$(echo "$SUDO_PASS" | sudo -S k3s ctr images list -q 2>/dev/null || true)"
else
  echo "IMAGE_PRESENCE: FAIL"
  echo "  Reason: cannot list containerd images."
  echo "  Install narrow helper: sudo bash scripts/operator/install-k3s-image-helper.sh"
  echo "  Then: bash scripts/operator/fullboot.sh verify-images"
  echo ""
  exit 2
fi

export NS TAG REPO_PATH CTR_IMAGES
IMAGE_EXIT=0
ADVISORY_EXIT=0

python3 <<'PY'
import os
import subprocess
import sys
import time

import yaml

repo = os.environ.get("REPO_PATH", ".")
tag = os.environ.get("TAG", "preview")
ns = os.environ.get("NS", "impilo-full-preview")
ctr_raw = os.environ.get("CTR_IMAGES", "")
ctr_lines = [ln.strip() for ln in ctr_raw.splitlines() if ln.strip()]

cls = yaml.safe_load(open(os.path.join(repo, "config/full-boot-service-classification.yml")))
req = [e for e in cls["classifications"] if e.get("classification") == "required_full_boot"]


def expected_ref(e):
    if e.get("official_image"):
        off = e["official_image"]
        return off if ":" in off else f"{off}:latest"
    sid = e["id"]
    return f"impilo/{sid}:{tag}"


def image_present(ref: str) -> tuple[bool, str]:
    repo = ref.split(":")[0] if ":" in ref else ref
    want_tag = ref.split(":", 1)[1] if ":" in ref else "latest"
    matches = []
    for line in ctr_lines:
        if repo not in line:
            continue
        if want_tag == "preview":
            if ":preview" in line or ":preview-" in line:
                matches.append(line)
        elif f":{want_tag}" in line:
            matches.append(line)
    if matches:
        return True, matches[0]
    return False, ""


def has_shell_probe(ref: str) -> bool:
    # Minimal / JVM-only images often lack sleep/sh — use containerd only.
    base = ref.split(":")[0].lower()
    if "hapi" in base or "keycloak" in base:
        return False
    return True


print("--- A. Image presence (k3s containerd) ---")
present = 0
missing = []
for e in req:
    ref = expected_ref(e)
    ok, matched = image_present(ref)
    sid = e["id"]
    if ok:
        present += 1
        print(f"PASS  {sid}  {ref}")
        if matched and matched != ref:
            print(f"      matched: {matched}")
    else:
        missing.append((sid, ref))
        print(f"FAIL  {sid}  {ref}  (not in containerd)")

total = len(req)
print("")
if missing:
    print(f"IMAGE_PRESENCE: FAIL  ({present}/{total} present)")
    for sid, ref in missing:
        print(f"  missing: {ref}")
else:
    print(f"IMAGE_PRESENCE: PASS  ({present}/{total} present)")

print("")
print("--- B. Temporary test pods (advisory only) ---")
print("Pods prefixed v- are image checks only; Completed/Running both OK if image pulled.")
print("StartError on images without shell (e.g. HAPI) is not an image-missing signal.")
print("")

kubectl_get = subprocess.run(
    ["kubectl", "get", "namespace", ns], capture_output=True
)
if kubectl_get.returncode != 0:
    subprocess.run(["kubectl", "create", "namespace", ns], check=False)

# Remove stale verification pods
subprocess.run(
    [
        "kubectl",
        "delete",
        "pods",
        "-n",
        ns,
        "-l",
        "impilo.io/fullboot-image-verify=true",
        "--ignore-not-found",
    ],
    capture_output=True,
)
for e in req:
    sid = e["id"]
    name = ("v-" + sid.replace("_", "-"))[:63].rstrip("-")
    subprocess.run(
        ["kubectl", "delete", "pod", name, "-n", ns, "--ignore-not-found"],
        capture_output=True,
    )

advisory_fail = 0
advisory_ok = 0
for e in req:
    ref = expected_ref(e)
    sid = e["id"]
    name = ("v-" + sid.replace("_", "-"))[:63].rstrip("-")
    if not has_shell_probe(ref):
        print(f"SKIP  {name}  pod probe (use containerd only for {ref})")
        continue
    subprocess.run(
        [
            "kubectl",
            "run",
            name,
            "-n",
            ns,
            "--image=" + ref,
            "--image-pull-policy=Never",
            "--restart=Never",
            "--labels=impilo.io/fullboot-image-verify=true,impilo.io/service=" + sid,
            "--command",
            "--",
            "/bin/sh",
            "-c",
            "exit 0",
        ],
        capture_output=True,
    )
    time.sleep(0.2)

time.sleep(8)
try:
    out = subprocess.check_output(
        ["kubectl", "get", "pods", "-n", ns, "-l", "impilo.io/fullboot-image-verify=true", "--no-headers"],
        text=True,
        stderr=subprocess.DEVNULL,
    )
except subprocess.CalledProcessError:
    out = ""

for line in out.splitlines():
    parts = line.split()
    if len(parts) < 3:
        continue
    pod, status = parts[0], parts[2]
    ok = status in ("Running", "Completed", "Succeeded")
    if ok:
        advisory_ok += 1
        print(f"ADVISORY_PASS  {pod}  {status}")
    elif status == "ErrImageNeverPull":
        print(f"ADVISORY_FAIL  {pod}  {status}  (image not on node)")
        advisory_fail += 1
    else:
        print(f"ADVISORY_WARN  {pod}  {status}  (see kubectl describe; may be probe/cmd)")
        # Do not count StartError on slim images as advisory_fail if containerd passed

print("")
print(f"TEST_POD_ADVISORY: ok={advisory_ok} warn/fail={advisory_fail} (non-blocking)")

print("")
print("--- C. Runtime deployment readiness ---")
print("RUNTIME_DEPLOYMENT: NOT_APPLICABLE  (full boot Helm deploy has not run)")

print("")
print(f"SUMMARY image_present={present} image_missing={len(missing)} required={total}")
if missing:
    print("SUMMARY ok=0 fail={}".format(len(missing)))
    sys.exit(1)
print("SUMMARY ok={} fail=0".format(total))
sys.exit(0)
PY

IMAGE_EXIT=$?

echo ""
echo "Cleaning up temporary v-* verification pods in $NS ..."
kubectl delete pods -n "$NS" -l impilo.io/fullboot-image-verify=true --ignore-not-found 2>/dev/null || true
for p in $(kubectl get pods -n "$NS" --no-headers -o custom-columns=NAME:.metadata.name 2>/dev/null | grep '^v-' || true); do
  kubectl delete pod -n "$NS" "$p" --ignore-not-found 2>/dev/null || true
done

echo ""
echo "Log: $REPORT"
return "$IMAGE_EXIT"
}

run_verify 2>&1 | tee "$REPORT"
exit "${PIPESTATUS[0]}"
