#!/usr/bin/env python3
"""Extract WHO SMART Guidelines L2 Digital Adaptation Kit artefacts to verbatim JSON.

This script is mechanical. It reads the vendored WHO spreadsheets under
``docs/reference/who-dak/<pack>/`` and writes their contents to ``<pack>/extracted/*.json``
without interpreting, correcting or normalising anything.

WHY THE SPLIT MATTERS
---------------------
Extraction and authoring are different acts and are deliberately different programs. Turning a
WHO row into an Impilo rule is a clinical decision that a person makes and signs; ANC.DT.01's row
for a convulsing woman outputs "clinician's discretion", which is a defensible global hedge and an
indefensible national rule. A generator would ship the hedge. So this script produces evidence,
and a human produces content that cites it.

IT NEVER REPAIRS ITS INPUT
--------------------------
WHO's published PNC.S.01 contact schedule has inverted comparison bounds (contact 2's due window
reads ``72 hours < "Hours after childbirth" < 48 hours``) and its overdue column appears shifted by
one. Those strings are copied out exactly as published and flagged in ``parseWarnings``. Silently
"fixing" them would ship a corrected window under a WHO citation, which is a worse failure than
carrying the defect visibly: the reader would have no way to know the rule they are reading is ours.

NO NEW BUILD DEPENDENCY
-----------------------
xlsx is a zip of XML, and the standard library reads both. Adding openpyxl to the toolchain for a
script that runs offline, by hand, a few times a year is not worth the supply-chain surface.

DETERMINISM
-----------
Output is byte-reproducible for a given input: sorted keys, fixed indentation, no timestamps in
the payload. Re-running on unchanged sources must produce an unchanged tree, so ``git status`` is
a valid check that the committed extraction still matches the committed sources.

Usage:
    python3 scripts/clinical/dak/extract-dak-l2.py [--pack smart-anc] [--check]

    --check  extract to a temporary tree and diff against what is committed; exit 1 on drift.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import tempfile
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

MAIN = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
RELS = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}"

REPO_ROOT = Path(__file__).resolve().parents[3]
DAK_ROOT = REPO_ROOT / "docs" / "reference" / "who-dak"

# Sheets that carry no DAK content. Matched case-insensitively against the whole sheet name.
BOILERPLATE_SHEETS = {
    "cover", "overview", "read me", "readme", "references", "dev", "changelog",
    "do not change list of values", "instructions", "title",
}

# ── spreadsheet reading ────────────────────────────────────────────────────────


def _column_letters(ref: str) -> str:
    return "".join(ch for ch in ref if ch.isalpha())


def _column_index(letters: str) -> int:
    n = 0
    for ch in letters:
        n = n * 26 + (ord(ch.upper()) - 64)
    return n - 1


class Workbook:
    """Just enough of the xlsx format to read cell text, in sheet and row order."""

    def __init__(self, path: Path):
        self.path = path
        self._zip = zipfile.ZipFile(path)
        self._shared = self._read_shared_strings()
        self._sheets = self._read_sheet_index()

    def _read_shared_strings(self) -> list[str]:
        if "xl/sharedStrings.xml" not in self._zip.namelist():
            return []
        root = ET.fromstring(self._zip.read("xl/sharedStrings.xml"))
        return ["".join(t.text or "" for t in si.iter(MAIN + "t")) for si in root.iter(MAIN + "si")]

    def _read_sheet_index(self) -> dict[str, str]:
        rels = {
            r.get("Id"): r.get("Target")
            for r in ET.fromstring(self._zip.read("xl/_rels/workbook.xml.rels"))
        }
        book = ET.fromstring(self._zip.read("xl/workbook.xml"))
        out: dict[str, str] = {}
        for sheet in book.iter(MAIN + "sheet"):
            target = rels[sheet.get(RELS + "id")]
            part = target.lstrip("/") if target.startswith("/") else "xl/" + target
            out[sheet.get("name")] = part
        return out

    @property
    def sheet_names(self) -> list[str]:
        return list(self._sheets)

    def rows(self, sheet_name: str) -> list[list[str]]:
        """Rows as dense lists of cell text; blank cells are empty strings."""
        root = ET.fromstring(self._zip.read(self._sheets[sheet_name]))
        out: list[list[str]] = []
        for row in root.iter(MAIN + "row"):
            cells: dict[int, str] = {}
            for cell in row.iter(MAIN + "c"):
                value = self._cell_text(cell)
                if value:
                    cells[_column_index(_column_letters(cell.get("r") or ""))] = value
            if not cells:
                out.append([])
                continue
            width = max(cells) + 1
            out.append([cells.get(i, "") for i in range(width)])
        return out

    def _cell_text(self, cell) -> str:
        inline = cell.find(MAIN + "is")
        if inline is not None:
            return _clean("".join(t.text or "" for t in inline.iter(MAIN + "t")))
        value = cell.find(MAIN + "v")
        if value is None or value.text is None:
            return ""
        if cell.get("t") == "s":
            return _clean(self._shared[int(value.text)])
        return _clean(value.text)


def _clean(text: str) -> str:
    """Normalise whitespace without changing meaning.

    Non-breaking spaces and trailing padding are spreadsheet artefacts, not content — WHO's own
    headers contain both. Line breaks inside a cell ARE content (an Action cell legitimately reads
    "Proceed with ANC contact OR\\nReferral") and are preserved.
    """
    return text.replace(" ", " ").replace("\r\n", "\n").strip()


# ── shape detection ────────────────────────────────────────────────────────────

# A decision-table sheet is laid out as key/value metadata rows, then a header row, then rules.
# The header row is found by content rather than by position, because the 2021 ANC workbooks and
# the 2025 PNC/SMBP workbooks put it in different places and use different column sets. Hard-coding
# a row number would silently mis-read one of the two families.
HEADER_MARKERS = ("input", "inputs", "input 1", "input(s)")


# Data-dictionary sheets carry a two-row header: a row of authoring help text sits ABOVE the real
# column names. Falling back to "first row with several populated cells" lands on the help text and
# reads the whole sheet one row out — which produced zero data elements from a dictionary that
# clearly has hundreds. The real header is identified by the column that every DAK dictionary has.
DICTIONARY_MARKERS = ("data element id", "data element label")


def _find_header_row(rows: list[list[str]]) -> int | None:
    for index, row in enumerate(rows[:20]):
        lowered = [c.strip().lower() for c in row if c.strip()]
        if any(any(marker in cell for marker in DICTIONARY_MARKERS) for cell in lowered):
            return index
    for index, row in enumerate(rows[:20]):
        lowered = [c.strip().lower() for c in row if c.strip()]
        if any(cell in HEADER_MARKERS for cell in lowered):
            return index
    return None


def _metadata_above(rows: list[list[str]], header_index: int) -> dict[str, str]:
    """Rows above the header are ``label | value`` pairs (Decision ID, Business Rule, Trigger…)."""
    meta: dict[str, str] = {}
    for row in rows[:header_index]:
        cells = [c for c in row if c.strip()]
        if len(cells) >= 2:
            meta[_normalise_key(cells[0])] = cells[1]
    return meta


def _normalise_key(label: str) -> str:
    key = re.sub(r"[^a-z0-9]+", " ", label.lower()).strip()
    return re.sub(r" (\w)", lambda m: m.group(1).upper(), key)


def _records(rows: list[list[str]], header_index: int) -> list[dict[str, str]]:
    headers = [c.strip() for c in rows[header_index]]
    out: list[dict[str, str]] = []
    for row in rows[header_index + 1:]:
        if not any(c.strip() for c in row):
            continue
        record: dict[str, str] = {}
        for i, header in enumerate(headers):
            if not header:
                continue
            value = row[i] if i < len(row) else ""
            if value:
                record[header] = value
        if record:
            out.append(record)
    return out


# ── warnings: observed defects in the published sources ────────────────────────

_COMPARISON = re.compile(
    r"(?P<low>[0-9]+)\s*(?P<lowunit>hours?|days?|weeks?)?\s*<\s*[\"“][^\"”]+[\"”]\s*<\s*"
    r"(?P<high>[0-9]+)\s*(?P<highunit>hours?|days?|weeks?)?"
)


def _range_warnings(where: str, value: str) -> list[str]:
    """Flag a published range whose bounds are the wrong way round.

    WHO's PNC.S.01 publishes ``72 hours < "Hours after childbirth" < 48 hours``. Read literally
    that window can never open. We copy it verbatim and say so here; the corrected window is stated
    in our own content as an explicit adaptation, so the difference between what WHO published and
    what Zimbabwe runs is visible rather than silently absorbed.
    """
    warnings: list[str] = []
    for match in _COMPARISON.finditer(value):
        low, high = int(match.group("low")), int(match.group("high"))
        low_unit = (match.group("lowunit") or "").rstrip("s")
        high_unit = (match.group("highunit") or "").rstrip("s")
        if low_unit == high_unit and low >= high:
            warnings.append(
                f"{where}: published range has its bounds inverted and can never be satisfied "
                f"as written — {value!r}. Copied verbatim; do not normalise here."
            )
    return warnings


# ── extraction ─────────────────────────────────────────────────────────────────


def _is_content_sheet(name: str) -> bool:
    return name.strip().lower() not in BOILERPLATE_SHEETS


def _classify(sheet_name: str, meta: dict[str, str]) -> str:
    """What a sheet holds — decided from its content first, its name second.

    Name alone is not enough: the ANC decision-logic workbook contains a family-planning table
    (``FP.LO.004 Reasonably certain not pregnant``) whose tab name never says "DT". Classified by
    name it would be filed as a data dictionary and disappear from the decision-table inventory —
    and it is one of the more useful tables in the set, since the FP DAK ships no decision logic at
    all and pregnancy exclusion is the gate on starting a contraceptive method.
    """
    upper = sheet_name.upper()
    if "decisionId" in meta or "businessRule" in meta:
        return "decision-table"
    if "scheduleId" in meta:
        return "schedule"
    if ".DT" in upper:
        return "decision-table"
    if re.search(r"\.S\.\d", upper) or "SCHEDULE" in upper or "SCHED" in upper:
        return "schedule"
    if "INDICATOR" in upper:
        return "indicator"
    if "TEST CASE" in upper:
        return "test-case"
    return "data-dictionary"


# One sheet does not mean one decision table. WHO groups related tables onto a single tab and
# names it with a range ("ANC.DT.07–14 Laboratory&imaging"), so 16 sheets carry 38 tables. Coverage
# has to be tracked per table id, not per sheet, or a table nobody implemented would never be
# missed — which is the failure this whole traceability spine exists to prevent.
_ID = re.compile(r"\b([A-Z]{2,4}(?:\.[A-Z0-9]+)*\.(?:DT|S|IND|LO)\.?\s?\d+)", re.IGNORECASE)
_RANGE = re.compile(r"\b([A-Z]{2,4}\.(?:DT|S|IND))\.(\d+)\s*[-–—]\s*(\d+)", re.IGNORECASE)


def _ids_from_sheet_name(sheet_name: str) -> list[str]:
    ids: list[str] = []
    for prefix, start, end in _RANGE.findall(sheet_name):
        first, last = int(start), int(end)
        if last >= first and last - first < 60:
            ids.extend(f"{prefix.upper()}.{n:02d}" for n in range(first, last + 1))
    if not ids:
        for raw in _ID.findall(sheet_name):
            ids.append(_canonical_id(raw))
    return _dedupe(ids)


def _ids_from_note(rows: list[list[str]]) -> list[str]:
    """Ids listed in a sheet's own "this tab has N decision support tables" note."""
    ids: list[str] = []
    for row in rows[:6]:
        for cell in row:
            if "decision support table" in cell.lower():
                ids.extend(_canonical_id(raw) for raw in _ID.findall(cell))
    return _dedupe(ids)


