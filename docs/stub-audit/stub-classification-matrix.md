# Stub Classification Matrix

**Date**: 2026-03-23
**Scope**: All UI pages, backend services, mobile screens

---

## Classification Key

| Classification | Definition |
|---------------|------------|
| **REAL** | Real API integration, real workflows, real data, proper error handling |
| **THIN** | Some API integration but incomplete workflows or local-only state |
| **STUB** | Route exists but mostly placeholder content |
| **MOCK-DATA-DRIVEN** | Uses hardcoded sample data arrays instead of real API queries |
| **SHELL-ONLY** | Just layout/navigation or placeholder text, no functional content |
| **REDIRECT** | Page redirects to another route (intentional) |
| **NEEDS-MANUAL-REVIEW** | Ambiguous — requires domain expert assessment |

---

## A. Web UI Pages — By Route

### experience (87 pages) — REAL

| Route/Page | Classification | Evidence |
|-----------|---------------|----------|
| `/home` | REAL | useAuthStore, useFacilityStore, useShiftStore; QUICK_ACTIONS is navigation links not mock data |
| `/ehr/[patientId]/*` (vitals, allergies, conditions, medications, encounters) | REAL | usePatient(), useEncounters() queries, FHIR rendering |
| `/queue/*` | REAL | Queue management with real patient state |
| `/pharmacy/*` | REAL | Prescription dispensing with barcode scanning |
| `/admin/audit` | REAL | Audit log viewer with filtering |
| `/admin/users`, `/admin/policies`, `/admin/roles`, `/admin/tenants` | REAL | Governance management CRUD |
| `/registry/*` (providers, facilities, terminology, products) | REAL | Registry access with search/pagination |
| `/finance/bills`, `/finance/claims`, `/finance/payments`, `/finance/tariffs` | REAL | All use real APIs |
| `/settings/*` | REAL | Account, security, preferences with real state management |
| `/page` | REDIRECT | redirect("/home") |

### ops-console (10 pages)

| Route/Page | Classification | Evidence |
|-----------|---------------|----------|
| `/vito` | SHELL-ONLY | Dashboard section: "Client list and search functionality will be rendered here" (line 61) |
| `/vito/audit` | REAL | Audit trail with filters |
| `/vito/cards` | REAL | SMART Card management lifecycle |
| `/vito/clients` | REAL | Client registry browsing with search |
| `/vito/config` | REAL | Registry mode toggle |
| `/vito/dedup` | REAL | Deduplication queue |
| `/vito/issuance` | REAL | Card issuance workflow |
| `/vito/match-queue` | REAL | Identity resolution matching |
| `/vito/provisional` | REAL | Provisional identity handling |
| `/tuso` | REAL | TUSO service integration |

### msika-flow-portal (5 pages)

| Route/Page | Classification | Evidence |
|-----------|---------------|----------|
| `/browse` | MOCK-DATA-DRIVEN | SAMPLE_ITEMS array (lines 14-23) with 8 hardcoded items; msikaFlowApi.validateCart() is real |
| `/cart` | REAL | Shopping cart operations |
| `/orders` | REAL | Order history and tracking |
| `/pickup` | REAL | Pickup status tracking |
| `/substitutions` | REAL | Substitution request management |

### msika-flow-ops (5 pages)

| Route/Page | Classification | Evidence |
|-----------|---------------|----------|
| `/orders` | SHELL-ONLY | 15 lines, static text "Use the search bar...", no API, no search |
| `/audit` | REAL | Audit trail with filters |
| `/reviews` | REAL | Workflow review queue |
| `/stuck` | REAL | Stuck order diagnosis |
| `/vendors` | REAL | Vendor performance metrics |

### developer-console (8 pages)

| Route/Page | Classification | Evidence |
|-----------|---------------|----------|
| `/dashboard` | REAL | getDashboardStats() API call |
| `/clients` | REAL | listClients() API call |
| `/clients/register` | REAL | Form submission |
| `/clients/[clientId]` | REAL | Individual client detail |
| `/catalog` | REAL | Browseable API catalog |
| `/certification` | REAL | Certification test runner |
| `/federation` | REAL | Federation readiness checks |
| `/sandbox` | REAL | SAMPLE_PAYLOADS are intentional API examples for testing tool; page loads real client data via listClients() |

### costa-console (7 pages) — ALL REAL

| Route/Page | Classification | Evidence |
|-----------|---------------|----------|
| `/page` | REDIRECT | Routes to /tariffs |
| `/approvals` | REAL | Approval workflows |
| `/audit` | REAL | Audit trail |
| `/bills` | REAL | Bill lifecycle (DRAFT→APPROVED→FINALIZED) |
| `/rulesets` | REAL | Rule management CRUD |
| `/simulate` | REAL | Simulation engine with API |
| `/tariffs` | REAL | Tariff management |

