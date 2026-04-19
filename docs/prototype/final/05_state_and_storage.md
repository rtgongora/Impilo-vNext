# 05 — State & Storage (Index & Contract)

> **Document type**: Index document — points to canonical spec sources for state management and storage.
> **Last updated**: 2026-03-11
> **Status**: CANONICAL INDEX — enforced by `scripts/spec-integrity-check.sh`

---

## Purpose

This document indexes the state management and storage architecture for the Experience Platform. The implementation artifacts are the source of truth; this file maps the architecture to its canonical locations.

## Summary Constraints (from original prototype)

- Provider tree: QueryClient → Auth → Facility → Workspace → Shift → Router
- 6 React contexts (original spec) → **4 Zustand stores** (implementation — see Conflict #7)
- 2 sessionStorage keys (original spec) → implementation now uses a small sessionStorage set plus an `HttpOnly` refresh cookie (see Conflict #3)
- 10 role-checking database functions for authorization
- Capability gating controls feature visibility

## Canonical Sources

### Client State Management (Zustand Stores)

4 Zustand stores implementing the hierarchical tenancy model:

| Store | Location | State Shape | Purpose |
|-------|----------|------------|---------|
| `authStore` | `ui/experience/src/hooks/useAuthStore.ts` | `{ token, user, isAuthenticated }` | Authentication session |
| `facilityStore` | `ui/experience/src/stores/facility.ts` | `{ facility, facilities }` | Selected facility context |
| `workspaceStore` | `ui/experience/src/stores/workspace.ts` | `{ workspace, workspaces }` | Active workspace |
| `shiftStore` | `ui/experience/src/stores/shift.ts` | `{ shift, isShiftOpen }` | Current shift state |

Hierarchy chain: Auth → Facility → Workspace → Shift (each level gates the next)

### Server State Management (TanStack Query)

- **QueryClient** wraps the entire provider tree
- Cache invalidation patterns per endpoint (stale-while-revalidate)
- Mutations with optimistic updates for critical paths (encounter creation, queue updates)
- API client: `ui/experience/src/lib/api-client.ts` — injects trust headers on every request

### Session Storage & Cookie Keys

Experience now persists non-secret continuity in sessionStorage and keeps the refresh credential in an `HttpOnly` cookie:

| Key | Purpose |
|-----|---------|
| `exp:auth_user` | Authenticated user identity |
| `exp:expires_at` | Access token expiry metadata |
| `exp:facility` | Currently selected facility |
| `exp:workspace` | Active workspace |
| `exp:shift` | Current shift state |
| `exp_has_session` cookie | Non-secret session presence marker for route gating |
| `exp_refresh_token` cookie (`HttpOnly`) | Refresh credential, not readable by browser JavaScript |

### Route Guard Chain

Guards enforce the hierarchical context requirement:

| Guard | Requires | Redirects To |
|-------|----------|-------------|
| `auth` | Valid session marker, then BFF-backed session recovery | `/auth/login` |
| `facility` | Auth + selected facility | `/facility` |
| `workspace` | Facility + active workspace | `/workspace` |
| `shift` | Workspace + open shift | `/shift` |
| `role` | Shift + specific role | `/home` (with error toast) |

Guard definitions: `ui/experience/src/lib/routes.ts` (per-route `guard` field)

### Server-Side Storage

| Storage Layer | Technology | Purpose |
|--------------|-----------|---------|
| Primary database | PostgreSQL 16 | BFF domain tables, outbox, idempotency store |
| Session store | In-memory (BFF) | Server-side session validation |
| Event outbox | `event_outbox` table | Reliable Kafka publishing (outbox pattern) |
| Idempotency store | `idempotency_store` table | Request deduplication |

Database migrations: `services/experience-bff/src/main/resources/db/migration/`

### Architectural Spec References

| Canonical File | What It Provides |
|----------------|-----------------|
| [EVENTING_AND_TOPICS.md](../../plan/EVENTING_AND_TOPICS.md) | Outbox pattern, EventEnvelope schema, Kafka topic naming |
| [API_CONVENTIONS_V11.md](../../plan/API_CONVENTIONS_V11.md) | Idempotency contract, header-based tenant/pod context |
| [SERVICE_CATALOG.md](../../plan/SERVICE_CATALOG.md) | BFF database schema name and service definition |
| [IMPILO_VNEXT_BUILD_PLAN.md](../../plan/IMPILO_VNEXT_BUILD_PLAN.md) | Constraint C6: each service gets its own PostgreSQL database |
| [TESTING_CONVENTIONS.md](../../plan/TESTING_CONVENTIONS.md) | TestContainers patterns for database integration tests |
| [06-consistency-classes.md](../../architecture/v1.1/06-consistency-classes.md) | Class A/B/C consistency guarantees for storage operations |
| [05-event-schema-template.md](../../architecture/v1.1/05-event-schema-template.md) | Outbox event envelope field requirements |
| [experience-platform-acceptance-pack.md](../../acceptance/experience-platform-acceptance-pack.md) | Storage verification through outbox proof queries |

## Spec Conflicts

- **Conflict #3** (from [compose/experience/SPEC_CONFLICTS.md](../../../compose/experience/SPEC_CONFLICTS.md)): Original spec mentioned "2 sessionStorage keys" without specifying names. Implementation now keeps non-secret continuity in sessionStorage and uses cookies for session markers / refresh.
- **Conflict #7**: Original spec mentioned "6 React contexts." Implementation uses 4 Zustand stores covering the Auth→Facility→Workspace→Shift hierarchy. Two additional contexts (possibly notifications, theme) were not implemented due to insufficient specification.

## Contract Statement

> The Zustand stores, route guards, and session storage keys in the Experience UI source code are the source of truth for client-side state management. The PostgreSQL migrations in the BFF are the source of truth for server-side storage schema. The outbox and idempotency patterns follow `EVENTING_AND_TOPICS.md` and `API_CONVENTIONS_V11.md` respectively.
