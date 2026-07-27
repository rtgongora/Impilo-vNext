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

echo "=== TOP no-record-level-emit guard ==="

# The TOP identifiers whose appearance on an emit path is forbidden.
TOP_TOKENS='pct_top_procedures|pct_top_authorisations|TopProcedure|TopAuthorisation|top_procedure_id'

# Files that publish record-level events: outbox writers, Kafka producers, event publishers. We scan
# these for any mention of a TOP token. A match means a TOP record is being put on a path that leaves
# the service with row-level content — exactly what the ruling forbids.
EMIT_FILES=$(git ls-files -- 'services/pct-service/**/*.java' \
  | xargs -r grep -lE 'Outbox|KafkaTemplate|event_outbox|EventPublisher|\.send\(|publishEvent' 2>/dev/null || true)

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
for f in $(git ls-files -- 'services/pct-service/**/V435__*.sql'); do
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
