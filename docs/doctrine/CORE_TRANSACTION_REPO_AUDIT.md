# Core Transaction Repo Audit

## Scope

This audit inspects `README.md`, `AGENTS.md`, `CLAUDE.md`, `INTEGRATED_OPERATING_MODEL.md`, `WORKFORCE_GOVERNANCE.md`, `docs/architecture`, `docs/registry`, `contracts`, `compose/experience`, `ui/one-ui-shell`, and existing tests/contracts relevant to transaction orchestration.

## Existing Strengths Already Aligned

1. **Seven-plane architecture is explicit** in root docs and `docs/architecture/planes/00-production-plane-doctrine.md`.
2. **Source-of-truth discipline is already formalized** in `docs/registry/system-of-record-map.md` and `docs/registry/service-ownership-matrix.md`.
3. **Trust-first request flow is established** via TSHEPO guidance in `AGENTS.md`/`CLAUDE.md`.
4. **Experience orchestration exists** via `one-ui-shell` and `experience-bff` with extensive route surface.
5. **BFF contract base exists** in `contracts/openapi/experience-bff.openapi.yaml`.
6. **Event catalog exists** in `contracts/async/impilo-events.asyncapi.yaml`.
7. **Workflow-centric UX patterns exist** in queue, telemedicine, EHR timeline, and finance route surfaces.

## Existing Services Supporting Core Transaction

- **Trust**: Tshepo services and Mvumo contract surfaces.
- **Registry**: Vito, Varapi, Tuso, Zibo, Indawo, Ubomi.
- **Clinical**: Butano, PCT, OROS, Pharmacy, Inpatient.
- **Enterprise**: Costa, MusheX, Coverage, GL, Procurement.
- **Integration**: Integration hub, offline-sync/offline-edge, notification, jobs, adapters.
- **Experience**: one-ui-shell + experience-bff.

## Existing Contract and BFF Patterns

- OpenAPI contracts already split by service in `contracts/openapi`.
- AsyncAPI catalogs already maintained under `contracts/async` and `contracts/asyncapi`.
- BFF is explicitly documented as an orchestration layer and should remain composition-only.

## Existing Security, Trust, and Audit Mechanisms

- Trust headers are standardized and documented.
- TSHEPO ext_authz enforcement is mandatory by doctrine.
- Outbox/event discipline is explicitly mandated.
- Audit services and audit routes already exist.

## Duplication Risks Identified

1. Multiple parallel docs and path variants can drift if doctrine is not centralized.
2. Experience routes can become feature islands without explicit transaction-state mapping.
3. Existing event catalogs are broad but not yet normalized around one canonical "core transaction" envelope.
4. BFF route growth can accidentally imply ownership unless composition contracts are explicit.

## Gaps Addressed By This Implementation

1. Canonical Core Transaction state machine and lifecycle mapping.
2. Canonical Core Transaction event model and envelope shape.
3. Explicit service ownership and data ownership overlays bound to transaction flow.
4. Experience-layer transaction scaffolding (client journey + provider workspace + timeline).
5. Transaction-aware fixtures and tests for state/events/UX rendering.
6. Agent compliance rules to prevent orphan features and duplicate truth creation.
7. Three synchronized journey doctrine and mapping artifacts.
8. Nompilo companion model with accessibility and feedback structures.

## Implementation Approach Used

1. Add a **canonical contract anchor** in `contracts/core-transaction.ts`.
2. Add doctrine docs under `docs/doctrine` and architecture overlays under `docs/architecture`.
3. Add dedicated OpenAPI + AsyncAPI contracts for core transaction composition/eventing.
4. Scaffold One UI Shell transaction feature surfaces with typed fixtures and lifecycle UI.
5. Add focused tests for transitions, event shape requirements, BFF view shape, timeline and stepper rendering.
6. Update contributor and agent guidance to enforce doctrine compliance for future work.
