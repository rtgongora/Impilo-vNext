#!/usr/bin/env python3
"""Apply Impilo semantic token class replacements across UI workspaces."""

from __future__ import annotations

import re
from pathlib import Path

UI_ROOT = Path(__file__).resolve().parents[2] / "ui"

EXTENSIONS = {".tsx", ".ts", ".jsx", ".js", ".css", ".mdx"}

REPLACEMENTS: list[tuple[str, str]] = [
    (r"\bbg-slate-50/90\b", "bg-background/90"),
    (r"\bbg-slate-50/80\b", "bg-background/80"),
    (r"\bbg-slate-50/50\b", "bg-background/50"),
    (r"\bbg-slate-50\b", "bg-background"),
    (r"\bbg-slate-100\b", "bg-neutral-100"),
    (r"\bbg-slate-900/40\b", "bg-neutral-900/40"),
    (r"\bbg-slate-950/30\b", "bg-neutral-900/30"),
    (r"\bbg-slate-950/95\b", "bg-card/95"),
    (r"\bbg-slate-950\b", "bg-card"),
    (r"\bbg-slate-900\b", "bg-neutral-900"),
    (r"\bborder-slate-700\b", "border-border"),
    (r"\bborder-slate-800\b", "border-border"),
    (r"\bborder-slate-200\b", "border-border"),
    (r"\bborder-slate-100\b", "border-border"),
    (r"\bborder-slate-300\b", "border-border"),
    (r"\btext-slate-900\b", "text-foreground"),
    (r"\btext-slate-800\b", "text-foreground"),
    (r"\btext-slate-700\b", "text-foreground"),
    (r"\btext-slate-600\b", "text-muted-foreground"),
    (r"\btext-slate-500\b", "text-muted-foreground"),
    (r"\btext-slate-400\b", "text-muted-foreground"),
    (r"\btext-slate-300\b", "text-muted-foreground"),
    (r"\btext-slate-100\b", "text-foreground"),
    (r"\bhover:bg-slate-100\b", "hover:bg-primary-soft"),
    (r"\bhover:bg-slate-900\b", "hover:bg-primary-soft"),
    (r"\bhover:text-slate-600\b", "hover:text-foreground"),
    (r"\bhover:text-slate-700\b", "hover:text-foreground"),
    (r"\bhover:border-slate-300\b", "hover:border-border"),
    (r"\bbg-gray-50/80\b", "bg-background/80"),
    (r"\bbg-gray-50/50\b", "bg-background/50"),
    (r"\bbg-gray-50\b", "bg-background"),
    (r"\bbg-gray-100\b", "bg-neutral-100"),
    (r"\bbg-gray-200\b", "bg-neutral-100"),
    (r"\bborder-gray-300\b", "border-border"),
    (r"\bborder-gray-200\b", "border-border"),
    (r"\bborder-gray-100\b", "border-border"),
    (r"\btext-gray-900\b", "text-foreground"),
    (r"\btext-gray-800\b", "text-foreground"),
    (r"\btext-gray-700\b", "text-foreground"),
    (r"\btext-gray-600\b", "text-muted-foreground"),
    (r"\btext-gray-500\b", "text-muted-foreground"),
    (r"\btext-gray-400\b", "text-muted-foreground"),
    (r"\btext-gray-300\b", "text-muted-foreground"),
    (r"\bhover:bg-gray-50\b", "hover:bg-primary-soft"),
    (r"\bhover:bg-gray-100\b", "hover:bg-primary-soft"),
    (r"\bhover:text-gray-600\b", "hover:text-foreground"),
    (r"\bhover:text-gray-700\b", "hover:text-foreground"),
    (r"\bhover:text-gray-800\b", "hover:text-foreground"),
    (r"\bhover:text-gray-900\b", "hover:text-foreground"),
    (r"\bhover:border-gray-300\b", "hover:border-border"),
    (r"\bbg-white/95\b", "bg-card/95"),
    (r"\bbg-white/90\b", "bg-card/90"),
    (r"\bbg-white/80\b", "bg-card/80"),
    (r"\bbg-white/50\b", "bg-card/50"),
    (r"\bbg-white\b", "bg-card"),
    (r"\bborder-emerald-200\b", "border-success/25"),
    (r"\bbg-emerald-50\b", "bg-success-soft"),
    (r"\btext-emerald-900\b", "text-impilo-700"),
    (r"\btext-emerald-800\b", "text-impilo-700"),
    (r"\btext-emerald-700\b", "text-impilo-700"),
    (r"\btext-emerald-600\b", "text-primary"),
    (r"\bborder-indigo-200\b", "border-info/25"),
    (r"\bbg-indigo-50\b", "bg-info-soft"),
    (r"\btext-indigo-950\b", "text-impilo-700"),
    (r"\btext-indigo-900\b", "text-impilo-700"),
    (r"\btext-indigo-800\b", "text-impilo-700"),
    (r"\btext-indigo-700\b", "text-impilo-700"),
    (r"\bbg-indigo-700\b", "bg-primary"),
    (r"\bborder-blue-200\b", "border-info/25"),
    (r"\bbg-blue-50\b", "bg-info-soft"),
    (r"\btext-blue-700\b", "text-impilo-700"),
    (r"\bborder-amber-200\b", "border-warning/35"),
    (r"\bbg-amber-50\b", "bg-warning-soft"),
    (r"\btext-amber-950\b", "text-warning-foreground"),
    (r"\btext-amber-900\b", "text-warning-foreground"),
    (r"\btext-amber-800\b", "text-warning-foreground"),
    (r"\btext-amber-700\b", "text-warning-foreground"),
    (r"\bborder-rose-200\b", "border-danger/28"),
    (r"\bbg-rose-50\b", "bg-danger-soft"),
    (r"\btext-rose-950\b", "text-danger"),
    (r"\btext-rose-900\b", "text-danger"),
    (r"\btext-rose-700\b", "text-danger"),
    (r"\bbg-red-50\b", "bg-danger-soft"),
    (r"\bborder-red-200\b", "border-danger/28"),
    (r"\btext-red-700\b", "text-danger"),
    (r"\bborder-purple-200\b", "border-warning/35"),
    (r"\bbg-purple-50\b", "bg-warning-soft"),
    (r"\btext-purple-700\b", "text-warning-foreground"),
    (r"\bborder-fuchsia-200\b", "border-border"),
    (r"\bbg-fuchsia-50\b", "bg-neutral-100"),
    (r"\btext-fuchsia-700\b", "text-muted-foreground"),
    (r"\btext-blue-600\b", "text-primary"),
    (r"\bhover:text-blue-600\b", "hover:text-primary"),
    (r"\bhover:text-impilo-600\b", "hover:text-primary"),
    (r"\btext-impilo-600\b", "text-primary"),
    (r"\btext-impilo-700\b", "text-primary-hover"),
    (r"\bhover:text-impilo-700\b", "hover:text-primary-hover"),
    (r"\bborder-impilo-200\b", "border-primary/25"),
    (r"\bbg-impilo-50\b", "bg-primary-soft"),
    (r"\bbg-impilo-100\b", "bg-primary-soft"),
    (r"\bhover:bg-impilo-50\b", "hover:bg-primary-soft"),
    (r"\bhover:bg-impilo-100\b", "hover:bg-primary-soft"),
    (r"\bhover:border-impilo-200\b", "hover:border-primary/25"),
    (r"\bborder-impilo-100\b", "border-primary/20"),
    (r"\btext-impilo-100\b", "text-white/85"),
    (r"\btext-impilo-200\b", "text-white/70"),
    (r"\btext-impilo-500\b", "text-primary"),
    (r"\bbg-impilo-500\b", "bg-primary"),
    (r"\bbg-impilo-600\b", "bg-primary-hover"),
    (r"\bhover:bg-impilo-600\b", "hover:bg-primary-hover"),
    (r"\bfocus:ring-impilo-400\b", "focus:ring-primary/40"),
    (r"\bfrom-impilo-50\b", "from-primary-soft"),
    (r"\bto-white\b", "to-card"),
    (r"\bmin-h-screen bg-card\b", "min-h-screen bg-background"),
    (r"\bflex h-screen bg-card\b", "flex h-screen bg-background"),
    # Dark mode slate/gray cleanup
    (r"\bdark:text-slate-50\b", "dark:text-foreground"),
    (r"\bdark:text-slate-100\b", "dark:text-foreground"),
    (r"\bdark:text-slate-200\b", "dark:text-foreground"),
    (r"\bdark:text-slate-300\b", "dark:text-muted-foreground"),
    (r"\bdark:text-gray-100\b", "dark:text-foreground"),
    (r"\bdark:text-gray-200\b", "dark:text-foreground"),
    (r"\bdark:border-slate-600\b", "dark:border-border"),
    (r"\bdark:border-slate-700\b", "dark:border-border"),
    (r"\bdark:border-gray-700\b", "dark:border-border"),
    (r"\bdark:bg-slate-800\b", "dark:bg-neutral-900"),
    (r"\bdark:bg-gray-950/60\b", "dark:bg-card/60"),
    (r"\bdark:bg-gray-950\b", "dark:bg-card"),
    (r"\bdark:hover:bg-slate-800\b", "dark:hover:bg-neutral-900"),
    (r"\bdark:hover:bg-slate-900\b", "dark:hover:bg-neutral-900"),
    (r"\bhover:bg-slate-200\b", "hover:bg-primary-soft"),
    (r"\bhover:bg-slate-800\b", "hover:bg-primary-hover"),
    (r"\bbg-slate-200\b", "bg-border"),
    (r"\bbg-slate-500\b", "bg-neutral-500"),
    (r"\bbg-slate-800\b", "bg-primary-hover"),
    (r"\btext-slate-950\b", "text-foreground"),
    (r"\bhover:text-gray-200\b", "hover:text-foreground"),
    (r"\btext-gray-200\b", "text-muted-foreground"),
    (r"\btext-amber-200\b", "text-warning-foreground"),
    (r"\bbg-teal-700\b", "bg-primary"),
    (r"\bbg-gray-400\b", "bg-neutral-500"),
    (r"\bbg-gray-800\b", "bg-primary-hover"),
    (r"\bbg-gray-900\b", "bg-neutral-900"),
    (r"\bbg-gray-950\b", "bg-neutral-900"),
]

