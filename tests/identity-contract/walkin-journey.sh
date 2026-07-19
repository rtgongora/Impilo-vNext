#!/usr/bin/env bash
# =============================================================================
# Provider WALK-IN journey — live acceptance drive.
#
# Answers "does the walk-in journey actually work" by driving it end-to-end as a
# provider persona (dr.mapfumo, CLINICIAN @ facility) THROUGH experience-bff on
# the live estate: register a walk-in patient → enqueue (starts a PCT journey +
# queue item) → the patient appears WAITING on the board → call them in → start
# an encounter. Each step asserts the real HTTP contract; a break is captured
# where it happens, not hidden.
#
# SAFE-ish: this DOES create a walk-in patient + journey + encounter on the estate
# (that is the journey). Test data as the provider persona.
#
# Env: EXPERIENCE_BFF, TENANT, PROVIDER_USER (dr.mapfumo), PROVIDER_ID
#      (PROV-ZW-00001), FACILITY_UUID (a facility with a materialised queue),
#      QUEUE_TYPE (FIFO on the estate today).
# =============================================================================
set -uo pipefail

REPO="${REPO_PATH:-$(cd "$(dirname "$0")/../.." && pwd)}"
TS="$(date +%Y%m%d-%H%M%S 2>/dev/null || echo run)"
EVIDENCE_DIR="${EVIDENCE_DIR:-$REPO/reports/identity-contract/walkin-$TS}"
mkdir -p "$EVIDENCE_DIR"

NS="${IDENTITY_PACK_NAMESPACE:-impilo-full-preview}"
svcip(){ kubectl get svc -n "$NS" "$1" -o jsonpath='{.spec.clusterIP}' 2>/dev/null; }
EXPERIENCE_BFF="${EXPERIENCE_BFF:-http://$(svcip experience-bff):8160}"
TENANT="${TENANT:-00000000-0000-0000-0000-000000000001}"
PROVIDER_USER="${PROVIDER_USER:-dr.mapfumo}"
PROVIDER_PASSWORD="${PROVIDER_PASSWORD:-ImpiloTest123!}"
PROVIDER_ID="${PROVIDER_ID:-PROV-ZW-00001}"
FACILITY_UUID="${FACILITY_UUID:-f1000000-0000-0000-0000-000000000001}"
QUEUE_TYPE="${QUEUE_TYPE:-FIFO}"
TOKEN_CLIENT="${TOKEN_CLIENT:-experience-ui}"
TOKEN=""

