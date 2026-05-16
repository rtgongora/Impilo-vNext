#!/usr/bin/env python3
"""
Core Transaction Doctrine repo-wide compliance audit.

This gate is intentionally machine-checkable and additive:
- verifies doctrine anchors and canonical contracts exist
- verifies Experience BFF exposes dual-surface core transaction routes
- verifies major sovereign emitters dual-emit to core.transaction.events
- verifies CI has runtime + load + doctrine jobs

Outputs:
- docs/reports/core-transaction-compliance-report.json
- docs/reports/core-transaction-compliance-report.md
"""

from __future__ import annotations

import json
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import List
import yaml


@dataclass
class CheckResult:
    id: str
    description: str
    status: str
    details: str


REQUIRED_DOCS = [
    "docs/doctrine/CORE_TRANSACTION_DOCTRINE.md",
    "docs/doctrine/CORE_TRANSACTION_STATE_MACHINE.md",
    "docs/doctrine/CORE_TRANSACTION_ACCEPTANCE_CRITERIA.md",
    "docs/architecture/core-transaction-event-model.md",
    "docs/architecture/core-transaction-bff-composition.md",
]

REQUIRED_CONTRACTS = [
    "contracts/core-transaction.ts",
    "contracts/openapi/core-transaction-openapi.yaml",
    "contracts/openapi/experience-bff.openapi.yaml",
    "contracts/asyncapi/core-transaction-events.asyncapi.yaml",
]

CHECKLIST_FILE = "docs/architecture/core-transaction-service-compliance.yaml"
ARCH_REGISTRY_FILE = "docs/architecture/services-registry.yaml"

CHECKLIST_KEYS = [
    "q01_transaction_types",
    "q02_lifecycle_stages",
    "q03_actor_served",
    "q04_source_of_truth_plane",
    "q05_source_of_truth_service",
    "q06_states_create_read_update_close",
    "q07_emitted_events",
    "q08_permission_or_consent",
    "q09_audit_event",
    "q10_ui_surface",
    "q11_bff_or_contract",
    "q12_tests",
    "q13_analytics_reporting_output",
    "q14_offline_federated_behavior",
    "q15_failure_mode_behavior",
    "q16_improves_access_delivery_continuity_accountability_intelligence",
    "q17_duplicate_domain_truth_check",
    "q18_person_centered_continuity",
    "q19_provider_burden_impact",
    "q20_health_system_impact",
]

STRICT_CORE_SERVICES = {
    "vito-service",
    "varapi-service",
    "tuso-service",
    "tshepo-authz-service",
    "butano-service",
    "zibo-service",
    "msika-service",
    "msika-flow-service",
    "mushex-service",
    "costing-engine-service",
    "ubomi-service",
    "indawo-service",
    "learning-service",
    "guidance-service",
    "pct-service",
    "oros-service",
    "pharmacy-service",
    "experience-bff",
    "workflow-service",
}

EMITTERS = [
    (
        "pct-service",
        "services/pct-service/src/main/java/zw/gov/mohcc/impilo/pct/events/OutboxPublisher.java",
        "services/pct-service/src/main/resources/application.yml",
    ),
    (
        "costing-engine-service",
        "services/costing-engine-service/src/main/java/zw/gov/mohcc/impilo/costa/kafka/OutboxPublisher.java",
        "services/costing-engine-service/src/main/resources/application.yml",
    ),
    (
        "oros-service",
        "services/oros-service/src/main/java/zw/gov/mohcc/impilo/oros/events/OutboxPublisher.java",
        "services/oros-service/src/main/resources/application.yml",
    ),
    (
        "pharmacy-service",
        "services/pharmacy-service/src/main/java/zw/gov/mohcc/impilo/pharmacy/events/OutboxPublisher.java",
        "services/pharmacy-service/src/main/resources/application.yml",
    ),
    (
        "msika-flow-service",
        "services/msika-flow-service/src/main/java/zw/gov/mohcc/impilo/msikaflow/events/OutboxPublisher.java",
        "services/msika-flow-service/src/main/resources/application.yml",
    ),
    (
        "mushex-service",
        "services/mushex-service/src/main/java/zw/gov/mohcc/impilo/mushex/kafka/OutboxPublisher.java",
        "services/mushex-service/src/main/resources/application.yml",
    ),
]


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def exists_check(root: Path, relpaths: List[str], check_id: str, description: str) -> CheckResult:
    missing = [p for p in relpaths if not (root / p).exists()]
    if missing:
        return CheckResult(check_id, description, "fail", "Missing: " + ", ".join(missing))
    return CheckResult(check_id, description, "pass", "All required files exist")