WORKSPACES = [
    "shared-ui",
    "one-ui-shell",
    "ops-console",
    "portal",
    "ops-docs",
    "self-service",
    "butano-web",
    "pct-web",
    "zibo-web",
    "oros-web",
    "pharmacy-web",
    "inventory-web",
    "support-console",
    "developer-console",
    "costa-console",
    "msika-web",
    "msika-flow-ops",
    "msika-flow-portal",
    "msika-flow-vendor",
    "mushex-payer-portal",
    "mushex-ops-console",
    "mushex-finance-console",
    "ehr",
]


def transform(content: str) -> str:
    for pattern, repl in REPLACEMENTS:
        content = re.sub(pattern, repl, content)
    return content


def iter_source_files() -> list[Path]:
    files: list[Path] = []
    for ws in WORKSPACES:
        root = UI_ROOT / ws
        if not root.is_dir():
            continue
        for sub in ("src", "components", "."):
            base = root / sub if sub != "." else root
            if not base.is_dir():
                continue
            if sub == ".":
                # shared-ui root ts/tsx only (not rglob everything)
                for path in base.glob("*"):
                    if path.suffix in EXTENSIONS:
                        files.append(path)
                for path in (base / "components").rglob("*") if (base / "components").is_dir() else []:
                    if path.suffix in EXTENSIONS:
                        files.append(path)
            else:
                files.extend(p for p in base.rglob("*") if p.suffix in EXTENSIONS)
    return sorted(set(files))


def main() -> None:
    changed = 0
    for path in iter_source_files():
        original = path.read_text(encoding="utf-8")
        updated = transform(original)
        if updated != original:
            path.write_text(updated, encoding="utf-8")
            changed += 1
    print(f"Updated {changed} files")


if __name__ == "__main__":
    main()