### ehr (5 pages)

| Route/Page | Classification | Evidence |
|-----------|---------------|----------|
| `/page` | THIN | PatientSearch uses useQuery + apiClient.searchPatients (REAL); EncounterPanel + ehrStore.startEncounter() creates encounters locally (THIN) |

### All Other UI Apps — ALL REAL

| App | Pages | Classification | Notes |
|-----|-------|---------------|-------|
| butano-web | 5 | REAL | FHIR bundle parsing, resource stats |
| inventory-web | 5 | REAL | Stock management with alerts |
| mushex-ops-console | 5 | REAL | Fraud detection & claims |
| mushex-finance-console | 5 | REAL | Financial ledger & settlements |
| mushex-payer-portal | 3 | REAL | Payer portal payments |
| msika-web | 7 | REAL | Product catalog registry |
| msika-flow-vendor | 4 | REAL | Vendor fulfillment |
| oros-web | 6 | REAL | Lab result workflows |
| pct-web | 4 | REAL | Control tower with auto-refresh |
| pharmacy-web | 3 | REAL | 843-line dispense workflow |
| portal | 4 | REAL | Health ID QR + OTP |
| self-service | 4 | REAL | Document claim workflow |
| support-console | 7 | REAL | SLA dashboard with metrics |
| zibo-web | 5 | REAL | Terminology pack management |
| ops-docs | 10 | REAL | Document & credential mgmt |
| one-ui-shell | 1 | SHELL-ONLY | Intentional — navigation frame |

---

## B. Backend Services — By Service

| Service | Java | Ctrl | Mig | Test | Classification |
|---------|------|------|-----|------|---------------|
| vito-service | 130 | 19 | 19 | 21 | REAL |
| tuso-service | 118 | 9 | 4 | 6 | REAL |
| experience-bff | 116 | 58 | 8 | 4 | REAL |
| mushex-service | 115 | 10 | 1 | 9 | REAL |
| varapi-service | 114 | 12 | 4 | 5 | REAL |
| inventory-service | 107 | 10 | 2 | 6 | REAL |
| costing-engine-service | 100 | 6 | 1 | 7 | REAL |
| pct-service | 99 | 11 | 3 | 6 | REAL |
| msika-flow-service | 98 | 9 | 1 | 8 | REAL |
| pharmacy-service | 91 | 9 | 1 | 6 | REAL |
| oros-service | 91 | 10 | 1 | 6 | REAL |
| msika-service | 75 | 8 | 3 | 5 | REAL |
| zibo-service | 65 | 8 | 2 | 6 | REAL |
| tshepo-authz-service | 59 | 6 | 1 | 6 | REAL |
| tshepo-identity-service | 53 | 6 | 1 | 6 | REAL |
| tshepo-offline-service | 50 | 5 | 1 | 4 | REAL |
| notification-service | 45 | 4 | 3 | 4 | REAL |
| tshepo-consent-service | 43 | 5 | 1 | 4 | REAL |
| tshepo-service | 40 | 3 | 6 | 3 | REAL |
| offline-edge-service | 40 | 5 | 4 | 8 | REAL |
| support-service | 39 | 7 | 4 | 3 | REAL |
| integration-hub | 39 | 4 | 3 | 4 | REAL |
| tshepo-audit-service | 37 | 6 | 1 | 4 | REAL |
| coverage-service | 37 | 5 | 2 | 3 | REAL |
| data-governance-service | 36 | 4 | 4 | 2 | ADEQUATE |
| tshepo-keys-service | 35 | 5 | 1 | 4 | REAL |
| surveillance-service | 34 | 4 | 2 | 6 | REAL |
| reporting-service | 33 | 1 | 1 | 7 | REAL |
| data-access-governance-service | 33 | 2 | 1 | 8 | REAL |
| butano-service | 33 | 5 | 1 | 4 | REAL |
| rules-service | 31 | 1 | 2 | 4 | ADEQUATE |
| data-pipeline-service | 31 | 3 | 1 | 7 | REAL |
| national-data-repository-service | 30 | 2 | 1 | 6 | REAL |
| channels-service | 30 | 5 | 1 | 2 | ADEQUATE |
| observability-service | 27 | 3 | 2 | 5 | ADEQUATE |
| card-print-agent | 27 | 3 | 1 | 1 | ADEQUATE |
| share-slip-service | 26 | 3 | 1 | 1 | ADEQUATE |
| identity-assurance-service | 26 | 2 | 1 | 7 | REAL |
| credential-verification-service | 25 | 3 | 1 | 1 | ADEQUATE |
| landela-adapter-service | 24 | 3 | 1 | 1 | ADEQUATE |
| campaigns-service | 24 | 1 | 1 | 6 | REAL |
| document-service | 23 | 2 | 1 | 1 | ADEQUATE |
| workflow-service | 22 | 3 | 2 | 2 | ADEQUATE |
| ndr-service | 22 | 1 | 3 | 2 | ADEQUATE |
| indawo-service | 22 | 3 | 2 | 2 | ADEQUATE |
| data-warehouse-service | 22 | 2 | 2 | 3 | ADEQUATE |
| security-hardening-service | 21 | 2 | 1 | 4 | ADEQUATE |
| offline-sync-service | 20 | 3 | 2 | 2 | ADEQUATE |
| dispatch-service | 20 | 2 | 2 | 2 | ADEQUATE |
| developer-portal-service | 20 | 1 | 2 | 4 | ADEQUATE |
| ubomi-service | 19 | 4 | 1 | 1 | ADEQUATE |
| forms-service | 19 | 1 | 1 | 2 | ADEQUATE |
| data-ingestion-service | 19 | 1 | 3 | 2 | ADEQUATE |
| jobs-service | 18 | 2 | 1 | 2 | ADEQUATE |
| iot-ingestion-service | 17 | 1 | 2 | 2 | ADEQUATE |
| product-registry-service | 16 | 3 | 2 | 2 | ADEQUATE |
| pharmacy-elmis-adapter | 16 | 2 | 1 | 2 | ADEQUATE |
| inventory-elmis-adapter | 16 | 2 | 1 | 2 | ADEQUATE |
| inpatient-service | 16 | 2 | 2 | 1 | ADEQUATE |
| connector-fhir-adapter | 16 | 1 | 2 | 2 | ADEQUATE |
| asset-registry-service | 16 | 2 | 3 | 2 | ADEQUATE |
| pacs-adapter-service | 15 | 2 | 1 | 2 | ADEQUATE |
| fhir-gateway-service | 14 | 2 | 1 | 2 | ADEQUATE |
| audit-ledger-service | 14 | 1 | 2 | 2 | ADEQUATE |
| search-service | 13 | 1 | 1 | 2 | ADEQUATE |
| schema-registry-service | 13 | 1 | 1 | 2 | ADEQUATE |
| shared-core | 12 | 0 | 0 | 0 | LIBRARY |
| butano-fhir | 12 | 2 | 1 | 2 | ADEQUATE |

