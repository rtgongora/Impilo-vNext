# Provider App Feature Map

This document provides a comprehensive mapping of every feature in the Provider App, linking each to its operational mode, screens, service layer, BFF route, and backend service.

## Feature Matrix

| Feature Area | Mode | Screen(s) | Service File | BFF Route | Backend Service | Status |
|---|---|---|---|---|---|---|
| Patient Lookup | Provider | Worklist, Patients | `patientService.ts` | `GET /internal/v1/mobile/provider/patients` | VITO | IMPLEMENTED |
| Encounters | Provider | Encounter Detail | `encounterService.ts` | `POST /internal/v1/mobile/provider/encounters` | TUSO | IMPLEMENTED |
| Vitals | Provider | Vitals Capture | `vitalsService.ts` | `POST /internal/v1/mobile/provider/vitals` | TUSO | IMPLEMENTED |
| Diagnosis | Provider | Diagnosis Entry | `diagnosisService.ts` | `POST /internal/v1/mobile/provider/diagnoses` | TUSO | IMPLEMENTED |
| Prescriptions | Provider | Prescription Form | `prescriptionService.ts` | `POST /internal/v1/mobile/provider/prescriptions` | OROS | IMPLEMENTED |
| Labs | Provider | Lab Orders, Lab Results | `labService.ts` | `POST /internal/v1/mobile/provider/labs` | ZIBO | IMPLEMENTED |
| Referrals | Provider | Referral Form, Referral List | `referralService.ts` | `POST /internal/v1/mobile/provider/referrals` | TUSO | IMPLEMENTED |
| Tasks | Provider | Worklist | `taskService.ts` | `GET /internal/v1/mobile/provider/tasks` | TUSO | IMPLEMENTED |
| Messaging | Provider, Outreach, Supervisor | Messages, Chat | `messagingService.ts` | `POST /internal/v1/mobile/provider/messages` | Messaging Gateway | IMPLEMENTED |
| Telemedicine | Provider | Telemedicine Session | `telemedicineService.ts` | `POST /internal/v1/mobile/provider/telemedicine` | Telemedicine Service | IMPLEMENTED |
| Households | Outreach | Household List, Household Detail | `householdService.ts` | `GET /internal/v1/mobile/provider/households` | VITO | IMPLEMENTED |
| Screenings | Outreach | Screening Form, Screening List | `screeningService.ts` | `POST /internal/v1/mobile/provider/screenings` | TUSO | IMPLEMENTED |
| Immunizations | Outreach | Immunization Record | `immunizationService.ts` | `POST /internal/v1/mobile/provider/immunizations` | TUSO | IMPLEMENTED |
| Follow-Ups | Outreach | Follow-Up Schedule | `followUpService.ts` | `GET /internal/v1/mobile/provider/follow-ups` | TUSO | IMPLEMENTED |
| Supervisor Dashboard | Supervisor | Dashboard | `dashboardService.ts` | `GET /internal/v1/mobile/provider/supervisor/dashboard` | Analytics Service | IMPLEMENTED |
| Team Overview | Supervisor | Team List, Staff Detail | `teamService.ts` | `GET /internal/v1/mobile/provider/supervisor/team` | VITO | IMPLEMENTED |
| Stock Management | Supervisor | Stock List, Stock Adjust | `stockService.ts` | `GET /internal/v1/mobile/provider/supervisor/stock` | OROS | IMPLEMENTED |
| Dispatch | Supervisor | Dispatch Queue | `dispatchService.ts` | `POST /internal/v1/mobile/provider/supervisor/dispatch` | TUSO | IMPLEMENTED |
| Escalations | Supervisor | Escalation List, Escalation Detail | `escalationService.ts` | `GET /internal/v1/mobile/provider/supervisor/escalations` | TUSO | IMPLEMENTED |
| Support Tickets | Supervisor | Support Form | `supportService.ts` | `POST /internal/v1/mobile/provider/supervisor/support` | Ops Service | IMPLEMENTED |
| Offline Sync | Offline Edge | Sync Status, Queue | `syncService.ts` | `POST /internal/v1/mobile/provider/sync` | Sync Gateway | IMPLEMENTED |
| Conflict Resolution | Offline Edge | Conflict List, Conflict Detail | `conflictService.ts` | `POST /internal/v1/mobile/provider/sync/resolve` | Sync Gateway | IMPLEMENTED |
| Break-Glass | Offline Edge | Emergency Access | `breakGlassService.ts` | `POST /internal/v1/mobile/provider/break-glass` | TSHEPO | IMPLEMENTED |
| Entitlement Verification | Provider, Outreach | Entitlement Check | `entitlementService.ts` | `GET /internal/v1/mobile/provider/entitlements` | PCT | IMPLEMENTED |
| Forms | Provider, Outreach | Dynamic Form Renderer | `formService.ts` | `GET /internal/v1/mobile/provider/forms` | VARAPI | IMPLEMENTED |
| Notifications | All | Notification Center | `notificationService.ts` | `GET /internal/v1/mobile/provider/notifications` | Messaging Gateway | IMPLEMENTED |
| Activity Feed | Provider | Activity Tab | `activityService.ts` | `GET /internal/v1/mobile/provider/activity` | TUSO | IMPLEMENTED |

## Notes

- All BFF routes are prefixed with `/internal/v1/mobile/provider` and enforce v1.1 header contract compliance.
- Every write operation supports idempotency keys to ensure safe retries during offline sync.
- Backend services communicate via the outbox pattern; Kafka events are published reliably from each service's `event_outbox` table.
- The "Backend Service" column indicates the primary service handling the domain logic. Some features involve orchestration across multiple services at the BFF layer.
