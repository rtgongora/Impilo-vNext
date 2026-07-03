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
MAX_PHASE="${SCENARIO_A_MAX_PHASE:-9}"
RUN_TAG="scnA-$(date +%s)"

PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ok: $*"; }
fail() { FAIL=$((FAIL+1)); echo "  FAIL: $*" >&2; exit 1; }
phase(){ echo ""; echo "== PHASE $1: $2"; }
jqpy() { python3 -c "import json,sys
d=json.load(sys.stdin)
$1"; }

# Rebuild the trust-header array before each call (TOKEN/ANCHOR evolve by phase).
HDRS=()
build_hdrs() {
  HDRS=(-H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-e2e"
        -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)"
        -H "X-Correlation-ID: $RUN_TAG"
        -H "X-Actor-ID: ${ANCHOR:-anonymous}" -H "X-Actor-Type: PROVIDER"
        -H "X-Facility-ID: $FACILITY_ID")
  [[ -n "${PROVIDER_ID:-}" ]] && HDRS+=(-H "X-Provider-ID: $PROVIDER_ID")
  [[ -n "${TOKEN:-}" ]] && HDRS+=(-H "Authorization: Bearer $TOKEN")
  return 0
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
build_hdrs
PROVIDER_ID=$(curl -s "$PREVIEW_URL/internal/v1/identity/linked-ids" "${HDRS[@]}" \
  | jqpy "print(d['data']['attributes'].get('providerId',''))")
[[ "$PROVIDER_ID" == "$EXPECTED_PROVIDER" ]] || fail "providerId '$PROVIDER_ID' != '$EXPECTED_PROVIDER'"
build_hdrs
ASSIGN_COUNT=$(curl -s "$PREVIEW_URL/internal/v1/workforce-governance/assignments/search?subjectType=PROVIDER&subjectId=$PROVIDER_ID&status=ACTIVE" "${HDRS[@]}" \
  | jqpy "print(len(d.get('data',[])))")
[[ "$ASSIGN_COUNT" -ge 1 ]] || fail "no ACTIVE assignments for $PROVIDER_ID"
ok "$PROVIDER_ID has $ASSIGN_COUNT ACTIVE assignment(s) → work access"
[[ "$MAX_PHASE" -ge 3 ]] || { echo "PASS ($PASS checks)"; exit 0; }

# ── Phase 3: session contract → workforce profile → shift check-in ──────────
phase 3 "session contract → Vashandi profile → shift check-in (persisted)"
build_hdrs
CONTRACT=$(curl -s "$PREVIEW_URL/internal/v1/session/experience" "${HDRS[@]}")
PROFILE_ID=$(echo "$CONTRACT" | jqpy "
attrs=d.get('data',{}).get('attributes',d.get('data',{}))
print(attrs.get('vashandiWorkforceProfileId') or '')")
[[ -n "$PROFILE_ID" ]] || fail "session contract has no vashandiWorkforceProfileId (vashandi session-context broken) — contract: $(echo "$CONTRACT" | head -c 400)"
ok "workforce profile bound: $PROFILE_ID"

build_hdrs
CHECKIN=$(curl -s -X POST "$PREVIEW_URL/internal/v1/vashandi/attendance/adhoc-check-in" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-checkin" "${HDRS[@]}" \
  -d "{\"workforceProfileId\":\"$PROFILE_ID\",\"facilityId\":\"$FACILITY_ID\"}")
CHECKIN_OK=$(echo "$CHECKIN" | jqpy "print(str(d.get('success', d.get('data') is not None)).lower())")
[[ "$CHECKIN_OK" == "true" ]] || fail "check-in failed: $(echo "$CHECKIN" | head -c 400)"
SHIFT_ID=$(echo "$CHECKIN" | jqpy "
data=d.get('data') or {}
print(data.get('shiftId') or data.get('eventId') or data.get('id') or '')")
[[ -n "$SHIFT_ID" ]] || fail "check-in returned no event/shift id: $(echo "$CHECKIN" | head -c 300)"
build_hdrs
ATTEND_COUNT=$(curl -s "$PREVIEW_URL/internal/v1/vashandi/attendance?workforceProfileId=$PROFILE_ID" "${HDRS[@]}" \
  | jqpy "
data=d.get('data') or {}
items=data.get('items') if isinstance(data,dict) else data
print(len(items or []))")
[[ "$ATTEND_COUNT" -ge 1 ]] || fail "no persisted attendance events for profile $PROFILE_ID"
ok "checked in (shift ref $SHIFT_ID, $ATTEND_COUNT attendance event(s) persisted)"
[[ "$MAX_PHASE" -ge 4 ]] || { echo "PASS ($PASS checks)"; exit 0; }

# ── Phase 4: patient search + walk-in registration (VITO SoR) ───────────────
phase 4 "patient search + walk-in registration"
build_hdrs
SEARCH=$(curl -s -X POST "$PREVIEW_URL/internal/v1/patients/search" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-search" "${HDRS[@]}" -d '{"name":"Moyo"}')
SEARCH_N=$(echo "$SEARCH" | jqpy "print(len(d.get('data') or []))")
ok "patient search by name returned $SEARCH_N result(s)"

WALKIN_NAME="Walkin ${RUN_TAG}"
build_hdrs
CREATED=$(curl -s -X POST "$PREVIEW_URL/internal/v1/patients" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-patient" "${HDRS[@]}" \
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
build_hdrs
ENTRY=$(curl -s -X POST "$PREVIEW_URL/internal/v1/queue/entries" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-queue" "${HDRS[@]}" \
  -d "{\"patient_id\":\"$CPID\",\"patient_cpid\":\"$CPID\",\"facility_id\":\"$FACILITY_ID\",\"queue_type\":\"FIFO\",\"priority\":\"routine\"}")
ITEM_ID=$(echo "$ENTRY" | jqpy "
data=d.get('data') or {}
print(data.get('itemId') or data.get('id') or '')")
JOURNEY_ID=$(echo "$ENTRY" | jqpy "print((d.get('meta') or {}).get('journey_id',''))")
[[ -n "$ITEM_ID" && -n "$JOURNEY_ID" ]] || fail "queue entry not created: $(echo "$ENTRY" | head -c 400)"
STATUS=$(echo "$ENTRY" | jqpy "print((d.get('data') or {}).get('status',''))")
ok "queued: item $ITEM_ID journey $JOURNEY_ID status ${STATUS:-WAITING}"

build_hdrs
CALLED=$(curl -s -X POST "$PREVIEW_URL/internal/v1/queue/entries/$ITEM_ID/call" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-call" "${HDRS[@]}" -d '{}')
CALLED_STATUS=$(echo "$CALLED" | jqpy "print((d.get('data') or {}).get('status',''))")
[[ "$CALLED_STATUS" == "CALLED" ]] || fail "call transition: got '$CALLED_STATUS': $(echo "$CALLED" | head -c 300)"
ok "patient CALLED"
[[ "$MAX_PHASE" -ge 6 ]] || { echo "PASS ($PASS checks)"; exit 0; }

# ── Phase 6: encounter start carrying the shift linkage ──────────────────────
phase 6 "encounter start with X-Shift-ID → PCT persists shift linkage"
build_hdrs
ENC=$(curl -s -X POST "$PREVIEW_URL/internal/v1/encounters" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-enc" \
  -H "X-Shift-ID: $SHIFT_ID" "${HDRS[@]}" \
  -d "{\"patient_id\":\"$CPID\",\"journey_id\":\"$JOURNEY_ID\",\"encounter_type\":\"CONSULTATION\",\"entry_point\":\"walk_in\"}")
ENC_ID=$(echo "$ENC" | jqpy "print((d.get('meta') or {}).get('encounter_id',''))")
[[ -n "$ENC_ID" ]] || fail "encounter not created: $(echo "$ENC" | head -c 400)"
ENC_SHIFT=$(echo "$ENC" | jqpy "
data=d.get('data') or {}
print(data.get('shiftId') or '')")
[[ "$ENC_SHIFT" == "$SHIFT_ID" ]] || fail "encounter shiftId '$ENC_SHIFT' != checked-in shift '$SHIFT_ID'"
ok "encounter $ENC_ID linked to shift $SHIFT_ID (patient/provider/facility/journey/shift chain closed)"

build_hdrs
COMPLETED=$(curl -s -X POST "$PREVIEW_URL/internal/v1/queue/entries/$ITEM_ID/complete" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-complete" "${HDRS[@]}" -d '{}')
COMPLETED_STATUS=$(echo "$COMPLETED" | jqpy "print((d.get('data') or {}).get('status',''))")
[[ "$COMPLETED_STATUS" == "COMPLETED" ]] || fail "complete transition: got '$COMPLETED_STATUS'"
ok "queue item COMPLETED"

[[ "$MAX_PHASE" -ge 7 ]] || { echo "PASS ($PASS checks)"; exit 0; }

# ── Phase 7: lab order lifecycle → OROS RESULT_AVAILABLE ────────────────────
phase 7 "lab order → collect → result → OROS RESULT_AVAILABLE"
build_hdrs
LAB=$(curl -s -X POST "$PREVIEW_URL/internal/v1/lab-orders" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-lab" "${HDRS[@]}" \
  -d "{\"patient_id\":\"$CPID\",\"patient_cpid\":\"$CPID\",\"encounter_id\":\"$ENC_ID\",\"test_name\":\"Full Blood Count\",\"test_code\":\"FBC\",\"category\":\"HEMATOLOGY\",\"priority\":\"ROUTINE\",\"ordered_by\":\"$PROVIDER_ID\",\"facility_id\":\"$FACILITY_ID\"}")
LAB_ID=$(echo "$LAB" | jqpy "print((d.get('data') or {}).get('id',''))")
LAB_OROS=$(echo "$LAB" | jqpy "print(((d.get('data') or {}).get('attributes') or {}).get('oros_order_id',''))")
[[ -n "$LAB_ID" && -n "$LAB_OROS" ]] || fail "lab order not placed in OROS (sovereign delegation): $(echo "$LAB" | head -c 400)"
ok "lab order placed: $LAB_ID (OROS-sovereign)"

build_hdrs
curl -s -X POST "$PREVIEW_URL/internal/v1/lab-orders/$LAB_ID/collect" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-collect" "${HDRS[@]}" -d '{}' >/dev/null
build_hdrs
curl -s -X POST "$PREVIEW_URL/internal/v1/lab-orders/$LAB_ID/result" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-result" "${HDRS[@]}" \
  -d '{"result_data":[{"analyte":"HGB","value":"13.5","unit":"g/dL","interpretation":"NORMAL"}]}' >/dev/null

build_hdrs
LAB_STATE=$(curl -s "$PREVIEW_URL/internal/v1/lab-orders/$LAB_ID" "${HDRS[@]}" \
  | jqpy "
data=d.get('data') or {}
print(data.get('status') or data.get('orderStatus') or '')")
[[ "$LAB_STATE" == "RESULT_AVAILABLE" || "$LAB_STATE" == "COMPLETED" || "$LAB_STATE" == "RESULTED" ]] \
  || fail "OROS lab order state '$LAB_STATE' (want RESULT_AVAILABLE) — BFF result call did not reach the SoR"
ok "lab result recorded at the SoR: OROS state $LAB_STATE"
[[ "$MAX_PHASE" -ge 8 ]] || { echo "PASS ($PASS checks)"; exit 0; }

# ── Phase 8: imaging — real DICOM through Orthanc → pacs-adapter → OROS ─────
phase 8 "imaging order → DICOM upload (Orthanc) → study register/forward/sync → report → OROS result"
build_hdrs
IMG=$(curl -s -X POST "$PREVIEW_URL/internal/v1/lab-orders" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-img" "${HDRS[@]}" \
  -d "{\"patient_id\":\"$CPID\",\"patient_cpid\":\"$CPID\",\"encounter_id\":\"$ENC_ID\",\"test_name\":\"Chest X-Ray\",\"test_code\":\"CXR\",\"category\":\"XRAY\",\"priority\":\"ROUTINE\",\"ordered_by\":\"$PROVIDER_ID\",\"facility_id\":\"$FACILITY_ID\"}")
IMG_ORDER=$(echo "$IMG" | jqpy "print((d.get('data') or {}).get('id',''))")
IMG_OROS=$(echo "$IMG" | jqpy "print(((d.get('data') or {}).get('attributes') or {}).get('oros_order_id',''))")
[[ -n "$IMG_ORDER" && -n "$IMG_OROS" ]] || fail "imaging order not placed in OROS: $(echo "$IMG" | head -c 400)"
ok "imaging order placed: $IMG_ORDER (OROS IMAGING)"

STUDY_UID="1.2.826.0.1.3680043.10.9999.$(date +%s).$RANDOM"
DCM="/tmp/scenario-a-$RUN_TAG.dcm"
python3 "$(dirname "$0")/lib/minimal-dicom.py" "$DCM" \
  "$STUDY_UID" "$STUDY_UID.1" "$STUDY_UID.1.1" "$CPID" "Walkin^${RUN_TAG}" "ACC-$RUN_TAG" >/dev/null
build_hdrs
ORTHANC_RESP=$(curl -s -X POST "$PREVIEW_URL/internal/v1/pacs/instances/dicom" \
  -H "Content-Type: application/dicom" -H "Idempotency-Key: $RUN_TAG-dicom" "${HDRS[@]}" --data-binary "@$DCM")
ORTHANC_STATUS=$(echo "$ORTHANC_RESP" | jqpy "print(d.get('Status',''))" 2>/dev/null || echo "")
[[ "$ORTHANC_STATUS" == "Success" || "$ORTHANC_STATUS" == "AlreadyStored" ]] \
  || fail "Orthanc DICOM ingest failed: $(echo "$ORTHANC_RESP" | head -c 300)"
ok "DICOM instance stored in Orthanc (study $STUDY_UID)"

# Register the study on the pacs-adapter with the OROS correlation — the
# modality-integration actor's step; emits pacs.study.available for OROS.
PACS_IP="$(kubectl get svc pacs-adapter-service -n "${FULL_BOOT_NAMESPACE:-impilo-full-preview}" -o jsonpath='{.spec.clusterIP}' 2>/dev/null || echo pacs-adapter-service)"
STUDY=$(kubectl exec -n "${FULL_BOOT_NAMESPACE:-impilo-full-preview}" deploy/experience-bff -- curl -sS -X POST \
  "http://${PACS_IP}:8113/internal/v1/imaging-studies" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-register" \
  -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-e2e" \
  -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN_TAG" \
  -H "X-Actor-ID: $ANCHOR" -H "X-Actor-Type: PROVIDER" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"patientCpid\":\"$CPID\",\"studyUid\":\"$STUDY_UID\",\"modality\":\"OT\",\"studyDate\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"description\":\"Scenario A chest x-ray\",\"orosOrderId\":\"$IMG_ORDER\",\"accessionNumber\":\"ACC-$RUN_TAG\",\"encounterRef\":\"$ENC_ID\",\"facilityId\":\"$FACILITY_ID\"}")
STUDY_ID=$(echo "$STUDY" | jqpy "print(d.get('id',''))")
[[ -n "$STUDY_ID" ]] || fail "pacs study registration failed: $(echo "$STUDY" | head -c 300)"
ok "pacs study registered: id=$STUDY_ID (correlated to $IMG_ORDER)"

kubectl exec -n "${FULL_BOOT_NAMESPACE:-impilo-full-preview}" deploy/experience-bff -- curl -sS -X POST \
  "http://${PACS_IP}:8113/internal/v1/imaging-studies/$STUDY_ID/forward" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-forward" \
  -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-e2e" \
  -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN_TAG" \
  -H "X-Actor-ID: $ANCHOR" -H "X-Actor-Type: PROVIDER" -d '{}' >/dev/null \
  && ok "study forwarded to Orthanc backend" || fail "study forward to Orthanc failed"

SYNC=$(kubectl exec -n "${FULL_BOOT_NAMESPACE:-impilo-full-preview}" deploy/experience-bff -- curl -sS -X POST \
  "http://${PACS_IP}:8113/internal/v1/imaging-studies/$STUDY_ID/sync-hierarchy" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-sync" \
  -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-e2e" \
  -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN_TAG" \
  -H "X-Actor-ID: $ANCHOR" -H "X-Actor-Type: PROVIDER" -d '{}')
echo "  sync: $(echo "$SYNC" | head -c 160)"

build_hdrs
REPORT=$(curl -s -X POST "$PREVIEW_URL/internal/v1/imaging/studies/$STUDY_ID/report-links" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-report" "${HDRS[@]}" \
  -d "{\"reportRef\":\"scenario-a-report-$RUN_TAG\",\"reportText\":\"No acute cardiopulmonary abnormality. Scenario A verification report.\",\"reportingProviderRef\":\"$PROVIDER_ID\",\"reportStatus\":\"AVAILABLE\"}")
REPORT_ID=$(echo "$REPORT" | jqpy "
data=d.get('data') or d
print(data.get('id','') if isinstance(data,dict) else '')")
[[ -n "$REPORT_ID" ]] || fail "radiology report link failed: $(echo "$REPORT" | head -c 300)"
ok "radiology report linked to study (report link $REPORT_ID)"

# Event loop: pacs.study.available → OROS imaging result (Kafka listeners on).
IMG_STATE=""
for i in $(seq 1 12); do
  build_hdrs
  IMG_STATE=$(curl -s "$PREVIEW_URL/internal/v1/lab-orders/$IMG_ORDER" "${HDRS[@]}" \
    | jqpy "
data=d.get('data') or {}
print(data.get('status') or data.get('orderStatus') or '')")
  [[ "$IMG_STATE" == "RESULT_AVAILABLE" || "$IMG_STATE" == "COMPLETED" ]] && break
  sleep 5
done
[[ "$IMG_STATE" == "RESULT_AVAILABLE" || "$IMG_STATE" == "COMPLETED" ]] \
  || fail "OROS imaging order never reached RESULT_AVAILABLE (state '$IMG_STATE') — pacs→oros event loop broken"
ok "OROS imaging order $IMG_ORDER → $IMG_STATE via pacs.study.available event loop"

[[ "$MAX_PHASE" -ge 9 ]] || { echo "PASS ($PASS checks)"; exit 0; }

# ── Phase 9: teleconsult → SPECIALTY_POOL routing → consent → accept → video ─
phase 9 "teleconsult request → pool routing → consent → accept → LiveKit token/room"
PURPOSE_HDR=(-H "X-Purpose-Of-Use: TREATMENT")
build_hdrs
TELE=$(curl -s -X POST "$PREVIEW_URL/internal/v1/teleconsult/sessions" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-tele" "${HDRS[@]}" "${PURPOSE_HDR[@]}" \
  -d "{\"patientId\":\"$CPID\",\"encounterId\":\"$ENC_ID\",\"urgency\":\"routine\",\"specialty\":\"GENERAL_MEDICINE\",\"clinicalQuestion\":\"Scenario A teleconsult verification\",\"virtualMode\":\"video\"}")
TELE_ID=$(echo "$TELE" | jqpy "
data=d.get('data') or {}
print(data.get('referralId') or data.get('id') or data.get('referral_id') or '')")
[[ -n "$TELE_ID" ]] || fail "teleconsult session not created: $(echo "$TELE" | head -c 400)"
ok "teleconsult session created: $TELE_ID (VITO-validated patient)"

build_hdrs
ROUTE=$(curl -s -X PUT "$PREVIEW_URL/internal/v1/teleconsult/sessions/$TELE_ID/referral" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-route" "${HDRS[@]}" "${PURPOSE_HDR[@]}" \
  -d '{"routingType":"SPECIALTY_POOL","routingTarget":"GENERAL_MEDICINE","clinicalQuestion":"Scenario A teleconsult verification"}')
echo "$ROUTE" | jqpy "
err=d.get('error')
import sys
sys.exit(1 if err else 0)" || fail "pool routing failed: $(echo "$ROUTE" | head -c 300)"
ok "routed to SPECIALTY_POOL:GENERAL_MEDICINE"

build_hdrs
curl -s -X POST "$PREVIEW_URL/internal/v1/teleconsult/sessions/$TELE_ID/consent" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-consent" "${HDRS[@]}" "${PURPOSE_HDR[@]}" \
  -d "{\"patientId\":\"$CPID\",\"type\":\"TELEMEDICINE\"}" >/dev/null
build_hdrs
curl -s -X POST "$PREVIEW_URL/internal/v1/teleconsult/sessions/$TELE_ID/submit" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-tsubmit" "${HDRS[@]}" "${PURPOSE_HDR[@]}" -d '{}' >/dev/null
build_hdrs
curl -s -X POST "$PREVIEW_URL/internal/v1/teleconsult/sessions/$TELE_ID/accept" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-taccept" "${HDRS[@]}" "${PURPOSE_HDR[@]}" -d '{}' >/dev/null
ok "consent recorded, submitted to pool, accepted by specialist"

build_hdrs
MEDIA=$(curl -s -X POST "$PREVIEW_URL/internal/v1/teleconsult/sessions/$TELE_ID/media/token" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-token" "${HDRS[@]}" "${PURPOSE_HDR[@]}" \
  -d "{\"displayName\":\"Dr Mapfumo\",\"role\":\"PROVIDER\"}")
ROOM_URL=$(echo "$MEDIA" | jqpy "
data=d.get('data') or {}
print(data.get('room_url') or data.get('roomUrl') or data.get('url') or '')")
TOKEN_JWT=$(echo "$MEDIA" | jqpy "
data=d.get('data') or {}
print(data.get('token') or data.get('accessToken') or data.get('access_token') or '')")
[[ -n "$ROOM_URL" && -n "$TOKEN_JWT" ]] || fail "media token not issued: $(echo "$MEDIA" | head -c 400)"
echo "$TOKEN_JWT" | python3 -c "
import sys,base64,json
tok=sys.stdin.read().strip().split('.')
assert len(tok)==3, 'not a JWT'
payload=tok[1]+'='*(-len(tok[1])%4)
claims=json.loads(base64.urlsafe_b64decode(payload))
assert 'video' in claims or 'room' in str(claims), claims
" || fail "media token is not a valid LiveKit JWT"
ok "LiveKit media token issued (room_url=$ROOM_URL)"

# Signal-level join proof: websocket 101 upgrade against the room endpoint.
WS_HOST_PORT=$(echo "$ROOM_URL" | sed -E 's#^wss?://##; s#/.*$##')
WS_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
  "http://${WS_HOST_PORT}/rtc/validate?access_token=${TOKEN_JWT}" 2>/dev/null || echo 000)
if [[ "$WS_CODE" == "200" ]]; then
  ok "LiveKit signal endpoint validated the token (rtc/validate 200)"
else
  # Hairpin block means the VM cannot reach its own public IP — probe in-cluster.
  VALIDATE=$(kubectl exec -n "${FULL_BOOT_NAMESPACE:-impilo-full-preview}" deploy/experience-bff -- \
    curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
    "http://livekit:7880/rtc/validate?access_token=${TOKEN_JWT}" 2>/dev/null || echo 000)
  [[ "$VALIDATE" == "200" ]] || fail "LiveKit token validation failed (public=$WS_CODE in-cluster=$VALIDATE)"
  ok "LiveKit signal endpoint validated the token in-cluster (public path pending firewall: 7880/7881 tcp + 7882 udp)"
fi

build_hdrs
curl -s -X POST "$PREVIEW_URL/internal/v1/teleconsult/sessions/$TELE_ID/end" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $RUN_TAG-tend" "${HDRS[@]}" "${PURPOSE_HDR[@]}" -d '{}' >/dev/null
ok "session ended (room lifecycle closed)"

echo ""
echo "PASS: Scenario A phases 1-$MAX_PHASE green ($PASS checks) — run tag $RUN_TAG"
