# Surveillance Service

v1.1-native public health surveillance service that ingests clinical and public
health events, evaluates threshold-based signal definitions, records signal hits,
and maintains a cases registry .

## Port

| Service | Port |
|---------|------|
| surveillance-service | 8180 |

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/internal/v1/ingest` | Ingest a clinical/public health event |
| POST | `/internal/v1/signals` | Create a signal definition |
| GET | `/internal/v1/signals` | List all signals for the tenant |
| GET | `/internal/v1/cases` | List cases (optional `?status=` filter) |

## Kafka Events (via outbox)

| Event Type | Topic |
|------------|-------|
| SIGNAL_CREATED | `impilo.surv.signal.created.v1` |
| SIGNAL_HIT | `impilo.surv.signal.hit.v1` |
| CASE_OPENED | `impilo.surv.case.opened.v1` |

## Kafka Inbound Consumers

The surveillance service also consumes upstream clinical and alert rails:

| Topic | Purpose |
|-------|---------|
| `clinical.pct.encounter.completed` | Evaluate encounter-completion signals and trigger case logic where configured |
| `clinical.pct.death.recorded` | Evaluate mortality-related surveillance triggers |
| `analytics.surveillance.alert` | Ingest threshold alerts from analytics flows |

## Database Schema (`surv`)

- `signals` — threshold-based trigger definitions (event type, condition field, threshold, window)
- `signal_hits` — recorded trigger occurrences when ingested events match signals
- `cases` — public health case registry (auto-opened when threshold = 1)
- `event_outbox` — transactional outbox for Kafka
- `idempotency_keys` — request deduplication

## Signal Evaluation Flow

```
Ingest Event
    │
    ▼
Match active signals by event_type
    │
    ├──▶ Record signal_hit + emit SIGNAL_HIT event
    │
    └──▶ If threshold ≤ 1 → auto-open case + emit CASE_OPENED event
```

## Running Locally

```bash
cd services
mvn -pl surveillance-service spring-boot:run
```

## Running Tests

```bash
cd services
mvn -pl surveillance-service test
```
