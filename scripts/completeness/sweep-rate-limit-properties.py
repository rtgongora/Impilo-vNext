#!/usr/bin/env python3
"""One-shot sweep: make the Wave 14 rate-limit bucket configurable in every SecurityBaselineConfig.

Each service keeps its own numbers as the property defaults, so runtime behaviour is unchanged --
including the six services that deliberately raised theirs (msika/vito 200/3, ubomi/pharmacy/zibo/tuso
150/2). This is not a normalisation pass.

Why it is needed: the bucket is in-memory and keyed by actor, and MockMvc gives every request in a
Spring test context the same actor. A module whose suite makes more than its capacity in requests
starts 429ing its own tests, and the failure surfaces as a bogus status assertion in whichever test
follows -- see data-governance-service and F13 in docs/handover/2026-07-29-*.md.

Kept in the tree rather than run and discarded: the same sweep is needed again if services are
scaffolded from an older template, and the transformation is easier to audit as code than as a diff of
97 files. Idempotent -- files already carrying the property form are skipped.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# The optional group is a hand-written note some services carry inside the body ("TUSO: 150
# requests/min capacity..."). It states the service's intent, so it is preserved rather than replaced.
BEAN = re.compile(
    r"    @Bean\n"
    r"    public RateLimitGuard rateLimitGuard\(\) \{\n"
    r"(        //[^\n]*\n)?"
    r"        return new RateLimitGuard\((\d+), (\d+)\);\n"
    r"    \}\n"
)

VALUE_IMPORT = "import org.springframework.beans.factory.annotation.Value;\n"
FIRST_IMPORT = "import org.springframework.boot.web.servlet.FilterRegistrationBean;\n"


def _dedent_note(note: str | None) -> str:
    """Move a body comment out to method level, where the new signature leaves it."""
    return note.replace("        //", "    //", 1) if note else ""


def replacement(note: str, max_tokens: str, refill: str) -> str:
    return (
        (note or "")
        + "    // Capacity and refill are properties so a test profile can widen the bucket: it is\n"
        "    // in-memory and keyed by actor, and every MockMvc request in a Spring test context\n"
        "    // presents the same actor, so a suite of more than {max_tokens} requests throttles itself.\n"
        "    // Defaults are this service's own previous values -- runtime behaviour is unchanged.\n"
        "    @Bean\n"
        "    public RateLimitGuard rateLimitGuard(\n"
        '            @Value("${{impilo.security.rate-limit.max-tokens:{max_tokens}}}") long maxTokens,\n'
        '            @Value("${{impilo.security.rate-limit.refill-per-second:{refill}}}") long refillPerSecond) {{\n'
        "        return new RateLimitGuard(maxTokens, refillPerSecond);\n"
        "    }}\n"
    ).format(max_tokens=max_tokens, refill=refill)


def main() -> int:
    changed, skipped = 0, 0
    for path in sorted((ROOT / "services").glob("*/src/main/java/**/SecurityBaselineConfig.java")):
        text = path.read_text()
        match = BEAN.search(text)
        if not match:
            skipped += 1
            continue

        text = BEAN.sub(
            lambda m: replacement(_dedent_note(m.group(1)), m.group(2), m.group(3)), text, count=1
        )
        if VALUE_IMPORT not in text:
            if FIRST_IMPORT not in text:
                print(f"SKIP {path.relative_to(ROOT)}: unexpected import block", file=sys.stderr)
                skipped += 1
                continue
            # org.springframework.beans sorts before org.springframework.boot.
            text = text.replace(FIRST_IMPORT, VALUE_IMPORT + FIRST_IMPORT, 1)

        path.write_text(text)
        print(f"{match.group(2)}/{match.group(3)} -> properties  {path.relative_to(ROOT)}")
        changed += 1

    print(f"\nsweep-rate-limit-properties: changed={changed} skipped={skipped}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
