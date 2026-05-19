# Core Transaction Implementation Summary

## 1) What Was Implemented

- Extended doctrine from two-journey scaffold to explicit three synchronized journeys.
- Added formal Nompilo Intelligent Journey Companion doctrine and architecture overlays.
- Expanded canonical contract model with journey, payment-gate, and Nompilo companion types/helpers.
- Expanded One UI Shell scaffolding with platform journey route, three-journey mapping, and Nompilo panels.
- Added fixture coverage for payment gate, failed payment, offline sync, accessibility assist, and feedback capture.
- Added a canonical Core Transaction contract and state/event helpers.
- Added formal doctrine and architecture alignment documentation pack.
- Added agent compliance rules for future implementation discipline.
- Added OpenAPI/AsyncAPI contracts for composed view and canonical events.
- Added One UI Shell transaction scaffolding with typed fixtures.
- Added focused tests for state machine behavior and transaction UX rendering.
- Added runtime `experience-bff` composition/controller wiring for dual-surface Core Transaction endpoints.
- Added compose-backed integration harness and CI load/runtime gates for Core Transaction.
- Added sovereign dual-emission mapping into `core.transaction.events` from PCT and COSTA outbox publishers.
- Extended sovereign dual-emission mapping into `core.transaction.events` for OROS, Pharmacy, Msika Flow, and MusheX outbox publishers.
- Added a repo-wide machine-checkable doctrine audit script and blocking CI doctrine gate with report artifacts.
- Added strict per-service Core Transaction checklist matrix with all 20 acceptance criteria fields.

## 2) Files Inspected

- `README.md`, `AGENTS.md`, `CLAUDE.md`
- `INTEGRATED_OPERATING_MODEL.md`, `WORKFORCE_GOVERNANCE.md`
- `docs/architecture/planes/00-production-plane-doctrine.md`
- `docs/registry/system-of-record-map.md`
- `docs/registry/service-ownership-matrix.md`
- `contracts/openapi/experience-bff.openapi.yaml`
- `contracts/async/impilo-events.asyncapi.yaml`
- `ui/one-ui-shell/src/lib/routes.ts`
- representative queue/home/test files under `ui/one-ui-shell`
- `compose/experience/README.md`
- `services/experience-bff/src/main/java/**/CoreTransactionController.java`
- `services/experience-bff/src/main/java/**/CoreTransactionCompositionService.java`
- `services/pct-service/src/main/java/**/OutboxPublisher.java`
- `services/costing-engine-service/src/main/java/**/OutboxPublisher.java`
- `services/oros-service/src/main/java/**/OutboxPublisher.java`
- `services/pharmacy-service/src/main/java/**/OutboxPublisher.java`
- `services/msika-flow-service/src/main/java/**/OutboxPublisher.java`
- `services/mushex-service/src/main/java/**/OutboxPublisher.java`

## 3) Files Created

