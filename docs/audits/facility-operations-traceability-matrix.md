# Facility operations — traceability matrix

Experience routes are under `ui/experience`. APIs are proxied via **Experience BFF** `NEXT_PUBLIC_BFF_URL` → `/internal/v1/*` unless noted as **gateway** (`/api/v1/*`). Service status abbreviations: **LIVE**, **SKELETON**, **NEW** (see `docs/plan/SERVICE_CATALOG.md`).

| Capability | Backend service | API endpoint (primary) | Frontend surface | Mobile surface | Events | Audit trail | Status | Remaining gap | Test coverage |
|------------|-----------------|------------------------|-------------------|----------------|--------|-------------|--------|---------------|---------------|
| Facility Control Tower | PCT queues + ubomi beds (BFF) | `GET /internal/v1/queue/entries`, `POST /internal/v1/queue/entries/stats`, `GET /internal/v1/beds*`, staffing roster | `/clinical/control-tower`, hub link from `/facility-operations` | `MobileProviderExtendedController` queue | PCT/outbox when deployed | BFF + PCT policy | **Partial** — real metrics when PCT/beds return data; thresholds derive from live snapshot | Tuso control-telemetry; no-show/appointment roll-up in tower | `clinical/control-tower/page.test.tsx` |
| Queue Management | pct-service (BFF proxy) | `/internal/v1/queue/**` (+ `GET .../audit-trail` BFF-local) | `/queue`, `/queue/*`, patient flow board | Mobile queue + triage | Queue mutations invalidate React Query | PCT + BFF deque (interim) | **Partial** — transfer→PCT, LWBS→LEFT, pause/resume/no-show | PCT-native immutable audit store | `queue/*.test.tsx`, flow board tests |
| District / national ops | PCT via BFF | `POST /internal/v1/operations/facility-queue-snapshots` + `GET /internal/v1/facilities` | `/facility-operations/district-view` | — | — | Same as queue | **Partial** — live PCT roll-up, max 40 facilities | Warehouse roll-ups; patient-free national KPI contracts | Manual |
| Appointments | scheduling-service (NEW) + BFF bridge | `/internal/v1/appointments`, `/internal/v1/appointments/resources`, `/internal/v1/appointments/availability` | `/scheduling` | Partial (citizen appointments) | Booking creates/updates | BFF | **Partial** — list/create wired; recurrence/reminder integration incomplete | notification-service reminder jobs | `scheduling/page.test.tsx` |
| Scheduling engine | scheduling-service (**MVP** in repo) | `GET /v1/slots`, `POST /v1/slots/reserve` (port 8121) + BFF `/internal/v1/appointments/*` | `/scheduling` (calendar), `/scheduling/on-call` | Limited | — | — | **Partial** — MVP service ships; BFF still bridges Tuso for bookings | Wire BFF → scheduling-service client | Roster/on-call tests |
| Workforce Management | staffing (BFF) + Varapi identity | `/internal/v1/staffing/**`, Varapi via gateway | `/shift`, `/shift/roster`, `/scheduling/roster` | Mobile provider schedule | Shift lifecycle | BFF | **Partial** | Varapi duty roster ↔ PCT assignment | `scheduling/roster/*.test.tsx` |
| Rosters | staffing + shifts | `/internal/v1/shifts`, staffing roster week | `/scheduling/roster`, `/shift` | Mobile | — | — | **Partial** | Service-point assignment matrix | Roster tests |
| Rooms / service points | tuso-service (**SKELETON**) | `GET /internal/v1/appointments/resources` (+ gateway when live) | `/facility-operations/resources` | Tuso snapshots | — | — | **Partial** — Tuso-backed read board (empty when id format mismatch) | Maintenance / occupancy flags | Manual |
| Bed Management | ubomi-service (**SKELETON**) + BFF beds | `/internal/v1/beds`, `/internal/v1/beds/wards` | Control tower + `/beds` if enabled | Mobile beds | — | — | **Partial** — reads when BFF wired | Transfer + ward board | Control tower indirect |
| Patient Flow Board | PCT + appointments | Queue entries + queue-type lanes + appointments tally | `/facility-operations/patient-flow` | — | — | — | **Partial** | Unified timeline lane API (single patient journey strip) | `facility-operations/patient-flow/page.test.tsx` |
| Tasks | Shell task manager + future BFF tasks | `/shell/task-manager`; BFF tasks TBD | Hub link | `MobileTaskController` | — | — | **Gap** — no unified facility task API in chart shell | PCT task integration | Shell manual |
| Handover | shift handover routes | `/shift/handover` | `/shift/handover` | Partial | — | — | **Partial** | Clinical safety checklist templates | — |
| Operational alerts | Derived from beds/queue + assistant | `buildOperationalAlerts` + `/internal/v1/assistant/notifications` | Control tower, assistant strip | Mobile notices | — | Logs | **Partial** | integration-hub alert ingestion | Control tower |
| Notifications | notification-service (**LIVE**) | `/internal/v1/notifications/**` (BFF proxy) | Comms hub, scheduling flows | Citizen + provider messaging | Outbox | notification-service | **Partial** | Appointment reminder templates | Various |
| Reports / daily summary | reports BFF + analytics (future) | `/internal/v1/reports/**` | `/reports`, home tiles | Provider reports | — | — | **Gap** — no single “daily operational summary” API | Aggregate SQL / analytics pipeline | `reports` smoke |

---

## Cross-cutting integration (required links)

| Link | Status |
|------|--------|
| Queue → patient chart (`/ehr/{id}`) | **Wired** in queue search / walk-in patterns |
| Appointment → encounter | **Partial** — scheduling UI documents conversion gap |
| Control tower → queues/scheduling | **Wired** via `/facility-operations` hub |
| Telemedicine ↔ booking | **Partial** — telemedicine hub + scheduling separate |
| Referral receiving queue | `/queue/incoming-referrals` exists |

---

## Mobile (`apps/mobile`)

Not exhaustively re-tested in this pass; BFF `MobileProviderExtendedController` and `mobile/provider/*` controllers mirror subset of queue, labs, triage. Document as **ongoing parity** work.
