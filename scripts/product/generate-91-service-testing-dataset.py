#!/usr/bin/env python3
"""Generate vnext-91-service-testing-dataset.csv — one+ concrete test per registry service."""
from __future__ import annotations

import csv
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
REGISTRY = ROOT / "docs/registry/services-registry.yaml"
OUT = ROOT / "reports/product/vnext-91-service-testing-dataset.csv"

# Madi-level navigation specificity per service (extend over time)
TESTS: dict[str, list[dict]] = {
    "madi-service": [
        {
            "test_id": "MADI-001",
            "role": "Blood bank officer",
            "user_story": "As a blood bank officer I need the programme dashboard so that I can monitor stock and demand.",
            "navigation": "/madi/dashboard",
            "expected": "KPI tiles and 30-day forecast table render from GET /internal/v1/madi/dashboard",
            "evidence": "Screenshot + network 200 on madi dashboard API",
        },
        {
            "test_id": "MADI-002",
            "role": "Clinician",
            "user_story": "As a clinician I need to issue blood for an order so that transfusion can proceed.",
            "navigation": "/madi/orders → select order → crossmatch → issue",
            "expected": "Order moves to ISSUED; unit reserved count updates",
            "evidence": "Order detail shows status ISSUED; API 200",
        },
    ],
    "vito-service": [
        {
            "test_id": "VITO-001",
            "role": "Registry clerk",
            "user_story": "As a clerk I need to register a client so that they receive a Health ID.",
            "navigation": "/id-services → Register client → submit",
            "expected": "Health ID displayed; VITO client row created",
            "evidence": "Search returns new client; BFF /internal/v1/identity/* 201",
        },
    ],
    "varapi-service": [
        {
            "test_id": "VARAPI-001",
            "role": "Provider",
            "user_story": "As a provider I need my Provider ID linked so that WORK tab activates.",
            "navigation": "Login → WORK tab",
            "expected": "Session shows providerPublicId; active work assignment visible",
            "evidence": "BFF session contract; workforce assignment ACTIVE",
        },
    ],
}

# Default test template for services without bespoke rows yet
def default_test(sid: str, name: str, plane: str, nav: str, role: str) -> dict:
    return {
        "test_id": f"{sid.upper().replace('-', '_')[:20]}-001",
        "role": role,
        "user_story": f"As a {role} I need {name} capability so that the Health OS workflow is complete.",
        "navigation": nav,
        "expected": f"API-backed content from {sid} via BFF or documented honest blocked state",
        "evidence": f"Screenshot + BFF/downstream 200 or labelled Blocked for {sid}",
    }


