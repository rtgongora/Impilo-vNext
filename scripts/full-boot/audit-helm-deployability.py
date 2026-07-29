#!/usr/bin/env python3
"""Audit Helm deployability for required full-boot services."""
from __future__ import annotations

import json
import os
from datetime import datetime, timezone
from pathlib import Path

import yaml

ROOT = Path(os.environ.get("REPO_PATH", Path(__file__).resolve().parents[2]))
CLS_PATH = ROOT / "config/full-boot-service-classification.yml"
HELM_DIR = ROOT / "deploy/helm/impilo-vnext"
VALUES_FULL = HELM_DIR / "values-full-preview.yaml"
VALUES_RUNTIME_GEN = HELM_DIR / "values-full-preview-runtime.generated.yaml"
TEMPLATES_DIR = HELM_DIR / "templates"

DEDICATED = {
    "postgres",
    "redis",
    "kafka",
    "keycloak",
    "envoy",
    "minio",
    "hapi-fhir",
    "experience-bff",
    "one-ui-shell",
}

INFRA_KEYS = {
    "postgres": "postgres",
    "redis": "redis",
    "kafka": "kafka",
    "keycloak": "keycloak",
    "envoy": "envoy",
    "minio": "minio",
    "hapi-fhir": "hapiFhir",
}

TEMPLATE_MAP = {
    "postgres": "postgres.yaml",
    "redis": "redis.yaml",
    "kafka": "kafka.yaml",
    "keycloak": "keycloak.yaml",
    "envoy": "envoy.yaml",
    "minio": "minio.yaml",
    "hapi-fhir": "hapi-fhir.yaml",
    "experience-bff": "experience-bff.yaml",
    "one-ui-shell": "one-ui-shell.yaml",
}


def load_merged_values() -> dict:
    """Merge static full-preview values with generated fullBootServices overlay."""
    values = yaml.safe_load(VALUES_FULL.read_text()) if VALUES_FULL.exists() else {}
    if not isinstance(values, dict):
        values = {}
    if VALUES_RUNTIME_GEN.exists():
        gen = yaml.safe_load(VALUES_RUNTIME_GEN.read_text()) or {}
        if isinstance(gen, dict):
            if gen.get("fullBootServices"):
                values["fullBootServices"] = gen["fullBootServices"]
            if gen.get("postgres"):
                pg = values.get("postgres") or {}
                pg.update(gen["postgres"])
                values["postgres"] = pg
    return values


def helm_coverage(sid: str, values: dict, template_text: str) -> tuple[str, str]:
    if sid in DEDICATED:
        key = INFRA_KEYS.get(sid)
        if sid == "experience-bff":
            key = "experienceBff"
        elif sid == "one-ui-shell":
            key = "oneUiShell"
        block = values.get(key) if key else None
        if block and block.get("enabled") is False:
            return "helm_partial", "disabled in values-full-preview"
        tpl = TEMPLATE_MAP[sid]
        if (TEMPLATES_DIR / tpl).exists():
            return "helm_ready", tpl
        return "helm_missing", f"missing {tpl}"
    svc = (values.get("fullBootServices") or {}).get(sid)
    if not svc:
        return "config_missing", "not in fullBootServices (runtime.generated)"
    if "fullBootServices" not in template_text:
        return "helm_missing", "microservice template"
    # Generic microservice.yaml deploys every fullBootServices entry; enabled is wave state.
    note = "microservice.yaml"
    if svc.get("enabled") is False:
        note = "microservice.yaml (wave-disabled)"
    return "helm_ready", note


