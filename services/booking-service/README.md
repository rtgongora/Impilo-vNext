# booking-service

Sovereign owner of **Booking** (transaction container) and **Appointment** (scheduled event) on the Impilo experience plane.

- **Port:** 8265
- **Schema:** `booking`
- **Package:** `zw.gov.mohcc.impilo.booking`
- **ADR:** [ADR-0051](../../docs/adr/ADR-0051-booking-appointment-distinction.md)

## Responsibilities

| Aggregate | Role |
|-----------|------|
| **Booking** | Transaction lifecycle — triage, approval, Mvumo gating, payment, resource reservation, sovereign links |
| **Appointment** | Scheduled operational event — created from confirmed bookings or direct provider/citizen scheduling |

TUSO retains facility/resource availability; booking-service orchestrates conversion and PCT check-in links.

## API

| Path | Description |
|------|-------------|
| `GET/POST /v1/bookings` | List/create bookings |
| `POST /v1/bookings/{id}/approve` | Approve and convert to appointment |
| `POST /v1/bookings/{id}/confirm` | Confirm (Mvumo-gated) |
| `GET/POST /v1/appointments` | List/create appointments (TUSO-compatible shapes) |
| `GET/POST /v1/appointments/citizen/{cpid}` | Citizen appointment flows |

All `/v1/*` routes require trust headers via `TrustContextFilter`.

## Integrations

Configured in `application.yml` under `booking.integrations.*`:

- **VARAPI** — provider resolution (`providerPublicId`), eligibility
- **TUSO** — slots, resource reservations, facility metadata
- **MVUMO** — consent/agreement/authorisation gating
- **PCT** — journey, queue, encounter (check-in)
- **COSTA / MUSHEX / coverage** — payment calculation stubs
- **OROS / Nhume / learning / community** — type-specific booking links

## Local development

```bash
cd services/booking-service
../mvnw test
../mvnw spring-boot:run
```

Requires PostgreSQL database `booking` with Flyway migrations applied automatically on startup.

## Events

Transactional outbox table `booking.event_outbox` — `BookingOutboxPublisher` polls unpublished rows (Kafka bridge deferred).
