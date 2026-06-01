#!/usr/bin/env bash
# Verify required full-boot images are present in k3s (imagePullPolicy=Never test pods).
set -uo pipefail
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"
cd "$REPO_PATH"
full_boot_ensure_artifacts

NS="${NS:-${FULL_BOOT_NAMESPACE:-impilo-full-preview}}"
TAG="${1:-preview}"
export NS TAG REPO_PATH

kubectl get namespace "$NS" >/dev/null 2>&1 || kubectl create namespace "$NS"

python3 <<'PY'
import os
import subprocess
import time

import yaml

repo = os.environ.get("REPO_PATH", ".")
ns = os.environ.get("NS") or os.environ.get("FULL_BOOT_NAMESPACE") or "impilo-full-preview"
tag = os.environ.get("TAG", "preview")

cls = yaml.safe_load(open(os.path.join(repo, "config/full-boot-service-classification.yml")))
req = [e for e in cls["classifications"] if e.get("classification") == "required_full_boot"]
results = []
for e in req:
    sid = e["id"]
    if e.get("official_image"):
        img = e["official_image"] if ":" in e["official_image"] else e["official_image"] + ":latest"
    else:
        img = f"impilo/{sid}:{tag}"
    name = ("v-" + sid.replace("_", "-"))[:63].rstrip("-")
    subprocess.run(["kubectl", "delete", "pod", name, "-n", ns, "--ignore-not-found"], capture_output=True)
    subprocess.run(
        [
            "kubectl",
            "run",
            name,
            "-n",
            ns,
            "--image=" + img,
            "--image-pull-policy=Never",
            "--restart=Never",
            "--command",
            "--",
            "sleep",
            "20",
        ],
        capture_output=True,
    )
    time.sleep(0.3)
time.sleep(10)
out = subprocess.check_output(["kubectl", "get", "pods", "-n", ns, "--no-headers"], text=True)
for line in out.splitlines():
    if not line.startswith("v-"):
        continue
    parts = line.split()
    pod, status = parts[0], parts[2]
    ok = status == "Running"
    results.append((pod, status, ok))
    print(f"{'PASS' if ok else 'FAIL'} {pod} {status}")
fail = sum(1 for _, _, ok in results if not ok)
okc = sum(1 for _, _, ok in results if ok)
print(f"SUMMARY ok={okc} fail={fail} total={len(results)} namespace={ns}")
raise SystemExit(1 if fail else 0)
PY
