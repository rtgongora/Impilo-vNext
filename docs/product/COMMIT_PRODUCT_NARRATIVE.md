# Commit Product Narrative

> Generated: 2026-06-14 · Branch `claude/staging-ux-orchestration-remediation-Yypyl` · HEAD `c0e65ddb` · **1256 commits**

This narrative themes the branch history by canonical plane and highlights the current uncommitted delta (visual/chrome refresh + full-stack unblock work).

## Executive thread

Impilo vNext evolved from infrastructure and trust foundations into a **576-route unified experience shell** backed by **90+ microservices**. Recent work concentrates on:

1. **Experience orchestration** — one-ui-shell convergence, shell taskbar, role-aware home, Nompilo, route parity gates.
2. **Domain verticals** — MADI haemovigilance, social timeline, telemedicine, public-health ops, marketplace/finance.
3. **Full-boot preview** — wave-based k3s deployment so BFF downstream URLs resolve instead of 500-ing.
4. **Visual system refresh (uncommitted)** — official palette, African print canvas, compact chrome, citizen wallet widget, error degradation.

## Recent commits (latest 40)

- `c0e65ddb` feat: apply Impilo visual system across all UI workspaces
- `b69a9c90` feat: centralise Impilo visual tokens and branded shared-ui components
- `f910c590` fix(helm): route /internal,/actuator,/health to BFF via IngressRoute
- `cfda6e15` fix(experience-bff): strip hop-by-hop headers on all responses to prevent duplicate Transfer-Encoding
- `f3da49cc` fix: bind BFF msika apps base URL in preview config
- `ef1afd0f` fix: align msika/VITO preview trust and BFF marketplace degraded handling
- `22fba055` chore: harden phased full-preview wave promotion script
- `103a5aab` feat: tier-1 registry parity and launcher catalogue (PCW-2)
- `c274ea63` chore: close BFF downstream env gaps for full-preview (PCW-1)
- `b487e83b` fix(madi): add blood_inventory created_at and phased wave promote script
- `0ff0a6eb` test(ui): align register tests with 12-char Keycloak password policy
- `9392c23a` chore(preview): keep workforce-governance enabled for governed login chain
- `997a10d0` fix(preview): wire governed login chain, OAuth bypass, and sovereign seeds
- `e0c5af90` chore: regenerate full-boot reports and preview contract matrices
- `a2627182` docs(product): add full-preview UAT validation pack for 4917def8
- `d17315ef` chore(operator): add staged rollout and stale k3s image refresh helpers
- `ff5fe8fe` fix(services): repair madi schema mapping and campaigns Flyway version
- `436d1415` fix(preview): wire BFF downstream URLs and preview OAuth bypass
- `e82b3190` fix(bff): make Health OS launcher null-safe for missing facilityId
- `84115871` fix(ui): stop /work route redirect loop when work tab is blocked
- `46254765` fix(mobile): close parity wave typecheck tests and runtime validation
- `0ce94f82` feat(mobile): complete vNext mobile parity wiring and runtime wave
- `4917def8` fix(build): unblock full-stack reactor compile for preview image rebuild
- `ba7064e2` chore: ignore local Maven cache, test results, and stray apps/web copy
- `8c0e75ed` chore: regenerate full-boot reports and contract matrices
- `23f772cf` feat(branding): complete sovereign service logo coverage
- `82778272` feat(branding): add sovereign service logos across vNext UI
- `fc30788d` feat(admin-governance): add invitation lifecycle UI and BFF endpoints
- `a94e4fba` feat(admin-governance): wire Keycloak activation and invitation delivery
- `5fa3f2e3` fix(test): satisfy home page and session experience quality gates
- `8510069f` fix(test): restore home and session experience gate regressions
- `16b88421` fix(admin-governance): resolve BFF compile and sidebar test regressions
- `eaa65c5f` fix(admin-governance): clear preview gate blockers for bootstrap onboarding
- `b0c6d917` feat(admin-governance): add bootstrap mode and delegated bulk onboarding
- `074f08a1` feat(trust): add MoHCC organogram seed catalogues and three-tab session doctrine
- `77544580` feat(live): wire PCT, Fundo, and campaigns integration bridges
- `740e808b` feat(live): first-class Impilo Live modes + governance guardrails
- `0089211a` chore: regenerate parity inventories after PH and data-plane completion
- `e586d255` feat: clear UI surfacing hotspots and close operator parity gaps
- `3616d4f8` chore: sync capability matrices and registry maturity after ED and scheduling waves

