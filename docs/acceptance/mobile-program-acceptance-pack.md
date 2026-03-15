# Mobile Program — Acceptance Pack

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0
> Standard: vNext V3 + Tech Companion Spec 2.0
> Authority: This document defines what "done" means for the entire mobile app program.

---

## 1. Program-Level Success Criteria

The mobile app program is **complete** when ALL of the following are true:

| # | Criterion | Evidence |
|---|-----------|----------|
| 1 | All 4 apps (Provider, Citizen, Support, Developer) are implemented as real vertical slices | Source code in `apps/mobile/` with no mocks/stubs/TODOs |
| 2 | All shared foundation packages are published and used by all apps | Import audit across all 4 apps |
| 3 | All backend services touched are COMPLIANT in compliance matrix | `docs/compliance/full-platform-compliance-matrix.md` shows no BLOCKED/STUB |
| 4 | All golden paths pass end-to-end | Test evidence per golden path below |
| 5 | All documentation obligations met | PR diffs include doc updates per `docs/mobile/app-led-delivery-rules.md` |
| 6 | No prohibited patterns remain | CI scan for TODO/STUB/MOCK/FIXME tokens |
| 7 | Offline sync proven with data integrity | Airplane mode test with conflict resolution evidence |
| 8 | Push notifications delivered on both platforms | FCM + APNs delivery receipts |
| 9 | Accessibility baseline met | WCAG 2.1 AA scan results per app |
| 10 | Security checklist passed | Certificate pinning, encrypted storage, biometric auth verified |

---

## 2. Per-App Evidence Requirements

### 2.1 General Evidence (required for every app)

| Evidence Type | Description | Format |
|---------------|-------------|--------|
| Golden Path Recording | Screen recording or step-by-step screenshot walkthrough | Video/images |
| API Trace | Correlation ID trace from mobile → BFF → service → DB → outbox | Log excerpt with correlation_id |
| Outbox Proof | `SELECT * FROM event_outbox WHERE correlation_id = '<golden-path-correlation-id>'` | SQL output |
| Compliance Check | Service row in compliance matrix updated | Markdown diff |
| Import Audit | Grep for `@impilo/mobile-*` imports across app | Shell output |
| Error Path | Demonstrate error envelope on invalid input | API response JSON |

---

## 3. M1 — Provider App Acceptance

### 3.1 Golden Paths

#### GP-P1: Patient Lookup → Clinical Visit → Close

| Step | Action | Expected Outcome | Verification |
|------|--------|-------------------|-------------|
| 1 | Login as Provider via Keycloak PKCE | Token received, session established | `useAuth().isAuthenticated === true` |
| 2 | Search patient by name | Results from vito-service via experience-bff | API response contains patient list |
| 3 | Select patient, open new visit | Visit created in pct-service | `SELECT * FROM visits WHERE patient_cpid = ?` |
| 4 | Capture vitals (BP, temp, weight) | Vitals stored in pct-service | `SELECT * FROM vitals WHERE visit_id = ?` |
| 5 | Record diagnosis (ICD-11 code) | Diagnosis stored in oros-service | `SELECT * FROM diagnoses WHERE visit_id = ?` |
| 6 | Create prescription | Rx stored in pharmacy-service | `SELECT * FROM prescriptions WHERE visit_id = ?` |
| 7 | Close visit | Visit status = CLOSED, outbox event emitted | `SELECT * FROM event_outbox WHERE entity_id = ?` |
| **Status** | | | **PENDING** |

#### GP-P2: Outreach Mode — Community Visit

| Step | Action | Expected Outcome | Verification |
|------|--------|-------------------|-------------|
| 1 | Switch to Outreach mode | Mode context changes, GPS tracking starts | Session context shows mode=OUTREACH |
| 2 | Select household from register | Household members loaded from vito-service | API response with household members |
| 3 | Log community visit with findings | Visit stored with GPS coordinates | `SELECT * FROM visits WHERE type = 'OUTREACH'` |
| 4 | Sync to server | Data uploaded to experience-bff | Sync status = SYNCED |
| **Status** | | | **PENDING** |

#### GP-P3: Offline Edge — Disconnected Visit

| Step | Action | Expected Outcome | Verification |
|------|--------|-------------------|-------------|
| 1 | Download edge snapshot for facility | Local DB populated with facility patient data | `useEdgeSnapshot().lastSnapshot` has timestamp |
| 2 | Enable airplane mode | Network unavailable | `NetInfo.isConnected === false` |
| 3 | Search patient (local) | Results from local SQLite | Results displayed without network call |
| 4 | Capture full visit (vitals + Dx + Rx) | All data stored locally | Local DB queries return data |
| 5 | Disable airplane mode | Network restored | `NetInfo.isConnected === true` |
| 6 | Sync triggers automatically | Local data pushed to server via CRDT merge | `useSyncEngine().status === 'synced'` |
| 7 | Verify server state | Server DB matches local data | SQL queries on server match local records |
| **Status** | | | **PENDING** |

