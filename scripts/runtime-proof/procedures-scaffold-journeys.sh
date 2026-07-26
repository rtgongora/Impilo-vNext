#!/usr/bin/env bash
# Procedures pipeline P0 — scaffold runtime proof.
#
# Proves the procedures-service schema against REAL Postgres, not H2. The distinction is not
# ceremony: this estate has been bitten twice by defects H2 masked — a jsonb-as-varchar 500 that
# only appears on Postgres, and a VARCHAR(16) column silently sized to 255 by ddl-auto so an
# oversized status value passed every unit test and threw in production.
#
# What it asserts is the P0 half of pipeline §4 and §30: an index row per request, and a request
# that cannot be abandoned without a reason and a next action. "No request disappears" is the
# specification's phrasing; a CHECK constraint is where it becomes true rather than intended.
#
# Usage:  bash scripts/runtime-proof/procedures-scaffold-journeys.sh
#         KEEP_RIG=1 ... to leave the container up for inspection.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="proc-rig-pg-$$"
PG_PORT="${PROC_RIG_PG_PORT:-25439}"
TENANT="00000000-0000-4000-8000-000000000001"
MIGRATION="services/procedures-service/src/main/resources/db/migration/V001__init.sql"
EVIDENCE="reports/journeys/procedures-scaffold-proof-$(date +%Y%m%d-%H%M%S)"

