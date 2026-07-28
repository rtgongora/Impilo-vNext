#!/usr/bin/env bash
# Procedures pipeline P-R.4 + P-R2 — tshepo-authz policy rows for the procedures BFF proxy.
#
# Proves the V300 (catalogue/appropriateness/competence) and V301 (P7 safety-pause/sedation +
# P9 recovery/aftercare) policy rules against real Postgres AND proves, by direct reflection
# into the real PolicyEngine and AuthzInternalRequest classes (not by trusting either
# migration's own comments), the two defects the route shapes and path pins were designed to
# avoid: the trailing-slash path_contains failure, and the free-text-code resource_type
# derivation trap. The Java proofs live in tshepo-authz-service's own test module
# (PathContainsSegmentTest, ProceduresRouteShapeTest) and are run here for a single evidence
# trail; this script adds the data-hygiene checks a unit test cannot express.
#
# RESTORED 2026-07-28 (Wave P-R2): this file was deleted by an unrelated commit (90e64207f,
# an NCZ/org-registry feature with a 65-file diff and no stated reason to touch this path —
# almost certainly a broad, non-path-scoped commit sweeping up another session's local state).
# Recovered from its origin commit (b3e0e059a) via `git show <sha>:<path>`, not rewritten from
# memory, so the V300 assertions below are exactly what P-R.4 originally proved. Flagged for
# investigation via spawn_task; not blocking this wave.
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
chk "J-PR4-0 whole tshepo-authz chain applies with V300+V301" "$cf" "^0$"

# V302 (SB-3, 2026-07-28) added 10 'procedures-analytics-%' rows to the same chain; excluded
# here so this count stays a V300-only assertion (surgery-authz-journeys.sh owns the V302 counts).
chk "J-PR4-1 exactly 18 V300 rows land" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE name LIKE 'procedures-%'
        AND name NOT LIKE 'procedures-safety-pause%' AND name NOT LIKE 'procedures-sedation%'
        AND name NOT LIKE 'procedures-recovery%' AND name NOT LIKE 'procedures-aftercare%'
        AND name NOT LIKE 'procedures-analytics%'")" "^18$"

chk "J-PR2-1 exactly 30 V301 rows land (P7 safety-pause/sedation + P9 recovery/aftercare)" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE
        name LIKE 'procedures-safety-pause%' OR name LIKE 'procedures-sedation%'
        OR name LIKE 'procedures-recovery%' OR name LIKE 'procedures-aftercare%'")" "^30$"

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

chk "J-PR4-5 all four V300 procedures resource types are represented" \
  "$(q "SELECT count(DISTINCT resource_type) FROM tshepo_authz.policy_rule
        WHERE name LIKE 'procedures-%' AND resource_type IN
          ('catalogue','catalogue-detail','evaluate','competence')")" "^4$"

chk "J-PR2-2 all six V301 procedures resource types are represented" \
  "$(q "SELECT count(DISTINCT resource_type) FROM tshepo_authz.policy_rule
        WHERE resource_type IN ('safety-pause-templates','sedation-levels','sedation-level-detail',
                                 'recovery-settings','recovery-setting-detail','aftercare-templates')")" "^6$"

# The pin-substring safety this wave depends on: "sedation-levels" (list) must not accidentally
# authorize "sedation-level-detail" (single) via a naive substring match, and likewise for
# recovery-settings vs recovery-setting-detail — checked directly rather than assumed, since a
# false ALLOW from an over-broad pin is worse than the routes simply not being wired yet.
chk "J-PR2-3 the sedation-levels pin is not a raw substring of the sedation-level-detail path" \
  "$(q "SELECT CASE WHEN position('/procedures/sedation-levels' in '/internal/v1/procedures/sedation-level-detail') = 0
                THEN 'SAFE' ELSE 'COLLISION' END")" "SAFE"

chk "J-PR2-4 the recovery-settings pin is not a raw substring of the recovery-setting-detail path" \
  "$(q "SELECT CASE WHEN position('/procedures/recovery-settings' in '/internal/v1/procedures/recovery-setting-detail') = 0
                THEN 'SAFE' ELSE 'COLLISION' END")" "SAFE"

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
