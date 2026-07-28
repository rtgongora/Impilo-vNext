#!/usr/bin/env bash
# Surgery pipeline S0 — scaffold runtime proof.
#
# Proves the surgery-service schema against REAL Postgres, not H2 — the same reason every
# scaffold rig in this programme does (H2 has masked a jsonb-as-varchar 500 and a silently
# widened VARCHAR column in this estate before).
#
# S0 is intentionally thin: schema + outbox only. The surgical episode itself is S1's own wave,
# because it must extend pct_problems/pct_care_plans per the 2026-07-26 CC-2 amendment rather
# than duplicate them — real design work, not scaffold work. This rig therefore proves two
# things: the outbox itself, and a CC-2 REGRESSION GUARD — that no table in this schema, now or
# in any future migration, holds a person-level longitudinal registry fact PCT already owns
# (condition/diagnosis/staging/recurrence/surveillance-plan/outcome). Trivially true today with
# zero domain tables; the assertion exists so it keeps being true once S1 adds one.
#
# Usage:  bash scripts/runtime-proof/surgery-scaffold-journeys.sh
#         KEEP_RIG=1 ... to leave the container up for inspection.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="surgery-rig-pg-$$"
PG_PORT="${SURGERY_RIG_PG_PORT:-25460}"
TENANT="00000000-0000-4000-8000-000000000001"
MIGRATION="services/surgery-service/src/main/resources/db/migration/V001__init.sql"
EVIDENCE="reports/journeys/surgery-scaffold-proof-$(date +%Y%m%d-%H%M%S)"

PASS=0; FAIL=0
cleanup() {
  if [[ "${KEEP_RIG:-0}" == "1" ]]; then
    echo "KEEP_RIG=1 — leaving $PG_NAME up on port $PG_PORT"
  else
    docker rm -f "$PG_NAME" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

q() { docker exec "$PG_NAME" psql -U impilo -d surgery -tAc "$1" 2>&1; }

chk() {
  local name="$1" out="$2" expect="$3"
  if echo "$out" | grep -qi -- "$expect"; then
    echo "PASS  $name"; PASS=$((PASS+1))
  else
    echo "FAIL  $name"; echo "      expected /$expect/, got: $(echo "$out" | tr '\n' ' ' | cut -c1-200)"
    FAIL=$((FAIL+1))
  fi
}

echo "=== surgery-service S0 scaffold proof ==="
mkdir -p "$EVIDENCE"

docker rm -f "$PG_NAME" >/dev/null 2>&1 || true
docker run -d --name "$PG_NAME" \
  -e POSTGRES_PASSWORD=impilo -e POSTGRES_USER=impilo -e POSTGRES_DB=surgery \
  -p "${PG_PORT}:5432" postgres:16 >/dev/null || { echo "FAIL: could not start postgres"; exit 1; }

# Wait for the DATABASE to answer, never pg_isready — the entrypoint reports ready during
# initdb and then restarts, so pg_isready goes green before `surgery` exists.
echo -n "waiting for postgres"
for _ in $(seq 1 60); do
  if docker exec "$PG_NAME" psql -U impilo -d surgery -tAc "select 1" >/dev/null 2>&1; then break; fi
  echo -n "."; sleep 2
done
echo
docker exec "$PG_NAME" psql -U impilo -d surgery -tAc "select 1" >/dev/null 2>&1 \
  || { echo "FAIL: postgres never became reachable"; exit 1; }

docker exec -i "$PG_NAME" psql -U impilo -d surgery -v ON_ERROR_STOP=1 < "$MIGRATION" \
  > "$EVIDENCE/migration.txt" 2>&1
chk "J-S0-0 V001 applies to real Postgres" "$(cat "$EVIDENCE/migration.txt"; echo "rc=$?")" "CREATE INDEX"

chk "J-S0-1 a well-formed outbox event is accepted" \
  "$(q "INSERT INTO surgery.event_outbox (aggregate_type,aggregate_id,event_type,idempotency_key,tenant_id,subject_id,subject_type,occurred_at,payload_json) VALUES ('x','1','t','k1','$TENANT','s','S',now(),'{}')")" \
  "INSERT 0 1"

chk "J-S0-2 outbox idempotency key is unique — replay cannot double-publish" \
  "$(q "INSERT INTO surgery.event_outbox (aggregate_type,aggregate_id,event_type,idempotency_key,tenant_id,subject_id,subject_type,occurred_at,payload_json) VALUES ('x','2','t','k1','$TENANT','s','S',now(),'{}')")" \
  "uq_surgery_outbox_idempotency"

chk "J-S0-3 the unpublished-events partial index exists — what the drainage rig queries" \
  "$(q "SELECT count(*) FROM pg_indexes WHERE schemaname='surgery' AND indexname='idx_surgery_outbox_unpublished'")" \
  "^1$"

# CC-2 regression guard (see this file's own header). Zero domain tables today; the assertion
# exists so S1 onward cannot silently add a duplicate person-level registry PCT already owns.
chk "J-S0-4 no person-level longitudinal registry table exists in this schema (CC-2)" \
  "$(q "SELECT coalesce(string_agg(tablename,','),'none') FROM pg_tables
        WHERE schemaname='surgery' AND (
          tablename LIKE '%condition%' OR tablename LIKE '%diagnosis%' OR
          tablename LIKE '%staging%' OR tablename LIKE '%surveillance%' OR
          tablename LIKE '%outcome%' OR tablename LIKE '%recurrence%')")" \
  "^none$"

{
  echo "surgery-service S0 scaffold proof"
  echo "generated: $(date -Is)"
  echo "PASS=$PASS FAIL=$FAIL"
} > "$EVIDENCE/summary.txt"

echo
echo "S0 scaffold proof: PASS=$PASS FAIL=$FAIL   evidence: $EVIDENCE"
[[ $FAIL -eq 0 ]] || exit 1