## Themed by plane

### cross-cutting (492 commits)

- feat: apply Impilo visual system across all UI workspaces
- feat: centralise Impilo visual tokens and branded shared-ui components
- chore: harden phased full-preview wave promotion script
- chore: regenerate full-boot reports and preview contract matrices
- docs(product): add full-preview UAT validation pack for 4917def8
- chore(operator): add staged rollout and stale k3s image refresh helpers
- fix(services): repair madi schema mapping and campaigns Flyway version
- fix(build): unblock full-stack reactor compile for preview image rebuild
- chore: ignore local Maven cache, test results, and stray apps/web copy
- chore: regenerate full-boot reports and contract matrices
- feat(branding): complete sovereign service logo coverage
- feat(branding): add sovereign service logos across vNext UI

_…and 480 more._

### experience (210 commits)

- fix(helm): route /internal,/actuator,/health to BFF via IngressRoute
- fix(experience-bff): strip hop-by-hop headers on all responses to prevent duplicate Transfer-Encoding
- fix: bind BFF msika apps base URL in preview config
- chore: close BFF downstream env gaps for full-preview (PCW-1)
- fix(ui): stop /work route redirect loop when work tab is blocked
- fix(mobile): close parity wave typecheck tests and runtime validation
- feat(mobile): complete vNext mobile parity wiring and runtime wave
- fix(test): satisfy home page and session experience quality gates
- fix(test): restore home and session experience gate regressions
- feat(shell): Fundo enrolment UX, content uploads, and shell A-D remediation
- chore(compose): add learning-service to experience dev stack
- feat(mobile): deepen MADI central-bank, Fundo LMS, intake dedup, and PH field ops

_…and 198 more._

### registry (153 commits)

- feat: tier-1 registry parity and launcher catalogue (PCW-2)
- fix(bff): make Health OS launcher null-safe for missing facilityId
- chore: sync capability matrices and registry maturity after ED and scheduling waves
- feat(mobile): deepen provider Fundo assessment submit and certificate issue
- feat: enrich facility discovery with data-quality and BFF spatial proxy
- feat: facility master import, Ndila maps, and orchestration wave
- feat(vito): issuance queue ops and delegated pickup facility names
- feat(phase-4.3): close pharmacy, referral, vito pickup, monitoring, and mobile Rx depth
- chore: register live-service in full-boot waves and sync registry maturity
- feat: add Impilo Live mobile parity for citizen and provider apps
- chore: register MADI in platform registry, gates, and architecture docs
- feat: add MADI mobile parity for citizen donors and provider clinical

_…and 141 more._

### trust (126 commits)

- fix: align msika/VITO preview trust and BFF marketplace degraded handling
- test(ui): align register tests with 12-char Keycloak password policy
- fix(preview): wire governed login chain, OAuth bypass, and sovereign seeds
- fix(preview): wire BFF downstream URLs and preview OAuth bypass
- feat(admin-governance): wire Keycloak activation and invitation delivery
- feat(trust): add MoHCC organogram seed catalogues and three-tab session doctrine
- test(shell): add Fundo compose and journey e2e plus shell auth flows
- feat: wire patient search, consent, payer-ops, and context journeys
- feat(trust): extend security settings with trust governance strip
- fix(deploy): durable Keycloak realm import and full-preview public ingress
- fix(tshepo-identity): use a valid 32-byte default MOSIP KEK
- fix: stabilize human-authorized preview pipeline gates