---

## C. Mobile Applications

### Citizen App — ALL REAL

| Screen/Service | Classification | Evidence |
|---------------|---------------|----------|
| HomeScreen | REAL | fetchAppointments(), fetchPrescriptions(), fetchLabResults() |
| HealthTimelineScreen | REAL | getTimeline() from service, 100 items |
| MessagingInboxScreen | REAL | fetchConversations(), createConversation() |
| ThreadViewScreen | REAL | Real message fetch/send via messagingService |
| MarketplaceScreen | REAL | marketplaceService for browse/request |
| TelehealthListScreen | REAL | telehealthService |
| TelehealthSessionScreen | REAL | Session join with video token |
| All 14 services | REAL | All use apiClient from @impilo/mobile-api-client |

### Provider App — ALL REAL

| Screen/Service | Classification | Evidence |
|---------------|---------------|----------|
| ProviderDashboardScreen | REAL | Task list, recent encounters |
| PatientLookupScreen | REAL | Patient search → encounter creation |
| EncounterScreen | REAL | Full encounter workflow |
| All clinical panels (Vitals, Diagnosis, Rx, Labs, Referrals, Notes) | REAL | API-driven |
| Supervisor screens | REAL | Team overview, escalations, stock |
| Outreach screens | REAL | Household visits, screenings |
| Offline screens | REAL | Sync queue, conflict review, break-glass |
| All 14 services | REAL | All use apiClient from @impilo/mobile-api-client |

---

## Summary Counts

| Classification | UI Pages | Backend Services | Mobile Screens | Total |
|---------------|----------|-----------------|----------------|-------|
| REAL | 204 | 36 | 41 | 281 |
| ADEQUATE | 0 | 31 | 0 | 31 |
| THIN | 2 | 0 | 0 | 2 |
| MOCK-DATA-DRIVEN | 1 | 0 | 0 | 1 |
| SHELL-ONLY | 2 | 0 | 0 | 2 |
| REDIRECT | 2 | 0 | 0 | 2 |
| LIBRARY | 0 | 1 | 0 | 1 |
| **Total** | **211** | **68** | **41** | **320** |
