# Reporting Service

Report definition registry and run engine for the Impilo platform.

## Overview

The Reporting service provides on-demand and scheduled report generation with JSON/CSV export formats.
It reads aggregate data from NDR (National Data Repository) or local stub tables and produces
formatted output for analytics and operational reporting.

## Port

| Environment | Port |
|-------------|------|
| Local dev   | 8160 |

## API Endpoints

All endpoints are under `/internal/v1/reports`.

| Method | Path                          | Description                    |
|--------|-------------------------------|--------------------------------|
| POST   | `/internal/v1/reports`        | Create a report definition     |
| POST   | `/internal/v1/reports/{key}/run`       | Run a report (stub execution)  |
| GET    | `/internal/v1/reports/{key}/runs`      | List runs for a report         |
| POST   | `/internal/v1/reports/{key}/schedules` | Create a schedule entry (stub) |

## v1.1 Compliance

This service is **v1.1-native**. The tech-companion library auto-configures:

- **Header enforcement**: `X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID` required on all `/internal/v1/**` endpoints
- **Idempotency**: `Idempotency-Key` header required on POST/PUT/PATCH commands
- **Timeout enforcement**: `X-Client-Timeout-MS` header respected

## Database

- **Database name**: `reporting`
- **Tables**: `rpt_report_definitions`, `rpt_report_runs`, `rpt_report_schedules`, `rpt_event_outbox`, `idempotency_keys`
- **Migration**: Flyway `V001__init.sql`

## Kafka Events (via outbox)

| Event Type               | Topic                                  |
|--------------------------|----------------------------------------|
| `REPORT_CREATED`         | `impilo.reporting.report.created.v1`   |
| `REPORT_RAN`             | `impilo.reporting.report.ran.v1`       |
| `SCHEDULE_CREATED`       | `impilo.reporting.schedule.created.v1` |

## Running Locally

```bash
# From services/ directory
mvn -pl reporting-service spring-boot:run
```

## Testing

```bash
mvn -pl reporting-service test
```

Tests include:
- `ReportingGoldenContractIT` — v1.1 compliance (header enforcement, idempotency, error envelope)
- `ReportDefinitionServiceTest` — definition creation, duplicate key rejection, format parsing
- `ReportRunServiceTest` — run execution, format override, stub output, outbox events
- `ScheduleServiceTest` — schedule creation, parameters, format handling
- `OutboxPublisherTest` — topic routing, batch publishing, error handling
- `ReportControllerTest` — controller unit tests (HTTP status codes, error handling)
- `ReportControllerIT` — full integration tests (MockMvc + H2)
