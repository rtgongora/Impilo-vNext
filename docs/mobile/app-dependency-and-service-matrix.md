# Mobile App — Dependency & Service Matrix

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0
> Standard: vNext V3 + Tech Companion Spec 2.0

---

## Reading Guide

| Column | Meaning |
|--------|---------|
| **App** | Which mobile app requires this feature |
| **Feature Area** | Functional domain within the app |
| **Backend Service** | Platform service that backs this feature |
| **Service Exists** | Whether `services/<name>` has code beyond scaffold |
| **Compliance Status** | Current status from `docs/compliance/full-platform-compliance-matrix.md` |
| **Action Required** | NONE / UPGRADE (add routes, fix compliance) / NEW (implement from scaffold) |
| **Specific Work** | What exactly must be done |
| **Docs to Update** | Which platform docs must be modified |
| **Acceptance Artifact** | Which acceptance pack section must be updated |

---

## M1 — Provider App

| Feature Area | Backend Service | Service Exists | Compliance Status | Action Required | Specific Work | Docs to Update | Acceptance Artifact |
|---|---|---|---|---|---|---|---|
| Mobile BFF Aggregation | experience-bff | Yes | COMPLIANT | DONE | ✅ 15 mobile provider controllers implemented (`/internal/v1/mobile/provider/*`); v1.1 header enforcement; outbox; idempotency | compliance-matrix | provider-app-acceptance-pack |
| Patient Lookup | vito-service | Yes | COMPLIANT | NONE | — | — | — |
| Patient Lookup | search-service | Yes (scaffold) | N/A | NEW | Implement patient search with fuzzy matching, NID lookup, QR decode | compliance-matrix | mobile-acceptance § Provider |
| Facility Context | indawo-service | Yes | COMPLIANT | NONE | — | — | — |
| Clinical Visit — Vitals | pct-service | Yes | PARTIAL | UPGRADE | Migrate legacy `/v1/` routes to `/internal/v1/`; add vitals batch endpoint | compliance-matrix | mobile-acceptance § Provider |
| Clinical Visit — Diagnosis | oros-service | Yes | PARTIAL | UPGRADE | Migrate legacy `/v1/` routes to `/internal/v1/`; add ICD-11 search endpoint | compliance-matrix | mobile-acceptance § Provider |
| Clinical Visit — Rx | pharmacy-service | Yes | PARTIAL | UPGRADE | Migrate legacy routes to `/internal/v1/`; add mobile Rx creation flow | compliance-matrix | mobile-acceptance § Provider |
| Dynamic Forms | forms-service | Yes (scaffold) | N/A | NEW | Implement form schema CRUD, form rendering API, submission + validation | compliance-matrix | mobile-acceptance § Provider |
| Task Board | workflow-service | Yes (scaffold) | N/A | NEW | Implement task assignment, status transitions, escalation engine | compliance-matrix | mobile-acceptance § Provider |
| Outreach — GPS | tuso-service | Yes | COMPLIANT | NONE | — (location/facility already available) | — | — |
| Outreach — Household | vito-service | Yes | COMPLIANT | UPGRADE | Add household-level grouping endpoint | — | mobile-acceptance § Provider |
| Offline Sync | offline-sync-service | Yes (helm) | N/A | NEW | Implement CRDT merge protocol, conflict resolution, sync status API | compliance-matrix, offline docs | mobile-acceptance § Provider |
| Offline Edge | offline-edge-service | Yes (scaffold) | N/A | NEW | Implement edge snapshot generation, reconciliation, rollback | compliance-matrix, offline docs | mobile-acceptance § Provider |
| Notifications — Push | notification-service | Yes | COMPLIANT | UPGRADE | Add FCM/APNs push transport, device registration endpoint | compliance-matrix | mobile-acceptance § Provider |
| Auth & Session | tshepo-service | Yes | COMPLIANT | NONE | — | — | — |
| Auth — Offline Token | tshepo-offline-service | Yes | PARTIAL | UPGRADE | Migrate to `/internal/v1/`; ensure offline token issuance flow works for mobile | compliance-matrix | mobile-acceptance § Provider |
| Referrals | ubomi-service | Yes | PARTIAL | UPGRADE | Migrate to `/internal/v1/`; add referral creation endpoint | compliance-matrix | mobile-acceptance § Provider |
| Lab Orders | butano-service | Yes | PARTIAL | UPGRADE | Migrate to `/internal/v1/`; add lab order + results query | compliance-matrix | mobile-acceptance § Provider |

