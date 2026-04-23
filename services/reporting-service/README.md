# Reporting Service

Report definition registry and run engine for the Impilo platform.

## Overview

The Reporting service provides on-demand and scheduled report generation with JSON/CSV export formats.
It executes parameterized SQL queries against the reporting database with tenant isolation,
DML/DDL safety guards, and automatic row limits.

## Port

| Environment | Port |
|-------------|------|
| Local dev   | 8160 |

## API Endpoints

All endpoints are under `/internal/v1/reports`.

| Method | Path                          | Description                    |
|--------|-------------------------------|--------------------------------|
| POST   | `/internal/v1/reports`        | Create a report definition     |
| POST   | `/internal/v1/reports/{key}/run`       | Run a report                   |
| GET    | `/internal/v1/reports/{key}/runs`      | List runs for a report         |
| POST   | `/internal/v1/reports/{key}/schedules` | Create a scheduled report      |

## v1.1 Compliance

This service is **v1.1-native**. The tech-companion library auto-configures:

- **Header enforcement**: `X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID` required on all `/internal/v1/**` endpoints
- **Idempotency**: `Idempotency-Key` header required on POST/PUT/PATCH commands
- **Timeout enforcement**: `X-Client-Timeout-MS` header respected

## Report Execution

The report engine:
1. Validates the query template (rejects DML/DDL)
2. Binds runtime parameters via `NamedParameterJdbcTemplate` (prevents SQL injection)
3. Injects `tenant_id` as a mandatory parameter for data isolation
4. Applies a row limit (10,000 max) to prevent resource exhaustion
5. Formats output as JSON or CSV

## Scheduled Reports

Schedules use Spring `@Scheduled` polling (60-second interval) with cron expressions.
The scheduler:
1. Queries active schedules whose `next_run_at` has passed
2. Triggers report execution via `ReportRunService`
3. Computes the next run time from the cron expression
4. Updates `last_run_at` and `next_run_at`

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

**JDK note:** The repo targets **Java 21** (`impilo-parent`). Mockito inline mocks rely on Byte Buddy; on **JDK 24+** the parent POM sets **`-Dnet.bytebuddy.experimental=true`** for Surefire so unit tests (for example `ReportControllerTest`) can run. Prefer **JDK 21** for builds to match CI.

Tests include:
- `ReportingGoldenContractIT` — v1.1 compliance (header enforcement, idempotency, error envelope)
- `ReportDefinitionServiceTest` — definition creation, duplicate key rejection, format parsing
- `ReportRunServiceTest` — run execution, format override, output validation, outbox events
- `ScheduleServiceTest` — schedule creation, cron parsing, parameters, format handling
- `OutboxPublisherTest` — topic routing, batch publishing, error handling
- `ReportControllerTest` — controller unit tests (HTTP status codes, error handling)
- `ReportControllerIT` — full integration tests (MockMvc + H2)
