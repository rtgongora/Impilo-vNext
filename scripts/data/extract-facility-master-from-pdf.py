#!/usr/bin/env python3
"""Extract Master Health Facility pack artefacts from searchable PDFs."""

from __future__ import annotations

import csv
import json
import re
import subprocess
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Any

REPO = Path(__file__).resolve().parents[2]
PACK_DIR = REPO / "docs/data/facility-master-2024-07-23"
OUT_DIR = PACK_DIR / "generated"
SQL_PDF = PACK_DIR / "04_SQL_AND_DATA_QUALITY_searchable.pdf"

VALUES_MARKER = "VALUES ("


@dataclass
class FacilityRecord:
    facility_uid: str
    facility_code: str | None
    facility_name: str
    province: str | None
    district: str | None
    facility_type: str | None
    ownership: str | None
    location_context: str | None
    service_level: str | None
    status: str | None
    bed_capacity: int | None
    latitude: float | None
    longitude: float | None
    contact_phone_e164: str | None
    source_dataset_date: str = "2024-07-23"

    def has_valid_coordinates(self) -> bool:
        if self.latitude is None or self.longitude is None:
            return False
        if not (-90 <= self.latitude <= 90 and -180 <= self.longitude <= 180):
            return False
        if self.latitude == 0 and self.longitude == 0:
            return False
        return True

    def is_open(self) -> bool:
        return (self.status or "").strip().lower() == "open"


def pdftotext(pdf: Path) -> str:
    result = subprocess.run(
        ["pdftotext", str(pdf), "-"],
        capture_output=True,
        text=True,
        check=True,
    )
    return result.stdout


def normalize_sql_text(text: str) -> str:
    lines = []
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        if stripped.startswith("Impilo vNext Cursor PDF"):
            continue
        if stripped.startswith("Page "):
            continue
        if "Searchable PDF" in stripped and stripped.endswith("PDF"):
            continue
        if stripped.startswith("BEGIN FILE:") or stripped.startswith("END FILE:"):
            continue
        if stripped.startswith("="):
            continue
        lines.append(line)
    return "\n".join(lines)


def parse_nullable_int(value: str) -> int | None:
    value = value.strip()
    if value.upper() == "NULL" or value == "":
        return None
    return int(float(value))


def parse_nullable_float(value: str) -> float | None:
    value = value.strip()
    if value.upper() == "NULL" or value == "":
        return None
    return float(value)


def parse_nullable_phone(value: str) -> str | None:
    value = value.strip()
    if value.upper() == "NULL" or value == "":
        return None
    if value.startswith("'") and value.endswith("'"):
        value = value[1:-1]
    return value or None


def split_sql_values(blob: str) -> list[str]:
    """Split a SQL VALUES tuple into individual value tokens."""
    tokens: list[str] = []
    current: list[str] = []
    in_string = False
    i = 0
    while i < len(blob):
        ch = blob[i]
        if in_string:
            if ch == "'":
                if i + 1 < len(blob) and blob[i + 1] == "'":
                    current.append("''")
                    i += 2
                    continue
                in_string = False
                current.append("'")
                i += 1
                continue
            current.append(ch)
            i += 1
            continue
        if ch == "'":
            in_string = True
            current.append(ch)
            i += 1
            continue
        if ch == ",":
            tokens.append("".join(current).strip())
            current = []
            i += 1
            continue
        current.append(ch)
        i += 1
    if current:
        tokens.append("".join(current).strip())
    return tokens


def parse_sql_token(token: str) -> str | int | float | None:
    token = token.strip()
    if token.upper() == "NULL" or token == "":
        return None
    if token.startswith("'") and token.endswith("'"):
        return token[1:-1].replace("''", "'")
    if re.fullmatch(r"-?\d+", token):
        return int(token)
    if re.fullmatch(r"-?\d+(?:\.\d+)?", token):
        return float(token)
    return token


