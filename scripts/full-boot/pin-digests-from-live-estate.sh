#!/usr/bin/env bash
# Generate the Helm digest pins from THE LIVE ESTATE, not from a registry tag.
#
# Why this exists, and why resolve-image-digests.sh is not it:
#   resolve-image-digests.sh resolves each service's digest by asking the registry what
#   :preview points at now. That is one lane's most recent build. Writing those pins
#   assigns that lane's view to services other lanes built and deployed, and the next
#   `helm upgrade` then reverts their work — while reporting success.
#
#   Measured 2026-08-06: all 103 running deployments carry digests the committed pins do
#   not name. The pins date from 2026-07-27. A helm upgrade today would revert the entire
#   estate, not the eight services the hold note recorded.
#
# What this does instead: reads the digest each Deployment is ACTUALLY running and writes
# those. The result makes the image portion of a `helm upgrade` a no-op — which is what
# lets an unrelated change (enabling ext_authz) go out without taking the estate with it.
#
# Usage: bash scripts/full-boot/pin-digests-from-live-estate.sh [--namespace impilo-full-preview] [--write]
#        Without --write it prints a diff summary and changes nothing.
set -euo pipefail

NS="impilo-full-preview"
WRITE=0
OUT="deploy/helm/impilo-vnext/values-full-preview-digests.generated.yaml"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --namespace) NS="$2"; shift 2 ;;
    --write) WRITE=1; shift ;;
    --out) OUT="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

command -v kubectl >/dev/null || { echo "kubectl not found" >&2; exit 2; }
kubectl get ns "$NS" >/dev/null 2>&1 || { echo "namespace $NS not reachable" >&2; exit 2; }

# The JSON goes via a file, not a pipe: a heredoc-fed `python3 -` takes its stdin from the
# heredoc, so piping kubectl into it silently feeds python its own script instead of the
# cluster data. It failed loudly here, but the shape of that mistake is a script that reads
# nothing and writes a confident, empty answer.
SNAP="$(mktemp)"; trap 'rm -f "$SNAP"' EXIT
kubectl -n "$NS" get deploy -o json > "$SNAP"
OUT="$OUT" NS="$NS" WRITE="$WRITE" SNAP="$SNAP" python3 <<'PY'
import json, os, sys, datetime, pathlib, re

data = json.load(open(os.environ["SNAP"]))
out = pathlib.Path(os.environ["OUT"])
write = os.environ["WRITE"] == "1"

# The pin file is keyed to the Impilo registry and rendered as registry/repo@sha256, so it
# may only carry images FROM that registry. Third-party infrastructure — envoyproxy/envoy,
# apache/kafka, hapiproject/hapi, mirror.gcr.io/livekit — is pulled from upstream and
# pinning it here would render nonsense paths under the local registry prefix.
# Note keycloak IS in scope: the estate runs a custom 127.0.0.1:5000/impilo/keycloak build.
IMPILO_REGISTRY_PREFIX = "127.0.0.1:5000/impilo/"

live = {}
undigested = []
skipped_third_party = []
for item in data.get("items", []):
    name = item["metadata"]["name"]
    for c in item["spec"]["template"]["spec"]["containers"]:
        img = c["image"]
        if not img.startswith(IMPILO_REGISTRY_PREFIX):
            skipped_third_party.append(f"{name} -> {img}")
            break
        if "@sha256:" in img:
            repo, digest = img.split("@", 1)
            live[name] = {"repo": repo, "digest": digest}
        else:
            # A tag-based spec still resolves to one concrete digest at runtime, and the
            # running pod knows it. Recording that is the difference between pinning what
            # is deployed and dropping the service from the pin set — which would let the
            # next helm upgrade render it from the chart default and revert whoever is
            # mid-flight on that tag. Two services were in exactly this state on 2026-08-06.
            undigested.append(f"{name} -> {img}")
        break  # first container is the service container by convention here

# Second pass: resolve tag-based deployments from the digest their pod actually resolved.
resolved_from_pod = []
if undigested:
    import subprocess
    pods = json.loads(subprocess.run(
        ["kubectl", "-n", os.environ["NS"], "get", "pod", "-o", "json"],
        capture_output=True, text=True).stdout or '{"items":[]}')
    by_owner = {}
    for pod in pods.get("items", []):
        for cs in pod.get("status", {}).get("containerStatuses", []) or []:
            iid = cs.get("imageID", "")
            if "@sha256:" in iid:
                by_owner.setdefault(cs.get("name"), iid.split("@", 1)[1])
    still_unpinnable = []
    for entry in undigested:
        name, img = entry.split(" -> ", 1)
        digest = by_owner.get(name)
        if digest:
            live[name] = {"repo": img.rsplit(":", 1)[0], "digest": digest}
            resolved_from_pod.append(name)
        else:
            still_unpinnable.append(entry)
    undigested = still_unpinnable

if not live:
    print("FAIL: no digest-pinned deployments found; refusing to write an empty pin set", file=sys.stderr)
    sys.exit(1)

# A tag-based image cannot be pinned honestly: there is no digest to record. Say so rather
# than inventing one or silently dropping the service from the file.
if skipped_third_party:
    print(f"SKIPPED {len(skipped_third_party)} third-party image(s) — not from the Impilo registry, "
          f"so not pinnable in this file: "
          f"{', '.join(sorted(e.split(' -> ')[0] for e in skipped_third_party))}")
if resolved_from_pod:
    print(f"NOTE: {len(resolved_from_pod)} tag-based deployment(s) pinned from their running pod's "
          f"resolved digest: {', '.join(sorted(resolved_from_pod))}")
if undigested:
    print(f"WARNING: {len(undigested)} deployment(s) cannot be pinned (no running pod digest):")
    for u in undigested[:10]:
        print(f"  - {u}")

prev = out.read_text() if out.exists() else ""
changed = [n for n, v in sorted(live.items()) if v["digest"] not in prev]
print(f"\nlive digest-pinned deployments : {len(live)}")
print(f"pins that would change         : {len(changed)}")
print(f"pins already correct           : {len(live) - len(changed)}")
if changed[:8]:
    print("  e.g. " + ", ".join(changed[:8]))

if not write:
    print("\nDRY RUN — nothing written. Re-run with --write to update the pin file.")
    sys.exit(0)

registry = "127.0.0.1:5000"
m = re.search(r"^registry:\s*(\S+)", prev, re.M)
if m:
    registry = m.group(1)

lines = [
    "# AUTO-GENERATED — do not edit by hand.",
    "# Regenerate: bash scripts/full-boot/pin-digests-from-live-estate.sh --write",
    "#",
    "# Generated FROM THE LIVE ESTATE: each digest below is what that Deployment is actually",
    "# running, read from the cluster. Not from a registry tag — resolving :preview would pin",
    "# one lane's latest build over other lanes' deployed work, and the next helm upgrade would",
    "# revert them while reporting success.",
    f"generated_at: '{datetime.datetime.now(datetime.timezone.utc).isoformat()}'",
    "generated_from: live-cluster",
    f"namespace: {os.environ['NS']}",
    f"registry: {registry}",
    f"digest_pin_count: {len(live)}",
    "fullBootServices:",
]
for name, v in sorted(live.items()):
    lines.append(f"  {name}:")
    lines.append("    image:")
    lines.append(f"      digest: {v['digest']}")
out.write_text("\n".join(lines) + "\n")
print(f"\nWROTE {out} — {len(live)} pins, all matching what the estate is running.")
PY