---

## M2 — Citizen / Patient App

| Feature Area | Backend Service | Service Exists | Compliance Status | Action Required | Specific Work | Docs to Update | Acceptance Artifact |
|---|---|---|---|---|---|---|---|
| Mobile BFF — Citizen | experience-bff | Yes | N/A | UPGRADE | Add citizen aggregation routes (`/internal/v1/mobile/citizen/*`) | compliance-matrix | mobile-acceptance § Citizen |
| Health Profile | vito-service | Yes | COMPLIANT | NONE | — | — | — |
| Visit History | pct-service | Yes | PARTIAL | NONE (reuse M1) | — | — | — |
| Visit History | oros-service | Yes | PARTIAL | NONE (reuse M1) | — | — | — |
| Appointments | workflow-service | Yes (scaffold) | N/A | UPGRADE | Add appointment booking, reschedule, cancel (extends M1 task engine) | — | mobile-acceptance § Citizen |
| Prescriptions | pharmacy-service | Yes | PARTIAL | UPGRADE | Add citizen-facing refill request endpoint | — | mobile-acceptance § Citizen |
| Secure Messaging | channels-service | Yes | COMPLIANT | UPGRADE | Add secure patient–provider messaging: thread CRUD, read receipts, attachments | compliance-matrix | mobile-acceptance § Citizen |
| Telehealth | channels-service | Yes | COMPLIANT | UPGRADE | Add WebRTC signaling for video/audio sessions | compliance-matrix | mobile-acceptance § Citizen |
| Telehealth — Session | ubomi-service | Yes | PARTIAL | UPGRADE | Add telehealth session lifecycle management (book → join → end → summary) | compliance-matrix | mobile-acceptance § Citizen |
| Marketplace | msika-service | Yes | COMPLIANT | UPGRADE | Add citizen-facing product catalog, coverage-linked checkout | compliance-matrix | mobile-acceptance § Citizen |
| Coverage / Benefits | coverage-service | Yes | COMPLIANT | UPGRADE | Add citizen-facing benefits query, claims history | — | mobile-acceptance § Citizen |
| Share Slip | share-slip-service | Yes | PARTIAL | UPGRADE | Migrate to `/internal/v1/`; add mobile QR/PDF generation | compliance-matrix | mobile-acceptance § Citizen |
| Consent Management | tshepo-consent-service | Yes | PARTIAL | UPGRADE | Migrate to `/internal/v1/`; add mobile consent grant/revoke flow | compliance-matrix | mobile-acceptance § Citizen |
| Notifications | notification-service | Yes | COMPLIANT | NONE (reuse M1) | — | — | — |

---

## M3 — Support App ✅ IMPLEMENTED

