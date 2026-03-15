# Mobile App-Led Delivery Rules

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0
> Standard: vNext V3 + Tech Companion Spec 2.0
> Authority: This document is binding for all mobile app wave merges.

---

## 1. Purpose

These rules ensure that every mobile app delivered in the Impilo vNext mobile program is a **complete vertical slice** — from UI through BFF to backend service to database — with no mocks, stubs, or deferred work.

---

## 2. Merge Gate Rules

### Rule 1: No App May Be Merged Without Its Backend Dependencies

- Every feature in the app must call a **real, deployed backend endpoint**.
- The endpoint must be implemented in the corresponding service under `services/`.
- The endpoint must comply with v1.1 header enforcement, idempotency, outbox pattern, and error envelope.
- **Evidence required:** Golden path test that exercises the full request chain from mobile UI → experience-bff → target service → database → outbox event.
- **Violation:** PR blocked. No exceptions.

### Rule 2: No App May Be Merged Without Documentation Updates

- Every service touched by the app wave must have its entry in `docs/compliance/full-platform-compliance-matrix.md` updated to reflect current compliance status.
- Any new service endpoints must be documented in the service's own README or API spec.
- Architecture decisions must be recorded if they deviate from existing patterns.
- **Evidence required:** Diff in PR includes doc changes for every service modified.
- **Violation:** PR blocked.

### Rule 3: No App May Be Merged Without Acceptance Pack Updates

- The app's section in `docs/acceptance/mobile-program-acceptance-pack.md` must be complete and passing.
- Each golden path must have:
  - Step-by-step description
  - Expected outcome at each step
  - Verification command or query
  - Pass/fail status
- **Evidence required:** Acceptance pack section marked as PASS with verification evidence.
- **Violation:** PR blocked.

### Rule 4: No App May Be Merged Without Shared Foundation Reuse

- Apps must use shared packages from `@impilo/mobile-*` (see `docs/mobile/shared-foundation-scope.md`).
- No app may re-implement auth, API client, trust header injection, offline sync, or design system primitives.
- If a shared package is missing functionality, the package must be extended — not bypassed.
- **Evidence required:** Import audit showing shared package usage for auth, API, trust headers, offline, and design system.
- **Violation:** PR blocked.

---

## 3. Vertical Slice Definition

A feature is considered a **complete vertical slice** when ALL of the following are true:

| Layer | Requirement |
|-------|-------------|
| **Mobile UI** | Screen implemented with real navigation, real data binding, error states, loading states |
| **Shared Foundation** | Uses `@impilo/mobile-auth`, `@impilo/mobile-api-client`, `@impilo/mobile-trust`, `@impilo/mobile-design-system` |
| **Experience BFF** | Aggregation route exists in `experience-bff` under `/internal/v1/mobile/<app>/*` |
| **Target Service** | Backend service endpoint exists, passes golden contract test |
| **Database** | Migration exists, schema matches domain model |
| **Outbox** | `event_outbox` row emitted for every mutation |
| **v1.1 Compliance** | 4-header enforcement active, idempotency on commands, error envelope on all responses |
| **Tests** | Unit tests for service logic, integration test for BFF→service, golden path end-to-end |

---

## 4. Prohibited Patterns

| Pattern | Why It Is Banned | What to Do Instead |
|---------|------------------|--------------------|
| Mock API responses in app code | Hides integration failures | Implement real BFF route |
| `// TODO: implement later` | Defers work indefinitely | Implement now or remove feature from scope |
| Stub services returning hardcoded data | Masks schema/contract mismatches | Implement real service logic with DB |
| Feature flags hiding incomplete features | Ships broken code to production | Complete the feature or defer the entire slice |
| Direct service calls bypassing BFF | Breaks aggregation and auth layer | Route through experience-bff |
| Custom auth/token logic in app | Duplicates shared foundation | Use `@impilo/mobile-auth` |
| Inline trust header construction | Error-prone, contract drift | Use `@impilo/mobile-trust` |
| Local-only data without sync | Data loss on device change | Use `@impilo/mobile-offline` with CRDT sync |

---

## 5. Wave Dependency Rules

| Wave | Prerequisite |
|------|-------------|
| M1 (Provider) | Shared foundation packages must be created and published to workspace |
| M2 (Citizen) | M1 shared foundation stable; all M1 backend upgrades merged |
| M3 (Support) | M1 + M2 shared foundation stable; support-service implemented |
| M4 (Developer) | M1–M3 shared foundation stable; developer-portal-service implemented |

- No wave may start implementation until its prerequisite wave's shared foundation is stable.
- Backend services created in an earlier wave are available to later waves without re-implementation.
- If a later wave discovers a gap in an earlier wave's backend, the fix is made in the earlier service and the compliance matrix is updated.

---

## 6. Compliance Posture for All Mobile Work

All mobile app work must maintain the vNext V3 + Tech Companion Spec 2.0 posture:

| Requirement | Standard |
|-------------|----------|
| Trust Headers | v1.1 — all 14 headers per `ui/shared-ui/lib/contracts.ts` and `TrustHeaders.java` |
| Idempotency | `X-Idempotency-Key` on all POST/PUT/PATCH mutations |
| Error Envelope | `ApiEnvelope<T>` with `{success, data, error{code,message,status}, correlationId, timestamp}` |
| Event Outbox | Every mutation emits to `event_outbox` with v1.1 columns (tenant_id, pod_id, request_id, correlation_id, idempotency_key) |
| EventEnvelope | Kafka events use `EventEnvelope` wrapper with causation chain |
| Federation | Services that participate in federation must enforce `FederationAuthority` |
| Consistency | Services declare consistency class (STRONG, BOUNDED, EVENTUAL) per action |

---

## 7. Enforcement

- CI pipeline must include a **vertical slice check** that verifies:
  - No `TODO`, `FIXME`, `STUB`, or `MOCK` tokens in merged app code
  - All imports from `@impilo/mobile-*` resolve to real packages
  - Compliance matrix has been modified in the PR diff
  - Acceptance pack has been modified in the PR diff
- Code review checklist must include all 4 merge gate rules above.
- Wave lead signs off on acceptance pack before merge.
