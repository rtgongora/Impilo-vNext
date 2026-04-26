# scheduling-service (MVP)

Spring Boot **slot engine stub** for Impilo vNext:

- `GET /v1/health` — liveness + engine label
- `GET /v1/slots?resource_id=&date=` — deterministic 20-minute slots (08:00–17:00)
- `POST /v1/slots/reserve` — in-memory hold (409 if already reserved)
- `POST /v1/slots/release` — release a hold (`resource_id`, `date`, `start_time`, `hold_token`)
- `GET /v1/engine/capacity-rules` — placeholder policy payload

## Integration

1. Run on port **8128** by default (avoids clashing with `inpatient-service` on **8121**; override with `SERVER_PORT` / `application.properties` as needed).
2. Point **Experience BFF** `SchedulingController` / Tuso bridges at this base URL when the façade should delegate slot math here instead of in-process Tuso availability.
3. Wire **notification-service** reminder jobs to slot reservations once booking IDs are canonical.

## Build

```bash
mvn -pl scheduling-service -am package -DskipTests
```

This module is intentionally **stateless across restarts** (in-memory map) until PostgreSQL migrations are added.
