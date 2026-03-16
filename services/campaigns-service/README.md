# Campaigns Service

v1.1-native public health campaign management service. Manages campaign
definitions with target groups and messages, handles participant enrollments,
dispatches campaign deliveries, and emits campaign lifecycle events.

## Port

| Service | Port |
|---------|------|
| campaigns-service | 8190 |

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/internal/v1/campaigns` | Create a campaign |
| GET | `/internal/v1/campaigns` | List campaigns for the tenant |
| POST | `/internal/v1/campaigns/{id}/enroll` | Enroll a participant |
| POST | `/internal/v1/campaigns/{id}/dispatch` | Dispatch campaign deliveries |

## Kafka Events (via outbox)

| Event Type | Topic |
|------------|-------|
| CAMPAIGN_CREATED | `impilo.campaigns.created.v1` |
| ENROLLMENT_CREATED | `impilo.campaigns.enrolled.v1` |
| CAMPAIGN_DISPATCHED | `impilo.campaigns.dispatched.v1` |

## Database Schema (`camp`)

- `campaigns` — campaign definitions (type, target group, message template, channel, dates)
- `enrollments` — participant enrollments
- `deliveries` — message delivery tracking (created per enrollment on dispatch)
- `event_outbox` — transactional outbox for Kafka
- `idempotency_keys` — request deduplication

## Campaign Lifecycle

```
DRAFT ──▶ (create)
    │
    ├── enroll participants
    │
    └── dispatch ──▶ creates delivery per enrollment
                      → outbox event triggers notification-service
```

## Running Locally

```bash
cd services
mvn -pl campaigns-service spring-boot:run
```

## Running Tests

```bash
cd services
mvn -pl campaigns-service test
```
