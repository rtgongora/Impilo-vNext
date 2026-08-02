#!/usr/bin/env python3
"""Audit deployed image provenance against a git branch.

A merged branch is not a deployed branch. This walks every workload in a namespace, reads the
commit each running image was actually built from, and asks git whether that commit is an
ancestor of the branch the estate is supposed to be running.

Why it exists: the browser login was broken in preview because `experience-bff` was running an
image built from a commit that was NOT an ancestor of the integration branch -- it predated the
merge that brought the authentication work in. Source truth said the work was merged; deployed
truth said it had never shipped. Nothing in the estate compared the two.

Provenance is read from the image's own OCI config blob (the labels the build stamps in), not
from a tag or a pin file. A tag can be moved and a pin file can go stale; the config blob is
part of the content the digest addresses.

Classification, deliberately five-valued -- UNKNOWN and EXTERNAL are not failures, and collapsing
them into one bucket is how a real gap gets hidden among the third-party images:

  IN_BRANCH   commit is an ancestor AND the service's own source is unchanged since -- the
              deployed image reflects current branch source for that service
  STALE       commit is an ancestor, but the service's source HAS changed since the image was
              built. Being "on the branch" is necessary and not sufficient: an ancestor can be
              arbitrarily old, so this is the bucket that says the running binary does not
              contain the branch's current code for this service
  DIVERGENT   commit exists in the repo but is NOT an ancestor -- running code the branch does
              not contain, the failure mode this tool was written for
  UNREACHABLE commit is stamped but not present in this repo (built elsewhere / gc'd)
  UNSTAMPED   an Impilo image carrying no commit label -- provenance cannot be established
  EXTERNAL    not an Impilo image (postgres, redis, envoy, ...) -- out of scope, not a finding

Usage:
  python3 scripts/security/audit-deployed-provenance.py --branch HEAD \
      --namespace impilo-full-preview --output reports/estate/deployed-provenance.json
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

REGISTRY = "127.0.0.1:5000"
ACCEPT = ",".join([
    "application/vnd.oci.image.index.v1+json",
    "application/vnd.docker.distribution.manifest.list.v2+json",
    "application/vnd.oci.image.manifest.v1+json",
    "application/vnd.docker.distribution.manifest.v2+json",
])
# Labels the runtime build stamps, most specific first.
COMMIT_LABELS = ("org.opencontainers.image.revision", "org.opencontainers.image.version")
SHA_RE = re.compile(r"\b([0-9a-f]{7,40})\b")


def sh(*args: str) -> tuple[int, str]:
    p = subprocess.run(args, capture_output=True, text=True)
    return p.returncode, p.stdout.strip()


def registry_get(path: str, accept: str | None = None) -> bytes | None:
    req = urllib.request.Request(f"http://{REGISTRY}{path}")
    if accept:
        req.add_header("Accept", accept)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.read()
    except Exception:
        return None


def image_config(repo: str, digest: str) -> dict | None:
    """Resolve an image reference to its config blob, following an index to the amd64 child."""
    raw = registry_get(f"/v2/{repo}/manifests/{digest}", ACCEPT)
    if not raw:
        return None
    try:
        manifest = json.loads(raw)
    except Exception:
        return None

    if "manifests" in manifest:  # an index: pick the linux/amd64 child
        child = None
        for m in manifest["manifests"]:
            plat = m.get("platform") or {}
            if plat.get("architecture") == "amd64" and plat.get("os") == "linux":
                child = m["digest"]
                break
        if child is None:
            return None
        raw = registry_get(f"/v2/{repo}/manifests/{child}", ACCEPT)
        if not raw:
            return None
        manifest = json.loads(raw)

    cfg_digest = (manifest.get("config") or {}).get("digest")
    if not cfg_digest:
        return None
    blob = registry_get(f"/v2/{repo}/blobs/{cfg_digest}")
    if not blob:
        return None
    try:
        return json.loads(blob)
    except Exception:
        return None


def commit_from_config(cfg: dict) -> tuple[str | None, str]:
    """Return (commit, source-of-evidence)."""
    container = cfg.get("config") or {}
    labels = container.get("Labels") or {}
    for label in COMMIT_LABELS:
        value = labels.get(label)
        if value:
            m = SHA_RE.search(value)
            if m:
                return m.group(1), f"label:{label}"
    # The chart injects IMPILO_GIT_COMMIT into every pod; the image may carry it too.
    for env in container.get("Env") or []:
        if env.startswith("IMPILO_GIT_COMMIT="):
            value = env.split("=", 1)[1]
            if value:
                m = SHA_RE.search(value)
                if m:
                    return m.group(1), "env:IMPILO_GIT_COMMIT"
    return None, "none"


def service_source_paths(workload: str) -> list[str]:
    """Repository paths whose change means this workload's image is out of date."""
    # src/main only. A change under src/test does not alter the shipped binary, and counting it
    # would overstate staleness -- the claim here has to be about what is actually running.
    candidates = [
        Path("services") / workload / "src" / "main",
        Path("ui") / workload / "src",
        Path("apps") / workload / "src",
    ]
    alias = {"butano-fhir": "services/butano-fhir/src/main", "hapi-fhir": None,
             "one-ui-shell": "ui/one-ui-shell/src"}
    if workload in alias:
        return [alias[workload]] if alias[workload] else []
    return [str(c) for c in candidates if c.is_dir()]


