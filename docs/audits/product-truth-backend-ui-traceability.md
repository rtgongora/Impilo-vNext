# Product Truth — Backend-to-UI Traceability

> Generated: 2026-06-20T11:02:04.418Z

For each service: backend capabilities → API → BFF → UI → mobile → persistence.

## ai-model-registry-service

- **Path:** `services/ai-model-registry-service`
- **Domain:** intelligence (data)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (5 controllers, 19 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: ai-model-registry.openapi.yaml) |
| 3 | Wired via BFF? | Yes (2 clients) |
| 4 | Visible in UI? | No (0 refs) |
| 5 | Visible on mobile? | No (0 refs) |
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
| 3 | Wired via BFF? | Yes (3 clients) |
| 4 | Visible in UI? | Yes (2 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/telemedicine/analytics/page.tsx`
- `ui/one-ui-shell/src/hooks/queries/useTelemedicineAnalytics.ts`

## asset-registry-service

- **Path:** `services/asset-registry-service`
- **Domain:** platform-ops (integration)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (4 controllers, 16 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: asset-registry.openapi.yaml) |
| 3 | Wired via BFF? | Yes (2 clients) |
| 4 | Visible in UI? | Yes (3 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (5 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/operations/assets/page.tsx`
- `ui/one-ui-shell/src/hooks/queries/useAssets.ts`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

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
| 5 | Visible on mobile? | No (0 refs) |
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
| 3 | Wired via BFF? | Yes (11 clients) |
| 4 | Visible in UI? | Yes (25 refs) |
| 5 | Visible on mobile? | Yes (8 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/home/appointments/page.test.tsx`
- `ui/one-ui-shell/src/app/home/appointments/page.tsx`
- `ui/one-ui-shell/src/app/home/bookings/[bookingId]/page.tsx`
- `ui/one-ui-shell/src/app/home/bookings/new/page.test.tsx`
- `ui/one-ui-shell/src/app/home/bookings/new/page.tsx`

## butano-fhir

- **Path:** `services/butano-fhir`
- **Domain:** care-delivery (clinical)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 7 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: butano.custom.openapi.yaml) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

**Gaps:**
- [D] butano-fhir: partial frontend/BFF wiring (medium)

## butano-service

- **Path:** `services/butano-service`
- **Domain:** care-delivery (clinical)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (5 controllers, 14 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: butano.custom.openapi.yaml) |
| 3 | Wired via BFF? | Yes (7 clients) |
| 4 | Visible in UI? | Yes (13 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/developer/api-catalog/page.tsx`
- `ui/one-ui-shell/src/app/developer/sandbox/page.tsx`
- `ui/one-ui-shell/src/app/operations/butano/page.tsx`
- `ui/one-ui-shell/src/app/operations/page.tsx`
- `ui/one-ui-shell/src/components/intelligent/SmartEncounterFlow.tsx`

## campaigns-service

- **Path:** `services/campaigns-service`
- **Domain:** public-health-campaigns (data)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 8 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: campaigns.openapi.yaml) |
| 3 | Wired via BFF? | Yes (7 clients) |
| 4 | Visible in UI? | Yes (15 refs) |
| 5 | Visible on mobile? | Yes (4 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/omnichannel/page.tsx`
- `ui/one-ui-shell/src/app/public-health/campaigns/page.tsx`
- `ui/one-ui-shell/src/app/public-health/page.tsx`
- `ui/one-ui-shell/src/components/platform/NotificationCommsOrchestrationRail.test.tsx`

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
| 5 | Visible on mobile? | No (0 refs) |
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
| 1 | Real backend capabilities? | Yes (5 controllers, 13 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: channels.openapi.yaml) |
| 3 | Wired via BFF? | Yes (11 clients) |
| 4 | Visible in UI? | Yes (11 refs) |
| 5 | Visible on mobile? | Yes (5 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/access/page.tsx`
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/communication/page.tsx`
- `ui/one-ui-shell/src/app/communication/secure-messaging/page.tsx`
- `ui/one-ui-shell/src/app/omnichannel/page.tsx`

## clinical-knowledge-platform-service

- **Path:** `services/clinical-knowledge-platform-service`
- **Domain:** clinical-knowledge (clinical)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 18 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: clinical-knowledge-platform.openapi.yaml) |
| 3 | Wired via BFF? | Yes (6 clients) |
| 4 | Visible in UI? | Yes (3 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (5 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/clinical-tools/page.tsx`
- `ui/one-ui-shell/src/components/intelligent/SmartEncounterFlow.tsx`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## community-service

- **Path:** `services/community-service`
- **Domain:** workflow-orchestration (experience)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (6 controllers, 47 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: social.openapi.yaml) |
| 3 | Wired via BFF? | Yes (19 clients) |
| 4 | Visible in UI? | Yes (39 refs) |
| 5 | Visible on mobile? | Yes (18 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/clinical-tools/page.tsx`
- `ui/one-ui-shell/src/app/communities/[id]/page.tsx`
- `ui/one-ui-shell/src/app/communities/page.tsx`
- `ui/one-ui-shell/src/app/home/bookings/new/page.tsx`

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
| 5 | Visible on mobile? | No (0 refs) |
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
| 1 | Real backend capabilities? | Yes (20 controllers, 115 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: costa.openapi.yaml) |
| 3 | Wired via BFF? | Yes (16 clients) |
| 4 | Visible in UI? | Yes (34 refs) |
| 5 | Visible on mobile? | Yes (3 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (11 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/enterprise/charge-sheet/page.test.tsx`
- `ui/one-ui-shell/src/app/enterprise/charge-sheet/page.tsx`
- `ui/one-ui-shell/src/app/finance/commerce-integrations/page.tsx`
- `ui/one-ui-shell/src/app/finance/costa/encounter/[encounterId]/page.test.tsx`
- `ui/one-ui-shell/src/app/finance/costa/encounter/[encounterId]/page.tsx`

## coverage-service

- **Path:** `services/coverage-service`
- **Domain:** finance (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (13 controllers, 52 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: coverage.openapi.yaml) |
| 3 | Wired via BFF? | Yes (10 clients) |
| 4 | Visible in UI? | Yes (40 refs) |
| 5 | Visible on mobile? | Yes (12 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (9 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/coverage/enroll/page.tsx`
- `ui/one-ui-shell/src/app/coverage/member/page.tsx`
- `ui/one-ui-shell/src/app/coverage/page.test.tsx`
- `ui/one-ui-shell/src/app/coverage/page.tsx`

## credential-verification-service

- **Path:** `services/credential-verification-service`
- **Domain:** finance (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (5 controllers, 15 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: credential-verification.openapi.yaml) |
| 3 | Wired via BFF? | Yes (2 clients) |
| 4 | Visible in UI? | Yes (8 refs) |
| 5 | Visible on mobile? | Yes (1 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/verify/credential/layout.tsx`
- `ui/one-ui-shell/src/app/verify/credential/page.tsx`
- `ui/one-ui-shell/src/lib/__tests__/credential-verification-golden-thread.test.ts`
- `ui/one-ui-shell/src/lib/credentialVerifyPublic.test.ts`
- `ui/one-ui-shell/src/lib/credentialVerifyPublic.ts`

## data-access-governance-service

- **Path:** `services/data-access-governance-service`
- **Domain:** intelligence (data)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 9 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: data-access-governance.openapi.yaml) |
| 3 | Wired via BFF? | Yes (3 clients) |
| 4 | Visible in UI? | Yes (20 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/access/governance/page.tsx`
- `ui/one-ui-shell/src/app/admin/data-governance/page.tsx`
- `ui/one-ui-shell/src/app/dags/page.tsx`
- `ui/one-ui-shell/src/app/intelligence/page.tsx`
- `ui/one-ui-shell/src/components/administration-governance/GovernanceActionResult.tsx`

## data-governance-service

- **Path:** `services/data-governance-service`
- **Domain:** intelligence (data)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (4 controllers, 15 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: data-governance.openapi.yaml) |
| 3 | Wired via BFF? | Yes (4 clients) |
| 4 | Visible in UI? | Yes (5 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (4 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/data-governance/page.tsx`
- `ui/one-ui-shell/src/app/admin/page.tsx`
- `ui/one-ui-shell/src/hooks/queries/useDataGovernance.ts`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`
- `ui/one-ui-shell/src/lib/routes.ts`

## data-ingestion-service

- **Path:** `services/data-ingestion-service`
- **Domain:** intelligence (data)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 6 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: data-ingestion.openapi.yaml) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | No (0 refs) |
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
| 3 | Wired via BFF? | Yes (2 clients) |
| 4 | Visible in UI? | Yes (3 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/data-intelligence/pipelines/page.tsx`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`
- `ui/one-ui-shell/src/lib/routes.ts`

## data-warehouse-service

- **Path:** `services/data-warehouse-service`
- **Domain:** intelligence (data)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 5 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: data-warehouse.openapi.yaml) |
| 3 | Wired via BFF? | Yes (3 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | No (0 refs) |
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
| 1 | Real backend capabilities? | Yes (1 controllers, 15 routes) |
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
- `ui/one-ui-shell/src/components/navigation/ExperienceSidebar.tsx`
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
| 3 | Wired via BFF? | Yes (13 clients) |
| 4 | Visible in UI? | Yes (45 refs) |
| 5 | Visible on mobile? | Yes (7 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (4 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/enterprise/fleet/page.tsx`
- `ui/one-ui-shell/src/app/enterprise/warehousing/page.tsx`
- `ui/one-ui-shell/src/app/inventory/requisitions/page.tsx`
- `ui/one-ui-shell/src/app/madi/logistics/page.tsx`
- `ui/one-ui-shell/src/app/madi/page.tsx`

## document-service

- **Path:** `services/document-service`
- **Domain:** care-delivery (clinical)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 16 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: document-store.openapi.yaml) |
| 3 | Wired via BFF? | Yes (21 clients) |
| 4 | Visible in UI? | Yes (2 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/ehr/[patientId]/procedures/[episodeId]/page.tsx`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## experience-bff

- **Path:** `services/experience-bff`
- **Domain:** workflow-orchestration (experience)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (254 controllers, 2144 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: experience-bff.openapi.yaml) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (155 refs) |
| 5 | Visible on mobile? | Yes (52 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (45 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/clinical-curation/page.test.tsx`
- `ui/one-ui-shell/src/app/admin/clinical-curation/page.tsx`
- `ui/one-ui-shell/src/app/admin/data-export/page.tsx`
- `ui/one-ui-shell/src/app/admin/federation/page.tsx`
- `ui/one-ui-shell/src/app/admin/integration-status/page.tsx`

## fhir-gateway-service

- **Path:** `services/fhir-gateway-service`
- **Domain:** care-delivery (clinical)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 8 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: fhir-gateway.openapi.yaml) |
| 3 | Wired via BFF? | Yes (3 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | No (0 refs) |
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
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 8 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: forms.openapi.yaml) |
| 3 | Wired via BFF? | Yes (5 clients) |
| 4 | Visible in UI? | Yes (9 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/clinical-tools/forms/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/encounter/[encounterId]/page.tsx`
- `ui/one-ui-shell/src/app/inventory/stock-management/page.tsx`
- `ui/one-ui-shell/src/components/ehr/EncounterDocumentsSheet.tsx`
- `ui/one-ui-shell/src/components/encounter/StructuredEncounterForms.tsx`

## general-ledger-service

- **Path:** `services/general-ledger-service`
- **Domain:** enterprise-resource (enterprise)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (7 controllers, 28 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: general-ledger.openapi.yaml) |
| 3 | Wired via BFF? | Yes (2 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/hooks/queries/useGeneralLedger.ts`

**Gaps:**
- [D] general-ledger-service: partial frontend/BFF wiring (medium)
- [O] general-ledger-service: no automated tests detected (medium)

## guidance-service

- **Path:** `services/guidance-service`
- **Domain:** clinical-knowledge (clinical)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 5 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: guidance.openapi.yaml) |
| 3 | Wired via BFF? | Yes (5 clients) |
| 4 | Visible in UI? | Yes (36 refs) |
| 5 | Visible on mobile? | Yes (1 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/clinical-curation/page.tsx`
- `ui/one-ui-shell/src/app/ask/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/encounter/[encounterId]/page.test.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/encounter/[encounterId]/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/encounters/page.tsx`

**Gaps:**
- [O] guidance-service: no automated tests detected (medium)

## hr-payroll-service

- **Path:** `services/hr-payroll-service`
- **Domain:** enterprise-resource (enterprise)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 21 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: hr-payroll.openapi.yaml) |
| 3 | Wired via BFF? | Yes (2 clients) |
| 4 | Visible in UI? | Yes (2 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/erp/hr/page.tsx`
- `ui/one-ui-shell/src/hooks/queries/useHrPayroll.ts`

**Gaps:**
- [O] hr-payroll-service: no automated tests detected (medium)

## identity-assurance-service

- **Path:** `services/identity-assurance-service`
- **Domain:** identity-governance (trust)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 5 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: identity-assurance.openapi.yaml) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (5 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/auth/register/assurance/page.tsx`
- `ui/one-ui-shell/src/components/citizen/IdentityAssuranceBanner.tsx`
- `ui/one-ui-shell/src/hooks/queries/useIdentity.ts`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`
- `ui/one-ui-shell/src/lib/routes.ts`

## indawo-service

- **Path:** `services/indawo-service`
- **Domain:** registry-spine (registry)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (4 controllers, 33 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: indawo.openapi.yaml) |
| 3 | Wired via BFF? | Yes (4 clients) |
| 4 | Visible in UI? | Yes (8 refs) |
| 5 | Visible on mobile? | Yes (1 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (6 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/public-health/publicHealthTabParams.test.ts`
- `ui/one-ui-shell/src/components/public-health/FieldOperationsTab.tsx`
- `ui/one-ui-shell/src/components/public-health/InspectionsTab.tsx`
- `ui/one-ui-shell/src/config/serviceBranding.ts`
- `ui/one-ui-shell/src/data/workSurfaceModules.ts`

## inpatient-service

- **Path:** `services/inpatient-service`
- **Domain:** care-delivery (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (6 controllers, 88 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: inpatient.openapi.yaml) |
| 3 | Wired via BFF? | Yes (11 clients) |
| 4 | Visible in UI? | Yes (33 refs) |
| 5 | Visible on mobile? | Yes (6 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (12 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/clinical/inpatient/admissions/[admissionId]/page.tsx`
- `ui/one-ui-shell/src/app/clinical/inpatient/admissions/page.tsx`
- `ui/one-ui-shell/src/app/clinical/inpatient/discharge/[admissionId]/page.tsx`
- `ui/one-ui-shell/src/app/clinical/inpatient/nursing/page.tsx`
- `ui/one-ui-shell/src/app/clinical/inpatient/page.tsx`

## integration-hub

- **Path:** `services/integration-hub`
- **Domain:** interoperability (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (4 controllers, 31 routes) |
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
| 5 | Visible on mobile? | No (0 refs) |
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
| 1 | Real backend capabilities? | Yes (10 controllers, 51 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: inventory.openapi.yaml) |
| 3 | Wired via BFF? | Yes (6 clients) |
| 4 | Visible in UI? | Yes (34 refs) |
| 5 | Visible on mobile? | Yes (8 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/data-export/page.tsx`
- `ui/one-ui-shell/src/app/enterprise/charge-sheet/page.test.tsx`
- `ui/one-ui-shell/src/app/enterprise/charge-sheet/page.tsx`
- `ui/one-ui-shell/src/app/enterprise/warehousing/page.test.tsx`
- `ui/one-ui-shell/src/app/enterprise/warehousing/page.tsx`

## iot-ingestion-service

- **Path:** `services/iot-ingestion-service`
- **Domain:** platform-ops (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 9 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: iot-ingestion.openapi.yaml) |
| 3 | Wired via BFF? | Yes (2 clients) |
| 4 | Visible in UI? | Yes (2 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
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
| 4 | Visible in UI? | Yes (6 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/data-export/page.tsx`
- `ui/one-ui-shell/src/app/registry/intake/page.tsx`
- `ui/one-ui-shell/src/app/reports/[id]/page.tsx`
- `ui/one-ui-shell/src/hooks/queries/useAdminReportJobs.ts`
- `ui/one-ui-shell/src/hooks/queries/useRegistryIntake.ts`

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
| 5 | Visible on mobile? | No (0 refs) |
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
| 1 | Real backend capabilities? | Yes (19 controllers, 102 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: learning.openapi.yaml) |
| 3 | Wired via BFF? | Yes (5 clients) |
| 4 | Visible in UI? | Yes (71 refs) |
| 5 | Visible on mobile? | Yes (18 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (14 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/data-intelligence/reports/page.tsx`
- `ui/one-ui-shell/src/app/home/credentials/page.tsx`
- `ui/one-ui-shell/src/app/home/page.tsx`
- `ui/one-ui-shell/src/app/intelligence/page.tsx`
- `ui/one-ui-shell/src/app/learning/admin/moderation/page.tsx`

## live-service

- **Path:** `services/live-service`
- **Domain:** live-events-broadcast (experience)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (11 controllers, 68 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: impilo-live.openapi.yaml) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (13 refs) |
| 5 | Visible on mobile? | Yes (7 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/live/admin/page.tsx`
- `ui/one-ui-shell/src/app/live/cpd/page.tsx`
- `ui/one-ui-shell/src/app/live/create/page.tsx`
- `ui/one-ui-shell/src/app/live/event/[eventId]/page.tsx`
- `ui/one-ui-shell/src/app/live/page.test.tsx`

## llm-orchestration-service

- **Path:** `services/llm-orchestration-service`
- **Domain:** platform-ops (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 6 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: none) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | No (0 refs) |
| 5 | Visible on mobile? | No (0 refs) |
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
| 1 | Real backend capabilities? | Yes (11 controllers, 75 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: madi.openapi.yaml) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (47 refs) |
| 5 | Visible on mobile? | Yes (10 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (5 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/ehr/[patientId]/orders/page.tsx`
- `ui/one-ui-shell/src/app/madi/blood-bank/fridges/page.tsx`
- `ui/one-ui-shell/src/app/madi/blood-bank/page.tsx`
- `ui/one-ui-shell/src/app/madi/blood-bank/stock/page.tsx`
- `ui/one-ui-shell/src/app/madi/central-bank/page.tsx`

## msika-apps-service

- **Path:** `services/msika-apps-service`
- **Domain:** marketplace (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (0 controllers, 33 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: msika-apps.openapi.yaml) |
| 3 | Wired via BFF? | Yes (3 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | Yes (4 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/marketplace/apps/page.tsx`

**Gaps:**
- [D] msika-apps-service: partial frontend/BFF wiring (medium)

## msika-flow-service

- **Path:** `services/msika-flow-service`
- **Domain:** marketplace (enterprise)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (11 controllers, 69 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: msika-flow.openapi.yaml) |
| 3 | Wired via BFF? | Yes (7 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

**Gaps:**
- [D] msika-flow-service: partial frontend/BFF wiring (medium)

## msika-service

- **Path:** `services/msika-service`
- **Domain:** marketplace (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (11 controllers, 51 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: msika-core.openapi.yaml) |
| 3 | Wired via BFF? | Yes (16 clients) |
| 4 | Visible in UI? | Yes (30 refs) |
| 5 | Visible on mobile? | Yes (1 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (5 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/clinical-tools/page.tsx`
- `ui/one-ui-shell/src/app/finance/commerce-integrations/page.test.tsx`
- `ui/one-ui-shell/src/app/finance/commerce-integrations/page.tsx`
- `ui/one-ui-shell/src/app/finance/costa/page.tsx`
- `ui/one-ui-shell/src/app/finance/msika-governance/page.test.tsx`

## mushe-wallet-service

- **Path:** `services/mushe-wallet-service`
- **Domain:** finance (enterprise)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (6 controllers, 40 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: mushe-wallet.openapi.yaml) |
| 3 | Wired via BFF? | Yes (3 clients) |
| 4 | Visible in UI? | Yes (7 refs) |
| 5 | Visible on mobile? | Yes (7 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/finance/billing/[id]/page.tsx`
- `ui/one-ui-shell/src/app/finance/mushex-platform/page.tsx`
- `ui/one-ui-shell/src/app/marketplace/cart/page.tsx`
- `ui/one-ui-shell/src/app/wallet/page.tsx`
- `ui/one-ui-shell/src/components/navigation/ExperienceSidebar.tsx`

**Gaps:**
- [O] mushe-wallet-service: no automated tests detected (medium)

## mushex-service

- **Path:** `services/mushex-service`
- **Domain:** finance (enterprise)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (16 controllers, 73 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: mushex.openapi.yaml) |
| 3 | Wired via BFF? | Yes (11 clients) |
| 4 | Visible in UI? | Yes (5 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (8 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/enterprise/charge-sheet/page.tsx`
- `ui/one-ui-shell/src/components/finance/MusheXRailSafetyPanel.tsx`
- `ui/one-ui-shell/src/config/serviceBranding.ts`
- `ui/one-ui-shell/src/hooks/queries/useMushexPlatformAdmin.ts`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## mvumo-service

- **Path:** `services/mvumo-service`
- **Domain:** identity-governance (trust)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 38 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: mvumo.openapi.yaml) |
| 3 | Wired via BFF? | Yes (9 clients) |
| 4 | Visible in UI? | Yes (28 refs) |
| 5 | Visible on mobile? | Yes (4 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (4 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/ehr/[patientId]/preferences/communications/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/summary/page.tsx`
- `ui/one-ui-shell/src/app/home/bookings/[bookingId]/page.tsx`
- `ui/one-ui-shell/src/app/home/bookings/new/page.test.tsx`
- `ui/one-ui-shell/src/app/home/bookings/new/page.tsx`

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
| 5 | Visible on mobile? | No (0 refs) |
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
| 1 | Real backend capabilities? | Yes (14 controllers, 81 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: ndila.openapi.yaml) |
| 3 | Wired via BFF? | Yes (5 clients) |
| 4 | Visible in UI? | Yes (62 refs) |
| 5 | Visible on mobile? | Yes (5 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/discover/facilities/page.tsx`
- `ui/one-ui-shell/src/app/enterprise/oversight/page.tsx`
- `ui/one-ui-shell/src/app/madi/donor/drives/page.tsx`
- `ui/one-ui-shell/src/app/ndila/page.tsx`
- `ui/one-ui-shell/src/app/nhume/dashboard/page.tsx`

## ndr-service

- **Path:** `services/ndr-service`
- **Domain:** intelligence (data)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 5 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: ndr.openapi.yaml) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (2 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/components/data-intelligence/NdrWarehouseQueryPanel.tsx`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## nhume-service

- **Path:** `services/nhume-service`
- **Domain:** interoperability (integration)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (7 controllers, 93 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: none) |
| 3 | Wired via BFF? | Yes (4 clients) |
| 4 | Visible in UI? | Yes (40 refs) |
| 5 | Visible on mobile? | Yes (15 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (4 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/madi/logistics/page.tsx`
- `ui/one-ui-shell/src/app/madi/page.tsx`
- `ui/one-ui-shell/src/app/ndila/page.tsx`
- `ui/one-ui-shell/src/app/nhume/analytics/page.tsx`
- `ui/one-ui-shell/src/app/nhume/autonomous/page.tsx`

**Gaps:**
- [C] nhume-service: no matched OpenAPI contract (medium)

## notification-service

- **Path:** `services/notification-service`
- **Domain:** interoperability (integration)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (4 controllers, 26 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: notification.openapi.yaml) |
| 3 | Wired via BFF? | Yes (17 clients) |
| 4 | Visible in UI? | Yes (44 refs) |
| 5 | Visible on mobile? | Yes (15 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (10 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/access/page.tsx`
- `ui/one-ui-shell/src/app/admin/notifications/templates/page.tsx`
- `ui/one-ui-shell/src/app/admin/page.tsx`
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/caregiving/notifications/page.tsx`

## observability-service

- **Path:** `services/observability-service`
- **Domain:** platform-ops (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (4 controllers, 11 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: observability.openapi.yaml) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (2 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/system-monitor/page.tsx`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## offline-edge-service

- **Path:** `services/offline-edge-service`
- **Domain:** platform-ops (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (6 controllers, 16 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: offline-edge.openapi.yaml) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | Yes (3 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (8 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
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
| 1 | Real backend capabilities? | Yes (10 controllers, 45 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: oros.openapi.yaml) |
| 3 | Wired via BFF? | Yes (11 clients) |
| 4 | Visible in UI? | Yes (52 refs) |
| 5 | Visible on mobile? | Yes (10 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/system-monitor/page.tsx`
- `ui/one-ui-shell/src/app/clinical/inpatient/rounds/page.tsx`
- `ui/one-ui-shell/src/app/developer/api-catalog/page.tsx`
- `ui/one-ui-shell/src/app/developer/sandbox/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/encounter/[encounterId]/page.test.tsx`

## pacs-adapter-service

- **Path:** `services/pacs-adapter-service`
- **Domain:** care-delivery (clinical)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 27 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: pacs-adapter.openapi.yaml) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (2 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (5 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/system-monitor/page.tsx`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## pct-service

- **Path:** `services/pct-service`
- **Domain:** care-delivery (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (17 controllers, 115 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: pct.openapi.yaml) |
| 3 | Wired via BFF? | Yes (39 clients) |
| 4 | Visible in UI? | Yes (412 refs) |
| 5 | Visible on mobile? | Yes (95 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (14 migrations) |
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
| 5 | Visible on mobile? | No (0 refs) |
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
| 1 | Real backend capabilities? | Yes (10 controllers, 38 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: pharmacy.openapi.yaml) |
| 3 | Wired via BFF? | Yes (11 clients) |
| 4 | Visible in UI? | Yes (52 refs) |
| 5 | Visible on mobile? | Yes (7 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (4 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/clinical/inpatient/admissions/[admissionId]/page.tsx`
- `ui/one-ui-shell/src/app/clinical/inpatient/discharge/[admissionId]/page.tsx`
- `ui/one-ui-shell/src/app/clinical/inpatient/page.tsx`
- `ui/one-ui-shell/src/app/clinical/inpatient/rounds/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/discharge/page.tsx`

## procurement-service

- **Path:** `services/procurement-service`
- **Domain:** enterprise-resource (enterprise)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 21 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: procurement.openapi.yaml) |
| 3 | Wired via BFF? | Yes (2 clients) |
| 4 | Visible in UI? | Yes (12 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/enterprise/warehousing/page.tsx`
- `ui/one-ui-shell/src/app/erp/layout.tsx`
- `ui/one-ui-shell/src/app/erp/page.tsx`
- `ui/one-ui-shell/src/app/erp/procurement/page.tsx`
- `ui/one-ui-shell/src/app/inventory/stock-management/page.tsx`

**Gaps:**
- [O] procurement-service: no automated tests detected (medium)

## product-registry-service

- **Path:** `services/product-registry-service`
- **Domain:** registry-spine (registry)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 11 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: product-registry.openapi.yaml) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (3 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/registry/products/page.tsx`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`
- `ui/one-ui-shell/src/lib/routes.ts`

## referral-service

- **Path:** `services/referral-service`
- **Domain:** platform-ops (integration)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 8 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: referral.openapi.yaml) |
| 3 | Wired via BFF? | Yes (12 clients) |
| 4 | Visible in UI? | Yes (50 refs) |
| 5 | Visible on mobile? | Yes (13 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/system-monitor/page.tsx`
- `ui/one-ui-shell/src/app/citizen/record-sharing/page.tsx`
- `ui/one-ui-shell/src/app/clinical-tools/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/__tests__/coordination-journey.test.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/consults/page.tsx`

## reporting-service

- **Path:** `services/reporting-service`
- **Domain:** intelligence (data)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (2 controllers, 7 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: reporting.openapi.yaml) |
| 3 | Wired via BFF? | Yes (5 clients) |
| 4 | Visible in UI? | Yes (15 refs) |
| 5 | Visible on mobile? | Yes (1 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/coverage/page.tsx`
- `ui/one-ui-shell/src/app/data-intelligence/page.tsx`
- `ui/one-ui-shell/src/app/data-intelligence/reports/page.tsx`
- `ui/one-ui-shell/src/app/reports/page.tsx`

## rtc-gateway-service

- **Path:** `services/rtc-gateway-service`
- **Domain:** platform-ops (integration)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 6 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: rtc-gateway.openapi.yaml) |
| 3 | Wired via BFF? | Yes (3 clients) |
| 4 | Visible in UI? | Yes (74 refs) |
| 5 | Visible on mobile? | Yes (13 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/system-monitor/page.tsx`
- `ui/one-ui-shell/src/app/auth/context-chooser/page.tsx`
- `ui/one-ui-shell/src/app/clinical/control-tower/page.tsx`
- `ui/one-ui-shell/src/app/communication/page.test.tsx`
- `ui/one-ui-shell/src/app/communication/page.tsx`

## rules-service

- **Path:** `services/rules-service`
- **Domain:** clinical-knowledge (clinical)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 7 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: rules.openapi.yaml) |
| 3 | Wired via BFF? | Yes (10 clients) |
| 4 | Visible in UI? | Yes (24 refs) |
| 5 | Visible on mobile? | Yes (2 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/data-governance/page.tsx`
- `ui/one-ui-shell/src/app/admin/policies/page.tsx`
- `ui/one-ui-shell/src/app/clinical-tools/forms/page.tsx`
- `ui/one-ui-shell/src/app/clinical-tools/page.tsx`
- `ui/one-ui-shell/src/app/clinical-tools/rules/page.tsx`

## scheduling-service

- **Path:** `services/scheduling-service`
- **Domain:** care-delivery (clinical)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 6 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: scheduling.openapi.yaml) |
| 3 | Wired via BFF? | Yes (8 clients) |
| 4 | Visible in UI? | Yes (18 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/queue/scheduled/page.tsx`
- `ui/one-ui-shell/src/app/scheduling/booking-requests/page.tsx`
- `ui/one-ui-shell/src/app/scheduling/page.test.tsx`
- `ui/one-ui-shell/src/app/scheduling/page.tsx`
- `ui/one-ui-shell/src/app/scheduling/today/page.tsx`

## schema-registry-service

- **Path:** `services/schema-registry-service`
- **Domain:** platform-ops (integration)
- **Product status:** internal-only

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 6 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: schema-registry.openapi.yaml) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | No (0 refs) |
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
| 3 | Wired via BFF? | Yes (34 clients) |
| 4 | Visible in UI? | Yes (355 refs) |
| 5 | Visible on mobile? | Yes (36 refs) |
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
| 1 | Real backend capabilities? | Yes (3 controllers, 7 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: security-hardening.openapi.yaml) |
| 3 | Wired via BFF? | No (0 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | No (0 refs) |
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
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 13 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: share-slip.openapi.yaml) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (5 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`
- `ui/one-ui-shell/src/lib/shareSlipPublic.test.ts`
- `ui/one-ui-shell/src/lib/shareSlipPublic.ts`
- `ui/one-ui-shell/src/lib/sidecar-retirement-ledger-v2.ts`
- `ui/one-ui-shell/src/lib/sidecar-retirement-ledger.ts`

## simba-service

- **Path:** `services/simba-service`
- **Domain:** wellness-personal-health-data (enterprise)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (18 controllers, 93 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: simba.openapi.yaml) |
| 3 | Wired via BFF? | Yes (4 clients) |
| 4 | Visible in UI? | Yes (64 refs) |
| 5 | Visible on mobile? | Yes (23 refs) |
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

## support-service

- **Path:** `services/support-service`
- **Domain:** platform-ops (integration)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (8 controllers, 20 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: support.openapi.yaml) |
| 3 | Wired via BFF? | Yes (26 clients) |
| 4 | Visible in UI? | Yes (56 refs) |
| 5 | Visible on mobile? | Yes (18 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (4 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/auth/login/page.tsx`
- `ui/one-ui-shell/src/app/clinical-tools/page.tsx`
- `ui/one-ui-shell/src/app/coverage/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/encounter/[encounterId]/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/growth-chart/page.tsx`

## surveillance-service

- **Path:** `services/surveillance-service`
- **Domain:** public-health-surveillance (data)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (5 controllers, 56 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: surveillance.openapi.yaml) |
| 3 | Wired via BFF? | Yes (6 clients) |
| 4 | Visible in UI? | Yes (24 refs) |
| 5 | Visible on mobile? | Yes (2 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (11 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/enterprise/oversight/page.tsx`
- `ui/one-ui-shell/src/app/public-health/page.tsx`
- `ui/one-ui-shell/src/app/public-health/publicHealthTabParams.test.ts`
- `ui/one-ui-shell/src/app/public-health/surveillance/page.tsx`

## tshepo-audit-service

- **Path:** `services/tshepo-audit-service`
- **Domain:** identity-governance (trust)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (6 controllers, 16 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: tshepo-audit.openapi.yaml) |
| 3 | Wired via BFF? | Yes (5 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

**Gaps:**
- [D] tshepo-audit-service: partial frontend/BFF wiring (medium)

## tshepo-authz-service

- **Path:** `services/tshepo-authz-service`
- **Domain:** identity-governance (trust)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (8 controllers, 30 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: tshepo-authz.openapi.yaml) |
| 3 | Wired via BFF? | Yes (6 clients) |
| 4 | Visible in UI? | Yes (2 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (12 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/components/trust/BreakGlassRequestPanel.tsx`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## tshepo-consent-service

- **Path:** `services/tshepo-consent-service`
- **Domain:** identity-governance (trust)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (5 controllers, 18 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: tshepo-consent.openapi.yaml) |
| 3 | Wired via BFF? | Yes (8 clients) |
| 4 | Visible in UI? | Yes (3 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/registry/mvumo/page.tsx`
- `ui/one-ui-shell/src/hooks/queries/useConsent.ts`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## tshepo-identity-service

- **Path:** `services/tshepo-identity-service`
- **Domain:** identity-governance (trust)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (6 controllers, 20 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: tshepo-identity.openapi.yaml) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

**Gaps:**
- [D] tshepo-identity-service: partial frontend/BFF wiring (medium)

## tshepo-keys-service

- **Path:** `services/tshepo-keys-service`
- **Domain:** identity-governance (trust)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (5 controllers, 17 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: tshepo-keys.openapi.yaml) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (2 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/keys/page.tsx`
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

## tshepo-offline-service

- **Path:** `services/tshepo-offline-service`
- **Domain:** identity-governance (trust)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (5 controllers, 19 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: tshepo-offline.openapi.yaml) |
| 3 | Wired via BFF? | Yes (3 clients) |
| 4 | Visible in UI? | Yes (1 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | Yes — review |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/lib/registry-service-module-refs.ts`

**Gaps:**
- [D] tshepo-offline-service: partial frontend/BFF wiring (medium)

## tshepo-service

- **Path:** `services/tshepo-service`
- **Domain:** identity-governance (trust)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (8 controllers, 26 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: tshepo.openapi.yaml) |
| 3 | Wired via BFF? | Yes (24 clients) |
| 4 | Visible in UI? | Yes (18 refs) |
| 5 | Visible on mobile? | Yes (1 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (11 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/keys/page.tsx`
- `ui/one-ui-shell/src/app/admin/page.test.tsx`
- `ui/one-ui-shell/src/app/admin/page.tsx`
- `ui/one-ui-shell/src/app/developer/api-catalog/page.tsx`
- `ui/one-ui-shell/src/app/developer/sandbox/page.tsx`

## tuso-service

- **Path:** `services/tuso-service`
- **Domain:** registry-spine (registry)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (15 controllers, 80 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: tuso.openapi.yaml) |
| 3 | Wired via BFF? | Yes (15 clients) |
| 4 | Visible in UI? | Yes (15 refs) |
| 5 | Visible on mobile? | Yes (1 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (11 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/developer/api-catalog/page.tsx`
- `ui/one-ui-shell/src/app/developer/sandbox/page.tsx`
- `ui/one-ui-shell/src/app/scheduling/page.tsx`
- `ui/one-ui-shell/src/app/tuso/page.tsx`
- `ui/one-ui-shell/src/components/facility/FacilityDigitalReadinessOrchestrationPanel.tsx`

## ubomi-service

- **Path:** `services/ubomi-service`
- **Domain:** registry-spine (registry)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (4 controllers, 13 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: ubomi.openapi.yaml) |
| 3 | Wired via BFF? | Yes (2 clients) |
| 4 | Visible in UI? | Yes (8 refs) |
| 5 | Visible on mobile? | Yes (2 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/ubomi/page.tsx`
- `ui/one-ui-shell/src/components/registry/CrvsUbomiOrchestrationRail.test.tsx`
- `ui/one-ui-shell/src/components/registry/CrvsUbomiOrchestrationRail.tsx`
- `ui/one-ui-shell/src/config/serviceBranding.ts`
- `ui/one-ui-shell/src/hooks/queries/useUbomiRegistry.ts`

## varapi-service

- **Path:** `services/varapi-service`
- **Domain:** registry-spine (registry)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (27 controllers, 195 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: varapi.openapi.yaml) |
| 3 | Wired via BFF? | Yes (21 clients) |
| 4 | Visible in UI? | Yes (22 refs) |
| 5 | Visible on mobile? | Yes (2 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (15 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/developer/api-catalog/page.tsx`
- `ui/one-ui-shell/src/app/developer/sandbox/page.tsx`
- `ui/one-ui-shell/src/app/home/credentials/page.tsx`
- `ui/one-ui-shell/src/app/home/page.tsx`
- `ui/one-ui-shell/src/app/id-services/page.tsx`

## vito-service

- **Path:** `services/vito-service`
- **Domain:** registry-spine (registry)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (24 controllers, 137 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: vito.openapi.yaml) |
| 3 | Wired via BFF? | Yes (27 clients) |
| 4 | Visible in UI? | Yes (46 refs) |
| 5 | Visible on mobile? | Yes (2 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (29 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/api/mobile/provider/hubs/[hub]/route.ts`
- `ui/one-ui-shell/src/app/citizen/delegated-pickup/page.tsx`
- `ui/one-ui-shell/src/app/citizen/id-recovery/page.tsx`
- `ui/one-ui-shell/src/app/citizen/page.tsx`
- `ui/one-ui-shell/src/app/collaboration/access/page.tsx`

## wellness-service

- **Path:** `services/wellness-service`
- **Domain:** wellness-compatibility-alias (enterprise)
- **Product status:** deprecated

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (4 controllers, 50 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: wellness.openapi.yaml) |
| 3 | Wired via BFF? | Yes (8 clients) |
| 4 | Visible in UI? | Yes (50 refs) |
| 5 | Visible on mobile? | Yes (23 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (4 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/communities/page.tsx`
- `ui/one-ui-shell/src/app/home/bookings/new/page.tsx`
- `ui/one-ui-shell/src/app/home/page.tsx`
- `ui/one-ui-shell/src/app/madi/donor/screening/page.tsx`
- `ui/one-ui-shell/src/app/monitoring/devices/page.tsx`

## workflow-service

- **Path:** `services/workflow-service`
- **Domain:** interoperability (integration)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (3 controllers, 11 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: workflow.openapi.yaml) |
| 3 | Wired via BFF? | Yes (27 clients) |
| 4 | Visible in UI? | Yes (66 refs) |
| 5 | Visible on mobile? | Yes (7 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/admin/beds/page.tsx`
- `ui/one-ui-shell/src/app/ai-governance/page.tsx`
- `ui/one-ui-shell/src/app/beds/page.tsx`
- `ui/one-ui-shell/src/app/ehr/[patientId]/consults/page.tsx`
- `ui/one-ui-shell/src/app/finance/billing/[id]/page.tsx`

## workforce-governance-service

- **Path:** `services/workforce-governance-service`
- **Domain:** workforce-operations (enterprise)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (1 controllers, 60 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: workforce-governance.openapi.yaml) |
| 3 | Wired via BFF? | Yes (2 clients) |
| 4 | Visible in UI? | Yes (6 refs) |
| 5 | Visible on mobile? | No (0 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (3 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/organization-admin/governance/page.tsx`
- `ui/one-ui-shell/src/lib/administration-governance/__tests__/administration-governance.test.ts`
- `ui/one-ui-shell/src/lib/administration-governance/onboard-options.ts`
- `ui/one-ui-shell/src/lib/administration-governance/surfaces.ts`
- `ui/one-ui-shell/src/lib/administration-governance/tiles.ts`

## vashandi-workforce-service

- **Path:** `services/vashandi-workforce-service`
- **Domain:** workforce-operations (enterprise)
- **Product status:** mostly-real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (7 controllers, 42 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: none) |
| 3 | Wired via BFF? | Yes (1 clients) |
| 4 | Visible in UI? | Yes (35 refs) |
| 5 | Visible on mobile? | Yes (8 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (1 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/auth/login/page.tsx`
- `ui/one-ui-shell/src/app/work/vashandi/access-review/page.tsx`
- `ui/one-ui-shell/src/app/work/vashandi/analytics/page.tsx`
- `ui/one-ui-shell/src/app/work/vashandi/assignments/page.tsx`
- `ui/one-ui-shell/src/app/work/vashandi/attendance/page.tsx`

**Gaps:**
- [C] vashandi-workforce-service: no matched OpenAPI contract (medium)

## zibo-service

- **Path:** `services/zibo-service`
- **Domain:** terminology (registry)
- **Product status:** real

| # | Question | Answer |
|---|----------|--------|
| 1 | Real backend capabilities? | Yes (8 controllers, 43 routes) |
| 2 | Exposed via API/contracts? | Yes (contract: zibo.openapi.yaml) |
| 3 | Wired via BFF? | Yes (2 clients) |
| 4 | Visible in UI? | Yes (9 refs) |
| 5 | Visible on mobile? | Yes (1 refs) |
| 6 | Fake/partial/disconnected? | No |
| 7 | Backend without UI? | No |
| 8 | UI without backend? | No |
| 9 | Persists to DB? | Yes (2 migrations) |
| 10 | Fixture-only flows? | No |

**UI references (sample):**
- `ui/one-ui-shell/src/app/madi/processing/page.tsx`
- `ui/one-ui-shell/src/app/registry/page.tsx`
- `ui/one-ui-shell/src/app/registry/terminology/[id]/page.tsx`
- `ui/one-ui-shell/src/app/registry/terminology/page.tsx`
- `ui/one-ui-shell/src/config/serviceBranding.ts`

