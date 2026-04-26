# ADR-0050 — Unified mobile shell: single app end-state via hybrid migration

## Status

Proposed (Gap 5 — 2026-04-26)

## Context

Impilo mobile currently ships as two separate Expo applications:

- `apps/mobile/citizen-app`
- `apps/mobile/provider-app`

Doctrine explicitly calls out unified mobile shell as a structural requirement:

- `docs/doctrine/doctrine-gap-matrix.md` — “Unified mobile shell” (STRUCTURAL)

Two apps impose ongoing duplication and drift risk across:

- auth lifecycle (Keycloak PKCE, token refresh, secure storage)
- trust header injection (Companion/TSHEPO contract)
- deep links, push notifications, OTA/update channels
- navigation primitives and shared UX patterns
- Maestro smoke coverage and CI wiring

At the same time, a big-bang merge is high risk because it touches release surfaces (bundle IDs, store listings, Keycloak clients) and large navigation trees.

## Decision

Adopt a **single role-adaptive Expo app** as the end-state (“unified mobile shell”), implemented via a **hybrid migration**:

- Commit to **one app** (one shell) with **role switcher / role-adaptive navigation**.
- Execute delivery in **incremental phases** that extract shared foundations first and keep both apps running until the unified shell is stable.

## Non‑negotiables (engineering invariants)

- **One token store**: a single secure token + session persistence implementation shared by all mobile surfaces.
- **One trust header builder**: mobile requests must emit the doctrine header contract consistently.
- **One Provider activation state machine**: “sign in as person; practice as provider only under activated Provider ID”.
- **Deterministic E2E hooks**: stable `testID`s and Maestro flows remain first-class.

## Phased plan

### Phase 0 — Inventory (release surfaces)

Inventory and document deltas between apps:

- Expo identifiers: `apps/mobile/citizen-app/app.config.ts`, `apps/mobile/provider-app/app.config.ts`
- OTA/update channels and runtime config
- deep link schemes and redirect URIs used by Keycloak
- push notification identifiers (if enabled)
- Keycloak OIDC client IDs and callback URLs
- app store listing constraints (name, bundle IDs, review flows)

### Phase 1 — Shared foundations (reduce drift first)

Promote shared packages as single sources of truth:

- `apps/mobile/packages/mobile-auth` for auth + secure token store + Provider activation persistence
- `apps/mobile/packages/mobile-trust` for header contract + injection
- existing shared layers (`mobile-design-system`, `mobile-api-client`) remain the default primitives

Goal: both apps keep shipping, but **auth/trust drift becomes impossible**.

### Phase 2 — Unified shell (role-adaptive navigation)

Create a unified app shell that composes:

- citizen “feature stack”
- provider “feature stack”
- role selector driven by claims + Provider activation state

Key rule: role switcher changes **navigation surface + headers**, not identity.

### Phase 3 — Decommission duplicates

After parity and stability:

- migrate Maestro baseline `appId` to the unified app
- retire redundant Keycloak clients and store listings
- remove duplicated screens last

## Acceptance criteria

- All regulated provider calls include `x-provider-id` when provider is activated.
- Maestro smoke suite remains green during Phase 1 and is upgraded (not rewritten) for Phase 2.
- No divergence in request headers between citizen/provider surfaces.

## Alternatives considered

- **Keep two apps forever; only share a navigation package** — rejected (does not satisfy “unified shell”, continues duplication in auth/trust/push/release).
- **Big-bang merge** — rejected for delivery risk and release-surface coupling.

## Consequences

- Positive: simplifies governance, reduces drift, strengthens doctrine alignment (“one shell”).
- Negative: requires careful handling of bundle IDs, Keycloak clients, and OTA channels during transition.

