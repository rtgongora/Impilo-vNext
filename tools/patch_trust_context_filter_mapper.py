#!/usr/bin/env python3
"""Wire TrustContextFilter(ObjectMapper) across SecurityConfig beans (obligations-aware visibility)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "services"

BEAN_OLD = """    public TrustContextFilter trustContextFilter() {
        return new TrustContextFilter();
    }"""
BEAN_NEW = """    public TrustContextFilter trustContextFilter(ObjectMapper objectMapper) {
        return new TrustContextFilter(objectMapper);
    }"""

FR_OLD = """    public FilterRegistrationBean<TrustContextFilter> trustContextFilter() {
        FilterRegistrationBean<TrustContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TrustContextFilter());"""
FR_NEW = """    public FilterRegistrationBean<TrustContextFilter> trustContextFilter(ObjectMapper objectMapper) {
        FilterRegistrationBean<TrustContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TrustContextFilter(objectMapper));"""


def ensure_mapper_import(text: str) -> str:
    if "import com.fasterxml.jackson.databind.ObjectMapper;" in text:
        return text
    return re.sub(
        r"(^package [^;]+;\s*\n)",
        r"\1\nimport com.fasterxml.jackson.databind.ObjectMapper;\n",
        text,
        count=1,
        flags=re.MULTILINE,
    )


def inject_trust_context_filter_param(text: str) -> str:
    if ".addFilterBefore(trustContextFilter," not in text:
        return text
    if re.search(
        r"SecurityFilterChain\s+\w+\([^)]*\bTrustContextFilter\s+trustContextFilter\b",
        text,
        re.DOTALL,
    ):
        return text

    subs = [
        (
            r"(public SecurityFilterChain\s+filterChain)\(HttpSecurity http\)(\s+throws)",
            r"\1(HttpSecurity http, TrustContextFilter trustContextFilter)\2",
        ),
        (
            r"(public SecurityFilterChain\s+filterChain)\(HttpSecurity http,\s*\n(\s*)@Value",
            r"\1(HttpSecurity http, TrustContextFilter trustContextFilter,\n\2@Value",
        ),
        (
            r"(public SecurityFilterChain\s+filterChain)\(\s*\n(\s*)HttpSecurity http,\s*\n(\s*)@Value",
            r"\1(\n\2HttpSecurity http, TrustContextFilter trustContextFilter,\n\3@Value",
        ),
        (
            r"(public SecurityFilterChain\s+securityFilterChain)\(HttpSecurity http\)(\s+throws)",
            r"\1(HttpSecurity http, TrustContextFilter trustContextFilter)\2",
        ),
        (
            r"(public SecurityFilterChain\s+securityFilterChain)\(HttpSecurity http,\s*\n(\s*)@Value",
            r"\1(HttpSecurity http, TrustContextFilter trustContextFilter,\n\2@Value",
        ),
        (
            r"(public SecurityFilterChain\s+securityFilterChain)\(\s*\n(\s*)HttpSecurity http,\s*\n(\s*)@Value",
            r"\1(\n\2HttpSecurity http, TrustContextFilter trustContextFilter,\n\3@Value",
        ),
        (
            r"(public SecurityFilterChain\s+chain)\(HttpSecurity http, @Value",
            r"\1(HttpSecurity http, TrustContextFilter trustContextFilter, @Value",
        ),
    ]
    for pat, repl in subs:
        text, n = re.subn(pat, repl, text, count=1)
        if n:
            return text
    print("WARN: could not inject TrustContextFilter param", file=sys.stderr)
    return text


def patch_text(text: str) -> str:
    text = ensure_mapper_import(text)
    if BEAN_OLD in text:
        text = text.replace(BEAN_OLD, BEAN_NEW)
    if FR_OLD in text:
        text = text.replace(FR_OLD, FR_NEW)
    text = text.replace(".addFilterBefore(trustContextFilter(),", ".addFilterBefore(trustContextFilter,")
    text = inject_trust_context_filter_param(text)
    return text


def main() -> int:
    paths = sorted(
        set(ROOT.rglob("SecurityConfig.java")) | set(ROOT.rglob("TestSecurityConfig.java"))
    )
    changed = 0
    for p in paths:
        raw = p.read_text(encoding="utf-8").replace("\r\n", "\n")
        if "zw.gov.mohcc.impilo.shared.auth.TrustContextFilter" not in raw:
            continue
        if "reporting-service" in str(p).replace("\\", "/") and p.name == "SecurityConfig.java":
            continue
        new = patch_text(raw)
        if new != raw:
            p.write_text(new, encoding="utf-8")
            print(p.relative_to(ROOT.parent))
            changed += 1
    print(f"updated {changed} file(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
