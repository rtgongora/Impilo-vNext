#!/usr/bin/env bash
# ============================================================================
# Teleconsult hardening — runtime proof (J-TH-1..4, TM-B20 proof expansion).
#
# THE POINT: the A/B-wave guards that harden the teleconsult spine are REAL and
# provable in isolation, and the lifecycle events they emit are VERSIONED (.v1).
# This rig boots pct-service alone (the service that owns the state machine, the
# lifecycle timer and the outbox) with the transition guard flipped to ENFORCE,
# and drives the guarded edges directly over PCT's REST surface.
#
#   J-TH-1  TM-B20 event versioning — create + submit a referral →
#           every `telemedicine.session.*` outbox row is suffixed `.v1`
#           and NOT ONE bare (unversioned) telemedicine.session row exists
#   J-TH-2  A1 illegal transition (ENFORCE) — POST /complete on a still-SUBMITTED
#           referral (SUBMITTED->COMPLETED is not in the §11.3 matrix) → HTTP 409
#           ILLEGAL_TRANSITION, status stays SUBMITTED, and the transition ledger
#           records the refused edge (allowed=false, mode=ENFORCE)
#   J-TH-3  A1 allowed-actions — GET /allowed-actions for the SUBMITTED referral
#           advertises ACCEPTED and withholds COMPLETED (guard drives the UI)
#   J-TH-4  B4 no-show / expiry timer — backdate submitted_at past the expiry
#           window → the lifecycle sweep transitions SUBMITTED->EXPIRED through the
#           same guard (allowed=true, edge "expire") and emits
#           telemedicine.session.expired.v1
#
# Honesty note on flags: IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true is RIG-ONLY
# (same mechanism as the virtual-care/msika/wallet rigs; never a deploy config).
# The transition guard runs in ENFORCE here to PROVE it rejects — production runs
# it in SHADOW until the flip criterion (zero allowed=false rows on happy traffic)
# is met; this rig is exactly that criterion's evidence.
#
# Requirements: docker, java 21, packaged jar for: pct-service
# Usage: EV=<dir> bash scripts/runtime-proof/teleconsult-hardening-journeys.sh (KEEP_RIG=1 to inspect)
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-/tmp/teleconsult-hardening-evidence}; RIGLOG=${RIGLOG:-/tmp/teleconsult-hardening-logs}
mkdir -p "$EV" "$RIGLOG"
TEN=00000000-0000-4000-8000-000000000001
PGPORT=15783; KPORT=19197; RPORT=16536
PCT=http://localhost:29388
POOL=GENERAL_TELEMEDICINE
PASS=0; FAIL=0; SKIP=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
skip(){ echo "   SKIP: $1" | tee -a "$EV/journal.txt"; SKIP=$((SKIP+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
PSQL(){ docker exec th-rig-pg psql -U impilo -d "$1" -tAc "$2" 2>/dev/null | tr -d '[:space:]'; }
hdr(){ local r=$1 a=${2:-rig-$1}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Idempotency-Key:$(uuidgen) -H Idempotency-Key:$(uuidgen) -H X-Actor-ID:$a -H X-Actor-Type:$r -H X-Purpose-Of-Use:TREATMENT"; }
jval(){ python3 -c "import json,sys
r=json.load(sys.stdin)
d=r.get('data') if isinstance(r,dict) and 'data' in r else r
cur=d
for k in '$1'.split('.'):
    cur=(cur or {}).get(k) if isinstance(cur,dict) else None
print(cur if cur is not None else '')"; }
jar(){ ls "$REPO/services/$1/target/$1-"*.jar 2>/dev/null | grep -v original | head -1; }

[ -n "$(jar pct-service)" ] || { echo "FATAL: missing jar for pct-service — run 'mvn -DskipTests package' in services/pct-service"; exit 1; }

say "RIG: infra + pct-service (transition guard=ENFORCE, lifecycle sweep=5s, expiry=1440m)"
docker rm -f th-rig-pg th-rig-kafka th-rig-redis >/dev/null 2>&1
docker run -d --name th-rig-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name th-rig-kafka -p $KPORT:$KPORT redpandadata/redpanda:latest \
  redpanda start --overprovisioned --smp 1 --memory 512M --reserve-memory 0M --node-id 0 --check=false \
  --kafka-addr PLAINTEXT://0.0.0.0:$KPORT --advertise-kafka-addr PLAINTEXT://localhost:$KPORT >/dev/null
docker run -d --name th-rig-redis -p $RPORT:6379 redis:7-alpine >/dev/null
sleep 12
docker exec th-rig-pg psql -U impilo -d postgres -c "CREATE DATABASE pct" >/dev/null 2>&1

env POSTGRES_HOST=localhost POSTGRES_PORT=$PGPORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo \
  IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:$KPORT \
  SPRING_KAFKA_LISTENER_AUTO_STARTUP=true SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=$RPORT \
  REDIS_HOST=localhost REDIS_PORT=$RPORT SERVER_PORT=29388 \
  PCT_TELEMEDICINE_TRANSITIONS_MODE=ENFORCE \
  PCT_TELEMEDICINE_LIFECYCLE_ENABLED=true \
  PCT_TELEMEDICINE_LIFECYCLE_EXPIRY_MINUTES=1440 \
  PCT_TELEMEDICINE_LIFECYCLE_SWEEP_INTERVAL_MS=5000 \
  nohup java -jar "$(jar pct-service)" > "$RIGLOG/pct.log" 2>&1 & echo $! > "$RIGLOG/pct.pid"

teardown(){ [ -f "$RIGLOG/pct.pid" ] && kill "$(cat "$RIGLOG/pct.pid")" 2>/dev/null
  [ -z "${KEEP_RIG:-}" ] && docker rm -f th-rig-pg th-rig-kafka th-rig-redis >/dev/null 2>&1; }

c=000
for i in $(seq 1 24); do c=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:29388/actuator/health 2>/dev/null); [ "$c" = 200 ] && break; sleep 5; done
[ "$c" = 200 ] && ok "pct-service healthy" || { bad "pct-service not healthy ($c) — see $RIGLOG/pct.log"; teardown; exit 1; }

# resolve schema-qualified table names (flyway may use `pct.` schema or public)
RTBL=pct_referrals; OTBL=pct_event_outbox; LTBL=pct_referral_transitions
[ "$(PSQL pct "SELECT to_regclass('pct.pct_referrals')")" != "" ] && [ "$(PSQL pct "SELECT to_regclass('pct.pct_referrals')")" != "" ] && RTBL=pct.pct_referrals OTBL=pct.pct_event_outbox LTBL=pct.pct_referral_transitions

mkref(){ # $1 mode(message/video) → echoes referralId
  curl -sS $(hdr PROVIDER rig-dr-moyo) -X POST $PCT/v1/referrals \
    -d "{\"patientId\":\"$(uuidgen)\",\"reason\":\"Hardening rig case\",\"virtual_mode\":\"$1\",\"routing_pool_id\":\"$POOL\",\"routing_kind\":\"POOL\"}" | jval id; }

# ── J-TH-1: TM-B20 event versioning ─────────────────────────────────────────
say "J-TH-1: lifecycle events are versioned .v1 (create + submit)"
REF=$(mkref message)
[ -n "$REF" ] && ok "referral created ($REF)" || { bad "create failed"; teardown; exit 1; }
curl -sS $(hdr PROVIDER rig-dr-moyo) -X POST $PCT/v1/referrals/$REF/submit >/dev/null
ST=$(PSQL pct "SELECT status FROM $RTBL WHERE referral_id='$REF'")
[ "$ST" = "SUBMITTED" ] && ok "referral SUBMITTED" || bad "status=$ST (expected SUBMITTED)"
V1=$(PSQL pct "SELECT count(*) FROM $OTBL WHERE aggregate_id='$REF' AND event_type LIKE 'telemedicine.session.%.v1'")
BARE=$(PSQL pct "SELECT count(*) FROM $OTBL WHERE aggregate_id='$REF' AND event_type LIKE 'telemedicine.session.%' AND event_type NOT LIKE '%.v1'")
[ "${V1:-0}" -ge 1 ] && ok "≥1 versioned telemedicine.session.*.v1 outbox row ($V1)" || bad "no .v1 lifecycle rows ($V1)"
[ "${BARE:-0}" = 0 ] && ok "zero unversioned telemedicine.session rows (TM-B20 acceptance)" || bad "$BARE bare unversioned lifecycle rows leaked"

# ── J-TH-5: TM-B5/OD-10 chat transcript is durably case-persisted with provenance ──
say "J-TH-5: session chat persists to the case (referral.messages) with server provenance"
curl -sS $(hdr PROVIDER rig-dr-moyo) -X POST $PCT/v1/referrals/$REF/respond \
  -d '{"response_type":"MESSAGE","message":"Please describe the cough at night","responder_id":"rig-dr-moyo"}' >/dev/null
MSGCT=$(PSQL pct "SELECT jsonb_array_length(messages) FROM $RTBL WHERE referral_id='$REF'")
[ "${MSGCT:-0}" -ge 1 ] && ok "chat message stored on the case ($MSGCT in referral.messages)" || bad "message not persisted ($MSGCT)"
HASTS=$(PSQL pct "SELECT count(*) FROM $RTBL WHERE referral_id='$REF' AND messages @> '[{\"responderId\":\"rig-dr-moyo\"}]' AND messages::text LIKE '%timestamp%'")
[ "${HASTS:-0}" = 1 ] && ok "message carries server provenance (author + timestamp)" || bad "message missing provenance ($HASTS)"

# ── J-TH-6: B5-2 media-modality downgrade ladder VIDEO→AUDIO→ASYNC ───────────
say "J-TH-6: media downgrade ladder is monotonic + recorded + versioned-evented"
REF2=$(mkref video); curl -sS $(hdr PROVIDER rig-dr-moyo) -X POST $PCT/v1/referrals/$REF2/submit >/dev/null
curl -sS $(hdr PROVIDER rig-dr-moyo) -X POST $PCT/v1/referrals/$REF2/media-modality -d '{"modality":"AUDIO","reason":"video froze"}' >/dev/null
M1=$(PSQL pct "SELECT media_modality FROM $RTBL WHERE referral_id='$REF2'")
[ "$M1" = "AUDIO" ] && ok "VIDEO→AUDIO recorded (media_modality=AUDIO)" || bad "step-down not recorded ($M1)"
DEVT=$(PSQL pct "SELECT count(*) FROM $OTBL WHERE aggregate_id='$REF2' AND event_type='telemedicine.session.media_downgraded.v1'")
[ "${DEVT:-0}" -ge 1 ] && ok "emitted telemedicine.session.media_downgraded.v1" || bad "no downgrade event ($DEVT)"
curl -sS $(hdr PROVIDER rig-dr-moyo) -X POST $PCT/v1/referrals/$REF2/media-modality -d '{"modality":"ASYNC","reason":"audio dropped"}' >/dev/null
M2=$(PSQL pct "SELECT media_modality FROM $RTBL WHERE referral_id='$REF2'")
[ "$M2" = "ASYNC" ] && ok "AUDIO→ASYNC recorded (continues on durable chat)" || bad "async step not recorded ($M2)"
UP=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr PROVIDER rig-dr-moyo) -X POST $PCT/v1/referrals/$REF2/media-modality -d '{"modality":"VIDEO","reason":"try again"}')
[ "$UP" = "409" ] && ok "illegal step-UP ASYNC→VIDEO refused 409 (no silent reversal)" || bad "step-up not rejected (HTTP $UP)"
REST=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr PROVIDER rig-dr-moyo) -X POST $PCT/v1/referrals/$REF2/media-modality -d '{"modality":"VIDEO","reason":"bandwidth back","restore":true}')
M3=$(PSQL pct "SELECT media_modality FROM $RTBL WHERE referral_id='$REF2'")
[ "$REST" = "200" ] && [ "$M3" = "VIDEO" ] && ok "provider restore=true steps back up to VIDEO" || bad "restore failed (HTTP $REST, modality=$M3)"

