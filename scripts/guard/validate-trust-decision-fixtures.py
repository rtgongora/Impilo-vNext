#!/usr/bin/env python3
"""Validate shared tshepo.trust.v1 fixtures with a real JSON Schema validator.

Layers proven here:
  1. JSON Schema (contracts/schemas/trust-decision-v1.schema.json), Draft 2020-12,
     with strict RFC3339 date-time format checking.
  2. OpenAPI-compatible models: the bundled OpenAPI components
     (reports/contracts/tshepo-trust-decision-v1.bundled.json, produced by
     scripts/completeness/lint-trust-decision-openapi.mjs) converted from
     OpenAPI 3.0 `nullable` style to JSON Schema type unions and validated
     with the same validator.

Rules:
  - every `valid` fixture must pass both layers;
  - every `invalid` fixture tagged with "schema" in `rejects` must FAIL the
    JSON Schema layer;
  - invalid fixtures NOT tagged "schema" must PASS the JSON Schema layer
    (proving the rejection is genuinely semantic and owned by Java/TS logic);
  - OpenAPI layer must reject the same tagged fixtures except those listed in
    OPENAPI_LOOSER (constructs OpenAPI 3.0 cannot express, e.g. propertyNames).

Java and TypeScript validation of the same fixtures live in
TrustContractFixturesTest.java and trust-decision-contracts.test.ts.
"""
from __future__ import annotations

import json
import re
import sys
from datetime import datetime
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker

REPO = Path(__file__).resolve().parents[2]
SCHEMA_FILE = REPO / "contracts/schemas/trust-decision-v1.schema.json"
FIXTURES_FILE = REPO / "contracts/trust-decision/fixtures/trust-decision-v1.fixtures.json"
BUNDLED_OPENAPI = REPO / "reports/contracts/tshepo-trust-decision-v1.bundled.json"

# Invalid fixtures that OpenAPI 3.0 models cannot structurally reject
# (JSON Schema propertyNames has no OpenAPI 3.0 equivalent). They must still be
# rejected by the JSON Schema, Java, and TypeScript layers.
OPENAPI_LOOSER = {"audit-event-clinical-payload-key"}

RFC3339 = re.compile(
    r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})$"
)

format_checker = FormatChecker()


@format_checker.checks("date-time", raises=ValueError)
def _check_date_time(value: object) -> bool:
    if not isinstance(value, str):
        return True
    if not RFC3339.match(value):
        raise ValueError(f"not RFC3339: {value!r}")
    datetime.fromisoformat(value.replace("Z", "+00:00"))
    return True


def openapi_to_jsonschema(node: object) -> object:
    """Convert bundled OpenAPI 3.0 schemas (nullable style) to JSON Schema."""
    if isinstance(node, list):
        return [openapi_to_jsonschema(x) for x in node]
    if isinstance(node, dict):
        out = {}
        for key, value in node.items():
            if key == "nullable":
                continue
            out[key] = openapi_to_jsonschema(value)
        if node.get("nullable") is True and isinstance(out.get("type"), str):
            out["type"] = [out["type"], "null"]
        return out
    return node


def validator_for(defs: dict, type_name: str) -> Draft202012Validator:
    if type_name not in defs:
        raise KeyError(f"type {type_name} missing from schema definitions")
    return Draft202012Validator(
        {"$ref": f"#/$defs/{type_name}", "$defs": defs},
        format_checker=format_checker,
    )


def errors_for(validator: Draft202012Validator, data: object) -> list[str]:
    return [
        f"{'/'.join(str(p) for p in e.absolute_path) or '<root>'}: {e.message}"
        for e in validator.iter_errors(data)
    ]


def main() -> int:
    schema = json.loads(SCHEMA_FILE.read_text())
    fixtures = json.loads(FIXTURES_FILE.read_text())
    json_defs = schema["$defs"]

    if not BUNDLED_OPENAPI.exists():
        print(
            "ERROR: bundled OpenAPI missing — run "
            "scripts/completeness/lint-trust-decision-openapi.mjs first",
            file=sys.stderr,
        )
        return 2
    bundled = json.loads(BUNDLED_OPENAPI.read_text())
    openapi_defs = {
        name: openapi_to_jsonschema(sch)
        for name, sch in bundled["components"]["schemas"].items()
    }

    failures: list[str] = []
    counts = {"valid": 0, "invalid": 0, "checks": 0}

    for fixture in fixtures["valid"]:
        name, type_name, data = fixture["name"], fixture["type"], fixture["data"]
        counts["valid"] += 1
        for layer, defs in (("jsonschema", json_defs), ("openapi", openapi_defs)):
            counts["checks"] += 1
            errs = errors_for(validator_for(defs, type_name), data)
            if errs:
                failures.append(f"VALID fixture '{name}' rejected by {layer}: {errs[:3]}")

    for fixture in fixtures["invalid"]:
        name, type_name, data = fixture["name"], fixture["type"], fixture["data"]
        rejects = set(fixture.get("rejects", []))
        counts["invalid"] += 1

        counts["checks"] += 1
        json_errs = errors_for(validator_for(json_defs, type_name), data)
        if "schema" in rejects and not json_errs:
            failures.append(f"INVALID fixture '{name}' NOT rejected by jsonschema")
        if "schema" not in rejects and json_errs:
            failures.append(
                f"INVALID fixture '{name}' unexpectedly rejected by jsonschema "
                f"(claimed semantic-only): {json_errs[:2]}"
            )

        counts["checks"] += 1
        oa_errs = errors_for(validator_for(openapi_defs, type_name), data)
        must_fail_openapi = "schema" in rejects and name not in OPENAPI_LOOSER
        if must_fail_openapi and not oa_errs:
            failures.append(f"INVALID fixture '{name}' NOT rejected by openapi model")
        if name in OPENAPI_LOOSER and oa_errs:
            failures.append(
                f"fixture '{name}' expected to pass looser OpenAPI model but failed: {oa_errs[:2]}"
            )

    print(
        f"trust-decision fixtures: {counts['valid']} valid + {counts['invalid']} invalid "
        f"fixtures, {counts['checks']} schema checks, {len(failures)} failure(s)"
    )
    for failure in failures:
        print(f"  FAIL {failure}")
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main())
