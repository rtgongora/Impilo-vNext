# Booking vs Appointment — Product Doctrine

> **Status:** Canonical  
> **ADR:** [ADR-0051](../adr/ADR-0051-booking-appointment-distinction.md)  
> **SoR:** `booking-service` (experience plane)

## Core distinction

| Concept | Answers | Role |
|---------|---------|------|
| **Booking** | Has access been requested, reserved, approved, consented, paid, confirmed, rejected, or fulfilled? | Transaction container |
| **Appointment** | When, where, with whom, and for what care activity? | Scheduled operational event |
| **Encounter** | What care was actually delivered? | Clinical/service interaction |

**Do not collapse** Booking and Appointment into one generic calendar object.

## Lifecycle rules

1. Booking is the transaction container; Appointment may be created from a Booking.
2. A Booking may exist without an Appointment initially.
3. An Appointment is created only when availability, eligibility, consent/agreement/authorisation (Mvumo), payment/exemption/programme coverage, and approval rules are satisfied.
4. Some bookings are instantly confirmed; others require provider, facility, programme, or resource-owner review.
5. Mvumo is the consent, agreement, and authorisation layer for booking acceptance workflows.
6. Orders, referrals, payments, queue tokens, bed allocation, theatre slots, dispatch, delivery, and training sessions **link** to the Booking — they are not duplicated.

## UI language

### Client-facing
- **Book** — initiate a booking request
- **My Bookings** — transaction status (pending, confirmed, cancelled)
- **My Appointments** — confirmed scheduled events
- Mvumo prompts: "Consent required", "Accept telemedicine terms", "Authorise data sharing"

### Provider/facility-facing
- **Booking Requests** — inbox needing triage/approval
- **Appointments** — calendar of scheduled events
- **Today's Appointments** — confirmed operational workload
- **Pending Bookings** — unresolved transactions
- **Resource Bookings** — beds, theatre, diagnostics, transport

## Integration boundaries

| Service | Role in booking journey |
|---------|------------------------|
| VITO | Client identity (CPID) |
| VARAPI | Provider ID (`providerPublicId`), eligibility, affiliation |
| TUSO | Facility, workspace, resource availability, resource reservations |
| MVUMO | Consent, agreement, authorisation gating |
| MUSHEX/COSTA | Payment, exemption, claims |
| PCT | Queue, encounter, referral links |
| OROS | Lab/radiology orders |
| Nhume | Dispatch/delivery |
| learning-service | Training/CPD sessions |