- `contracts/core-transaction.ts`
- `contracts/asyncapi/core-transaction-events.asyncapi.yaml`
- `contracts/openapi/core-transaction-openapi.yaml`
- `docs/doctrine/CORE_TRANSACTION_REPO_AUDIT.md`
- `docs/doctrine/CORE_TRANSACTION_DOCTRINE.md`
- `docs/doctrine/CORE_TRANSACTION_STATE_MACHINE.md`
- `docs/doctrine/CLIENT_PROVIDER_JOURNEY_MAP.md`
- `docs/doctrine/EXPERIENCE_LAYER_TRANSACTION_ANCHOR.md`
- `docs/doctrine/CORE_TRANSACTION_ACCEPTANCE_CRITERIA.md`
- `docs/architecture/core-transaction-plane-map.md`
- `docs/architecture/core-transaction-event-model.md`
- `docs/architecture/core-transaction-service-ownership.md`
- `docs/architecture/core-transaction-data-ownership.md`
- `docs/architecture/core-transaction-bff-composition.md`
- `docs/architecture/core-transaction-ui-alignment.md`
- `docs/architecture/core-transaction-offline-federated-model.md`
- `docs/architecture/core-transaction-security-audit-model.md`
- `docs/architecture/core-transaction-failure-modes.md`
- `docs/templates/CORE_TRANSACTION_FEATURE_ALIGNMENT_CHECKLIST.md`
- `docs/README.md`
- `docs/architecture/README.md`
- `docs/architecture/planes/README.md`
- `docs/doctrine/README.md`
- `ui/one-ui-shell/README.md`
- `ui/one-ui-shell/src/features/core-transaction/types.ts`
- `ui/one-ui-shell/src/features/core-transaction/state-machine.ts`
- `ui/one-ui-shell/src/features/core-transaction/components.tsx`
- `ui/one-ui-shell/src/features/core-transaction/fixtures/core-transactions.ts`
- `ui/one-ui-shell/src/features/core-transaction/__tests__/core-transaction.test.tsx`
- `ui/one-ui-shell/src/app/core-transaction/page.tsx`
- `ui/one-ui-shell/src/app/client-journey/page.tsx`
- `ui/one-ui-shell/src/app/provider-workspace/page.tsx`
- `compose/core-transaction/docker-compose.core-transaction-e2e.yml`
- `compose/core-transaction/wiremock/**/mappings/*.json`
- `test/integration/core-transaction-runtime.sh`
- `tests/performance/k6-core-transaction.js`
- `scripts/architecture/audit-core-transaction-compliance.py`
- `docs/architecture/core-transaction-service-compliance.yaml`
- `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/CoreTransactionController.java`
- `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/service/CoreTransactionCompositionService.java`
- `services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/CoreTransactionControllerTest.java`
- `services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/contract/CoreTransactionOpenApiContractTest.java`

## 4) Files Modified

- `README.md`
- `AGENTS.md`
- `CLAUDE.md`
- `compose/experience/README.md`
- `ui/one-ui-shell/src/lib/routes.ts`
- `contracts/openapi/experience-bff.openapi.yaml`
- `.github/workflows/ci.yml`
- `services/pct-service/src/main/java/zw/gov/mohcc/impilo/pct/events/OutboxPublisher.java`
- `services/pct-service/src/main/resources/application.yml`
- `services/costing-engine-service/src/main/java/zw/gov/mohcc/impilo/costa/kafka/OutboxPublisher.java`
- `services/costing-engine-service/src/main/resources/application.yml`
- `services/costing-engine-service/src/test/java/zw/gov/mohcc/impilo/costa/kafka/OutboxPublisherRoutingConventionTest.java`
- `services/oros-service/src/main/resources/application.yml`
- `services/pharmacy-service/src/main/resources/application.yml`
- `services/msika-flow-service/src/main/resources/application.yml`
- `services/mushex-service/src/main/resources/application.yml`
- `services/oros-service/src/test/java/zw/gov/mohcc/impilo/oros/events/OutboxPublisherRoutingConventionTest.java`
- `services/pharmacy-service/src/test/java/zw/gov/mohcc/impilo/pharmacy/events/OutboxPublisherRoutingConventionTest.java`
- `services/msika-flow-service/src/test/java/zw/gov/mohcc/impilo/msikaflow/events/OutboxPublisherTest.java`
- `services/mushex-service/src/test/java/zw/gov/mohcc/impilo/mushex/OutboxPublisherTest.java`

## 5) Existing Functionality Preserved

- Seven-plane architecture remains unchanged.
- Existing service ownership boundaries remain unchanged.
- Existing BFF and event catalogs are preserved and extended additively.
- Existing One UI Shell routes and modules remain intact.

## 6) New Doctrine Added

- Core Transaction doctrine as explicit foundational anchor.
- Lifecycle and state machine doctrine.
- Experience-layer transaction orchestration doctrine.
- Failure/offline/security doctrine overlays.

## 7) Contracts Added

- Canonical TS domain contract (`contracts/core-transaction.ts`).
- Core Transaction OpenAPI composition contract.
- Core Transaction AsyncAPI event contract.

## 8) Event Model Added

