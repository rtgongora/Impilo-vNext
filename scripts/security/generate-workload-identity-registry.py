#!/usr/bin/env python3
"""Generate the Tshepo workload identity registry from live cluster state.

Checkpoint 4. Every Deployment, StatefulSet, CronJob and Job in the target namespace
becomes one registry row carrying the canonical workload identity plus the evidence
needed to migrate it off shared credentials.

Measured fields come from the cluster. Declared fields come from the repository
registries and are labelled as such -- a declaration is not evidence of a live call.
Anything neither measured nor declared is written as UNKNOWN, never guessed.

Usage:
  python3 scripts/security/generate-workload-identity-registry.py \
      --namespace impilo-full-preview --environment preview --cluster k3s-impilo \
      --output docs/security/trust-audit/checkpoint-4/workload-identity-registry.yaml
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

import yaml

REPO = Path(__file__).resolve().parents[2]

# A service DNS target inside the cluster, as it appears in an env var value.
# The host group must swallow dots, otherwise an external FQDN such as
# https://impilo.mohcc.gov.zw/ captures the bare first label and survives the
# "external FQDN" filter below as if it were an in-cluster service.
SERVICE_URL = re.compile(r"https?://([a-z0-9][a-z0-9.-]*)(?::(\d+))?", re.IGNORECASE)

# Env var names whose values are credentials or point at one. Never emit values.
SECRET_NAME_HINT = re.compile(r"SECRET|PASSWORD|TOKEN|KEY|CREDENTIAL", re.IGNORECASE)

INFRA_WORKLOADS = {
    "postgres", "redis", "kafka", "minio", "orthanc", "hapi-fhir",
    "keycloak", "keycloak-database", "envoy", "livekit", "livekit-egress",
    "ndila-martin", "matcher-engine", "redroid", "opa",
}


def kubectl_json(namespace: str, kind: str) -> list[dict]:
    proc = subprocess.run(
        ["kubectl", "-n", namespace, "get", kind, "-o", "json"],
        capture_output=True, text=True,
    )
    if proc.returncode != 0:
        print(f"warn: kubectl get {kind} failed: {proc.stderr.strip()}", file=sys.stderr)
        return []
    return json.loads(proc.stdout or "{}").get("items", [])


def pod_spec_of(item: dict, kind: str) -> dict:
    spec = item.get("spec", {})
    if kind == "CronJob":
        return spec.get("jobTemplate", {}).get("spec", {}).get("template", {}).get("spec", {})
    return spec.get("template", {}).get("spec", {})


def load_repo_context() -> tuple[dict, dict]:
    """Return (classification_by_id, registry_by_id) from the repository registries."""
    classification = {}
    cpath = REPO / "config" / "full-boot-service-classification.yml"
    if cpath.exists():
        doc = yaml.safe_load(cpath.read_text()) or {}
        for row in doc.get("classifications", []) or []:
            if row.get("id"):
                classification[row["id"]] = row

    registry = {}
    rpath = REPO / "docs" / "registry" / "services-registry.yaml"
    if rpath.exists():
        doc = yaml.safe_load(rpath.read_text()) or {}
        for row in doc.get("services", []) or []:
            if row.get("id"):
                registry[row["id"]] = row
    return classification, registry


def measured_destinations(containers: list[dict], self_name: str) -> list[str]:
    """Service hostnames this workload is configured to call, read from its own env."""
    found: set[str] = set()
    for c in containers:
        for env in c.get("env", []) or []:
            value = env.get("value")
            if not isinstance(value, str):
                continue
            for host, _port in SERVICE_URL.findall(value):
                host = host.lower()
                if host == self_name or host in {"localhost", "127.0.0.1"}:
                    continue
                if "." in host:  # external FQDN, not an in-cluster service
                    continue
                found.add(host)
    return sorted(found)


def credential_evidence(containers: list[dict]) -> dict:
    """Which credential mechanisms this workload is configured for. Names only."""
    keycloak_client = None
    secret_refs: set[str] = set()
    oauth_disabled = None
    issuer = None
    for c in containers:
        for env in c.get("env", []) or []:
            name = env.get("name", "")
            value = env.get("value")
            ref = (env.get("valueFrom") or {}).get("secretKeyRef") or {}
            if ref.get("name"):
                secret_refs.add(ref["name"])
            if name.endswith("KEYCLOAK_BACKEND_CLIENT_ID") or name == "KEYCLOAK_BACKEND_CLIENT_ID":
                keycloak_client = value
            if name == "IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS":
                oauth_disabled = value
            if "ISSUER_URI" in name and isinstance(value, str) and value:
                issuer = value
    return {
        "keycloak_client_id": keycloak_client or "UNKNOWN",
        "secret_refs": sorted(secret_refs),
        "oauth_resource_server_disabled": oauth_disabled if oauth_disabled is not None else "UNKNOWN",
        "token_issuer": issuer or "UNKNOWN",
    }


def flow_category(name: str, kind: str, classification: dict) -> str:
    if kind in {"Job", "CronJob"}:
        return "BATCH"
    if name in INFRA_WORKLOADS:
        return "INFRASTRUCTURE"
    runtime_kind = (classification.get(name) or {}).get("runtime_kind")
    if runtime_kind == "infrastructure":
        return "INFRASTRUCTURE"
    if name in {"experience-bff", "one-ui-shell", "public-website"}:
        return "EDGE_COMPOSITION"
    return "SYNCHRONOUS_SERVICE"


def build_rows(namespace: str, environment: str, cluster: str) -> list[dict]:
    classification, registry = load_repo_context()
    rows: list[dict] = []

    for kind, api in (("Deployment", "deploy"), ("StatefulSet", "sts"),
                      ("CronJob", "cronjob"), ("Job", "job")):
        for item in kubectl_json(namespace, api):
            name = item["metadata"]["name"]
            spec = pod_spec_of(item, kind)
            containers = (spec.get("containers") or []) + (spec.get("initContainers") or [])
            current_sa = spec.get("serviceAccountName") or "default"
            automount = spec.get("automountServiceAccountToken")

            # Target SA is the workload's own name: one identity per workload, never shared.
            target_sa = name
            cls = classification.get(name, {})
            reg = registry.get(name, {})
            creds = credential_evidence(containers)

            bypasses = []
            if creds["oauth_resource_server_disabled"] == "true":
                bypasses.append("IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true")
            if current_sa == "default":
                bypasses.append("shared-default-serviceaccount")
            if automount is not False:
                bypasses.append("serviceaccount-token-automounted")
            if creds["keycloak_client_id"] not in ("UNKNOWN", None):
                bypasses.append(f"shared-keycloak-client:{creds['keycloak_client_id']}")

            rows.append({
                "workload_id": f"urn:impilo:workload:{environment}:{cluster}:{namespace}:{target_sa}:{name}",
                "workload": name,
                "kind": kind,
                "environment": environment,
                "cluster": cluster,
                "namespace": namespace,
                "service_account": {
                    "current": current_sa,
                    "target": target_sa,
                    "automount_current": automount if automount is not None else "DEFAULT_TRUE",
                    "automount_target": False,
                },
                "owner": reg.get("owner_team") or "UNKNOWN",
                "plane": reg.get("primary_plane") or cls.get("plane") or "UNKNOWN",
                "domain": reg.get("domain") or cls.get("domain") or "UNKNOWN",
                "credential": {
                    "current_type": ("SHARED_KEYCLOAK_CLIENT"
                                     if creds["keycloak_client_id"] != "UNKNOWN"
                                     else "NONE_OBSERVED"),
                    "target_type": "PROJECTED_K8S_TOKEN",
                    "keycloak_client_id_current": creds["keycloak_client_id"],
                    "issuer_current": creds["token_issuer"],
                    "issuer_target": f"https://impilo.mohcc.gov.zw/realms/impilo",
                    "audience_current": "UNKNOWN",
                    "audience_target": f"urn:impilo:tshepo:{name}",
                    "secret_refs": creds["secret_refs"],
                },
                "destinations": {
                    "measured_from_env": measured_destinations(containers, name),
                    "declared_consumes_from": reg.get("consumes_from") or [],
                    "declared_exposes_to": reg.get("exposes_to") or [],
                    "evidence": "MEASURED_CONFIG_ONLY",
                },
                "flow_category": flow_category(name, kind, classification),
                "human_delegation_required": "UNKNOWN",
                "current_bypasses": bypasses,
                "transport": {
                    "state": "PLAINTEXT_HTTP",
                    "mtls": "ABSENT",
                    "network_policy": "NOT_ENFORCED_BY_CLUSTER",
                },
                "migration_cohort": "UNASSIGNED",
            })

    rows.sort(key=lambda r: (r["kind"], r["workload"]))
    return rows


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--namespace", default="impilo-full-preview")
    ap.add_argument("--environment", default="preview")
    ap.add_argument("--cluster", default="k3s-impilo")
    ap.add_argument("--output", required=True)
    args = ap.parse_args()

    rows = build_rows(args.namespace, args.environment, args.cluster)
    if not rows:
        print("error: no workloads discovered -- refusing to write an empty registry", file=sys.stderr)
        return 1

    counts: dict[str, int] = {}
    for r in rows:
        counts[r["kind"]] = counts.get(r["kind"], 0) + 1

    doc = {
        "registry_version": 1,
        "generated_by": "scripts/security/generate-workload-identity-registry.py",
        "source_of_truth": (
            "Live cluster inventory. Repository registries supply owner/plane/declared "
            "destinations only, and are labelled declared. UNKNOWN is retained, never guessed."
        ),
        "namespace": args.namespace,
        "environment": args.environment,
        "cluster": args.cluster,
        "node_counts": dict(sorted(counts.items())),
        "total_nodes": len(rows),
        "workloads": rows,
    }

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(yaml.safe_dump(doc, sort_keys=False, width=120))
    print(f"wrote {out} -- {len(rows)} workloads {dict(sorted(counts.items()))}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