def main() -> None:
    cls = yaml.safe_load(CLS_PATH.read_text())
    required = [
        e
        for e in cls["classifications"]
        if e.get("classification") == "required_full_boot"
        or e.get("full_boot_classification") == "required_full_boot"
    ]
    template_text = "\n".join(
        p.read_text() for p in TEMPLATES_DIR.iterdir() if p.suffix in (".yaml", ".tpl")
    )
    values = load_merged_values()

    rows = []
    for e in required:
        sid = e["id"]
        status, note = helm_coverage(sid, values, template_text)
        rows.append(
            {
                "id": sid,
                "plane": e.get("plane"),
                "port": e.get("default_http_port"),
                "deploy_order": e.get("deploy_order_group"),
                "image": e.get("image_name") or e.get("official_image"),
                "status": status,
                "note": note,
            }
        )

    counts: dict[str, int] = {}
    for r in rows:
        counts[r["status"]] = counts.get(r["status"], 0) + 1

    md = [
        "# Full-Boot Required Helm Readiness",
        "",
        "Chart readiness for the services full boot actually requires. The whole-estate view — every",
        "registered service and whether it has a chart at all — is FULL_HELM_DEPLOYABILITY_MATRIX.md,",
        "written by generate-full-boot-artifacts.mjs. Both once wrote that one path, so whichever ran",
        "last in the pipeline replaced the other's report with an incompatible table.",
        "",
        f"Generated: {datetime.now(timezone.utc).isoformat()}",
        "",
        f"**Required services:** {len(required)}",
        "",
        "## Summary",
        "",
        "| Status | Count |",
        "|--------|-------|",
    ]
    for k, v in sorted(counts.items()):
        md.append(f"| {k} | {v} |")
    md.extend(
        [
            "",
            "## Required services",
            "",
            "| Service | Plane | Port | Deploy group | Image | Helm status | Notes |",
            "|---------|-------|------|--------------|-------|-------------|-------|",
        ]
    )
    for r in rows:
        md.append(
            f"| {r['id']} | {r['plane']} | {r['port'] or '—'} | {r['deploy_order']} | {r['image'] or '—'} | {r['status']} | {r['note']} |"
        )
    md.append("")
    (ROOT / "docs/environment/FULL_BOOT_REQUIRED_HELM_READINESS.md").write_text("\n".join(md))

    infra_deps = [
        ("postgres", "postgres:16-alpine", "postgres.yaml", "postgres", "5432", "pg_isready", 1),
        ("redis", "redis:7-alpine", "redis.yaml", "redis", "6379", "redis ping", 2),
        ("kafka", "apache/kafka:3.7.1", "kafka.yaml", "kafka", "9092", "tcp:9092", 3),
        ("keycloak", "quay.io/keycloak/keycloak:25.0", "keycloak.yaml", "keycloak", "8080", "/health/ready", 4),
        ("minio", "minio/minio:latest", "minio.yaml", "minio", "9000", "/minio/health/ready", 5),
        ("hapi-fhir", "hapiproject/hapi:v7.4.0", "hapi-fhir.yaml", "hapiFhir", "8090", "/fhir/metadata", 6),
        ("envoy", "envoyproxy/envoy:v1.31-latest", "envoy.yaml", "envoy", "10000", "admin /ready", 7),
    ]
    infra_md = [
        "# Full Boot Infrastructure Dependency Matrix",
        "",
        f"Generated: {datetime.now(timezone.utc).isoformat()}",
        "",
        "Target namespace: **impilo-full-preview**",
        "",
        "| Component | Official image | Chart template | Values key | Port | Health | Order | Status |",
        "|-----------|----------------|----------------|------------|------|--------|-------|--------|",
    ]
    for comp, image, tpl, vkey, port, health, order in infra_deps:
        enabled = (values.get(vkey) or {}).get("enabled", False)
        st = "helm_ready" if enabled and (TEMPLATES_DIR / tpl).exists() else "config_missing"
        infra_md.append(
            f"| {comp} | {image} | templates/{tpl} | values-full-preview.yaml#{vkey} | {port} | {health} | {order} | {st} |"
        )
    infra_md.append("")
    (ROOT / "docs/environment/FULL_BOOT_INFRASTRUCTURE_DEPENDENCY_MATRIX.md").write_text(
        "\n".join(infra_md)
    )

    report_dir = ROOT / "reports/full-boot"
    report_dir.mkdir(parents=True, exist_ok=True)
    (report_dir / "helm-deployability-audit.json").write_text(
        json.dumps({"required": len(required), "counts": counts, "rows": rows}, indent=2)
    )
    ready = counts.get("helm_ready", 0)
    print(
        f"helm audit: {len(required)} required, helm_ready={ready}, "
        f"missing={(counts.get('helm_missing', 0) + counts.get('config_missing', 0))}"
    )


if __name__ == "__main__":
    main()
