#!/usr/bin/env bash
# ============================================================================
# Citizen virtual-care lane — runtime proof (J-VC-1..3, Lane E Wave 1).
#
# THE POINT: a citizen can request a teleconsult into a ROUTABLE virtual
# hospital and the request lands as a REAL pool-routed PCT referral on the
# existing teleconsult spine — visible on the pool queue, acceptable by a
# provider, completable with the COSTA value trigger firing. Honesty is
# asserted on the failure paths too.
#
#   J-VC-1  citizen registers in VITO → BFF virtual-hospital directory shows
#           honest availability (5 REQUESTABLE of 21) → POST virtual-care
#           request (video + consent when mvumo is up, else message with an
#           honest note) → psql: pct_referrals row with routing_kind=POOL +
#           routing_pool_id=GENERAL_TELEMEDICINE, status=SUBMITTED → provider
#           pool queue (BFF /teleconsult/pool/{poolId}/queue) contains it →
#           provider accept → status ACCEPTED → media token issued (rtc-gateway
#           leg; SKIP with note when the jar is absent) → complete with a
#           structured note → pct event_outbox has TELECONSULT_COMPLETED →
#           COSTA charge row for source_ref=referralId
#   J-VC-2  request to a CONFIGURED_AWAITING_SUBSTRATE virtual hospital →
#           HTTP 422 VIRTUAL_HOSPITAL_NOT_YET_ROUTABLE and NO referral row
#   J-VC-3  video request without consent → rejected (422 CONSENT_REQUIRED)
#           and NO referral row
#
# Honesty note on flags: IMPILO_SECURITY_ALLOW_ANONYMOUS=true on the BFF and
# IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true on services are RIG-ONLY (the
# same mechanism as the msika/wallet rigs; never a deploy config). The VITO
# fail-closed identity check, the consent gate, and the routable-VH gate are
# NOT bypassed — they are what this rig proves.
#
# Requirements: docker, java 21, packaged jars for: vito-service pct-service
#   costing-engine-service experience-bff  (optional: mvumo-service
#   rtc-gateway-service — legs degrade to honest SKIPs without them)
# Usage: EV=<dir> bash scripts/runtime-proof/virtual-care-journeys.sh (KEEP_RIG=1 to inspect)
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-/tmp/virtual-care-evidence}; RIGLOG=${RIGLOG:-/tmp/virtual-care-logs}
mkdir -p "$EV" "$RIGLOG"
TEN=00000000-0000-4000-8000-000000000001
PGPORT=15683; KPORT=19097; RPORT=16436
VITO=http://localhost:29082; PCT=http://localhost:29088; COSTA=http://localhost:29141
BFF=http://localhost:29161; MVUMO=http://localhost:29197; RTC=http://localhost:29198
POOL=GENERAL_TELEMEDICINE; VH=vh-national-telemedicine; VH_DARK=vh-mental-health
PASS=0; FAIL=0; SKIP=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
skip(){ echo "   SKIP: $1" | tee -a "$EV/journal.txt"; SKIP=$((SKIP+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
PSQL(){ docker exec vcare-rig-pg psql -U impilo -d "$1" -tAc "$2"; }
hdr(){ local r=$1 a=${2:-rig-$1}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Idempotency-Key:$(uuidgen) -H Idempotency-Key:$(uuidgen) -H X-Actor-ID:$a -H X-Actor-Type:$r -H X-Purpose-Of-Use:TREATMENT"; }
jval(){ python3 -c "import json,sys
r=json.load(sys.stdin)
d=r.get('data') if isinstance(r,dict) and 'data' in r else r
cur=d
for k in '$1'.split('.'):
    cur=(cur or {}).get(k) if isinstance(cur,dict) else None
print(cur if cur is not None else '')"; }
poll(){ local v=""; for i in $(seq 1 "$4"); do v=$(PSQL "$1" "$2"); [ "$v" = "$3" ] && { echo "$v"; return 0; }; sleep 5; done; echo "$v"; return 1; }
jar(){ ls "$REPO/services/$1/target/$1-"*.jar 2>/dev/null | grep -v original | head -1; }

HAVE_MVUMO=""; [ -n "$(jar mvumo-service)" ] && HAVE_MVUMO=1
HAVE_RTC="";   [ -n "$(jar rtc-gateway-service)" ] && HAVE_RTC=1
for s in vito-service pct-service costing-engine-service experience-bff; do
  [ -n "$(jar $s)" ] || { echo "FATAL: missing jar for $s — run 'mvn -DskipTests package' in services/$s"; exit 1; }
done

say "RIG: infra + services (vito + pct + costa + bff${HAVE_MVUMO:+ + mvumo}${HAVE_RTC:+ + rtc-gateway})"
docker rm -f vcare-rig-pg vcare-rig-kafka vcare-rig-redis >/dev/null 2>&1
docker run -d --name vcare-rig-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name vcare-rig-kafka -p $KPORT:$KPORT redpandadata/redpanda:latest \
  redpanda start --overprovisioned --smp 1 --memory 512M --reserve-memory 0M --node-id 0 --check=false \
  --kafka-addr PLAINTEXT://0.0.0.0:$KPORT --advertise-kafka-addr PLAINTEXT://localhost:$KPORT >/dev/null
docker run -d --name vcare-rig-redis -p $RPORT:6379 redis:7-alpine >/dev/null
sleep 12
for db in vito pct costa_db mvumo impilo_rtc; do
  docker exec vcare-rig-pg psql -U impilo -d postgres -c "CREATE DATABASE $db" >/dev/null 2>&1
done

COMMON="POSTGRES_HOST=localhost POSTGRES_PORT=$PGPORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo \
IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:$KPORT \
SPRING_KAFKA_LISTENER_AUTO_STARTUP=true SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=$RPORT \
REDIS_HOST=localhost REDIS_PORT=$RPORT"

# VITO boot guards demand a real (>=32 byte) HMAC pepper — rig value, never a deploy secret.
env $COMMON SERVER_PORT=29082 \
  VITO_HMAC_PEPPER=rig-pepper-0123456789-0123456789-0123456789 \
  nohup java -jar "$(jar vito-service)" > "$RIGLOG/vito.log" 2>&1 & echo $! > "$RIGLOG/vito.pid"
env $COMMON SERVER_PORT=29088 \
  RTC_GATEWAY_BASE_URL=$RTC \
  nohup java -jar "$(jar pct-service)" > "$RIGLOG/pct.log" 2>&1 & echo $! > "$RIGLOG/pct.pid"
env $COMMON SERVER_PORT=29141 \
  nohup java -jar "$(jar costing-engine-service)" > "$RIGLOG/costa.log" 2>&1 & echo $! > "$RIGLOG/costa.pid"
if [ -n "$HAVE_MVUMO" ]; then
  env $COMMON SERVER_PORT=29197 \
    nohup java -jar "$(jar mvumo-service)" > "$RIGLOG/mvumo.log" 2>&1 & echo $! > "$RIGLOG/mvumo.pid"
fi
if [ -n "$HAVE_RTC" ]; then
  env $COMMON SERVER_PORT=29198 RTC_DB_URL="jdbc:postgresql://localhost:$PGPORT/impilo_rtc" \
    nohup java -jar "$(jar rtc-gateway-service)" > "$RIGLOG/rtc.log" 2>&1 & echo $! > "$RIGLOG/rtc.pid"
fi
# BFF: rig-only open auth (same mechanism as the msika/wallet rigs; never a deploy config).
env $COMMON SERVER_PORT=29161 \
  PCT_BASE_URL=$PCT VITO_BASE_URL=$VITO COSTA_BASE_URL=$COSTA \
  MVUMO_BASE_URL=$MVUMO RTC_GATEWAY_BASE_URL=$RTC \
  SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration \
  IMPILO_SECURITY_ALLOW_ANONYMOUS=true \
  nohup java -jar "$(jar experience-bff)" > "$RIGLOG/bff.log" 2>&1 & echo $! > "$RIGLOG/bff.pid"

sleep 60
SVCS=("vito 29082" "pct 29088" "costa 29141" "bff 29161")
[ -n "$HAVE_MVUMO" ] && SVCS+=("mvumo 29197")
[ -n "$HAVE_RTC" ] && SVCS+=("rtc 29198")
NEED=${#SVCS[@]}; UP=0
for svc in "${SVCS[@]}"; do
  set -- $svc
  c=000
  for i in $(seq 1 18); do c=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:$2/actuator/health 2>/dev/null); [ "$c" = 200 ] && break; sleep 5; done
  echo "   $1 ($2): $c" | tee -a "$EV/journal.txt"; [ "$c" = 200 ] && UP=$((UP+1))
done
# Early exit must also kill the java pids — orphaned services from a failed run keep the
# ports and silently serve the NEXT run's requests (bit us on run 2→4: a stale PCT answered).
teardown(){ for p in "$RIGLOG"/*.pid; do kill "$(cat "$p")" 2>/dev/null; done
  [ -z "${KEEP_RIG:-}" ] && docker rm -f vcare-rig-pg vcare-rig-kafka vcare-rig-redis >/dev/null 2>&1; }
[ "$UP" = "$NEED" ] && ok "$UP/$NEED services healthy" || { bad "only $UP/$NEED healthy (see $RIGLOG)"; teardown; exit 1; }

# ── Fixture: real citizens in VITO (the identity gate is NOT bypassed) ───────
say "FIXTURE: register citizens in VITO"
# Registration goes through the client-registry lane — the same lane the BFF identity
# check reads (and the one the VITO test-security profile exposes; /v1/identity/register
# stays INTERNAL-authenticated even with oauth disabled — the 403 is fail-closed posture).
register_citizen(){ # $1 given name — echoes healthId
  curl -sS $(hdr SYSTEM rig-registrar) -X POST $VITO/v1/client-registry/registrations \
    -d "{\"registrationType\":\"SELF_INITIATED\",\"initiatedChannel\":\"CITIZEN_APP\",\"firstName\":\"$1\",\"lastName\":\"Rig\",\"dateOfBirth\":\"1990-04-01\",\"sex\":\"F\"}" | jval master.healthId
}
C1=$(register_citizen Chipo)
C2=$(register_citizen Rudo)
[ -n "$C1" ] && [ -n "$C2" ] && ok "citizens registered ($C1, $C2)" || bad "VITO registration failed (C1='$C1' C2='$C2')"

# ── J-VC-1: request → pool queue → accept → complete → COSTA ────────────────
say "J-VC-1: citizen request lands ROUTED in the VH pool; provider accepts and completes"
DIR=$(curl -sS $(hdr PATIENT $C1) "$BFF/internal/v1/virtual-care/virtual-hospitals?audience=citizen")
echo "$DIR" > "$EV/jvc1-directory.json"
TOTAL=$(echo "$DIR" | python3 -c "import json,sys;d=json.load(sys.stdin);print(len(d['data']['virtualHospitals']))")
REQABLE=$(echo "$DIR" | python3 -c "import json,sys;d=json.load(sys.stdin);print(sum(1 for h in d['data']['virtualHospitals'] if h['availability']=='REQUESTABLE'))")
[ "$TOTAL" = "21" ] && [ "$REQABLE" = "5" ] && ok "directory honest: $REQABLE/$TOTAL REQUESTABLE" || bad "directory: total=$TOTAL requestable=$REQABLE"

if [ -n "$HAVE_MVUMO" ]; then
  REQBODY="{\"virtualHospitalId\":\"$VH\",\"reason\":\"Persistent cough for two weeks\",\"modality\":\"video\",\"consentGranted\":true}"
else
  skip "mvumo jar absent — J-VC-1 uses modality=message (video consent leg proven in J-VC-3 rejection + unit tests)"
  REQBODY="{\"virtualHospitalId\":\"$VH\",\"reason\":\"Persistent cough for two weeks\",\"modality\":\"message\",\"consentGranted\":true}"
fi
REQ=$(curl -sS $(hdr PATIENT $C1) -X POST $BFF/internal/v1/virtual-care/requests -d "$REQBODY")
echo "$REQ" > "$EV/jvc1-request.json"
REF=$(echo "$REQ" | jval requestId)
[ -n "$REF" ] && ok "request queued (referral $REF)" || bad "request failed: $(echo $REQ|head -c 200)"

RK=$(PSQL pct "SELECT routing_kind FROM pct.pct_referrals WHERE referral_id='$REF'" 2>/dev/null || PSQL pct "SELECT routing_kind FROM pct_referrals WHERE referral_id='$REF'")
RP=$(PSQL pct "SELECT routing_pool_id FROM pct.pct_referrals WHERE referral_id='$REF'" 2>/dev/null || PSQL pct "SELECT routing_pool_id FROM pct_referrals WHERE referral_id='$REF'")
ST=$(PSQL pct "SELECT status FROM pct.pct_referrals WHERE referral_id='$REF'" 2>/dev/null || PSQL pct "SELECT status FROM pct_referrals WHERE referral_id='$REF'")
[ "$RK" = "POOL" ] && [ "$RP" = "$POOL" ] && ok "psql: referral routed POOL/$POOL" || bad "psql routing: kind=$RK pool=$RP"
[ "$ST" = "SUBMITTED" ] && ok "psql: referral SUBMITTED" || bad "psql status=$ST"

QUEUE=$(curl -sS $(hdr PROVIDER rig-dr-moyo) "$BFF/internal/v1/teleconsult/pool/$POOL/queue")
echo "$QUEUE" > "$EV/jvc1-pool-queue.json"
[ -n "$REF" ] && echo "$QUEUE" | grep -q "$REF" && ok "provider pool queue contains the citizen request" || bad "pool queue missing $REF: $(echo $QUEUE|head -c 200)"

ACC=$(curl -sS $(hdr PROVIDER rig-dr-moyo) -X POST $BFF/internal/v1/teleconsult/sessions/$REF/accept -d '{"notes":"rig accept"}')
echo "$ACC" > "$EV/jvc1-accept.json"
ST2=$(PSQL pct "SELECT status FROM pct.pct_referrals WHERE referral_id='$REF'" 2>/dev/null || PSQL pct "SELECT status FROM pct_referrals WHERE referral_id='$REF'")
[ "$ST2" = "ACCEPTED" ] && ok "provider accepted (psql status=ACCEPTED)" || bad "accept: psql status=$ST2"

if [ -n "$HAVE_RTC" ]; then
  TOK=$(curl -sS $(hdr PROVIDER rig-dr-moyo) -X POST $BFF/internal/v1/teleconsult/sessions/$REF/media/token -d '{"displayName":"Dr Moyo","role":"PROVIDER"}')
  echo "$TOK" > "$EV/jvc1-token.json"
  echo "$TOK" | grep -qE '"accessToken"|"token"|"status":"READY"' && ok "governed media token issued" || bad "media token: $(echo $TOK|head -c 200)"
else
  skip "rtc-gateway jar absent — media-token leg not exercised (endpoint + governance covered by BFF tests)"
fi

DONE=$(curl -sS $(hdr PROVIDER rig-dr-moyo) -X POST $BFF/internal/v1/teleconsult/sessions/$REF/complete \
  -d '{"actionsTaken":"Advised treatment","patientOutcome":"Stable","closureNarrative":"Resolved remotely"}')
echo "$DONE" > "$EV/jvc1-complete.json"
ST3=$(PSQL pct "SELECT status FROM pct.pct_referrals WHERE referral_id='$REF'" 2>/dev/null || PSQL pct "SELECT status FROM pct_referrals WHERE referral_id='$REF'")
[ "$ST3" = "COMPLETED" ] && ok "referral COMPLETED" || bad "complete: psql status=$ST3"
OUTB=$(PSQL pct "SELECT count(*) FROM pct.pct_event_outbox WHERE event_type='TELECONSULT_COMPLETED' AND aggregate_id='$REF'")
[ "$OUTB" = "1" ] && ok "value trigger emitted exactly once (outbox TELECONSULT_COMPLETED)" || bad "outbox TELECONSULT_COMPLETED count=$OUTB"
CH=$(poll costa_db "SELECT count(*) FROM costa_charge_records WHERE source_ref='$REF'" 1 18)
[ "$CH" = "1" ] && ok "COSTA charge row created for the teleconsult" || bad "COSTA charge rows=$CH (check costa consumer / kafka)"

# ── J-VC-2: non-routable VH → honest 422, no referral ───────────────────────
say "J-VC-2: request to a CONFIGURED_AWAITING_SUBSTRATE VH is an honest 422"
BEFORE=$(PSQL pct "SELECT count(*) FROM pct.pct_referrals WHERE patient_cpid='$C2'" 2>/dev/null || PSQL pct "SELECT count(*) FROM pct_referrals WHERE patient_cpid='$C2'")
R2=$(curl -sS -w '\n%{http_code}' $(hdr PATIENT $C2) -X POST $BFF/internal/v1/virtual-care/requests \
  -d "{\"virtualHospitalId\":\"$VH_DARK\",\"reason\":\"Feeling anxious\",\"modality\":\"message\"}")
CODE2=$(echo "$R2" | tail -1); BODY2=$(echo "$R2" | head -n -1); echo "$BODY2" > "$EV/jvc2-response.json"
[ "$CODE2" = "422" ] && ok "HTTP 422 for non-routable VH" || bad "HTTP $CODE2"
echo "$BODY2" | grep -q VIRTUAL_HOSPITAL_NOT_YET_ROUTABLE && ok "code VIRTUAL_HOSPITAL_NOT_YET_ROUTABLE" || bad "wrong code: $(echo $BODY2|head -c 160)"
AFTER=$(PSQL pct "SELECT count(*) FROM pct.pct_referrals WHERE patient_cpid='$C2'" 2>/dev/null || PSQL pct "SELECT count(*) FROM pct_referrals WHERE patient_cpid='$C2'")
[ "$BEFORE" = "$AFTER" ] && ok "no referral row created ($AFTER)" || bad "referral leaked: $BEFORE -> $AFTER"

# ── J-VC-3: video without consent → rejected, no referral ───────────────────
say "J-VC-3: video without consent is rejected before any referral exists"
R3=$(curl -sS -w '\n%{http_code}' $(hdr PATIENT $C2) -X POST $BFF/internal/v1/virtual-care/requests \
  -d "{\"virtualHospitalId\":\"$VH\",\"reason\":\"Skin rash\",\"modality\":\"video\",\"consentGranted\":false}")
CODE3=$(echo "$R3" | tail -1); BODY3=$(echo "$R3" | head -n -1); echo "$BODY3" > "$EV/jvc3-response.json"
[ "$CODE3" = "422" ] && ok "HTTP 422 without consent" || bad "HTTP $CODE3"
echo "$BODY3" | grep -q CONSENT_REQUIRED && ok "code CONSENT_REQUIRED" || bad "wrong code: $(echo $BODY3|head -c 160)"
AFTER3=$(PSQL pct "SELECT count(*) FROM pct.pct_referrals WHERE patient_cpid='$C2'" 2>/dev/null || PSQL pct "SELECT count(*) FROM pct_referrals WHERE patient_cpid='$C2'")
[ "$AFTER3" = "$AFTER" ] && ok "no referral row created ($AFTER3)" || bad "referral leaked on consent rejection"

echo "RESULT: PASS=$PASS FAIL=$FAIL SKIP=$SKIP" | tee -a "$EV/journal.txt"
teardown
[ "$FAIL" = 0 ]
