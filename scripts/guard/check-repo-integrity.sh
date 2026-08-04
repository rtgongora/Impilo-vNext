#!/usr/bin/env bash
# =============================================================================
# GUARD — cheap repository integrity checks that catch a whole class of merge
# and tooling accidents before they reach a build.
#
#   1. `git diff --check`  — trailing whitespace and, decisively, leftover
#      conflict markers. A committed `<<<<<<<` compiles in exactly the languages
#      where it does the most damage (JSON, YAML, SQL) and is invisible in a
#      squashed diff review.
#   2. Disabled TypeScript checking — `typescript.ignoreBuildErrors` or
#      `eslint.ignoreDuringBuilds` in a Next config turns a red build green
#      without changing a line of product code. That is the single highest-value
#      "false green" switch in this repo.
#
# Exit: 0 clean · 1 at least one finding
# =============================================================================
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.." || exit 1
fail=0

echo "== git diff --check (whitespace / conflict markers) =="
if ! git diff --check HEAD -- . ; then
  echo "  FAIL: whitespace errors or conflict markers above"
  fail=1
else
  echo "  ok"
fi

# Only `typescript.ignoreBuildErrors` is unconditionally wrong: it makes tsc
# errors invisible to the build with nothing else covering them. `eslint.
# ignoreDuringBuilds` is NOT the same thing — this repo runs `npm run lint` as
# its own blocking gate (scripts/test/run-frontend-checks.sh, "frontend-lint"),
# so disabling it inside `next build` avoids linting twice rather than skipping
# it. Failing on that would be a false alarm, and a guard that cries wolf is a
# guard the next engineer switches off.
echo "== disabled TypeScript enforcement =="
ts_hits="$(grep -rnE 'ignoreBuildErrors\s*:\s*true' \
        --include='*.mjs' --include='*.js' --include='*.ts' \
        ui/ apps/ 2>/dev/null || true)"
if [ -n "$ts_hits" ]; then
  echo "$ts_hits" | sed 's/^/  /'
  echo "  FAIL: typescript.ignoreBuildErrors makes type errors invisible to the build"
  fail=1
else
  echo "  ok (typescript.ignoreBuildErrors is not set)"
fi

echo "== eslint.ignoreDuringBuilds (tolerated only while lint is separately gated) =="
if grep -rqE 'ignoreDuringBuilds\s*:\s*true' --include='*.mjs' ui/ 2>/dev/null; then
  if grep -q 'frontend-lint' scripts/test/run-frontend-checks.sh 2>/dev/null; then
    echo "  ok (set, but 'frontend-lint' runs npm run lint as a blocking gate)"
  else
    echo "  FAIL: lint is disabled during builds AND no separate lint gate runs it"
    fail=1
  fi
else
  echo "  ok (not set)"
fi

if [ "$fail" -ne 0 ]; then
  echo ""
  echo "GUARD FAIL  repository integrity"
  exit 1
fi
echo "GUARD PASS  repository integrity"
exit 0
