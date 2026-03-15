# Mobile App Program — Execution Plan

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0
> Standard: vNext V3 + Tech Companion Spec 2.0
> Posture: App-Led Vertical Slices — no mocks, no stubs, no TODOs

---

## 1. Final App Build Order

The build order is determined by dependency depth, shared-foundation bootstrap requirements, and backend service readiness.

| Wave | App | Rationale |
|------|-----|-----------|
| **M1** | **Provider App** (Provider + Outreach + Supervisor + Offline Edge modes) | Highest clinical value; exercises the broadest backend surface (PCT, OROS, VITO, TUSO, offline-sync, forms, workflow); forces shared foundation to be built first |
| **M2** | **Citizen / Patient App** (Personal + Social + Marketplace + Messaging + Telehealth) | Citizen-facing; depends on channels-service, coverage-service, marketplace features in msika-service, messaging in channels-service, telehealth in ubomi-service; reuses M1 shared foundation |
| **M3** | **Support App** | Internal helpdesk; depends on support-service, search-service, audit-ledger-service; lightest backend surface; reuses M1+M2 shared foundation |
| **M4** | **Developer / Partner App** | External developer experience; depends on developer-portal-service, integration-hub; reuses all prior shared foundation |

---

## 2. Shared Foundation Scope (built during M1, reused in M2–M4)

All shared packages are defined in `docs/mobile/shared-foundation-scope.md`. Summary:

| Package | Purpose |
|---------|---------|
| `@impilo/mobile-auth` | Keycloak PKCE session, token refresh, biometric unlock |
| `@impilo/mobile-api-client` | v1.1 header injection, ApiEnvelope, idempotency, retry |
| `@impilo/mobile-messaging` | Push notification registration, Kafka-backed real-time channels |
| `@impilo/mobile-timeline` | Unified event feed / activity timeline |
| `@impilo/mobile-offline` | Offline-first CRDT sync, queue, conflict resolution |
| `@impilo/mobile-design-system` | Impilo design tokens, Radix-native mobile components |
| `@impilo/mobile-trust` | Trust header contract (mirrors `ui/shared-ui/lib/contracts.ts`) |

---

## 3. Per-App Vertical Slice Scope

### 3.1 M1 — Provider App

**Modes:** Provider, Outreach, Supervisor, Offline Edge

#### Feature Areas

| Feature | Description | Primary Screen(s) |
|---------|-------------|--------------------|
| Patient Lookup | Search/scan patient by name, NID, or QR | Home → Search |
| Clinical Visit | Open visit, vitals capture, diagnosis (ICD-11), prescriptions | Visit → Vitals → Diagnosis → Rx |
| Forms Engine | Dynamic forms driven by forms-service | Visit → Dynamic Form |
| Task Board | Assigned tasks, overdue reminders, supervisor escalation | Dashboard → Tasks |
| Outreach Mode | Community visit logging, GPS track, household register | Outreach → Household → Visit |
| Supervisor Dashboard | Team overview, KPI tiles, approval queue | Supervisor → Dashboard |
| Offline Edge | Full visit workflow with local-first storage, background sync | All screens (offline overlay) |
| Prescriptions & Dispensing | Create Rx, view dispensing status | Visit → Rx → Dispensing |
| Referrals | Create/view referrals to other facilities | Visit → Referral |
| Lab Orders | Order labs, view results | Visit → Lab → Results |
| Notifications | Push + in-app for task assignments, results, escalations | Notification tray |

#### Backend Service Dependencies

| Service | Exists | Needs Upgrade | New Endpoints Needed |
|---------|--------|---------------|----------------------|
| experience-bff | Yes (scaffold) | Yes — add mobile-specific aggregation routes | `/internal/v1/mobile/provider/*` |
| vito-service | Yes (COMPLIANT) | No | — |
| pct-service | Yes (PARTIAL) | Yes — `/internal/v1` route migration | — |
| oros-service | Yes (PARTIAL) | Yes — `/internal/v1` route migration | — |
| tuso-service | Yes (COMPLIANT) | No | — |
| forms-service | Yes (scaffold) | Yes — implement form schema CRUD + submission | `/internal/v1/forms/*` |
| workflow-service | Yes (scaffold) | Yes — implement task assignment engine | `/internal/v1/tasks/*` |
| offline-sync-service | Yes (helm ready) | Yes — implement CRDT merge endpoints | `/internal/v1/sync/*` |
| offline-edge-service | Yes (scaffold) | Yes — implement edge snapshot + reconciliation | `/internal/v1/edge/*` |
| notification-service | Yes (COMPLIANT) | Yes — add mobile push (FCM/APNs) transport | `/internal/v1/push/*` |
| pharmacy-service | Yes (PARTIAL) | Yes — `/internal/v1` route migration | — |
| search-service | Yes (scaffold) | Yes — implement patient search API | `/internal/v1/search/patients` |
| indawo-service | Yes (COMPLIANT) | No | — |
| tshepo-service | Yes (COMPLIANT) | No | — |

