#!/usr/bin/env python3
"""Generate vNext service accountability matrix CSV from registry + preview truth."""
from __future__ import annotations

import csv
import json
import re
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
CLASS = ROOT / "config/full-boot-service-classification.yml"
REGISTRY = ROOT / "docs/registry/services-registry.yaml"
RUNTIME = ROOT / "deploy/helm/impilo-vnext/values-full-preview-runtime.generated.yaml"
PREVIEW = ROOT / "reports/full-boot/preview-generation.json"
BFF_GEN = ROOT / "scripts/full-boot/generate-full-preview-bff-downstream-env.mjs"
APP_REG = ROOT / "ui/one-ui-shell/src/lib/shell/app-registry.ts"
OUT_CSV = ROOT / "reports/product/vnext-service-accountability-matrix.csv"

# Parse BFF mappings and exclusions from generator source
_gen_src = BFF_GEN.read_text()
BFF_SERVICES = {
    m.group(1)
    for m in re.finditer(r'\["[A-Z_]+", "([^"]+)"\]', _gen_src)
}
BFF_EXCLUDED = set(re.findall(r'"([^"]+)":\s*\n\s*"', _gen_src.split("BFF_DOWNSTREAM_EXCLUDED")[1].split("};")[0])) if "BFF_DOWNSTREAM_EXCLUDED" in _gen_src else set()

SHELL_SLUGS = set(re.findall(r'serviceSlug:\s*"([^"]+)"', APP_REG.read_text()))

# Domain route hints from shell apps (href paths)
SHELL_ROUTES = re.findall(r'href:\s*"([^"]+)"', APP_REG.read_text())

EXTERNAL_DOCTRINE = {
    "banking-rails", "civil-registry-system", "dhis2", "external-elmis",
    "external-idp", "external-pacs-network", "lims", "mosip", "sms-whatsapp-gateway",
}

SLUG_TO_SERVICE = {
    "fundo": "learning-service",
    "vito": "vito-service",
    "msika": "msika-service",
    "nompilo": "guidance-service",  # + llm-orchestration
    "nhume": "dispatch-service",
    "madi": "madi-service",
    "ndila": "ndila-service",
}

def load_yaml(path: Path):
    return yaml.safe_load(path.read_text())

def main():
    classification = load_yaml(CLASS)["classifications"]
    registry = {s["id"]: s for s in load_yaml(REGISTRY).get("services", [])}
    runtime = load_yaml(RUNTIME).get("fullBootServices", {})
    preview = json.loads(PREVIEW.read_text())

    rows = []
    for entry in classification:
        sid = entry["id"]
        reg = registry.get(sid, {})
        lane = entry.get("deployment_lane", "")
        plane = entry.get("plane") or reg.get("primary_plane", "")
        domain = entry.get("domain") or reg.get("domain", "")
        product = (reg.get("product_names") or [sid.replace("-", " ").title()])[0]

        runtime_required = lane in (
            "runtime_k8s_microservice", "runtime_official", "runtime_dedicated"
        )
        if lane == "doctrine_only":
            runtime_required = False
        if lane == "build_validate_only":
            runtime_required = False

        is_external = sid in EXTERNAL_DOCTRINE
        internal_vnext = "external dependency (contract only)" if is_external else "internal vNext"

        helm_svc = runtime.get(sid, {})
        deployed = helm_svc.get("enabled", False) if runtime_required else (
            "N/A" if lane in ("build_validate_only", "doctrine_only") else False
        )
        running = deployed if preview["full_stack"]["ready"] == preview["full_stack"]["total"] and deployed else (
            "partial" if deployed else ("N/A" if not runtime_required else "NO")
        )

        # Shell surfacing
        slug_match = sid.replace("-service", "").replace("-adapter", "")
        shell_surfaced = (
            sid in ("one-ui-shell", "experience-bff")
            or sid.replace("-service", "") in SHELL_SLUGS
            or any(slug_match in r for r in SHELL_ROUTES)
            or entry.get("frontend_surface_expected") and lane == "runtime_k8s_microservice"
        )
        if lane == "build_validate_only" and sid.endswith("-web") or sid in ("portal", "ehr", "ops-console"):
            shell_surfaced = "absorbed into one-ui-shell"
        elif lane == "build_validate_only" and sid in ("citizen-app", "provider-app"):
            shell_surfaced = "mobile artifact lane"

        mobile = entry.get("mobile_surface_expected", False)
        if sid in ("citizen-app", "provider-app"):
            mobile = "YES (mobile app)"
        elif not mobile:
            mobile = "N/A"
        else:
            mobile = "partial — see MOBILE_PARITY_MATRIX"

        bff = "YES" if sid in BFF_SERVICES else (
            "EXCLUDED" if sid in BFF_EXCLUDED else (
                "N/A" if lane != "runtime_k8s_microservice" else "NO"
            )
        )

        kc = "browser client" if sid == "one-ui-shell" else (
            "service account (impilo-bff)" if sid == "experience-bff" else (
                "bearer/API via Envoy+TSHEPO" if runtime_required else "N/A"
            )
        )
        kc_status = "preview: OAuth bypass global; production: policy-enforced"

        fw = reg.get("frontend_wiring_status", "unknown-or-partial")
        maturity = "running" if running in (True, "partial") else (
            "blocked: not deployed" if runtime_required and not deployed else
            "supporting component" if lane == "build_validate_only" else
            "external contract reference" if is_external else "unknown"
        )

        blockers = []
        if runtime_required and bff == "NO":
            blockers.append("BFF downstream URL not generated")
        if runtime_required and fw == "unknown-or-partial":
            blockers.append("frontend wiring partial/unknown")
        if sid == "wellness-service":
            blockers.append("deprecated — SoR migrated to simba-service")
        if preview.get("single_public_stack") and lane == "runtime_k8s_microservice" and not deployed:
            blockers.append("defect: not enabled in helm runtime")

        rows.append({
            "service_name": product,
            "service_slug": sid,
            "plane_domain": f"{plane}/{domain}",
            "product_role": "; ".join(reg.get("system_of_record_for") or []) or entry.get("component_type", ""),
            "internal_or_external": internal_vnext,
            "runtime_deployment_required": "YES" if runtime_required else f"NO ({lane})",
            "deployed_preview": "YES" if deployed is True else ("NO" if deployed is False else deployed),
            "running_preview": "YES" if running is True else str(running),
            "surfaced_shell": str(shell_surfaced),
            "surfaced_mobile": str(mobile),
            "bff_downstream": bff,
            "keycloak_trust": kc,
            "required_roles": "role-aware per route — see shell app-registry",
            "required_context": "Health ID; Provider ID; Facility; Workspace; Purpose of Use",
            "main_routes": "see FRONTEND_ROUTE_INVENTORY / app-registry",
            "main_apis": f"contracts/openapi; port {reg.get('default_http_port', helm_svc.get('port', 'N/A'))}",
            "events_topics": "event_outbox per service doctrine",
            "dependencies": "; ".join(reg.get("consumes_from") or [])[:120],
            "health_endpoint": "/actuator/health" if runtime_required else "N/A",
            "test_scenarios_required": "UAT workbook row required per service",
            "current_maturity": maturity,
            "current_blockers": "; ".join(blockers) or "none",
            "required_fixes": "; ".join(blockers) or "lower walkthrough priority only",
            "preview_testability_dod": "logged-in click-flow + API-backed content + role/context test",
        })

    OUT_CSV.parent.mkdir(parents=True, exist_ok=True)
    with OUT_CSV.open("w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)

    print(f"Wrote {len(rows)} rows to {OUT_CSV}")

if __name__ == "__main__":
    main()
