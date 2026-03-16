# Mobile Parity Fixes Applied

**Date**: 2026-03-16
**Branch**: `claude/review-project-manifest-jb5O0`

## Summary

**Closure Wave 1**: 24 files changed, ~3,984 lines added to achieve full mobile parity for both Provider App and Citizen App.
**Closure Wave 2**: 8 additional files changed, ~1,200 lines added for discharge workflow and consent management.

## Provider App Fixes (Phase 2)

### New Screens

| File | Description |
|---|---|
| `ProfessionalProfileScreen.tsx` | Provider profile view with credentials, specializations, facility assignment, contact info, editable fields |
| `ScheduleScreen.tsx` | Shift/roster calendar view with weekly schedule, on-call status, swap requests |
| `ResultsViewScreen.tsx` | Lab results review with acknowledge action, filtering by status, result detail modal |

### New Services

| File | Description |
|---|---|
| `profileService.ts` | Provider profile CRUD, schedule queries, notices fetch — all using mobile-api-client with trust headers |

### New BFF Controllers

| Controller | Endpoints |
|---|---|
| `MobileProfileController.java` | GET /mobile/v1/provider/profile, PATCH /mobile/v1/provider/profile |
| `MobileScheduleController.java` | GET /mobile/v1/provider/schedule |
| `MobileNoticesController.java` | GET /mobile/v1/provider/notices |
| `MobileResultsController.java` | GET /mobile/v1/results, POST /mobile/v1/results/{id}/acknowledge |

### Navigation Updates

| File | Change |
|---|---|
| `ProviderTabs.tsx` | Added Results tab and Profile tab to bottom navigation |

## Citizen App Fixes (Phase 3)

### New Screens

| File | Description |
|---|---|
| `RecordsScreen.tsx` | Medical records/documents viewer with category filters, download actions, document detail view |
| `RemindersScreen.tsx` | Medication/appointment reminders with create, edit, dismiss, snooze, and notification scheduling |
| `HealthTimelineScreen.tsx` | Unified health timeline with chronological event display, category filters, detail expansion |
| `SupportScreen.tsx` | Support tickets with create/view, knowledge base articles, FAQ accordion |

### New Services

| File | Description |
|---|---|
| `recordsService.ts` | Records listing, download, share — using mobile-api-client |
| `remindersService.ts` | Reminders CRUD with notification scheduling integration |
| `healthTimelineService.ts` | Timeline events fetch with category filtering |

### New BFF Controllers

| Controller | Endpoints |
|---|---|
| `CitizenRecordsController.java` | GET /mobile/v1/citizen/records |
| `CitizenRemindersController.java` | GET, POST, PATCH, DELETE /mobile/v1/citizen/reminders |
| `CitizenTimelineController.java` | GET /mobile/v1/citizen/timeline |

### New Domain

| File | Description |
|---|---|
| `V7__reminders_table.sql` | Reminders table with type, title, scheduled_at, status, patient_id, tenant_id |
| `Reminder.java` | JPA entity with dismiss/snooze lifecycle methods |
| `ReminderRepository.java` | JPA repository with patient/tenant queries |

### Navigation Updates

| File | Change |
|---|---|
| `PersonalScreen.tsx` | Added Records, Reminders, Timeline tabs to personal hub |

## Closure Wave 2 Additions

### Provider App — Discharge Workflow

#### New Screens

| File | Description |
|---|---|
| `DischargeScreen.tsx` | Discharge form with type picker, diagnosis, treatment summary, follow-up, medications, patient instructions |

#### New Services

| File | Description |
|---|---|
| `dischargeService.ts` | submitDischarge + getDischargeStatus using mobile-api-client with trust headers |

#### New BFF Controllers

| Controller | Endpoints |
|---|---|
| `MobileDischargeController.java` | POST /mobile/v1/provider/discharge, GET /mobile/v1/provider/discharge/{encounterId} |

### Citizen App — Consent Management

#### New Screens

| File | Description |
|---|---|
| `ConsentScreen.tsx` | Consent preference toggles for data sharing categories (treatment, research, analytics, third-party) |

#### New Services

| File | Description |
|---|---|
| `consentService.ts` | getConsents + updateConsent using mobile-api-client |

#### New BFF Controllers

| Controller | Endpoints |
|---|---|
| `CitizenConsentController.java` | GET /mobile/v1/citizen/consents, PUT /mobile/v1/citizen/consents |

#### New Domain

| File | Description |
|---|---|
| `V8__encounter_discharge_columns.sql` | consent_preferences table with tenant/patient/type unique constraint |

#### Navigation Updates

| File | Change |
|---|---|
| `PersonalScreen.tsx` | Added Consent tab to personal hub |