def strip_page_noise(line: str) -> str | None:
    stripped = line.strip()
    if not stripped:
        return None
    if stripped.startswith("Impilo vNext Cursor PDF"):
        return None
    if re.fullmatch(r"Page \d+", stripped):
        return None
    if stripped.endswith("Searchable PDF") and "PDF" in stripped:
        return None
    if stripped.startswith("BEGIN FILE:") or stripped.startswith("END FILE:"):
        return None
    if stripped.startswith("="):
        return None
    return line


def coalesce_insert_blocks(text: str) -> list[str]:
    """Merge PDF line wraps so VALUES tuples are intact."""
    cleaned: list[str] = []
    for line in text.splitlines():
        kept = strip_page_noise(line)
        if kept is not None:
            cleaned.append(kept.strip())

    blocks: list[str] = []
    current: list[str] = []
    for line in cleaned:
        if line.startswith("INSERT INTO staging_master_health_facilities_20240723"):
            if current:
                blocks.append(" ".join(current))
            current = [line]
            continue
        if current:
            current.append(line)
            if "DO NOTHING;" in line:
                blocks.append(" ".join(current))
                current = []
    if current:
        blocks.append(" ".join(current))
    return blocks


def parse_records_from_sql(text: str) -> list[FacilityRecord]:
    records: list[FacilityRecord] = []
    for chunk in coalesce_insert_blocks(text):
        marker = chunk.find(VALUES_MARKER)
        if marker < 0:
            continue
        start = marker + len(VALUES_MARKER)
        end = -1
        for pattern in (") ON CONFLICT", ")ON CONFLICT"):
            end = chunk.find(pattern, start)
            if end >= 0:
                break
        if end < 0:
            continue
        values_blob = chunk[start:end]
        tokens = split_sql_values(values_blob)
        if len(tokens) < 15:
            continue
        parsed = [parse_sql_token(t) for t in tokens[:15]]
        records.append(
            FacilityRecord(
                facility_uid=str(parsed[0]),
                facility_code=parsed[1] if parsed[1] else None,
                facility_name=str(parsed[2]),
                province=parsed[3] if isinstance(parsed[3], str) else None,
                district=parsed[4] if isinstance(parsed[4], str) else None,
                facility_type=parsed[5] if isinstance(parsed[5], str) else None,
                ownership=parsed[6] if isinstance(parsed[6], str) else None,
                location_context=parsed[7] if isinstance(parsed[7], str) else None,
                service_level=parsed[8] if isinstance(parsed[8], str) else None,
                status=parsed[9] if isinstance(parsed[9], str) else None,
                bed_capacity=parsed[10] if isinstance(parsed[10], int) else None,
                latitude=parsed[11] if isinstance(parsed[11], (int, float)) else None,
                longitude=parsed[12] if isinstance(parsed[12], (int, float)) else None,
                contact_phone_e164=parsed[13] if isinstance(parsed[13], str) else None,
                source_dataset_date=str(parsed[14]) if parsed[14] else "2024-07-23",
            )
        )
    return records


