# ADR-0051: Booking vs Appointment Platform Distinction

## Status
Accepted — 2026-06-07

## Context

Impilo vNext conflated "booking" (transaction/reservation) with "appointment" (scheduled care event) in TUSO facility registry. Citizens and providers need distinct concepts: a booking request may pend on consent, payment, or approval before an appointment exists.

Lovable's prior model separated Bookings (transaction) from Appointments (scheduled event). Telemedicine routing already supports polymorphic targets (provider, workspace, facility service).

## Decision

1. Create **`booking-service`** as sovereign owner of **Booking** and **Appointment** aggregates on the experience/orchestration plane (port **8265**).
2. **Migrate** Appointment SoR from `tuso-service` to `booking-service`. TUSO retains facility/resource availability only.
3. **Booking** carries state machine, Mvumo gating, payment requirements, approval workflow, and links to external sovereign records by ID.
4. **Appointment** is created via `convertBookingToAppointment` when rules pass.
5. TUSO `AppointmentController` becomes a **deprecated read-through shim** for compatibility.
6. BFF exposes `/internal/v1/bookings/*` alongside preserved `/internal/v1/appointments/*`.

## Consequences

### Positive
- Clear product language (My Bookings vs My Appointments)
- Mvumo gates confirmation properly
- Real VARAPI Provider ID resolution
- Extensible booking types (theatre, lab, dispatch, training)

### Negative
- New service in full-boot matrix
- Migration of `tuso.appointment` data required
- Large frontend surface area

## Alternatives considered

- **PCT as owner:** Rejected — PCT owns encounter/queue journey, not scheduling transactions
- **TUSO as owner:** Rejected — appointments are person/provider-centric, not facility-registry-centric
- **Single unified entity:** Rejected per product doctrine
