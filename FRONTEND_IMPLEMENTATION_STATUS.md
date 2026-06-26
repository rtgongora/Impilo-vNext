# Frontend Implementation Status

## Snapshot

This status consolidates current wave progress across web and mobile experience layers.

## By surface

| Surface | Status | Notes |
|---|---|---|
| `ui/one-ui-shell` | Partial | Broad route coverage and journey maps in place; mixed live/partial/fixture maturity by domain |
| Citizen app | Partial | Core tabs and service clients wired; depth and route completeness still uneven |
| Provider app | Partial | Strong operational shells and mode routing; parity depth and reachability cleanup ongoing |
| Mobile shared packages | Partial-to-Complete | Core trust/api/auth/offline/design-system foundations implemented; parity hardening continues |

## By doctrine requirement

| Requirement | Status | Current evidence |
|---|---|---|
| Health OS shell coherence | Partial | Role-aware shells exist in web/mobile, still converging on uniform orchestration quality |
| Seven-plane mapping | Partial | Documented for major surfaces; checklist now tracks per-surface mapping |
| BFF composition | Partial | Dominant pattern established; remaining drift captured in wiring audits |
| Contract-first | Partial | Canonical contracts used broadly; residual local model drift remains |
| No fake capability | Partial | Maturity badges and labels are present; not all surfaces yet standardized |
| Trust layer (TSHEPO/headers/context) | Partial | Web mature; mobile parity improved in this wave, full convergence pending |
| Offline/provisional reconciliation | Partial | Coverage commands now use durable mobile offline queue integration with retry/reconciliation panels; other domains still uneven |
| Nompilo contextual companion | Partial | Embedded in mobile and web; full parity in commands and journey integration pending |
| Mobile/web parity | Partial | Strong breadth, uneven depth by domain and role |

## Wave changes completed in this update

- Added the six required parity/architecture/doctrine deliverables at repo root.
- Expanded mobile trust header support for `x-department-id`, `x-ward-id`, `x-programme-id`, and `x-access-mode`.
- Updated mobile trust tests to validate the expanded context header envelope.
- Wired previously unreachable implemented screens into active navigation/workflow:
  - citizen provider discovery now appears in Personal sections
  - provider triage, billing, PACS and discharge now appear under Clinical Tools tabs
