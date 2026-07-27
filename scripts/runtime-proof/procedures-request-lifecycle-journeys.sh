#!/usr/bin/env bash
# Procedures pipeline P2 — procedure request lifecycle depth, against the real OROS schema.
#
# OROS already owned the procedure request: order_type=PROCEDURE since V001 and a
# deny-by-default ProcedureWorkflow guard over nineteen states since V011. This wave added the
# five states §4 was missing and the reason-and-next-action rule, and this proves the half that
# lives in the database — a service-only guard is advisory to anything with a connection.
#
# The whole OROS migration chain is applied, not just V300, because a constraint that only
# works against a schema built in isolation proves nothing about the schema that ships.
set -uo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="oros-lifecycle-rig-$$"; PG_PORT="${PROC_RIG_PG_PORT:-25442}"
TENANT="00000000-0000-4000-8000-000000000001"
EVIDENCE="reports/journeys/procedures-request-lifecycle-proof-$(date +%Y%m%d-%H%M%S)"
PASS=0; FAIL=0
cleanup() { [[ "${KEEP_RIG:-0}" == "1" ]] && echo "KEEP_RIG=1 — $PG_NAME on $PG_PORT" || docker rm -f "$PG_NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT
q() { docker exec "$PG_NAME" psql -U impilo -d oros -tAc "$1" 2>&1; }
chk() { if echo "$2" | grep -qi -- "$3"; then echo "PASS  $1"; PASS=$((PASS+1));
        else echo "FAIL  $1"; echo "      expected /$3/, got: $(echo "$2"|tr '\n' ' '|cut -c1-200)"; FAIL=$((FAIL+1)); fi; }

echo "=== procedures P2 request-lifecycle proof ==="
mkdir -p "$EVIDENCE"
docker rm -f "$PG_NAME" >/dev/null 2>&1 || true
docker run -d --name "$PG_NAME" -e POSTGRES_PASSWORD=impilo -e POSTGRES_USER=impilo \
  -e POSTGRES_DB=oros -p "${PG_PORT}:5432" postgres:16 >/dev/null || { echo "FAIL: postgres"; exit 1; }
echo -n "waiting for postgres"
for _ in $(seq 1 60); do docker exec "$PG_NAME" psql -U impilo -d oros -tAc "select 1" >/dev/null 2>&1 && break; echo -n "."; sleep 2; done; echo

chain_fail=0
for f in $(ls services/oros-service/src/main/resources/db/migration/*.sql | sort -V); do
  out=$(docker exec -i "$PG_NAME" psql -U impilo -d oros -v ON_ERROR_STOP=1 < "$f" 2>&1)
  echo "$out" > "$EVIDENCE/$(basename "$f").txt"
  echo "$out" | grep -qi "^ERROR" && { echo "  chain failure in $(basename "$f")"; chain_fail=1; }
done
chk "J-P2-0 whole OROS migration chain applies with V300" "$chain_fail" "^0$"

# Real columns, taken from the schema rather than assumed. An earlier version of this rig
# invented an "id" column and reported five false failures — a rig that fails for the wrong
# reason erodes trust as fast as one that passes for the wrong reason.
ins() { # $1 order_id  $2 order_type  $3 extra cols  $4 extra vals
  q "INSERT INTO oros_orders (order_id, tenant_id, facility_id, patient_cpid, order_type, placed_by, workflow_state $3)
     VALUES ('$1', '$TENANT', '$TENANT', 'CPID-1', '$2', 'dr-seed', 'CANCELLED' $4)"; }

chk "J-P2-1 PROCEDURE cancelled with NO reason is REFUSED" \
  "$(ins req-a PROCEDURE '' '')" "chk_oros_procedure_non_progressing_needs_reason_and_next_action"
chk "J-P2-2 PROCEDURE cancelled with a reason but NO next action is REFUSED" \
  "$(ins req-b PROCEDURE ', workflow_state_reason' ", 'Patient unwell'")" "chk_oros_procedure_non_progressing"
chk "J-P2-3 whitespace-only next action is REFUSED" \
  "$(ins req-c PROCEDURE ', workflow_state_reason, workflow_next_action' ", 'Patient unwell', '   '")" "chk_oros_procedure_non_progressing"
chk "J-P2-4 cancelled WITH a reason and a next action is accepted" \
  "$(ins req-d PROCEDURE ', workflow_state_reason, workflow_next_action' ", 'Patient unwell on the day', 'Re-list within 14 days; clinic to contact patient'")" "INSERT 0 1"
chk "J-P2-5 IMAGING is UNAFFECTED — the rule is opt-in per category, not imposed" \
  "$(ins req-e IMAGING '' '')" "INSERT 0 1"
chk "J-P2-6 LAB is UNAFFECTED" \
  "$(ins req-f LAB '' '')" "INSERT 0 1"
chk "J-P2-7 constraint is NOT VALID — history grandfathered, not rewritten" \
  "$(q "SELECT convalidated FROM pg_constraint WHERE conname='chk_oros_procedure_non_progressing_needs_reason_and_next_action'")" "^f$"
chk "J-P2-8 the un-actioned backlog is queryable rather than erased" \
  "$(q "SELECT count(*) FROM oros_orders WHERE order_type='PROCEDURE'
        AND workflow_state IN ('REJECTED','CANCELLED','DEFERRED','ABORTED','FAILED','NO_SHOW','RETURNED_FOR_CLARIFICATION')
        AND (workflow_state_reason IS NULL OR workflow_next_action IS NULL)")" "^[0-9]"
chk "J-P2-9 a PROCEDURE order may still reach a progressing state with no reason" \
  "$(q "INSERT INTO oros_orders (order_id, tenant_id, facility_id, patient_cpid, order_type, placed_by, workflow_state)
        VALUES ('req-g', '$TENANT', '$TENANT', 'CPID-2', 'PROCEDURE', 'dr-seed', 'IN_PROGRESS')")" "INSERT 0 1"

{ echo "P2 request-lifecycle proof"; echo "generated: $(date -Is)"; echo "PASS=$PASS FAIL=$FAIL"; } > "$EVIDENCE/summary.txt"
echo; echo "P2 request-lifecycle proof: PASS=$PASS FAIL=$FAIL   evidence: $EVIDENCE"
[[ $FAIL -eq 0 ]] || exit 1