- Canonical event names and envelope shape with required governance context fields.

## 9) BFF Alignment Added

- Explicit composed-view API contract and composition doctrine.
- Runtime endpoint wiring in `experience-bff` for:
  - `GET/POST /internal/v1/core-transactions`
  - `GET /internal/v1/core-transactions/{transactionId}`
  - `POST /internal/v1/core-transactions/{transactionId}/actions/{actionCode}`
  - `GET /internal/v1/core-transactions/{transactionId}/timeline`
  - `GET /internal/v1/core-transactions/{transactionId}/next-actions`
- Doctrine-facing alias runtime surface:
  - `/experience/core-transactions/*`

## 10) UI Scaffolding Added

- Core transaction shell and reusable panels.
- Client and provider journey stepper surfaces.
- Transaction timeline and state/type/sync badges.
- Failure, permission, loading, empty, and error states.

## 11) Tests Added

- Transition validity and invalid transition behavior.
- Terminal state recognition.
- Emergency branch transition logic.
- BFF view shape expectations.
- Timeline/next-action/failure/offline/client-provider stepper rendering.

## 12) Build and Check Results

- `npm run type-check` (in `ui/one-ui-shell`) -> **PASS**
- `npx vitest run src/features/core-transaction/__tests__/core-transaction.test.tsx` -> **PASS (9 tests)**
- `mvn test -Dtest=CoreTransactionControllerTest,CoreTransactionOpenApiContractTest` (in `services/experience-bff`) -> **PASS**
- `mvn test -Dtest=OutboxPublisherRoutingConventionTest` (in `services/costing-engine-service`) -> **PASS**
- `mvn -DskipTests compile` (in `services/pct-service`) -> **PASS**
- `docker compose -f compose/core-transaction/docker-compose.core-transaction-e2e.yml config` -> **PASS**
- `mvn test -Dtest=OutboxPublisherRoutingConventionTest` (in `services/oros-service`) -> **PASS**
- `mvn test -Dtest=OutboxPublisherRoutingConventionTest` (in `services/pharmacy-service`) -> **PASS**
- `mvn test -Dtest=OutboxPublisherTest` (in `services/msika-flow-service`) -> **PASS**
- `mvn test -Dtest=OutboxPublisherTest` (in `services/mushex-service`) -> **PASS**
- `python scripts/architecture/audit-core-transaction-compliance.py` -> **PASS**

## 13) Known Gaps

- Core transaction runtime composition currently uses additive orchestration logic and fixture-compatible envelopes; deeper sovereign response normalization is a follow-up hardening slice.
- Local execution of `test/integration/core-transaction-runtime.sh` requires `bash`; current Windows environment lacked `/bin/bash`, so compose runtime assertions were validated through CI wiring and compose config validation.
- The compose-backed runtime harness currently uses controlled WireMock sovereign doubles for deterministic contract gating, while preserving direct-service integration gates in other compose suites.

## 14) Assumptions

- Additive doctrine-first alignment is preferred to avoid destabilizing broad in-flight repository work.
- Fixture-driven UI scaffolding is acceptable for first-wave alignment while backend composition endpoints are being finalized.

## 15) Risks and Follow-Up Work

1. Expand `core.transaction.events` payload normalization from dual-emission to explicit canonical envelope transformation across all emitting services (OROS, Pharmacy, Msika Flow, MusheX).
2. Add downstream-ready integration compose profile (non-mocked) including PCT/Workflow/COSTA for full sovereign runtime parity.
3. Add high-volume load baselines for timeline and action endpoints with artifacted SLO trend checks.
4. Add navigation affordances from home/dashboard modules into new transaction pages.

## 16) Guidance for Future Contributors

- Start with `docs/templates/CORE_TRANSACTION_FEATURE_ALIGNMENT_CHECKLIST.md`.
- Use `contracts/core-transaction.ts` as canonical state/type/event anchor.
- Preserve source-of-truth boundaries and avoid parallel domain models.
- Ensure every meaningful action maps to state + event + permission + audit.
