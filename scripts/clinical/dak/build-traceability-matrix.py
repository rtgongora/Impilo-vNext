#!/usr/bin/env python3
"""Join what WHO published against what Impilo ships, and say where they differ.

Reads the extracted DAK artefacts under ``docs/reference/who-dak/*/extracted/`` and every clinical
content pack under ``services/clinical-knowledge-platform-service/src/main/resources/clinical/``,
and writes ``docs/clinical-governance/rmnp/dak-traceability-matrix.json`` plus a rendered ``.md``.

WHAT THIS IS FOR
----------------
"DAK aligned" is a claim anyone can make. This produces the evidence for it, per artefact: which
WHO table we implemented, which rule implements it, whether we adopted the WHO text or changed it,
and — for anything we did not implement — a recorded decision saying so.

The last part is the one that matters. A wrong rule tends to get noticed. A table nobody ever
looked at does not: it simply is not there, and nothing in the system is shaped like its absence.
So every extracted decision table, schedule and indicator must appear here as either covered or
consciously excluded, and the guard fails on anything that is neither.

Usage:
    python3 scripts/clinical/dak/build-traceability-matrix.py [--check]

    --check  rebuild into a temporary tree and fail if the committed matrix is stale.
"""

from __future__ import annotations

import argparse
import json
import sys
import tempfile
from collections import defaultdict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
DAK_ROOT = REPO_ROOT / "docs" / "reference" / "who-dak"
CKP_CONTENT = (REPO_ROOT / "services" / "clinical-knowledge-platform-service"
               / "src" / "main" / "resources" / "clinical")
FORMS_SEEDS = REPO_ROOT / "services" / "forms-service" / "src" / "main" / "resources" / "seed-forms"
OUT_DIR = REPO_ROOT / "docs" / "clinical-governance" / "rmnp"
EXCLUSIONS = OUT_DIR / "dak-coverage-exclusions.json"

ADAPTATION_TYPES = {
    "ADOPTED_VERBATIM", "STRENGTHENED", "NARROWED", "SPLIT", "MERGED",
    "ADDED_NATIONAL", "DEFERRED", "REJECTED",
}
EXCLUSION_DECISIONS = {"DEFERRED", "REJECTED", "OUT_OF_SCOPE", "COVERED_ELSEWHERE"}


# ── what WHO published ─────────────────────────────────────────────────────────


def published_artefacts() -> dict[str, dict]:
    """Every decision table, schedule and indicator id WHO published, and where it came from."""
    artefacts: dict[str, dict] = {}
    for pack in sorted(p for p in DAK_ROOT.iterdir() if (p / "extracted").is_dir()):
        for filename, kind, key in (
            ("decision-tables.json", "DECISION_TABLE", "tables"),
            ("schedules.json", "SCHEDULE", "schedules"),
            ("indicators.json", "INDICATOR", "indicators"),
        ):
            path = pack / "extracted" / filename
            if not path.exists():
                continue
            for entry in json.loads(path.read_text(encoding="utf-8")).get(key, []):
                ids = entry.get("coversIds") or [entry.get("id")]
                ids = [i for i in ids if i]
                # WHO groups several tables onto one tab, so the tab's business rule describes only
                # the first of them. Carrying it onto all eight would put "An ultrasound is
                # recommended..." next to the syphilis and tuberculosis tables, which reads as a
                # statement about those tables and is simply false. Flag it instead.
                sheet_level_title = len(ids) > 1
                for artefact_id in ids:
                    artefacts.setdefault(artefact_id, {
                        "dakId": artefact_id,
                        "pack": pack.name,
                        "kind": kind,
                        "sheet": entry.get("sheet"),
                        "title": (entry.get("metadata") or {}).get("businessRule")
                                 or (entry.get("metadata") or {}).get("decisionId")
                                 or entry.get("sheet"),
                        # Absent for the 2021 workbooks. Recorded as published, not defaulted:
                        # a hit policy says whether rows are ordered-exclusive or collect-all.
                        "hitPolicy": entry.get("hitPolicy"),
                        "titleIsSheetLevel": sheet_level_title,
                        "publishedRows": len(entry.get("rows") or []),
                        "sourceFile": entry.get("sourceFile"),
                    })
    return artefacts