- Remediated doctrine web journey pages (`/core-transaction`, `/client-journey`, `/provider-workspace`, `/platform-journey`) to remove fixture injection and render explicit live/empty/error states from BFF data only.
- Added trust/context disclosure banners across doctrine web journey routes and platform operational feed cards backed by live `/internal/v1/workflows` and `/internal/v1/dispatch/tasks` telemetry.
- Added platform-level workflow/dispatch timeline cards with recent state transitions and timestamps for operational visibility.
- Added operator drill-down controls on platform journey for workflow status/type and dispatch status, wired to query parameters in the workflow/dispatch hooks.
- Added reusable operator telemetry primitives (`lib` + shared panel component) and reused them across `/operations/workflows`, `/operations/dispatch`, and `/platform-journey` to reduce drift.
- Platform telemetry filters are now URL-synced (`wfStatus`, `wfType`, `dpStatus`) with reset support for shareable troubleshooting views.
- Provider workspace now includes provider-facing workflow/dispatch telemetry overlays using the shared panel and URL-synced filters (`pWfStatus`, `pWfType`, `pDpStatus`).
- Added row-level actionability from platform/provider telemetry cards into operations routes, with focused-row highlighting and URL-synced filters on `/operations/workflows` and `/operations/dispatch`.
- Added contract-backed core-transaction command action for Nompilo human handoff requests (`POST /internal/v1/core-transactions/{transactionId}/nompilo/handoff`) with explicit accepted/error feedback states.
- Expanded operations dispatch to surface backend datasets (dashboard, console, deliveries, fleet, couriers, missions) and wired live delivery-create command submission.
- Expanded operations workflows to surface workflow definitions and workflow instances from backend workflow APIs.
- Added a second contract-backed core-transaction command path: Nompilo command execution (`POST /internal/v1/core-transactions/{transactionId}/nompilo/command`) with explicit accepted/error feedback states.
- Added BFF pass-throughs and web command consoles for workflow instance start/transition (`/internal/v1/workflows/instances`) and dispatch task/delivery actions (`/internal/v1/dispatch/tasks/*`, `/internal/v1/dispatch/deliveries/{id}/{action}`), keeping all command feedback explicit.
- Upgraded provider mobile `Flow/Ops` from read-only feeds to a shared service-backed action surface for workflow start/transition, dispatch task create/assign/complete, and delivery actions.
- Expanded Registry plane parity beyond identity: web Registry Hub identity commands are now guided and validated rather than raw JSON; product pages call canonical `/internal/v1/product-registry/*`; terminology pages resolve ZIBO artifacts through `/internal/v1/registry/zibo/artifacts/resolve`; unavailable federation/key admin pages are relabelled instead of calling missing aliases; Mvumo now has typed `/internal/v1/mvumo-admin/*` template/consent-request entry points; registry intake has list/cancel job management; provider mobile Admin & Registry now reaches facility lifecycle, locality review, registry intake/import, product registry, ZIBO terminology, and trust/consent read models through real BFF routes; citizen mobile ID Recovery uses live identity search/resolve/recovery.
- Started the clinical care orchestration wave by accounting for existing PCT-backed surfaces first: web/mobile already expose queue, encounters, triage, vitals, labs, referrals, prescriptions, notes, discharge, and telemedicine. This pass fixes the most immediate contract gaps without deleting existing workflows: web encounter start now requires and sends the canonical PCT journey ID, patient-summary start buttons route to the guided encounter form, provider mobile encounter start uses `/internal/v1/mobile/provider/encounters` with `pct_journey_id`, provider mobile queue management now calls canonical `/internal/v1/queue/entries*`, mobile vitals include required patient context, mobile triage sends the typed snake_case payload, and the encounter triage tab now renders the live triage component.
- Upgraded Coverage/Payer operations: web `/coverage` now uses guided command fields, React-state forms, canonical appeal submit/review/decision actions, and stronger idempotency/amount/policy guardrails; `/finance/payer-ops` exposes an intent-linked payer state machine across remittance claim, attempts, receipts, settlements, and refunds; citizen/provider mobile now expose payer-ops workspaces with claims/remittance/appeals/settlements reconciliation plus durable provisional retry queues.
- Validation evidence: focused web payer/coverage Vitest suites pass; provider mobile registry identity/operations service and AdminRegistry interaction tests pass; citizen mobile identity service and ID Recovery interaction tests pass; web, citizen mobile, and provider mobile typechecks pass; web production build passes.
- **Provider / Clinical / Place integration wave (GAP-22 registration):** registered the new web surfaces from `integration/provider-clinical-place` — `/facility/[id]/{cockpit,setup,departments,regulators,control-tower}` (Facility Mode cockpit/setup/control-tower) and `/indawo`, `/indawo/{surveillance,outbreaks,field-teams}` (place mode), plus the Adaptive Encounter Cockpit component and the Work/Pro/Life context picker wired into `ContextRail`. New hooks: `useCadreDecision.ts`, `useWorkContext.ts`, and the `facility-mode/` hooks (`useFacilityMode`, `useControlTower`, `usePlaceMode`, `useRegulators`). These are **web-only**: mobile parity for the new provider/place surfaces is **Missing (GAP-19)**, and **no patient-facing journey surfaces were shipped (GAP-8)**. Adaptive cadre form *content* is Partial (GAP-10) and the unifying sorting session is Missing (GAP-11). Independent verification (per the gap register) shows the lane code real and green (tsc 0 err, vitest green, lint 0 err) — but Live labels apply only to the provider/place surfaces, not patient experience or mobile.

## Remaining high-impact work

- Convert remaining fixture-backed doctrine journeys to live orchestration where backend support exists.
- Complete per-surface doctrine checklist closure from Partial to Complete with evidence.
- Continue route reachability cleanup for implemented but non-routed screens.
- Continue expanding parity tests beyond the newly covered coverage/payer/registry surfaces into long-tail service families.
