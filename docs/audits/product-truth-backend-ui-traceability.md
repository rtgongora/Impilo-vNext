# Product Truth — Backend-to-UI Traceability

> Generated: 2026-07-29T19:29:08.104Z

For each service: backend capabilities → API → BFF → UI → mobile → persistence.

## ai-model-registry-service

- **Path:** `services/ai-model-registry-service`
- **Domain:** intelligence (data)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (5 controllers, 19 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: ai-model-registry.openapi.yaml) |
| 3 | Wired via BFF? | Yes (3 clients) |
| 4 | Visible in UI? | Yes (0 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

## analytics-pipeline-service

- **Path:** `services/analytics-pipeline-service`
- **Domain:** platform-ops (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 4 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: analytics-pipeline.openapi.yaml) |
| 3 | Wired via BFF? | Yes (4 clients) |
| 4 | Visible in UI? | Yes (3 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/telemedicine/analytics/page.tsx`
- `ui/one-ui-shell/src/app/work/telemedicine/operations/page.tsx`
- `ui/one-ui-shell/src/hooks/queries/useTelemedicineAnalytics.ts`

## asset-registry-service

- **Path:** `services/asset-registry-service`
- **Domain:** platform-ops (integration)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (5 controllers, 50 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: asset-registry.openapi.yaml) |
| 3 | Wired via BFF? | Yes (4 clients) |
| 4 | Visible in UI? | Yes (7 refs) |
| 5 | Visible on mobile? | Yes (2 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (7 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/erp/assets/page.tsx`
- `ui/one-ui-shell/src/app/operations/assets/page.tsx`
- `ui/one-ui-shell/src/app/operations/equipment/page.tsx`
- `ui/one-ui-shell/src/components/telemonitoring/DevicePostureBadge.tsx`
- `ui/one-ui-shell/src/hooks/queries/useAssets.ts`

## audit-ledger-service

- **Path:** `services/audit-ledger-service`
- **Domain:** platform-ops (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 5 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: audit-ledger.openapi.yaml) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## booking-service

- **Path:** `services/booking-service`
- **Domain:** workflow-orchestration (experience)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 33 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: booking.openapi.yaml) |
| 3 | Wired via BFF? | Yes (18 clients) |
| 4 | Visible in UI? | Yes (58 refs) |
| 5 | Visible on mobile? | Yes (24 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/auth/login/page.tsx`
- `ui/one-ui-shell/src/app/finance/commerce-integrations/page.tsx`
- `ui/one-ui-shell/src/app/home/appointments/[appointmentId]/page.tsx`
- `ui/one-ui-shell/src/app/home/appointments/page.test.tsx`
- `ui/one-ui-shell/src/app/home/appointments/page.tsx`

## butano-fhir

- **Path:** `services/butano-fhir`
- **Domain:** care-delivery (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 8 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: butano.custom.openapi.yaml) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (13 refs) |
| 5 | Visible on mobile? | Yes (1 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/operations/butano/page.tsx`
- `ui/one-ui-shell/src/app/operations/page.tsx`
- `ui/one-ui-shell/src/app/production-command-centre/page.tsx`
- `ui/one-ui-shell/src/components/navigation/ExperienceSidebar.tsx`

## butano-service

- **Path:** `services/butano-service`
- **Domain:** care-delivery (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (5 controllers, 14 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: butano.custom.openapi.yaml) |
| 3 | Wired via BFF? | Yes (10 clients) |
| 4 | Visible in UI? | Yes (41 refs) |
| 5 | Visible on mobile? | Yes (29 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/sidecar-retirement/page.test.tsx`
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/citizen/wallet/page.test.tsx`
- `ui/one-ui-shell/src/app/citizen/wallet/page.tsx`
- `ui/one-ui-shell/src/app/citizen/wallet/records/page.tsx`

## campaigns-service

- **Path:** `services/campaigns-service`
- **Domain:** public-health-campaigns (data)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 12 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: campaigns.openapi.yaml) |
| 3 | Wired via BFF? | Yes (9 clients) |
| 4 | Visible in UI? | Yes (27 refs) |
| 5 | Visible on mobile? | Yes (9 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/communication/approvals/page.test.tsx`
- `ui/one-ui-shell/src/app/communication/approvals/page.tsx`
- `ui/one-ui-shell/src/app/enterprise/oversight/page.tsx`
- `ui/one-ui-shell/src/app/omnichannel/page.tsx`

## card-print-agent

- **Path:** `services/card-print-agent`
- **Domain:** interoperability (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 12 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: card-print.openapi.yaml) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## channels-service

- **Path:** `services/channels-service`
- **Domain:** interoperability (integration)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (6 controllers, 15 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: channels.openapi.yaml) |
| 3 | Wired via BFF? | Yes (13 clients) |
| 4 | Visible in UI? | Yes (43 refs) |
| 5 | Visible on mobile? | Yes (9 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/access/governance/page.tsx`
- `ui/one-ui-shell/src/app/access/page.tsx`
- `ui/one-ui-shell/src/app/admin/comms-ops/page.tsx`
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/citizen/wallet/comms/page.tsx`

## clinical-knowledge-platform-service

- **Path:** `services/clinical-knowledge-platform-service`
- **Domain:** clinical-knowledge (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (7 controllers, 34 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: clinical-knowledge-platform.openapi.yaml) |
| 3 | Wired via BFF? | Yes (10 clients) |
| 4 | Visible in UI? | Yes (10 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (10 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/clinical-tools/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/encounter/[encounterId]/page.tsx`
- `ui/one-ui-shell/src/components/EHRLayout.tsx`
- `ui/one-ui-shell/src/components/clinical/AIDiagnosticAssistant.tsx`
- `ui/one-ui-shell/src/components/clinical/ActiveCDSBanner.tsx`

## community-service

- **Path:** `services/community-service`
- **Domain:** workflow-orchestration (experience)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (6 controllers, 47 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: social.openapi.yaml) |
| 3 | Wired via BFF? | Yes (20 clients) |
| 4 | Visible in UI? | Yes (83 refs) |
| 5 | Visible on mobile? | Yes (43 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/clinical/nutrition-tracing/page.tsx`
- `ui/one-ui-shell/src/app/clinical-tools/page.tsx`
- `ui/one-ui-shell/src/app/communities/[id]/page.tsx`
- `ui/one-ui-shell/src/app/communities/page.tsx`

## connector-fhir-adapter

- **Path:** `services/connector-fhir-adapter`
- **Domain:** interoperability (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 5 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: connector-fhir.openapi.yaml) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## costing-engine-service

- **Path:** `services/costing-engine-service`
- **Domain:** finance (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (30 controllers, 189 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: costa.openapi.yaml) |
| 3 | Wired via BFF? | Yes (21 clients) |
| 4 | Visible in UI? | Yes (56 refs) |
| 5 | Visible on mobile? | Yes (7 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (24 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/budgets/[budgetId]/page.tsx`
- `ui/one-ui-shell/src/app/budgets/page.tsx`
- `ui/one-ui-shell/src/app/citizen/wallet/payments/page.tsx`
- `ui/one-ui-shell/src/app/coverage/operations/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/discharge/page.test.tsx`

## coverage-service

- **Path:** `services/coverage-service`
- **Domain:** finance (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (30 controllers, 159 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: coverage.openapi.yaml) |
| 3 | Wired via BFF? | Yes (24 clients) |
| 4 | Visible in UI? | Yes (129 refs) |
| 5 | Visible on mobile? | Yes (18 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (20 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/citizen/wallet/payments/page.test.tsx`
- `ui/one-ui-shell/src/app/citizen/wallet/payments/page.tsx`
- `ui/one-ui-shell/src/app/coverage/contracts/page.tsx`
- `ui/one-ui-shell/src/app/coverage/enroll/page.tsx`

## daidzai-service

- **Path:** `services/daidzai-service`
- **Domain:** workflow-orchestration (experience)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (9 controllers, 63 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: daidzai.openapi.yaml) |
| 3 | Wired via BFF? | Yes (5 clients) |
| 4 | Visible in UI? | Yes (45 refs) |
| 5 | Visible on mobile? | Yes (15 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (9 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/clinical/emergency/episode/[episodeId]/page.tsx`
- `ui/one-ui-shell/src/app/clinical/emergency/resus/[activationId]/page.tsx`
- `ui/one-ui-shell/src/app/emergency/page.tsx`
- `ui/one-ui-shell/src/app/emergency/services/page.tsx`
- `ui/one-ui-shell/src/app/emergency/sos/page.test.tsx`

## credential-verification-service

- **Path:** `services/credential-verification-service`
- **Domain:** finance (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (5 controllers, 15 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: credential-verification.openapi.yaml) |
| 3 | Wired via BFF? | Yes (2 clients) |
| 4 | Visible in UI? | Yes (9 refs) |
| 5 | Visible on mobile? | Yes (1 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/verify/credential/layout.tsx`
- `ui/one-ui-shell/src/app/verify/credential/page.tsx`
- `ui/one-ui-shell/src/components/credentials/CredentialVerificationWorkflowPanel.tsx`
- `ui/one-ui-shell/src/lib/__tests__/credential-verification-golden-thread.test.ts`
- `ui/one-ui-shell/src/lib/credentialVerifyPublic.test.ts`

## data-access-governance-service

- **Path:** `services/data-access-governance-service`
- **Domain:** intelligence (data)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (5 controllers, 15 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: data-access-governance.openapi.yaml) |
| 3 | Wired via BFF? | Yes (3 clients) |
| 4 | Visible in UI? | Yes (32 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/access/governance/page.tsx`
- `ui/one-ui-shell/src/app/admin/data-governance/page.tsx`
- `ui/one-ui-shell/src/app/dags/page.tsx`
- `ui/one-ui-shell/src/app/dags/policy/page.tsx`
- `ui/one-ui-shell/src/app/intelligence/page.tsx`

## data-governance-service

- **Path:** `services/data-governance-service`
- **Domain:** intelligence (data)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (6 controllers, 27 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: data-governance.openapi.yaml) |
| 3 | Wired via BFF? | Yes (8 clients) |
| 4 | Visible in UI? | Yes (9 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (8 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/data-governance/page.tsx`
- `ui/one-ui-shell/src/app/admin/page.tsx`
- `ui/one-ui-shell/src/app/ai-governance/page.tsx`
- `ui/one-ui-shell/src/app/data-intelligence/audit/page.tsx`
- `ui/one-ui-shell/src/app/data-intelligence/page.tsx`

## data-ingestion-service

- **Path:** `services/data-ingestion-service`
- **Domain:** intelligence (data)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 6 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: data-ingestion.openapi.yaml) |
| 3 | Wired via BFF? | Yes (2 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## data-pipeline-service

- **Path:** `services/data-pipeline-service`
- **Domain:** intelligence (data)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (4 controllers, 8 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: data-pipeline.openapi.yaml) |
| 3 | Wired via BFF? | Yes (3 clients) |
| 4 | Visible in UI? | Yes (4 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/data-intelligence/pipelines/page.tsx`
- `ui/one-ui-shell/src/hooks/queries/useDataPipelineWatermarks.ts`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`
- `ui/one-ui-shell/src/lib/routes.ts`

## data-warehouse-service

- **Path:** `services/data-warehouse-service`
- **Domain:** intelligence (data)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (4 controllers, 8 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: data-warehouse.openapi.yaml) |
| 3 | Wired via BFF? | Yes (4 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## developer-portal-service

- **Path:** `services/developer-portal-service`
- **Domain:** platform-ops (integration)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 19 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: developer-portal.openapi.yaml) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (5 refs) |
| 5 | Visible on mobile? | Yes (1 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/developer/page.tsx`
- `ui/one-ui-shell/src/components/navigation/sidebar-zones.ts`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`
- `ui/one-ui-shell/src/lib/routes.ts`

## dispatch-service

- **Path:** `services/dispatch-service`
- **Domain:** platform-ops (integration)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (4 controllers, 34 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: dispatch.openapi.yaml) |
| 3 | Wired via BFF? | Yes (23 clients) |
| 4 | Visible in UI? | Yes (121 refs) |
| 5 | Visible on mobile? | Yes (45 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (4 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/clinical/page.tsx`
- `ui/one-ui-shell/src/app/communication/approvals/page.tsx`
- `ui/one-ui-shell/src/app/core-transaction/page.tsx`
- `ui/one-ui-shell/src/app/diagnostics/lab-worklist/page.test.tsx`
- `ui/one-ui-shell/src/app/diagnostics/lab-worklist/page.tsx`

## document-service

- **Path:** `services/document-service`
- **Domain:** care-delivery (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 17 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: document-store.openapi.yaml) |
| 3 | Wired via BFF? | Yes (32 clients) |
| 4 | Visible in UI? | Yes (212 refs) |
| 5 | Visible on mobile? | Yes (151 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/access/page.tsx`
- `ui/one-ui-shell/src/app/admin/clinical-curation/page.test.tsx`
- `ui/one-ui-shell/src/app/admin/clinical-curation/page.tsx`
- `ui/one-ui-shell/src/app/admin/diagnostics-catalogue/page.tsx`
- `ui/one-ui-shell/src/app/admin/layout.tsx`

## experience-bff

- **Path:** `services/experience-bff`
- **Domain:** workflow-orchestration (experience)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (376 controllers, 3476 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: experience-bff.openapi.yaml) |
| 3 | Wired via BFF? | Yes (0 clients) |
| 4 | Visible in UI? | Yes (226 refs) |
| 5 | Visible on mobile? | Yes (95 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (45 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/clinical-curation/page.test.tsx`
- `ui/one-ui-shell/src/app/admin/clinical-curation/page.tsx`
- `ui/one-ui-shell/src/app/admin/data-export/page.tsx`
- `ui/one-ui-shell/src/app/admin/diagnostics-catalogue/page.tsx`
- `ui/one-ui-shell/src/app/admin/federation/page.tsx`

## fhir-gateway-service

- **Path:** `services/fhir-gateway-service`
- **Domain:** care-delivery (clinical)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 9 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: fhir-gateway.openapi.yaml) |
| 3 | Wired via BFF? | Yes (4 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## forms-service

- **Path:** `services/forms-service`
- **Domain:** clinical-knowledge (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 10 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: forms.openapi.yaml) |
| 3 | Wired via BFF? | Yes (9 clients) |
| 4 | Visible in UI? | Yes (55 refs) |
| 5 | Visible on mobile? | Yes (15 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/clinical-tools/forms/page.tsx`
- `ui/one-ui-shell/src/app/clinical-tools/page.tsx`
- `ui/one-ui-shell/src/app/clinical-tools/rules/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/documents/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/encounter/[encounterId]/page.test.tsx`

## general-ledger-service

- **Path:** `services/general-ledger-service`
- **Domain:** enterprise-resource (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (7 controllers, 28 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: general-ledger.openapi.yaml) |
| 3 | Wired via BFF? | Yes (3 clients) |
| 4 | Visible in UI? | Yes (5 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/erp/gl/page.tsx`
- `ui/one-ui-shell/src/app/erp/page.tsx`
- `ui/one-ui-shell/src/hooks/queries/useGeneralLedger.ts`
- `ui/one-ui-shell/src/lib/__tests__/phase6-service-completion-golden-thread.test.ts`
- `ui/one-ui-shell/src/lib/routes.ts`

## guidance-service

- **Path:** `services/guidance-service`
- **Domain:** clinical-knowledge (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (6 controllers, 39 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: guidance.openapi.yaml) |
| 3 | Wired via BFF? | Yes (16 clients) |
| 4 | Visible in UI? | Yes (218 refs) |
| 5 | Visible on mobile? | Yes (17 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (19 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/account-deletion/page.tsx`
- `ui/one-ui-shell/src/app/admin/clinical-curation/page.tsx`
- `ui/one-ui-shell/src/app/ai-governance/page.tsx`
- `ui/one-ui-shell/src/app/ask/page.tsx`
- `ui/one-ui-shell/src/app/auth/login/page.tsx`

## hr-payroll-service

- **Path:** `services/hr-payroll-service`
- **Domain:** enterprise-resource (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 13 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: hr-payroll.openapi.yaml) |
| 3 | Wired via BFF? | Yes (3 clients) |
| 4 | Visible in UI? | Yes (4 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/erp/hr/page.tsx`
- `ui/one-ui-shell/src/components/workspace-ops/HRShiftsPanel.tsx`
- `ui/one-ui-shell/src/hooks/queries/useHrPayroll.ts`
- `ui/one-ui-shell/src/lib/__tests__/phase6-service-completion-golden-thread.test.ts`

## identity-assurance-service

- **Path:** `services/identity-assurance-service`
- **Domain:** identity-governance (trust)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (5 controllers, 20 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: identity-assurance.openapi.yaml) |
| 3 | Wired via BFF? | Yes (22 clients) |
| 4 | Visible in UI? | Yes (19 refs) |
| 5 | Visible on mobile? | Yes (4 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/auth/register/assurance/page.tsx`
- `ui/one-ui-shell/src/app/auth/register/page.test.tsx`
- `ui/one-ui-shell/src/app/auth/register/page.tsx`
- `ui/one-ui-shell/src/app/citizen/page.tsx`
- `ui/one-ui-shell/src/app/citizen/wallet/identity/page.tsx`

## indawo-service

- **Path:** `services/indawo-service`
- **Domain:** registry-spine (registry)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (6 controllers, 50 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: indawo.openapi.yaml) |
| 3 | Wired via BFF? | Yes (7 clients) |
| 4 | Visible in UI? | Yes (23 refs) |
| 5 | Visible on mobile? | Yes (5 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (12 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/indawo/field-teams/page.tsx`
- `ui/one-ui-shell/src/app/indawo/outbreaks/page.tsx`
- `ui/one-ui-shell/src/app/indawo/page.tsx`
- `ui/one-ui-shell/src/app/indawo/surveillance/page.tsx`
- `ui/one-ui-shell/src/app/production-command-centre/page.tsx`

## inpatient-service

- **Path:** `services/inpatient-service`
- **Domain:** care-delivery (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (10 controllers, 171 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: inpatient.openapi.yaml) |
| 3 | Wired via BFF? | Yes (19 clients) |
| 4 | Visible in UI? | Yes (76 refs) |
| 5 | Visible on mobile? | Yes (15 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (44 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/beds/page.tsx`
- `ui/one-ui-shell/src/app/citizen/inpatient/[admissionRef]/page.test.tsx`
- `ui/one-ui-shell/src/app/citizen/inpatient/[admissionRef]/page.tsx`
- `ui/one-ui-shell/src/app/clinical/inpatient/admissions/[admissionId]/page.tsx`
- `ui/one-ui-shell/src/app/clinical/inpatient/admissions/new/page.tsx`

## integration-hub

- **Path:** `services/integration-hub`
- **Domain:** interoperability (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (5 controllers, 35 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: integration-hub.openapi.yaml) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (12 refs) |
| 5 | Visible on mobile? | Yes (1 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (4 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/integration-status/page.tsx`
- `ui/one-ui-shell/src/app/admin/integration-templates/page.tsx`
- `ui/one-ui-shell/src/app/admin/page.tsx`
- `ui/one-ui-shell/src/app/data-intelligence/pipelines/page.tsx`
- `ui/one-ui-shell/src/app/developer/event-catalogue/page.tsx`

## inventory-elmis-adapter

- **Path:** `services/inventory-elmis-adapter`
- **Domain:** care-delivery (clinical)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 8 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: inventory-elmis.openapi.yaml) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## inventory-service

- **Path:** `services/inventory-service`
- **Domain:** care-delivery (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (22 controllers, 132 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: inventory.openapi.yaml) |
| 3 | Wired via BFF? | Yes (11 clients) |
| 4 | Visible in UI? | Yes (111 refs) |
| 5 | Visible on mobile? | Yes (26 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (16 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/beds/page.tsx`
- `ui/one-ui-shell/src/app/admin/data-export/page.tsx`
- `ui/one-ui-shell/src/app/admin/keys/page.tsx`
- `ui/one-ui-shell/src/app/admin/page.tsx`
- `ui/one-ui-shell/src/app/clinical/dictation/page.tsx`

## iot-ingestion-service

- **Path:** `services/iot-ingestion-service`
- **Domain:** platform-ops (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 9 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: iot-ingestion.openapi.yaml) |
| 3 | Wired via BFF? | Yes (3 clients) |
| 4 | Visible in UI? | Yes (3 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/operations/equipment/iot/page.tsx`
- `ui/one-ui-shell/src/hooks/queries/useDevices.ts`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## jobs-service

- **Path:** `services/jobs-service`
- **Domain:** interoperability (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 9 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: jobs.openapi.yaml) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (17 refs) |
| 5 | Visible on mobile? | Yes (2 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/data-export/page.test.tsx`
- `ui/one-ui-shell/src/app/admin/data-export/page.tsx`
- `ui/one-ui-shell/src/app/finance/commerce-integrations/page.tsx`
- `ui/one-ui-shell/src/app/operations/vito/page.tsx`
- `ui/one-ui-shell/src/app/operations/vito/print/page.tsx`

## landela-adapter-service

- **Path:** `services/landela-adapter-service`
- **Domain:** interoperability (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (4 controllers, 16 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: landela-adapter.openapi.yaml) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (9 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/access/page.tsx`
- `ui/one-ui-shell/src/app/clinical-tools/page.tsx`
- `ui/one-ui-shell/src/app/landela/page.tsx`
- `ui/one-ui-shell/src/app/registry/intake/page.tsx`
- `ui/one-ui-shell/src/components/ehr/EncounterDocumentsSheet.tsx`

## learning-service

- **Path:** `services/learning-service`
- **Domain:** workflow-orchestration (experience)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (29 controllers, 186 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: learning.openapi.yaml) |
| 3 | Wired via BFF? | Yes (7 clients) |
| 4 | Visible in UI? | Yes (165 refs) |
| 5 | Visible on mobile? | Yes (45 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (30 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/about/page.tsx`
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/auth/context-chooser/page.tsx`
- `ui/one-ui-shell/src/app/clinical-tools/page.tsx`
- `ui/one-ui-shell/src/app/data-intelligence/reports/page.tsx`

## live-service

- **Path:** `services/live-service`
- **Domain:** live-events-broadcast (experience)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (12 controllers, 79 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: impilo-live.openapi.yaml) |
| 3 | Wired via BFF? | Yes (2 clients) |
| 4 | Visible in UI? | Yes (529 refs) |
| 5 | Visible on mobile? | Yes (104 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (5 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/beds/page.tsx`
- `ui/one-ui-shell/src/app/admin/comms-ops/page.test.tsx`
- `ui/one-ui-shell/src/app/admin/comms-ops/page.tsx`
- `ui/one-ui-shell/src/app/admin/hpa-enrichment/page.tsx`
- `ui/one-ui-shell/src/app/admin/integration-status/page.tsx`

## llm-orchestration-service

- **Path:** `services/llm-orchestration-service`
- **Domain:** platform-ops (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 6 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: none) |
| 3 | Wired via BFF? | Yes (6 clients) |
| 4 | Visible in UI? | Yes (0 refs) |
| 5 | Visible on mobile? | Yes (1 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

## madi-service

- **Path:** `services/madi-service`
- **Domain:** platform-ops (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (14 controllers, 86 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: madi.openapi.yaml) |
| 3 | Wired via BFF? | Yes (2 clients) |
| 4 | Visible in UI? | Yes (77 refs) |
| 5 | Visible on mobile? | Yes (28 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (9 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/ehr/[patientId]/orders/page.tsx`
- `ui/one-ui-shell/src/app/madi/blood-bank/crossmatch/page.tsx`
- `ui/one-ui-shell/src/app/madi/blood-bank/fridges/page.tsx`
- `ui/one-ui-shell/src/app/madi/blood-bank/issue/page.tsx`
- `ui/one-ui-shell/src/app/madi/blood-bank/orders/page.tsx`

## msika-apps-service

- **Path:** `services/msika-apps-service`
- **Domain:** marketplace (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (0 controllers, 33 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: msika-apps.openapi.yaml) |
| 3 | Wired via BFF? | Yes (4 clients) |
| 4 | Visible in UI? | Yes (24 refs) |
| 5 | Visible on mobile? | Yes (5 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/developer/event-catalogue/page.tsx`
- `ui/one-ui-shell/src/app/developer/page.tsx`
- `ui/one-ui-shell/src/app/finance/commerce-integrations/page.tsx`
- `ui/one-ui-shell/src/app/marketplace/apps/[itemCode]/page.tsx`
- `ui/one-ui-shell/src/app/marketplace/apps/admin/activation/page.tsx`

## msika-flow-service

- **Path:** `services/msika-flow-service`
- **Domain:** marketplace (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (17 controllers, 104 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: msika-flow.openapi.yaml) |
| 3 | Wired via BFF? | Yes (11 clients) |
| 4 | Visible in UI? | Yes (38 refs) |
| 5 | Visible on mobile? | Yes (3 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (11 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/finance/commerce-integrations/page.tsx`
- `ui/one-ui-shell/src/app/marketplace/cart/page.tsx`
- `ui/one-ui-shell/src/app/marketplace/catalog/page.test.tsx`
- `ui/one-ui-shell/src/app/marketplace/catalog/page.tsx`
- `ui/one-ui-shell/src/app/marketplace/ops/page.test.tsx`

## msika-service

- **Path:** `services/msika-service`
- **Domain:** marketplace (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (16 controllers, 83 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: msika-core.openapi.yaml) |
| 3 | Wired via BFF? | Yes (27 clients) |
| 4 | Visible in UI? | Yes (68 refs) |
| 5 | Visible on mobile? | Yes (10 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (9 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/sidecar-retirement/page.test.tsx`
- `ui/one-ui-shell/src/app/clinical-tools/page.tsx`
- `ui/one-ui-shell/src/app/finance/billing/[id]/page.tsx`
- `ui/one-ui-shell/src/app/finance/commerce-integrations/page.test.tsx`
- `ui/one-ui-shell/src/app/finance/commerce-integrations/page.tsx`

## mushe-wallet-service

- **Path:** `services/mushe-wallet-service`
- **Domain:** finance (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (9 controllers, 59 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: mushe-wallet.openapi.yaml) |
| 3 | Wired via BFF? | Yes (9 clients) |
| 4 | Visible in UI? | Yes (8 refs) |
| 5 | Visible on mobile? | Yes (10 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (11 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/finance/billing/[id]/page.tsx`
- `ui/one-ui-shell/src/app/finance/mushex-platform/page.tsx`
- `ui/one-ui-shell/src/app/marketplace/cart/page.tsx`
- `ui/one-ui-shell/src/components/navigation/ExperienceSidebar.tsx`
- `ui/one-ui-shell/src/components/payment/PaymentMethodPicker.tsx`

## mushex-service

- **Path:** `services/mushex-service`
- **Domain:** finance (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (17 controllers, 77 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: mushex.openapi.yaml) |
| 3 | Wired via BFF? | Yes (14 clients) |
| 4 | Visible in UI? | Yes (129 refs) |
| 5 | Visible on mobile? | Yes (24 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (9 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/budgets/[budgetId]/page.tsx`
- `ui/one-ui-shell/src/app/budgets/page.tsx`
- `ui/one-ui-shell/src/app/citizen/wallet/page.test.tsx`
- `ui/one-ui-shell/src/app/citizen/wallet/page.tsx`
- `ui/one-ui-shell/src/app/citizen/wallet/payments/page.test.tsx`

## mvumo-service

- **Path:** `services/mvumo-service`
- **Domain:** identity-governance (trust)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 50 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: mvumo.openapi.yaml) |
| 3 | Wired via BFF? | Yes (15 clients) |
| 4 | Visible in UI? | Yes (43 refs) |
| 5 | Visible on mobile? | Yes (8 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (9 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/citizen/wallet/dependants/page.tsx`
- `ui/one-ui-shell/src/app/citizen/wallet/page.test.tsx`
- `ui/one-ui-shell/src/app/citizen/wallet/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/preferences/communications/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/summary/page.tsx`

## national-data-repository-service

- **Path:** `services/national-data-repository-service`
- **Domain:** intelligence (data)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 7 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: national-data-repository.openapi.yaml) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## ndila-service

- **Path:** `services/ndila-service`
- **Domain:** interoperability (integration)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (17 controllers, 98 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: ndila.openapi.yaml) |
| 3 | Wired via BFF? | Yes (11 clients) |
| 4 | Visible in UI? | Yes (98 refs) |
| 5 | Visible on mobile? | Yes (18 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (7 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/hpa-enrichment/page.tsx`
- `ui/one-ui-shell/src/app/discover/facilities/page.tsx`
- `ui/one-ui-shell/src/app/emergency/page.tsx`
- `ui/one-ui-shell/src/app/emergency/services/page.tsx`
- `ui/one-ui-shell/src/app/enterprise/oversight/page.tsx`

## ndr-service

- **Path:** `services/ndr-service`
- **Domain:** intelligence (data)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (4 controllers, 9 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: ndr.openapi.yaml) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (2 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (4 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/components/data-intelligence/NdrWarehouseQueryPanel.tsx`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## nhume-service

- **Path:** `services/nhume-service`
- **Domain:** interoperability (integration)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (7 controllers, 97 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: nhume.openapi.yaml) |
| 3 | Wired via BFF? | Yes (8 clients) |
| 4 | Visible in UI? | Yes (64 refs) |
| 5 | Visible on mobile? | Yes (23 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (8 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/madi/central-bank/page.tsx`
- `ui/one-ui-shell/src/app/madi/logistics/page.tsx`
- `ui/one-ui-shell/src/app/madi/page.tsx`
- `ui/one-ui-shell/src/app/marketplace/orders/[id]/page.tsx`
- `ui/one-ui-shell/src/app/ndila/page.tsx`

## notification-service

- **Path:** `services/notification-service`
- **Domain:** interoperability (integration)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (6 controllers, 32 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: notification.openapi.yaml) |
| 3 | Wired via BFF? | Yes (28 clients) |
| 4 | Visible in UI? | Yes (96 refs) |
| 5 | Visible on mobile? | Yes (24 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (18 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/access/page.tsx`
- `ui/one-ui-shell/src/app/admin/comms-ops/page.tsx`
- `ui/one-ui-shell/src/app/admin/notifications/templates/page.tsx`
- `ui/one-ui-shell/src/app/admin/page.tsx`
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`

## observability-service

- **Path:** `services/observability-service`
- **Domain:** platform-ops (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (6 controllers, 17 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: observability.openapi.yaml) |
| 3 | Wired via BFF? | Yes (4 clients) |
| 4 | Visible in UI? | Yes (8 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/system-monitor/page.tsx`
- `ui/one-ui-shell/src/app/not-found.tsx`
- `ui/one-ui-shell/src/hooks/queries/useAdminObservability.ts`
- `ui/one-ui-shell/src/lib/client-observability.test.ts`
- `ui/one-ui-shell/src/lib/client-observability.ts`

## offline-edge-service

- **Path:** `services/offline-edge-service`
- **Domain:** platform-ops (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (6 controllers, 16 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: offline-edge.openapi.yaml) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (2 refs) |
| 5 | Visible on mobile? | Yes (3 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (8 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/work/telemonitoring/chw/page.tsx`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## offline-sync-service

- **Path:** `services/offline-sync-service`
- **Domain:** interoperability (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 12 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: offline-sync.openapi.yaml) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (3 refs) |
| 5 | Visible on mobile? | Yes (1 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/clinical-tools/page.tsx`
- `ui/one-ui-shell/src/data/workSurfaceModules.ts`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## oros-service

- **Path:** `services/oros-service`
- **Domain:** care-delivery (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (24 controllers, 154 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: oros.openapi.yaml) |
| 3 | Wired via BFF? | Yes (15 clients) |
| 4 | Visible in UI? | Yes (97 refs) |
| 5 | Visible on mobile? | Yes (19 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (18 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/diagnostics-catalogue/page.tsx`
- `ui/one-ui-shell/src/app/admin/integrations/page.tsx`
- `ui/one-ui-shell/src/app/admin/system-monitor/page.tsx`
- `ui/one-ui-shell/src/app/clinical/inpatient/rounds/page.tsx`
- `ui/one-ui-shell/src/app/developer/api-catalog/page.tsx`

## pacs-adapter-service

- **Path:** `services/pacs-adapter-service`
- **Domain:** care-delivery (clinical)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 39 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: pacs-adapter.openapi.yaml) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (15 refs) |
| 5 | Visible on mobile? | Yes (7 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (7 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/system-monitor/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/imaging/page.test.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/imaging/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/imaging/viewer/page.tsx`
- `ui/one-ui-shell/src/app/home/page.test.tsx`

## patient-safety-service

- **Path:** `services/patient-safety-service`
- **Domain:** pharmacovigilance (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (5 controllers, 30 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: patient-safety.openapi.yaml) |
| 3 | Wired via BFF? | Yes (4 clients) |
| 4 | Visible in UI? | Yes (8 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/work/patient-safety/cases/[caseId]/page.tsx`
- `ui/one-ui-shell/src/app/work/patient-safety/mcaz/page.tsx`
- `ui/one-ui-shell/src/app/work/patient-safety/new/page.tsx`
- `ui/one-ui-shell/src/app/work/patient-safety/page.tsx`
- `ui/one-ui-shell/src/app/work/patient-safety/reports/[reportId]/page.tsx`

## pct-service

- **Path:** `services/pct-service`
- **Domain:** care-delivery (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (47 controllers, 363 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: pct.openapi.yaml) |
| 3 | Wired via BFF? | Yes (58 clients) |
| 4 | Visible in UI? | Yes (599 refs) |
| 5 | Visible on mobile? | Yes (138 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (99 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/access/page.tsx`
- `ui/one-ui-shell/src/app/admin/beds/page.test.tsx`
- `ui/one-ui-shell/src/app/admin/beds/page.tsx`
- `ui/one-ui-shell/src/app/admin/clinical-curation/page.test.tsx`
- `ui/one-ui-shell/src/app/admin/clinical-curation/page.tsx`

## pharmacy-elmis-adapter

- **Path:** `services/pharmacy-elmis-adapter`
- **Domain:** care-delivery (clinical)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 7 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: pharmacy-elmis.openapi.yaml) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## pharmacy-service

- **Path:** `services/pharmacy-service`
- **Domain:** care-delivery (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (11 controllers, 45 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: pharmacy.openapi.yaml) |
| 3 | Wired via BFF? | Yes (12 clients) |
| 4 | Visible in UI? | Yes (105 refs) |
| 5 | Visible on mobile? | Yes (15 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (8 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/clinical/control-tower/page.tsx`
- `ui/one-ui-shell/src/app/clinical/inpatient/admissions/[admissionId]/page.tsx`
- `ui/one-ui-shell/src/app/clinical/inpatient/discharge/[admissionId]/page.tsx`
- `ui/one-ui-shell/src/app/clinical/inpatient/discharge-board/page.tsx`
- `ui/one-ui-shell/src/app/clinical/inpatient/page.tsx`

## procurement-service

- **Path:** `services/procurement-service`
- **Domain:** enterprise-resource (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 21 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: procurement.openapi.yaml) |
| 3 | Wired via BFF? | Yes (5 clients) |
| 4 | Visible in UI? | Yes (28 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/enterprise/page.tsx`
- `ui/one-ui-shell/src/app/enterprise/warehousing/page.tsx`
- `ui/one-ui-shell/src/app/erp/layout.tsx`
- `ui/one-ui-shell/src/app/erp/page.tsx`
- `ui/one-ui-shell/src/app/erp/procurement/page.tsx`

## product-registry-service

- **Path:** `services/product-registry-service`
- **Domain:** registry-spine (registry)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (4 controllers, 14 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: product-registry.openapi.yaml) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (13 refs) |
| 5 | Visible on mobile? | Yes (5 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/ehr/[patientId]/orders/page.test.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/orders/page.tsx`
- `ui/one-ui-shell/src/app/finance/commerce-integrations/page.test.tsx`
- `ui/one-ui-shell/src/app/finance/commerce-integrations/page.tsx`
- `ui/one-ui-shell/src/app/marketplace/catalog/page.test.tsx`

## referral-service

- **Path:** `services/referral-service`
- **Domain:** platform-ops (integration)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 13 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: referral.openapi.yaml) |
| 3 | Wired via BFF? | Yes (23 clients) |
| 4 | Visible in UI? | Yes (141 refs) |
| 5 | Visible on mobile? | Yes (26 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/system-monitor/page.tsx`
- `ui/one-ui-shell/src/app/citizen/record-sharing/page.tsx`
- `ui/one-ui-shell/src/app/citizen/virtual-care/page.tsx`
- `ui/one-ui-shell/src/app/clinical/control-tower/page.tsx`
- `ui/one-ui-shell/src/app/clinical/inpatient/discharge/[admissionId]/page.tsx`

## reporting-service

- **Path:** `services/reporting-service`
- **Domain:** intelligence (data)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 7 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: reporting.openapi.yaml) |
| 3 | Wired via BFF? | Yes (13 clients) |
| 4 | Visible in UI? | Yes (61 refs) |
| 5 | Visible on mobile? | Yes (8 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/coverage/page.tsx`
- `ui/one-ui-shell/src/app/data-intelligence/page.tsx`
- `ui/one-ui-shell/src/app/data-intelligence/pipelines/page.tsx`
- `ui/one-ui-shell/src/app/data-intelligence/reports/page.tsx`

## rito-quality-safety-service

- **Path:** `services/rito-quality-safety-service`
- **Domain:** workflow-orchestration (experience)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (12 controllers, 95 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: rito-quality-safety.openapi.yaml) |
| 3 | Wired via BFF? | Yes (4 clients) |
| 4 | Visible in UI? | Yes (32 refs) |
| 5 | Visible on mobile? | Yes (12 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (7 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/feedback/visit/[encounterRef]/page.tsx`
- `ui/one-ui-shell/src/app/get-involved/page.tsx`
- `ui/one-ui-shell/src/app/khuluma/feedback/page.tsx`
- `ui/one-ui-shell/src/app/marketplace/store/listing/[id]/page.tsx`
- `ui/one-ui-shell/src/app/my-life/feedback/[caseId]/page.tsx`

## participation-service

- **Path:** `services/participation-service`
- **Domain:** workflow-orchestration (experience)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 20 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: participation.openapi.yaml) |
| 3 | Wired via BFF? | Yes (2 clients) |
| 4 | Visible in UI? | Yes (22 refs) |
| 5 | Visible on mobile? | Yes (3 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/contact/page.tsx`
- `ui/one-ui-shell/src/app/get-involved/idea/page.tsx`
- `ui/one-ui-shell/src/app/get-involved/page.tsx`
- `ui/one-ui-shell/src/app/get-involved/test/page.tsx`
- `ui/one-ui-shell/src/app/wellness/page.tsx`

## procedures-service

- **Path:** `services/procedures-service`
- **Domain:** care-delivery (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (7 controllers, 21 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: procedures.openapi.yaml) |
| 3 | Wired via BFF? | Yes (7 clients) |
| 4 | Visible in UI? | Yes (38 refs) |
| 5 | Visible on mobile? | Yes (2 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (10 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/data-export/page.tsx`
- `ui/one-ui-shell/src/app/clinical-tools/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/discharge/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/orders/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/procedures/[episodeId]/page.tsx`

## surgery-service

- **Path:** `services/surgery-service`
- **Domain:** care-delivery (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (10 controllers, 39 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: surgery.openapi.yaml) |
| 3 | Wired via BFF? | Yes (6 clients) |
| 4 | Visible in UI? | Yes (31 refs) |
| 5 | Visible on mobile? | Yes (4 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (8 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/clinical-tools/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/medicine/specialty/[specialty]/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/procedures/page.test.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/procedures/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/workspace/[specialty]/page.tsx`

## telemonitoring-service

- **Path:** `services/telemonitoring-service`
- **Domain:** care-delivery (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (5 controllers, 38 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: telemonitoring.openapi.yaml) |
| 3 | Wired via BFF? | Yes (3 clients) |
| 4 | Visible in UI? | Yes (10 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (5 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/my/monitoring/page.tsx`
- `ui/one-ui-shell/src/app/work/telemonitoring/chw/page.tsx`
- `ui/one-ui-shell/src/app/work/telemonitoring/page.tsx`
- `ui/one-ui-shell/src/components/telemonitoring/AccountableClosureForm.test.tsx`
- `ui/one-ui-shell/src/components/telemonitoring/AccountableClosureForm.tsx`

## mental-health-service

- **Path:** `services/mental-health-service`
- **Domain:** care-delivery (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 28 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: mental-health.openapi.yaml) |
| 3 | Wired via BFF? | Yes (2 clients) |
| 4 | Visible in UI? | Yes (22 refs) |
| 5 | Visible on mobile? | Yes (1 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/citizen/virtual-care/request/page.test.tsx`
- `ui/one-ui-shell/src/app/discover/services/page.tsx`
- `ui/one-ui-shell/src/app/discover/virtual-care/page.test.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/advance-directives/page.test.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/advance-directives/page.tsx`

## rtc-gateway-service

- **Path:** `services/rtc-gateway-service`
- **Domain:** platform-ops (integration)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 19 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: rtc-gateway.openapi.yaml) |
| 3 | Wired via BFF? | Yes (4 clients) |
| 4 | Visible in UI? | Yes (143 refs) |
| 5 | Visible on mobile? | Yes (19 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (5 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/system-monitor/page.tsx`
- `ui/one-ui-shell/src/app/auth/context-chooser/page.tsx`
- `ui/one-ui-shell/src/app/citizen/my-care/page.tsx`
- `ui/one-ui-shell/src/app/citizen/virtual-care/page.test.tsx`
- `ui/one-ui-shell/src/app/citizen/virtual-care/page.tsx`

## rules-service

- **Path:** `services/rules-service`
- **Domain:** clinical-knowledge (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 11 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: rules.openapi.yaml) |
| 3 | Wired via BFF? | Yes (25 clients) |
| 4 | Visible in UI? | Yes (78 refs) |
| 5 | Visible on mobile? | Yes (5 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/data-governance/page.tsx`
- `ui/one-ui-shell/src/app/admin/page.tsx`
- `ui/one-ui-shell/src/app/admin/policies/page.tsx`
- `ui/one-ui-shell/src/app/ai-governance/page.tsx`
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`

## scheduling-service

- **Path:** `services/scheduling-service`
- **Domain:** care-delivery (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 21 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: scheduling.openapi.yaml) |
| 3 | Wired via BFF? | Yes (9 clients) |
| 4 | Visible in UI? | Yes (78 refs) |
| 5 | Visible on mobile? | Yes (113 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/auth/login/page.tsx`
- `ui/one-ui-shell/src/app/communication/page.tsx`
- `ui/one-ui-shell/src/app/home/appointments/[appointmentId]/page.tsx`
- `ui/one-ui-shell/src/app/home/appointments/page.test.tsx`
- `ui/one-ui-shell/src/app/home/appointments/page.tsx`

## schema-registry-service

- **Path:** `services/schema-registry-service`
- **Domain:** platform-ops (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 10 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: schema-registry.openapi.yaml) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## search-service

- **Path:** `services/search-service`
- **Domain:** intelligence (data)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 5 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: search.openapi.yaml) |
| 3 | Wired via BFF? | Yes (42 clients) |
| 4 | Visible in UI? | Yes (536 refs) |
| 5 | Visible on mobile? | Yes (68 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/access/page.tsx`
- `ui/one-ui-shell/src/app/admin/audit/[id]/page.tsx`
- `ui/one-ui-shell/src/app/admin/audit/page.tsx`
- `ui/one-ui-shell/src/app/admin/beds/page.test.tsx`
- `ui/one-ui-shell/src/app/admin/beds/page.tsx`

## security-hardening-service

- **Path:** `services/security-hardening-service`
- **Domain:** platform-ops (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (4 controllers, 10 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: security-hardening.openapi.yaml) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## share-slip-service

- **Path:** `services/share-slip-service`
- **Domain:** finance (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 13 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: share-slip.openapi.yaml) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (7 refs) |
| 5 | Visible on mobile? | Yes (113 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/diagnostics/intake/qr/page.tsx`
- `ui/one-ui-shell/src/lib/__tests__/phase6-service-completion-golden-thread.test.ts`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`
- `ui/one-ui-shell/src/lib/shareSlipPublic.test.ts`
- `ui/one-ui-shell/src/lib/shareSlipPublic.ts`

## simba-service

- **Path:** `services/simba-service`
- **Domain:** wellness-personal-health-data (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (40 controllers, 193 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: simba.openapi.yaml) |
| 3 | Wired via BFF? | Yes (12 clients) |
| 4 | Visible in UI? | Yes (120 refs) |
| 5 | Visible on mobile? | Yes (40 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (14 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/ask/page.tsx`
- `ui/one-ui-shell/src/app/auth/register/assurance/page.tsx`
- `ui/one-ui-shell/src/app/auth/register/status/page.tsx`
- `ui/one-ui-shell/src/app/communities/page.tsx`
- `ui/one-ui-shell/src/app/discover/services/page.tsx`

## support-service

- **Path:** `services/support-service`
- **Domain:** platform-ops (integration)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (9 controllers, 33 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: support.openapi.yaml) |
| 3 | Wired via BFF? | Yes (55 clients) |
| 4 | Visible in UI? | Yes (220 refs) |
| 5 | Visible on mobile? | Yes (42 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (5 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/account-deletion/page.tsx`
- `ui/one-ui-shell/src/app/admin/beds/page.tsx`
- `ui/one-ui-shell/src/app/ask/page.tsx`
- `ui/one-ui-shell/src/app/auth/mfa/page.tsx`
- `ui/one-ui-shell/src/app/auth/register/page.tsx`

## surveillance-service

- **Path:** `services/surveillance-service`
- **Domain:** public-health-surveillance (data)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (7 controllers, 67 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: surveillance.openapi.yaml) |
| 3 | Wired via BFF? | Yes (9 clients) |
| 4 | Visible in UI? | Yes (32 refs) |
| 5 | Visible on mobile? | Yes (10 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (12 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/enterprise/oversight/page.tsx`
- `ui/one-ui-shell/src/app/home/page.tsx`
- `ui/one-ui-shell/src/app/indawo/page.tsx`
- `ui/one-ui-shell/src/app/indawo/surveillance/page.tsx`

## tshepo-audit-service

- **Path:** `services/tshepo-audit-service`
- **Domain:** identity-governance (trust)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (6 controllers, 16 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: tshepo-audit.openapi.yaml) |
| 3 | Wired via BFF? | Yes (11 clients) |
| 4 | Visible in UI? | Yes (21 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/audit/[id]/page.tsx`
- `ui/one-ui-shell/src/app/admin/audit/page.tsx`
- `ui/one-ui-shell/src/app/admin/page.test.tsx`
- `ui/one-ui-shell/src/app/admin/page.tsx`
- `ui/one-ui-shell/src/app/admin/system-monitor/page.tsx`

## tshepo-authz-service

- **Path:** `services/tshepo-authz-service`
- **Domain:** identity-governance (trust)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (13 controllers, 53 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: tshepo-authz.openapi.yaml) |
| 3 | Wired via BFF? | Yes (10 clients) |
| 4 | Visible in UI? | Yes (18 refs) |
| 5 | Visible on mobile? | Yes (3 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (59 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/break-glass/page.tsx`
- `ui/one-ui-shell/src/app/admin/consent/page.tsx`
- `ui/one-ui-shell/src/app/admin/policies/page.tsx`
- `ui/one-ui-shell/src/app/registry/trust/page.tsx`
- `ui/one-ui-shell/src/components/administration-governance/GdhcnReadinessBoard.tsx`

## tshepo-consent-service

- **Path:** `services/tshepo-consent-service`
- **Domain:** identity-governance (trust)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (5 controllers, 18 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: tshepo-consent.openapi.yaml) |
| 3 | Wired via BFF? | Yes (11 clients) |
| 4 | Visible in UI? | Yes (35 refs) |
| 5 | Visible on mobile? | Yes (1 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/auth/login/page.test.tsx`
- `ui/one-ui-shell/src/app/auth/login/passkey/callback/page.tsx`
- `ui/one-ui-shell/src/app/auth/login/passkey/passkey-flow.test.tsx`
- `ui/one-ui-shell/src/app/auth/login/provider-id/page.tsx`
- `ui/one-ui-shell/src/app/auth/login/scan/page.tsx`

## tshepo-identity-service

- **Path:** `services/tshepo-identity-service`
- **Domain:** identity-governance (trust)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (7 controllers, 26 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: tshepo-identity.openapi.yaml) |
| 3 | Wired via BFF? | Yes (7 clients) |
| 4 | Visible in UI? | Yes (57 refs) |
| 5 | Visible on mobile? | Yes (8 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (5 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/auth/context-chooser/page.tsx`
- `ui/one-ui-shell/src/app/auth/register/assurance/page.tsx`
- `ui/one-ui-shell/src/app/auth/register/page.test.tsx`
- `ui/one-ui-shell/src/app/auth/register/page.tsx`

## tshepo-keys-service

- **Path:** `services/tshepo-keys-service`
- **Domain:** identity-governance (trust)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (6 controllers, 21 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: tshepo-keys.openapi.yaml) |
| 3 | Wired via BFF? | Yes (2 clients) |
| 4 | Visible in UI? | Yes (8 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/keys/page.tsx`
- `ui/one-ui-shell/src/app/admin/page.test.tsx`
- `ui/one-ui-shell/src/app/admin/page.tsx`
- `ui/one-ui-shell/src/app/registry/trust/page.tsx`
- `ui/one-ui-shell/src/components/ZoneNavigation.tsx`

## tshepo-offline-service

- **Path:** `services/tshepo-offline-service`
- **Domain:** identity-governance (trust)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (5 controllers, 19 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: tshepo-offline.openapi.yaml) |
| 3 | Wired via BFF? | Yes (4 clients) |
| 4 | Visible in UI? | Yes (5 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/clinical-tools/page.tsx`
- `ui/one-ui-shell/src/components/clinical/OfflineClinicalQueueOrchestrationPanel.test.tsx`
- `ui/one-ui-shell/src/components/clinical/OfflineClinicalQueueOrchestrationPanel.tsx`
- `ui/one-ui-shell/src/hooks/queries/useOfflineClinicalQueue.ts`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## abis-service

- **Path:** `services/abis-service`
- **Domain:** biometric-identity (trust)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 14 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: abis.openapi.yaml) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (16 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/auth/login/scan/page.tsx`
- `ui/one-ui-shell/src/app/finance/settlements/page.test.tsx`
- `ui/one-ui-shell/src/app/marketplace/pickup/page.test.tsx`
- `ui/one-ui-shell/src/app/operations/vito/adjudication/page.tsx`
- `ui/one-ui-shell/src/app/queue/walk-in/page.test.tsx`

## matcher-engine

- **Path:** `services/matcher-engine`
- **Domain:** biometric-identity (trust)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 5 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: none) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | No (0 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/hooks/queries/useAbisBiometric.ts`

## tshepo-service

- **Path:** `services/tshepo-service`
- **Domain:** identity-governance (trust)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (8 controllers, 26 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: tshepo.openapi.yaml) |
| 3 | Wired via BFF? | Yes (44 clients) |
| 4 | Visible in UI? | Yes (54 refs) |
| 5 | Visible on mobile? | Yes (6 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (12 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/about/page.tsx`
- `ui/one-ui-shell/src/app/admin/keys/page.tsx`
- `ui/one-ui-shell/src/app/admin/page.test.tsx`
- `ui/one-ui-shell/src/app/admin/page.tsx`
- `ui/one-ui-shell/src/app/developer/api-catalog/page.tsx`

## tuso-service

- **Path:** `services/tuso-service`
- **Domain:** registry-spine (registry)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (54 controllers, 334 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: tuso.openapi.yaml) |
| 3 | Wired via BFF? | Yes (29 clients) |
| 4 | Visible in UI? | Yes (92 refs) |
| 5 | Visible on mobile? | Yes (13 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (53 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/facility-imports/[runId]/page.tsx`
- `ui/one-ui-shell/src/app/admin/facility-imports/page.tsx`
- `ui/one-ui-shell/src/app/admin/hpa-enrichment/page.tsx`
- `ui/one-ui-shell/src/app/admin/queues/page.tsx`
- `ui/one-ui-shell/src/app/developer/api-catalog/page.tsx`

## ubomi-service

- **Path:** `services/ubomi-service`
- **Domain:** registry-spine (registry)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (4 controllers, 15 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: ubomi.openapi.yaml) |
| 3 | Wired via BFF? | Yes (4 clients) |
| 4 | Visible in UI? | Yes (14 refs) |
| 5 | Visible on mobile? | Yes (9 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (5 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/ubomi/page.tsx`
- `ui/one-ui-shell/src/components/registry/CrvsUbomiOrchestrationRail.test.tsx`
- `ui/one-ui-shell/src/components/registry/CrvsUbomiOrchestrationRail.tsx`
- `ui/one-ui-shell/src/config/serviceBranding.ts`
- `ui/one-ui-shell/src/features/production-command-centre/tile-registry.ts`

## varapi-service

- **Path:** `services/varapi-service`
- **Domain:** registry-spine (registry)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (50 controllers, 298 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: varapi.openapi.yaml) |
| 3 | Wired via BFF? | Yes (52 clients) |
| 4 | Visible in UI? | Yes (81 refs) |
| 5 | Visible on mobile? | Yes (10 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (40 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/hpa-enrichment/page.tsx`
- `ui/one-ui-shell/src/app/admin/workforce-intake/page.tsx`
- `ui/one-ui-shell/src/app/developer/api-catalog/page.tsx`
- `ui/one-ui-shell/src/app/developer/sandbox/page.tsx`
- `ui/one-ui-shell/src/app/home/credentials/page.tsx`

## vito-service

- **Path:** `services/vito-service`
- **Domain:** registry-spine (registry)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (28 controllers, 153 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: vito.openapi.yaml) |
| 3 | Wired via BFF? | Yes (38 clients) |
| 4 | Visible in UI? | Yes (102 refs) |
| 5 | Visible on mobile? | Yes (11 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (39 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/auth/register/status/page.tsx`
- `ui/one-ui-shell/src/app/citizen/delegated-pickup/page.tsx`
- `ui/one-ui-shell/src/app/citizen/id-recovery/page.tsx`
- `ui/one-ui-shell/src/app/citizen/page.tsx`

## wellness-service

- **Path:** `services/wellness-service`
- **Domain:** wellness-compatibility-alias (enterprise)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (4 controllers, 50 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: wellness.openapi.yaml) |
| 3 | Wired via BFF? | Yes (14 clients) |
| 4 | Visible in UI? | Yes (123 refs) |
| 5 | Visible on mobile? | Yes (43 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (4 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/ask/page.tsx`
- `ui/one-ui-shell/src/app/auth/register/assurance/page.tsx`
- `ui/one-ui-shell/src/app/auth/register/status/page.tsx`
- `ui/one-ui-shell/src/app/communities/page.tsx`
- `ui/one-ui-shell/src/app/discover/services/page.tsx`

## workflow-service

- **Path:** `services/workflow-service`
- **Domain:** interoperability (integration)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 11 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: workflow.openapi.yaml) |
| 3 | Wired via BFF? | Yes (33 clients) |
| 4 | Visible in UI? | Yes (213 refs) |
| 5 | Visible on mobile? | Yes (35 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/access/governance/page.tsx`
- `ui/one-ui-shell/src/app/admin/beds/page.tsx`
- `ui/one-ui-shell/src/app/admin/page.tsx`
- `ui/one-ui-shell/src/app/ai-governance/page.tsx`
- `ui/one-ui-shell/src/app/beds/page.tsx`

## workforce-governance-service

- **Path:** `services/workforce-governance-service`
- **Domain:** workforce-operations (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (9 controllers, 108 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: workforce-governance.openapi.yaml) |
| 3 | Wired via BFF? | Yes (5 clients) |
| 4 | Visible in UI? | Yes (21 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (14 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/workforce-intake/page.tsx`
- `ui/one-ui-shell/src/app/organization-admin/governance/[id]/page.tsx`
- `ui/one-ui-shell/src/app/organization-admin/governance/page.tsx`
- `ui/one-ui-shell/src/app/platform-origin/page.tsx`
- `ui/one-ui-shell/src/components/administration-governance/AccessRequestBoard.tsx`

## vashandi-workforce-service

- **Path:** `services/vashandi-workforce-service`
- **Domain:** workforce-operations (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (14 controllers, 76 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: vashandi-workforce.openapi.yaml) |
| 3 | Wired via BFF? | Yes (5 clients) |
| 4 | Visible in UI? | Yes (75 refs) |
| 5 | Visible on mobile? | Yes (8 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (12 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/workforce-intake/page.tsx`
- `ui/one-ui-shell/src/app/erp/hr/page.test.tsx`
- `ui/one-ui-shell/src/app/erp/hr/page.tsx`
- `ui/one-ui-shell/src/app/provider/get-access/page.tsx`
- `ui/one-ui-shell/src/app/provider/workplace/page.tsx`

## organization-registry-service

- **Path:** `services/organization-registry-service`
- **Domain:** organization-registry (registry)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (9 controllers, 57 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: organization-registry.openapi.yaml) |
| 3 | Wired via BFF? | Yes (6 clients) |
| 4 | Visible in UI? | Yes (2 refs) |
| 5 | Visible on mobile? | Yes (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (15 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/organization-admin/onboarding/page.tsx`
- `ui/one-ui-shell/src/hooks/queries/useOrgOnboarding.ts`

## khuluma-service

- **Path:** `services/khuluma-service`
- **Domain:** communication-coordination (experience)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (7 controllers, 66 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: khuluma.openapi.yaml) |
| 3 | Wired via BFF? | Yes (9 clients) |
| 4 | Visible in UI? | Yes (82 refs) |
| 5 | Visible on mobile? | Yes (14 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (8 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/comms-ops/page.test.tsx`
- `ui/one-ui-shell/src/app/admin/comms-ops/page.tsx`
- `ui/one-ui-shell/src/app/citizen/wallet/comms/page.tsx`
- `ui/one-ui-shell/src/app/citizen/wallet/page.tsx`
- `ui/one-ui-shell/src/app/communication/announcements/page.test.tsx`

## zibo-service

- **Path:** `services/zibo-service`
- **Domain:** terminology (registry)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (10 controllers, 50 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: zibo.openapi.yaml) |
| 3 | Wired via BFF? | Yes (3 clients) |
| 4 | Visible in UI? | Yes (39 refs) |
| 5 | Visible on mobile? | Yes (6 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (11 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/imaging/worklist/page.tsx`
- `ui/one-ui-shell/src/app/lab/results/page.tsx`
- `ui/one-ui-shell/src/app/lab/worklist/page.tsx`
- `ui/one-ui-shell/src/app/madi/processing/page.tsx`
- `ui/one-ui-shell/src/app/registry/page.tsx`