def published_elements() -> set[str]:
    ids: set[str] = set()
    for pack in sorted(p for p in DAK_ROOT.iterdir() if (p / "extracted").is_dir()):
        path = pack / "extracted" / "data-dictionary.json"
        if not path.exists():
            continue
        for sheet in json.loads(path.read_text(encoding="utf-8")).get("sheets", []):
            for row in sheet.get("rows", []):
                if isinstance(row, dict) and row.get("id"):
                    ids.add(row["id"])
    return ids


# ── what Impilo ships ──────────────────────────────────────────────────────────


def _walk(node, found: list[tuple[dict, dict | None, str]], code: str | None = None) -> None:
    """Collect every object carrying a dakRef or an adaptation, with the nearest rule code."""
    if isinstance(node, dict):
        code = node.get("code") or node.get("tableId") or node.get("serviceCode") or code
        if "dakRef" in node or "adaptation" in node:
            found.append((node.get("dakRef") or {}, node.get("adaptation"), code or "?"))
        for value in node.values():
            _walk(value, found, code)
    elif isinstance(node, list):
        for value in node:
            _walk(value, found, code)


def shipped_artefacts() -> tuple[list[dict], list[str]]:
    """Every shipped artefact that cites a DAK reference, plus content problems found."""
    shipped: list[dict] = []
    problems: list[str] = []
    for path in sorted(list(CKP_CONTENT.glob("*.json")) + list(FORMS_SEEDS.glob("*.json"))):
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            problems.append(f"{path.relative_to(REPO_ROOT)}: not valid JSON ({exc})")
            continue
        found: list[tuple[dict, dict | None, str]] = []
        _walk(payload, found)
        for dak_ref, adaptation, code in found:
            shipped.append({
                "code": code,
                "file": str(path.relative_to(REPO_ROOT)),
                "packId": payload.get("packId") or payload.get("formKey"),
                "dakId": dak_ref.get("id"),
                "row": dak_ref.get("row"),
                "dakVersion": dak_ref.get("dakVersion"),
                "sourceHash": dak_ref.get("sourceHash"),
                "adaptationType": (adaptation or {}).get("type"),
                "approvingAuthority": (adaptation or {}).get("approvingAuthority"),
                "rationale": (adaptation or {}).get("rationale"),
                "evidenceRefs": (adaptation or {}).get("evidenceRefs") or [],
            })
    return shipped, problems


def exclusions() -> dict[str, dict]:
    if not EXCLUSIONS.exists():
        return {}
    payload = json.loads(EXCLUSIONS.read_text(encoding="utf-8"))
    return {e["dakId"]: e for e in payload.get("exclusions", [])}


# ── the matrix ─────────────────────────────────────────────────────────────────


def build() -> tuple[dict, list[str]]:
    published = published_artefacts()
    shipped, problems = shipped_artefacts()
    excluded = exclusions()

    by_dak: dict[str, list[dict]] = defaultdict(list)
    for entry in shipped:
        if entry["dakId"]:
            by_dak[entry["dakId"]].append(entry)

    rows: list[dict] = []
    for dak_id in sorted(published):
        artefact = published[dak_id]
        implementations = by_dak.get(dak_id, [])
        exclusion = excluded.get(dak_id)
        if implementations:
            status = "SHIPPED"
        elif exclusion:
            status = exclusion.get("decision", "DEFERRED")
        else:
            status = "UNCOVERED"
        rows.append({
            **artefact,
            "status": status,
            "implementations": [
                {"code": i["code"], "file": i["file"], "row": i["row"],
                 "adaptationType": i["adaptationType"],
                 "approvingAuthority": i["approvingAuthority"]}
                for i in implementations
            ],
            "adaptationTypes": sorted({i["adaptationType"] for i in implementations
                                       if i["adaptationType"]}),
            "exclusionReason": (exclusion or {}).get("reason"),
            "revisitCondition": (exclusion or {}).get("revisitCondition"),
        })

    # An artefact that cites a DAK id nobody published is a typo or an invention, and either way it
    # is a citation to something that does not exist.
    for entry in shipped:
        if entry["dakId"] and entry["dakId"] not in published:
            problems.append(
                f"{entry['file']} rule {entry['code']}: cites {entry['dakId']}, which is not in any "
                f"extracted DAK. Check the identifier, or mark the rule ADDED_NATIONAL."
            )

    counts: dict[str, int] = defaultdict(int)
    for row in rows:
        counts[row["status"]] += 1

    matrix = {
        "generatedFrom": "docs/reference/who-dak/*/extracted",
        "publishedArtefacts": len(rows),
        "publishedDataElements": len(published_elements()),
        "shippedCitations": len(shipped),
        "statusCounts": dict(sorted(counts.items())),
        "rows": rows,
    }
    return matrix, problems


