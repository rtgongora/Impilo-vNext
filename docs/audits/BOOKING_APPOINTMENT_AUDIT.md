# Booking vs Appointment — Codebase Audit

> **Generated:** 2026-06-07  
> **Authority:** Booking/Appointment platform distinction ADR-0051

## Summary

| Concept | Pre-change owner | Post-change owner |
|---------|------------------|-------------------|
| **Booking** (transaction container) | None (marketplace + TUSO resource bookings only) | `booking-service` |
| **Appointment** (scheduled event) | `tuso-service` (`tuso.appointment`) | `booking-service` |
| **Resource reservation** (theatre, room, equipment) | `tuso-service` (`tuso.booking`) | `tuso-service` (consumed by booking-service) |
| **Mvumo consent/agreement/authorisation** | `mvumo-service` + BFF teleconsult | `mvumo-service` gated via booking-service |
| **Encounter** | `pct-service` | `pct-service` (linked from appointment) |

## Term usages found

### appointment
- **Sovereign:** `services/tuso-service` — `V010__appointments.sql`, `AppointmentEntity`, `AppointmentService`, `AppointmentController` (`/v1/appointments`)
- **BFF:** `SchedulingController` (`/internal/v1/appointments`), `CitizenAppointmentController` (`/internal/v1/mobile/citizen/appointments`)
- **Web:** `/scheduling`, `/home/appointments`, `/queue/scheduled`
- **Mobile:** `AppointmentsSection.tsx` (citizen)
- **Gap:** `provider_id` unvalidated; no booking transaction; citizen resource picker not persisted

### booking
- **TUSO resource:** `tuso.booking` — resource-level reservation (theatre, room)
- **Marketplace:** `/marketplace/bookings` — enterprise commerce (separate bounded context)
- **scheduling-service:** in-memory slot holds only
- **Gap:** No clinical booking transaction container

### schedule / slot / calendar
- TUSO `resource_calendar`, `ResourceService.getAvailableSlots`
- scheduling-service MVP `/v1/slots`
- BFF `/internal/v1/appointments/availability`
- Staff roster: `/scheduling/roster` (shifts, not patient appointments)

### encounter / check-in / queue
- PCT: journeys, queue items, encounters
- BFF check-in: `SchedulingController.checkInAppointment` → PCT journey + enqueue
- No booking-to-encounter link today

### consent / Mvumo
- `mvumo-service`: full consent-request lifecycle
- Telemedicine: creates PENDING consent only; does not gate on GRANTED
- No booking-specific Mvumo integration

### payment / exemption / programme
- COSTA bills, MUSHEX payment intents/claims, coverage-service eligibility
- Not wired to appointment/booking flows

### telemedicine routing
- `TeleconsultController.validateRoutingTarget` — PRACTITIONER (VARAPI), WORKSPACE (TUSO), FACILITY_SERVICE
- Pattern reused for booking target picker

## Interchangeable usage to refactor

| Location | Issue | Remediation |
|----------|-------|-------------|
| `/scheduling` page | Creates "appointment" directly | Provider creates Booking; converts to Appointment when rules satisfied |
| `/home/appointments` | Citizen "books" into appointment row | Citizen creates Booking (REQUESTED); Appointment on confirm |
| `CLIENT_NAVIGATION` | Points citizens to `/scheduling` | Point to `/home/bookings/new` |
| `home/page.tsx` | "Today's bookings" = appointment count | Split booking vs appointment metrics |
| TUSO `AppointmentController` | SoR for appointments | Deprecated shim → booking-service |

## Preserved functionality

- All existing BFF appointment endpoint paths remain
- TUSO resource booking and facility registry unchanged
- Marketplace bookings unchanged
- PCT queue/encounter/check-in spine unchanged (linked via booking-service)
- Mvumo service unchanged (orchestrated by booking-service)