def slugify(name: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
    return slug or "facility"


def build_tuso_seed(records: list[FacilityRecord]) -> list[dict[str, Any]]:
    out = []
    for i, r in enumerate(records, start=2):
        out.append(
            {
                "facility_uid": r.facility_uid,
                "facility_code": r.facility_code,
                "facility_name": r.facility_name,
                "province": r.province,
                "district": r.district,
                "facility_type": r.facility_type,
                "ownership": r.ownership,
                "location_context": r.location_context,
                "service_level": r.service_level,
                "status": r.status,
                "bed_capacity": r.bed_capacity,
                "contact_phone_e164": r.contact_phone_e164,
                "date_opened": None,
                "date_closed": None,
                "comments": None,
                "source_row": i,
                "source_dataset_date": r.source_dataset_date,
            }
        )
    return out


def build_ndila_seed(records: list[FacilityRecord]) -> list[dict[str, Any]]:
    out = []
    for r in records:
        out.append(
            {
                "facility_uid": r.facility_uid,
                "facility_code": r.facility_code,
                "facility_name": r.facility_name,
                "province": r.province,
                "district": r.district,
                "latitude": r.latitude,
                "longitude": r.longitude,
                "has_coordinates": r.has_valid_coordinates(),
                "location_context": r.location_context,
                "service_level": r.service_level,
                "status": r.status,
            }
        )
    return out


def build_geojson(records: list[FacilityRecord]) -> dict[str, Any]:
    features = []
    for i, r in enumerate(records, start=2):
        if not r.has_valid_coordinates():
            continue
        features.append(
            {
                "type": "Feature",
                "geometry": {
                    "type": "Point",
                    "coordinates": [r.longitude, r.latitude],
                },
                "properties": {
                    "source_row": i,
                    "facility_uid": r.facility_uid,
                    "facility_code": r.facility_code,
                    "facility_name": r.facility_name,
                    "facility_name_slug": slugify(r.facility_name),
                    "province": r.province,
                    "district": r.district,
                    "facility_type": r.facility_type,
                    "ownership": r.ownership,
                    "location_context": r.location_context,
                    "service_level": r.service_level,
                    "status": r.status,
                    "bed_capacity": r.bed_capacity,
                    "contact_phone_e164": r.contact_phone_e164,
                    "date_opened": None,
                    "date_closed": None,
                    "comments": None,
                    "source_dataset": "Master Health Facility List",
                    "source_dataset_date": r.source_dataset_date,
                    "has_coordinates": True,
                    "is_open": r.is_open(),
                },
            }
        )
    return {
        "type": "FeatureCollection",
        "name": "Zimbabwe Master Health Facilities 2024-07-23",
        "features": features,
    }


def build_lookup_values(records: list[FacilityRecord]) -> dict[str, list[str]]:
    keys = [
        ("province", lambda r: r.province),
        ("district", lambda r: r.district),
        ("facility_type", lambda r: r.facility_type),
        ("ownership", lambda r: r.ownership),
        ("location_context", lambda r: r.location_context),
        ("service_level", lambda r: r.service_level),
        ("status", lambda r: r.status),
    ]
    out: dict[str, list[str]] = {}
    for key, getter in keys:
        values = sorted({getter(r) for r in records if getter(r)})
        out[key] = values
    return out


def build_quality_report(records: list[FacilityRecord]) -> dict[str, Any]:
    code_counter = Counter(r.facility_code for r in records if r.facility_code)
    duplicate_codes = {code: count for code, count in code_counter.items() if count > 1}
    missing_codes = [r.facility_uid for r in records if not r.facility_code]
    invalid_coords = [
        {
            "facility_uid": r.facility_uid,
            "facility_code": r.facility_code,
            "facility_name": r.facility_name,
            "latitude": r.latitude,
            "longitude": r.longitude,
        }
        for r in records
        if not r.has_valid_coordinates()
    ]
    return {
        "summary": {
            "source_file": "Master Health Facility 23 July 2024 (1).xlsx",
            "source_dataset_date": "2024-07-23",
            "records_total": len(records),
            "records_open": sum(1 for r in records if r.is_open()),
            "records_with_valid_coordinates": sum(1 for r in records if r.has_valid_coordinates()),
            "records_missing_or_invalid_coordinates": len(invalid_coords),
            "records_missing_facility_code": len(missing_codes),
            "duplicate_facility_codes_count": len(duplicate_codes),
            "provinces": len({r.province for r in records if r.province}),
            "districts": len({r.district for r in records if r.district}),
            "levels": sorted({r.service_level for r in records if r.service_level}),
        },
        "duplicate_facility_codes": duplicate_codes,
        "missing_facility_codes": missing_codes,
        "missing_or_invalid_coordinates": invalid_coords,
    }


def build_staging_sql(records: list[FacilityRecord]) -> str:
    lines = [
        "-- Master Health Facility List seed derived from 23 July 2024 workbook.",
        "-- Import into a staging table first; do not overwrite production Tuso records without dedup/reconciliation.",
        "CREATE TABLE IF NOT EXISTS staging_master_health_facilities_20240723 (",
        "  facility_uid text PRIMARY KEY,",
        "  facility_code text,",
        "  facility_name text NOT NULL,",
        "  province text,",
        "  district text,",
        "  facility_type text,",
        "  ownership text,",
        "  location_context text,",
        "  service_level text,",
        "  status text,",
        "  bed_capacity integer,",
        "  latitude double precision,",
        "  longitude double precision,",
        "  contact_phone_e164 text,",
        "  source_dataset_date date",
        ");",
        "",
    ]

    def sql_val(value: Any) -> str:
        if value is None:
            return "NULL"
        if isinstance(value, (int, float)):
            return str(value)
        escaped = str(value).replace("'", "''")
        return f"'{escaped}'"

    for r in records:
        lines.append(
            "INSERT INTO staging_master_health_facilities_20240723 "
            "(facility_uid, facility_code, facility_name, province, district, facility_type, ownership, "
            "location_context, service_level, status, bed_capacity, latitude, longitude, contact_phone_e164, "
            f"source_dataset_date) VALUES ({sql_val(r.facility_uid)}, {sql_val(r.facility_code)}, "
            f"{sql_val(r.facility_name)}, {sql_val(r.province)}, {sql_val(r.district)}, {sql_val(r.facility_type)}, "
            f"{sql_val(r.ownership)}, {sql_val(r.location_context)}, {sql_val(r.service_level)}, {sql_val(r.status)}, "
            f"{sql_val(r.bed_capacity)}, {sql_val(r.latitude)}, {sql_val(r.longitude)}, {sql_val(r.contact_phone_e164)}, "
            f"'{r.source_dataset_date}') ON CONFLICT (facility_uid) DO NOTHING;"
        )
    return "\n".join(lines) + "\n"


def write_csv(records: list[FacilityRecord], path: Path) -> None:
    fieldnames = [
        "facility_uid",
        "facility_code",
        "facility_name",
        "province",
        "district",
        "facility_type",
        "ownership",
        "location_context",
        "service_level",
        "status",
        "bed_capacity",
        "latitude",
        "longitude",
        "contact_phone_e164",
        "source_dataset_date",
    ]
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for r in records:
            writer.writerow(asdict(r))


def main() -> int:
    if not SQL_PDF.exists():
        print(f"Missing PDF: {SQL_PDF}", file=sys.stderr)
        return 1

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    text = pdftotext(SQL_PDF)
    records = parse_records_from_sql(text)
    if len(records) != 2023:
        print(f"WARN: expected 2023 records, parsed {len(records)}", file=sys.stderr)

    clean_records = [asdict(r) for r in records]
    write_csv(records, OUT_DIR / "master_health_facilities_clean.csv")
    (OUT_DIR / "master_health_facilities_clean.json").write_text(
        json.dumps(clean_records, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    with (OUT_DIR / "master_health_facilities_clean.jsonl").open("w", encoding="utf-8") as f:
        for row in clean_records:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")

    (OUT_DIR / "tuso_facility_registry_seed.json").write_text(
        json.dumps(build_tuso_seed(records), indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    (OUT_DIR / "ndila_facility_location_seed.json").write_text(
        json.dumps(build_ndila_seed(records), indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    (OUT_DIR / "ndila_health_facilities.geojson").write_text(
        json.dumps(build_geojson(records), indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    (OUT_DIR / "facility_lookup_values.json").write_text(
        json.dumps(build_lookup_values(records), indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    (OUT_DIR / "facility_data_quality_report.json").write_text(
        json.dumps(build_quality_report(records), indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    (OUT_DIR / "tuso_staging_seed_postgres.sql").write_text(
        build_staging_sql(records),
        encoding="utf-8",
    )

    report = build_quality_report(records)
    summary = report["summary"]
    print(json.dumps(summary, indent=2))
    print(f"Generated artefacts in {OUT_DIR}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
