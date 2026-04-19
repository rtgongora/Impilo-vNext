# Lovable vs vNext Decision Register

> **Created**: 2026-03-16
> **Purpose**: Documents intentional divergences where vNext chose a different approach than Lovable, with justification

---

## Decision Format

Each entry records:
- **Decision ID**: Sequential identifier
- **Area**: What part of the system
- **Lovable Approach**: What the prototype specified or implied
- **vNext Approach**: What was actually implemented
- **Verdict**: BETTER / ACCEPTABLE / NEEDS_REVIEW
- **Justification**: Why the divergence is appropriate

---

## Decisions

### D-001: Zustand Stores Instead of React Contexts

| Field | Value |
|-------|-------|
| Area | State Management |
| Lovable | 6 React Contexts (from prototype summary) |
| vNext | 4 Zustand stores (auth, facility, workspace, shift) |
| Verdict | **BETTER** |
| Justification | Zustand provides simpler API, better DevTools, no provider nesting hell, built-in persistence support. 4 stores cover the Auth→Facility→Workspace→Shift hierarchy completely. The 2 missing contexts (likely notifications, theme) were not specified in detail and can be added as Zustand stores if needed. |
| Spec Conflict | #7 in SPEC_CONFLICTS.md |

### D-002: BFF Pattern Instead of Supabase Direct

| Field | Value |
|-------|-------|
| Area | API Architecture |
| Lovable | 60 Supabase tables, 30 RPC functions, 30 Edge Functions |
| vNext | Spring Boot BFF with 13 controllers, PostgreSQL, v1.1 conventions |
| Verdict | **BETTER** |
| Justification | BFF pattern provides: (1) Trust header enforcement at server layer, (2) v1.1 API convention compliance, (3) Outbox pattern for reliable eventing, (4) Idempotency enforcement, (5) Proper backend validation. Direct Supabase access would bypass the trust-first architecture that is core to Impilo vNext. |
| Spec Conflict | #5 in SPEC_CONFLICTS.md |

### D-003: Expanded Continuity State Instead of 2 Prototype Keys

| Field | Value |
|-------|-------|
| Area | Session Persistence |
| Lovable | 2 browser persistence keys (names not specified) |
| vNext | Non-secret continuity in browser storage (`exp:auth_user`, `exp:expires_at`, facility/workspace/shift context) plus `exp_has_session` marker cookie and `HttpOnly` refresh cookie |
| Verdict | **BETTER** |
| Justification | The Auth→Facility→Workspace→Shift hierarchy requires continuity at each level, but bearer credentials should not stay readable in browser storage. The current model keeps access tokens in memory, uses browser storage only for non-secret continuity, and uses cookies for session refresh/presence. |
| Spec Conflict | #3 in SPEC_CONFLICTS.md |

### D-004: Simplified Auth Instead of Full Keycloak OIDC

| Field | Value |
|-------|-------|
| Area | Authentication |
| Lovable | 4 auth pathways including SSO and biometric |
| vNext | Stage-1: Email + Provider ID via BFF. Keycloak integration deferred. |
| Verdict | **ACCEPTABLE** |
| Justification | Full Keycloak/OIDC integration requires Keycloak realm configuration, client registration, and token exchange flows that are Stage-2 deliverables. The simplified auth endpoint provides the same UX surface (login form → session token → protected routes) while deferring the identity provider complexity. |
| Spec Conflict | #4 in SPEC_CONFLICTS.md |

### D-005: 3-Zone Sidebar Navigation

| Field | Value |
|-------|-------|
| Area | Navigation Structure |
| Lovable | 11 sidebar contexts (from prototype summary) |
| vNext | 3-zone sidebar (Life, Work, Professional) with context-aware items |
| Verdict | **BETTER** |
| Justification | The 3-zone model (Life: Home/Notifications/Profile/Preferences; Work: Queue/Pharmacy/Inventory/Marketplace/Finance; Professional: Registry/Admin/Reports/Settings) provides a clearer mental model than 11 independent contexts. The sidebar still adapts to the current zone, but within a consistent framework. |

### D-006: TanStack Query for Server State

| Field | Value |
|-------|-------|
| Area | Data Fetching |
| Lovable | Not specified (Supabase client implied) |
| vNext | TanStack Query with custom hooks per domain |
| Verdict | **BETTER** |
| Justification | TanStack Query provides: (1) Automatic caching and deduplication, (2) Background refetch, (3) Optimistic updates for mutations, (4) Loading/error/success state management, (5) Query invalidation on mutations. Each domain has its own hook file (usePatients, useEncounters, useVitals, etc.) following a consistent pattern. |

### D-007: Telemedicine Web Omission

| Field | Value |
|-------|-------|
| Area | Telemedicine |
| Lovable | Implied telemedicine as first-class workflow |
| vNext | Mobile-only telemedicine; no web routes |
| Verdict | **NEEDS_REVIEW** |
| Justification | The 98-route Experience Platform spec does not include telemedicine routes. Mobile apps have full telemedicine support. However, the Lovable prototype may have intended web-based telemedicine as part of the EHR workflow. **Decision**: Not implementing telemedicine web routes in this wave since they were not in the 98-route spec. If needed, they can be added as a new zone. |

### D-008: Route Registry as Source of Truth

| Field | Value |
|-------|-------|
| Area | Route Management |
| Lovable | Site map document with route list |
| vNext | `routes.ts` TypeScript file with full route metadata |
| Verdict | **BETTER** |
| Justification | Having routes defined in TypeScript provides: (1) Type safety, (2) Runtime route resolution, (3) Automated parity checking via `route-parity-check.mjs`, (4) Build-time validation. Documentation derives from code, not vice versa. |

### D-009: v1.1 API Conventions

| Field | Value |
|-------|-------|
| Area | API Contract |
| Lovable | Not specified beyond Supabase client |
| vNext | Full v1.1 convention: 4 mandatory headers, idempotency, error envelope, cursor pagination |
| Verdict | **BETTER** |
| Justification | v1.1 conventions provide enterprise-grade API behavior: trust headers for multi-tenant security, idempotency for safe retries, error envelope for consistent error handling, cursor pagination for large datasets. This is a significant upgrade over direct Supabase access. |

### D-010: Outbox Pattern for Eventing

| Field | Value |
|-------|-------|
| Area | Event Publishing |
| Lovable | Not specified |
| vNext | Outbox table with EventEnvelope schema, Kafka publishing |
| Verdict | **BETTER** |
| Justification | The outbox pattern guarantees exactly-once event delivery even if Kafka is temporarily unavailable. Every state change (encounter created, shift opened, etc.) produces a verified event record. This is critical for a health information exchange. |

---

## Summary

| Verdict | Count |
|---------|-------|
| BETTER | 8 |
| ACCEPTABLE | 1 |
| NEEDS_REVIEW | 1 |
| **Total** | **10** |

The majority of intentional divergences represent genuine improvements over the Lovable prototype's implied architecture. The one item needing review (telemedicine web) is deferred rather than rejected.