SURFACING_NAV: dict[str, tuple[str, str, str]] = {
    # sid -> (navigation, role, surface_type)
    "ai-model-registry-service": ("/admin/ai-models or /data-intelligence/models", "Platform admin", "admin route"),
    "analytics-pipeline-service": ("/data-intelligence/pipelines", "Data steward", "domain hub"),
    "asset-registry-service": ("/inventory/assets", "Supply officer", "domain hub"),
    "audit-ledger-service": ("/admin/trust/audit-ledger", "Trust admin", "admin route"),
    "booking-service": ("/appointments/book", "Citizen", "MY HEALTH route"),
    "butano-fhir": ("N/A — infra FHIR layer via FHIR_BASE_URL", "Interop", "supporting via butano/hapi"),
    "butano-service": ("/ehr/[patientId]/summary", "Clinician", "clinical hub"),
    "campaigns-service": ("/public-health/campaigns", "PH officer", "domain hub"),
    "card-print-agent": ("/home/credentials/print", "Clerk", "task workflow"),
    "channels-service": ("/communication/channels", "Comms officer", "domain hub"),
    "clinical-knowledge-platform-service": ("/clinical/knowledge", "Clinician", "clinical hub"),
    "community-service": ("/communities", "Citizen", "MY LIFE route"),
    "connector-fhir-adapter": ("/admin/integration-status", "Integration admin", "admin route"),
    "costing-engine-service": ("/finance/tariffs", "Finance officer", "enterprise route"),
    "coverage-service": ("/finance/coverage/check", "Clerk", "enterprise route"),
    "credential-verification-service": ("/verify/credential", "Reviewer", "self-service route"),
    "data-access-governance-service": ("/dags", "Data governor", "admin route"),
    "data-governance-service": ("/data-intelligence/governance", "Data steward", "domain hub"),
    "data-ingestion-service": ("/data-intelligence/ingestion", "Data engineer", "domain hub"),
    "data-pipeline-service": ("/data-intelligence/pipelines", "Data steward", "domain hub"),
    "data-warehouse-service": ("/data-intelligence/warehouse", "Analyst", "domain hub"),
    "developer-portal-service": ("/developer", "Developer", "admin route"),
    "dispatch-service": ("/nhume/dispatch", "Dispatcher", "operations route"),
    "document-service": ("/home/documents", "Any authenticated", "documents route"),
    "experience-bff": ("/health/version", "Reviewer", "orchestration — API only"),
    "fhir-gateway-service": ("N/A — Envoy/FHIR ingress", "Interop", "infra"),
    "forms-service": ("/clinical/forms", "Clinician", "clinical hub"),
    "general-ledger-service": ("/finance/ledger", "Finance officer", "enterprise route"),
    "guidance-service": ("/ask", "Any user", "Nompilo route"),
    "hr-payroll-service": ("/enterprise/hr-payroll", "HR admin", "enterprise route"),
    "identity-assurance-service": ("/settings/security/assurance", "Citizen", "MY PROFESSIONAL route"),
    "indawo-service": ("/public-health/site-registry", "PH officer", "domain hub"),
    "inpatient-service": ("/clinical/inpatient/beds", "Ward nurse", "clinical hub"),
    "integration-hub": ("/admin/integration-status", "Integration admin", "admin route"),
    "inventory-elmis-adapter": ("/inventory/sync", "Supply officer", "domain hub — adapter"),
    "inventory-service": ("/inventory", "Supply officer", "domain hub"),
    "iot-ingestion-service": ("/madi/blood-bank/fridges", "Blood bank officer", "via Madi IoT"),
    "jobs-service": ("/operations/jobs", "Ops admin", "admin route"),
    "landela-adapter-service": ("/clinical/documents/landela", "Clinician", "adapter route"),
    "learning-service": ("/learning", "Learner", "MY LIFE route"),
    "live-service": ("/live/discover", "Citizen", "MY LIFE route"),
    "llm-orchestration-service": ("/ask (Nompilo composer)", "Any user", "via guidance"),
    "msika-apps-service": ("/marketplace/apps", "Vendor", "marketplace route"),
    "msika-flow-service": ("/marketplace", "Buyer", "marketplace route"),
    "msika-service": ("/marketplace/catalog", "Procurement", "marketplace route"),
    "mushe-wallet-service": ("/wallet", "Citizen", "MY LIFE route"),
    "mushex-service": ("/finance/payments", "Finance officer", "enterprise route"),
    "mvumo-service": ("/settings/devices", "User", "trust/devices route"),
    "national-data-repository-service": ("/data-intelligence/ndr", "Analyst", "domain hub"),
    "ndila-service": ("/public-health/maps", "PH officer", "domain hub"),
    "ndr-service": ("/data-intelligence/ndr/query", "Analyst", "domain hub"),
    "nhume-service": ("/nhume", "Dispatcher", "operations route"),
    "notification-service": ("/communication/notifications", "Any user", "notifications route"),
    "observability-service": ("/operations/observability", "Ops admin", "admin route"),
    "offline-edge-service": ("Mobile provider offline screens", "Provider", "mobile surface"),
    "offline-sync-service": ("/operations/offline-sync", "Ops admin", "admin route"),
    "oros-service": ("/lab/orders", "Lab tech", "clinical hub"),
    "pacs-adapter-service": ("/clinical/imaging/worklist", "Radiologist", "clinical hub"),
    "pct-service": ("/queue → /ehr/[cpid]", "Clinician", "WORK/clinical route"),
    "pharmacy-elmis-adapter": ("/pharmacy/sync", "Pharmacist", "adapter route"),
    "pharmacy-service": ("/pharmacy", "Pharmacist", "clinical hub"),
    "procurement-service": ("/enterprise/procurement", "Procurement officer", "enterprise route"),
    "product-registry-service": ("/marketplace/product-registry", "Catalog curator", "marketplace route"),
    "referral-service": ("/clinical/referrals", "Clinician", "clinical hub"),
    "reporting-service": ("/data-intelligence/reports", "Analyst", "domain hub"),
    "rtc-gateway-service": ("/telemedicine/session", "Provider", "telemedicine — may be blocked"),
    "rules-service": ("/clinical/rules", "Clinical admin", "clinical hub"),
    "scheduling-service": ("/appointments/schedule", "Clerk", "scheduling route"),
    "schema-registry-service": ("/admin/schema-registry", "Platform admin", "admin route"),
    "search-service": ("/search", "Any user", "global search"),
    "security-hardening-service": ("/admin/security-hardening", "Security admin", "admin route"),
    "share-slip-service": ("/finance/share-slip", "Finance clerk", "enterprise route"),
    "simba-service": ("/monitoring/devices", "Citizen", "MY HEALTH route"),
    "support-service": ("/support", "Any user", "support route"),
    "surveillance-service": ("/public-health/surveillance", "PH officer", "domain hub"),
    "tshepo-audit-service": ("/admin/trust/audit", "Trust admin", "admin route"),
    "tshepo-authz-service": ("N/A — Envoy ext_authz", "Platform", "trust infra"),
    "tshepo-consent-service": ("/settings/consent", "Citizen", "MY LIFE route"),
    "tshepo-identity-service": ("/admin/trust/identity", "Trust admin", "admin route"),
    "tshepo-keys-service": ("/admin/trust/keys", "Trust admin", "admin route"),
    "tshepo-offline-service": ("/clinical/tools/offline", "Provider", "clinical tools"),
    "tshepo-service": ("N/A — legacy monolith; decomposed TSHEPO services", "Platform", "supporting"),
    "tuso-service": ("/registry/facilities", "Facility manager", "registry route"),
    "ubomi-service": ("/ubomi", "Civil registrar", "registry route"),
    "wellness-service": ("N/A — deprecated; test via simba-service /monitoring", "Citizen", "blocked — use Simba"),
    "workforce-governance-service": ("WORK tab + /enterprise/workforce", "Provider/Admin", "WORK route"),
    "zibo-service": ("/registry/terminology", "Terminology curator", "registry route"),
    "one-ui-shell": ("/home", "Any user", "shell landing"),
}


