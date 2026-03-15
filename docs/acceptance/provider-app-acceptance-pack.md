# Provider App — Acceptance Pack

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0
> Standard: vNext V3 + Tech Companion Spec 2.0

---

## 1. Scope

The Provider App is a mobile-first application serving frontline healthcare workers across **4 operational modes**:

| Mode | Role | Description |
|------|------|-------------|
| **Provider** | Clinician / Nurse | Patient lookup, encounter workflow, vitals, diagnosis, prescriptions, labs, referrals |
| **Outreach** | Community Health Worker | Household registration, community visits, screenings, immunizations, follow-ups |
| **Supervisor** | Facility Manager | KPI dashboard, team oversight, stock management, dispatch, escalations |
| **Offline Edge** | Any (disconnected) | Offline-first data capture, sync queue, conflict resolution, break-glass access |

All modes share a common authentication layer (Keycloak PKCE), trust-header injection, and the Experience BFF as the single backend gateway.

---

## 2. Prerequisites

| Dependency | Endpoint | Purpose |
|------------|----------|---------|
| Keycloak | `:8080` | OIDC identity provider, realm and client configured |
| Experience BFF | `:8086` | API gateway for all Provider App requests |
| PCT (Patient Care Tracker) | `:8088` | Encounters, vitals, diagnoses, prescriptions, labs, referrals |
| OROS (Operational Resource Orchestration) | `:8089` | Stock, dispatch, supervisor operations |
| VITO (Vital Identity & Trust Oracle) | `:8082` | Patient identity, PII storage |
| TUSO (Task & Unified Scheduling Orchestrator) | `:8084` | Tasks, scheduling, follow-ups |
| PostgreSQL | `:5432` | Persistent storage for all services |
| Redis | `:6379` | Caching, session store |
| Kafka | `:9092` | Event streaming, outbox relay |

**Environment setup**:
1. All services running via `docker compose up` or individual service launch.
2. Keycloak realm imported with test users for each role (provider, outreach worker, supervisor).
3. Seed data loaded: test patients, facilities, stock items.

---

## 3. Acceptance Criteria

### 3.1 Authentication & Authorization

| ID | Criterion | Steps | Expected Result |
|----|-----------|-------|-----------------|
| AC-001 | Keycloak PKCE login flow completes | Open app, tap Login, enter credentials | Redirected back to app with valid tokens |
| AC-002 | Token refresh works before expiry | Wait for token near-expiry, trigger API call | New access token obtained silently, no re-login |
| AC-003 | Facility selection after login | Login successfully | Facility picker shown; selection stored in context |
| AC-004 | Mode switching based on roles | Login with multi-role user | Mode selector shows only permitted modes |
| AC-005 | AuthGuard blocks unauthenticated access | Navigate to protected route without token | Redirected to login screen |
| AC-006 | Trust headers injected on every API call | Inspect outgoing request headers | All 4 hard-required trust headers present |

- [ ] **AC-001**: Keycloak PKCE login flow completes
- [ ] **AC-002**: Token refresh works before expiry
- [ ] **AC-003**: Facility selection after login
- [ ] **AC-004**: Mode switching based on roles
- [ ] **AC-005**: AuthGuard blocks unauthenticated access
- [ ] **AC-006**: Trust headers injected on every API call

---

### 3.2 Provider Mode

| ID | Criterion | Steps | Expected Result |
|----|-----------|-------|-----------------|
| AC-010 | Patient search by name | Enter patient name in search | Matching patients returned with demographics |
| AC-011 | Patient search by NID | Enter national ID in search | Exact patient match returned |
| AC-012 | QR code scan identifies patient | Scan patient QR code | Patient record loaded from CPID |
| AC-013 | Encounter creation | Select patient, tap "Start Encounter" | New encounter created with patient ID, status OPEN |
| AC-014 | Vitals recording | Enter BP, HR, Temp, SpO2, RR, Weight, Height | All vitals saved; BMI auto-calculated |
| AC-015 | Vitals batch recording | Record multiple vitals in one submission | All vitals persisted in single API call |
| AC-016 | ICD-11 diagnosis search and selection | Type diagnosis term, select from results | Diagnosis linked to encounter with ICD-11 code |
| AC-017 | Prescription creation | Enter medication, dosage, frequency, duration | Prescription saved to encounter |
| AC-018 | Lab order creation | Select lab test, set urgency (ROUTINE/URGENT/STAT) | Lab order created with correct urgency level |
| AC-019 | Referral creation | Select facility, specialty, add notes | Referral created with target facility and specialty |
| AC-020 | Clinical notes saved | Enter free-text clinical notes | Notes persisted against encounter |
| AC-021 | Encounter close with summary | Tap "Close Encounter" | Encounter status set to CLOSED, summary generated |
| AC-022 | Activity feed shows timeline | Navigate to activity feed | Timeline events displayed in chronological order |
| AC-023 | Task list shows assigned tasks | Navigate to task list | Tasks assigned to current provider displayed |
| AC-024 | Task status update | Accept, complete, or escalate a task | Task status updated; event emitted |