# ── J-TH-7: B5-3 provider-only side-channel is structurally isolated ─────────
say "J-TH-7: provider note lands in provider_notes, NEVER in the patient-visible messages"
SECRET="INTERNAL: discuss dosage privately"
curl -sS $(hdr PROVIDER rig-dr-moyo) -X POST $PCT/v1/referrals/$REF2/provider-note \
  -d "{\"note\":\"$SECRET\",\"author\":\"rig-dr-moyo\"}" >/dev/null
PN=$(PSQL pct "SELECT jsonb_array_length(provider_notes) FROM $RTBL WHERE referral_id='$REF2'")
[ "${PN:-0}" -ge 1 ] && ok "provider note stored in provider_notes ($PN)" || bad "provider note not stored ($PN)"
LEAK=$(PSQL pct "SELECT count(*) FROM $RTBL WHERE referral_id='$REF2' AND messages::text LIKE '%dosage privately%'")
[ "${LEAK:-1}" = 0 ] && ok "provider note ABSENT from patient-visible messages (no leak)" || bad "LEAK: provider note found in messages ($LEAK)"
GPN=$(curl -sS $(hdr PROVIDER rig-dr-moyo) $PCT/v1/referrals/$REF2/provider-notes)
echo "$GPN" | grep -q "dosage privately" && ok "provider read-back returns the side-channel note" || bad "provider notes GET missing note"
PEVT=$(PSQL pct "SELECT count(*) FROM $OTBL WHERE aggregate_id='$REF2' AND event_type='telemedicine.session.provider_note_added.v1'")
[ "${PEVT:-0}" -ge 1 ] && ok "emitted telemedicine.session.provider_note_added.v1" || bad "no provider_note event ($PEVT)"

