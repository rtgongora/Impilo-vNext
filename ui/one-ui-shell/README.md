# One UI Shell

The One UI Shell is the canonical web orchestration surface for Impilo vNext.

## Core Transaction Alignment

Transaction-aware scaffolding is available at:

- `/core-transaction`
- `/client-journey`
- `/provider-workspace`
- `/platform-journey`

Feature code lives in:

- `src/features/core-transaction`

Doctrine references:

- `docs/doctrine/CORE_TRANSACTION_DOCTRINE.md`
- `docs/doctrine/CORE_TRANSACTION_STATE_MACHINE.md`
- `docs/doctrine/THREE_CORE_JOURNEYS.md`
- `docs/doctrine/NOMPILO_INTELLIGENT_JOURNEY_COMPANION.md`
- `docs/architecture/core-transaction-bff-composition.md`

## Journey Route Anchors

- Person Journey: `/client-journey`, `/citizen`, `/discover`, `/wellness`, `/wallet`, `/telemedicine`
- Provider Journey: `/provider-workspace`, `/clinical`, `/queue`, `/ehr/[patientId]`, `/lab`, `/pharmacy`, `/learning`
- Platform Journey: `/platform-journey`, `/operations`, `/monitoring`, `/reports`, `/finance`, `/enterprise`, `/admin`
- Cross-cutting: `/core-transaction`, `/ask`, `/search`, `/support`, `/settings`

## Nompilo Command Layer

- Global command bar is available in `AppLayout` through `NompiloGlobalCommandBar`.
- Shell command palette is available through taskbar search and `Ctrl+K`.
- `/ask` provides conversational workflow support.
- `/search` and `/intelligence` provide governed retrieval and explanation views.

## Shared UI Locations

- App shell and navigation: `src/components/AppLayout.tsx`, `src/components/navigation`, `src/components/shell`
- Core transaction feature: `src/features/core-transaction`
- Role and route grouping metadata: `src/lib/ui-route-journey-map.ts`
- Accessibility controls: `src/components/accessibility/AccessibilityToolbar.tsx`

## Adding Future UI Features (Without Duplication)

1. Add route metadata first (`src/lib/routes.ts` and `src/lib/ui-route-journey-map.ts`).
2. Reuse existing shell surfaces before adding net-new wrappers.
3. If feature is transaction-aware, compose through `src/features/core-transaction`.
4. If feature needs Nompilo interaction, integrate with command bar/palette patterns.
5. Add focused tests in `src/components/__tests__` or feature tests before expanding to page-level tests.