_…and 114 more._

### clinical (117 commits)

- feat(clinical): ED visit operations with triage engine and cross-surface UX
- feat(clinical): inpatient depth, perioperative pipeline, and anaesthesia scoring
- feat(wave-1): clinical/finance journey completion evidence
- feat(pharmacy): sovereign five-rights verification in BFF dispense path
- feat(clinical): close Phase 4.2 journey depth chains on web and mobile
- feat(inpatient): sovereign bed management API with ward UI wiring
- feat(clinical): add citizen /home/results route from health summary
- fix(full-boot): wave-3 Hibernate schema alignment for clinical services
- fix(butano): wire HAPI FHIR JPA server so the SHR boots
- feat(one-ui-shell): merge AppLayout/EHRLayout - union of clinical chrome and modern shell
- feat(one-ui-shell): lift clinical-forms subsystem and DAK encounter dispatcher
- ﻿feat(one-ui-shell): lift clinical-chrome capability set from ui/experience

_…and 105 more._

### enterprise (78 commits)

- fix(madi): add blood_inventory created_at and phased wave promote script
- test: runtime evidence for document upload and finance journeys
- feat: replace finance payer-ops and workspace JSON dumps with product tables
- fix(mushex): match lock_version column type to schema
- fix(msika): correct marketplace risk migration tables
- fix: dispatch numeric fields and nhume CommsHub bean wiring
- fix: full-boot blockers, reports, and post-checkpoint workflow
- feat: add human sudo checkpoint workflow for full boot helper
- feat(enterprise): wire fleet page to dispatch operations APIs
- feat(health-os): govern integrations and capability marketplace as a sovereign Health Operating System
- feat(nhume): add Nhume dispatch, delivery and last-mile logistics service
- feat(learning): harden LMS rollout and learner workflows

_…and 66 more._

### data (50 commits)

- chore(preview): keep workforce-governance enabled for governed login chain
- feat(admin-governance): add invitation lifecycle UI and BFF endpoints
- fix(admin-governance): resolve BFF compile and sidebar test regressions
- fix(admin-governance): clear preview gate blockers for bootstrap onboarding
- feat(admin-governance): add bootstrap mode and delegated bulk onboarding
- feat(live): first-class Impilo Live modes + governance guardrails
- feat: Phase 7 consumer journeys — Nompilo, social, surveillance, Fundo
- feat(monitoring): device-native readings ingestion for citizen monitoring
- feat(telemedicine): analytics ingest on consult complete and SLA dashboard
- feat: close Impilo Live gaps for replay, Fundo CPD, and analytics
- feat(madi): certify core transactions and close governance gaps
- feat: add full vnext build boot and doctrine readiness pipeline

_…and 38 more._

### integration (30 commits)

- chore: register booking-service in full-boot waves and maturity sync
- fix: resolve Flyway clashes and offline-edge schema gaps
- merge: sync with origin claude/staging-ux-orchestration-remediation-Yypyl
- fix(offline-edge-service): restore security deps and actor id fallback
- feat(mushe-wallet): harden wallet core — tx limits, Luhn PAN, PIN ops, MUSHEX sync
- merge: sync staging branch with implement branch
- feat(contracts): add channels and offline-sync OpenAPI specs, expand notification contract
- feat(eventing): add Kafka bus topology and AsyncAPI event catalog
- docs(architecture): Phase E slice 1 — Kafka event catalog and AsyncAPI workspace
- fix: add @EnableAsync to ExperienceBffApplication
- docs: add healthcare coding standards interoperability doctrine
- fix: auto-sync work mode, integrate licenses, protect workspaces

_…and 18 more._

## Uncommitted working-tree delta (58 paths)

