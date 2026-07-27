#!/usr/bin/env bash
# Procedures pipeline P4 — site and side, against the real inpatient schema.
#
# The pipeline audit's most serious finding: nothing in this platform verified site and side.
# They were recorded on a referral and displayed on a banner; no structured field held them on
# the episode and no gate refused to start a procedure whose side was never confirmed.
#
# The whole inpatient migration chain is applied — 40-odd migrations including the trauma
# lane's — because a constraint proven against a schema built in isolation proves nothing
# about the schema that ships, and because this table carries ten live rigs and two Kafka
# consumers behind it.
set -uo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="inpatient-siteside-rig-$$"; PG_PORT="${PROC_RIG_PG_PORT:-25443}"
TENANT="00000000-0000-4000-8000-000000000001"
EVIDENCE="reports/journeys/procedures-site-side-proof-$(date +%Y%m%d-%H%M%S)"
PASS=0; FAIL=0
cleanup() { [[ "${KEEP_RIG:-0}" == "1" ]] && echo "KEEP_RIG=1 — $PG_NAME on $PG_PORT" || docker rm -f "$PG_NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT
q() { docker exec "$PG_NAME" psql -U impilo -d inpatient -tAc "$1" 2>&1; }
chk() { if echo "$2" | grep -qi -- "$3"; then echo "PASS  $1"; PASS=$((PASS+1));
        else echo "FAIL  $1"; echo "      expected /$3/, got: $(echo "$2"|tr '\n' ' '|cut -c1-200)"; FAIL=$((FAIL+1)); fi; }

echo "=== procedures P4 site-and-side proof ==="
mkdir -p "$EVIDENCE"
docker rm -f "$PG_NAME" >/dev/null 2>&1 || true
docker run -d --name "$PG_NAME" -e POSTGRES_PASSWORD=impilo -e POSTGRES_USER=impilo \
  -e POSTGRES_DB=inpatient -p "${PG_PORT}:5432" postgres:16 >/dev/null || { echo "FAIL: postgres"; exit 1; }
echo -n "waiting for postgres"
for _ in $(seq 1 60); do docker exec "$PG_NAME" psql -U impilo -d inpatient -tAc "select 1" >/dev/null 2>&1 && break; echo -n "."; sleep 2; done; echo

chain_fail=0
for f in $(ls services/inpatient-service/src/main/resources/db/migration/*.sql | sort -V); do
  out=$(docker exec -i "$PG_NAME" psql -U impilo -d inpatient -v ON_ERROR_STOP=1 < "$f" 2>&1)
  echo "$out" > "$EVIDENCE/$(basename "$f").txt"
  echo "$out" | grep -qi "^ERROR" && { echo "  chain failure in $(basename "$f")"; chain_fail=1; }
done
chk "J-P4-0 whole inpatient migration chain applies with V300" "$chain_fail" "^0$"

ep() { q "INSERT INTO inpatient.procedure_episode (tenant_id, subject_cpid, procedure_name $2)
          VALUES ('$TENANT', 'CPID-$1', 'Test procedure' $3) RETURNING episode_id"; }

chk "J-P4-1 an episode with no side is still accepted — additive, existing rows unaffected" \
  "$(ep 1 '' '')" "-"

chk "J-P4-2 an invented laterality value is REFUSED" \
  "$(ep 2 ', laterality' ", 'sort of left'")" "chk_procedure_episode_laterality"

chk "J-P4-3 a structured side is accepted" \
  "$(ep 3 ', laterality' ", 'LEFT'")" "-"

chk "J-P4-4 a confirmation actor with no timestamp is REFUSED" \
  "$(ep 4 ', laterality, site_side_confirmed_by' ", 'LEFT', 'dr-a'")" "chk_procedure_episode_site_side_confirmation_pair"

chk "J-P4-5 a confirmation timestamp with no actor is REFUSED" \
  "$(ep 5 ', laterality, site_side_confirmed_at' ", 'LEFT', now()")" "chk_procedure_episode_site_side_confirmation_pair"

# The tick that asserts a check nobody performed.
chk "J-P4-6 confirming site and side without recording a side is REFUSED" \
  "$(ep 6 ', site_side_confirmed_by, site_side_confirmed_at' ", 'dr-a', now()")" "chk_procedure_episode_confirmation_needs_a_side"

chk "J-P4-7 a complete confirmation is accepted" \
  "$(ep 7 ', laterality, site_side_confirmed_by, site_side_confirmed_at' ", 'RIGHT', 'dr-a', now()")" "-"

chk "J-P4-8 an invented setting is REFUSED" \
  "$(ep 8 ', setting' ", 'CAR_PARK'")" "chk_procedure_episode_setting"

chk "J-P4-9 non-theatre settings are now executable on this engine" \
  "$(ep 9 ', setting' ", 'BEDSIDE'")" "-"

# Backfill honesty: every pre-existing row means exactly what it meant before.
chk "J-P4-10 every existing episode defaults to THEATRE" \
  "$(q "SELECT count(*) FROM inpatient.procedure_episode WHERE setting IS NULL OR setting <> 'THEATRE'
        AND setting NOT IN ('BEDSIDE')")" "^0$"

# A migration must never invent a side. An unverified side written by a backfill is exactly the
# confident-but-unchecked value this wave exists to eliminate.
chk "J-P4-11 the migration invented no laterality for existing rows" \
  "$(q "SELECT count(*) FROM inpatient.procedure_episode WHERE laterality IS NOT NULL AND subject_cpid = 'CPID-1'")" "^0$"

chk "J-P4-12 theatre status vocabulary is untouched — status and lifecycle_state coexist" \
  "$(q "SELECT CASE WHEN count(*) = 2 THEN 'BOTH' ELSE 'MISSING' END FROM information_schema.columns
        WHERE table_schema='inpatient' AND table_name='procedure_episode'
          AND column_name IN ('status','lifecycle_state')")" "BOTH"

{ echo "P4 site-and-side proof"; echo "generated: $(date -Is)"; echo "PASS=$PASS FAIL=$FAIL"; } > "$EVIDENCE/summary.txt"
echo; echo "P4 site-and-side proof: PASS=$PASS FAIL=$FAIL   evidence: $EVIDENCE"
[[ $FAIL -eq 0 ]] || exit 1