#### GP-P4: Supervisor Dashboard

| Step | Action | Expected Outcome | Verification |
|------|--------|-------------------|-------------|
| 1 | Login as Supervisor | Supervisor role recognized | Session actorType includes supervisor |
| 2 | View team KPI dashboard | Metrics loaded from workflow-service | KPI tiles populated |
| 3 | Review escalated task | Task detail loaded | Task status = ESCALATED |
| 4 | Approve/reject escalation | Task status updated | `SELECT * FROM tasks WHERE id = ? AND status = 'APPROVED'` |
| **Status** | | | **PENDING** |

---

## 4. M2 — Citizen / Patient App Acceptance

### 4.1 Golden Paths

#### GP-C1: Personal Health Profile

| Step | Action | Expected Outcome | Verification |
|------|--------|-------------------|-------------|
| 1 | Login as Citizen via Keycloak PKCE | Citizen session established | ActorType = CITIZEN |
| 2 | View health profile | Demographics, conditions, medications loaded | API responses from vito-service, pct-service |
| 3 | View visit history timeline | Chronological events displayed | Timeline events sorted by date |
| 4 | View active prescriptions | Current Rx list from pharmacy-service | Rx items with status = ACTIVE |
| **Status** | | | **PENDING** |

#### GP-C2: Secure Messaging

| Step | Action | Expected Outcome | Verification |
|------|--------|-------------------|-------------|
| 1 | Open messaging | Thread list loaded from channels-service | API response with threads |
| 2 | Send message to provider | Message stored, delivered | `SELECT * FROM messages WHERE thread_id = ?` |
| 3 | Receive reply | Push notification + in-app update | Notification received, thread updated |
| **Status** | | | **PENDING** |

#### GP-C3: Telehealth Session

| Step | Action | Expected Outcome | Verification |
|------|--------|-------------------|-------------|
| 1 | Book telehealth appointment | Appointment created via workflow-service | Appointment record in DB |
| 2 | Join video session at scheduled time | WebRTC connection via channels-service | Video/audio stream established |
| 3 | End session | Session summary generated in ubomi-service | Session record with duration, notes |
| **Status** | | | **PENDING** |

#### GP-C4: Marketplace Purchase

| Step | Action | Expected Outcome | Verification |
|------|--------|-------------------|-------------|
| 1 | Browse health products | Product catalog from msika-service | Product list displayed |
| 2 | Check coverage for product | Coverage status from coverage-service | Coverage response (covered/not) |
| 3 | Complete purchase | Order created in msika-service | `SELECT * FROM orders WHERE citizen_id = ?` |
| **Status** | | | **PENDING** |

#### GP-C5: Consent Management

| Step | Action | Expected Outcome | Verification |
|------|--------|-------------------|-------------|
| 1 | View current consent grants | Consent list from tshepo-consent-service | Consent records displayed |
| 2 | Revoke consent for a provider | Consent status = REVOKED | `SELECT * FROM consents WHERE status = 'REVOKED'` |
| 3 | Verify provider can no longer access data | Subsequent access denied | 403 response when provider queries data |
| **Status** | | | **PENDING** |

---

## 5. M3 — Support App Acceptance ✅ IMPLEMENTED

> Implementation: `ui/support-console/` (web app) + `services/support-service/` (backend)
> Full acceptance pack: `docs/acceptance/support-app-acceptance-pack.md`

### 5.1 Golden Paths

#### GP-S1: Ticket Lifecycle

| Step | Action | Expected Outcome | Verification |
|------|--------|-------------------|-------------|
| 1 | Login as Support agent | Support role session, ActorType = OPERATOR | useSupportSession store |
| 2 | View ticket queue | Filtered ticket list from support-service | GET /internal/v1/support/tickets with status/priority/category filters |
| 3 | Open ticket detail | Full ticket with comments, messages, assignments, diagnostics | GET ticket + comments + assignments + messages |
| 4 | Add comment | Comment stored, outbox event emitted | POST /internal/v1/support/tickets/{id}/comments → 201, outbox: ticket.comment.added.v1 |
| 5 | Assign ticket | Assignment recorded, ticket assigneeRef updated | POST /internal/v1/support/tickets/{id}/assign → 201, outbox: ticket.assigned.v1 |
| 6 | Escalate ticket | Escalation level incremented, priority promoted at L3 | POST /internal/v1/support/tickets/{id}/escalate → 200, outbox: ticket.escalated.v1 |
| 7 | Send message | Message stored in thread | POST /internal/v1/support/tickets/{id}/messages → 201, outbox: message.sent.v1 |
| 8 | Resolve ticket | Status = RESOLVED, resolvedAt set, outbox event | PATCH /internal/v1/support/tickets/{id} → 200, outbox: ticket.updated.v1 |
| **Status** | | | **PASS** |

