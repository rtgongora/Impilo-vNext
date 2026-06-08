# Booking / Appointment Migration Guide

> **ADR:** ADR-0051 | **Date:** 2026-06-07

## What changed

| Before | After |
|--------|-------|
| `tuso.appointment` SoR | `booking.appointment` SoR in `booking-service` |
| Citizen POST creates appointment row | Citizen POST creates **Booking** (`REQUESTED`); Appointment on confirm/convert |
| `provider_id` free string | VARAPI `providerPublicId` validated |
| No booking transaction | `booking.booking` state machine with Mvumo/payment/approval gates |

## Preserved (no removal)

- BFF paths: `/internal/v1/appointments/*`, `/internal/v1/mobile/citizen/appointments/*`
- TUSO `AppointmentController` (deprecated fallback, `forward-enabled=false` default)
- TUSO resource bookings (`tuso.booking`) for theatre/room/equipment
- Marketplace `/marketplace/bookings` (enterprise plane)

## Data backfill

```sql
-- Run once when booking-service DB is provisioned (adjust column mapping as needed)
INSERT INTO booking.appointment (
    id, tenant_id, booking_id, client_id, patient_cpid, patient_id,
    provider_id, provider_name, facility_id, appointment_type,
    start_time, end_time, status, reason, notes, resource_id, created_at
)
SELECT
    id, tenant_id, NULL, patient_id, patient_cpid, patient_id,
    provider_id, provider_name, facility_id, appointment_type,
    scheduled_at, end_at, status, reason, notes, resource_id, created_at
FROM tuso.appointment
ON CONFLICT (id) DO NOTHING;
```

## BFF routing

`SchedulingController` and `CitizenAppointmentController` use `BookingServiceClient` → `http://localhost:8265`.

Configure: `impilo.services.booking-base-url` in experience-bff `application.yml`.

## UI language

| Surface | Label |
|---------|-------|
| Citizen | My Bookings / My Appointments |
| Provider | Booking Requests / Today's Appointments |

## Rollback

Set `impilo.services.booking-base-url` to empty and re-enable TUSO local `AppointmentService` in BFF (git revert BFF client swap). No data loss — `tuso.appointment` table retained.