def _canonical_id(raw: str) -> str:
    text = raw.upper().replace(" ", "")
    match = re.match(r"^(.*?)\.?(\d+)$", text)
    if not match:
        return text
    head, number = match.groups()
    head = head.rstrip(".")
    return f"{head}.{int(number):02d}"


def _dedupe(values: list[str]) -> list[str]:
    seen: dict[str, None] = {}
    for value in values:
        seen.setdefault(value, None)
    return list(seen)


def _decision_id(meta: dict[str, str], sheet_name: str) -> str:
    for key in ("decisionId", "scheduleId"):
        if meta.get(key):
            # "ANC.DT.01 Danger signs" -> "ANC.DT.01"; a bare id is returned unchanged.
            head = meta[key].split()[0]
            if re.match(r"^[A-Z]+[A-Z0-9.]*\.\d+$|^[A-Z.]+\d+", head):
                return head
            return meta[key]
    return sheet_name.split()[0]


def extract_workbook(path: Path, full_dictionaries: bool = False) -> dict:
    book = Workbook(path)
    payload = {
        "sourceFile": path.name,
        "sourceSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "sheetsRead": [],
        "sheetsSkipped": [],
        "decisionTables": [],
        "schedules": [],
        "indicators": [],
        "dataElements": [],
        "testCases": [],
        "parseWarnings": [],
    }

    for sheet_name in book.sheet_names:
        if not _is_content_sheet(sheet_name):
            payload["sheetsSkipped"].append({"sheet": sheet_name, "reason": "boilerplate"})
            continue
        rows = book.rows(sheet_name)
        header_index = _find_header_row(rows)
        if header_index is None:
            header_index = _first_dense_row(rows)
            if header_index is None:
                payload["sheetsSkipped"].append({"sheet": sheet_name, "reason": "no header row found"})
                continue
        meta = _metadata_above(rows, header_index)
        records = _records(rows, header_index)
        payload["sheetsRead"].append({"sheet": sheet_name, "rows": len(records)})

        kind = _classify(sheet_name, meta)
        from_name = _ids_from_sheet_name(sheet_name)
        from_note = _ids_from_note(rows)
        covers = _dedupe(from_name + from_note)

        # A sheet whose title and whose own note disagree about which tables it holds is worth
        # saying out loud. ANC.DT.07–14's note lists DT.09 twice and never mentions DT.14, so the
        # title promises eight tables and the note names seven. Neither is authoritative, and a
        # coverage report built on the wrong one would quietly under- or over-count.
        if from_name and from_note and set(from_name) != set(from_note):
            payload["parseWarnings"].append(
                f"{sheet_name}: the sheet title and the sheet's own note disagree about which "
                f"tables it contains — title says {sorted(set(from_name))}, note says "
                f"{sorted(set(from_note))}. Both recorded; resolve when authoring."
            )

        # Decision tables, schedules and indicators are small and are read by a human reviewing an
        # adaptation, so their rows stay keyed by column name and are committed in full.
        #
        # Data dictionaries are different in kind. What traceability needs from them is the set of
        # data element identifiers a rule may cite (ANC.B6.DE1, FP.DE.252) and enough label to
        # recognise one — not every column of every row. The full sheets are enormous and largely
        # historical: the family-planning dictionary alone carries four superseded draft snapshots
        # (Master 0.1 through 1.0) beside the current one, ~5,000 rows across 65 columns, and
        # committing all of it produced 11 MB of JSON nobody would ever read.
        #
        # So the committed artefact is an index. The full dictionary has not been lost — the source
        # workbook is committed beside it and `--full-dictionaries` re-extracts every row on demand.
        # Deciding which of WHO's four draft snapshots is authoritative is a judgement, and this
        # script does not make judgements; keeping the source and indexing it makes the question
        # answerable without answering it here.
        rows_out: list = records
        if kind == "data-dictionary" and not full_dictionaries:
            headers = [c.strip() for c in rows[header_index] if c.strip()]
            rows_out = _element_index(records, headers)

        entry = {
            "id": _decision_id(meta, sheet_name),
            "coversIds": covers,
            "idsFromSheetName": from_name,
            "idsFromSheetNote": from_note,
            "sheet": sheet_name,
            "rowEncoding": ("full" if full_dictionaries else "element-index")
            if kind == "data-dictionary" else "keyed",
            "metadata": meta,
            # Absent for the 2021 ANC workbooks. Recorded as null rather than defaulted: a hit
            # policy says whether rows are ordered-exclusive or collect-all, and guessing it would
            # guess how the rules combine.
            "hitPolicy": meta.get("hitPolicyIndicator"),
            "columns": [c.strip() for c in rows[header_index] if c.strip()],
            "rows": rows_out,
        }

        for record in records:
            for column, value in record.items():
                payload["parseWarnings"].extend(
                    _range_warnings(f"{sheet_name} / {column}", value)
                )
                # A published cell containing a broken formula reference means the workbook was
                # shipped with a dangling link. The text is still copied out, but nobody should
                # author a clinical rule from a cell whose own spreadsheet could not resolve it.
                if "#REF!" in value:
                    payload["parseWarnings"].append(
                        f"{sheet_name} / {column}: published cell contains an unresolved "
                        f"spreadsheet reference (#REF!). Content captured but not trustworthy."
                    )

        if kind == "decision-table":
            payload["decisionTables"].append(entry)
        elif kind == "schedule":
            payload["schedules"].append(entry)
        elif kind == "indicator":
            payload["indicators"].append(entry)
        elif kind == "test-case":
            payload["testCases"].append(entry)
        else:
            payload["dataElements"].append(entry)

    return payload