# ── J-TH-2: A1 illegal transition rejected under ENFORCE ─────────────────────
say "J-TH-2: SUBMITTED->COMPLETED is refused 409 ILLEGAL_TRANSITION (ENFORCE)"
R=$(curl -sS -w '\n%{http_code}' $(hdr PROVIDER rig-dr-moyo) -X POST $PCT/v1/referrals/$REF/complete \
  -d '{"actionsTaken":"skip accept","patientOutcome":"n/a","closureNarrative":"illegal edge"}')
CODE=$(echo "$R" | tail -1); BODY=$(echo "$R" | head -n -1); echo "$BODY" > "$EV/jth2-complete.json"
[ "$CODE" = "409" ] && ok "HTTP 409 on illegal complete" || bad "HTTP $CODE (expected 409)"
echo "$BODY" | grep -q ILLEGAL_TRANSITION && ok "code ILLEGAL_TRANSITION" || bad "wrong code: $(echo "$BODY" | head -c 160)"
ST2=$(PSQL pct "SELECT status FROM $RTBL WHERE referral_id='$REF'")
[ "$ST2" = "SUBMITTED" ] && ok "status unchanged (still SUBMITTED)" || bad "status drifted to $ST2"
LREF=$(PSQL pct "SELECT count(*) FROM $LTBL WHERE referral_id='$REF' AND allowed=false AND mode='ENFORCE'")
[ "${LREF:-0}" -ge 1 ] && ok "transition ledger recorded the refused edge (allowed=false, ENFORCE)" || bad "no refused-edge ledger row ($LREF)"

