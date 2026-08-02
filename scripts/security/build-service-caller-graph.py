#!/usr/bin/env python3
"""Build the service-to-service caller graph, and invert it.

Checkpoint 7's gate. Every row of the Checkpoint 1 bypass inventory records
`Known callers: BFF + peers (unenumerated)` with `Consumer evidence: PARTIAL`. No cohort may
enter enforcement while that is true: turning on authentication for a service whose callers are
unknown is how a preview estate discovers a caller by breaking it.

Three independent edge sources, kept separate so a claim can be traced to how it was found:

  SOURCE_DEFAULT  a `${X_BASE_URL:http://localhost:PORT}` default in the caller's own
                  application.yml. The localhost port resolves to a service through the port map.
                  This is the strongest source-level signal: it encodes the intended peer even
                  when the environment does not override it.
  SOURCE_DIRECT   a literal `http://<service>:<port>` in the caller's source.
  RUNTIME_ENV     a URL in the caller's live container environment.

A service reached by none of the three is a candidate for the first cohort -- but "no edge found"
is NOT "no caller". Kafka topics, hardcoded URLs built at runtime, and anything addressed through
a gateway are all invisible here. The output therefore states confidence rather than certainty,
and cohort selection must still confirm against live traffic.

Usage:
  python3 scripts/security/build-service-caller-graph.py \
      --output reports/estate/service-caller-graph.json
"""
from __future__ import annotations

import argparse
import json
import re
from collections import defaultdict
from pathlib import Path

import yaml

REPO = Path(__file__).resolve().parents[2]

LOCALHOST_URL = re.compile(r"\$\{[A-Z0-9_]*URL[A-Z0-9_]*:https?://localhost:(\d+)", re.I)
DIRECT_URL = re.compile(r"https?://([a-z0-9][a-z0-9-]+):(\d+)")

# Infrastructure a service may address that is not an Impilo service.
INFRA_PORTS = {
    5432: "postgres", 6379: "redis", 9092: "kafka", 9000: "minio", 9001: "minio",
    8080: "keycloak", 10000: "envoy", 8181: "opa", 7880: "livekit", 8042: "orthanc",
}


def port_map() -> dict[int, str]:
    """port -> service, from the generated runtime values (the authoritative allocation)."""
    ports: dict[int, str] = {}
    p = REPO / "deploy/helm/impilo-vnext/values-full-preview-runtime.generated.yaml"
    if p.exists():
        doc = yaml.safe_load(p.read_text()) or {}
        for name, svc in (doc.get("fullBootServices") or {}).items():
            if svc and svc.get("port"):
                ports[int(svc["port"])] = name
    # Workloads rendered by their own template, absent from fullBootServices.
    ports.setdefault(8160, "experience-bff")
    ports.setdefault(3000, "one-ui-shell")
    ports.setdefault(8090, "hapi-fhir")
    return ports


def service_dirs() -> list[Path]:
    return sorted(d for d in (REPO / "services").iterdir()
                  if d.is_dir() and (d / "src" / "main").is_dir())


def scan_source(svc: Path, ports: dict[int, str]) -> tuple[set[str], set[str]]:
    """Return (edges_from_localhost_defaults, edges_from_direct_urls)."""
    by_default: set[str] = set()
    direct: set[str] = set()
    for f in list((svc / "src" / "main" / "resources").rglob("*.yml")) + \
             list((svc / "src" / "main" / "resources").rglob("*.yaml")) + \
             list((svc / "src" / "main" / "java").rglob("*.java")):
        try:
            text = f.read_text(errors="ignore")
        except Exception:
            continue
        for port in LOCALHOST_URL.findall(text):
            callee = ports.get(int(port)) or INFRA_PORTS.get(int(port))
            if callee and callee != svc.name:
                by_default.add(callee)
        for host, port in DIRECT_URL.findall(text):
            host = host.lower()
            if host in ("localhost", "127", "host"):
                continue
            if host == svc.name:
                continue
            # Only count a host that is a known workload; anything else is external.
            if host in ports.values() or host in INFRA_PORTS.values():
                direct.add(host)
    return by_default, direct


def runtime_edges() -> dict[str, set[str]]:
    """Live-environment edges, reused from the workload identity registry."""
    out: dict[str, set[str]] = defaultdict(set)
    p = REPO / "docs/security/trust-audit/checkpoint-4/workload-identity-registry.yaml"
    if not p.exists():
        return out
    doc = yaml.safe_load(p.read_text()) or {}
    for row in doc.get("workloads", []) or []:
        for dest in (row.get("destinations") or {}).get("measured_from_env") or []:
            if dest != row["workload"]:
                out[row["workload"]].add(dest)
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--output", required=True)
    args = ap.parse_args()

    ports = port_map()
    runtime = runtime_edges()

    edges: dict[str, dict[str, set[str]]] = defaultdict(lambda: defaultdict(set))
    for svc in service_dirs():
        by_default, direct = scan_source(svc, ports)
        for c in by_default:
            edges[svc.name][c].add("SOURCE_DEFAULT")
        for c in direct:
            edges[svc.name][c].add("SOURCE_DIRECT")
    for caller, callees in runtime.items():
        for c in callees:
            edges[caller][c].add("RUNTIME_ENV")

    known = set(ports.values()) | set(INFRA_PORTS.values()) | set(edges)
    inbound: dict[str, dict[str, list[str]]] = {s: {} for s in sorted(known)}
    for caller, callees in edges.items():
        for callee, how in callees.items():
            inbound.setdefault(callee, {})[caller] = sorted(how)

    rows = []
    for svc in sorted(inbound):
        callers = inbound[svc]
        rows.append({
            "service": svc,
            "inbound_caller_count": len(callers),
            "inbound_callers": {k: v for k, v in sorted(callers.items())},
            "outbound_callee_count": len(edges.get(svc, {})),
            # A service nothing appears to call is the low-blast-radius end of the estate.
            # "No edge found" is not "no caller" -- Kafka and runtime-built URLs are invisible here.
            "cohort_signal": ("NO_OBSERVED_CALLER" if not callers
                              else "SINGLE_CALLER" if len(callers) == 1
                              else "MULTI_CALLER"),
        })

    doc = {
        "generated_by": "scripts/security/build-service-caller-graph.py",
        "edge_sources": ["SOURCE_DEFAULT", "SOURCE_DIRECT", "RUNTIME_ENV"],
        "caveat": ("Absence of an edge is not proof of no caller. Kafka topics, runtime-built "
                   "URLs and gateway-mediated calls are not visible to this method."),
        "service_count": len(rows),
        "edge_count": sum(len(v) for v in edges.values()),
        "services": rows,
    }
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(doc, indent=2) + "\n")

    sig = defaultdict(int)
    for r in rows:
        sig[r["cohort_signal"]] += 1
    print(f"services {len(rows)}  edges {doc['edge_count']}")
    for k in ("NO_OBSERVED_CALLER", "SINGLE_CALLER", "MULTI_CALLER"):
        print(f"  {k:20s} {sig[k]}")
    print(f"\nwrote {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