_ELEMENT_ID = re.compile(r"^[A-Z]{2,4}(?:\.[A-Z0-9]+)*\.DE[.\s]?\d+$", re.IGNORECASE)


def _element_index(records: list[dict[str, str]], headers: list[str]) -> list[dict[str, str]]:
    """One entry per data element: its identifier, its label, and any terminology codes.

    The identifier column is found by content rather than by name, because the four DAK families
    label it differently ("[ANC] Data element ID", "Data Element ID", "DE ID"). A row with no
    recognisable identifier is kept with an empty id rather than dropped — a dictionary row that
    does not look like an element is still evidence about the sheet, and dropping it silently would
    make the index look complete when it is not.
    """
    code_headers = [h for h in headers
                    if re.search(r"ICD|LOINC|SNOMED|ICHI|ICF", h, re.IGNORECASE)]
    label_headers = [h for h in headers
                     if re.search(r"data element label|element name|label|name", h, re.IGNORECASE)]
    out: list[dict[str, str]] = []
    for record in records:
        identifier = ""
        for value in record.values():
            if _ELEMENT_ID.match(value.strip()):
                identifier = value.strip()
                break
        label = next((record[h] for h in label_headers if record.get(h)), "")
        entry: dict[str, str] = {"id": identifier, "label": label}
        codes = {h: record[h] for h in code_headers if record.get(h)}
        if codes:
            entry["codes"] = codes
        if identifier or label:
            out.append(entry)
    return out