| Feature Area | Backend Service | Service Exists | Compliance Status | Action Required | Specific Work | Docs to Update | Acceptance Artifact |
|---|---|---|---|---|---|---|---|
| Dashboard Stats | support-service | Yes | COMPLIANT | DONE | ✅ GET /internal/v1/support/dashboard/stats — ticket volume, SLA metrics | compliance-matrix | support-app-acceptance-pack |
| Ticket CRUD | support-service | Yes | COMPLIANT | DONE | ✅ Full ticket lifecycle: create, update, list with filters (status, priority, category, assignee) | compliance-matrix | support-app-acceptance-pack |
| Ticket Comments | support-service | Yes | COMPLIANT | DONE | ✅ POST/GET /internal/v1/support/tickets/{id}/comments — internal comment threads | compliance-matrix | support-app-acceptance-pack |
| Ticket Assignment | support-service | Yes | COMPLIANT | DONE | ✅ POST/GET /internal/v1/support/tickets/{id}/assign — assignment with history | compliance-matrix | support-app-acceptance-pack |
| Ticket Escalation | support-service | Yes | COMPLIANT | DONE | ✅ POST /internal/v1/support/tickets/{id}/escalate — level-based with auto-priority at L3 | compliance-matrix | support-app-acceptance-pack |
| Ticket Messages | support-service | Yes | COMPLIANT | DONE | ✅ POST/GET /internal/v1/support/tickets/{id}/messages — AGENT/REQUESTER/SYSTEM threads | compliance-matrix | support-app-acceptance-pack |
| Knowledge Base | support-service | Yes | COMPLIANT | DONE | ✅ Article CRUD with DRAFT→PUBLISHED→ARCHIVED lifecycle | compliance-matrix | support-app-acceptance-pack |
| Incident Queue | support-service | Yes | COMPLIANT | DONE | ✅ Tickets with category=INCIDENT + escalation queue | compliance-matrix | support-app-acceptance-pack |
| Diagnostics | support-service | Yes | COMPLIANT | DONE | ✅ correlation_id/request_id linkage to observability | compliance-matrix | support-app-acceptance-pack |
| User Lookup | vito-service | Yes | COMPLIANT | NONE | — | — | — |
| Notifications | notification-service | Yes | COMPLIANT | NONE (reuse M1) | — | — | — |

---

## M4 — Developer / Partner App

| Feature Area | Backend Service | Service Exists | Compliance Status | Action Required | Specific Work | Docs to Update | Acceptance Artifact |
|---|---|---|---|---|---|---|---|
| Mobile BFF — Developer | experience-bff | Yes | N/A | UPGRADE | Add developer portal aggregation (`/internal/v1/mobile/developer/*`) | compliance-matrix | mobile-acceptance § Developer |
| API Key Management | developer-portal-service | Yes (scaffold) | N/A | NEW | Implement key create/rotate/revoke, scoped permissions | compliance-matrix | mobile-acceptance § Developer |
| App Registration | developer-portal-service | Yes (scaffold) | N/A | NEW | Implement partner app registration, OAuth client provisioning | compliance-matrix | mobile-acceptance § Developer |
| Usage Analytics | developer-portal-service | Yes (scaffold) | N/A | NEW | Implement API call metrics aggregation, latency percentiles, error rates | compliance-matrix | mobile-acceptance § Developer |
| Webhook Management | integration-hub | Yes | COMPLIANT | UPGRADE | Add webhook registration, delivery logs, retry config endpoints | — | mobile-acceptance § Developer |
| API Explorer | developer-portal-service | Yes (scaffold) | N/A | NEW | Implement OpenAPI spec serving, interactive try-it proxy | compliance-matrix | mobile-acceptance § Developer |
| Sandbox | developer-portal-service | Yes (scaffold) | N/A | NEW | Implement isolated test environment provisioning with seed data | compliance-matrix | mobile-acceptance § Developer |
| Documentation | developer-portal-service | Yes (scaffold) | N/A | UPGRADE | Serve platform API docs as structured content | — | mobile-acceptance § Developer |
| Notifications | notification-service | Yes | COMPLIANT | NONE (reuse M1) | — | — | — |

---

## Summary Statistics

| Metric | Count |
|--------|-------|
| Total feature areas across all apps | 48 |
| Distinct backend services touched | 22 |
| Services requiring NEW implementation | 8 (experience-bff, search-service, forms-service, workflow-service, offline-sync-service, offline-edge-service, support-service, developer-portal-service + audit-ledger-service) |
| Services requiring UPGRADE | 14 |
| Services requiring NO changes | 4 (vito-service, tuso-service, indawo-service, tshepo-service) |
| Compliance matrix rows to add/update | 9+ |
| Acceptance pack sections to produce | 4 (one per app) |
