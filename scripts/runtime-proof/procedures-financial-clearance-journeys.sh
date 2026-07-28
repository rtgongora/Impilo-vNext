#!/usr/bin/env bash
# Procedures pipeline P12 — financial clearance schema proof (§23), against the real inpatient
# schema (real Postgres).
#
# The whole inpatient migration chain is applied — same reasoning as
# procedures-site-side-journeys.sh: a constraint proven against a schema built in isolation
# proves nothing about the schema that ships. This rig proves the SCHEMA half (columns, CHECK
# constraints); the enforcement LOGIC (the emergency bypass, the fresh-query-never-cached
# behaviour, the never-cancels-the-episode guarantee) is proven in
# ProcedureFinancialClearanceTest — H2 does not enforce these CHECK constraints, so both proofs
# are needed and neither substitutes for the other.
set -uo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

PG_NAME="inpatient-financial-rig-$$"; PG_PORT="${PROC_RIG_PG_PORT:-25455}"
TENANT="00000000-0000-4000-8000-000000000001"
EVIDENCE="$(pwd)/reports/journeys/procedures-financial-clearance-proof-$(date +%Y%m%d-%H%M%S)"
PASS=0; FAIL=0
cleanup() { [[ "${KEEP_RIG:-0}" == "1" ]] && echo "KEEP_RIG=1 — $PG_NAME on $PG_PORT" || docker rm -f "$PG_NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT
q() { docker exec "$PG_NAME" psql -U impilo -d inpatient -tAc "$1" 2>&1; }
chk() { if echo "$2" | grep -qi -- "$3"; then echo "PASS  $1"; PASS=$((PASS+1));
        else echo "FAIL  $1"; echo "      expected /$3/, got: $(echo "$2"|tr '\n' ' '|cut -c1-200)"; FAIL=$((FAIL+1)); fi; }

echo "=== procedures P12 financial-clearance schema proof ==="
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
chk "J-P12-0 whole inpatient migration chain applies with V302" "$chain_fail" "^0$"

EPISODE_ID=$(q "INSERT INTO inpatient.procedure_episode (tenant_id, subject_cpid, procedure_name)
                 VALUES ('$TENANT', 'CPID-FIN-1', 'Test procedure') RETURNING episode_id" | head -1)

ep_update() { q "UPDATE inpatient.procedure_episode SET $1 WHERE episode_id = '$EPISODE_ID'"; }

chk "J-P12-1 an existing pre-P12 row (no financial columns set) still satisfies the new CHECKs" \
  "$(q "SELECT count(*) FROM inpatient.procedure_episode
        WHERE episode_id = '$EPISODE_ID' AND financial_clearance_status IS NULL")" "^1$"

chk "J-P12-2 financial_clearance_checked_by without _at is REFUSED" \
  "$(ep_update "financial_clearance_checked_by = 'actor-1'")" "chk_procedure_episode_financial_clearance_pair"

chk "J-P12-3 financial_clearance_checked_at without _by is REFUSED" \
  "$(ep_update "financial_clearance_checked_at = now()")" "chk_procedure_episode_financial_clearance_pair"

chk "J-P12-4 a paired financial clearance check IS accepted" \
  "$(ep_update "financial_clearance_status = 'ALLOWED_WITHOUT_PAYMENT', financial_clearance_checked_by = 'actor-1', financial_clearance_checked_at = now()")" \
  "UPDATE 1"

chk "J-P12-5 an invented financial_clearance_status is REFUSED" \
  "$(ep_update "financial_clearance_status = 'PROBABLY_FINE_I_GUESS', financial_clearance_checked_by = 'actor-1', financial_clearance_checked_at = now()")" \
  "chk_procedure_episode_financial_clearance_status"

chk "J-P12-6 the EMERGENCY_OVERRIDE status (bypass-without-a-query) is a valid value, not fabricated" \
  "$(ep_update "financial_clearance_status = 'EMERGENCY_OVERRIDE', financial_clearance_checked_by = 'actor-1', financial_clearance_checked_at = now()")" \
  "UPDATE 1"

chk "J-P12-7 both explicit BLOCKED_* statuses are valid values (the ones that actually gate a start)" \
  "$(ep_update "financial_clearance_status = 'BLOCKED_PENDING_PAYMENT', financial_clearance_checked_by = 'actor-1', financial_clearance_checked_at = now()")" \
  "UPDATE 1"

# P4's own site-and-side pairing constraint, on the same table this migration adds columns to —
# proven still intact, not merely assumed unaffected by an additive ALTER TABLE. laterality is
# set in the same UPDATE to satisfy chk_procedure_episode_confirmation_needs_a_side, the same
# evaluation-order lesson P8's rig already learned: Postgres checks every CHECK on the row, so
# an UPDATE touching only the field under test can trip a different constraint first.
chk "J-P12-8 P4's site_side_confirmed pairing constraint still holds after V302 stacks on the same table" \
  "$(ep_update "laterality = 'LEFT', site_side_confirmed_at = now()")" "chk_procedure_episode_site_side_confirmation_pair"

{ echo "procedures P12 financial-clearance schema proof"; echo "generated: $(date -Is)"; echo "PASS=$PASS FAIL=$FAIL"; } > "$EVIDENCE/summary.txt"
echo; echo "P12 financial-clearance proof: PASS=$PASS FAIL=$FAIL   evidence: $EVIDENCE"
[[ $FAIL -eq 0 ]] || exit 1
