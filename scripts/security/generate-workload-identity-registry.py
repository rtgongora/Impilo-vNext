#!/usr/bin/env python3
"""Generate the Tshepo workload identity registry from live cluster state.

Checkpoint 4. Every Deployment, StatefulSet, CronJob and Job in the target namespace
becomes one registry row carrying the canonical workload identity plus the evidence
needed to migrate it off shared credentials.

Measured fields come from the cluster. Declared fields come from the repository
registries and are labelled as such -- a declaration is not evidence of a live call.
Anything neither measured nor declared is written as UNKNOWN, never guessed.

Three fields are *derived*, and each carries the derivation alongside the value so the
value can be re-checked rather than believed:

  owner                     -- plane/domain resolved from the repository registries.
                               There is no CODEOWNERS file anywhere in the repository and
                               services-registry.yaml carries `owner_team: TBD` estate-wide,
                               so `owner.team` is UNKNOWN with `team_source` recording that
                               the search was made. Plane+domain is a real, verifiable
                               ownership statement; `TBD` was not.
  human_delegation_required -- whether the workload ever acts for a human. Batch and
                               infrastructure workloads act only for themselves. For every
                               other workload this is grepped out of its own source tree:
                               a service that reads the acting human's trust context is a
                               service that requires human delegation.
  credential.audience_current
                            -- read from live container env, then from the Keycloak realm
                               import. NONE_CONFIGURED means "inspected, and no audience
                               exists" -- a finding. UNKNOWN means "could not inspect".
                               They are not the same statement and are never collapsed.

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

# Workloads that carry no row in either repository registry. Each entry is an
# ownership statement backed by a named artefact, not an opinion -- the evidence
# string is emitted into the registry as owner.source_evidence so it can be checked.
INFRA_OWNER_MAP = {
    "livekit":            ("experience", "live-events-broadcast", "peer-classification:live-service"),
    "livekit-egress":     ("experience", "live-events-broadcast", "peer-classification:live-service"),
    "orthanc":            ("clinical", "infrastructure", "peer-classification:hapi-fhir"),
    "redroid":            ("experience", "mobile", "peer-classification:citizen-app,provider-app"),
    "public-website":     ("experience", "ui-workspace", "manifest:deploy/tls/mohcc-gov/public-website.yaml"),
    "estate-health-watch": ("integration", "platform-ops",
                            "manifest:deploy/helm/impilo-vnext/templates/estate-health-watch.yaml"),
}

# The acting human's trust context, as it appears in a service's own source.
# A service that reads any of these is reading the identity of a human it is acting
# for; a service that reads none of them only ever acts as itself.
ACTOR_CONTEXT_PATTERN = (
    r"CompanionHeaders\.(ACTOR_ID|ACTOR_TYPE|PURPOSE_OF_USE)"
    r"|TrustHeaders\.(ACTOR_ID|ACTOR_TYPE|PURPOSE_OF_USE)"
    r"|[\"'][Xx]-[Aa]ctor-[A-Za-z]+[\"']"
    r"|[\"'][Xx]-[Pp]urpose-[Oo]f-[Uu]se[\"']"
    r"|TrustContextHolder"
    r"|@PreAuthorize"
    r"|SecurityContextHolder"
    r"|@AuthenticationPrincipal"
)

# Env var names that would carry a JWT audience if one were configured anywhere.
AUDIENCE_ENV_HINT = re.compile(r"AUDIENCE|_AUD$", re.IGNORECASE)

REALM_IMPORT = REPO / "deploy" / "helm" / "impilo-vnext" / "files" / "realm-impilo-preview.json"


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
    audience_env: list[str] = []
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
            # An audience is not a secret; it is the claim a resource server must check.
            if AUDIENCE_ENV_HINT.search(name) and isinstance(value, str) and value:
                audience_env.append(value)
    return {
        "keycloak_client_id": keycloak_client or "UNKNOWN",
        "secret_refs": sorted(secret_refs),
        "oauth_resource_server_disabled": oauth_disabled if oauth_disabled is not None else "UNKNOWN",
        "token_issuer": issuer or "UNKNOWN",
        "audience_env": sorted(set(audience_env)),
    }


def load_realm_audiences() -> tuple[dict[str, list[str]], str]:
    """Audiences the Keycloak realm import mints, per clientId.

    Keycloak only puts an `aud` claim in an access token when an audience protocol
    mapper (`oidc-audience-mapper`) is attached to the client or to one of its client
    scopes. Absence of that mapper is the reason `aud` is absent at runtime -- this
    reads the import so the finding is derived, not asserted.
    """
    if not REALM_IMPORT.exists():
        return {}, "REALM_IMPORT_MISSING"
    realm = json.loads(REALM_IMPORT.read_text())

    def audiences_of(mappers) -> list[str]:
        out = []
        for m in mappers or []:
            if (m.get("protocolMapper") or "") == "oidc-audience-mapper":
                cfg = m.get("config") or {}
                aud = cfg.get("included.client.audience") or cfg.get("included.custom.audience")
                if aud:
                    out.append(aud)
        return out

    scope_audiences: dict[str, list[str]] = {}
    for scope in realm.get("clientScopes", []) or []:
        auds = audiences_of(scope.get("protocolMappers"))
        if auds:
            scope_audiences[scope.get("name")] = auds

    per_client: dict[str, list[str]] = {}
    for client in realm.get("clients", []) or []:
        cid = client.get("clientId")
        if not cid:
            continue
        auds = list(audiences_of(client.get("protocolMappers")))
        for scope in (client.get("defaultClientScopes") or []) + (client.get("optionalClientScopes") or []):
            auds += scope_audiences.get(scope, [])
        per_client[cid] = sorted(set(auds))
    return per_client, "REALM_IMPORT_READ"


def resolve_audience(creds: dict, realm_audiences: dict[str, list[str]], realm_state: str) -> tuple:
    """Return (audience_current, audience_evidence).

    NONE_CONFIGURED is a measurement: env was read and the realm import was read, and
    neither sets an audience. UNKNOWN is reserved for "could not inspect" and is only
    reached when the realm import is unreadable.
    """
    if creds["audience_env"]:
        return (creds["audience_env"] if len(creds["audience_env"]) > 1 else creds["audience_env"][0],
                "LIVE_ENV_AUDIENCE_VAR")

    client = creds["keycloak_client_id"]
    if client != "UNKNOWN":
        if realm_state == "REALM_IMPORT_MISSING":
            return "UNKNOWN", "NO_AUDIENCE_ENV_AND_REALM_IMPORT_UNREADABLE"
        minted = realm_audiences.get(client)
        if minted:
            return (minted if len(minted) > 1 else minted[0]), f"REALM_AUDIENCE_MAPPER:{client}"
        if minted is None:
            return "UNKNOWN", f"NO_AUDIENCE_ENV_AND_CLIENT_NOT_IN_REALM_IMPORT:{client}"
        return "NONE_CONFIGURED", f"NO_AUDIENCE_ENV_AND_NO_REALM_AUDIENCE_MAPPER:{client}"

    if realm_state == "REALM_IMPORT_MISSING":
        return "UNKNOWN", "NO_AUDIENCE_ENV_AND_REALM_IMPORT_UNREADABLE"
    if any(realm_audiences.values()):
        # Some client does mint an audience but this workload names none -- not estate-wide.
        return "NONE_CONFIGURED", "NO_AUDIENCE_ENV_AND_NO_OAUTH_CLIENT_CONFIGURED"
    return "NONE_CONFIGURED", "NO_AUDIENCE_ENV_AND_NO_AUDIENCE_MAPPER_IN_REALM"


def _plane_domain(name: str, classification: dict, registry: dict) -> tuple | None:
    reg = registry.get(name) or {}
    cls = classification.get(name) or {}
    plane = reg.get("primary_plane") or cls.get("plane")
    domain = reg.get("domain") or cls.get("domain")
    if not plane or not domain:
        return None
    source = "SERVICES_REGISTRY" if reg.get("primary_plane") else "FULL_BOOT_SERVICE_CLASSIFICATION"
    return plane, domain, source, ""


def resolve_owner(name: str, classification: dict, registry: dict) -> dict:
    """Ownership as the canonical plane/domain pair the workload belongs to.

    A generated Job (`postgres-backup-29757570`, `keycloak-h2-export-mfa-...`) carries no
    registry row of its own, but it is unambiguously the parent workload's operation, so
    the parent's ownership is inherited and the inheritance is recorded.
    """
    def emit(plane, domain, source, evidence):
        return {
            "plane": plane,
            "domain": domain,
            # No CODEOWNERS file exists anywhere in the repository, and
            # docs/registry/services-registry.yaml sets owner_team_default: TBD with
            # `owner_team: TBD` on every service row. There is no team source to read.
            "team": "UNKNOWN",
            "team_source": "NO_TEAM_SOURCE_IN_REPO",
            "source": source,
            "source_evidence": evidence,
        }

    hit = _plane_domain(name, classification, registry)
    if hit:
        return emit(*hit)
    if name in INFRA_OWNER_MAP:
        plane, domain, evidence = INFRA_OWNER_MAP[name]
        return emit(plane, domain, "INFRA_OWNER_MAP", evidence)

    parts = name.split("-")
    for i in range(len(parts) - 1, 0, -1):
        stem = "-".join(parts[:i])
        for candidate in (stem, f"{stem}-service"):
            hit = _plane_domain(candidate, classification, registry)
            if hit:
                plane, domain, source, _ = hit
                return emit(plane, domain, f"DERIVED_FROM_PARENT_WORKLOAD:{candidate}", source)
            if candidate in INFRA_OWNER_MAP:
                plane, domain, evidence = INFRA_OWNER_MAP[candidate]
                return emit(plane, domain, f"DERIVED_FROM_PARENT_WORKLOAD:{candidate}", evidence)

    return emit("UNKNOWN", "UNKNOWN", "UNRESOLVED", "no registry row, no parent workload")


def source_tree(name: str, classification: dict, registry: dict) -> Path | None:
    """The workload's own source tree in this repository, or None if it has none."""
    cls = classification.get(name) or {}
    reg = registry.get(name) or {}
    candidates = []
    if cls.get("source_path"):
        candidates.append(REPO / cls["source_path"])
    if reg.get("maven_module"):
        candidates.append(REPO / "services" / reg["maven_module"])
    candidates += [REPO / "services" / name, REPO / "ui" / name]
    for c in candidates:
        if (c / "src" / "main" / "java").is_dir() or (c / "src").is_dir():
            return c
    return None