def render_markdown(matrix: dict) -> str:
    lines = [
        "# WHO DAK traceability matrix",
        "",
        "**Generated — do not edit by hand.** Rebuild with",
        "`python3 scripts/clinical/dak/build-traceability-matrix.py`.",
        "",
        "One row per artefact WHO published. `SHIPPED` means an Impilo rule cites it; every other",
        "status is a recorded decision in `dak-coverage-exclusions.json`. `UNCOVERED` means nobody",
        "has decided yet, and the guard fails on it — a table nobody looked at is the failure this",
        "matrix exists to make visible.",
        "",
        f"- Published artefacts: **{matrix['publishedArtefacts']}**",
        f"- Published data elements: **{matrix['publishedDataElements']}**",
        f"- Shipped citations: **{matrix['shippedCitations']}**",
        "",
        "| Status | Count |",
        "|---|---|",
    ]
    for status, count in matrix["statusCounts"].items():
        lines.append(f"| `{status}` | {count} |")
    lines += [
        "",
        "| DAK id | Kind | Hit policy | Title | Status | Implemented by | Adaptation | Note |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for row in matrix["rows"]:
        implemented = ", ".join(f"`{i['code']}`" for i in row["implementations"]) or "—"
        adaptation = ", ".join(row["adaptationTypes"]) or "—"
        note = row["exclusionReason"] or ""
        title = (row["title"] or "")[:80].replace("|", "/").replace("\n", " ")
        if row.get("titleIsSheetLevel"):
            title = f"(tab-level) {title}"
        lines.append(
            f"| `{row['dakId']}` | {row['kind']} | {row['hitPolicy'] or '—'} | {title} | "
            f"`{row['status']}` | {implemented} | {adaptation} | {note[:120]} |"
        )
    return "\n".join(lines) + "\n"


def write(matrix: dict, out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "dak-traceability-matrix.json").write_text(
        json.dumps(matrix, indent=2, sort_keys=True, ensure_ascii=False) + "\n", encoding="utf-8")
    (out_dir / "dak-traceability-matrix.md").write_text(render_markdown(matrix), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true",
                        help="fail if the committed matrix differs from a fresh rebuild")
    args = parser.parse_args()

    matrix, problems = build()

    if args.check:
        with tempfile.TemporaryDirectory() as tmp:
            write(matrix, Path(tmp))
            for produced in sorted(Path(tmp).iterdir()):
                target = OUT_DIR / produced.name
                if not target.exists() or target.read_bytes() != produced.read_bytes():
                    print(f"FAIL: {target.relative_to(REPO_ROOT)} is stale — rebuild and commit it")
                    return 1
    else:
        write(matrix, OUT_DIR)

    print(f"{matrix['publishedArtefacts']} published artefacts, "
          f"{matrix['shippedCitations']} shipped citations")
    for status, count in matrix["statusCounts"].items():
        print(f"  {status}: {count}")
    for problem in problems:
        print(f"  PROBLEM {problem}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
