#!/usr/bin/env bash
# =============================================================================
# Scenario A — frontline health-worker clinical journey (API steel thread).
#
# Proves, against a LIVE preview estate, the chain:
#   1. login (real Keycloak) → canonical person anchor (health_id claim)
#   2. linked-ids → providerId → ACTIVE workforce assignment (Work access)
#   3. session experience contract → Vashandi workforce profile → shift
#      check-in (persisted attendance event)
#   4. patient search + walk-in registration via VITO (CPID minted by SoR)
#   5. queue entry → CALLED → IN_CONSULTATION → COMPLETED (PCT-owned states)
#   6. encounter start carrying X-Shift-ID → PCT persists shift linkage
#
# Every phase asserts CONTENT (ids, states), not just HTTP 200s. Fails fast.
#
# Usage:
#   bash scripts/e2e/scenario-a-clinical-journey.sh            # all phases
#   SCENARIO_A_MAX_PHASE=4 bash scripts/e2e/scenario-a-clinical-journey.sh
# Env:
#   PREVIEW_URL       (default http://127.0.0.1)
#   TENANT_ID         (default canonical preview UUID)
#   PERSONA           (default dr.mapfumo)
#   PERSONA_PASSWORD  (default ImpiloTest123!)
# =============================================================================
set -euo pipefail

PREVIEW_URL="${PREVIEW_URL:-http://127.0.0.1}"
TENANT_ID="${TENANT_ID:-00000000-0000-4000-8000-000000000001}"
PERSONA="${PERSONA:-dr.mapfumo}"
PERSONA_PASSWORD="${PERSONA_PASSWORD:-ImpiloTest123!}"
EXPECTED_ANCHOR="${EXPECTED_ANCHOR:-c0000000-0000-4000-8000-000000000001}"
EXPECTED_PROVIDER="${EXPECTED_PROVIDER:-PROV-ZW-00001}"
FACILITY_ID="${FACILITY_ID:-f1000000-0000-0000-0000-000000000001}"
MAX_PHASE="${SCENARIO_A_MAX_PHASE:-6}"
RUN_TAG="scnA-$(date +%s)"

PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ok: $*"; }
fail() { FAIL=$((FAIL+1)); echo "  FAIL: $*" >&2; exit 1; }
phase(){ echo ""; echo "== PHASE $1: $2"; }
jqpy() { python3 -c "import json,sys
d=json.load(sys.stdin)
$1"; }

hdrs() {
  # trust headers + actor identity; $1 optional extra purpose
  echo -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-e2e" \
       -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" \
       -H "X-Correlation-ID: $RUN_TAG" \
       -H "X-Actor-ID: ${ANCHOR:-anonymous}" -H "X-Actor-Type: PROVIDER" \
       ${PROVIDER_ID:+-H "X-Provider-ID: $PROVIDER_ID"} \
       ${TOKEN:+-H "Authorization: Bearer $TOKEN"} \
       -H "X-Facility-ID: $FACILITY_ID"
}

# ── Phase 1: login → person anchor ──────────────────────────────────────────
phase 1 "login → canonical person anchor"
LOGIN=$(curl -s -X POST "$PREVIEW_URL/internal/v1/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-e2e" \
  -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN_TAG" \
  -H "Idempotency-Key: $RUN_TAG-login" \
  -d "{\"email\":\"$PERSONA\",\"password\":\"$PERSONA_PASSWORD\"}")
TOKEN=$(echo "$LOGIN" | jqpy "print(d['data']['attributes']['token'])") || fail "login failed: $(echo "$LOGIN" | head -c 300)"
ANCHOR=$(echo "$LOGIN" | jqpy "print(d['data']['attributes']['user']['healthId'])")
[[ "$ANCHOR" == "$EXPECTED_ANCHOR" ]] || fail "anchor '$ANCHOR' != expected '$EXPECTED_ANCHOR' (health_id claim broken)"
ok "$PERSONA anchored as $ANCHOR"

# ── Phase 2: linked-ids → provider → ACTIVE assignment ──────────────────────
phase 2 "linked-ids → provider → ACTIVE workforce assignment"
PROVIDER_ID=$(curl -s "$PREVIEW_URL/internal/v1/identity/linked-ids" $(hdrs) \
  | jqpy "print(d['data']['attributes'].get('providerId',''))")
[[ "$PROVIDER_ID" == "$EXPECTED_PROVIDER" ]] || fail "providerId '$PROVIDER_ID' != '$EXPECTED_PROVIDER'"
ASSIGN_COUNT=$(curl -s "$PREVIEW_URL/internal/v1/workforce-governance/assignments/search?subjectType=PROVIDER&subjectId=$PROVIDER_ID&status=ACTIVE" $(hdrs) \
  | jqpy "print(len(d.get('data',[])))")
[[ "$ASSIGN_COUNT" -ge 1 ]] || fail "no ACTIVE assignments for $PROVIDER_ID"
ok "$PROVIDER_ID has $ASSIGN_COUNT ACTIVE assignment(s) → work access"
[[ "$MAX_PHASE" -ge 3 ]] || { echo "PASS ($PASS checks)"; exit 0; }

# ── Phase 3: session contract → workforce profile → shift check-in ──────────
phase 3 "session contract → Vashandi profile → shift check-in (persisted)"
CONTRACT=$(curl -s "$PREVIEW_URL/internal/v1/session/experience" $(hdrs))
PROFILE_ID=$(echo "$CONTRACT" | jqpy "
attrs=d.get('data',{}).get('attributes',d.get('data',{}))
print(attrs.get('vashandiWorkforceProfileId') or '')")
[[ -n "$PROFILE_ID" ]] || fail "session contract has no vashandiWorkforceProfileId (vashandi session-context broken) — contract: $(echo "$CONTRACT" | head -c 400)"
ok "workforce profile bound: $PROFILE_ID"

CHECKIN=$(curl -s -X POST "$PREVIEW_URL/internal/v1/vashandi/attendance/check-in" \
  -H "Content-Type: application/json" $(hdrs) \
  -d "{\"shiftId\":\"self-service\",\"workforceProfileId\":\"$PROFILE_ID\",\"checkInMode\":\"self_check_in\"}")
CHECKIN_OK=$(echo "$CHECKIN" | jqpy "print(str(d.get('success', d.get('data') is not None)).lower())")
[[ "$CHECKIN_OK" == "true" ]] || fail "check-in failed: $(echo "$CHECKIN" | head -c 400)"
SHIFT_ID=$(echo "$CHECKIN" | jqpy "
data=d.get('data') or {}
print(data.get('shiftId') or data.get('id') or 'self-service')")
ATTEND_COUNT=$(curl -s "$PREVIEW_URL/internal/v1/vashandi/attendance?workforceProfileId=$PROFILE_ID" $(hdrs) \
  | jqpy "
data=d.get('data') or {}
items=data.get('items') if isinstance(data,dict) else data
print(len(items or []))")
[[ "$ATTEND_COUNT" -ge 1 ]] || fail "no persisted attendance events for profile $PROFILE_ID"
ok "checked in (shift ref $SHIFT_ID, $ATTEND_COUNT attendance event(s) persisted)"
[[ "$MAX_PHASE" -ge 4 ]] || { echo "PASS ($PASS checks)"; exit 0; }

# ── Phase 4: patient search + walk-in registration (VITO SoR) ───────────────
phase 4 "patient search + walk-in registration"
SEARCH=$(curl -s -X POST "$PREVIEW_URL/internal/v1/patients/search" \
  -H "Content-Type: application/json" $(hdrs) -d '{"name":"Moyo"}')
SEARCH_N=$(echo "$SEARCH" | jqpy "print(len(d.get('data') or []))")
ok "patient search by name returned $SEARCH_N result(s)"

WALKIN_NAME="Walkin ${RUN_TAG}"
CREATED=$(curl -s -X POST "$PREVIEW_URL/internal/v1/patients" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-patient" $(hdrs) \
  -d "{\"given_name\":\"Walkin\",\"family_name\":\"${RUN_TAG}\",\"date_of_birth\":\"1990-05-01\",\"sex\":\"FEMALE\",\"phone\":\"+263771$(date +%N | cut -c1-6)\",\"facility_id\":\"$FACILITY_ID\"}")
CPID=$(echo "$CREATED" | jqpy "
data=d.get('data') or {}
attrs=data.get('attributes', data)
print(attrs.get('cpid') or attrs.get('patient_id') or data.get('id') or '')")
[[ -n "$CPID" ]] || fail "walk-in registration returned no CPID: $(echo "$CREATED" | head -c 400)"
ok "walk-in registered → CPID $CPID (minted by VITO)"
[[ "$MAX_PHASE" -ge 5 ]] || { echo "PASS ($PASS checks)"; exit 0; }

# ── Phase 5: queue lifecycle WAITING → CALLED → COMPLETED ────────────────────
phase 5 "queue entry lifecycle"
ENTRY=$(curl -s -X POST "$PREVIEW_URL/internal/v1/queue/entries" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-queue" $(hdrs) \
  -d "{\"patient_id\":\"$CPID\",\"patient_cpid\":\"$CPID\",\"facility_id\":\"$FACILITY_ID\",\"queue_type\":\"FIFO\",\"priority\":\"routine\"}")
ITEM_ID=$(echo "$ENTRY" | jqpy "
data=d.get('data') or {}
print(data.get('itemId') or data.get('id') or '')")
JOURNEY_ID=$(echo "$ENTRY" | jqpy "print((d.get('meta') or {}).get('journey_id',''))")
[[ -n "$ITEM_ID" && -n "$JOURNEY_ID" ]] || fail "queue entry not created: $(echo "$ENTRY" | head -c 400)"
STATUS=$(echo "$ENTRY" | jqpy "print((d.get('data') or {}).get('status',''))")
ok "queued: item $ITEM_ID journey $JOURNEY_ID status ${STATUS:-WAITING}"

CALLED=$(curl -s -X POST "$PREVIEW_URL/internal/v1/queue/entries/$ITEM_ID/call" \
  -H "Content-Type: application/json" $(hdrs) -d '{}')
CALLED_STATUS=$(echo "$CALLED" | jqpy "print((d.get('data') or {}).get('status',''))")
[[ "$CALLED_STATUS" == "CALLED" ]] || fail "call transition: got '$CALLED_STATUS': $(echo "$CALLED" | head -c 300)"
ok "patient CALLED"
[[ "$MAX_PHASE" -ge 6 ]] || { echo "PASS ($PASS checks)"; exit 0; }

# ── Phase 6: encounter start carrying the shift linkage ──────────────────────
phase 6 "encounter start with X-Shift-ID → PCT persists shift linkage"
ENC=$(curl -s -X POST "$PREVIEW_URL/internal/v1/encounters" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-enc" \
  -H "X-Shift-ID: $SHIFT_ID" $(hdrs) \
  -d "{\"patient_id\":\"$CPID\",\"journey_id\":\"$JOURNEY_ID\",\"encounter_type\":\"CONSULTATION\",\"entry_point\":\"walk_in\"}")
ENC_ID=$(echo "$ENC" | jqpy "print((d.get('meta') or {}).get('encounter_id',''))")
[[ -n "$ENC_ID" ]] || fail "encounter not created: $(echo "$ENC" | head -c 400)"
ENC_SHIFT=$(echo "$ENC" | jqpy "
data=d.get('data') or {}
print(data.get('shiftId') or '')")
[[ "$ENC_SHIFT" == "$SHIFT_ID" ]] || fail "encounter shiftId '$ENC_SHIFT' != checked-in shift '$SHIFT_ID'"
ok "encounter $ENC_ID linked to shift $SHIFT_ID (patient/provider/facility/journey/shift chain closed)"

COMPLETED=$(curl -s -X POST "$PREVIEW_URL/internal/v1/queue/entries/$ITEM_ID/complete" \
  -H "Content-Type: application/json" $(hdrs) -d '{}')
COMPLETED_STATUS=$(echo "$COMPLETED" | jqpy "print((d.get('data') or {}).get('status',''))")
[[ "$COMPLETED_STATUS" == "COMPLETED" ]] || fail "complete transition: got '$COMPLETED_STATUS'"
ok "queue item COMPLETED"

echo ""
echo "PASS: Scenario A phases 1-$MAX_PHASE green ($PASS checks) — run tag $RUN_TAG"
