# Runbook: Kafka Recovery

> Scope: Kafka broker failure, topic data loss, and consumer group recovery
> Severity trigger: SEV-2 (care path continues via outbox buffering)
> Architecture context: Kafka is the event transport, not the source of truth. All domain data originates in PostgreSQL via the transactional outbox pattern.

---

## 1. When to Use This Runbook

- Kafka broker(s) unreachable
- KRaft controller quorum lost
- Topic data corrupted or deleted
- Consumer group offsets lost or invalid
- MirrorMaker 2 replication failure
- Kafka cluster requires full redeployment

---

## 2. Key Design Principle

**Kafka data loss is an RTO problem, not an RPO problem.** Because every service uses the transactional outbox pattern:

1. Domain writes commit to PostgreSQL (the source of truth)
2. Events are written to `event_outbox` table in the same transaction
3. The outbox publisher asynchronously publishes events to Kafka
4. If Kafka is unavailable, events accumulate in the outbox table and are published when Kafka returns

**Therefore:** Kafka failure does NOT cause data loss. Clinical care continues. The impact is delayed event propagation to downstream consumers (e.g., SHR sync, audit aggregation, notification delivery).

---

## 3. Failure Scenarios

### 3.1 Single Broker Failure (KRaft Mode)

**Impact:** Minimal if replication factor ≥ 3. KRaft automatically re-elects partition leaders.

**Detection:**
```bash
# Check broker health
kafka-metadata.sh --snapshot /var/kafka-data/__cluster_metadata-0/00000000000000000000.log \
  --cluster-id <CLUSTER_ID>

# Or via JMX/Prometheus
# Alert: KafkaBrokerDown fires when broker count < expected
```

**Recovery:**
```bash
# 1. Check if the broker is simply down
kubectl get pods -n impilo -l app=kafka

# 2. If pod is in CrashLoopBackOff, check logs
kubectl logs -n impilo kafka-0 --tail=100

# 3. Common fixes:
#    - Disk full: expand PVC or clean up log segments
kubectl exec -n impilo kafka-0 -- df -h /var/kafka-data
#    - OOM: increase memory limits
#    - Corrupt log segment: delete the segment and let Kafka rebuild from replicas

# 4. If broker rejoins, partitions re-balance automatically
# Monitor under-replicated partitions:
kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --under-replicated-partitions
```

### 3.2 KRaft Controller Quorum Loss

**Impact:** No new topics can be created, no partition reassignment. Existing producers/consumers continue working briefly.

**Detection:**
```bash
# KRaft controller status
kafka-metadata.sh --snapshot /var/kafka-data/__cluster_metadata-0/*.log \
  --cluster-id <CLUSTER_ID>
```

**Recovery:**
```bash
# 1. Restore controller quorum (need majority of controllers running)
# If 3-controller setup, need at least 2 up
kubectl get pods -n impilo -l app=kafka-controller

# 2. If a single controller is down, restart it
kubectl delete pod kafka-controller-2 -n impilo

# 3. If quorum is permanently lost (majority failed):
# Format the metadata with a new cluster ID — THIS IS A FULL CLUSTER RESET
kafka-storage.sh format -t <NEW_CLUSTER_UUID> \
  -c /etc/kafka/kraft-controller.properties

# After cluster reset, recreate topics and replay from outbox (see §4)
```

### 3.3 Topic Data Loss

**Impact:** Consumers may miss events. Data is NOT lost — it exists in service databases.

