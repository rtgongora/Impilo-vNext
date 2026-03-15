# Mobile Shared Foundations

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0

---

## Overview

The mobile shared foundations are a set of 7 workspace packages under `apps/mobile/packages/` that provide cross-cutting capabilities to all 4 mobile apps (Provider, Citizen, Support, Developer).

These packages are built once and reused across all apps. No app may re-implement functionality provided by a shared package.

---

## Packages

| # | Package | Purpose | Dependencies |
|---|---------|---------|-------------|
| 1 | `@impilo/mobile-trust` | Trust header constants, types, header builder | None (leaf) |
| 2 | `@impilo/mobile-design-system` | Design tokens, UI components, layout, forms, clinical, feedback | None (leaf) |
| 3 | `@impilo/mobile-auth` | Keycloak PKCE, token lifecycle, session store, biometric unlock | mobile-trust |
| 4 | `@impilo/mobile-api-client` | HTTP client, v1.1 headers, retry, idempotency, envelope parsing | mobile-trust, mobile-auth |
| 5 | `@impilo/mobile-offline` | Local persistence, operation queue, sync engine, conflict resolution | mobile-trust, mobile-api-client |
| 6 | `@impilo/mobile-messaging` | Push notifications, inbox, conversations, real-time channels | mobile-trust, mobile-api-client |
| 7 | `@impilo/mobile-timeline` | Event normalization, timeline feed, filtering, deep links | mobile-trust, mobile-api-client |

---

## Quick Start

```typescript
// 1. Configure trust and auth (app bootstrap)
import { configureAuth } from '@impilo/mobile-auth';
import { configureApiClient } from '@impilo/mobile-api-client';
import { configureOfflineStorage, MemoryStorageAdapter } from '@impilo/mobile-offline';

configureAuth({
  realm: 'impilo',
  clientId: 'provider-mobile',
  baseUrl: 'https://auth.impilo.gov.zw',
  redirectUri: 'impilo-provider://callback',
});

configureApiClient({
  baseUrl: 'https://api.impilo.gov.zw',
  defaultTimeoutMs: 30000,
  maxRetries: 3,
});

configureOfflineStorage(new MemoryStorageAdapter());

// 2. Use hooks in components
import { useAuth, useSession } from '@impilo/mobile-auth';
import { useTimeline } from '@impilo/mobile-timeline';
import { useNotifications } from '@impilo/mobile-messaging';
import { useOfflineStore, useSyncEngine } from '@impilo/mobile-offline';
import { Button, Card, VitalCard, LoadingSpinner } from '@impilo/mobile-design-system';
```

---

## Build Order

```
1. mobile-trust          (leaf — no dependencies)
2. mobile-design-system  (leaf — no dependencies)
3. mobile-auth           (depends on trust)
4. mobile-api-client     (depends on trust + auth)
5. mobile-offline        (depends on trust + api-client)
6. mobile-messaging      (depends on trust + api-client)
7. mobile-timeline       (depends on trust + api-client)
```

---

## Testing

Each package has its own test suite under `test/` using Vitest:

```bash
# Run all tests for a package
cd apps/mobile/packages/mobile-trust && npx vitest run

# Run all package tests
for pkg in mobile-trust mobile-auth mobile-api-client mobile-messaging mobile-timeline mobile-offline mobile-design-system; do
  (cd apps/mobile/packages/$pkg && npx vitest run)
done
```

---

## Related Docs

- [Package Map](./package-map.md) — Detailed file-by-file listing of every package
- [Shared Foundation Scope](../shared-foundation-scope.md) — Original specification
- [App Interoperability Architecture](../app-interoperability-architecture.md) — How apps interoperate
- [Mobile Program Acceptance Pack](../../acceptance/mobile-shared-foundations-acceptance-pack.md) — Acceptance criteria