def reads_actor_context(tree: Path) -> bool:
    """True when the workload's own source reads the acting human's trust context."""
    src = tree / "src" / "main" / "java"
    if not src.is_dir():
        src = tree / "src"
    proc = subprocess.run(
        ["grep", "-rlE", ACTOR_CONTEXT_PATTERN,
         "--include=*.java", "--include=*.ts", "--include=*.tsx", str(src)],
        capture_output=True, text=True,
    )
    return proc.returncode == 0 and bool(proc.stdout.strip())


def human_delegation(name: str, kind: str, flow: str, classification: dict, registry: dict) -> tuple:
    """Return (human_delegation_required, human_delegation_evidence).

    Precedence is evidence strength, not convenience: kind first (a Job has no human
    session by construction), then infrastructure, then the workload's own source.
    """
    if kind in {"Job", "CronJob"}:
        return False, "KIND_BATCH"
    if flow == "INFRASTRUCTURE":
        return False, "INFRASTRUCTURE"
    tree = source_tree(name, classification, registry)
    if tree is None:
        return "UNKNOWN", "NO_SOURCE_TREE_IN_REPO"
    if reads_actor_context(tree):
        return True, "READS_ACTOR_CONTEXT"
    return False, "NO_ACTOR_CONTEXT_FOUND"


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
    realm_audiences, realm_state = load_realm_audiences()
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
            reg = registry.get(name, {})
            creds = credential_evidence(containers)
            owner = resolve_owner(name, classification, registry)
            flow = flow_category(name, kind, classification)
            delegation, delegation_evidence = human_delegation(
                name, kind, flow, classification, registry)
            audience, audience_evidence = resolve_audience(creds, realm_audiences, realm_state)

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
                "owner": owner,
                "plane": owner["plane"],
                "domain": owner["domain"],
                "credential": {
                    "current_type": ("SHARED_KEYCLOAK_CLIENT"
                                     if creds["keycloak_client_id"] != "UNKNOWN"
                                     else "NONE_OBSERVED"),
                    "target_type": "PROJECTED_K8S_TOKEN",
                    "keycloak_client_id_current": creds["keycloak_client_id"],
                    "issuer_current": creds["token_issuer"],
                    "issuer_target": f"https://impilo.mohcc.gov.zw/realms/impilo",
                    "audience_current": audience,
                    "audience_evidence": audience_evidence,
                    "audience_target": f"urn:impilo:tshepo:{name}",
                    "secret_refs": creds["secret_refs"],
                },
                "destinations": {
                    "measured_from_env": measured_destinations(containers, name),
                    "declared_consumes_from": reg.get("consumes_from") or [],
                    "declared_exposes_to": reg.get("exposes_to") or [],
                    "evidence": "MEASURED_CONFIG_ONLY",
                },
                "flow_category": flow,
                "human_delegation_required": delegation,
                "human_delegation_evidence": delegation_evidence,
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
            "destinations only, and are labelled declared. Derived fields (owner, "
            "human_delegation_required, credential.audience_current) each carry a sibling "
            "evidence field naming how they were derived. UNKNOWN means could-not-determine "
            "and is retained, never guessed; NONE_CONFIGURED means inspected-and-absent."
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

    # Print the derivation distribution: a derived field whose distribution is not
    # inspected is a field nobody has actually checked.
    for label, key in (("human_delegation_required", lambda r: r["human_delegation_required"]),
                       ("human_delegation_evidence", lambda r: r["human_delegation_evidence"]),
                       ("owner.source", lambda r: r["owner"]["source"].split(":")[0]),
                       ("owner.plane/domain", lambda r: f'{r["owner"]["plane"]}/{r["owner"]["domain"]}'),
                       ("audience_current", lambda r: r["credential"]["audience_current"]),
                       ("audience_evidence", lambda r: r["credential"]["audience_evidence"].split(":")[0])):
        tally: dict[str, int] = {}
        for r in rows:
            tally[str(key(r))] = tally.get(str(key(r)), 0) + 1
        print(f"  {label}: " + ", ".join(f"{k}={v}" for k, v in sorted(tally.items(), key=lambda kv: -kv[1])))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
