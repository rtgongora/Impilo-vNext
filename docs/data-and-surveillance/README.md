# Data & Surveillance Platform

This directory documents the public health data and surveillance capabilities
within the Impilo vNext platform.

## Services Overview

| Service | Port | Schema | Description |
|---------|------|--------|-------------|
| [surveillance-service](../../services/surveillance-service/) | 8180 | `surv` | Event ingestion, signal detection, cases registry |
| [campaigns-service](../../services/campaigns-service/) | 8190 | `camp` | Campaign management, enrollments, message dispatch |

## Architecture

```
                    ┌──────────────────────┐
                    │   Clinical Events    │
                    │  (PCT, OROS, BUTANO)  │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │ surveillance-service  │
                    │  POST /v1/ingest     │
                    │  Signal evaluation   │
                    │  Case auto-opening   │
                    └──────────┬───────────┘
                               │
                    Kafka: impilo.surv.*
                               │
                               ▼
                    ┌──────────────────────┐
                    │  campaigns-service   │
                    │  Campaign registry   │
                    │  Enrollments (stub)  │
                    │  Dispatch (stub)     │
                    └──────────┬───────────┘
                               │
                    Kafka: impilo.campaigns.*
                               │
                               ▼
                    ┌──────────────────────┐
                    │ notification-service │
                    │  (SMS, Email, Push)  │
                    └──────────────────────┘
```

## Event Flow

### Surveillance

1. External systems (or Kafka consumers) POST clinical events to `/internal/v1/ingest`
2. The ingestion engine matches events against active signal definitions
3. Signal hits are recorded and `impilo.surv.signal.hit.v1` events are emitted
4. When a signal's threshold is met (threshold = 1 for immediate), a case is auto-opened
5. Case-opened events (`impilo.surv.case.opened.v1`) can trigger downstream workflows

### Campaigns

1. Public health officers create campaigns with target groups and message templates
2. Participants are enrolled into campaigns (stub — will integrate with VITO patient registry)
3. Dispatch creates delivery records for each enrollment (stub — will integrate with notification-service)
4. All lifecycle events are emitted to Kafka for downstream consumption

## Kafka Topics

| Topic | Producer | Description |
|-------|----------|-------------|
| `impilo.surv.signal.created.v1` | surveillance-service | New signal definition created |
| `impilo.surv.signal.hit.v1` | surveillance-service | Signal threshold matched |
| `impilo.surv.case.opened.v1` | surveillance-service | Public health case opened |
| `impilo.campaigns.created.v1` | campaigns-service | New campaign created |
| `impilo.campaigns.enrolled.v1` | campaigns-service | Participant enrolled |
| `impilo.campaigns.dispatched.v1` | campaigns-service | Campaign dispatched |

## Integration Points

- **PCT** — patient journey events can be ingested for disease surveillance
- **OROS** — lab result events can trigger signal evaluation
- **BUTANO** — FHIR resources can be referenced in signal conditions
- **VITO** — patient identity for campaign enrollment (future)
- **notification-service** — campaign message delivery (future)