- [ ] **AC-010**: Patient search by name returns results
- [ ] **AC-011**: Patient search by NID returns results
- [ ] **AC-012**: QR code scan identifies patient
- [ ] **AC-013**: Encounter creation with patient ID
- [ ] **AC-014**: Vitals recording (BP, HR, Temp, SpO2, RR, Weight, Height, BMI)
- [ ] **AC-015**: Vitals batch recording
- [ ] **AC-016**: ICD-11 diagnosis search and selection
- [ ] **AC-017**: Prescription creation with dosage/frequency/duration
- [ ] **AC-018**: Lab order creation with urgency levels
- [ ] **AC-019**: Referral creation with facility/specialty
- [ ] **AC-020**: Clinical notes saved to encounter
- [ ] **AC-021**: Encounter close with summary
- [ ] **AC-022**: Activity feed shows timeline events
- [ ] **AC-023**: Task list shows assigned tasks
- [ ] **AC-024**: Task status update (accept/complete/escalate)

---

### 3.3 Messaging & Telemedicine

| ID | Criterion | Steps | Expected Result |
|----|-----------|-------|-----------------|
| AC-030 | Conversation list loads | Navigate to messaging | All conversations for current user displayed |
| AC-031 | Messages load for conversation | Tap a conversation | Message history rendered in order |
| AC-032 | Send message | Type and send a message | Message appears in conversation, persisted |
| AC-033 | Real-time message delivery | Send message from another user | Message appears without page refresh |
| AC-034 | Telemedicine session list | Navigate to telemedicine | Active and scheduled sessions displayed |
| AC-035 | Join session returns video token | Tap "Join" on a session | Video token returned, video UI initialised |
| AC-036 | End session updates status | End a telemedicine session | Session status set to COMPLETED |

- [ ] **AC-030**: Conversation list loads
- [ ] **AC-031**: Messages load for conversation
- [ ] **AC-032**: Send message in conversation
- [ ] **AC-033**: Real-time message delivery
- [ ] **AC-034**: Telemedicine session list loads
- [ ] **AC-035**: Join session returns video token
- [ ] **AC-036**: End session updates status to COMPLETED

---

### 3.4 Outreach Mode

| ID | Criterion | Steps | Expected Result |
|----|-----------|-------|-----------------|
| AC-040 | Household list loads | Switch to Outreach mode | Assigned households displayed |
| AC-041 | Household registration | Tap "Register Household", fill form | New household created with members |
| AC-042 | Community visit with GPS | Start visit at household location | Visit recorded with GPS coordinates |
| AC-043 | Screening recording | Conduct screening (malnutrition, malaria, TB, HIV, diabetes, hypertension) | Screening results saved with type and outcome |
| AC-044 | Immunization recording | Record immunization with batch/lot number | Immunization event saved with vaccine details |
| AC-045 | Follow-up list sorted by overdue | Navigate to follow-ups | List sorted by overdue days descending |
| AC-046 | Offline household access | Go offline, access household data | Cached household data available |

- [ ] **AC-040**: Household list loads
- [ ] **AC-041**: Household registration
- [ ] **AC-042**: Community visit recording with GPS
- [ ] **AC-043**: Screening recording (malnutrition, malaria, TB, HIV, diabetes, hypertension)
- [ ] **AC-044**: Immunization recording with batch/lot
- [ ] **AC-045**: Follow-up list sorted by overdue days
- [ ] **AC-046**: Offline household access from cache

---

### 3.5 Supervisor Mode

| ID | Criterion | Steps | Expected Result |
|----|-----------|-------|-----------------|
| AC-050 | Dashboard KPI tiles load | Switch to Supervisor mode | Tiles: patients seen, encounters, avg wait, tasks, escalations, stock alerts |
| AC-051 | Team member list with status | Navigate to team view | Team members listed with online/offline/on-leave status |
| AC-052 | Stock levels with low-stock alerts | Navigate to stock view | Stock items displayed; low-stock items highlighted |
| AC-053 | Dispatch creation and confirmation | Create dispatch order | Dispatch confirmed with items and destination |
| AC-054 | Escalation acknowledge/resolve | View escalation, acknowledge or resolve | Escalation status updated; audit trail recorded |
| AC-055 | Support ticket creation | Create support ticket | Ticket created with category and description |

