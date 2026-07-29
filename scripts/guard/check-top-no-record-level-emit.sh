#!/usr/bin/env bash
# Termination-of-pregnancy: no record-level emit, ever.
#
# PO ruling (2026-07-27): TOP procedures are counted into AGGREGATE indicators only and MUST NEVER be
# emitted as a record-level event — no identifier and no linkable pseudonym reaches surveillance,
# because in a population this size a pseudonym is re-identifiable and the legal exposure of that
# falls on the patient, not on us.
#
# The absence of a record-level path is a deliberate, defended property, not an unwired gap a future
# lane should "complete". This guard makes that mechanical: it fails the build if a TOP entity or
# table ever appears on an event/outbox/Kafka publishing path. It is the same shape as the
# no-caesarean-for-slow-progress invariant — a dangerous thing made impossible to add by accident
# rather than trusted to memory.
set -uo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"
# guard_assert_scanned lives in the shared helper, which sets -e. This guard deliberately does not
# use -e — it collects every offender before deciding an exit — so restore its own error mode.
source "$(dirname "${BASH_SOURCE[0]}")/_guard-common.sh"
set +e

echo "=== TOP no-record-level-emit guard ==="

# The TOP identifiers whose appearance on an emit path is forbidden.
TOP_TOKENS='pct_top_procedures|pct_top_authorisations|TopProcedure|TopAuthorisation|top_procedure_id'

# Files that publish record-level events: outbox writers, Kafka producers, event publishers. We scan
# these for any mention of a TOP token. A match means a TOP record is being put on a path that leaves
# the service with row-level content — exactly what the ruling forbids.
EMIT_FILES=$(git ls-files --cached --others --exclude-standard -- 'services/pct-service/**/*.java' \
  | xargs -r grep -lE 'Outbox|KafkaTemplate|event_outbox|EventPublisher|\.send\(|publishEvent' 2>/dev/null || true)

# Self-reach: an empty scan set reports exactly what a clean tree reports. The outbox pattern is
# mandatory estate-wide, so pct-service always has publishing paths — zero here means this glob
# stopped reaching the tree, not that the risk went away.
guard_assert_scanned "$(printf '%s\n' "$EMIT_FILES" | grep -c .)" \
  "pct-service files on an event/outbox/publishing path" || {
  echo "TOP no-record-level-emit guard: FAILED"
  exit 1
}

# Strip Java comments (line //, and javadoc/block lines beginning with * or /*) before matching, so a
# comment that DESCRIBES the prohibition — "this must never emit a TopProcedure" — is not itself read
# as a violation. The guard is about code that emits, not prose about not emitting.
java_code_only() { grep -vE '^[[:space:]]*(\*|//|/\*)' "$1" 2>/dev/null || true; }

FAIL=0
OFFENDERS=""
for f in $EMIT_FILES; do
    if java_code_only "$f" | grep -nE "$TOP_TOKENS" >/dev/null 2>&1; then
        OFFENDERS="$OFFENDERS $f"
        FAIL=1
    fi
done

if [[ "$FAIL" -ne 0 ]]; then
    echo "FAIL: a termination-of-pregnancy record appears on an event/outbox/publishing path:"
    for f in $OFFENDERS; do
        echo "  $f"
        java_code_only "$f" | grep -nE "$TOP_TOKENS" | sed 's/^/      /'
    done
    echo ""
    echo "      TOP is aggregate-only by PO ruling. No record-level event may carry a TOP identifier"
    echo "      or a linkable pseudonym. If aggregate counting is needed, count into an indicator"
    echo "      total; do not emit the row. This absence is deliberate — do not 'complete' it."
    echo "TOP no-record-level-emit guard: FAILED"
    exit 1
fi

# Also assert the tables themselves declare no outbox/event COLUMN, so the schema cannot grow one.
# The migration's own header and COMMENT ON statements legitimately describe the absence ("NO
# event/outbox path exists"); those are prose, not columns, so strip -- line comments and COMMENT ON
# statements before matching. A real column declaration (`event_type ...`) survives the strip.
SCHEMA_OFFEND=""
TOP_MIGRATIONS=$(git ls-files --cached --others --exclude-standard -- 'services/pct-service/**/V435__*.sql')

# Self-reach: the TOP migration is pinned by version number, so a renumber would leave this loop
# scanning nothing while still reporting "the schema declares no emit column".
guard_assert_scanned "$(printf '%s\n' "$TOP_MIGRATIONS" | grep -c .)" \
  "TOP migration files matching V435__*.sql" || {
  echo "      If the TOP migration was renumbered, update the glob above to its new version."
  echo "TOP no-record-level-emit guard: FAILED"
  exit 1
}

for f in $TOP_MIGRATIONS; do
    hit=$(sed -E 's/--.*$//' "$f" | sed -E '/COMMENT[[:space:]]+ON/,/;/d' \
          | grep -niE 'event_type|outbox|event_id|emit' 2>/dev/null || true)
    if [[ -n "$hit" ]]; then
        SCHEMA_OFFEND="$SCHEMA_OFFEND"$'\n'"$f:"$'\n'"$hit"
    fi
done
if [[ -n "${SCHEMA_OFFEND// }" ]]; then
    echo "FAIL: the TOP migration declares an event/outbox column:"
    echo "$SCHEMA_OFFEND" | sed 's/^/  /'
    echo "TOP no-record-level-emit guard: FAILED"
    exit 1
fi

echo "  no TOP record reaches an event/outbox/publishing path; the schema declares no emit column"
echo "TOP no-record-level-emit guard: OK"
exit 0
