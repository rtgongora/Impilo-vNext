#!/usr/bin/env python3
"""Measure whether each service's OAuth bypass flag is actually load-bearing.

CP7 produced the finding this exists to generalise: `workforce-governance-service` carried
`IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true`, so retiring that flag looked like the way to
enforce authentication there. It was not. Its `SecurityConfig` ended in an UNCONDITIONAL
`.anyRequest().permitAll()` — the flag gated nothing, and flipping it would have changed no
behaviour whatsoever while reading, in every report, as "bypass retired".

That is the difference between retiring a bypass and performing the retirement of a bypass.

So before enforcing any CP8 cohort, each service is classified by what its source ACTUALLY does:

  FLAG_IS_SEAM        the flag genuinely gates the rule — flipping it changes behaviour
  UNCONDITIONAL_OPEN  permitAll regardless of the flag — flipping it is theatre
  ALREADY_ENFORCING   authenticated() with no bypass — nothing to retire
  NO_SECURITY_CONFIG  no config at all; posture comes from Spring Boot defaults or nothing
  UNKNOWN             a shape this script cannot read — reported, never assumed benign

UNKNOWN is deliberately not folded into a safe-looking bucket. A classifier that guesses is worse
than one that admits it cannot tell, because the guess is what gets quoted in a status report.

Usage:  python3 scripts/security/audit-enforcement-posture.py [--json]
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SERVICES = REPO / "services"

# The estate spells this two ways. `live-service` reads IMPILO_DISABLE_OAUTH_FOR_TESTS — missing
# the SECURITY_ segment — so the estate-wide flag never reached it. Both are matched, because a
# script that knew only the canonical name would report that service as having no bypass at all.
BYPASS_PROPERTY = re.compile(
    r"impilo\.security\.disable-oauth-for-tests|IMPILO_(?:SECURITY_)?DISABLE_OAUTH_FOR_TESTS",
    re.IGNORECASE,
)

ANY_REQUEST = re.compile(r"\.anyRequest\(\)\s*\.\s*(\w+)\s*\(")
# A @Value/@ConditionalOnProperty binding of the bypass onto a field or method.
FLAG_BINDING = re.compile(
    r'@(?:Value|ConditionalOnProperty)\s*\([^)]*disable-oauth-for-tests[^)]*\)', re.IGNORECASE
)


def java_sources(service: Path) -> list[Path]:
    root = service / "src" / "main" / "java"
    return list(root.rglob("*.java")) if root.is_dir() else []


def security_configs(service: Path) -> list[Path]:
    return [p for p in java_sources(service)
            if "SecurityConfig" in p.name or "securityFilterChain" in p.read_text(errors="ignore")]


def strip_comments(src: str) -> str:
    """Remove comments before reasoning about code.

    A doc block explaining a bypass matches every pattern here. Classifying on commentary is the
    same error as a guard that greps its own explanatory text.
    """
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    return re.sub(r"//[^\n]*", "", src)


def classify(service: Path) -> dict:
    configs = security_configs(service)
    resources = list((service / "src" / "main" / "resources").glob("application*.y*ml")) \
        if (service / "src" / "main" / "resources").is_dir() else []

    declares_bypass = any(BYPASS_PROPERTY.search(p.read_text(errors="ignore"))
                          for p in configs + resources)

    if not configs:
        return {"service": service.name, "posture": "NO_SECURITY_CONFIG",
                "declares_bypass": declares_bypass, "any_request": None, "detail":
                "no securityFilterChain found in src/main/java"}

    verdicts: set[str] = set()
    flag_bound = False
    for path in configs:
        body = strip_comments(path.read_text(errors="ignore"))
        verdicts.update(m.group(1) for m in ANY_REQUEST.finditer(body))
        if FLAG_BINDING.search(body) or BYPASS_PROPERTY.search(body):
            flag_bound = True

    if not verdicts:
        return {"service": service.name, "posture": "UNKNOWN", "declares_bypass": declares_bypass,
                "any_request": None,
                "detail": "securityFilterChain present but no .anyRequest() terminal rule found"}

    open_ = {"permitAll"} & verdicts
    closed = {"authenticated", "denyAll", "hasRole", "hasAuthority", "access"} & verdicts

    if open_ and not flag_bound:
        # The decisive case. permitAll with no flag binding in the same class means the bypass
        # env var is inert here: it is open whether the flag is true or false.
        posture = "UNCONDITIONAL_OPEN"
    elif open_ and flag_bound:
        posture = "FLAG_IS_SEAM"
    elif closed and not open_:
        posture = "ALREADY_ENFORCING"
    else:
        posture = "UNKNOWN"

    return {"service": service.name, "posture": posture, "declares_bypass": declares_bypass,
            "any_request": sorted(verdicts), "flag_bound_in_config": flag_bound,
            "detail": f"{len(configs)} security config(s)"}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    rows = [classify(s) for s in sorted(SERVICES.iterdir())
            if s.is_dir() and (s / "src" / "main").is_dir()]

    if args.json:
        print(json.dumps(rows, indent=2))
        return 0

    buckets: dict[str, list[str]] = {}
    for r in rows:
        buckets.setdefault(r["posture"], []).append(r["service"])

    print(f"Enforcement posture across {len(rows)} services\n")
    for posture in ("FLAG_IS_SEAM", "ALREADY_ENFORCING", "UNCONDITIONAL_OPEN",
                    "NO_SECURITY_CONFIG", "UNKNOWN"):
        names = sorted(buckets.get(posture, []))
        print(f"  {posture:<20} {len(names):>3}")
    print()

    theatre = [r for r in rows
               if r["posture"] in ("UNCONDITIONAL_OPEN", "NO_SECURITY_CONFIG")
               and r["declares_bypass"]]
    print(f"Services where retiring the bypass flag would change NOTHING: {len(theatre)}")
    for r in theatre[:15]:
        print(f"    {r['service']:<45} {r['posture']}")
    if len(theatre) > 15:
        print(f"    … and {len(theatre) - 15} more")

    unknown = [r for r in rows if r["posture"] == "UNKNOWN"]
    if unknown:
        print(f"\nUNKNOWN — not classifiable, must not be assumed safe: {len(unknown)}")
        for r in unknown[:10]:
            print(f"    {r['service']:<45} {r['detail']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
