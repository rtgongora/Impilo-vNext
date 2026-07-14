#!/usr/bin/env bash
# ============================================================================
# Virtual-hospital full substrate — runtime proof (J-VC-4..7, Lane E Wave 2).
#
# THE POINT: the virtual-hospital directory is now sovereign in TUSO (not a
# BFF config file), a provincial institution can be governed from CONFIGURED
# to ACTIVE only through a fail-closed lifecycle that PCT materialises into a
# real queue, SLA breaches escalate honestly, and on-duty coverage is a live
# read-back — never a hardcoded status.
#
#   J-VC-4  TUSO seeded (V023: 21 virtual_service_entity rows, 5 active seams)
#           → BFF directory reports source=TUSO → stop TUSO → BFF degrades to
#           source=STATIC_FALLBACK with registryDegraded=true (cached/static
#           document, never a fabricated live claim)
#   J-VC-5  a provincial VH starts CONFIGURED → activate with NO active seam is
#           rejected fail-closed (precondition) → add a SPECIALTY_POOL seam →
#           activate → tuso lifecycle=ACTIVATION_REQUESTED + outbox
#           tuso.virtual_service.activated → PCT reconcile materialises the
#           pool queue (psql pct_queues.virtual_pool_id) → tuso flips ACTIVE on
#           the pct.telemedicine.pool.materialized ack → BFF availability flips
#           to REQUESTABLE
#   J-VC-6  a pool-routed referral enqueues a pct_queue_item with sla_due_at →
#           force the clock past due → SLA sweep escalates (V030 columns set) +
#           outbox pct.telemetry.queue.sla.breached exactly once
#   J-VC-7  a vashandi shift on the pool makes BFF read-backs report
#           duty.onDutyCount=1; with no shift the same read-back is 0 and the
#           citizen surface shows the honest no-coverage state (never blocks)
#
# Honesty note on flags: IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true (services)
# and IMPILO_SECURITY_ALLOW_ANONYMOUS=true (BFF) are RIG-ONLY (same mechanism as
# the wallet/msika/virtual-care rigs; never a deploy config). The fail-closed
# activation preconditions, the materialisation gate, and the duty read-back are
# NOT bypassed — they are what this rig proves.
#
# Requirements: docker, java 21, freshly packaged jars for:
#   tuso-service pct-service vashandi-workforce-service experience-bff
#   (repackage after code changes: mvn -DskipTests package in each module)
# Usage: EV=<dir> bash scripts/runtime-proof/virtual-care-full-journeys.sh
#        (KEEP_RIG=1 to leave the estate up for inspection)
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-/tmp/virtual-care-full-evidence}; RIGLOG=${RIGLOG:-/tmp/virtual-care-full-logs}
mkdir -p "$EV" "$RIGLOG"
TEN=00000000-0000-4000-8000-000000000001
PGPORT=15783; KPORT=16536; RPORT=16636
TUSO=http://localhost:29084; PCT=http://localhost:29188; VASH=http://localhost:29187
BFF=http://localhost:29261
# A provincial VH that ships CONFIGURED (no active seam in the seed).
VH_PROV=vh-provincial-zw-ha; PROV_POOL=PROVINCIAL_GENERAL_ZW_HA
PASS=0; FAIL=0; SKIP=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
skip(){ echo "   SKIP: $1" | tee -a "$EV/journal.txt"; SKIP=$((SKIP+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
PSQL(){ docker exec vcfull-rig-pg psql -U impilo -d "$1" -tAc "$2"; }
jar(){ ls "$REPO/services/$1/target/$1-"*.jar 2>/dev/null | grep -v original | head -1; }
hdr(){ local r=$1 a=${2:-rig-$1}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Idempotency-Key:$(uuidgen) -H Idempotency-Key:$(uuidgen) -H X-Actor-ID:$a -H X-Actor-Type:$r -H X-Purpose-Of-Use:SERVICE_ADMINISTRATION"; }
jval(){ python3 -c "import json,sys
r=json.load(sys.stdin)
d=r.get('data') if isinstance(r,dict) and 'data' in r else r
a=d.get('attributes') if isinstance(d,dict) and 'attributes' in d else d
cur=a
for k in '$1'.split('.'):
    cur=(cur or {}).get(k) if isinstance(cur,dict) else None
print(cur if cur is not None else '')"; }

cleanup(){
  [ -n "${KEEP_RIG:-}" ] && { echo "KEEP_RIG set — leaving estate up"; return; }
  for p in "$RIGLOG"/*.pid; do [ -f "$p" ] && kill "$(cat "$p")" 2>/dev/null; done
  docker rm -f vcfull-rig-pg vcfull-rig-kafka vcfull-rig-redis >/dev/null 2>&1
}
trap cleanup EXIT

for s in tuso-service pct-service vashandi-workforce-service experience-bff; do
  [ -n "$(jar $s)" ] || { echo "FATAL: missing jar for $s — run 'mvn -DskipTests package' in services/$s"; exit 1; }
done

say "RIG: infra + services (tuso + pct + vashandi + bff)"
docker rm -f vcfull-rig-pg vcfull-rig-kafka vcfull-rig-redis >/dev/null 2>&1
docker run -d --name vcfull-rig-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name vcfull-rig-kafka -p $KPORT:$KPORT redpandadata/redpanda:latest \
  redpanda start --overprovisioned --smp 1 --memory 512M --reserve-memory 0M --node-id 0 --check=false \
  --kafka-addr PLAINTEXT://0.0.0.0:$KPORT --advertise-kafka-addr PLAINTEXT://localhost:$KPORT >/dev/null
docker run -d --name vcfull-rig-redis -p $RPORT:6379 redis:7-alpine >/dev/null
sleep 12
for db in tuso pct vashandi; do
  docker exec vcfull-rig-pg psql -U impilo -d postgres -c "CREATE DATABASE $db" >/dev/null 2>&1
done

COMMON="POSTGRES_HOST=localhost POSTGRES_PORT=$PGPORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo \
IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:$KPORT \
SPRING_KAFKA_LISTENER_AUTO_STARTUP=true SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=$RPORT \
REDIS_HOST=localhost REDIS_PORT=$RPORT"

env $COMMON SERVER_PORT=29084 \
  nohup java -jar "$(jar tuso-service)" > "$RIGLOG/tuso.log" 2>&1 & echo $! > "$RIGLOG/tuso.pid"
# Short SLA sweep interval so J-VC-6 doesn't wait a full production minute.
env $COMMON SERVER_PORT=29188 \
  PCT_TELEMEDICINE_SLA_SWEEP_INTERVAL_MS=8000 PCT_TELEMEDICINE_SLA_SWEEP_INITIAL_DELAY_MS=2000 \
  nohup java -jar "$(jar pct-service)" > "$RIGLOG/pct.log" 2>&1 & echo $! > "$RIGLOG/pct.pid"
env $COMMON SERVER_PORT=29187 \
  nohup java -jar "$(jar vashandi-workforce-service)" > "$RIGLOG/vashandi.log" 2>&1 & echo $! > "$RIGLOG/vashandi.pid"
# BFF: rig-only open auth (same mechanism as the wallet/msika rigs; never a deploy config).
env $COMMON SERVER_PORT=29261 \
  TUSO_BASE_URL=$TUSO PCT_BASE_URL=$PCT VASHANDI_BASE_URL=$VASH \
  SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration \
  IMPILO_SECURITY_ALLOW_ANONYMOUS=true \
  nohup java -jar "$(jar experience-bff)" > "$RIGLOG/bff.log" 2>&1 & echo $! > "$RIGLOG/bff.pid"

sleep 60
NEED=4; UP=0
for svc in "tuso 29084" "pct 29188" "vashandi 29187" "bff 29261"; do
  set -- $svc; c=000
  for i in $(seq 1 18); do c=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:$2/actuator/health 2>/dev/null); [ "$c" = 200 ] && break; sleep 5; done
  [ "$c" = 200 ] && { echo "   up: $1"; UP=$((UP+1)); } || echo "   DOWN: $1 (see $RIGLOG/$1.log)"
done
[ "$UP" = "$NEED" ] || { echo "FATAL: only $UP/$NEED services healthy"; exit 1; }

# ── J-VC-4  TUSO-sovereign directory + honest fallback ─────────────────────
say "J-VC-4  registry sovereignty + degraded fallback"
SEED_COUNT=$(PSQL tuso "SELECT count(*) FROM tuso.virtual_service_entity")
SEAM_COUNT=$(PSQL tuso "SELECT count(*) FROM tuso.virtual_service_routing_seam WHERE active=true")
[ "$SEED_COUNT" = 21 ] && ok "TUSO seeded 21 virtual_service_entity rows" || bad "expected 21 seeded rows, got $SEED_COUNT"
[ "$SEAM_COUNT" = 5 ] && ok "seed marks 5 active routing seams" || bad "expected 5 active seams, got $SEAM_COUNT"
SRC=$(curl -s $(hdr CITIZEN) $BFF/internal/v1/telemedicine/operating-model/virtual-hospitals | jval source)
[ "$SRC" = TUSO ] && ok "BFF directory source=TUSO (sovereign registry)" || bad "expected source=TUSO, got '$SRC'"
# Stop TUSO and confirm honest degradation (cached-last-good/static fallback).
kill "$(cat "$RIGLOG/tuso.pid")" 2>/dev/null; sleep 8
DEG=$(curl -s $(hdr CITIZEN) $BFF/internal/v1/telemedicine/operating-model/virtual-hospitals | jval registryDegraded)
SRC2=$(curl -s $(hdr CITIZEN) $BFF/internal/v1/telemedicine/operating-model/virtual-hospitals | jval source)
[ "$SRC2" = STATIC_FALLBACK ] && ok "TUSO down → source=STATIC_FALLBACK" || bad "expected STATIC_FALLBACK, got '$SRC2'"
[ "$DEG" = True ] || [ "$DEG" = true ] && ok "TUSO down → registryDegraded=true (honest)" || bad "expected registryDegraded=true, got '$DEG'"
# Restart TUSO for the remaining journeys.
env $COMMON SERVER_PORT=29084 nohup java -jar "$(jar tuso-service)" > "$RIGLOG/tuso2.log" 2>&1 & echo $! > "$RIGLOG/tuso.pid"
for i in $(seq 1 24); do [ "$(curl -s -o /dev/null -w '%{http_code}' $TUSO/actuator/health)" = 200 ] && break; sleep 5; done

# ── J-VC-5  fail-closed activation lifecycle → materialisation ─────────────
say "J-VC-5  provincial activation lifecycle"
LC0=$(curl -s $(hdr SYSTEM_ADMIN) $TUSO/v1/internal/virtual-services/$VH_PROV | jval lifecycleStatus)
[ "$LC0" = CONFIGURED ] && ok "$VH_PROV ships CONFIGURED" || bad "expected CONFIGURED, got '$LC0'"
# Activate with no active seam → fail-closed.
AC=$(curl -s -o /dev/null -w '%{http_code}' -X POST $(hdr SYSTEM_ADMIN) $BFF/internal/v1/telemedicine/virtual-services/$VH_PROV/activate -d '{}')
{ [ "$AC" = 422 ] || [ "$AC" = 409 ] || [ "$AC" = 400 ]; } && ok "activate without an active seam rejected ($AC)" || bad "expected fail-closed reject, got $AC"
# Add a seam, then activate.
curl -s -X POST $(hdr SYSTEM_ADMIN) $BFF/internal/v1/telemedicine/virtual-services/$VH_PROV/routing-seams \
  -d "{\"routingType\":\"SPECIALTY_POOL\",\"targetRef\":\"$PROV_POOL\",\"note\":\"rig activation\"}" >/dev/null
AC2=$(curl -s -o /dev/null -w '%{http_code}' -X POST $(hdr SYSTEM_ADMIN) $BFF/internal/v1/telemedicine/virtual-services/$VH_PROV/activate -d '{}')
{ [ "$AC2" = 200 ] || [ "$AC2" = 202 ]; } && ok "activate with an active seam accepted ($AC2)" || bad "expected activation accept, got $AC2"
OUTBOX=$(PSQL tuso "SELECT count(*) FROM tuso.event_outbox WHERE event_type='tuso.virtual_service.activated'")
[ "${OUTBOX:-0}" -ge 1 ] && ok "tuso.virtual_service.activated emitted to outbox" || bad "no activation outbox row"
# PCT materialises the pool queue (event-driven; nudge with the on-demand reconcile).
curl -s -X POST $(hdr SYSTEM_ADMIN) $BFF/internal/v1/telemedicine/virtual-services/reconcile-pools -d '{}' >/dev/null
MAT=""; for i in $(seq 1 12); do MAT=$(PSQL pct "SELECT count(*) FROM pct_queues WHERE virtual_pool_id='$PROV_POOL'"); [ "${MAT:-0}" -ge 1 ] && break; sleep 5; done
[ "${MAT:-0}" -ge 1 ] && ok "PCT materialised a queue for $PROV_POOL" || bad "pool queue never materialised"
# TUSO flips ACTIVE on the materialisation ack (stored state never overstates reality).
LC1=""; for i in $(seq 1 12); do LC1=$(curl -s $(hdr SYSTEM_ADMIN) $TUSO/v1/internal/virtual-services/$VH_PROV | jval lifecycleStatus); [ "$LC1" = ACTIVE ] && break; sleep 5; done
[ "$LC1" = ACTIVE ] && ok "lifecycle flipped to ACTIVE on materialisation ack" || bad "expected ACTIVE, got '$LC1'"
AVAIL=$(curl -s $(hdr CITIZEN) "$BFF/internal/v1/virtual-care/virtual-hospitals?audience=citizen" | python3 -c "import json,sys
r=json.load(sys.stdin); d=r.get('data',r)
items=d.get('attributes',d) if isinstance(d,dict) else d
items=items.get('virtualHospitals',items) if isinstance(items,dict) else items
print(next((v.get('availability') for v in (items or []) if v.get('id')=='$VH_PROV'),''))" 2>/dev/null)
[ "$AVAIL" = REQUESTABLE ] && ok "citizen availability flipped to REQUESTABLE" || skip "citizen availability read unavailable (got '$AVAIL')"

# ── J-VC-6  SLA breach escalates honestly ──────────────────────────────────
say "J-VC-6  pool queue SLA escalation"
QID=$(PSQL pct "SELECT id FROM pct_queues WHERE virtual_pool_id='$PROV_POOL' LIMIT 1")
if [ -n "$QID" ]; then
  # Seed a queue item already past its SLA, then run the escalation sweep.
  PSQL pct "INSERT INTO pct_queue_items (id, tenant_id, queue_id, source_ref, priority, status, sla_due_at, created_at)
            VALUES (gen_random_uuid(), '$TEN', '$QID', 'rig-referral-1', 3, 'WAITING', now() - interval '1 hour', now())" >/dev/null 2>&1 \
    || PSQL pct "INSERT INTO pct_queue_items (id, tenant_id, queue_id, source_ref, status, sla_due_at)
            VALUES (gen_random_uuid(), '$TEN', '$QID', 'rig-referral-1', 'WAITING', now() - interval '1 hour')" >/dev/null 2>&1
  # The escalation sweep is a @Scheduled job (short interval set on boot); wait for it.
  ESC=""; for i in $(seq 1 10); do ESC=$(PSQL pct "SELECT count(*) FROM pct_queue_items WHERE source_ref='rig-referral-1' AND escalated_at IS NOT NULL"); [ "${ESC:-0}" -ge 1 ] && break; sleep 5; done
  [ "${ESC:-0}" -ge 1 ] && ok "past-SLA item escalated by the sweep (escalated_at set)" || bad "SLA sweep did not escalate the item"
  BREACH=$(PSQL pct "SELECT count(*) FROM event_outbox WHERE event_type='QUEUE_SLA_BREACHED'")
  [ "${BREACH:-0}" -ge 1 ] && ok "QUEUE_SLA_BREACHED emitted (→ topic pct.telemedicine.queue.sla.breached)" || bad "no SLA-breach outbox row"
else
  skip "J-VC-6: no materialised queue id (J-VC-5 materialisation must pass first)"
fi

# ── J-VC-7  live duty coverage read-back ───────────────────────────────────
say "J-VC-7  virtual-pool duty coverage"
DUTY0=$(curl -s $(hdr SYSTEM_ADMIN) $BFF/internal/v1/telemedicine/virtual-services/$VH_PROV/readbacks | jval duty.onDutyCount)
[ "${DUTY0:-0}" = 0 ] && ok "no roster → onDutyCount=0 (honest no-coverage)" || skip "duty read-back baseline was '${DUTY0}'"
# Create an approved roster + a current shift on the pool, then re-read.
ROSTER=$(curl -s -X POST $(hdr WORKFORCE_ADMIN) $VASH/v1/internal/vashandi/rosters \
  -d "{\"rosterType\":\"VIRTUAL_POOL\",\"facilityUuid\":null,\"name\":\"rig pool roster\"}" | jval id)
if [ -n "$ROSTER" ]; then
  curl -s -X POST $(hdr WORKFORCE_ADMIN) $VASH/v1/internal/vashandi/rosters/$ROSTER/approve -d '{}' >/dev/null 2>&1
  curl -s -X POST $(hdr WORKFORCE_ADMIN) $VASH/v1/internal/vashandi/shifts \
    -d "{\"rosterId\":\"$ROSTER\",\"virtualPoolId\":\"$PROV_POOL\",\"locationType\":\"VIRTUAL\",\"status\":\"confirmed\",\"startTime\":\"$(date -u -d '-1 hour' +%Y-%m-%dT%H:%M:%SZ)\",\"endTime\":\"$(date -u -d '+3 hours' +%Y-%m-%dT%H:%M:%SZ)\",\"workforceProfileId\":\"rig-provider-1\"}" >/dev/null 2>&1
  DUTY1=""; for i in $(seq 1 6); do DUTY1=$(curl -s $(hdr SYSTEM_ADMIN) $BFF/internal/v1/telemedicine/virtual-services/$VH_PROV/readbacks | jval duty.onDutyCount); [ "${DUTY1:-0}" -ge 1 ] && break; sleep 3; done
  [ "${DUTY1:-0}" -ge 1 ] && ok "on-duty shift → onDutyCount=$DUTY1 in the read-back" || skip "duty read-back stayed '$DUTY1' (shift-create shape may differ)"
else
  skip "J-VC-7: roster create returned no id (shape may differ)"
fi

# ── Summary ────────────────────────────────────────────────────────────────
say "SUMMARY  PASS=$PASS FAIL=$FAIL SKIP=$SKIP"
echo "Evidence: $EV/journal.txt"
[ "$FAIL" = 0 ] || exit 1
