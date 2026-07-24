#!/usr/bin/env python3
"""Find internal platform names that can appear on public source surfaces."""

from __future__ import annotations

import glob
import re
import sys
from pathlib import Path

import yaml


def strip_comments(line: str, in_block: bool) -> tuple[str, bool]:
    output: list[str] = []
    i = 0
    quote: str | None = None
    escaped = False

    while i < len(line):
        if in_block:
            end = line.find("*/", i)
            if end < 0:
                return "".join(output), True
            i = end + 2
            in_block = False
            continue

        char = line[i]
        pair = line[i : i + 2]
        if quote:
            output.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            i += 1
            continue

        if char in {'"', "'", "`"}:
            quote = char
            output.append(char)
            i += 1
            continue
        if pair == "/*":
            in_block = True
            i += 2
            continue
        if pair == "//":
            break
        output.append(char)
        i += 1

    return "".join(output), in_block


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: check-public-naming.py <dictionary.yml>", file=sys.stderr)
        return 2

    dictionary = yaml.safe_load(Path(sys.argv[1]).read_text(encoding="utf-8"))
    terms = [entry["internal"] for entry in dictionary["terms"]]
    pattern = re.compile(r"\b(" + "|".join(map(re.escape, terms)) + r")\b", re.IGNORECASE)

    paths: set[str] = set()
    for surface in dictionary.get("scan_surfaces", []):
        paths.update(glob.glob(surface, recursive=True))

    hits: list[str] = []
    for path in sorted(paths):
        source = Path(path)
        if not source.is_file() or source.suffix.lower() in {".png", ".svg", ".ico"}:
            continue
        in_block = False
        for number, raw in enumerate(source.read_text(encoding="utf-8").splitlines(), 1):
            line, in_block = strip_comments(raw, in_block)
            stripped = line.lstrip()
            # Module paths are implementation wiring, not rendered copy or public URLs.
            if (
                stripped.startswith("import ")
                or "import(" in stripped
                or re.match(r"^}?\s*from\s+[\"']", stripped)
            ):
                continue
            if pattern.search(line):
                hits.append(f"{path}:{number}:{raw.strip()}")

    if hits:
        print("\n".join(hits))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