- `onfig/full-boot-service-classification.yml` (M)
- `deploy/helm/impilo-vnext/templates/preview-credentials.yaml` (M)
- `deploy/helm/impilo-vnext/values-preview.yaml` (M)
- `docs/architecture/API_ENDPOINT_INVENTORY.md` (M)
- `docs/architecture/BACKEND_CAPABILITY_INVENTORY.md` (M)
- `docs/architecture/FRONTEND_BACKEND_PARITY_MATRIX.md` (M)
- `docs/architecture/FRONTEND_ROUTE_INVENTORY.md` (M)
- `docs/architecture/MOBILE_PARITY_MATRIX.md` (M)
- `docs/environment/FULL_BOOT_INFRASTRUCTURE_DEPENDENCY_MATRIX.md` (M)
- `docs/product/CONTRACT_IMPLEMENTATION_MATRIX.md` (M)
- `docs/product/PRODUCT_TRUTH_RECOVERY_MAP.md` (M)
- `docs/product/PRODUCT_TRUTH_RECOVERY_PHASE_REPORT.md` (M)
- `docs/product/SERVICE_COVERAGE_LEDGER.md` (M)
- `reports/full-boot/build-targets.json` (M)
- `reports/full-boot/full-boot-runtime-report.json` (M)
- `reports/full-boot/full-boot-runtime-report.md` (M)
- `reports/full-boot/full-boot-smoke-report.json` (M)
- `reports/full-boot/image-strategy-targets.json` (M)
- `reports/full-boot/non-runtime-components.json` (M)
- `reports/full-boot/preview-generation.json` (M)
- `reports/full-boot/preview-generation.md` (M)
- `reports/full-boot/registry-inventory-contract.json` (M)
- `reports/product/contract-implementation-matrix.json` (M)
- `reports/product/product-truth-recovery-map.csv` (M)
- `reports/product/product-truth-recovery-map.json` (M)
- `reports/product/product-truth-rollups.json` (M)
- `reports/product/product-truth-rollups.md` (M)
- `reports/product/service-coverage-ledger.json` (M)
- `ui/one-ui-shell/README.md` (M)
- `ui/one-ui-shell/src/app/home/page.tsx` (M)
- `ui/one-ui-shell/src/components/AppLayout.tsx` (M)
- `ui/one-ui-shell/src/components/AuthLayout.tsx` (M)
- `ui/one-ui-shell/src/components/EHRLayout.tsx` (M)
- `ui/one-ui-shell/src/components/PageShell.tsx` (M)
- `ui/one-ui-shell/src/components/__tests__/PageShell.test.tsx` (M)
- `ui/one-ui-shell/src/components/__tests__/journey-shell-components.test.tsx` (M)
- `ui/one-ui-shell/src/components/accessibility/AccessibilityToolbar.tsx` (D)
- `ui/one-ui-shell/src/components/common/QueryResultPanel.tsx` (M)
- `ui/one-ui-shell/src/components/navigation/ExperienceSidebar.tsx` (M)
- `ui/one-ui-shell/src/components/shell/ShellTaskbar.test.tsx` (M)
- `ui/one-ui-shell/src/components/shell/ShellTaskbar.tsx` (M)
- `ui/one-ui-shell/src/generated/registry-maturity.json` (M)
- `ui/one-ui-shell/src/lib/routes.ts` (M)
- `ui/one-ui-shell/src/providers/Providers.tsx` (M)
- `ui/one-ui-shell/src/styles/globals.css` (M)
- `ui/shared-ui/components/WorkspaceHero.tsx` (M)
- `ui/shared-ui/tailwind-preset.ts` (M)
- `ui/shared-ui/tokens.css` (M)
- `307/` (??)
- `reports/full-boot/wave-enumeration.json` (??)

_…and 8 more paths._

## How to regenerate

```bash
node scripts/product/generate-commit-narrative.mjs
```