def bff_dual_surface_check(root: Path) -> CheckResult:
    controller = root / "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/CoreTransactionController.java"
    if not controller.exists():
        return CheckResult("bff-dual-surface", "Experience BFF dual-surface routes", "fail", f"Missing {controller}")
    text = read_text(controller)
    has_internal = "/internal/v1/core-transactions" in text
    has_alias = "/experience/core-transactions" in text
    if has_internal and has_alias:
        return CheckResult("bff-dual-surface", "Experience BFF dual-surface routes", "pass", "Internal + alias routes found")
    return CheckResult(
        "bff-dual-surface",
        "Experience BFF dual-surface routes",
        "fail",
        "CoreTransactionController is missing internal or alias route family",
    )


def emitter_dual_emit_checks(root: Path) -> List[CheckResult]:
    results: List[CheckResult] = []
    for service, publisher_rel, app_rel in EMITTERS:
        publisher = root / publisher_rel
        app = root / app_rel
        cid = f"dual-emit-{service}"
        if not publisher.exists() or not app.exists():
            results.append(
                CheckResult(cid, f"{service} dual-emits core.transaction.events", "fail", "Publisher or application.yml missing")
            )
            continue
        ptxt = read_text(publisher)
        atxt = read_text(app)
        has_topic = "core.transaction.events" in ptxt
        has_toggle = "dual-emit-core-transaction-enabled" in atxt
        has_topic_cfg = "core-transaction-topic" in atxt
        if has_topic and has_toggle and has_topic_cfg:
            results.append(CheckResult(cid, f"{service} dual-emits core.transaction.events", "pass", "Publisher + config present"))
        else:
            details = []
            if not has_topic:
                details.append("publisher missing topic mapping")
            if not has_toggle:
                details.append("application.yml missing dual-emit toggle")
            if not has_topic_cfg:
                details.append("application.yml missing core-transaction topic setting")
            results.append(CheckResult(cid, f"{service} dual-emits core.transaction.events", "fail", "; ".join(details)))
    return results


def ci_gate_check(root: Path) -> CheckResult:
    workflow = root / ".github/workflows/ci.yml"
    if not workflow.exists():
        return CheckResult("ci-core-transaction-jobs", "CI includes core transaction gates", "fail", "ci.yml missing")
    text = read_text(workflow)
    required_markers = [
        "core-transaction-runtime:",
        "core-transaction-load-baseline:",
        "core-transaction-doctrine-compliance:",
    ]
    missing = [m for m in required_markers if m not in text]
    if missing:
        return CheckResult("ci-core-transaction-jobs", "CI includes core transaction gates", "fail", "Missing jobs: " + ", ".join(missing))
    return CheckResult("ci-core-transaction-jobs", "CI includes core transaction gates", "pass", "All core transaction jobs present")


def _is_empty(value) -> bool:
    if value is None:
        return True
    if isinstance(value, str):
        return value.strip() == ""
    if isinstance(value, list):
        return len(value) == 0
    return False


