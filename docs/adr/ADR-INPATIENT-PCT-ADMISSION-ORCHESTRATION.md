# ADR: Inpatient vs PCT Admission Orchestration

**Status:** Accepted  
**Date:** 2026-06-08

## Context

Impilo has two admission-related surfaces:

1. **PCT** journey-linked admission workflow (`/v1/journeys/...`, PCT `AdmissionController`)
2. **inpatient-service** canonical inpatient records (`/internal/v1/admissions`)

Without explicit doctrine, operators and integrators risk duplicate truth or broken handoffs.

## Decision

| Concern | System of record | BFF surface |
|---|---|---|
| Patient journey / queue / encounter spine | `pct-service` | `/internal/v1/queue/*`, `/internal/v1/encounters/*` |
| Canonical inpatient episode (bed, ward, rounds, discharge) | `inpatient-service` | `/internal/v1/inpatient/*` |
| Outpatient → inpatient handoff | PCT orchestrates transition event; inpatient-service receives create admission command | BFF composes both |

**Ward rounds** are sovereign to `inpatient-service` only. BFF must not proxy ward rounds to PCT.

## Consequences

- UI admission lists use `/internal/v1/inpatient/admissions` with `admissionRef` as the stable key
- PCT journey admission remains for queue/encounter correlation, not bed/round documentation
- Future ADR amendment required if referral-service absorbs telemedicine orchestration from PCT
