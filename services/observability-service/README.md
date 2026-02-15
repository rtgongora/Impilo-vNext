# Observability Service

**Port:** 8210
**Schema:** `obs`
**Version:** v1.1-native (stub)

## Purpose

The Observability Service is a lightweight registry for dashboard definitions and
alert rules. It provides a central catalog of monitoring configurations that can
be provisioned per tenant.

## Domain Model

### Dashboards
Dashboard definition records with type (GRAFANA, CUSTOM, EMBEDDED), JSON config,
and lifecycle status.

### Alert Rules
Metric-based alert rule definitions with condition (GREATER_THAN, LESS_THAN, etc.),
threshold, and severity (INFO, WARNING, ERROR, CRITICAL).

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/internal/v1/dashboards` | Register a dashboard definition |
| GET | `/internal/v1/dashboards` | List dashboards for tenant |
| POST | `/internal/v1/alert-rules` | Create an alert rule |
| GET | `/internal/v1/alert-rules` | List alert rules for tenant |

## Kafka Events (Outbox)

| Event Type | Topic |
|------------|-------|
| `DASHBOARD_CREATED` | `impilo.obs.dashboard.created.v1` |
| `ALERT_RULE_CREATED` | `impilo.obs.alert-rule.created.v1` |

## Database Tables

- `obs.dashboards` — dashboard definitions
- `obs.alert_rules` — alert rule definitions
- `obs.event_outbox` — transactional outbox
- `obs.idempotency_keys` — request deduplication