#### GP-S2: Knowledge Base Lifecycle

| Step | Action | Expected Outcome | Verification |
|------|--------|-------------------|-------------|
| 1 | Create article | Article stored as DRAFT | POST /internal/v1/support/articles → 201, status=DRAFT |
| 2 | Publish article | Status = PUBLISHED, publishedAt set | PATCH /internal/v1/support/articles/{id} → 200, outbox: article.updated.v1 |
| 3 | Search articles | Published articles listed | GET /internal/v1/support/articles?status=PUBLISHED |
| 4 | Archive article | Status = ARCHIVED | PATCH /internal/v1/support/articles/{id} → 200 |
| **Status** | | | **PASS** |

#### GP-S3: Diagnostics Linkage

| Step | Action | Expected Outcome | Verification |
|------|--------|-------------------|-------------|
| 1 | Create ticket with correlation_id | Ticket stores correlation_id | POST with X-Correlation-ID header |
| 2 | Retrieve ticket | correlationId and requestId in response | GET ticket shows both IDs |
| 3 | Agent uses IDs to search observability | Cross-reference with Grafana/Loki/Tempo | Ticket detail shows diagnostics panel |
| **Status** | | | **PASS** |

#### GP-S4: Dashboard Analytics

| Step | Action | Expected Outcome | Verification |
|------|--------|-------------------|-------------|
| 1 | View dashboard | Stats loaded from backend | GET /internal/v1/support/dashboard/stats |
| 2 | Verify counts | Counts match database state | openCount, inProgressCount, resolvedCount, etc. |
| 3 | Navigate to filtered view | Quick-action links work | Click "Open Tickets" → /tickets?status=OPEN |
| **Status** | | | **PASS** |

---

## 6. M4 — Developer / Partner App Acceptance

### 6.1 Golden Paths

#### GP-D1: API Key Lifecycle

| Step | Action | Expected Outcome | Verification |
|------|--------|-------------------|-------------|
| 1 | Login as Developer | Developer session | Appropriate role/scope |
| 2 | Create API key with scoped permissions | Key generated by developer-portal-service | Key returned, stored securely |
| 3 | Make API call with key | Request succeeds, passes through TSHEPO | 200 response with data |
| 4 | Rotate key | Old key invalidated, new key issued | Old key returns 401 |
| **Status** | | | **PENDING** |

#### GP-D2: Webhook Management

| Step | Action | Expected Outcome | Verification |
|------|--------|-------------------|-------------|
| 1 | Register webhook endpoint | Webhook stored in integration-hub | Webhook record in DB |
| 2 | Trigger event that matches webhook | Event delivered to endpoint | Delivery log with 200 status |
| 3 | View delivery logs | Log entries from integration-hub | Delivery history displayed |
| **Status** | | | **PENDING** |

---

## 7. Repo Documentation Obligations Per App

| App | Must Update |
|-----|-------------|
| **All** | `docs/compliance/full-platform-compliance-matrix.md` |
| **All** | `docs/acceptance/mobile-program-acceptance-pack.md` (this document) |
| **All** | `docs/mobile/shared-foundation-scope.md` (if shared packages extended) |
| M1 Provider | `docs/offline/wave22-offline-pilot.md`, `docs/experience/ONLINE_VERIFICATION.md` |
| M2 Citizen | `docs/experience/ONLINE_VERIFICATION.md` |
| M3 Support | `docs/apps/support-app/README.md`, `docs/apps/support-app/feature-map.md`, `docs/apps/support-app/ops-workflows.md`, `docs/acceptance/support-app-acceptance-pack.md` |
| M4 Developer | `docs/acceptance/developer-platform-acceptance-pack.md` |

---

## 8. Compliance Evidence Checklist (per service touched)

For every backend service modified during a mobile app wave:

- [ ] Service row in compliance matrix is updated
- [ ] v1.1 header enforcement confirmed (4-header filter active)
- [ ] Idempotency on commands confirmed
- [ ] Golden contract test passes
- [ ] Outbox table has v1.1 columns
- [ ] EventEnvelope emission confirmed
- [ ] Health endpoint responding
- [ ] Status is COMPLIANT (not PARTIAL or BLOCKED)

---

## 9. Final Sign-Off

| Wave | App | Golden Paths Pass | Compliance Updated | Docs Updated | Acceptance Signed | Overall |
|------|-----|-------------------|--------------------|--------------|-------------------|---------|
| M1 | Provider | PENDING | PENDING | PENDING | PENDING | **PENDING** |
| M2 | Citizen | PENDING | PENDING | PENDING | PENDING | **PENDING** |
| M3 | Support | PASS | PASS | PASS | PASS | **PASS** |
| M4 | Developer | PENDING | PENDING | PENDING | PENDING | **PENDING** |
| **Program** | **All** | | | | | **PENDING** |
