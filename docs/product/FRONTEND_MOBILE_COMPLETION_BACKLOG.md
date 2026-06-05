# Frontend & Mobile Completion Backlog

> Living backlog derived from Product Truth Recovery, Core Transaction Matrix, and Experience Coherence Report.  
> Updated after each Phase 4 completion batch.

## Completed (Phase 4 — 2026-06-05)

| Journey | Web | Mobile | Notes |
|---------|-----|--------|-------|
| Provider Patient Encounter | `/ehr/[patientId]/encounter/[encounterId]` — `EncounterOrchestrationRail` + `useEncounterCoreTransaction` | `EncounterScreen` transaction context card | BFF `encounter_id` filter on core-transactions list |

## High priority — next batches

| Priority | Journey | Gap | Target surfaces |
|----------|---------|-----|-----------------|
| 1 | Core Transaction Orchestration Shell | Doctrine pages marked missing-journey in audit | `/core-transaction`, `/client-journey`, `/provider-workspace`, `/platform-journey` |
| 2 | Patient Search → encounter correlation | Queue search does not pass `transaction_id` | `/queue/search`, walk-in handoff |
| 3 | Context activation chain | Workspace/shift before clinical routes | Login → facility → workspace → shift |
| 4 | Health ID Issuance & Card Ops | Card pickup blocked | Registry issuance queue |
| 5 | Payment / Billing | `payer-ops` stubs | `/finance/payer-ops` |
| 6 | Lab Order write path | Orders read-only in UI | `/ehr/.../orders` + BFF write contract |
| 7 | Wellness routes map | Coming-soon stub | `/wellness/routes` |
| 8 | Mobile provider parity | 45 disconnected mobile screens | Provider app encounter shell alignment (partial — Phase 4) |

## Web routes — known stubs (do not treat as complete)

| Route | Issue |
|-------|-------|
| `/clinical-tools/forms` | Form builder mock-stub |
| `/finance/payer-ops` | Stub adapters |
| `/wellness/routes` | Coming-soon |

## Mobile — documented limitations

| Area | Limitation |
|------|------------|
| Provider encounter | Transaction context read-only on mobile; action apply deferred to web rail in Phase 4 |
| CRVS / UBOMI | No mobile screens |
| Field public health | Thinner than web |

## Regression guardrails

- `npm run test:no-stubs` in `ui/one-ui-shell`
- `npm run test:routes` for route parity
- `scripts/guard/check-backend-frontend-parity.sh`
- `scripts/guard/check-mobile-parity.sh`