def acceptance_criteria(sid: str) -> str:
    return (
        f"1) {sid} pod Running in impilo-full-preview; "
        "2) reachable via BFF or documented path; "
        "3) logged-in navigation completes; "
        "4) API returns data or honest Blocked label; "
        "5) evidence captured"
    )


def main():
    reg = yaml.safe_load(REGISTRY.read_text())
    services = [e for e in reg["services"] if e.get("id")]
    rows = []
    for svc in services:
        sid = svc["id"]
        name = (svc.get("product_names") or [sid])[0]
        plane = svc.get("primary_plane", "")
        custom = TESTS.get(sid)
        nav, role, _ = SURFACING_NAV.get(
            sid, (f"/operations/service/{sid.replace('-service', '')}", "Reviewer", "domain hub")
        )
        tests = custom or [default_test(sid, name, plane, nav, role)]
        for t in tests:
            rows.append({
                "service_id": sid,
                "service_name": name,
                "plane": plane,
                "test_id": t["test_id"],
                "role": t["role"],
                "user_story": t["user_story"],
                "acceptance_criteria": acceptance_criteria(sid),
                "definition_of_done": "Built, deployed, running, surfaced, testable, evidenced",
                "given_when_then": (
                    f"Given logged-in {t['role']} with seeded context "
                    f"When user navigates {t['navigation']} "
                    f"Then {t['expected']}"
                ),
                "navigation_path": t["navigation"],
                "expected_result": t["expected"],
                "api_evidence": t["evidence"],
                "pass_fail_rule": "PASS if API-backed content or honest Blocked; FAIL if dead click/blank/silent stub",
                "test_data_required": "Sovereign seed: scripts/deploy/seed-full-preview-sovereign-data.sh",
                "credential_context": "superadmin@impilo.gov.zw or role-specific seeded user; facility 1",
                "blocker_if_not_testable": "" if sid != "wellness-service" else "Use simba-service; wellness deprecated",
            })
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)
    print(f"Wrote {len(rows)} test rows for {len(services)} services → {OUT}")


if __name__ == "__main__":
    main()