mint_token(){
  local kc_ip; kc_ip=$(svcip keycloak); [ -n "$kc_ip" ] || return 0
  TOKEN=$(curl -sf -m 10 --resolve "keycloak:8080:$kc_ip" \
      -X POST "http://keycloak:8080/realms/impilo/protocol/openid-connect/token" \
      -d grant_type=password -d "client_id=$TOKEN_CLIENT" \
      -d "username=$PROVIDER_USER" --data-urlencode "password=$PROVIDER_PASSWORD" \
    | python3 -c 'import sys,json;print(json.load(sys.stdin).get("access_token",""))' 2>/dev/null) || TOKEN=""
}
derive_actor(){
  [ -n "${PROVIDER_ACTOR:-}" ] && { echo "$PROVIDER_ACTOR"; return; }
  [ -n "$TOKEN" ] || { echo ""; return; }
  local p; p=$(printf '%s' "$TOKEN" | cut -d. -f2); local pad=$(( ${#p} % 4 )); [ $pad -ne 0 ] && p="${p}$(printf '=%.0s' $(seq $((4-pad))))"
  printf '%s' "$p" | tr '_-' '/+' | base64 -d 2>/dev/null \
    | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("health_id") or d.get("provider_id") or d.get("sub") or "")' 2>/dev/null || echo ""
}

PASS=0; FAIL=0; SKIP=0
SUMMARY="$EVIDENCE_DIR/summary.txt"; : > "$SUMMARY"
ok(){   echo "  PASS: $1" | tee -a "$SUMMARY"; PASS=$((PASS+1)); }
bad(){  echo "  FAIL: $1" | tee -a "$SUMMARY"; FAIL=$((FAIL+1)); }
skip(){ echo "  SKIP: $1" | tee -a "$SUMMARY"; SKIP=$((SKIP+1)); }
say(){  echo "" | tee -a "$SUMMARY"; echo "== $1" | tee -a "$SUMMARY"; }
uuid(){ cat /proc/sys/kernel/random/uuid 2>/dev/null || python3 -c 'import uuid;print(uuid.uuid4())'; }

AUTH_HDR_FILE="$EVIDENCE_DIR/.auth"; trap 'rm -f "$AUTH_HDR_FILE"' EXIT
phdr(){ printf '%s' \
  "-H X-Tenant-ID:$TENANT -H X-Pod-ID:national -H X-Request-ID:$(uuid) -H X-Correlation-ID:$(uuid) "\
"-H X-Actor-Id:$ACTOR -H X-Actor-Type:PROVIDER -H X-Provider-ID:$PROVIDER_ID -H X-Facility-ID:$FACILITY_UUID "\
"-H X-Purpose-Of-Use:TREATMENT -H Content-Type:application/json"
  [ -s "$AUTH_HDR_FILE" ] && printf ' -H @%s' "$AUTH_HDR_FILE"; }
GET(){  curl -sk -m 15 -o "$EVIDENCE_DIR/.body" -w '%{http_code}' $(phdr) "$EXPERIENCE_BFF$1" 2>/dev/null; }
POST(){ curl -sk -m 20 -o "$EVIDENCE_DIR/.body" -w '%{http_code}' -X POST $(phdr) -H "Idempotency-Key:$(uuid)" --data "$2" "$EXPERIENCE_BFF$1" 2>/dev/null; }
body(){ cat "$EVIDENCE_DIR/.body" 2>/dev/null; }
jval(){ python3 -c 'import sys,json
try: d=json.load(open(sys.argv[1]))
except Exception: print(""); sys.exit()
def g(o,p):
    for k in p.split("."):
        if not isinstance(o,dict): return ""
        o=o.get(k)
    return o
v=g(d,sys.argv[2]); print(v if v is not None else "")' "$EVIDENCE_DIR/.body" "$1" 2>/dev/null; }

mint_token; ACTOR="$(derive_actor)"
if [ -n "$TOKEN" ]; then printf 'Authorization: Bearer %s\n' "$TOKEN" > "$AUTH_HDR_FILE"; else : > "$AUTH_HDR_FILE"; fi
{
  echo "Provider WALK-IN journey live drive — $TS"
  echo "bff: $EXPERIENCE_BFF  facility: $FACILITY_UUID  queueType: $QUEUE_TYPE"
  echo "auth: $( [ -n "$TOKEN" ] && echo "$PROVIDER_USER bearer minted; actor=${ACTOR:-<unresolved>} provider=$PROVIDER_ID" || echo "NO TOKEN (SKIP)" )"
} >> "$SUMMARY"

bff_up(){ curl -sk -m 5 -o /dev/null "$EXPERIENCE_BFF/actuator/health" 2>/dev/null; }

PATIENT_ID=""; PATIENT_CPID=""; JOURNEY_ID=""; ENTRY_ID=""

# W1 — provider registers a walk-in patient (the same VITO register CJ14 died on;
# a provider should be AUTHORISED where a citizen was not — this tests exactly that).
w1_register(){
  say "W1 register walk-in patient (provider-authorised VITO register)"
  bff_up || { skip "W1: BFF unreachable"; return 1; }
  local code; code=$(POST "/internal/v1/patients" \
    "{\"givenName\":\"Walkin\",\"familyName\":\"Rig$(date +%s | tail -c5)\",\"dateOfBirth\":\"1988-03-03\",\"sex\":\"M\"}")
  PATIENT_ID=$(jval healthId); [ -n "$PATIENT_ID" ] || PATIENT_ID=$(jval data.healthId); [ -n "$PATIENT_ID" ] || PATIENT_ID=$(jval data.id); [ -n "$PATIENT_ID" ] || PATIENT_ID=$(jval id)
  PATIENT_CPID=$(jval cpid); [ -n "$PATIENT_CPID" ] || PATIENT_CPID=$(jval data.cpid)
  case "$code" in
    200|201) if [ -n "$PATIENT_ID" ]; then ok "W1: walk-in patient registered ($PATIENT_ID)"; return 0
             else bad "W1: register 2xx but no patient id — body: $(body | head -c 160)"; return 1; fi;;
    403) bad "W1: provider register DENIED (403) — provider not authorised to register a walk-in (same boundary as CJ14)"; return 1;;
    502) bad "W1: register 502 (upstream VITO error) — $(body | head -c 140)"; return 1;;
    *) bad "W1: register HTTP $code — $(body | head -c 160)"; return 1;;
  esac
}