def _first_dense_row(rows: list[list[str]]) -> int | None:
    """Fallback header detection: the first row with three or more populated cells."""
    for index, row in enumerate(rows[:25]):
        if len([c for c in row if c.strip()]) >= 3:
            return index
    return None


def extract_pack(pack_dir: Path, out_dir: Path, full_dictionaries: bool = False) -> dict:
    out_dir.mkdir(parents=True, exist_ok=True)
    report = {"pack": pack_dir.name, "workbooks": [], "parseWarnings": []}
    combined: dict[str, list] = {
        "decisionTables": [], "schedules": [], "indicators": [],
        "dataElements": [], "testCases": [],
    }

    for xlsx in sorted(pack_dir.glob("*.xlsx")):
        payload = extract_workbook(xlsx, full_dictionaries)
        report["workbooks"].append({
            "file": payload["sourceFile"],
            "sha256": payload["sourceSha256"],
            "sheetsRead": payload["sheetsRead"],
            "sheetsSkipped": payload["sheetsSkipped"],
            "counts": {k: len(payload[k]) for k in combined},
        })
        report["parseWarnings"].extend(payload["parseWarnings"])
        for key in combined:
            for entry in payload[key]:
                entry = dict(entry)
                entry["sourceFile"] = payload["sourceFile"]
                combined[key].append(entry)

    _write(out_dir / "decision-tables.json", {"tables": combined["decisionTables"]})
    _write(out_dir / "schedules.json", {"schedules": combined["schedules"]})
    _write(out_dir / "indicators.json", {"indicators": combined["indicators"]})
    _write(out_dir / "data-dictionary.json", {"sheets": combined["dataElements"]})
    _write(out_dir / "test-cases.json", {"sheets": combined["testCases"]})
    _write(out_dir / "extraction-report.json", report)
    return report


