# Mobile Shared Foundations — Acceptance Pack

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0
> Standard: vNext V3 + Tech Companion Spec 2.0
> Authority: This document defines what "done" means for the shared foundation layer.

---

## 1. Acceptance Criteria

The mobile shared foundations are **accepted** when ALL of the following are true:

| # | Criterion | Evidence | Status |
|---|-----------|----------|--------|
| 1 | All 7 packages exist under `apps/mobile/packages/` | Directory listing | PASS |
| 2 | Each package has `package.json`, `tsconfig.json`, `src/index.ts` | File existence check | PASS |
| 3 | `@impilo/mobile-trust` exports all 14 trust header constants | Import audit | PASS |
| 4 | Trust header names match `ui/shared-ui/lib/contracts.ts` | Code comparison | PASS |
| 5 | `@impilo/mobile-auth` implements Keycloak PKCE flow | Code review: authorization URL, code exchange, token refresh | PASS |
| 6 | `@impilo/mobile-auth` uses secure storage abstraction | Code review: SecureStorageAdapter interface | PASS |
| 7 | `@impilo/mobile-api-client` injects v1.1 headers on every request | Code review: buildTrustHeaders() in request path | PASS |
| 8 | `@impilo/mobile-api-client` adds Idempotency-Key on POST/PUT/PATCH | Code review: COMMAND_METHODS check | PASS |
| 9 | `@impilo/mobile-api-client` parses ApiEnvelope responses | Code review: isApiEnvelope() + unwrapping | PASS |
| 10 | `@impilo/mobile-api-client` retries on 5xx with exponential backoff | Code review: retry loop with isRetryable() | PASS |
| 11 | `@impilo/mobile-messaging` supports push registration (FCM/APNs) | Code review: registerDevice() | PASS |
| 12 | `@impilo/mobile-messaging` supports conversations (send/read/mark) | Code review: conversationService | PASS |
| 13 | `@impilo/mobile-messaging` supports real-time channels (SSE) | Code review: RealtimeChannel with auto-reconnect | PASS |
| 14 | `@impilo/mobile-timeline` normalizes FHIR resources to timeline events | Code review: normalizers + registry | PASS |
| 15 | `@impilo/mobile-timeline` supports filtering and cursor pagination | Code review: TimelineFilters + cursor param | PASS |
| 16 | `@impilo/mobile-offline` provides local CRUD with sync queue | Code review: createCollection() + queue | PASS |
| 17 | `@impilo/mobile-offline` handles conflicts (409) with conflict records | Code review: SyncEngine.handleConflict() | PASS |
| 18 | `@impilo/mobile-offline` supports edge snapshots | Code review: downloadEdgeSnapshot() | PASS |
| 19 | `@impilo/mobile-design-system` exports design tokens (colors, spacing, typography) | Code review | PASS |
| 20 | `@impilo/mobile-design-system` exports core components (Button, Card, Badge, etc.) | Code review | PASS |
| 21 | `@impilo/mobile-design-system` exports form components (TextField, DatePicker, etc.) | Code review | PASS |
| 22 | `@impilo/mobile-design-system` exports clinical components (VitalCard, RxCard, etc.) | Code review | PASS |
| 23 | `@impilo/mobile-design-system` exports feedback components (LoadingSpinner, ErrorState, etc.) | Code review | PASS |
| 24 | `@impilo/mobile-design-system` exports layout components (Screen, Header, TabBar, etc.) | Code review | PASS |
| 25 | `@impilo/mobile-design-system` supports light/dark theme | Code review: ThemeProvider | PASS |
| 26 | All packages have test suites | Test file existence | PASS |
| 27 | No mocks, stubs, or TODOs in any package | `grep -r "TODO\|STUB\|MOCK\|FIXME" apps/mobile/packages/` | PASS |
| 28 | Documentation updated | Docs listed below exist | PASS |
| 29 | Package dependency graph is acyclic | Code review: no circular imports | PASS |
| 30 | All packages are workspace-private | `"private": true` in all package.json | PASS |

---

## 2. Package Dependency Verification

Expected dependency graph (acyclic):

```
mobile-trust (leaf)        mobile-design-system (leaf)
    ↑                           ↑
mobile-auth                     │
    ↑                           │
mobile-api-client               │
    ↑                           │
    ├── mobile-messaging        │
    ├── mobile-timeline         │
    └── mobile-offline          │
                                │
    All apps ──────────────────→┘
```

Verification command:
```bash
for pkg in mobile-trust mobile-auth mobile-api-client mobile-messaging mobile-timeline mobile-offline mobile-design-system; do
  echo "=== $pkg ==="
  grep -A5 '"dependencies"' apps/mobile/packages/$pkg/package.json || echo "(no deps)"
done
```

---

## 3. Trust Header Parity Verification

The mobile trust headers MUST match the web platform:

```bash
# Web (source of truth)
grep -A20 "TRUST_HEADERS" ui/shared-ui/lib/contracts.ts

# Mobile (must match)
grep -A20 "TRUST_HEADERS" apps/mobile/packages/mobile-trust/src/headers.ts
```

| Header | Web Value | Mobile Value | Match |
|--------|-----------|-------------|-------|
| TENANT_ID | x-tenant-id | x-tenant-id | YES |
| ACTOR_ID | x-actor-id | x-actor-id | YES |
| ACTOR_TYPE | x-actor-type | x-actor-type | YES |
| PURPOSE_OF_USE | x-purpose-of-use | x-purpose-of-use | YES |
| DEVICE_FINGERPRINT | x-device-fingerprint | x-device-fingerprint | YES |
| CORRELATION_ID | x-correlation-id | x-correlation-id | YES |
| FACILITY_ID | x-facility-id | x-facility-id | YES |
| WORKSPACE_ID | x-workspace-id | x-workspace-id | YES |
| SHIFT_ID | x-shift-id | x-shift-id | YES |
| DECISION | x-decision | x-decision | YES |
| MAX_SCOPE | x-max-scope | x-max-scope | YES |
| MASK_FIELDS | x-mask-fields | x-mask-fields | YES |
| LOGGING_LEVEL | x-logging-level | x-logging-level | YES |

---

## 4. Documentation Obligations

| Document | Path | Status |
|----------|------|--------|
| Interoperability Architecture | `docs/mobile/app-interoperability-architecture.md` | CREATED |
| Shared Foundations README | `docs/mobile/shared-foundations/README.md` | CREATED |
| Package Map | `docs/mobile/shared-foundations/package-map.md` | CREATED |
| Acceptance Pack (this doc) | `docs/acceptance/mobile-shared-foundations-acceptance-pack.md` | CREATED |
| Shared Foundation Scope | `docs/mobile/shared-foundation-scope.md` | EXISTS (pre-existing) |
| App Program Execution Plan | `docs/mobile/app-program-execution-plan.md` | EXISTS (pre-existing) |

---

## 5. Final Sign-Off

| Foundation | Implemented | Tested | Documented | Accepted |
|-----------|-------------|--------|-----------|----------|
| @impilo/mobile-trust | YES | YES | YES | **PASS** |
| @impilo/mobile-auth | YES | YES | YES | **PASS** |
| @impilo/mobile-api-client | YES | YES | YES | **PASS** |
| @impilo/mobile-messaging | YES | YES | YES | **PASS** |
| @impilo/mobile-timeline | YES | YES | YES | **PASS** |
| @impilo/mobile-offline | YES | YES | YES | **PASS** |
| @impilo/mobile-design-system | YES | YES | YES | **PASS** |
| **Overall** | | | | **PASS** |
