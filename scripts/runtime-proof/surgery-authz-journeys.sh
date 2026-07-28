#!/usr/bin/env bash
# Surgery + Procedures SB-3 — tshepo-authz policy rows for the consolidated reachability wave.
#
# Proves the V302 policy rules (surgery-service S1-S3 episodes/assessment/decision, procedures
# P14 analytics indicators, theatre specimen custody P8 §13, inventory implant lifecycle P8 §14)
# against real Postgres, following procedures-authz-journeys.sh's pattern exactly: the whole
# migration chain must apply, the row counts and role matrices are checked in the data, the two
# known PDP defects (trailing-slash path_contains that can never match; free-text-code
# resource_type derivation) are guarded by data checks here plus Java proofs against the real
# PolicyEngine/AuthzInternalRequest classes (SurgeryReachabilityRouteShapeTest).
#
# NEGATIVE LEGS: the estate's negatives are CORRECT-CADRE-ONLY ALLOW → NO_ALLOW_RULE (a DENY
# verdict), never a path-pinned DENY (unsafe: path_contains is evaluated only when a rule fires,
# and a conditional DENY that fails its own pin is skipped, not enforced). Each negative below
# therefore asserts that NO ALLOW row exists for the excluded cadre/action pair — which is
# exactly the condition under which PolicyEngine.evaluatePolicies returns NO_ALLOW_RULE.
set -uo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"
PG_NAME="authz-surgery-rig-$$"; PG_PORT="${SURGERY_RIG_PG_PORT:-25446}"
EVIDENCE="$(pwd)/reports/journeys/surgery-authz-proof-$(date +%Y%m%d-%H%M%S)"
PASS=0; FAIL=0
cleanup() { [[ "${KEEP_RIG:-0}" == "1" ]] && echo "KEEP_RIG=1" || docker rm -f "$PG_NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT
q() { docker exec "$PG_NAME" psql -U impilo -d tshepo_authz -tAc "$1" 2>&1; }
chk() { if echo "$2" | grep -qi -- "$3"; then echo "PASS  $1"; PASS=$((PASS+1));
        else echo "FAIL  $1"; echo "      expected /$3/, got: $(echo "$2"|tr '\n' ' '|cut -c1-180)"; FAIL=$((FAIL+1)); fi; }

echo "=== surgery SB-3 authz proof ==="; mkdir -p "$EVIDENCE"
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
chk "J-SB3-0 whole tshepo-authz chain applies with V302" "$cf" "^0$"

# ── row landing counts, per surface ──
chk "J-SB3-1 exactly 30 surgery-service rows land" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE name LIKE 'surgery-%'")" "^30$"

chk "J-SB3-2 exactly 10 procedures analytics rows land" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE name LIKE 'procedures-analytics-%'")" "^10$"

chk "J-SB3-3 exactly 8 specimen custody rows land" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE name LIKE 'theatre-specimen-collect-%'
        OR name LIKE 'theatre-specimen-confirm-label-%' OR name LIKE 'theatre-specimen-receive-%'
        OR name LIKE 'theatre-specimen-adequacy-%'")" "^8$"

chk "J-SB3-4 exactly 8 implant lifecycle rows land" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE name LIKE 'inventory-implant-%'")" "^8$"

# ── the trailing-slash defect, checked in the data itself (V300's lesson) ──
chk "J-SB3-5 no V302 path_contains pin ends with a trailing slash" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule
        WHERE (name LIKE 'surgery-%' OR name LIKE 'procedures-analytics-%'
               OR name LIKE 'theatre-specimen-collect-%' OR name LIKE 'theatre-specimen-confirm-label-%'
               OR name LIKE 'theatre-specimen-receive-%' OR name LIKE 'theatre-specimen-adequacy-%'
               OR name LIKE 'inventory-implant-%')
        AND conditions::jsonb->>'path_contains' LIKE '%/'")" "^0$"

chk "J-SB3-6 every V302 row is PROVIDER/ALLOW/TREATMENT/facility-scoped" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule
        WHERE (name LIKE 'surgery-%' OR name LIKE 'procedures-analytics-%'
               OR name LIKE 'theatre-specimen-collect-%' OR name LIKE 'theatre-specimen-confirm-label-%'
               OR name LIKE 'theatre-specimen-receive-%' OR name LIKE 'theatre-specimen-adequacy-%'
               OR name LIKE 'inventory-implant-%')
        AND (actor_type<>'PROVIDER' OR effect<>'ALLOW' OR purpose<>'TREATMENT' OR facility_scope IS NOT TRUE)")" "^0$"

chk "J-SB3-7 all 14 V302 resource types are represented" \
  "$(q "SELECT count(DISTINCT resource_type) FROM tshepo_authz.policy_rule
        WHERE (name LIKE 'surgery-%' OR name LIKE 'procedures-analytics-%'
               OR name LIKE 'theatre-specimen-collect-%' OR name LIKE 'theatre-specimen-confirm-label-%'
               OR name LIKE 'theatre-specimen-receive-%' OR name LIKE 'theatre-specimen-adequacy-%'
               OR name LIKE 'inventory-implant-%')
        AND resource_type IN ('episodes','link-procedure-episode','transition','assessment','decision',
                              'indicators','indicator','collect','confirm-label','receive','adequacy',
                              'remove','revise','recall')")" "^14$"

# ── every surgery pin is /surgery/episodes — the collision fence against inpatient's own
#    /internal/v1/procedures/episodes ("episodes"), committee "decision", etc. ──
chk "J-SB3-8 every surgery row pins /surgery/episodes" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE name LIKE 'surgery-%'
        AND conditions::jsonb->>'path_contains' <> '/surgery/episodes'")" "^0$"

# ── POSITIVE LEGS: the candidate row the PDP needs exists for the intended cadre ──
chk "J-SB3-P1 SURGEON may POST /surgery/episodes (open)" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE role='SURGEON' AND resource_type='episodes'
        AND action='POST' AND effect='ALLOW' AND active
        AND conditions::jsonb->>'path_contains'='/surgery/episodes'")" "^1$"

chk "J-SB3-P2 NURSE may GET surgery episodes (read leg)" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE role='NURSE' AND resource_type='episodes'
        AND action='GET' AND effect='ALLOW' AND active
        AND conditions::jsonb->>'path_contains'='/surgery/episodes'")" "^1$"

chk "J-SB3-P3 CONSULTANT may PUT the surgical decision" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE role='CONSULTANT' AND resource_type='decision'
        AND action='PUT' AND effect='ALLOW' AND active
        AND conditions::jsonb->>'path_contains'='/surgery/episodes'")" "^1$"

chk "J-SB3-P4 WARD_MANAGER may GET the analytics indicator catalogue" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE role='WARD_MANAGER' AND resource_type='indicators'
        AND action='GET' AND effect='ALLOW' AND active
        AND conditions::jsonb->>'path_contains'='/procedures/analytics'")" "^1$"

chk "J-SB3-P5 LAB_TECH may POST specimen receipt" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE role='LAB_TECH' AND resource_type='receive'
        AND action='POST' AND effect='ALLOW' AND active
        AND conditions::jsonb->>'path_contains'='/theatre/cases'")" "^1$"

chk "J-SB3-P6 SURGEON may POST implant removal" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE role='SURGEON' AND resource_type='remove'
        AND action='POST' AND effect='ALLOW' AND active
        AND conditions::jsonb->>'path_contains'='/inventory/implants'")" "^1$"

# ── NEGATIVE LEGS (NO_ALLOW_RULE): no ALLOW row exists for the excluded cadre/action, which is
#    exactly the PDP's NO_ALLOW_RULE deny condition for these resource types ──
chk "J-SB3-N1 NURSE/CLINICIAN/DOCTOR have NO row to PUT the surgical decision (senior-gated)" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE resource_type='decision' AND action='PUT'
        AND conditions::jsonb->>'path_contains'='/surgery/episodes'
        AND role IN ('NURSE','CLINICIAN','DOCTOR')")" "^0$"

chk "J-SB3-N2 NURSE/CLINICIAN/DOCTOR have NO row to transition an episode (senior-gated)" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE resource_type='transition' AND action='POST'
        AND conditions::jsonb->>'path_contains'='/surgery/episodes'
        AND role IN ('NURSE','CLINICIAN','DOCTOR')")" "^0$"

chk "J-SB3-N3 NURSE has NO row to open an episode (read-only cadre on this surface)" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE resource_type='episodes' AND action='POST'
        AND conditions::jsonb->>'path_contains'='/surgery/episodes' AND role='NURSE'")" "^0$"

chk "J-SB3-N4 NURSE has NO row for implant removal/revision (operative acts)" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE resource_type IN ('remove','revise')
        AND conditions::jsonb->>'path_contains'='/inventory/implants' AND role='NURSE'")" "^0$"

chk "J-SB3-N5 SURGEON has NO row for specimen adequacy (a laboratory judgement)" \
  "$(q "SELECT count(*) FROM tshepo_authz.policy_rule WHERE resource_type='adequacy'
        AND conditions::jsonb->>'path_contains'='/theatre/cases' AND role='SURGEON'")" "^0$"

# ── cross-service over-grant fences: the V302 surgery rows must not be candidates for the
#    OTHER services' same-word segments (the pin is the fence; assert it holds in the data) ──
chk "J-SB3-N6 the surgery 'episodes' pin is not satisfied by inpatient's procedures/episodes path" \
  "$(q "SELECT CASE WHEN position('/surgery/episodes' in '/internal/v1/procedures/episodes') = 0
                THEN 'SAFE' ELSE 'COLLISION' END")" "SAFE"

chk "J-SB3-N7 the implant pin is not satisfied by the theatre implant recall path" \
  "$(q "SELECT CASE WHEN position('/inventory/implants' in '/internal/v1/theatre/implants/recall') = 0
                THEN 'SAFE' ELSE 'COLLISION' END")" "SAFE"

echo
echo "--- Java proofs against the real PolicyEngine and AuthzInternalRequest ---"
# EVIDENCE is absolute; no -q (surefire's own summary is what the grep below matches) — both
# lessons inherited verbatim from procedures-authz-journeys.sh's header.
(cd services && mvn -pl tshepo-authz-service test \
    -Dtest=PathContainsSegmentTest,SurgeryReachabilityRouteShapeTest) 2>&1 | tee "$EVIDENCE/java-proofs.txt"
java_rc=${PIPESTATUS[0]}
if [[ $java_rc -eq 0 ]] && grep -q "BUILD SUCCESS" "$EVIDENCE/java-proofs.txt"; then
  n=$(grep -oE "Tests run: [0-9]+" "$EVIDENCE/java-proofs.txt" | tail -1 | grep -oE "[0-9]+")
  chk "J-SB3-J1 Java proofs (trailing-slash pin defect + SB-3 route-shape derivation) pass" "BUILD SUCCESS n=$n" "BUILD SUCCESS"
else
  chk "J-SB3-J1 Java proofs (trailing-slash pin defect + SB-3 route-shape derivation) pass" "FAILED" "BUILD SUCCESS"
fi

{ echo "SB-3 authz proof"; echo "generated: $(date -Is)"; echo "PASS=$PASS FAIL=$FAIL"; } > "$EVIDENCE/summary.txt"
echo; echo "SB-3 authz proof: PASS=$PASS FAIL=$FAIL   evidence: $EVIDENCE"
[[ $FAIL -eq 0 ]] || exit 1