# ── J-TH-3: A1 allowed-actions advertised by the guard ──────────────────────
say "J-TH-3: allowed-actions advertises ACCEPTED, withholds COMPLETED"
AA=$(curl -sS $(hdr PROVIDER rig-dr-moyo) "$PCT/v1/referrals/$REF/allowed-actions"); echo "$AA" > "$EV/jth3-actions.json"
echo "$AA" | grep -q ACCEPTED && ok "ACCEPTED offered for SUBMITTED" || bad "ACCEPTED missing: $(echo "$AA" | head -c 160)"
echo "$AA" | grep -qi '"COMPLETED"' && bad "COMPLETED wrongly offered for SUBMITTED" || ok "COMPLETED correctly withheld"

# ── J-TH-4: B4 no-show / expiry timer sweeps through the guard ──────────────
say "J-TH-4: backdated SUBMITTED referral expires via the lifecycle sweep"
PSQL pct "UPDATE $RTBL SET submitted_at = now() - interval '3 days' WHERE referral_id='$REF'" >/dev/null
EXP=""
for i in $(seq 1 12); do EXP=$(PSQL pct "SELECT status FROM $RTBL WHERE referral_id='$REF'"); [ "$EXP" = "EXPIRED" ] && break; sleep 5; done
[ "$EXP" = "EXPIRED" ] && ok "lifecycle sweep transitioned SUBMITTED->EXPIRED" || bad "status=$EXP after sweep (expected EXPIRED)"
EXPV1=$(PSQL pct "SELECT count(*) FROM $OTBL WHERE aggregate_id='$REF' AND event_type='telemedicine.session.expired.v1'")
[ "${EXPV1:-0}" -ge 1 ] && ok "emitted telemedicine.session.expired.v1" || bad "no expired.v1 event ($EXPV1)"
LEXP=$(PSQL pct "SELECT count(*) FROM $LTBL WHERE referral_id='$REF' AND action='expire' AND allowed=true")
[ "${LEXP:-0}" -ge 1 ] && ok "ledger records the guarded 'expire' edge (allowed=true)" || bad "no expire ledger row ($LEXP)"
# OD-10 survival guarantee: the chat transcript persisted in J-TH-5 is on the durable case row,
# so it outlives the session lifecycle end (EXPIRED here stands in for room close) — zero content lost.
SURV=$(PSQL pct "SELECT jsonb_array_length(messages) FROM $RTBL WHERE referral_id='$REF'")
[ "${SURV:-0}" -ge 1 ] && ok "chat transcript survives session end (still $SURV on the case after EXPIRED)" || bad "transcript lost on session end ($SURV)"

echo "RESULT: PASS=$PASS FAIL=$FAIL SKIP=$SKIP" | tee -a "$EV/journal.txt"
teardown
[ "$FAIL" = 0 ]
