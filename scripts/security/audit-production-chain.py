#!/usr/bin/env python3
"""Audits the PRODUCTION filter chain of every service, not the file as a whole.

WHY THIS EXISTS ALONGSIDE audit-enforcement-posture.py
------------------------------------------------------
That script classifies a service by the set of `.anyRequest()` terminals anywhere in its
security configs. When it sees `permitAll` together with a binding of the
`disable-oauth-for-tests` flag it returns FLAG_IS_SEAM — "the flag is the seam" — and the
CP8 guard treats that as fine.

It never checks whether the branch the ESTATE runs is the closed one. So a config that is
`permitAll` in BOTH branches classifies as FLAG_IS_SEAM and passes, while being wide open.
Proven 2026-08-07 by mutation: community-service was edited to drop its
`oauth2ResourceServer` and terminate the production branch in `permitAll`, and
`check-enforcement-posture.sh` still reported "GUARD PASS ... exactly matching the reviewed
baseline". That is the most likely regression shape there is — someone opens the production
branch while the test branch already looked like this — and it was invisible.

WHAT "PRODUCTION CHAIN" MEANS HERE
----------------------------------
The estate has two idioms for the test escape hatch:

  1. an if/else on a `disable-oauth-for-tests` @Value inside one @Bean method
  2. two @Bean methods, each @ConditionalOnProperty on that flag

In both, the production chain is the one that configures `oauth2ResourceServer` — the test
chain deliberately does not, so that no JwtDecoder bean is required and the context still
starts. That is the marker used here.

RULES
-----
  R1  A service whose config mentions oauth2ResourceServer must terminate that chain in a
      CLOSED rule (authenticated / denyAll / hasRole / hasAuthority / access).
  R2  No production chain may permit /internal/v1/test-command. It is a POST that returns 201
      and echoes its body; the golden-contract suites that drive it are in-JVM and run on the
      test chain. tshepo-audit-service keeps it on its test chain, which is correct and is why
      this is scoped to the production chain rather than to the file.

Services with no security config at all are NOT reported here — that is
audit-enforcement-posture.py's job, and duplicating it would produce two red lines for one
defect.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SERVICES = REPO / "services"

CLOSED = ("authenticated", "denyAll", "hasRole", "hasAuthority", "access")
PROBE_PATH = "/internal/v1/test-command"


def strip_comments(src: str) -> str:
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    return re.sub(r"//[^\n]*", "", src)


def security_configs(service: Path) -> list[Path]:
    root = service / "src" / "main" / "java"
    if not root.is_dir():
        return []
    return [p for p in root.rglob("*.java")
            if "SecurityFilterChain" in p.read_text(errors="ignore")]


def bean_regions(body: str) -> list[str]:
    """Split a config into one region per @Bean method, so a test chain in the same file is
    not mistaken for the production one."""
    idxs = [m.start() for m in re.finditer(r"@Bean\b", body)]
    if not idxs:
        return [body]
    idxs.append(len(body))
    return [body[idxs[i]:idxs[i + 1]] for i in range(len(idxs) - 1)]


def production_regions(body: str) -> list[str]:
    """Regions that configure oauth2ResourceServer — i.e. the chain the estate runs.

    Within an if/else idiom the whole method is one region, so the `disableOauthForTests`
    true-branch is included. That is deliberate: it is a `permitAll` that is unreachable in
    production, and excluding it correctly needs brace parsing. Instead the true-branch is
    stripped textually below, which is enough for both idioms in this estate.
    """
    out = []
    for region in bean_regions(body):
        if "oauth2ResourceServer" not in region:
            continue
        # Drop an `if (disableOauthForTests) { ... }` block so its permitAll is not counted.
        region = re.sub(
            r"if\s*\(\s*disableOauthForTests\s*\)\s*\{.*?\n\s*\}",
            "", region, flags=re.S)
        # And the `} else { ... permitAll ... }` tail of the inverted idiom.
        region = re.sub(
            r"\}\s*else\s*\{[^{}]*permitAll[^{}]*\}", "", region, flags=re.S)
        out.append(region)
    return out


def audit(service: Path) -> dict | None:
    configs = security_configs(service)
    if not configs:
        return None  # audit-enforcement-posture.py owns this case
    findings = []
    saw_production = False
    for path in configs:
        body = strip_comments(path.read_text(errors="ignore"))
        for region in production_regions(body):
            saw_production = True
            terminals = re.findall(r"\.anyRequest\(\)\s*\.\s*(\w+)\(", region)
            if terminals and not any(t in CLOSED for t in terminals):
                findings.append(
                    f"R1 production chain terminates in {terminals} — not a closed rule "
                    f"({path.relative_to(REPO)})")
            if f'"{PROBE_PATH}"' in region:
                findings.append(
                    f"R2 production chain permits {PROBE_PATH} ({path.relative_to(REPO)})")
    if not saw_production:
        return None  # no oauth2 chain at all — again, the other script's case
    return {"service": service.name, "findings": findings}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    rows = []
    for service in sorted(p for p in SERVICES.iterdir() if p.is_dir()):
        r = audit(service)
        if r:
            rows.append(r)

    if args.json:
        json.dump(rows, sys.stdout, indent=1)
        print()
        return 0

    bad = [r for r in rows if r["findings"]]
    print(f"production chains audited: {len(rows)}")
    for r in bad:
        for f in r["findings"]:
            print(f"  {r['service']}: {f}")
    print(f"services with findings: {len(bad)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
