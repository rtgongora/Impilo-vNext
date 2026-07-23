#!/usr/bin/env bash
# ============================================================================
# Order-to-outcome — runtime proof, Wave OF-A trust-spine tranche (OF-B30 rig).
#
# THE POINT: the OF-B1/OF-B2 trust spine is REAL — immutable order versions,
# signed prescription versions, the single-active claim token, and the
# server-side repeats counter — proven against real services on a real database,
# with the §13.2 anti-fraud negatives exercised as hard refusals.
#
#   J-OO-1  OF-B1/B2 spine — provision the PRESCRIPTION_SIGNING key (fail-closed
#           purpose keys now have a provisioning path), author a coded
#           prescription, sign+activate → SIGNED_ACTIVE + exactly one ACTIVE
#           token; place a PHARMACY order → version 1 snapshot; amend → v2 with
#           supersedes chain (UNIQUE race guard schema live); hold → resume
#           round-trips to the pre-hold state.
#   J-OO-2  Journey #51 — repeat-already-dispensed: claim decrements the
#           server-side counter (2→1); SAME Idempotency-Key replays the first
#           claim without a second decrement; final claim exhausts (0, terminal
#           EXHAUSTED, token revoked EXHAUSTED); any further claim is a 409.
#   J-OO-3  Journey #50 — compensation: releasing an undispensed claim restores
#           the counter with an audited event, re-activates the EXHAUSTED
#           prescription and re-mints a token; cancel then revokes it (CANCELLED).
#   J-OO-4  §13.2.2 — the token carries NO clinical content (payload fields are
#           exactly the opaque reference set); a tampered token is refused 403;
#           the verify endpoint reports claimability without a counter effect.
#   J-OO-5  RC-7 — amendment + re-sign atomically rotates the token: the
#           predecessor token can never claim again (409), the successor claims
#           against the new current version.
#
# Marketplace tranche (journeys #41 RFO legs, #48 TTL fail-close, #49 stock
# revalidation, #52 controlled block) is added when OF-B4/B5/B6/B11 land.
#
# Honesty note: IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true is RIG-ONLY (the
# standard rig mechanism, never a deploy config). Events are asserted in the
# outbox table (the durable truth the publisher drains) — no broker needed.
#
# Requirements: docker, java 21, packaged jars: oros-service, tshepo-keys-service
# Usage: EV=<dir> bash scripts/runtime-proof/order-to-outcome-journeys.sh (KEEP_RIG=1 to inspect)
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-/tmp/order-to-outcome-evidence}; RIGLOG=${RIGLOG:-/tmp/order-to-outcome-logs}
mkdir -p "$EV" "$RIGLOG"
TEN=00000000-0000-4000-8000-000000000001
PGPORT=15891; RPORT=16891
OROS=http://localhost:29891
KEYS=http://localhost:29892
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
PSQL(){ docker exec oo-rig-pg psql -U impilo -d "$1" -tAc "$2" 2>/dev/null | tr -d '[:space:]'; }
hdr(){ local r=$1 a=${2:-rig-$1}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Actor-ID:$a -H X-Actor-Type:$r -H X-Purpose-Of-Use:TREATMENT -H X-Facility-ID:$(uuidgen)"; }
jval(){ python3 -c "import json,sys
r=json.load(sys.stdin)
d=r.get('data') if isinstance(r,dict) and 'data' in r else r
cur=d
for k in '$1'.split('.'):
    cur=(cur or {}).get(k) if isinstance(cur,dict) else None
print(cur if cur is not None else '')"; }
jar(){ ls "$REPO/services/$1/target/$1-"*.jar 2>/dev/null | grep -v original | head -1; }

for s in oros-service tshepo-keys-service; do
  [ -n "$(jar $s)" ] || { echo "FATAL: missing jar for $s — run 'mvn -o -DskipTests package' in services/$s"; exit 1; }
done

say "RIG: postgres + redis + oros-service + tshepo-keys-service"
docker rm -f oo-rig-pg oo-rig-redis >/dev/null 2>&1
docker run -d --name oo-rig-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name oo-rig-redis -p $RPORT:6379 redis:7-alpine >/dev/null
# Postgres-in-docker double-start: initdb's temp server answers pg_isready, then
# restarts — retry CREATE DATABASE until it truly lands on the final server.
dbready=no
for _ in $(seq 1 90); do
  if docker exec oo-rig-pg psql -U impilo -d postgres -c "SELECT 1" >/dev/null 2>&1; then
    docker exec oo-rig-pg psql -U impilo -d postgres -c "CREATE DATABASE oros" >/dev/null 2>&1
    docker exec oo-rig-pg psql -U impilo -d postgres -c "CREATE DATABASE tshepo_keys" >/dev/null 2>&1
    if docker exec oo-rig-pg psql -U impilo -d oros -c "SELECT 1" >/dev/null 2>&1 \
       && docker exec oo-rig-pg psql -U impilo -d tshepo_keys -c "SELECT 1" >/dev/null 2>&1; then
      dbready=yes; break
    fi
  fi
  sleep 1
done
[ "$dbready" = yes ] || { echo "FATAL: rig databases never became ready"; exit 1; }

env POSTGRES_HOST=localhost POSTGRES_PORT=$PGPORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo \
  POSTGRES_DB=tshepo_keys IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true \
  SERVER_PORT=29892 SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:19999 \
  SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=$RPORT REDIS_HOST=localhost REDIS_PORT=$RPORT \
  nohup java -jar "$(jar tshepo-keys-service)" > "$RIGLOG/keys.log" 2>&1 & echo $! > "$RIGLOG/keys.pid"

env POSTGRES_HOST=localhost POSTGRES_PORT=$PGPORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo \
  POSTGRES_DB=oros IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true \
  SERVER_PORT=29891 SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:19999 \
  SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=$RPORT REDIS_HOST=localhost REDIS_PORT=$RPORT \
  OROS_INTEGRATION_TSHEPO_KEYS_URL=$KEYS \
  OROS_PRESCRIPTIONS_TOKEN_TTL_HOURS=72 \
  nohup java -jar "$(jar oros-service)" > "$RIGLOG/oros.log" 2>&1 & echo $! > "$RIGLOG/oros.pid"

cleanup(){
  [ "${KEEP_RIG:-0}" = 1 ] && { echo "KEEP_RIG=1 — leaving rig up"; return; }
  kill "$(cat "$RIGLOG/oros.pid" 2>/dev/null)" "$(cat "$RIGLOG/keys.pid" 2>/dev/null)" >/dev/null 2>&1
  docker rm -f oo-rig-pg oo-rig-redis >/dev/null 2>&1
}
trap cleanup EXIT

say "waiting for services"
for svc in "$KEYS/actuator/health" "$OROS/actuator/health"; do
  up=no
  for _ in $(seq 1 120); do
    curl -sf "$svc" >/dev/null 2>&1 && { up=yes; break; }; sleep 1
  done
  [ "$up" = yes ] || { echo "FATAL: $svc never came up"; tail -30 "$RIGLOG"/*.log; exit 1; }
done

# ── J-OO-1: trust spine ─────────────────────────────────────────────────────
say "J-OO-1: key provisioning + signed prescription + order versioning"

PROV=$(curl -s -X POST $(hdr SYSTEM key-admin) "$KEYS/v1/keys/provision" \
  -d "{\"tenantId\":\"$TEN\",\"purpose\":\"PRESCRIPTION_SIGNING\"}")
KEYID=$(echo "$PROV" | python3 -c "import json,sys; print(json.load(sys.stdin).get('keyId',''))" 2>/dev/null)
[ -n "$KEYID" ] && ok "PRESCRIPTION_SIGNING key provisioned ($KEYID)" || bad "key provisioning failed: $PROV"
# Idempotency: second provision returns the same key
PROV2=$(curl -s -X POST $(hdr SYSTEM key-admin) "$KEYS/v1/keys/provision" \
  -d "{\"tenantId\":\"$TEN\",\"purpose\":\"PRESCRIPTION_SIGNING\"}")
KEYID2=$(echo "$PROV2" | python3 -c "import json,sys; print(json.load(sys.stdin).get('keyId',''))" 2>/dev/null)
[ "$KEYID" = "$KEYID2" ] && ok "provisioning idempotent (same key)" || bad "provision minted a second key: $KEYID2"

CPID="RIG-CPID-$(date +%s)"
RX=$(curl -s -X POST $(hdr PROVIDER dr-rig) "$OROS/v1/prescriptions" -d "{
  \"patientCpid\":\"$CPID\",\"repeatsCeiling\":2,
  \"validityStart\":\"$(date +%F)\",\"validityEnd\":\"$(date -d '+6 months' +%F 2>/dev/null || date -v+6m +%F)\",
  \"indication\":\"Hypertension\",
  \"items\":[{\"medicationCode\":\"C09AA05\",\"displayName\":\"Ramipril 5mg\",\"dose\":\"5mg\",\"route\":\"PO\",\"frequency\":\"OD\",\"durationDays\":30}]}")
RXID=$(echo "$RX" | jval prescriptionId)
[ -n "$RXID" ] && ok "prescription drafted ($RXID)" || bad "draft failed: $RX"

SIGN=$(curl -s -X POST $(hdr PROVIDER dr-rig) "$OROS/v1/prescriptions/$RXID/sign")
[ "$(echo "$SIGN" | jval status)" = "SIGNED_ACTIVE" ] && ok "sign+activate atomic → SIGNED_ACTIVE" || bad "sign failed: $SIGN"
VCOUNT=$(PSQL oros "SELECT count(*) FROM oros_prescription_versions WHERE prescription_id='$RXID' AND signature_jws IS NOT NULL")
[ "$VCOUNT" = "1" ] && ok "signed version persisted with JWS" || bad "signed versions=$VCOUNT"
TCOUNT=$(PSQL oros "SELECT count(*) FROM oros_prescription_tokens WHERE prescription_id='$RXID' AND status='ACTIVE'")
[ "$TCOUNT" = "1" ] && ok "exactly one ACTIVE token (single-active invariant)" || bad "active tokens=$TCOUNT"

# Order versioning
ORDER=$(curl -s -X POST $(hdr PROVIDER dr-rig) "$OROS/v1/orders" -d "{
  \"orderType\":\"PHARMACY\",\"patientCpid\":\"$CPID\",\"ziboOrderCode\":\"RIG-PH-1\",
  \"clinicalNotes\":\"initial\",\"items\":[{\"code\":\"C09AA05\",\"displayName\":\"Ramipril\",\"quantity\":1}]}")
OID=$(echo "$ORDER" | jval orderId)
[ -n "$OID" ] && ok "order placed ($OID)" || bad "order place failed: $ORDER"
[ "$(PSQL oros "SELECT version FROM oros_order_versions WHERE order_id='$OID'")" = "1" ] \
  && ok "version 1 snapshot on placement" || bad "no v1 snapshot"

AMEND=$(curl -s -X POST $(hdr PROVIDER dr-rig) "$OROS/v1/orders/$OID/amend" \
  -d '{"clinicalNotes":"amended after clarification","reason":"Pharmacy clarification"}')
[ "$(echo "$AMEND" | jval version)" = "2" ] && ok "amend → immutable version 2" || bad "amend failed: $AMEND"
CHAIN=$(PSQL oros "SELECT count(*) FROM oros_order_versions v2 JOIN oros_order_versions v1 ON v2.supersedes_version_id=v1.order_version_id WHERE v2.order_id='$OID' AND v2.version=2 AND v1.version=1")
[ "$CHAIN" = "1" ] && ok "supersedes chain v2→v1" || bad "supersedes chain broken"

curl -s -X POST $(hdr PROVIDER dr-rig) "$OROS/v1/orders/$OID/hold" -d '{"reason":"PA pending"}' >/dev/null
[ "$(PSQL oros "SELECT status FROM oros_orders WHERE order_id='$OID'")" = "ON_HOLD" ] \
  && ok "hold applied (T4)" || bad "hold failed"
curl -s -X POST $(hdr PROVIDER dr-rig) "$OROS/v1/orders/$OID/resume" >/dev/null
[ "$(PSQL oros "SELECT status FROM oros_orders WHERE order_id='$OID'")" = "PLACED" ] \
  && ok "resume returns to pre-hold state (T5)" || bad "resume failed"

# ── J-OO-2: journey #51 — server-side counter is the only decrement path ────
say "J-OO-2 (#51): claim, idempotent replay, exhaustion, refusal"
TOKEN=$(curl -s $(hdr PROVIDER dr-rig) "$OROS/v1/prescriptions/$RXID/token" | jval tokenJws)
[ -n "$TOKEN" ] && ok "active token retrieved" || bad "no token"

K1="rig-claim-$(uuidgen)"
C1=$(curl -s -X POST $(hdr PROVIDER pharmacist-a) -H "Idempotency-Key:$K1" \
  "$OROS/v1/prescriptions/claims" -d "{\"token\":\"$TOKEN\",\"vendorRef\":\"pharmacy-A\"}")
[ "$(echo "$C1" | jval repeatsRemaining)" = "1" ] && ok "claim 1 decrements counter 2→1" || bad "claim1: $C1"
LINES=$(echo "$C1" | python3 -c "import json,sys; print(len(json.load(sys.stdin)['data']['lines']))")
[ "$LINES" = "1" ] && ok "authoritative lines retrieved server-side" || bad "lines=$LINES"

C1R=$(curl -s -X POST $(hdr PROVIDER pharmacist-a) -H "Idempotency-Key:$K1" \
  "$OROS/v1/prescriptions/claims" -d "{\"token\":\"$TOKEN\",\"vendorRef\":\"pharmacy-A\"}")
[ "$(echo "$C1R" | jval duplicateReplay)" = "True" ] && ok "same-key replay returns first claim" || bad "replay: $C1R"
[ "$(PSQL oros "SELECT repeats_remaining FROM oros_prescriptions WHERE prescription_id='$RXID'")" = "1" ] \
  && ok "no second decrement on replay" || bad "counter moved on replay"

C2=$(curl -s -X POST $(hdr PROVIDER pharmacist-b) -H "Idempotency-Key:rig-claim-$(uuidgen)" \
  "$OROS/v1/prescriptions/claims" -d "{\"token\":\"$TOKEN\",\"vendorRef\":\"pharmacy-B\"}")
CLAIM2=$(echo "$C2" | jval claimId)
[ "$(echo "$C2" | jval repeatsRemaining)" = "0" ] && ok "final claim exhausts (1→0)" || bad "claim2: $C2"
[ "$(PSQL oros "SELECT status FROM oros_prescriptions WHERE prescription_id='$RXID'")" = "EXHAUSTED" ] \
  && ok "prescription EXHAUSTED (terminal §9.2)" || bad "not exhausted"
[ "$(PSQL oros "SELECT status FROM oros_prescription_tokens WHERE prescription_id='$RXID' ORDER BY issued_at DESC LIMIT 1")" = "REVOKED" ] \
  && ok "exhaustion revoked the token" || bad "token not revoked"

C3=$(curl -s -o /dev/null -w '%{http_code}' -X POST $(hdr PROVIDER pharmacist-c) \
  "$OROS/v1/prescriptions/claims" -d "{\"token\":\"$TOKEN\"}")
[ "$C3" = "409" ] && ok "post-exhaustion claim refused 409 (#51)" || bad "refusal http=$C3"

# ── J-OO-3: journey #50 — compensation release ──────────────────────────────
say "J-OO-3 (#50): release restores counter + re-mints token; cancel revokes"
REL=$(curl -s -X POST $(hdr PROVIDER pharmacist-b) "$OROS/v1/prescriptions/claims/$CLAIM2/release" \
  -d '{"reason":"Episode cancelled before dispensing"}')
[ "$(echo "$REL" | jval claimStatus)" = "RELEASED" ] && ok "claim released" || bad "release: $REL"
[ "$(PSQL oros "SELECT repeats_remaining||'/'||status FROM oros_prescriptions WHERE prescription_id='$RXID'")" = "1/SIGNED_ACTIVE" ] \
  && ok "counter restored + re-activated (audited compensation)" || bad "restore failed"
[ "$(PSQL oros "SELECT count(*) FROM oros_prescription_tokens WHERE prescription_id='$RXID' AND status='ACTIVE'")" = "1" ] \
  && ok "token re-minted after reactivation" || bad "no re-minted token"
[ "$(PSQL oros "SELECT count(*) FROM oros_event_outbox WHERE aggregate_id='$RXID' AND event_type='PRESCRIPTION_CLAIM_RELEASED'")" = "1" ] \
  && ok "compensation event in outbox" || bad "no compensation event"

CAN=$(curl -s -X POST $(hdr PROVIDER dr-rig) "$OROS/v1/prescriptions/$RXID/cancel" -d '{"reason":"Therapy changed"}')
[ "$(echo "$CAN" | jval status)" = "CANCELLED" ] && ok "prescriber cancel (unclaimed remainder)" || bad "cancel: $CAN"
[ "$(PSQL oros "SELECT revoke_reason FROM oros_prescription_tokens WHERE prescription_id='$RXID' AND revoke_reason='CANCELLED'" )" = "CANCELLED" ] \
  && ok "cancel revoked the active token" || bad "cancel token revocation missing"

# ── J-OO-4: §13.2.2 — no clinical content; tamper refused; verify read-only ─
say "J-OO-4 (§13.2.2): token opacity + tamper refusal + verify"
RX2=$(curl -s -X POST $(hdr PROVIDER dr-rig) "$OROS/v1/prescriptions" -d "{
  \"patientCpid\":\"$CPID\",\"repeatsCeiling\":1,
  \"items\":[{\"medicationCode\":\"N02BE01\",\"displayName\":\"Paracetamol 500mg\"}]}")
RXID2=$(echo "$RX2" | jval prescriptionId)
curl -s -X POST $(hdr PROVIDER dr-rig) "$OROS/v1/prescriptions/$RXID2/sign" >/dev/null
TOKEN2=$(curl -s $(hdr PROVIDER dr-rig) "$OROS/v1/prescriptions/$RXID2/token" | jval tokenJws)
PAYLOAD_FIELDS=$(python3 -c "
import base64, json, sys
p='$TOKEN2'.split('.')[1]
p+='='*(-len(p)%4)
d=json.loads(base64.urlsafe_b64decode(p))
print(','.join(sorted(d.keys())))")
[ "$PAYLOAD_FIELDS" = "expires_at,issued_at,prescription_version_id,token_id" ] \
  && ok "token payload = opaque reference set ONLY (no clinical content)" || bad "payload fields: $PAYLOAD_FIELDS"
echo "$TOKEN2" | grep -qi "$CPID" && bad "CPID leaked into token" || ok "no patient identifier in token bytes"

TAMPERED="${TOKEN2%????}EVIL"
T4=$(curl -s -o /dev/null -w '%{http_code}' -X POST $(hdr PROVIDER pharmacist-x) \
  "$OROS/v1/prescriptions/claims" -d "{\"token\":\"$TAMPERED\"}")
[ "$T4" = "403" ] && ok "tampered token refused 403" || bad "tamper http=$T4"

VER=$(curl -s -X POST $(hdr PROVIDER pharmacist-x) "$OROS/v1/prescriptions/tokens/verify" -d "{\"token\":\"$TOKEN2\"}")
[ "$(echo "$VER" | jval valid)" = "True" ] && ok "verify: claimable (read-only pre-check)" || bad "verify: $VER"
[ "$(PSQL oros "SELECT repeats_remaining FROM oros_prescriptions WHERE prescription_id='$RXID2'")" = "1" ] \
  && ok "verify had no counter effect" || bad "verify decremented!"

# ── J-OO-5: RC-7 — amendment rotates the token atomically ───────────────────
say "J-OO-5 (RC-7): amend + re-sign → predecessor token dead, successor claims"
AM5=$(curl -s -X POST $(hdr PROVIDER dr-rig) "$OROS/v1/prescriptions/$RXID2/amend" -d '{
  "items":[{"medicationCode":"N02BE01","displayName":"Paracetamol 500mg","dose":"1g"}],
  "reason":"Dose corrected"}')
echo "   amend: $AM5" >> "$EV/journal.txt"
[ "$(echo "$AM5" | jval headVersion.version)" = "2" ] && ok "amendment v2 created (unsigned)" || bad "amend: $AM5"
SG5=$(curl -s -X POST $(hdr PROVIDER dr-rig) "$OROS/v1/prescriptions/$RXID2/sign")
echo "   sign: $SG5" >> "$EV/journal.txt"
[ "$(echo "$SG5" | jval status)" = "SIGNED_ACTIVE" ] && ok "amendment signed+activated" || bad "re-sign: $SG5"
OLD=$(curl -s -o /dev/null -w '%{http_code}' -X POST $(hdr PROVIDER pharmacist-x) \
  "$OROS/v1/prescriptions/claims" -d "{\"token\":\"$TOKEN2\"}")
[ "$OLD" = "409" ] && ok "predecessor token refused after amendment (RC-7 family)" || bad "old token http=$OLD"
TOKEN3=$(curl -s $(hdr PROVIDER dr-rig) "$OROS/v1/prescriptions/$RXID2/token" | jval tokenJws)
C5=$(curl -s -X POST $(hdr PROVIDER pharmacist-x) -H "Idempotency-Key:rig-$(uuidgen)" \
  "$OROS/v1/prescriptions/claims" -d "{\"token\":\"$TOKEN3\"}")
V5=$(echo "$C5" | jval prescriptionVersionId)
CURV=$(PSQL oros "SELECT current_version_id FROM oros_prescriptions WHERE prescription_id='$RXID2'")
[ -n "$V5" ] && [ "$V5" = "$CURV" ] && ok "successor token claims against current version" || bad "v2 claim: $C5"

say "RESULT: PASS=$PASS FAIL=$FAIL"
cp "$RIGLOG"/*.log "$EV/" 2>/dev/null
[ "$FAIL" = 0 ] || exit 1