# W2 — enqueue: starts a PCT journey + puts the patient on the queue.
w2_enqueue(){
  say "W2 enqueue walk-in → start journey + queue item"
  local pref="$PATIENT_CPID"; [ -n "$pref" ] || pref="$PATIENT_ID"
  local code; code=$(POST "/internal/v1/queue/entries" \
    "{\"patient_id\":\"$PATIENT_ID\",\"patient_cpid\":\"$pref\",\"facility_id\":\"$FACILITY_UUID\",\"queue_type\":\"$QUEUE_TYPE\",\"priority\":\"ROUTINE\"}")
  JOURNEY_ID=$(jval journey_id); [ -n "$JOURNEY_ID" ] || JOURNEY_ID=$(jval data.journey_id); [ -n "$JOURNEY_ID" ] || JOURNEY_ID=$(jval journeyId); [ -n "$JOURNEY_ID" ] || JOURNEY_ID=$(jval data.journeyId); [ -n "$JOURNEY_ID" ] || JOURNEY_ID=$(jval meta.journey_id)
  ENTRY_ID=$(jval id); [ -n "$ENTRY_ID" ] || ENTRY_ID=$(jval data.id); [ -n "$ENTRY_ID" ] || ENTRY_ID=$(jval queueItemId); [ -n "$ENTRY_ID" ] || ENTRY_ID=$(jval data.queueItemId)
  case "$code" in
    200|201) if [ -n "$JOURNEY_ID" ]; then ok "W2: journey started + patient enqueued (journey=$JOURNEY_ID entry=${ENTRY_ID:-?})"; return 0
             else bad "W2: enqueue 2xx but no journey_id — body: $(body | head -c 200)"; return 1; fi;;
    400) bad "W2: enqueue 400 — $(body | head -c 180)"; return 1;;
    404) bad "W2: enqueue 404 — no queue of type $QUEUE_TYPE at facility (UI expects TRIAGE/CONSULTATION; estate has $QUEUE_TYPE)"; return 1;;
    403) bad "W2: enqueue 403 — provider not authorised for queue ops at this facility"; return 1;;
    *) bad "W2: enqueue HTTP $code — $(body | head -c 180)"; return 1;;
  esac
}

# W3 — the patient must now appear WAITING on the board.
w3_board(){
  say "W3 board shows the walk-in patient WAITING"
  local code; code=$(GET "/internal/v1/queue/entries?status=WAITING&facility_id=$FACILITY_UUID")
  if [ "$code" != "200" ]; then bad "W3: board read HTTP $code"; return 1; fi
  if [ -n "$JOURNEY_ID" ] && body | grep -q "$JOURNEY_ID"; then ok "W3: the enqueued patient appears WAITING on the board"
  elif [ -n "$ENTRY_ID" ] && body | grep -q "$ENTRY_ID"; then ok "W3: the enqueued entry appears on the board"
  else bad "W3: enqueued patient NOT on the WAITING board — enqueue didn't surface (journey=$JOURNEY_ID). body: $(body | head -c 200)"; fi
}

# W4 — call the patient in.
w4_call(){
  say "W4 call the patient in"
  [ -n "$ENTRY_ID" ] || { skip "W4: no queue entry id to call"; return 1; }
  local code; code=$(POST "/internal/v1/queue/entries/$ENTRY_ID/call" '{}')
  case "$code" in
    200|201) ok "W4: patient called in (CALLED)"; return 0;;
    404) bad "W4: call 404 — queue entry not found ($ENTRY_ID)"; return 1;;
    *) bad "W4: call HTTP $code — $(body | head -c 160)"; return 1;;
  esac
}

# W5 — start the encounter within the journey.
w5_encounter(){
  say "W5 start the clinical encounter"
  [ -n "$JOURNEY_ID" ] || { skip "W5: no journey id for encounter"; return 1; }
  local code; code=$(POST "/internal/v1/encounters" \
    "{\"journey_id\":\"$JOURNEY_ID\",\"encounter_type\":\"OUTPATIENT\",\"encounter_context\":\"WALK_IN\"}")
  local enc; enc=$(jval id); [ -n "$enc" ] || enc=$(jval data.id); [ -n "$enc" ] || enc=$(jval encounterId); [ -n "$enc" ] || enc=$(jval data.encounterId)
  case "$code" in
    200|201) if [ -n "$enc" ]; then ok "W5: encounter STARTED ($enc) — walk-in journey complete end-to-end"
             else bad "W5: encounter 2xx but no encounter id — body: $(body | head -c 160)"; fi;;
    400) bad "W5: encounter 400 — $(body | head -c 180)";;
    404) bad "W5: encounter 404 — journey not found for encounter start";;
    *) bad "W5: encounter HTTP $code — $(body | head -c 160)";;
  esac
}

if w1_register; then
  if w2_enqueue; then w3_board; w4_call; w5_encounter; fi
fi

say "VERDICT"
echo "  PASS=$PASS  FAIL=$FAIL  SKIP=$SKIP  (walk-in end-to-end = W1..W5 all PASS)" | tee -a "$SUMMARY"
echo "  evidence: $EVIDENCE_DIR/summary.txt"
[ "$FAIL" -eq 0 ] || exit 1