**Recovery via outbox replay:**
```bash
# 1. Identify the affected topic and time window
# Example: platform.audit.events lost data from 14:00 to 15:00 UTC

# 2. Find the producing service(s) for that topic
# platform.audit.events → tshepo-service (tshepo_audit database)

# 3. Mark relevant outbox events as unpublished
psql -h "${DB_HOST}" -U impilo -d tshepo_audit -c "
  UPDATE event_outbox SET published_at = NULL
  WHERE created_at >= '2026-03-15 14:00:00+00'
  AND created_at <= '2026-03-15 15:00:00+00'
  AND published_at IS NOT NULL;
"

# 4. Restart the service outbox publisher (or the service itself)
kubectl rollout restart deployment/tshepo-audit-service -n impilo

# 5. The publisher will re-send these events to Kafka
# Monitor outbox lag:
psql -h "${DB_HOST}" -U impilo -d tshepo_audit -c "
  SELECT COUNT(*) FROM event_outbox WHERE published_at IS NULL;
"
```

### 3.4 Consumer Group Offset Loss

**Impact:** Consumers may re-process events (safe if consumers are idempotent) or skip events.

```bash
# 1. List consumer groups
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list

# 2. Check group status
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group <GROUP_ID>

# 3. Reset offsets to a timestamp (safe reprocessing)
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group <GROUP_ID> \
  --topic <TOPIC> \
  --reset-offsets --to-datetime "2026-03-15T14:00:00.000" \
  --execute

# 4. Or reset to earliest (full reprocessing — safe if consumers are idempotent)
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group <GROUP_ID> \
  --topic <TOPIC> \
  --reset-offsets --to-earliest \
  --execute
```

---

## 4. Full Kafka Cluster Recovery

If the entire Kafka cluster is lost and must be redeployed:

### 4.1 Deploy Fresh Cluster

```bash
# 1. Deploy Kafka via Helm (KRaft mode, no ZooKeeper)
helm upgrade --install kafka bitnami/kafka \
  -n impilo \
  --set kraft.enabled=true \
  --set controller.replicaCount=3 \
  --set broker.replicaCount=3 \
  --set listeners.client.protocol=PLAINTEXT \
  --set listeners.interBroker.protocol=PLAINTEXT
```

### 4.2 Recreate Topics

```bash
# Platform audit events
kafka-topics.sh --bootstrap-server localhost:9092 --create \
  --topic platform.audit.events --partitions 6 --replication-factor 3 \
  --config retention.ms=2592000000  # 30 days

# Clinical events
for topic in pct.encounter.opened pct.encounter.closed \
  oros.order.placed oros.order.status_changed oros.result.available; do
  kafka-topics.sh --bootstrap-server localhost:9092 --create \
    --topic "${topic}" --partitions 6 --replication-factor 3 \
    --config retention.ms=1209600000  # 14 days
done

# Pharmacy & inventory events
for topic in pharmacy.dispense.completed pharmacy.fulfillment.status_changed \
  inventory.reservation.status_changed inventory.ledger.event_posted; do
  kafka-topics.sh --bootstrap-server localhost:9092 --create \
    --topic "${topic}" --partitions 3 --replication-factor 3 \
    --config retention.ms=1209600000  # 14 days
done

# Finance events
for topic in mushex.payment.status_changed costa.bill.finalized costa.invoice.issued; do
  kafka-topics.sh --bootstrap-server localhost:9092 --create \
    --topic "${topic}" --partitions 3 --replication-factor 3 \
    --config retention.ms=2592000000  # 30 days
done

# Control channels (compacted)
kafka-topics.sh --bootstrap-server localhost:9092 --create \
  --topic impilo.control.revocation.v1 --partitions 3 --replication-factor 3 \
  --config cleanup.policy=compact \
  --config retention.ms=604800000  # 7 days
```

### 4.3 Replay from Outbox Tables

```bash
# For each service, mark all events as unpublished to trigger replay
# Use a cutoff timestamp to avoid replaying ancient events
CUTOFF=$(date -u -d "7 days ago" +"%Y-%m-%d %H:%M:%S+00")

for db in tshepo vito varapi tuso zibo msika pct oros \
  pharmacy inventory mushex costing; do
  echo "Replaying outbox for: ${db}"
  psql -h "${DB_HOST}" -U impilo -d "${db}" -c "
    UPDATE event_outbox SET published_at = NULL
    WHERE created_at >= '${CUTOFF}'
    AND published_at IS NOT NULL;
  " 2>/dev/null || echo "  (no outbox table or database not found)"
done

# Restart all services to trigger outbox publisher
kubectl rollout restart deployment -n impilo -l ring=0
kubectl rollout restart deployment -n impilo -l ring=1
```

