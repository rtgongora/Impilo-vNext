#!/usr/bin/env bash
# Procedures pipeline P-R.4 — tshepo-authz policy rows for the procedures BFF proxy.
#
# Proves the V300 policy rules against real Postgres AND proves, by direct reflection into the
# real PolicyEngine and AuthzInternalRequest classes (not by trusting the migration's own
# comments), the two defects the route shapes and path pins were designed to avoid: the
# trailing-slash path_contains failure, and the free-text-code resource_type derivation trap.
# The Java proofs live in tshepo-authz-service's own test module
# (PathContainsSegmentTest, ProceduresRouteShapeTest) and are run here for a single evidence
# trail; this script adds the data-hygiene checks a unit test cannot express.
set -uo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"
PG_NAME="authz-procedures-rig-$$"; PG_PORT="${PROC_RIG_PG_PORT:-25445}"
EVIDENCE="$(pwd)/reports/journeys/procedures-authz-proof-$(date +%Y%m%d-%H%M%S)"
PASS=0; FAIL=0
cleanup() { [[ "${KEEP_RIG:-0}" == "1" ]] && echo "KEEP_RIG=1" || docker rm -f "$PG_NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT
q() { docker exec "$PG_NAME" psql -U impilo -d tshepo_authz -tAc "$1" 2>&1; }
chk() { if echo "$2" | grep -qi -- "$3"; then echo "PASS  $1"; PASS=$((PASS+1));
        else echo "FAIL  $1"; echo "      expected /$3/, got: $(echo "$2"|tr '\n' ' '|cut -c1-180)"; FAIL=$((FAIL+1)); fi; }

echo "=== procedures P-R.4 authz proof ==="; mkdir -p "$EVIDENCE"
docker rm -f "$PG_NAME" >/dev/null 2>&1 || true
docker run -d --name "$PG_NAME" -e POSTGRES_PASSWORD=impilo -e POSTGRES_USER=impilo \
  -e POSTGRES_DB=tshepo_authz -p "${PG_PORT}:5432" postgres:16 >/dev/null || { echo "FAIL: postgres"; exit 1; }
echo -n "waiting"; for _ in $(seq 1 60); do q "select 1" >/dev/null 2>&1 && break; echo -n "."; sleep 2; done; echo

cf=0
for f in $(ls services/tshepo-authz-service/src/main/resources/db/migration/*.sql | sort -V); do
  out=$(docker exec -i "$PG_NAME" psql -U impilo -d tshepo_authz -v ON_ERROR_STOP=1 < "$f" 2>&1)
  echo "$out" > "$EVIDENCE/$(basename "$f").txt"
  echo "$out" | grep -qi "^ERROR" && { echo "  chain failure in $(basename "$f")"; cf=1; }
done
chk "J-PR4-0 whole tshepo-authz chain applies with V300" "$cf" "^0$"

chk "J-PR4-1 exactly 18 procedures rows land" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE name LIKE 'procedures-%'")" "^18$"

chk "J-PR4-2 no rule grants an unlisted role (DISPATCHER, RESPONDER etc.)" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE name LIKE 'procedures-%'
        AND role NOT IN ('CLINICIAN','DOCTOR','NURSE','CONSULTANT','WARD_MANAGER')")" "^0$"

chk "J-PR4-3 every rule is PROVIDER/ALLOW/TREATMENT/facility-scoped" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE name LIKE 'procedures-%'
        AND (actor_type<>'PROVIDER' OR effect<>'ALLOW' OR purpose<>'TREATMENT' OR facility_scope IS NOT TRUE)")" "^0$"

# The defect this whole migration is designed around, checked in the data itself: no pin may
# end with a trailing slash, because pathContainsSegment never matches one against a path with
# anything nested beneath it.
chk "J-PR4-4 no path_contains pin ends with a trailing slash" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE name LIKE 'procedures-%'
        AND conditions::jsonb->>'path_contains' LIKE '%/'")" "^0$"

chk "J-PR4-5 all four procedures resource types are represented" \
  "$(q "SELECT count(DISTINCT resource_type) FROM tshepo_authz.policy_rule WHERE name LIKE 'procedures-%'")" "^4$"

echo
echo "--- Java proofs against the real PolicyEngine and AuthzInternalRequest (reflection + direct calls) ---"
# EVIDENCE is now absolute — an earlier version of this rig built it as a relative path and
# then wrote into it from inside a `cd services` subshell, so the write landed one directory
# up from where anything looked for it and the rig reported a false failure on a real pass.
# No -q: it suppresses surefire's own "Tests run:" summary in this environment, which made an
# earlier version of this rig treat a real pass as a failure because the grep below had
# nothing to match. Verbose output plus a targeted grep is more reliable than a quiet build.
(cd services && mvn -pl tshepo-authz-service test \
    -Dtest=PathContainsSegmentTest,ProceduresRouteShapeTest) 2>&1 | tee "$EVIDENCE/java-proofs.txt"
java_rc=${PIPESTATUS[0]}
if [[ $java_rc -eq 0 ]] && grep -q "BUILD SUCCESS" "$EVIDENCE/java-proofs.txt"; then
  n=$(grep -oE "Tests run: [0-9]+" "$EVIDENCE/java-proofs.txt" | tail -1 | grep -oE "[0-9]+")
  chk "J-PR4-6 Java proofs (trailing-slash pin defect + route-shape derivation) pass" "BUILD SUCCESS n=$n" "BUILD SUCCESS"
else
  chk "J-PR4-6 Java proofs (trailing-slash pin defect + route-shape derivation) pass" "FAILED" "BUILD SUCCESS"
fi

{ echo "P-R.4 authz proof"; echo "generated: $(date -Is)"; echo "PASS=$PASS FAIL=$FAIL"; } > "$EVIDENCE/summary.txt"
echo; echo "P-R.4 authz proof: PASS=$PASS FAIL=$FAIL   evidence: $EVIDENCE"
[[ $FAIL -eq 0 ]] || exit 1
