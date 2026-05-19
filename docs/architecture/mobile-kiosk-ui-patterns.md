# Mobile and Kiosk UI Patterns

## Current Baseline

- Dedicated kiosk route exists at `/kiosk`.
- Mobile-friendly shell and taskbar behavior already adapts through responsive utility classes.

## Consolidation Guidance

- Keep client mobile nav anchored to:
  - Home (`/home`)
  - Find Care (`/discover`)
  - My Visit (`/client-journey`)
  - Records (`/monitoring` and `/home/documents`)
  - Ask (`/ask`)
- Keep provider mobile nav anchored to:
  - Duty (`/provider/activate` or `/shift`)
  - Work (`/provider-workspace`)
  - Patients (`/queue`, `/ehr`)
  - Tasks (`/queue`, `/scheduling`)
  - Ask (`/ask`)

## Kiosk Mode Expectations

- Identity entry.
- Service selection.
- Appointment check and queue join.
- Payment where required.
- Assistance and accessibility controls.
- Large hit targets and session timeout.

