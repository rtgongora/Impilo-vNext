# Booking vs Appointment — Implementation Summary

> **Date:** 2026-06-07 | **ADR:** ADR-0051

## What changed

### New sovereign service: `booking-service` (port 8265)
- **Booking** transaction container with full state machine, Mvumo gating, VARAPI Provider ID resolution
- **Appointment** scheduled events (migrated SoR from TUSO)
- Integration clients: VARAPI, TUSO, Mvumo, PCT, COSTA, MUSHEX, OROS, Nhume, Learning, Community
- OpenAPI: `contracts/openapi/booking.openapi.yaml`

### Experience BFF
- `BookingServiceClient`, `BookingController`, `BookingMvumoController`, `BookingRoutingController`, `BookingRequestsController`
- `CitizenBookingController` for mobile bookings
- `SchedulingController` + `CitizenAppointmentController` repointed to booking-service

### Web (`one-ui-shell`)
- **Citizen:** `/home/bookings`, `/home/bookings/new`, `/home/bookings/[bookingId]`, `/home/appointments`, `/home/appointments/[appointmentId]`
- **Provider:** `/scheduling/booking-requests`, `/scheduling/today`, `/scheduling/bookings/config`
- Nav: ExperienceSidebar, ui-route-journey-map, facilityOperationsNav, app-registry
- `EXPECTED_ROUTE_COUNT`: 426

### Mobile
- Citizen: `BookingsSection` (book service wizard + Mvumo hint) vs `AppointmentsSection` (confirmed only)
- Citizen: `bookingService.ts` + `facilityService.ts`; `CitizenBookingController` POST create
- Provider: `BookingRequestsScreen` in Clinical Tools

## What was preserved
- All BFF appointment endpoint paths
- TUSO `AppointmentController` (deprecated fallback)
- TUSO resource bookings and facility registry
- Marketplace bookings
- PCT queue/encounter check-in spine

## End-to-end flow
1. Citizen **books** → Booking `REQUESTED` (no appointment yet)
2. Mvumo consent/agreement if required → `GRANTED` gates confirm
3. Provider **approves** → `convertBookingToAppointment` → Appointment `SCHEDULED`
4. Check-in → PCT journey + queue → Encounter → Booking `FULFILLED`

## Tests
- `booking-service`: BookingServiceTest, AppointmentServiceTest, BookingStateMachineTest, ProviderResolutionServiceTest
- `experience-bff`: BookingControllerTest, SchedulingControllerTest, CitizenAppointmentControllerTest
- `one-ui-shell`: route parity 426/426, page tests for bookings surfaces
