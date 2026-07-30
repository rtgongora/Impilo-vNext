#!/usr/bin/env bash
#
# Guard: a `path_contains` pin must not end in `/`.
#
# PolicyEngine.pathContainsSegment requires the pin to be followed by `/` or end-of-path, so
# a pin written with a trailing slash matches only the bare collection root and never a
# record — the access such rules are almost always written to govern. See
# docs/architecture/POLICY_PATH_PIN_SEMANTICS.md.
#
# ~41 such pins already exist across five lanes. Failing on those on day one would make this
# a guard someone switches off, so the register in that document records the current count per
# migration and this script fails only when a count RISES or an unregistered migration
# introduces one. Correcting a lane means lowering its number, so the backlog can shrink but
# not silently grow.
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REGISTER="${REPO_ROOT}/docs/architecture/POLICY_PATH_PIN_SEMANTICS.md"

fail() { echo "FAIL: $*" >&2; exit 1; }

[[ -f "$REGISTER" ]] || fail "register not found: ${REGISTER}"

# ── registered baseline: migration -> allowed count ──────────────────────────
declare -A REGISTERED=()
while IFS=$'\t' read -r file count; do
    [[ -n "$file" ]] && REGISTERED["$file"]="$count"
done < <(
    grep -oP '^\|\s*`(V[0-9]+__[a-z0-9_]+\.sql)`.*\|\s*([0-9]+)\s*\|$' "$REGISTER" \
        | sed -E 's/^\|\s*`([^`]+)`.*\|\s*([0-9]+)\s*\|$/\1\t\2/' || true
)

(( ${#REGISTERED[@]} > 0 )) \
    || fail "parsed no rows out of the register — the guard would pass vacuously"

# ── observed: migration -> actual count ──────────────────────────────────────
declare -A OBSERVED=()
while IFS= read -r migration; do
    count=$(grep -o '"path_contains":[^,}]*' "$migration" | grep -c '/"' || true)
    (( count > 0 )) && OBSERVED["$(basename "$migration")"]="$count"
done < <(find "${REPO_ROOT}/services" -path '*/src/main/resources/db/migration/*.sql' -type f)

(( ${#OBSERVED[@]} > 0 )) \
    || fail "found no policy migrations at all — the scan is broken, not the estate"

# ── compare ─────────────────────────────────────────────────────────────────
violations=0
for migration in "${!OBSERVED[@]}"; do
    observed="${OBSERVED[$migration]}"
    allowed="${REGISTERED[$migration]:-0}"

    if (( observed > allowed )); then
        echo "  ${migration}: ${observed} trailing-slash pin(s), register allows ${allowed}" >&2
        violations=$((violations + 1))
    fi
done

if (( violations > 0 )); then
    cat >&2 <<EOF

A \`path_contains\` pin ending in \`/\` matches the collection root and nothing under it, so the
rule will not govern the records it names. Drop the trailing slash — segment bounding already
prevents \`/patients\` from reaching \`/patientsummary\`.

If the collection root really is the intended target, say so in
docs/architecture/POLICY_PATH_PIN_SEMANTICS.md and raise that migration's count.
EOF
    exit 1
fi

# A lane that fixed its rules should lower its number; report the drift so the register
# tracks reality rather than becoming a list of things that used to be true.
for migration in "${!REGISTERED[@]}"; do
    allowed="${REGISTERED[$migration]}"
    observed="${OBSERVED[$migration]:-0}"
    if (( observed < allowed )); then
        echo "NOTE: ${migration} is down to ${observed} (register says ${allowed}) — lower it."
    fi
done

echo "PASS: no new trailing-slash path_contains pins (${#REGISTERED[@]} migrations registered)"