- [ ] **AC-050**: Dashboard KPI tiles load (patients seen, encounters, avg wait, tasks, escalations, stock alerts)
- [ ] **AC-051**: Team member list with status
- [ ] **AC-052**: Stock levels with low-stock alerts
- [ ] **AC-053**: Dispatch creation and confirmation
- [ ] **AC-054**: Escalation acknowledge/resolve
- [ ] **AC-055**: Support ticket creation

---

### 3.6 Offline Edge Mode

| ID | Criterion | Steps | Expected Result |
|----|-----------|-------|-----------------|
| AC-060 | Online/offline status detection | Toggle network connectivity | Status indicator reflects current state |
| AC-061 | Sync queue displays pending items | Create data while offline | Pending items listed with type and timestamp |
| AC-062 | Sync all triggers background sync | Tap "Sync All" when online | All queued items synced; queue emptied |
| AC-063 | Failed items can be retried | Trigger sync failure, then retry | Failed item re-queued and retried successfully |
| AC-064 | Conflict detection on sync | Modify same record offline and on server | Conflict detected and flagged during sync |
| AC-065 | Conflict resolution | Choose LOCAL_WINS or SERVER_WINS | Conflict resolved per selection; both versions audited |
| AC-066 | Edge snapshot download | Request snapshot | Full offline dataset downloaded and cached |
| AC-067 | Entitlement verification by CPID | Enter CPID for entitlement check | Entitlement status returned (eligible/ineligible) |
| AC-068 | Break-glass activation with audit | Activate break-glass access | Elevated access granted; audit event recorded |
| AC-069 | Break-glass deactivation | Deactivate break-glass | Access reverted to normal; audit event recorded |

- [ ] **AC-060**: Online/offline status detection
- [ ] **AC-061**: Sync queue displays pending items
- [ ] **AC-062**: Sync all triggers background sync
- [ ] **AC-063**: Failed items can be retried
- [ ] **AC-064**: Conflict detection on sync
- [ ] **AC-065**: Conflict resolution (LOCAL_WINS/SERVER_WINS)
- [ ] **AC-066**: Edge snapshot download
- [ ] **AC-067**: Entitlement verification by CPID
- [ ] **AC-068**: Break-glass activation with audit
- [ ] **AC-069**: Break-glass deactivation

---

### 3.7 Backend Integration

| ID | Criterion | Steps | Expected Result |
|----|-----------|-------|-----------------|
| AC-070 | BFF route prefix | Inspect all API calls | All routes use `/internal/v1/` prefix |
| AC-071 | Trust headers present | Inspect request headers on any mutation | 4 hard-required trust headers attached |
| AC-072 | ApiEnvelope response shape | Inspect any API response | Shape: `{ success, data, error, correlationId, timestamp }` |
| AC-073 | Idempotency keys on writes | Send POST/PUT/PATCH request | `Idempotency-Key` header present |
| AC-074 | Outbox events emitted | Perform a mutation (e.g., create encounter) | Corresponding row inserted into `event_outbox` table |

- [ ] **AC-070**: All BFF routes use `/internal/v1/` prefix
- [ ] **AC-071**: 4 hard-required trust headers present on every request
- [ ] **AC-072**: ApiEnvelope response shape (success, data, error, correlationId, timestamp)
- [ ] **AC-073**: Idempotency keys on POST/PUT/PATCH
- [ ] **AC-074**: Outbox events emitted for mutations

---

## 4. Test Coverage

| Test File | Area | Tests | Status |
|-----------|------|-------|--------|
| `AuthGuard.test.tsx` | Navigation | 3 | |
| `ModeRouter.test.tsx` | Navigation | 4+ | |
| `EncounterWorkflow.test.tsx` | Provider | 5+ | |
| `PatientLookup.test.tsx` | Provider | 3 | |
| `ProviderDashboard.test.tsx` | Provider | 2 | |
| `OutreachDashboard.test.tsx` | Outreach | 2 | |
| `OfflineCapture.test.tsx` | Outreach | 3 | |
| `SupervisorDashboard.test.tsx` | Supervisor | 3 | |
| `SyncFlow.test.tsx` | Offline | 7 | |
| `ConflictReview.test.tsx` | Offline | 6 | |
| `Messaging.test.tsx` | Messaging | 3 | |
| `Telemedicine.test.tsx` | Telemedicine | 3 | |
| `BackendIntegration.test.tsx` | Integration | 5 | |

**Total**: 13 test files, 49+ test cases

---

## 5. Sign-off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Engineering Lead | | | |
| QA Lead | | | |
| Product Owner | | | |
| Clinical SME | | | |

---

*End of Provider App Acceptance Pack*
