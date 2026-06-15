#!/usr/bin/env python3
"""Resolve the kubelet-visible platform manifest digest for a registry image tag.

When a registry serves a multi-platform OCI image index (common after buildx pushes),
the index digest differs from the linux/amd64 manifest digest reported as pod imageID.
Helm must pin the platform manifest digest, not the index digest.
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from typing import Optional, Tuple


def _curl_manifest(
    registry: str, repository: str, reference: str
) -> Tuple[int, dict, str, str]:
    url = f"http://{registry}/v2/{repository}/manifests/{reference}"
    result = subprocess.run(
        [
            "curl",
            "-sS",
            "-D",
            "-",
            "-o",
            "-",
            "-H",
            "Accept: application/vnd.oci.image.index.v1+json",
            "-H",
            "Accept: application/vnd.docker.distribution.manifest.v2+json",
            "-H",
            "Accept: application/vnd.oci.image.manifest.v1+json",
            url,
        ],
        capture_output=True,
        text=True,
        timeout=30,
    )
    if result.returncode != 0:
        return result.returncode, {}, "", result.stderr.strip()

    header_blob, _, body = result.stdout.partition("\r\n\r\n")
    if not body and "\n\n" in result.stdout:
        header_blob, _, body = result.stdout.partition("\n\n")

    headers: dict[str, str] = {}
    content_type = ""
    digest = ""
    status = 0
    for line in header_blob.splitlines():
        lower = line.lower()
        if line.startswith("HTTP/"):
            try:
                status = int(line.split()[1])
            except (IndexError, ValueError):
                status = 0
        elif lower.startswith("content-type:"):
            content_type = line.split(":", 1)[1].strip()
            headers["content-type"] = content_type
        elif lower.startswith("docker-content-digest:"):
            digest = line.split(":", 1)[1].strip()
            headers["docker-content-digest"] = digest

    return status, headers, body, ""


def _platform_manifest_from_index(
    body: str,
    platform_os: str = "linux",
    platform_arch: str = "amd64",
) -> str:
    try:
        doc = json.loads(body)
    except json.JSONDecodeError:
        return ""

    manifests = doc.get("manifests") or []
    candidates = []
    for entry in manifests:
        platform = entry.get("platform") or {}
        os_name = platform.get("os", "")
        arch = platform.get("architecture", "")
        media_type = entry.get("mediaType", "")
        if os_name == "unknown" or arch == "unknown":
            continue
        if "attestation" in media_type:
            continue
        annotations = entry.get("annotations") or {}
        if annotations.get("vnd.docker.reference.type") == "attestation-manifest":
            continue
        candidates.append(entry)

    for entry in candidates:
        platform = entry.get("platform") or {}
        if platform.get("os") == platform_os and platform.get("architecture") == platform_arch:
            return entry.get("digest", "")

    for entry in candidates:
        digest = entry.get("digest", "")
        if digest:
            return digest
    return ""


def resolve_runtime_digest(
    registry: str,
    repository: str,
    tag: str = "preview",
    platform_os: str = "linux",
    platform_arch: str = "amd64",
) -> str:
    status, headers, body, err = _curl_manifest(registry, repository, tag)
    if status != 200:
        raise RuntimeError(err or f"registry manifest fetch failed HTTP {status} for {repository}:{tag}")

    content_type = headers.get("content-type", "")
    digest = headers.get("docker-content-digest", "")

    if "oci.image.index" in content_type or (
        body.strip().startswith("{") and '"manifests"' in body and '"mediaType"' in body
    ):
        try:
            doc = json.loads(body)
        except json.JSONDecodeError as exc:
            raise RuntimeError(f"invalid OCI index JSON for {repository}:{tag}") from exc
        if doc.get("manifests"):
            manifest_digest = _platform_manifest_from_index(body, platform_os, platform_arch)
            if manifest_digest:
                return manifest_digest

    if digest:
        return digest
    raise RuntimeError(f"no digest resolved for {repository}:{tag}")


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("service_id", help="Impilo service id (e.g. ai-model-registry-service)")
    parser.add_argument("--tag", default="preview")
    parser.add_argument("--registry", default="127.0.0.1:5000")
    parser.add_argument("--platform-os", default="linux")
    parser.add_argument("--platform-arch", default="amd64")
    args = parser.parse_args(argv)

    repository = f"impilo/{args.service_id}"
    try:
        digest = resolve_runtime_digest(
            args.registry,
            repository,
            args.tag,
            args.platform_os,
            args.platform_arch,
        )
    except RuntimeError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print(digest)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
