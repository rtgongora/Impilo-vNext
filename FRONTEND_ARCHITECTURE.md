# Frontend Architecture (Doctrine-Aligned)

## Scope and canonical references

This architecture is aligned to the current doctrine and runtime references in:

- `docs/doctrine/*` (Health OS, core transaction, person-first, no-fake-capability)
- `docs/architecture/*` and `docs/architecture/planes/*` (seven-plane mapping, BFF/trust routing)
- `docs/registry/*` (service ownership, forbidden responsibilities, wiring map)
- `docs/audits/*` (reality checks, parity, fixture and placeholder registers)
- `docs/mobile/*`, `docs/offline/*`, `docs/runtime*/*` (mobile parity, offline and validation constraints)
- `docs/privacy/*`, `docs/security/*`, `docs/governance/*`, `docs/integration*/*`, `docs/federation/*`, `docs/ux/*`

## System shape

- **Web shell:** `ui/one-ui-shell` (canonical route registry and journey mapping)
- **Mobile shells:** `apps/mobile/citizen-app` and `apps/mobile/provider-app`
- **Shared mobile packages:** `apps/mobile/packages/*` including trust, api-client, auth, offline, design-system, nompilo, messaging, ndila, integration
- **Backend orchestration edge:** Experience BFF (`/internal/v1/...`) as the composition layer

## Request path and trust model

All production paths follow this chain:

1. UI surface (web/mobile)
2. typed service/hook client
3. Experience BFF route
4. sovereign/shared domain service
5. canonical contract and event surfaces

Trust and context propagation requirements:

- Mandatory trust IDs: tenant, pod, request, correlation
- Actor/governance: actor, actor type, provider, purpose-of-use, assurance, access-mode
- Duty context: facility, department, ward, workspace, programme, shift
- Command idempotency: idempotency-key for command methods

## Frontend layering rules

- **Person-first before role-first:** person anchor is primary identity; provider mode is activated context
- **No shadow source-of-truth:** frontend and BFF compose, they do not become clinical/registry/payment SoR
- **Contract-first:** use canonical contracts and generated/typed DTOs where available
- **No fake capability:** every surface must expose `live`, `connected`, `partial`, `fixture`, or `not_wired` maturity honestly
- **Offline/provisional aware:** show pending sync, failed sync, conflicts, reconciliation and verified vs provisional states explicitly

## Journey and plane mapping model

- **Journeys:** Person/Client, Provider, Platform/Back-of-House, Cross-cutting
- **Planes:** Trust, Registry, Clinical, Data & Intelligence, Integration & Edge, Experience & Orchestration, Enterprise Resource & Market Operations
- Route and screen owners must declare journey and plane mapping in parity docs before being marked complete.

## Runtime composition points

- **Web route registry:** `ui/one-ui-shell/src/lib/routes.ts` (`EXPECTED_ROUTE_COUNT = 346`)
- **Journey grouping:** `ui/one-ui-shell/src/lib/ui-route-journey-map.ts`
- **Web trust injection:** `ui/one-ui-shell/src/lib/api-client.ts`
- **Mobile trust injection:** `apps/mobile/packages/mobile-trust`
- **Mobile API gateway client:** `apps/mobile/packages/mobile-api-client`

## Nompilo experience-layer placement

- Nompilo is global and contextual (role, workflow, permission, trust, accessibility aware)
- Mobile uses `mobile-nompilo`; web command/assistant surfaces remain partially converged
- Fallback-safe operation is mandatory when model/provider routes are unavailable

## Implementation invariants for new frontend work

- No direct frontend calls to sovereign services from feature screens
- No merge if loading/error/empty/provisional states are missing
- No merge if trust headers or journey/plane mapping are absent
- No merge if tests or runtime validation are not updated for changed route families

## 150+ service execution model

vNext has a large service footprint and cannot be remediated route-by-route manually. Execution follows a service-factory model:

1. **Registry-driven inventory first:** use `docs/registry/services-registry.yaml` as source for service ids, planes, and `frontend_wiring_status`.
2. **Tiered surfacing lanes:** prioritize by journey risk and operational value:
   - Tier A: trust, identity, queue, core transaction, workflow, dispatch, telemedicine, payments/claims.
   - Tier B: registry admin, marketplace ops, integration hub, reporting intelligence.
   - Tier C: long-tail and specialized services.
3. **Reusable surface primitives:** shared telemetry/action components and query hooks are mandatory to avoid per-service UI drift.
4. **Three-step completion rule per service family:** visibility (read) -> actionability (safe commands) -> parity (web/mobile + offline where relevant).
5. **DoD gate is evidence-based:** each wave updates parity/audit/checklist docs and must not mark complete without live endpoint wiring and explicit failure-state behavior.
