#!/usr/bin/env bash
# ============================================================================
# Operating-theatre QUEUE-DRAINAGE + REPLAY-SAFETY + RECONCILIATION (Wave 7 §17)
# runtime proof (J-QD-1..12) — the FINAL async-integrity gate.
#
# THE POINT: every theatre.* event the pipeline emits must be DRAINED by a live,
# idempotent consumer; a redelivered event (at-least-once Kafka) must NOT
# duplicate a clinical action or double-post billing/reporting; and the case
# end-state must RECONCILE across DB (inpatient projections) ↔ broker (Kafka
# lag) ↔ destinations (COSTA bill + reporting metric).
#
# Enumerated theatre.* async mechanism under proof:
#   inpatient.event_outbox  --(InpatientOutboxPublisher poll)-->  Kafka inpatient.events
#     ├── costa-costing-engine     : CostaEventConsumer.onTheatreCaseCompleted (surgical bundle;
#     │                              idempotent on episode_id via IdempotencyRepository)
#     └── reporting-service        : TheatreReportingConsumer (rpt_theatre_case_metric;
#                                    idempotent via UNIQUE(tenant_id, episode_id) upsert)
#
#   J-QD-1  DRAIN: a completed case emits theatre.case.completed; the outbox row
#           is published (published_at stamped) and both consumers process it.
#   J-QD-2  COSTA DESTINATION: exactly ONE surgical-case bundle billed for the case.
#   J-QD-3  REPORTING DESTINATION: exactly ONE rpt_theatre_case_metric (COMPLETED).
#   J-QD-4  REPLAY / no double-post BILLING: re-produce the SAME payload to
#           inpatient.events → COSTA bill-line count for the case is UNCHANGED.
#   J-QD-5  REPLAY / no duplicate PROJECTION: reporting metric stays exactly ONE
#           row for the case, still COMPLETED (upsert, not insert).
#   J-QD-6  BROKER DEPTH ZERO: consumer-group LAG == 0 for both groups (queue
#           fully drained, no unprocessed backlog).
#   J-QD-7  NO DEAD-LETTERS: no outbox row carries a publish_error; no failed
#           money-event quarantine row minted by the theatre path.
#   J-QD-8  RECONCILE status: inpatient episode COMPLETED == reporting metric COMPLETED.
#   J-QD-9  RECONCILE composition: reporting theatre_minutes == emitted minutes;
#           implant/blood counts in the projection == the inpatient record counts.
#   J-QD-10 RECONCILE billing: COSTA bundle line-items reconcile to the case
#           (THEATRE-TIME + ANAESTHESIA-GA + THEATRE-IMPLANT + THEATRE-BLOOD).
#   J-QD-11 IDEMPOTENCY LEDGER: exactly ONE costa idempotency ledger entry for the
#           case (THEATRE_CASE), proving the replay was absorbed, not re-executed.
#   J-QD-12 SECOND CASE ISOLATION: a distinct case drains independently (the
#           idempotency guard is per-episode, not a global stop).
#
# Requirements: docker, java 21, packaged jars for inpatient + reporting + costa.
#   mvn -f services/pom.xml -pl inpatient-service,reporting-service,costing-engine-service -am package -DskipTests
# Usage: EV=<dir> bash scripts/runtime-proof/theatre-queue-drainage-journeys.sh
#        KEEP_RIG=1 to leave infra + services running for inspection.
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-$REPO/reports/journeys/theatre-drainage-proof-$(date +%Y-%m-%d)}
RIGLOG=${RIGLOG:-/tmp/theatre-drainage-logs}
mkdir -p "$EV" "$RIGLOG"
# Preview tenant — matches the seeded AHFOZ theatre tariffs (costa V024) so the bundle prices resolve.
TEN=00000000-0000-4000-8000-000000000001
FAC=00000000-0000-4000-8000-0000000000fa
SURGEON=PROV-ZW-00020
CPID=CPID-ZW-DRAINAGE-E2E
PGPORT=15701; RPORT=16471; KPORT=19191
INP=http://localhost:28821
REP=http://localhost:28876
COSTA=http://localhost:28801
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
INPSQL(){ docker exec dr-rig-pg psql -q -U impilo -d inpatient -tAc "$1" | tr -d '[:space:]'; }
COSTASQL(){ docker exec dr-rig-pg psql -q -U impilo -d costa_db -tAc "$1" | tr -d '[:space:]'; }
REPSQL(){ docker exec dr-rig-pg psql -q -U impilo -d reporting -tAc "$1" | tr -d '[:space:]'; }
KRUN(){ docker exec dr-rig-kafka /opt/kafka/bin/"$@"; }
hdr(){ local a=${1:-$SURGEON} r=${2:-PROVIDER}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Facility-ID:$FAC -H X-Actor-ID:$a -H X-Actor-Type:$r -H X-Purpose-Of-Use:TREATMENT -H Idempotency-Key:idem-$(uuidgen)"; }
jval(){ python3 -c "import json,sys;d=json.load(sys.stdin);d=d.get('data',d) if isinstance(d,dict) else d;print(d.get('$1','') if isinstance(d,dict) else '')" 2>/dev/null; }

command -v docker >/dev/null || { echo "docker not available — rig cannot run"; exit 2; }

# ── Infra: postgres + redis + kafka (KRaft) ──────────────────────────────────
say "RIG: infra (postgres + redis + kafka)"
docker rm -f dr-rig-pg dr-rig-redis dr-rig-kafka >/dev/null 2>&1
docker run -d --name dr-rig-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name dr-rig-redis -p $RPORT:6379 redis:7-alpine >/dev/null
docker run -d --name dr-rig-kafka -p $KPORT:9092 \
  -e KAFKA_NODE_ID=1 -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:$KPORT \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  -e KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 \
  apache/kafka:3.7.1 >/dev/null

# Wait for postgres readiness (a fixed sleep races the boot — pin on pg_isready), then create DBs.
for i in $(seq 1 30); do docker exec dr-rig-pg pg_isready -U impilo >/dev/null 2>&1 && break; sleep 2; done
for db in inpatient reporting costa_db; do
  docker exec dr-rig-pg psql -U impilo -d postgres -c "CREATE DATABASE $db" >/dev/null 2>&1
done
sleep 6  # give kafka KRaft a moment to elect

svcjar(){ ls "$REPO/services/$1/target/$1-"*.jar 2>/dev/null | grep -v original | head -1; }
for s in inpatient-service reporting-service costing-engine-service; do
  [ -n "$(svcjar "$s")" ] || { echo "$s jar missing — run: mvn -f services/pom.xml -pl inpatient-service,reporting-service,costing-engine-service -am package -DskipTests"; exit 2; }
done

boot(){ # $1 service-dir  $2 db  $3 port  $4..extra env KEY=VAL
  local svc=$1 db=$2 port=$3; shift 3
  env POSTGRES_HOST=localhost POSTGRES_PORT=$PGPORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo POSTGRES_DB=$db \
    SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:$PGPORT/$db" \
    SPRING_DATASOURCE_USERNAME=impilo SPRING_DATASOURCE_PASSWORD=impilo \
    IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true SERVER_PORT=$port \
    SPRING_KAFKA_LISTENER_AUTO_STARTUP=true KAFKA_BOOTSTRAP=localhost:$KPORT \
    SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:$KPORT \
    SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=$RPORT \
    "$@" \
    nohup java -jar "$(svcjar "$svc")" > "$RIGLOG/$svc.log" 2>&1 & echo $! > "$RIGLOG/$svc.pid"
}

wait_health(){ local url=$1 name=$2 c=000
  for i in $(seq 1 90); do c=$(curl -s -o /dev/null -w '%{http_code}' "$url/actuator/health" 2>/dev/null); [ "$c" = 200 ] && break; sleep 3; done
  echo "   $name health: $c" | tee -a "$EV/journal.txt"
  [ "$c" = 200 ] || { bad "$name did not boot"; tail -40 "$RIGLOG/$name.log" 2>/dev/null; }
}

boot reporting-service reporting 28876
boot costing-engine-service costa_db 28801
boot inpatient-service inpatient 28821 INPATIENT_EMIT_MODE=LEGACY

for pair in "$REP reporting-service" "$COSTA costing-engine-service" "$INP inpatient-service"; do
  wait_health $pair
done

cleanup(){ if [ -z "${KEEP_RIG:-}" ]; then
  for p in "$RIGLOG"/*.pid; do kill "$(cat "$p")" >/dev/null 2>&1; done
  docker rm -f dr-rig-pg dr-rig-redis dr-rig-kafka >/dev/null 2>&1
fi }
trap cleanup EXIT

# ── Drive a complete theatre case with billable composition ──────────────────
say "J-QD: intake → seed implant+blood → complete → drain → replay → reconcile"

EPI=$(curl -s -X POST $INP/internal/v1/theatre/cases $(hdr) -d "{\"patientId\":\"$CPID\",\"procedureName\":\"Total hip replacement\",\"procedureCode\":\"THR\",\"orosOrderId\":\"RIG-ORDER-$(uuidgen)\",\"surgeonId\":\"$SURGEON\",\"anaesthetistId\":\"PROV-ZW-00040\",\"triagePriority\":\"ELECTIVE\"}" | jval id)
[ -n "$EPI" ] && ok "case intake ($EPI)" || bad "case intake failed"

# Seed one implant + one blood link so the emitted composition (and reconciliation) is non-trivial.
INPSQL "INSERT INTO inpatient.procedure_implant (implant_id, tenant_id, episode_id, seq, device_type, body_site, status, created_at, updated_at) VALUES (gen_random_uuid(), '$TEN', '$EPI', 1, 'HIP_PROSTHESIS', 'LEFT_HIP', 'IMPLANTED', now(), now())" >/dev/null
INPSQL "INSERT INTO inpatient.procedure_blood_link (link_id, tenant_id, episode_id, kind, seq, units_requested, blood_group, component_type, status, created_at, updated_at) VALUES (gen_random_uuid(), '$TEN', '$EPI', 'TRANSFUSION', 1, 2, 'O_POS', 'PRBC', 'ISSUED', now(), now())" >/dev/null
IMPL=$(INPSQL "SELECT count(*) FROM inpatient.procedure_implant WHERE episode_id='$EPI'")
BLD=$(INPSQL "SELECT count(*) FROM inpatient.procedure_blood_link WHERE episode_id='$EPI'")

# Complete the case → emits theatre.case.completed carrying the billable composition.
curl -s -X POST $INP/internal/v1/theatre/cases/$EPI/complete $(hdr) -d '{"theatreMinutes":135}' >/dev/null
CS=$(INPSQL "SELECT status FROM inpatient.procedure_episode WHERE episode_id='$EPI'")
[ "$CS" = "COMPLETED" ] && ok "case COMPLETED (theatre.case.completed emitted)" || bad "case not COMPLETED (got '$CS')"

# ── J-QD-1: outbox drained (published_at stamped) ────────────────────────────
PUB=0
for i in $(seq 1 20); do
  PUB=$(INPSQL "SELECT count(*) FROM inpatient.event_outbox WHERE aggregate_id='$EPI' AND event_type='theatre.case.completed' AND published_at IS NOT NULL")
  [ "$PUB" -ge 1 ] 2>/dev/null && break; sleep 3
done
[ "$PUB" -ge 1 ] 2>/dev/null && ok "J-QD-1 outbox drained → published to Kafka (published_at stamped)" || bad "J-QD-1 outbox row not published (got '$PUB')"

# ── J-QD-2 / J-QD-3: both destinations processed exactly once ────────────────
say "waiting for async drain → COSTA bundle + reporting projection"
BUNDLE=0
for i in $(seq 1 30); do
  BUNDLE=$(COSTASQL "SELECT count(*) FROM costa_bill_lines WHERE msika_code='THEATRE-TIME' AND source_ref='$EPI'")
  [ "$BUNDLE" -ge 1 ] 2>/dev/null && break; sleep 4
done
[ "$BUNDLE" = "1" ] && ok "J-QD-2 COSTA destination: exactly one THEATRE-TIME bundle line" || bad "J-QD-2 COSTA THEATRE-TIME line count='$BUNDLE' (expected 1)"

METRIC=0
for i in $(seq 1 30); do
  METRIC=$(REPSQL "SELECT count(*) FROM rpt_theatre_case_metric WHERE episode_id='$EPI' AND status='COMPLETED'")
  [ "$METRIC" -ge 1 ] 2>/dev/null && break; sleep 4
done
[ "$METRIC" = "1" ] && ok "J-QD-3 reporting destination: exactly one COMPLETED metric row" || bad "J-QD-3 reporting metric count='$METRIC' (expected 1)"

# Baselines for the replay assertions.
BASE_LINES=$(COSTASQL "SELECT count(*) FROM costa_bill_lines WHERE source_ref='$EPI'")
BASE_METRIC=$(REPSQL "SELECT count(*) FROM rpt_theatre_case_metric WHERE episode_id='$EPI'")
say "baseline COSTA lines for case=$BASE_LINES ; reporting rows for case=$BASE_METRIC"

# ── REPLAY: re-produce the SAME theatre.case.completed payload to inpatient.events ──
say "REPLAY: re-delivering the identical theatre.case.completed payload (at-least-once simulation)"
PAYLOAD=$(docker exec dr-rig-pg psql -q -U impilo -d inpatient -tAc "SELECT payload_json FROM inpatient.event_outbox WHERE aggregate_id='$EPI' AND event_type='theatre.case.completed' LIMIT 1" | tr -d '\n')
printf '%s\n' "$PAYLOAD" | KRUN kafka-console-producer.sh --bootstrap-server localhost:9092 --topic inpatient.events >/dev/null 2>&1
sleep 12  # allow both consumers to (idempotently) absorb the redelivery

REPLAY_LINES=$(COSTASQL "SELECT count(*) FROM costa_bill_lines WHERE source_ref='$EPI'")
[ "$REPLAY_LINES" = "$BASE_LINES" ] && ok "J-QD-4 REPLAY no double-post billing (COSTA lines still $BASE_LINES)" || bad "J-QD-4 replay DUPLICATED billing: $BASE_LINES → $REPLAY_LINES"

REPLAY_METRIC=$(REPSQL "SELECT count(*) FROM rpt_theatre_case_metric WHERE episode_id='$EPI'")
REPLAY_STATUS=$(REPSQL "SELECT status FROM rpt_theatre_case_metric WHERE episode_id='$EPI'")
{ [ "$REPLAY_METRIC" = "$BASE_METRIC" ] && [ "$REPLAY_STATUS" = "COMPLETED" ]; } \
  && ok "J-QD-5 REPLAY no duplicate projection (reporting rows still $BASE_METRIC, COMPLETED)" \
  || bad "J-QD-5 replay changed projection: rows $BASE_METRIC → $REPLAY_METRIC status=$REPLAY_STATUS"

# ── J-QD-6: broker depth zero (consumer-group lag) ───────────────────────────
sleep 4
lag_total(){ KRUN kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group "$1" 2>/dev/null \
  | awk 'NR>1 && $6 ~ /^[0-9]+$/ {s+=$6} END{print s+0}'; }
COSTA_LAG=$(lag_total costa-costing-engine)
REP_LAG=$(lag_total reporting-service)
{ [ "$COSTA_LAG" = "0" ] && [ "$REP_LAG" = "0" ]; } \
  && ok "J-QD-6 broker depth ZERO — both consumer groups fully drained (costa lag=$COSTA_LAG, reporting lag=$REP_LAG)" \
  || bad "J-QD-6 residual queue backlog (costa lag=$COSTA_LAG, reporting lag=$REP_LAG)"

# ── J-QD-7: no dead-letters ──────────────────────────────────────────────────
DLQ_OUTBOX=$(INPSQL "SELECT count(*) FROM inpatient.event_outbox WHERE publish_error IS NOT NULL")
FAILEDMONEY=$(COSTASQL "SELECT count(*) FROM costa_failed_money_events" 2>/dev/null); FAILEDMONEY=${FAILEDMONEY:-0}
{ [ "$DLQ_OUTBOX" = "0" ] && [ "$FAILEDMONEY" = "0" ]; } \
  && ok "J-QD-7 no dead-letters (outbox publish_error=0, costa failed-money quarantine=0)" \
  || bad "J-QD-7 dead-letters present (outbox errors=$DLQ_OUTBOX, failed-money=$FAILEDMONEY)"

# ── J-QD-8/9: reconciliation status + composition ────────────────────────────
EPI_STATUS=$(INPSQL "SELECT status FROM inpatient.procedure_episode WHERE episode_id='$EPI'")
MET_STATUS=$(REPSQL "SELECT status FROM rpt_theatre_case_metric WHERE episode_id='$EPI'")
[ "$EPI_STATUS" = "COMPLETED" ] && [ "$MET_STATUS" = "COMPLETED" ] \
  && ok "J-QD-8 status reconciles (inpatient=$EPI_STATUS == reporting=$MET_STATUS)" \
  || bad "J-QD-8 status mismatch (inpatient=$EPI_STATUS reporting=$MET_STATUS)"

MET_MIN=$(REPSQL "SELECT theatre_minutes FROM rpt_theatre_case_metric WHERE episode_id='$EPI'")
MET_IMPL=$(REPSQL "SELECT implant_count FROM rpt_theatre_case_metric WHERE episode_id='$EPI'")
MET_BLD=$(REPSQL "SELECT blood_units FROM rpt_theatre_case_metric WHERE episode_id='$EPI'")
{ [ "$MET_MIN" = "135" ] && [ "$MET_IMPL" = "$IMPL" ] && [ "$MET_BLD" = "$BLD" ]; } \
  && ok "J-QD-9 composition reconciles (minutes=$MET_MIN, implants proj=$MET_IMPL==rec=$IMPL, blood proj=$MET_BLD==rec=$BLD)" \
  || bad "J-QD-9 composition mismatch (min=$MET_MIN impl proj=$MET_IMPL rec=$IMPL blood proj=$MET_BLD rec=$BLD)"

# ── J-QD-10: COSTA bundle line-items reconcile to the case ───────────────────
LINE_TIME=$(COSTASQL "SELECT count(*) FROM costa_bill_lines WHERE msika_code='THEATRE-TIME' AND source_ref='$EPI'")
LINE_ANAES=$(COSTASQL "SELECT count(*) FROM costa_bill_lines WHERE msika_code='ANAESTHESIA-GA' AND source_ref='$EPI'")
LINE_IMPL=$(COSTASQL "SELECT count(*) FROM costa_bill_lines WHERE msika_code='THEATRE-IMPLANT' AND source_ref='$EPI'")
LINE_BLD=$(COSTASQL "SELECT count(*) FROM costa_bill_lines WHERE msika_code='THEATRE-BLOOD' AND source_ref='$EPI'")
{ [ "$LINE_TIME" = "1" ] && [ "$LINE_ANAES" = "1" ] && [ "$LINE_IMPL" = "1" ] && [ "$LINE_BLD" = "1" ]; } \
  && ok "J-QD-10 COSTA bundle reconciles to case (time+anaes+implant+blood each ×1)" \
  || bad "J-QD-10 bundle mismatch (time=$LINE_TIME anaes=$LINE_ANAES implant=$LINE_IMPL blood=$LINE_BLD)"

# ── J-QD-11: idempotency ledger — exactly one THEATRE_CASE entry ─────────────
LEDGER=$(COSTASQL "SELECT count(*) FROM costa_idempotency WHERE idempotency_key='THEATRE_CASE:$EPI'")
[ "$LEDGER" = "1" ] && ok "J-QD-11 idempotency ledger holds exactly one THEATRE_CASE entry (replay absorbed)" || bad "J-QD-11 idempotency ledger count='$LEDGER' (expected 1)"

# ── J-QD-12: second case drains independently ────────────────────────────────
EPI2=$(curl -s -X POST $INP/internal/v1/theatre/cases $(hdr) -d "{\"patientId\":\"$CPID-2\",\"procedureName\":\"Appendicectomy\",\"procedureCode\":\"APPENDIX\",\"orosOrderId\":\"RIG-ORDER-$(uuidgen)\",\"surgeonId\":\"$SURGEON\"}" | jval id)
curl -s -X POST $INP/internal/v1/theatre/cases/$EPI2/complete $(hdr) -d '{"theatreMinutes":45}' >/dev/null
M2=0
for i in $(seq 1 20); do
  M2=$(REPSQL "SELECT count(*) FROM rpt_theatre_case_metric WHERE episode_id='$EPI2' AND status='COMPLETED'")
  [ "$M2" -ge 1 ] 2>/dev/null && break; sleep 4
done
[ "$M2" = "1" ] && ok "J-QD-12 second case drains independently (per-episode idempotency, not a global stop)" || bad "J-QD-12 second case did not drain (got '$M2')"

# ── Verdict ──────────────────────────────────────────────────────────────────
say "RESULT: PASS=$PASS FAIL=$FAIL"
echo "PASS=$PASS FAIL=$FAIL" > "$EV/summary.txt"
[ "$FAIL" = 0 ]