#### Docs to Update
- `docs/mobile/shared-foundation-scope.md` (finalize during M1)
- `docs/compliance/full-platform-compliance-matrix.md` (add mobile BFF rows)
- `docs/offline/wave22-offline-pilot.md` (update with mobile offline sync protocol)
- `docs/experience/ONLINE_VERIFICATION.md` (update with mobile flow)

#### Acceptance Artifacts
- `docs/acceptance/mobile-program-acceptance-pack.md` § Provider App
- Provider golden path: patient lookup → visit → vitals → Dx → Rx → close visit
- Outreach golden path: select household → community visit → GPS log → sync
- Offline golden path: airplane mode → capture visit → reconnect → sync → verify server state
- Supervisor golden path: dashboard → KPI review → approve escalation

---

### 3.2 M2 — Citizen / Patient App

**Domains:** Personal, Social, Marketplace, Messaging, Telehealth

#### Feature Areas

| Feature | Description | Primary Screen(s) |
|---------|-------------|--------------------|
| Health Profile | View demographics, conditions, medications, allergies | Profile → Health |
| Visit History | Timeline of past encounters | History → Timeline |
| Appointments | Book, reschedule, cancel appointments | Appointments → Calendar |
| Prescriptions | View active Rx, request refill, track dispensing | Rx → Active → Refill |
| Messages | Secure messaging with providers | Messages → Thread |
| Telehealth | Video/audio consultation | Telehealth → Session |
| Marketplace | Browse health products, coverage-linked purchases | Marketplace → Products |
| Coverage | View coverage status, benefits, claims | Coverage → Benefits |
| Share Slip | Generate and share encounter summary | History → Visit → Share |
| Notifications | Appointment reminders, Rx ready, lab results, messages | Notification tray |
| Consent Management | Grant/revoke data sharing consent | Settings → Consent |

#### Backend Service Dependencies

| Service | Exists | Needs Upgrade | New Endpoints Needed |
|---------|--------|---------------|----------------------|
| experience-bff | Yes | Yes — add citizen-facing aggregation | `/internal/v1/mobile/citizen/*` |
| vito-service | Yes (COMPLIANT) | No | — |
| pct-service | Yes (PARTIAL) | Reuse M1 upgrades | — |
| oros-service | Yes (PARTIAL) | Reuse M1 upgrades | — |
| channels-service | Yes (COMPLIANT) | Yes — add secure messaging + telehealth signaling | `/internal/v1/channels/messages/*`, `/internal/v1/channels/telehealth/*` |
| coverage-service | Yes (COMPLIANT) | Yes — add citizen-facing benefits query | `/internal/v1/coverage/citizen/*` |
| msika-service | Yes (COMPLIANT) | Yes — add marketplace product catalog for citizens | `/internal/v1/msika/marketplace/*` |
| share-slip-service | Yes (PARTIAL) | Yes — `/internal/v1` route migration | — |
| tshepo-consent-service | Yes (PARTIAL) | Yes — `/internal/v1` route migration, mobile consent UI flow | — |
| notification-service | Yes (COMPLIANT) | Reuse M1 push transport | — |
| ubomi-service | Yes (PARTIAL) | Yes — add telehealth session management | `/internal/v1/ubomi/telehealth/*` |
| pharmacy-service | Yes (PARTIAL) | Reuse M1 upgrades, add refill endpoint | `/internal/v1/pharmacy/refill` |

#### Docs to Update
- `docs/compliance/full-platform-compliance-matrix.md` (add citizen BFF routes)
- `docs/experience/ONLINE_VERIFICATION.md` (citizen flows)

#### Acceptance Artifacts
- `docs/acceptance/mobile-program-acceptance-pack.md` § Citizen App
- Personal golden path: login → view profile → view visit history → view Rx
- Messaging golden path: open thread → send message → receive reply
- Telehealth golden path: book appointment → join session → end session → view summary
- Marketplace golden path: browse → select product → check coverage → checkout
- Consent golden path: view consent → revoke → verify data access blocked

---

### 3.3 M3 — Support App

#### Feature Areas

| Feature | Description | Primary Screen(s) |
|---------|-------------|--------------------|
| Ticket Queue | View open/assigned tickets, prioritize | Dashboard → Queue |
| Ticket Detail | View ticket, add notes, escalate, resolve | Queue → Detail |
| Knowledge Base | Search internal KB articles | KB → Search |
| User Lookup | Find user by name/NID, view account status | Search → User |
| Audit Trail | View user action history for incident investigation | User → Audit |
| System Status | Service health dashboard | Status → Services |
| Bulk Actions | Batch ticket reassignment, status update | Queue → Select → Bulk |

#### Backend Service Dependencies

