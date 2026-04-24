#!/usr/bin/env python3
"""Patch SecurityConfig.java: add test profile chain without OAuth2 RS for MockMvc ITs."""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1] / "services"
IMPORT_LINE = "import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;\n"
ANCHOR = "import org.springframework.context.annotation.Bean;"


def patch_file(p: pathlib.Path) -> bool:
    t = p.read_text(encoding="utf-8")
    if "testFilterChain" in t or "disable-oauth-for-tests" in t:
        return False
    if "TrustContextFilter" not in t or "oauth2ResourceServer" not in t:
        return False
    if ANCHOR not in t:
        return False
    if IMPORT_LINE.strip() not in t:
        t = t.replace(ANCHOR, IMPORT_LINE + ANCHOR, 1)

    m = re.search(
        r"(\s*)@Bean\s*\n\s*public SecurityFilterChain\s+(\w+)\(HttpSecurity http,\s*TrustContextFilter trustContextFilter\)\s*throws Exception\s*\{",
        t,
    )
    if not m:
        return False
    indent = m.group(1)
    start = m.start()
    i = t.find("{", m.end() - 1)
    depth = 0
    j = i
    end = None
    while j < len(t):
        if t[j] == "{":
            depth += 1
        elif t[j] == "}":
            depth -= 1
            if depth == 0:
                end = j + 1
                while end < len(t) and t[end] in " \t\r\n":
                    end += 1
                break
        j += 1
    if end is None:
        return False
    old_method = t[start:end]
    body_m = re.search(r"throws Exception\s*\{([\s\S]*)\}\s*$", old_method)
    if not body_m:
        return False
    body = body_m.group(1)
    new_block = (
        f"{indent}/**\n"
        f"{indent} * JVM tests (MockMvc, @ActiveProfiles(\"test\")) do not send Bearer tokens; open the chain\n"
        f"{indent} * while keeping TrustContextFilter so v1.1 header / idempotency behaviour stays testable.\n"
        f"{indent} */\n"
        f"{indent}@Bean\n"
        f'{indent}@ConditionalOnProperty(name = "impilo.security.disable-oauth-for-tests", havingValue = "true")\n'
        f"{indent}public SecurityFilterChain testFilterChain(HttpSecurity http, TrustContextFilter trustContextFilter) throws Exception {{\n"
        f"{indent}        http\n"
        f"{indent}                .csrf(csrf -> csrf.disable())\n"
        f"{indent}                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))\n"
        f"{indent}                .addFilterBefore(trustContextFilter, UsernamePasswordAuthenticationFilter.class)\n"
        f"{indent}                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());\n"
        f"{indent}        return http.build();\n"
        f"{indent}    }}\n\n"
        f"{indent}    @Bean\n"
        f'{indent}    @ConditionalOnProperty(name = "impilo.security.disable-oauth-for-tests", havingValue = "false", matchIfMissing = true)\n'
        f"{indent}    public SecurityFilterChain productionFilterChain(HttpSecurity http, TrustContextFilter trustContextFilter) throws Exception {{{body}}}"
    )
    t = t[:start] + new_block + t[end:]
    p.write_text(t, encoding="utf-8")
    return True


def ensure_test_yml(p: pathlib.Path) -> bool:
    if not p.exists():
        return False
    t = p.read_text(encoding="utf-8")
    if "disable-oauth-for-tests" in t:
        return False
    # Merge into existing impilo.security (first `  security:` after root `impilo:`)
    parts = re.split(r"(?m)^(impilo:\s*\n)", t, maxsplit=1)
    if len(parts) >= 3:
        prefix, impilo_hdr, rest = parts[0], parts[1], parts[2]
        sec_m = re.search(r"(?m)^(\s{2})security:\s*\n", rest)
        if sec_m:
            insert_at = sec_m.end()
            rest2 = rest[:insert_at] + "    disable-oauth-for-tests: true\n" + rest[insert_at:]
            p.write_text(prefix + impilo_hdr + rest2, encoding="utf-8")
            return True
    # impilo + companion, no security yet
    if re.search(r"(?m)^impilo:\s*$", t) and re.search(r"(?m)^\s+companion:\s*$", t):
        t2 = re.sub(
            r"(?m)^(impilo:\s*\n)(\s+companion:)",
            r"\1  security:\n    disable-oauth-for-tests: true\n\2",
            t,
            count=1,
        )
        if t2 != t:
            p.write_text(t2, encoding="utf-8")
            return True
    if re.search(r"(?m)^impilo:\s*$", t):
        t2 = re.sub(
            r"(?m)^(impilo:\s*\n)",
            r"\1  security:\n    disable-oauth-for-tests: true\n",
            t,
            count=1,
        )
        if t2 != t:
            p.write_text(t2, encoding="utf-8")
            return True
    sep = "" if t.endswith("\n") else "\n"
    p.write_text(t + sep + "impilo:\n  security:\n    disable-oauth-for-tests: true\n", encoding="utf-8")
    return True


def main() -> int:
    patched = []
    for p in ROOT.rglob("SecurityConfig.java"):
        rel = str(p.relative_to(ROOT))
        if "experience-bff" in rel:
            continue
        try:
            if patch_file(p):
                patched.append(rel)
        except OSError as e:
            print("ERR", p, e, file=sys.stderr)
    print("SecurityConfig patched:", len(patched))
    for x in sorted(patched):
        print(" ", x)

    yml_count = 0
    for p in ROOT.rglob("src/test/resources/application-test.yml"):
        if ensure_test_yml(p):
            yml_count += 1
    print("application-test.yml updated:", yml_count)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