PASS=0; FAIL=0
cleanup() {
  if [[ "${KEEP_RIG:-0}" == "1" ]]; then
    echo "KEEP_RIG=1 — leaving $PG_NAME up on port $PG_PORT"
  else
    docker rm -f "$PG_NAME" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

q() { docker exec "$PG_NAME" psql -U impilo -d procedures -tAc "$1" 2>&1; }

# The whole psql response is matched, never just its last line. An earlier version of this rig
# took `tail -1` and matched psql's DETAIL line instead of its ERROR line, so every correctly
# rejected write was reported as a failure. A rig that fails for the wrong reason erodes trust
# in it exactly as fast as one that passes for the wrong reason.
chk() {
  local name="$1" out="$2" expect="$3"
  if echo "$out" | grep -qi -- "$expect"; then
    echo "PASS  $name"; PASS=$((PASS+1))
  else
    echo "FAIL  $name"; echo "      expected /$expect/, got: $(echo "$out" | tr '\n' ' ' | cut -c1-200)"
    FAIL=$((FAIL+1))
  fi
}

echo "=== procedures-service P0 scaffold proof ==="
mkdir -p "$EVIDENCE"

docker rm -f "$PG_NAME" >/dev/null 2>&1 || true
docker run -d --name "$PG_NAME" \
  -e POSTGRES_PASSWORD=impilo -e POSTGRES_USER=impilo -e POSTGRES_DB=procedures \
  -p "${PG_PORT}:5432" postgres:16 >/dev/null || { echo "FAIL: could not start postgres"; exit 1; }

# Wait for the DATABASE to answer, never for pg_isready. The postgres entrypoint reports ready
# during initdb and then restarts, so pg_isready goes green before `procedures` exists. That race
# is what broke the theatre persistence rig; a fixed sleep hides it rather than fixing it.
echo -n "waiting for postgres"
for _ in $(seq 1 60); do
  if docker exec "$PG_NAME" psql -U impilo -d procedures -tAc "select 1" >/dev/null 2>&1; then break; fi
  echo -n "."; sleep 2
done
echo
docker exec "$PG_NAME" psql -U impilo -d procedures -tAc "select 1" >/dev/null 2>&1 \
  || { echo "FAIL: postgres never became reachable"; exit 1; }

docker exec -i "$PG_NAME" psql -U impilo -d procedures -v ON_ERROR_STOP=1 < "$MIGRATION" \
  > "$EVIDENCE/migration.txt" 2>&1
chk "J-P0-0 V001 applies to real Postgres" "$(cat "$EVIDENCE/migration.txt"; echo "rc=$?")" "CREATE INDEX"

chk "J-P0-1 baseline index row accepted" \
  "$(q "INSERT INTO procedures.procedure_execution_index (tenant_id,request_ref,subject_cpid) VALUES ('$TENANT','req-1','CPID-1')")" \
  "INSERT 0 1"

chk "J-P0-2 duplicate request_ref rejected — exactly one index row per request" \
  "$(q "INSERT INTO procedures.procedure_execution_index (tenant_id,request_ref,subject_cpid) VALUES ('$TENANT','req-1','CPID-1')")" \
  "uq_procedure_execution_request"

# §4: every rejection, cancellation or delay carries a reason and a next action.
chk "J-P0-3 abandon with no reason rejected" \
  "$(q "UPDATE procedures.procedure_execution_index SET lifecycle_state='ABANDONED' WHERE request_ref='req-1'")" \
  "chk_procedure_execution_abandon_reason"

chk "J-P0-4 abandon with whitespace-only reason rejected" \
  "$(q "UPDATE procedures.procedure_execution_index SET lifecycle_state='ABANDONED', state_reason='   ', next_action='x' WHERE request_ref='req-1'")" \
  "chk_procedure_execution_abandon_reason"

chk "J-P0-5 abandon with a real reason and next action accepted" \
  "$(q "UPDATE procedures.procedure_execution_index SET lifecycle_state='ABANDONED', state_reason='Equipment unavailable', next_action='Rebook at Parirenyatwa within 14 days' WHERE request_ref='req-1'")" \
  "UPDATE 1"

chk "J-P0-6 executor_ref without executor_service rejected — a ref nobody can resolve" \
  "$(q "INSERT INTO procedures.procedure_execution_index (tenant_id,request_ref,subject_cpid,executor_ref) VALUES ('$TENANT','req-2','CPID-2','ep-1')")" \
  "chk_procedure_execution_executor_pair"

chk "J-P0-7 executor service+ref pair accepted" \
  "$(q "INSERT INTO procedures.procedure_execution_index (tenant_id,request_ref,subject_cpid,executor_service,executor_ref) VALUES ('$TENANT','req-3','CPID-3','inpatient-service','ep-1')")" \
  "INSERT 0 1"

chk "J-P0-8 unknown lifecycle_state rejected" \
  "$(q "INSERT INTO procedures.procedure_execution_index (tenant_id,request_ref,subject_cpid,lifecycle_state) VALUES ('$TENANT','req-4','CPID-4','NONSENSE')")" \
  "chk_procedure_execution_lifecycle"

chk "J-P0-9 outbox idempotency key is unique — replay cannot double-publish" \
  "$(q "INSERT INTO procedures.event_outbox (aggregate_type,aggregate_id,event_type,idempotency_key,tenant_id,subject_id,subject_type,occurred_at,payload_json) VALUES ('x','1','t','k','$TENANT','s','S',now(),'{}'), ('x','2','t','k','$TENANT','s','S',now(),'{}')")" \
  "uq_procedures_outbox_idempotency"

# The query the index exists to make cheap: requests that never reached an executor.
chk "J-P0-10 unrouted-request reconciliation query answers" \
  "$(q "SELECT count(*) FROM procedures.procedure_execution_index WHERE executor_ref IS NULL AND lifecycle_state IN ('REQUESTED','ROUTED')")" \
  "^[0-9]"

# Engine-not-store (ADR decision 2): this schema must hold the inputs to a verdict, never a
# verdict. inpatient.procedure_readiness_check and procedure_checklist_item stay the record of
# truth. A table here whose name says otherwise is the rule being broken, so the rig asserts it
# rather than trusting review to notice a future migration.
chk "J-P0-11 engine-not-store — no readiness or checklist table in this schema" \
  "$(q "SELECT coalesce(string_agg(tablename,','),'none') FROM pg_tables WHERE schemaname='procedures' AND (tablename LIKE '%readiness%' OR tablename LIKE '%checklist%')")" \
  "^none$"

{
  echo "procedures-service P0 scaffold proof"
  echo "generated: $(date -Is)"
  echo "PASS=$PASS FAIL=$FAIL"
} > "$EVIDENCE/summary.txt"

echo
echo "P0 scaffold proof: PASS=$PASS FAIL=$FAIL   evidence: $EVIDENCE"
[[ $FAIL -eq 0 ]] || exit 1