### 4.4 Verify Event Flow

```bash
# Monitor outbox drain across services
watch -n 5 '
for db in tshepo vito varapi tuso zibo msika pct oros; do
  count=$(psql -h "${DB_HOST}" -U impilo -d "${db}" -tAc \
    "SELECT COUNT(*) FROM event_outbox WHERE published_at IS NULL;" 2>/dev/null || echo "N/A")
  echo "${db}: ${count} unpublished"
done
'

# Verify consumer group lag
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --all-groups | grep -v "TOPIC\|^$"
```

---

## 5. Outbox Backlog During Kafka Outage

During a Kafka outage, events accumulate in each service's `event_outbox` table. Monitor the backlog:

```bash
# Check outbox backlog per service
for db in tshepo vito varapi tuso zibo msika pct oros; do
  result=$(psql -h "${DB_HOST}" -U impilo -d "${db}" -tAc "
    SELECT COUNT(*) AS total,
           COUNT(*) FILTER (WHERE published_at IS NULL) AS unpublished,
           MIN(created_at) FILTER (WHERE published_at IS NULL) AS oldest_unpublished
    FROM event_outbox;" 2>/dev/null || echo "N/A")
  echo "${db}: ${result}"
done
```

**Thresholds:**

| Metric | Warning | Critical |
|--------|:-------:|:--------:|
| TSHEPO outbox unpublished | > 50 | > 200 |
| Other Ring 0 outbox unpublished | > 100 | > 500 |
| Ring 1 outbox unpublished | > 200 | > 1000 |
| Oldest unpublished event age | > 5 min | > 30 min |

**When Kafka returns**, the outbox publisher in each service will automatically drain the backlog. No manual intervention required unless the outbox table has grown very large (> 100K events), in which case:

```sql
-- Batch replay to avoid overwhelming Kafka
-- Process in chunks of 1000
UPDATE event_outbox SET published_at = NULL
WHERE id IN (
  SELECT id FROM event_outbox
  WHERE published_at IS NULL
  ORDER BY created_at ASC
  LIMIT 1000
);
```

---

## 6. MirrorMaker 2 Recovery

If MirrorMaker 2 replication to the DR cluster is broken:

```bash
# 1. Check connector status
kubectl exec -n impilo mm2-connect-0 -- \
  curl -s localhost:8083/connectors/impilo-dr-mirror/status | jq .

# 2. If connector is FAILED, restart it
kubectl exec -n impilo mm2-connect-0 -- \
  curl -X POST localhost:8083/connectors/impilo-dr-mirror/restart

# 3. If the connector cannot reconnect, check network connectivity
kubectl exec -n impilo mm2-connect-0 -- \
  kafka-broker-api-versions.sh --bootstrap-server dr-kafka:9092

# 4. If DR cluster is unreachable, replication pauses automatically
# and resumes when connectivity is restored (offset tracking is persistent)
```

---

## 7. Escalation

| Elapsed Time | Action |
|:------------:|--------|
| T+0 | Kafka alert fires; on-call SRE begins triage |
| T+5 min | Check outbox backlog across Ring 0 services |
| T+15 min | If outbox backlog > critical threshold: notify platform lead |
| T+30 min | If Kafka still down: engage Kafka operations team |
| T+1 hr | If outbox > 10K events: consider manual batch replay plan |
| T+2 hr | If no progress: escalate to engineering manager, consider DR failover |

---

## 8. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-03-15 | Wave 20 | Initial Kafka recovery runbook |
