#!/usr/bin/env python3
"""Product Value / Improvement Gate and Accepted Change Completion / Enablement Gate.

Shared by analysis, absorb, verify, and report modes of the Change Absorption Pipeline.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any

PRODUCT_VALUE_CLASSIFICATIONS = (
    "IMPROVEMENT_ACCEPT",
    "IMPROVEMENT_BUT_NEEDS_MODIFICATION",
    "USEFUL_IDEA_BAD_IMPLEMENTATION",
    "NO_CLEAR_VALUE",
    "REGRESSION_REJECT",
    "DUPLICATE_REJECT",
    "RISKY_DEFER",
    "NEEDS_PRODUCT_OWNER_DECISION",
)

COMPLETION_CLASSIFICATIONS = (
    "COMPLETE_AS_ABSORBED",
    "NEEDS_UI_WIRING",
    "NEEDS_BACKEND_WIRING",
    "NEEDS_BFF_WIRING",
    "NEEDS_MIGRATION_OR_SEED_DATA",
    "NEEDS_ROLE_POLICY_OR_TRUST_CONTEXT",
    "NEEDS_ROUTE_OR_NAVIGATION",
    "NEEDS_TESTS",
    "NEEDS_BROWSER_QA",
    "NEEDS_MOBILE_PARITY",
    "NEEDS_CONFIG_OR_DEPLOYMENT_REVIEW",
    "NEEDS_SEPARATE_INTAKE",
    "INCOMPLETE_DO_NOT_ABSORB_YET",
)

ACCEPTED_TECHNICAL_CLASSIFICATIONS = frozenset(
    {
        "ACCEPT_DIRECT",
        "ACCEPT_SELECTED_HUNKS",
        "ACCEPT_WITH_ADAPTATION",
        "CHERRY_PICK_COMMIT",
        "REIMPLEMENT_IDEA",
        "REGRESSION_RESTORATION",
    }
)

DEFER_LABELS = {
    "RISKY_DEFER": "risky platform change",
    "REGRESSION_REJECT": "rejected regression",
    "DUPLICATE_REJECT": "duplication / parallel implementation",
    "NO_CLEAR_VALUE": "no clear value",
    "NEEDS_PRODUCT_OWNER_DECISION": "product-owner decision required",
    "USEFUL_IDEA_BAD_IMPLEMENTATION": "valuable idea but needs separate intake or reimplementation",
}


def _bucket_key(path: str) -> str:
    parts = path.split("/")
    if path.startswith("ui/one-ui-shell/src/app/learning"):
        return "ui/learning-fundo"
    if path.startswith("ui/one-ui-shell/src/app/home"):
        return "ui/home-quick-access"
    if path.startswith("ui/one-ui-shell/"):
        return "ui/one-ui-shell"
    if path.startswith("services/experience-bff/"):
        return "services/experience-bff"
    if path.startswith("services/"):
        return f"services/{parts[1] if len(parts) > 1 else 'unknown'}"
    if path.startswith("apps/mobile/"):
        return "apps/mobile"
    if path.startswith("contracts/"):
        return "contracts"
    if path.startswith("deploy/") or path.startswith("helm/") or "docker-compose" in path:
        return "deploy/config"
    if path.startswith("compose/"):
        return "compose/ops"
    if path.startswith("docs/"):
        return "docs"
    return parts[0] if parts else path


def _is_acceptable_technical(cls: str) -> bool:
    return cls in ACCEPTED_TECHNICAL_CLASSIFICATIONS or cls in {
        "ACCEPT_DIRECT",
        "REGRESSION_RESTORATION",
    }


def _infer_value_judgement(
    bucket: str,
    files: list[str],
    classifications: set[str],
    infer: dict[str, Any],
    wiring_signals: list[str],
) -> dict[str, Any]:
    file_blob = " ".join(files).lower()
    deploy = any(
        x in file_blob
        for x in ("deploy/", "helm/", "docker-compose", ".generated.", "compose/", "ops/")
    )
    auth_trust = any(
        x in file_blob
        for x in (
            "authsession",
            "keycloak",
            "tshepo",
            "trust",
            "policyengine",
            "ext_authz",
        )
    )
    learning = "learning" in bucket or "fundo" in file_blob
    ui = bucket.startswith("ui/")
    mobile = bucket.startswith("apps/mobile")
    deleted = any(f in infer.get("deleted_files", []) for f in files)
    generated = any(f in infer.get("generated_artifacts_touched", []) for f in files)
    bad_wiring = any("REJECT signal" in s for s in wiring_signals)
    good_wiring = any("PASS signal" in s for s in wiring_signals)

    if "ui/experience/" in file_blob:
        return _value(
            "DUPLICATE_REJECT",
            "This touches the retired ui/experience fork; Product Truth uses one-ui-shell only.",
            "Reject — duplicate UX stack",
            "Do not absorb; extend one-ui-shell instead",
            "REGRESSION_REJECT",
        )
    if deleted:
        return _value(
            "NEEDS_PRODUCT_OWNER_DECISION",
            "Files were deleted on the source branch; confirm this is not removing Product Truth capability.",
            "Needs explicit PO approval before any deletion",
            "Confirm no route, API, or workflow loss",
            "NEEDS_PRODUCT_OWNER_DECISION",
        )
    if deploy or generated:
        return _value(
            "RISKY_DEFER",
            "This bucket changes deploy, compose, or generated runtime artifacts — not a safe blind absorb during convergence.",
            "Defer to separate platform/deploy intake",
            "Do not absorb without dedicated deploy review",
            "RISKY_DEFER",
        )
    if auth_trust and "experience-bff" in bucket:
        return _value(
            "RISKY_DEFER",
            "Auth/session/trust changes affect the governed request path; valuable ideas here need a dedicated trust intake.",
            "Separate trust/auth intake required",
            "Do not mix with UX-only absorption",
            "RISKY_DEFER",
        )
    if bad_wiring and not good_wiring:
        return _value(
            "USEFUL_IDEA_BAD_IMPLEMENTATION",
            "The direction may help users, but the diff still shows stubs, mocks, or dead handlers.",
            "Reimplement with real wiring before acceptance",
            "Remove placeholders; wire BFF/service clients",
            "USEFUL_IDEA_BAD_IMPLEMENTATION",
        )
    if learning and ui:
        return _value(
            "IMPROVEMENT_BUT_NEEDS_MODIFICATION",
            "Fundo learning UX/metadata appears to improve completeness over Product Truth, but browser QA and dual-rail UX need PO review.",
            "Accept selectively after verification",
            "Browser QA after absorb; confirm navigation and role gates",
            "ABSORB_SELECTIVELY",
        )
    if ui and good_wiring:
        return _value(
            "IMPROVEMENT_ACCEPT",
            "UI changes appear to strengthen wired product surfacing without obvious stub downgrade.",
            "Accept with adaptation and standard gates",
            "Run no-stub/route tests after absorb",
            "ABSORB_SELECTIVELY",
        )
    if ui:
        return _value(
            "IMPROVEMENT_BUT_NEEDS_MODIFICATION",
            "UI surfacing may add value, but wiring and parity must be verified before calling it an improvement.",
            "Accept only after wiring review",
            "Verify BFF hooks, navigation, and no-stub guards",
            "ABSORB_SELECTIVELY",
        )
    if bucket.startswith("services/") and learning:
        return _value(
            "IMPROVEMENT_BUT_NEEDS_MODIFICATION",
            "Learning-service metadata/API changes likely enable Fundo language/catalog features in Product Truth.",
            "Accept with backend + migration verification",
            "Ensure migration runs, BFF proxy exists, frontend hook updated",
            "ABSORB_SELECTIVELY",
        )
    if bucket.startswith("services/"):
        return _value(
            "NEEDS_PRODUCT_OWNER_DECISION",
            "Backend service changes may add capability but need domain ownership and parity review.",
            "Review service registry ownership first",
            "Confirm no duplicate system-of-record",
            "NEEDS_PRODUCT_OWNER_DECISION",
        )
    if mobile:
        return _value(
            "IMPROVEMENT_BUT_NEEDS_MODIFICATION",
            "Mobile changes may improve parity if wired through BFF; verify citizen/provider surfaces separately.",
            "Accept only with mobile parity follow-up",
            "Run mobile parity guards and separate mobile QA",
            "ABSORB_SELECTIVELY",
        )
    if "REJECT" in classifications:
        return _value(
            "REGRESSION_REJECT",
            "Technical classification marks this as reject — likely regression, generated artifact, or unsafe scope.",
            "Reject for this intake session",
            "None — do not absorb",
            "REJECT",
        )
    if infer.get("changed_count", 0) == 0:
        return _value(
            "NO_CLEAR_VALUE",
            "No diff versus Product Truth — nothing to absorb.",
            "Reject branch for intake",
            "N/A",
            "REJECT_BRANCH",
        )
    return _value(
        "NEEDS_PRODUCT_OWNER_DECISION",
        "Value is unclear from path heuristics alone — manual product judgement required.",
        "Manual review required",
        "Operator defines accept/modify/reject per file",
        "NEEDS_PRODUCT_OWNER_DECISION",
    )


def _value(
    judgement: str,
    meaning: str,
    acceptability: str,
    modification: str,
    recommendation: str,
) -> dict[str, Any]:
    return {
        "product_value_judgement": judgement,
        "product_owner_meaning": meaning,
        "acceptability": acceptability,
        "modification_required": modification,
        "recommendation": recommendation,
    }


def _infer_completion_needs(
    bucket: str,
    files: list[str],
    classification: str,
    infer: dict[str, Any],
) -> list[str]:
    if classification == "REJECT":
        return ["INCOMPLETE_DO_NOT_ABSORB_YET"]

    needs: list[str] = []
    file_blob = " ".join(files).lower()

    has_ui = any(f.startswith("ui/one-ui-shell/") for f in files)
    has_service = any(f.startswith("services/") for f in files)
    has_bff = any(f.startswith("services/experience-bff/") for f in files)
    has_contract = any(f.startswith("contracts/") for f in files)
    has_mobile = any(f.startswith("apps/mobile/") for f in files)
    has_migration = any("/db/migration/" in f for f in files)
    has_test = any(
        re.search(r"test|spec|__tests__", f, re.I) for f in files
    )
    has_route_page = any(
        f.startswith("ui/one-ui-shell/src/app/") and f.endswith("page.tsx") for f in files
    )
    has_layout = any("layout.tsx" in f for f in files)
    auth_trust = any(
        x in file_blob
        for x in ("authsession", "keycloak", "tshepo", "trust", "policy")
    )
    deploy = any(
        x in file_blob for x in ("deploy/", "helm/", "docker-compose", "compose/")
    )

    if deploy:
        needs.extend(["NEEDS_CONFIG_OR_DEPLOYMENT_REVIEW", "NEEDS_SEPARATE_INTAKE"])
    if auth_trust:
        needs.extend(["NEEDS_ROLE_POLICY_OR_TRUST_CONTEXT", "NEEDS_SEPARATE_INTAKE"])
    if has_ui and has_service and not has_bff:
        needs.append("NEEDS_BFF_WIRING")
    if has_ui and not has_service and "metadata" in file_blob:
        needs.append("NEEDS_BACKEND_WIRING")
    if has_service and has_migration:
        needs.append("NEEDS_MIGRATION_OR_SEED_DATA")
    elif has_service and not has_migration and any(
        "entity" in f.lower() or "repository" in f.lower() for f in files
    ):
        needs.append("NEEDS_MIGRATION_OR_SEED_DATA")
    if has_ui and (has_route_page or has_layout):
        needs.extend(["NEEDS_BROWSER_QA", "NEEDS_ROUTE_OR_NAVIGATION"])
    if has_ui:
        needs.append("NEEDS_TESTS")
    if has_bff:
        needs.extend(["NEEDS_BFF_WIRING", "NEEDS_TESTS"])
    if has_contract:
        needs.append("NEEDS_TESTS")
    if has_mobile:
        needs.append("NEEDS_MOBILE_PARITY")
    if bucket.startswith("ui/learning") or "fundo" in file_blob:
        needs.extend(["NEEDS_BROWSER_QA", "NEEDS_ROLE_POLICY_OR_TRUST_CONTEXT"])
    if has_ui and not has_test:
        needs.append("NEEDS_TESTS")

    if not needs and _is_acceptable_technical(classification):
        needs = ["COMPLETE_AS_ABSORBED"]

    # dedupe preserving order
    seen: set[str] = set()
    out: list[str] = []
    for n in needs:
        if n not in seen:
            seen.add(n)
            out.append(n)
    return out


def _risk_if_ignored(judgement: str, completion: list[str]) -> str:
    if judgement in ("REGRESSION_REJECT", "DUPLICATE_REJECT", "RISKY_DEFER"):
        return "Could weaken doctrine, duplicate systems, or destabilize preview/full-boot convergence"
    if "NEEDS_BROWSER_QA" in completion:
        return "Could ship confusing UX, hidden navigation regressions, or broken Fundo flows"
    if "NEEDS_ROLE_POLICY_OR_TRUST_CONTEXT" in completion:
        return "Could block studio/admin routes or bypass trust enforcement"
    if "NEEDS_MIGRATION_OR_SEED_DATA" in completion:
        return "Accepted code may compile but fail at runtime without DB/seed data"
    if "NEEDS_BFF_WIRING" in completion or "NEEDS_BACKEND_WIRING" in completion:
        return "UI may render but show empty/error states against live BFF"
    if judgement == "IMPROVEMENT_ACCEPT":
        return "Low if verification gates pass; medium if tests/browser QA skipped"
    return "Incomplete enablement — accepted changes may not work end-to-end"


def build_product_gates(infer: dict[str, Any], decisions: list[dict[str, Any]]) -> dict[str, Any]:
    wiring = infer.get("assessments", {}).get("real_wiring", [])
    if not wiring and "assessments" not in infer:
        wiring = []

    buckets: dict[str, dict[str, Any]] = defaultdict(
        lambda: {"files": [], "classifications": set(), "decisions": []}
    )
    for d in decisions:
        path = d.get("files") or d.get("path") or ""
        if not path:
            continue
        key = _bucket_key(path)
        buckets[key]["files"].append(path)
        buckets[key]["classifications"].add(d.get("classification", ""))
        buckets[key]["decisions"].append(d)

    bucket_rows: list[dict[str, Any]] = []
    for key in sorted(buckets.keys()):
        b = buckets[key]
        files = b["files"]
        cls_set = b["classifications"]
        primary_cls = next(
            (c for c in sorted(cls_set) if c != "NEEDS_PRODUCT_OWNER_DECISION"),
            next(iter(cls_set), "NEEDS_PRODUCT_OWNER_DECISION"),
        )
        value = _infer_value_judgement(key, files, cls_set, infer, wiring)
        completion = _infer_completion_needs(key, files, primary_cls, infer)
        sample = files[0] if files else key
        change_desc = f"{len(files)} file(s) in {key}; e.g. {sample}"
        bucket_rows.append(
            {
                "area": key,
                "change": change_desc,
                "files": files,
                "classification": primary_cls,
                "product_value_judgement": value["product_value_judgement"],
                "product_owner_meaning": value["product_owner_meaning"],
                "acceptability": value["acceptability"],
                "modification_required": value["modification_required"],
                "completion_needs": completion,
                "risk_if_ignored": _risk_if_ignored(
                    value["product_value_judgement"], completion
                ),
                "recommendation": value["recommendation"],
            }
        )

    executive = _build_executive_summary(infer, bucket_rows)
    deferred = _build_deferred_items(bucket_rows)

    return {
        "product_value_gate": {
            "classifications": list(PRODUCT_VALUE_CLASSIFICATIONS),
            "buckets": bucket_rows,
        },
        "completion_gate": {
            "classifications": list(COMPLETION_CLASSIFICATIONS),
            "by_bucket": {
                r["area"]: r["completion_needs"] for r in bucket_rows
            },
        },
        "executive_summary": executive,
        "deferred_items": deferred,
        "po_decision_table": bucket_rows,
    }


def _build_executive_summary(
    infer: dict[str, Any], buckets: list[dict[str, Any]]
) -> dict[str, str]:
    purpose = infer.get("inferred_source_purpose") or "Unknown"
    expected = infer.get("expected_value") or infer.get("source_group") or ""
    improvements = [
        b for b in buckets
        if b["product_value_judgement"]
        in ("IMPROVEMENT_ACCEPT", "IMPROVEMENT_BUT_NEEDS_MODIFICATION")
    ]
    rejects = [
        b for b in buckets
        if b["product_value_judgement"]
        in ("REGRESSION_REJECT", "DUPLICATE_REJECT", "NO_CLEAR_VALUE")
    ]
    risky = [b for b in buckets if b["product_value_judgement"] == "RISKY_DEFER"]
    modify = [
        b for b in buckets
        if b["product_value_judgement"] == "IMPROVEMENT_BUT_NEEDS_MODIFICATION"
    ]
    separate = [
        b for b in buckets
        if "NEEDS_SEPARATE_INTAKE" in b["completion_needs"]
        or b["product_value_judgement"] in ("RISKY_DEFER", "USEFUL_IDEA_BAD_IMPLEMENTATION")
    ]

    valuable = len(improvements) > 0 and len(rejects) < len(buckets)
    main_improvement = (
        improvements[0]["change"] if improvements else purpose
    )

    absorb_list = ", ".join(b["area"] for b in improvements[:5]) or "None identified"
    modify_list = ", ".join(
        f"{b['area']} ({b['modification_required']})" for b in modify[:5]
    ) or "None — accept with standard verification"
    reject_list = ", ".join(
        f"{b['area']} [{b['product_value_judgement']}]" for b in rejects[:5]
    ) or "None"
    separate_list = ", ".join(b["area"] for b in separate[:5]) or "None"
    post_work = _summarize_completion_needs(buckets)

    if valuable and not rejects and not risky:
        final = "ABSORB_SELECTIVELY — branch appears valuable; absorb approved buckets then complete enablement work"
    elif valuable and (risky or separate):
        final = "ABSORB_SELECTIVELY — valuable UX/domain slices only; defer platform/auth/deploy buckets to separate intakes"
    elif rejects and not improvements:
        final = "REJECT_BRANCH — no clear product improvement over Product Truth"
    else:
        final = "NEEDS_PRODUCT_OWNER_DECISION — mixed value; PO must choose absorb/modify/reject per bucket"

    return {
        "1_is_branch_valuable": (
            f"{'Yes — partial or full value likely' if valuable else 'Unclear or no — review required'}. "
            f"Inferred purpose: {purpose}."
            + (f" Operator expected: {expected}." if expected else "")
        ),
        "2_main_improvement": main_improvement,
        "3_what_to_absorb": absorb_list,
        "4_must_modify_before_acceptance": modify_list,
        "5_what_to_reject": reject_list,
        "6_separate_intake": separate_list,
        "7_post_absorption_work": post_work,
        "8_final_product_owner_recommendation": final,
    }


def _summarize_completion_needs(buckets: list[dict[str, Any]]) -> str:
    counts: dict[str, int] = defaultdict(int)
    for b in buckets:
        for n in b["completion_needs"]:
            if n != "COMPLETE_AS_ABSORBED":
                counts[n] += 1
    if not counts:
        return "Standard verification only (VM gates, commit review)"
    top = sorted(counts.items(), key=lambda x: -x[1])[:6]
    return "; ".join(f"{k} ({v} bucket(s))" for k, v in top)


def _build_deferred_items(buckets: list[dict[str, Any]]) -> list[dict[str, str]]:
    items: list[dict[str, str]] = []
    seen: set[str] = set()
    for b in buckets:
        j = b["product_value_judgement"]
        if j in DEFER_LABELS:
            key = f"{b['area']}:{j}"
            if key not in seen:
                seen.add(key)
                items.append(
                    {
                        "area": b["area"],
                        "label": DEFER_LABELS[j],
                        "meaning": b["product_owner_meaning"],
                        "recommendation": b["recommendation"],
                    }
                )
        elif "NEEDS_SEPARATE_INTAKE" in b["completion_needs"]:
            key = f"{b['area']}:separate"
            if key not in seen:
                seen.add(key)
                items.append(
                    {
                        "area": b["area"],
                        "label": "valuable but needs separate intake",
                        "meaning": b["product_owner_meaning"],
                        "recommendation": "Separate intake for enablement wiring",
                    }
                )
    return items


def enrich_decisions(
    infer: dict[str, Any], decisions: list[dict[str, Any]]
) -> list[dict[str, Any]]:
    gates = build_product_gates(infer, decisions)
    by_file: dict[str, dict[str, Any]] = {}
    for bucket in gates["product_value_gate"]["buckets"]:
        for f in bucket["files"]:
            by_file[f] = bucket

    enriched: list[dict[str, Any]] = []
    for d in decisions:
        path = d.get("files") or d.get("path") or ""
        b = by_file.get(path, {})
        row = dict(d)
        row["product_value_judgement"] = b.get(
            "product_value_judgement", "NEEDS_PRODUCT_OWNER_DECISION"
        )
        row["product_owner_meaning"] = b.get(
            "product_owner_meaning",
            "Manual review required for product value.",
        )
        row["acceptability"] = b.get("acceptability", d.get("recommended_action", ""))
        row["modification_required"] = b.get("modification_required", "Review before absorb")
        row["completion_needs"] = b.get("completion_needs", ["NEEDS_TESTS"])
        row["risk_if_ignored"] = b.get("risk_if_ignored", "Incomplete enablement")
        enriched.append(row)
    return enriched


def absorb_completion_warnings(approved_items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    warnings: list[dict[str, Any]] = []
    for item in approved_items:
        missing = []
        for field in (
            "product_value_judgement",
            "acceptability",
            "modification_required",
            "completion_needs",
            "risk_if_ignored",
        ):
            val = item.get(field)
            if val is None or val == "" or val == []:
                missing.append(field)
        if missing:
            placeholders = {
                "product_value_judgement": "NEEDS_PRODUCT_OWNER_DECISION",
                "acceptability": "Review during absorb — not recorded in approved plan",
                "modification_required": "Verify before commit",
                "completion_needs": ["NEEDS_TESTS", "NEEDS_BROWSER_QA"],
                "risk_if_ignored": "Enablement gaps may leave absorbed changes non-functional",
            }
            item.setdefault("_completion_placeholders", {})
            for m in missing:
                item["_completion_placeholders"][m] = placeholders.get(m, "TBD")
            warnings.append(
                {
                    "id": item.get("id", "?"),
                    "path": item.get("path", ""),
                    "missing_fields": missing,
                    "message": (
                        f"approved-plan item {item.get('id')} missing completion metadata: "
                        + ", ".join(missing)
                    ),
                }
            )
    return warnings


def build_completion_tracking_table(
    approved_items: list[dict[str, Any]],
    applied: list[str],
    verify_result: dict[str, Any] | None = None,
) -> list[dict[str, Any]]:
    applied_ids = set()
    for a in applied:
        applied_ids.add(a.split(":")[0])

    rows: list[dict[str, Any]] = []
    for item in approved_items:
        iid = item.get("id", "")
        absorbed = iid in applied_ids or any(iid in a for a in applied)
        needs = item.get("completion_needs") or item.get("_completion_placeholders", {}).get(
            "completion_needs", ["NEEDS_TESTS"]
        )
        if isinstance(needs, dict):
            needs = list(needs.values()) if needs else ["NEEDS_TESTS"]

        works = "Unknown"
        status = "Pending verification"
        if not absorbed:
            works = "No"
            status = "Not absorbed"
        elif verify_result:
            failed = any(
                c.get("result") == "FAIL"
                for c in verify_result.get("checks", [])
            )
            if failed:
                works = "Partial"
                status = "Verify failures — review logs"
            elif "NEEDS_BROWSER_QA" in needs:
                works = "Partial"
                status = "Code absorbed; browser QA still required"
            elif needs == ["COMPLETE_AS_ABSORBED"]:
                works = "Likely yes"
                status = "Complete pending commit/deploy"
            else:
                works = "Partial"
                status = "Additional enablement still required"

        owner = "engineering"
        if "NEEDS_PRODUCT_OWNER_DECISION" in str(item.get("product_value_judgement", "")):
            owner = "product-owner"
        if "NEEDS_ROLE_POLICY_OR_TRUST_CONTEXT" in needs:
            owner = "product-owner + trust"
        if "NEEDS_BROWSER_QA" in needs:
            owner = "product-owner + engineering"

        rows.append(
            {
                "accepted_change": f"{iid}: {item.get('path', item.get('reason', ''))}",
                "absorbed": "Yes" if absorbed else "No",
                "works_end_to_end": works,
                "additional_work": ", ".join(needs) if isinstance(needs, list) else str(needs),
                "owner": owner,
                "status": status,
                "product_owner_notes": item.get("product_owner_notes", ""),
            }
        )
    return rows


def render_executive_summary_md(summary: dict[str, str]) -> str:
    lines = [
        "## Product Owner Decision Summary",
        "",
        "Plain-language answers for this intake session:",
        "",
    ]
    labels = [
        ("1_is_branch_valuable", "1. Is this source-branch change an improvement over Product Truth?"),
        ("2_main_improvement", "2. What is the main improvement?"),
        ("3_what_to_absorb", "3. What should be absorbed?"),
        ("4_must_modify_before_acceptance", "4. What must be modified before acceptance?"),
        ("5_what_to_reject", "5. What should be rejected?"),
        ("6_separate_intake", "6. What should become a separate intake?"),
        ("7_post_absorption_work", "7. What additional Product Truth work is needed after absorption?"),
        ("8_final_product_owner_recommendation", "8. Final product-owner recommendation"),
    ]
    for key, label in labels:
        lines.append(f"**{label}**")
        lines.append("")
        lines.append(summary.get(key, "—"))
        lines.append("")
    return "\n".join(lines)


def render_po_table_md(rows: list[dict[str, Any]]) -> str:
    lines = [
        "## Product Value / Improvement Gate — decision table",
        "",
        "| Area | Change | Product value judgement | Acceptability | Modification needed | Completion needs | Risk if ignored | Recommendation |",
        "| ---- | ------ | ----------------------- | ------------- | ------------------- | ---------------- | --------------- | -------------- |",
    ]
    for r in rows:
        completion = ", ".join(r.get("completion_needs", []))
        lines.append(
            f"| {r['area']} | {r['change'][:60]} | {r['product_value_judgement']} | "
            f"{r['acceptability'][:50]} | {r['modification_required'][:50]} | "
            f"{completion[:60]} | {r['risk_if_ignored'][:50]} | {r['recommendation']} |"
        )
    lines.append("")
    for r in rows:
        lines.append(f"- **{r['area']}** — Product-owner meaning: {r['product_owner_meaning']}")
    lines.append("")
    return "\n".join(lines)


def render_completion_table_md(rows: list[dict[str, Any]]) -> str:
    lines = [
        "## Accepted Change Completion / Enablement Gate",
        "",
        "| Accepted change | Absorbed? | Works end-to-end? | Additional Product Truth work needed | Owner | Status |",
        "| --------------- | --------- | ----------------- | ------------------------------------ | ----- | ------ |",
    ]
    for r in rows:
        lines.append(
            f"| {r['accepted_change'][:70]} | {r['absorbed']} | {r['works_end_to_end']} | "
            f"{r['additional_work'][:60]} | {r['owner']} | {r['status']} |"
        )
    lines.append("")
    return "\n".join(lines)


def render_deferred_md(deferred: list[dict[str, str]]) -> str:
    if not deferred:
        return ""
    lines = ["## Deferred / rejected buckets (plain language)", ""]
    for d in deferred:
        lines.append(f"- **{d['area']}** — _{d['label']}_: {d['meaning']}")
    lines.append("")
    return "\n".join(lines)


def cmd_analyze(args: argparse.Namespace) -> int:
    infer = json.loads(Path(args.infer).read_text())
    decisions_doc = json.loads(Path(args.decisions).read_text())
    if isinstance(decisions_doc, dict) and decisions_doc.get("assessments"):
        infer = dict(infer)
        infer["assessments"] = decisions_doc["assessments"]
    decisions = decisions_doc.get("decisions", decisions_doc if isinstance(decisions_doc, list) else [])

    gates = build_product_gates(infer, decisions)
    enriched = enrich_decisions(infer, decisions)

    out = {
        **gates,
        "decisions_enriched": enriched,
    }
    Path(args.out).write_text(json.dumps(out, indent=2) + "\n")

    if args.md_out:
        md_parts = [
            render_executive_summary_md(gates["executive_summary"]),
            render_po_table_md(gates["po_decision_table"]),
            render_deferred_md(gates["deferred_items"]),
        ]
        Path(args.md_out).write_text("\n".join(md_parts))
    return 0


def cmd_absorb_warnings(args: argparse.Namespace) -> int:
    doc = json.loads(Path(args.approval).read_text())
    warnings = absorb_completion_warnings(doc.get("approved_items", []))
    out = {"warnings": warnings, "count": len(warnings)}
    Path(args.out).write_text(json.dumps(out, indent=2) + "\n")
    for w in warnings:
        print(f"WARN  {w['message']}", file=sys.stderr)
    return 0


def cmd_report(args: argparse.Namespace) -> int:
    gates: dict[str, Any] = {}
    if args.gates and Path(args.gates).exists():
        gates = json.loads(Path(args.gates).read_text())

    po_table = gates.get("po_decision_table") or gates.get("product_value_gate", {}).get("buckets", [])
    executive = gates.get("executive_summary", {})
    deferred = gates.get("deferred_items", [])

    approved: list[dict[str, Any]] = []
    if Path(args.approval).exists():
        approved = json.loads(Path(args.approval).read_text()).get("approved_items", [])

    applied: list[str] = []
    verify: dict[str, Any] | None = None
    if args.absorb and Path(args.absorb).exists():
        absorb = json.loads(Path(args.absorb).read_text())
        applied = absorb.get("applied", [])
    if args.verify and Path(args.verify).exists():
        raw = json.loads(Path(args.verify).read_text())
        verify = raw.get("verification", raw)
        if isinstance(verify, dict) and "checks" not in verify and "verification" in raw:
            verify = raw["verification"]

    completion_rows = build_completion_tracking_table(approved, applied, verify)

    md_parts: list[str] = []
    if executive:
        md_parts.append(render_executive_summary_md(executive))
    if po_table:
        md_parts.append("## Technical decision table (reference)")
        md_parts.append("")
        md_parts.append("_See analysis report for full per-file classifications._")
        md_parts.append("")
    md_parts.append(render_completion_table_md(completion_rows))
    if deferred:
        md_parts.append(render_deferred_md(deferred))

    Path(args.md_out).write_text("\n".join(md_parts))
    out = {
        "executive_summary": executive,
        "completion_tracking": completion_rows,
        "deferred_items": deferred,
    }
    Path(args.out).write_text(json.dumps(out, indent=2) + "\n")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Product value and completion gates")
    sub = parser.add_subparsers(dest="command", required=True)

    p_analyze = sub.add_parser("analyze")
    p_analyze.add_argument("--infer", required=True)
    p_analyze.add_argument("--decisions", required=True)
    p_analyze.add_argument("--out", required=True)
    p_analyze.add_argument("--md-out", default="")

    p_absorb = sub.add_parser("absorb-warnings")
    p_absorb.add_argument("--approval", required=True)
    p_absorb.add_argument("--out", required=True)

    p_report = sub.add_parser("report")
    p_report.add_argument("--gates", required=True)
    p_report.add_argument("--approval", default="")
    p_report.add_argument("--absorb", default="")
    p_report.add_argument("--verify", default="")
    p_report.add_argument("--out", required=True)
    p_report.add_argument("--md-out", required=True)

    args = parser.parse_args()
    if args.command == "analyze":
        return cmd_analyze(args)
    if args.command == "absorb-warnings":
        return cmd_absorb_warnings(args)
    if args.command == "report":
        return args and cmd_report(args)
    return 1


if __name__ == "__main__":
    sys.exit(main())