| Service | Exists | Needs Upgrade | New Endpoints Needed |
|---------|--------|---------------|----------------------|
| experience-bff | Yes | Yes — add support aggregation | `/internal/v1/mobile/support/*` |
| support-service | Yes (scaffold) | Yes — implement ticket CRUD, escalation, resolution | `/internal/v1/support/tickets/*` |
| search-service | Yes (scaffold) | Reuse M1 upgrades, add ticket search | `/internal/v1/search/tickets` |
| audit-ledger-service | Yes (scaffold) | Yes — implement audit query for user activity | `/internal/v1/audit/query` |
| vito-service | Yes (COMPLIANT) | No | — |
| notification-service | Yes (COMPLIANT) | Reuse M1+M2 push transport | — |
| tshepo-service | Yes (COMPLIANT) | No | — |

#### Docs to Update
- `docs/compliance/full-platform-compliance-matrix.md` (support-service compliance)

#### Acceptance Artifacts
- `docs/acceptance/mobile-program-acceptance-pack.md` § Support App
- Ticket golden path: create ticket → assign → add note → resolve → verify closed
- Audit golden path: lookup user → view audit trail → correlate with ticket

---

### 3.4 M4 — Developer / Partner App

#### Feature Areas

| Feature | Description | Primary Screen(s) |
|---------|-------------|--------------------|
| API Key Management | Create/rotate/revoke API keys | Dashboard → Keys |
| API Explorer | Interactive API documentation, try-it | Explorer → Endpoint |
| Webhook Management | Register/test/view webhook endpoints | Webhooks → Config |
| Usage Analytics | API call volume, latency, error rates | Analytics → Charts |
| App Registration | Register partner applications | Apps → Register |
| Sandbox | Test environment with mock data | Sandbox → Console |
| Documentation | Inline platform API docs | Docs → Browse |

#### Backend Service Dependencies

| Service | Exists | Needs Upgrade | New Endpoints Needed |
|---------|--------|---------------|----------------------|
| experience-bff | Yes | Yes — add developer portal aggregation | `/internal/v1/mobile/developer/*` |
| developer-portal-service | Yes (scaffold) | Yes — implement key management, app registration, usage stats | `/internal/v1/devportal/*` |
| integration-hub | Yes (COMPLIANT) | Yes — add webhook management endpoints | `/internal/v1/integration/webhooks/*` |
| tshepo-service | Yes (COMPLIANT) | No (API key auth via existing TSHEPO flow) | — |
| notification-service | Yes (COMPLIANT) | Reuse prior push transport | — |

#### Docs to Update
- `docs/compliance/full-platform-compliance-matrix.md` (developer-portal-service compliance)
- `docs/acceptance/developer-platform-acceptance-pack.md` (update with mobile dev app)

#### Acceptance Artifacts
- `docs/acceptance/mobile-program-acceptance-pack.md` § Developer App
- Key management golden path: create key → test call → rotate key → verify old key rejected
- Webhook golden path: register endpoint → trigger event → verify delivery → view logs

---

## 4. Cross-Cutting Concerns (All Waves)

| Concern | Implementation | Verification |
|---------|----------------|--------------|
| v1.1 Trust Headers | `@impilo/mobile-trust` injects all 14 headers on every request | Golden contract test per app |
| Idempotency | `@impilo/mobile-api-client` sends `X-Idempotency-Key` on all mutations | Replay test: same key → 200 not 409 |
| Error Envelope | All errors returned as `ApiEnvelope` with `{code, message, status}` | Negative path in every golden path |
| EventEnvelope / Outbox | Every backend mutation emits outbox event | `SELECT count(*) FROM event_outbox` after golden path |
| Offline Sync | CRDT-based local store with background sync | Airplane mode → capture → reconnect → verify merge |
| Push Notifications | FCM (Android) + APNs (iOS) via notification-service | Send test push → verify receipt |
| Accessibility | WCAG 2.1 AA on all screens | Automated a11y scan per screen |
| Security | Certificate pinning, biometric auth, encrypted local storage | Pen test checklist per app |

---

## 5. Execution Timeline Summary

| Wave | Start Condition | Key Deliverables |
|------|-----------------|------------------|
| M1 | Shared foundation packages created | Provider App + 14 backend upgrades + offline sync protocol |
| M2 | M1 shared foundation stable | Citizen App + channels/telehealth/marketplace upgrades |
| M3 | M1+M2 shared foundation stable | Support App + support-service + audit query |
| M4 | M1–M3 shared foundation stable | Developer App + developer-portal-service |

---

## 6. Exit Criteria for Full Mobile Program

- All 4 apps pass their golden path acceptance tests
- All backend services touched are COMPLIANT in `docs/compliance/full-platform-compliance-matrix.md`
- All docs listed in per-app sections are updated
- `docs/acceptance/mobile-program-acceptance-pack.md` is fully signed off
- No mocks, stubs, or TODOs remain in any app or touched service