def _write(path: Path, payload: dict) -> None:
    path.write_text(json.dumps(payload, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
                    encoding="utf-8")


# ── entry point ────────────────────────────────────────────────────────────────


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--pack", action="append",
                        help="pack directory name under docs/reference/who-dak (repeatable)")
    parser.add_argument("--full-dictionaries", action="store_true",
                        help="emit every data-dictionary row instead of the element index "
                             "(large; for inspection, not for committing)")
    parser.add_argument("--check", action="store_true",
                        help="extract to a temp tree and fail if it differs from what is committed")
    args = parser.parse_args()

    if not DAK_ROOT.is_dir():
        print(f"FAIL: {DAK_ROOT} does not exist — run fetch-dak-sources.sh first", file=sys.stderr)
        return 1

    packs = [DAK_ROOT / p for p in args.pack] if args.pack else [
        d for d in sorted(DAK_ROOT.iterdir()) if d.is_dir() and any(d.glob("*.xlsx"))
    ]
    if not packs:
        print("FAIL: no packs with .xlsx sources found", file=sys.stderr)
        return 1

    drift = False
    for pack in packs:
        if args.check:
            with tempfile.TemporaryDirectory() as tmp:
                report = extract_pack(pack, Path(tmp), args.full_dictionaries)
                committed = pack / "extracted"
                if not committed.is_dir():
                    print(f"FAIL: {pack.name} has no committed extraction")
                    drift = True
                    continue
                for produced in sorted(Path(tmp).glob("*.json")):
                    target = committed / produced.name
                    if not target.exists() or target.read_bytes() != produced.read_bytes():
                        print(f"FAIL: {pack.name}/extracted/{produced.name} is stale — "
                              f"re-run extract-dak-l2.py and commit the result")
                        drift = True
        else:
            report = extract_pack(pack, pack / "extracted", args.full_dictionaries)

        counts = {k: sum(w["counts"][k] for w in report["workbooks"]) for k in
                  ("decisionTables", "schedules", "indicators", "dataElements", "testCases")}
        print(f"{pack.name}: " + ", ".join(f"{v} {k}" for k, v in counts.items() if v))
        for warning in report["parseWarnings"]:
            print(f"  WARNING {warning}")

    return 1 if drift else 0


if __name__ == "__main__":
    raise SystemExit(main())
