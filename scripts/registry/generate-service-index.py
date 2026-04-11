#!/usr/bin/env python3
"""
Regenerate docs/registry/services-index.md from docs/registry/services-registry.yaml.

Preferred (Node, same output as CI/local docs):
  cd scripts/registry && npm install && npm run generate

Optional (Python):
  pip install -r scripts/registry/requirements.txt
  python scripts/registry/generate-service-index.py
"""

from __future__ import annotations

import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REGISTRY_PATH = ROOT / "docs" / "registry" / "services-registry.yaml"
OUTPUT_PATH = ROOT / "docs" / "registry" / "services-index.md"


def main() -> int:
    try:
        import yaml  # type: ignore
    except ImportError:
        print("Install PyYAML: pip install -r scripts/registry/requirements.txt", file=sys.stderr)
        return 1

    if not REGISTRY_PATH.is_file():
        print(f"Missing registry: {REGISTRY_PATH}", file=sys.stderr)
        return 1

    data = yaml.safe_load(REGISTRY_PATH.read_text(encoding="utf-8"))
    version = data.get("registry_version", "?")
    libs = data.get("libraries") or []
    services = data.get("services") or []

    def sort_key(entry: dict) -> str:
        return entry.get("maven_module") or entry.get("id") or ""

    libs = sorted(libs, key=sort_key)
    services = sorted(services, key=sort_key)

    lines: list[str] = [
        "# Impilo service index (generated)",
        "",
        f"**Registry version:** `{version}`  ",
        f"**Generated:** {datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M UTC')}  ",
        "**Source:** [`services-registry.yaml`](./services-registry.yaml)  ",
        "",
        "Regenerate:",
        "",
        "```bash",
        "pip install -r scripts/registry/requirements.txt",
        "python scripts/registry/generate-service-index.py",
        "```",
        "",
        "---",
        "",
        "## Libraries (Maven reactor, not deployable)",
        "",
        "| ID | Maven module | Path |",
        "|----|--------------|------|",
    ]
    for e in libs:
        mid = e.get("maven_module", "")
        path = e.get("path", f"services/{mid}" if not mid.startswith("libs/") else mid)
        lines.append(f"| `{e.get('id', '')}` | `{mid}` | `{path}` |")
    lines.extend(
        [
            "",
            "---",
            "",
            "## Services & agents",
            "",
            "| Maven module | ID | Plane | Sovereign | Group | Protocol | HTTP port | Product names |",
            "|--------------|-----|-------|-----------|-------|----------|-----------|---------------|",
        ]
    )
    for e in services:
        names = e.get("product_names") or []
        names_s = ", ".join(names) if names else "—"
        sg = e.get("sovereign_group") or "—"
        sov = "yes" if e.get("sovereign") else "—"
        port = e.get("default_http_port")
        port_s = str(port) if port is not None else "—"
        lines.append(
            f"| `{e.get('maven_module', '')}` | `{e.get('id', '')}` | {e.get('plane', '')} | {sov} | {sg} | "
            f"{e.get('primary_protocol', '')} | {port_s} | {names_s} |"
        )
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("*Sovereign* marks membership in the nine national dual-mode boundaries (see `TODO.md` / doctrine). "
                 "*Group* is the logical sovereign name when one product spans multiple modules.")
    lines.append("")

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {OUTPUT_PATH.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