def checklist_coverage_check(root: Path) -> CheckResult:
    architecture_path = root / ARCH_REGISTRY_FILE
    checklist_path = root / CHECKLIST_FILE
    if not architecture_path.exists():
        return CheckResult("checklist-architecture-registry", "Architecture registry exists for checklist cross-check", "fail", f"Missing {ARCH_REGISTRY_FILE}")
    if not checklist_path.exists():
        return CheckResult("checklist-file", "Core transaction checklist matrix exists", "fail", f"Missing {CHECKLIST_FILE}")

    architecture = yaml.safe_load(architecture_path.read_text(encoding="utf-8")) or {}
    checklist = yaml.safe_load(checklist_path.read_text(encoding="utf-8")) or {}

    services = architecture.get("services", [])
    entries = checklist.get("services", [])
    if not isinstance(services, list) or not isinstance(entries, list):
        return CheckResult("checklist-file-shape", "Checklist YAML has expected structure", "fail", "services arrays missing in architecture/checklist YAML")

    eligible_types = {"backend_service", "adapter", "worker", "frontend_app", "mobile_app"}
    eligible_impl = {"live", "skeleton", "new"}
    expected = {
        s.get("canonical_name")
        for s in services
        if isinstance(s, dict)
        and s.get("canonical_name")
        and s.get("type") in eligible_types
        and s.get("implementation_status") in eligible_impl
    }

    indexed = {}
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        name = entry.get("service")
        if name:
            indexed[name] = entry

    missing_services = sorted(expected - set(indexed.keys()))
    if missing_services:
        return CheckResult(
            "checklist-service-coverage",
            "Checklist coverage for all active services",
            "fail",
            "Missing checklist entries: " + ", ".join(missing_services[:20]) + (" ..." if len(missing_services) > 20 else ""),
        )

    missing_fields = []
    weak_event_fields = []
    for name in sorted(expected):
        entry = indexed[name]
        applies = bool(entry.get("applies", True))
        coverage = str(entry.get("coverage", "")).strip().lower()
        if not applies or coverage == "not_applicable":
            continue
        for key in CHECKLIST_KEYS:
            if key not in entry or _is_empty(entry.get(key)):
                missing_fields.append(f"{name}:{key}")

        if name in STRICT_CORE_SERVICES:
            emitted = entry.get("q07_emitted_events")
            if isinstance(emitted, list):
                lowered = [str(v).strip().lower() for v in emitted]
                if "none" in lowered or len(lowered) == 0:
                    weak_event_fields.append(name)
            elif _is_empty(emitted):
                weak_event_fields.append(name)

    if missing_fields:
        return CheckResult(
            "checklist-fields",
            "Checklist entries include all 20 acceptance mappings",
            "fail",
            "Missing field mappings: " + ", ".join(missing_fields[:20]) + (" ..." if len(missing_fields) > 20 else ""),
        )
    if weak_event_fields:
        return CheckResult(
            "checklist-core-events",
            "Core services declare emitted transaction-relevant events",
            "fail",
            "Core services with weak q07_emitted_events: " + ", ".join(sorted(set(weak_event_fields))),
        )

    return CheckResult(
        "checklist-complete",
        "Checklist matrix is complete and strict-core services have event mappings",
        "pass",
        f"Validated {len(expected)} service checklist entries",
    )


def write_reports(root: Path, checks: List[CheckResult]) -> None:
    reports_dir = root / "docs/reports"
    reports_dir.mkdir(parents=True, exist_ok=True)

    summary = {
        "total": len(checks),
        "passed": sum(1 for c in checks if c.status == "pass"),
        "failed": sum(1 for c in checks if c.status == "fail"),
    }

    json_payload = {"summary": summary, "checks": [asdict(c) for c in checks]}
    (reports_dir / "core-transaction-compliance-report.json").write_text(
        json.dumps(json_payload, indent=2),
        encoding="utf-8",
    )

    lines = [
        "# Core Transaction Compliance Report",
        "",
        f"- Total checks: **{summary['total']}**",
        f"- Passed: **{summary['passed']}**",
        f"- Failed: **{summary['failed']}**",
        "",
        "## Check Results",
        "",
    ]
    for c in checks:
        status_icon = "PASS" if c.status == "pass" else "FAIL"
        lines.append(f"- **{status_icon}** `{c.id}` - {c.description} ({c.details})")
    lines.append("")
    (reports_dir / "core-transaction-compliance-report.md").write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    checks: List[CheckResult] = [
        exists_check(root, REQUIRED_DOCS, "doctrine-docs", "Core doctrine documents exist"),
        exists_check(root, REQUIRED_CONTRACTS, "canonical-contracts", "Canonical core transaction contracts exist"),
        exists_check(root, [ARCH_REGISTRY_FILE, CHECKLIST_FILE], "checklist-artifacts", "Checklist artifacts exist"),
        bff_dual_surface_check(root),
        ci_gate_check(root),
        checklist_coverage_check(root),
    ]
    checks.extend(emitter_dual_emit_checks(root))

    write_reports(root, checks)
    failed = [c for c in checks if c.status == "fail"]
    if failed:
        print("Core Transaction compliance audit failed:")
        for c in failed:
            print(f"- {c.id}: {c.details}")
        return 1
    print("Core Transaction compliance audit passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