def workloads(namespace: str) -> list[dict]:
    out = []
    for kind, api in (("Deployment", "deploy"), ("StatefulSet", "sts"), ("CronJob", "cronjob")):
        rc, js = sh("kubectl", "-n", namespace, "get", api, "-o", "json")
        if rc != 0:
            continue
        for item in json.loads(js or "{}").get("items", []):
            spec = item["spec"]
            pod = (spec.get("jobTemplate", {}).get("spec", {}).get("template", {}).get("spec")
                   if kind == "CronJob" else spec.get("template", {}).get("spec")) or {}
            for c in pod.get("containers", []):
                out.append({"kind": kind, "workload": item["metadata"]["name"], "image": c["image"]})
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--namespace", default="impilo-full-preview")
    ap.add_argument("--branch", default="HEAD", help="reference the estate should be running")
    ap.add_argument("--output", required=True)
    args = ap.parse_args()

    rc, ref = sh("git", "rev-parse", args.branch)
    if rc != 0:
        print(f"error: cannot resolve --branch {args.branch}", file=sys.stderr)
        return 2

    rows, counts = [], {}
    for w in workloads(args.namespace):
        image = w["image"]
        row = {**w, "commit": None, "evidence": "none", "status": None}

        if not image.startswith(f"{REGISTRY}/impilo/"):
            row["status"] = "EXTERNAL"
        else:
            repo = image.split("/", 1)[1].split("@")[0].split(":")[0]
            ref_part = image.split("@")[1] if "@" in image else image.rsplit(":", 1)[1]
            cfg = image_config(repo, ref_part)
            commit, evidence = commit_from_config(cfg) if cfg else (None, "config-unreadable")
            row["commit"], row["evidence"] = commit, evidence
            if not commit:
                row["status"] = "UNSTAMPED"
            elif sh("git", "cat-file", "-e", f"{commit}^{{commit}}")[0] != 0:
                row["status"] = "UNREACHABLE"
            elif sh("git", "merge-base", "--is-ancestor", commit, ref)[0] == 0:
                # Ancestry alone is a weak claim: the merge that built this branch pulled in
                # several lanes, so almost any commit from any of them is an ancestor. The
                # question that decides whether the deployed binary is current is narrower --
                # has THIS service's source moved since the image was built?
                paths = service_source_paths(w["workload"])
                changed = []
                if paths:
                    rc2, log = sh("git", "log", "--oneline", f"{commit}..{ref}", "--", *paths)
                    changed = [l for l in log.splitlines() if l.strip()] if rc2 == 0 else []
                if changed:
                    row["status"] = "STALE"
                    row["unshipped_commits"] = len(changed)
                    row["unshipped_subjects"] = [c.split(" ", 1)[-1] for c in changed[:5]]
                    row["source_paths"] = paths
                else:
                    row["status"] = "IN_BRANCH"
                    row["source_paths"] = paths or None
            else:
                row["status"] = "DIVERGENT"
                # How far off the branch is it? Useful for triage order.
                rc2, behind = sh("git", "rev-list", "--count", f"{commit}..{ref}")
                row["commits_behind_branch"] = int(behind) if rc2 == 0 and behind else None
                rc3, subject = sh("git", "log", "-1", "--format=%s", commit)
                row["commit_subject"] = subject if rc3 == 0 else None

        counts[row["status"]] = counts.get(row["status"], 0) + 1
        rows.append(row)

    rows.sort(key=lambda r: (r["status"], r["workload"]))
    doc = {
        "namespace": args.namespace,
        "reference_branch": args.branch,
        "reference_commit": ref,
        "counts": dict(sorted(counts.items())),
        "workloads": rows,
    }
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(doc, indent=2) + "\n")

    print(f"reference {args.branch} = {ref[:9]}")
    for status, n in sorted(counts.items()):
        print(f"  {status:12s} {n}")
    stale = [r for r in rows if r["status"] == "STALE"]
    if stale:
        print("\nSTALE — on the branch, but the service's source moved since the image was built:")
        for r in sorted(stale, key=lambda x: -(x.get("unshipped_commits") or 0)):
            print(f"  {r['workload']:38s} {r['commit'][:9]}  "
                  f"{r['unshipped_commits']} unshipped commit(s)")
            for sub in (r.get("unshipped_subjects") or [])[:2]:
                print(f"      - {sub[:76]}")

    divergent = [r for r in rows if r["status"] == "DIVERGENT"]
    if divergent:
        print("\nDIVERGENT — running code this branch does not contain:")
        for r in divergent:
            print(f"  {r['workload']:38s} {r['commit'][:9]}  "
                  f"{r.get('commits_behind_branch')} commits behind  "
                  f"{(r.get('commit_subject') or '')[:56]}")
    print(f"\nwrote {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
