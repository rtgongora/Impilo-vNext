# Impilo vNext Feature Inventory

> **Purpose:** Persistent memory layer for coding agents — maps capabilities to doctrine, journeys, planes, wiring, and status.  
> **Generated:** 2026-05-29 (historical audit pass).  
> **Regenerate guidance:** Update when adding services, routes, or BFF controllers; run `node scripts/frontend/generate-parity-docs.mjs` for surfacing rows.

**Status legend:** `Preserved` | `Improved` | `Partial` | `Regression` | `Not Wired` | `Blocked` | `Backend Only` | `Requires Review`

---

## Summary counts

| Dimension | Count |
|-----------|-------|
| Maven service modules | 89 (88 services + shared-core) |
| Production registry services | 87 (+ 2 not registered: msika-apps, rtc-gateway) |
| Registry `wired` frontend | 3 |
| Registry `unknown-or-partial` | 84 |
| Web routes (`EXPECTED_ROUTE_COUNT`) | **417** |
| Surfacing matrix Live | 2 (Social, BUTANO SHR) |
| Surfacing matrix Partial | 19 |
| Surfacing matrix Not Wired | 1 (UBOMI) |

---

## High-priority capability inventory

| Feature | Core Transaction Stage | Journey | Plane | Owning Service | Backend Location | Contract | BFF Route | Web Route | Citizen Mobile | Provider Mobile | Build Module | Deployment Config | Tests | Status | Doctrine Dependencies |
|---------|------------------------|---------|-------|----------------|------------------|----------|-----------|-----------|----------------|-----------------|--------------|-------------------|-------|--------|----------------------|
| Core Transaction orchestration | MANAGE_STATE_MACHINE → COMPOSE_EXPERIENCE_VIEW | Person, Provider, Platform | Experience | workflow-service (SoR), experience-bff (compose) | `services/workflow-service`, `services/experience-bff/.../CoreTransactionController.java` | `contracts/core-transaction.ts` | `/internal/v1/core-transactions/*`, `/experience/core-transactions/*` | `/core-transaction` | Partial (personal indirect) | `CoreTransactionJourneyShellScreen` | workflow-service, experience-bff | `compose/core-transaction-e2e`, experience compose | BFF tests; mobile shell | **Partial — web hook missing** | State machine, event envelope, anti-duplication |
| Person identity / Health ID | IDENTITY_RESOLUTION | Person, Platform | Registry | vito-service | `services/vito-service` | `contracts/openapi/vito.openapi.yaml` | `/internal/v1/identity/*`, `/internal/v1/registry/*` | `/id-services`, `/registry/*` | health-id section | Patient lookup | vito-service | registry-e2e compose | Golden contract ITs (trust/registry jobs) | Partial | No PII in SHR; Vito SoR |
| Provider registry | IDENTITY_RESOLUTION (provider) | Provider, Platform | Registry | varapi-service | `services/varapi-service` | `contracts/openapi/varapi.openapi.yaml` | `/internal/v1/registry/*` | `/registry/providers/*` | prof profile | activation flow | varapi-service | registry-e2e | Partial IT | Partial | Provider ID activation doctrine |
| Facility / workspace | TRUST_CONTEXT_ESTABLISHED | Platform, Provider | Registry | tuso-service | `services/tuso-service` | `contracts/openapi/tuso.openapi.yaml` | `/internal/v1/facilities`, registry | `/facility/*`, `/registry/facilities/*` | — | SelectFacility/Workspace | tuso-service | registry-e2e | Partial | Partial | Facility operating model |
| Trust / authz / consent | TRUST_CONTEXT_ESTABLISHED | All | Trust | tshepo-authz, tshepo-consent, mvumo, tshepo-audit | `services/tshepo-*`, `mvumo-service` | tshepo OpenAPI slices | `/internal/v1/admin/trust/*` | `/admin/trust`, `/settings/security` | consent section | break-glass offline | tshepo cluster | trust-e2e compose | Trust e2e gates | Partial | Envoy ext_authz; 10-dimension access |
| SHR / clinical summary | RECORD_UPDATE / clinical completion | Provider, Person | Clinical | butano-service, butano-fhir | `services/butano-service` | `contracts/openapi/butano.custom.openapi.yaml` | `/internal/v1/summary/*`, `/internal/v1/timeline` | `/ehr/[patientId]/*` | records, allergies, conditions | Encounter, results | butano-service | experience compose (hapi) | Control tower tests | **Live (web)** | No PII in SHR; CPID only |
| PCT queue / encounter | QUEUED → IN_SERVICE | Person, Provider | Clinical | pct-service | `services/pct-service` | pct OpenAPI | `/internal/v1/pct/*` (BFF) | `/queue/*`, `/shift/*` | queue section | QueueManagement, Encounter | pct-service | experience compose | Partial | Partial | Dual-emit events |
| OROS orders / results | ORDERS_PENDING → ANCILLARY_IN_PROGRESS | Provider | Clinical | oros-service | `services/oros-service` | oros OpenAPI | BFF lab/imaging routes | `/lab/*`, clinical tools | results | ResultsView, orders tools | oros-service | Partial compose | Partial | Partial | Dual-emit; PACS pipeline |
| Pharmacy / Rx | IN_SERVICE, ORDERS_PENDING | Person, Provider | Clinical | pharmacy-service | `services/pharmacy-service` | pharmacy OpenAPI | BFF pharmacy | `/pharmacy/*` | prescriptions | pharmacy tools | pharmacy-service | sovereign overlay | Partial | Partial | Dual-emit; Rx journey |
| Inpatient | ADMITTED branch | Provider | Clinical | inpatient-service | `services/inpatient-service` | inpatient OpenAPI | BFF inpatient | `/clinical*`, inpatient tools | — | inpatient tool | inpatient-service | experience compose | Partial | Partial | State machine branch |
| Telemedicine | SCHEDULED → IN_SERVICE | Person, Provider | Clinical + Integration | experience-bff (orchestrate), pct, mvumo | BFF telemedicine controllers | experience-bff.openapi | `/internal/v1/teleconsult/*`, telehealth mobile | `/telemedicine/*` | TelehealthListScreen | telemedicine tool | experience-bff, pct, rtc-gateway | experience compose | MobileTelemedicineControllerTest | Partial; RTC **Blocked** | Telemedicine pipeline doc |
| PACS / imaging | ANCILLARY_IN_PROGRESS | Provider | Integration | pacs-adapter-service, oros | `services/pacs-adapter-service` | PACS pipeline doc | BFF imaging | clinical tools, imaging | — | pacs tool | pacs-adapter-service | orthanc in root compose | Partial | Partial | PACS_DICOM_PIPELINE |
| Costa costing / billing | COSTING_REQUIRED → FINANCIAL_PROCESSING | Person, Platform | Enterprise | costing-engine-service | `services/costing-engine-service` | costa.openapi | `/internal/v1/finance/*` | `/finance/*` | finance section | billing tool | costing-engine-service | sovereign overlay | Partial | Partial | Dual-emit; payment gate doctrine |
| MusheX payments / claims | PRE_SERVICE_PAYMENT_* , CLAIM_PENDING | Person, Platform | Enterprise | mushex-service | `services/mushex-service` | mushex.openapi | `/internal/v1/finance/*`, wallet | `/finance/*`, `/wallet` | wallet, finance | finance tool | mushex-service | sovereign overlay | Partial | Partial | No raw mushex in browser; dual-emit |
| Coverage / payer ops | COVERAGE_CHECK_PENDING → COVERAGE_CONFIRMED | Person, Platform | Enterprise | coverage-service | `services/coverage-service` | coverage OpenAPI | BFF finance/coverage | `/finance/*` | coverage section (commands) | — | coverage-service | Partial | Partial | Partial | Costa/MusheX timing doctrine |
| Msika catalog | SERVICE_SELECTED | Person, Platform | Registry / Enterprise | msika-service, product-registry | `services/msika-service` | msika OpenAPI | marketplace BFF | `/marketplace/*` | marketplace tab | marketplace ops | msika-service | Partial | Partial | Partial | Msika SoR |
| Msika Flow orders | ACCESS_GRANTED → fulfilment | Person | Enterprise | msika-flow-service | `services/msika-flow-service` | msika-flow.openapi | commerce BFF | `/marketplace/*` | marketplace | MarketplaceOps | msika-flow-service | Partial | Partial | Partial | Dual-emit |
| Msika Apps | — | Platform | Enterprise | msika-apps-service | `services/msika-apps-service` | — | — | marketplace apps | — | apps | msika-apps-service | **Not in registry** | None | **Requires Review** | Register service |
| Dispatch operations | TASKED, platform ops | Platform | Enterprise | dispatch-service | `services/dispatch-service` | workflow/dispatch | `/internal/v1/dispatch/*` | `/operations/dispatch` | — | workflow_dispatch tool | dispatch-service | sovereign overlay | sovereign smoke package only | Partial; **hook missing** | Unified with nhume UX |
| Nhume logistics | Fulfilment / follow-up | Person, Platform | Enterprise | nhume-service | `services/nhume-service` | nhume controllers | `/internal/v1/nhume/*`, mobile nhume | `/nhume/*` (18 routes) | nhume-track | partial | nhume-service | sovereign overlay | NhumeDeliveryServiceTest; smoke fail | Partial; runtime unstable | Ndila dependency |
| Ndila geospatial | Context / routing | Platform | Data | ndila-service | `services/ndila-service` | ndila.openapi | `/internal/v1/ndila/*` | `/ndila`, map panels | mobile-ndila SDK | map tools | ndila-service | sovereign (PostGIS off demo) | 6 unit tests | Partial | BFF exception path |
| Social timeline | Continuity / engagement | Person | Experience | community-service | `community-service/.../social` | `contracts/openapi/social.openapi.yaml` | `/internal/v1/social/*` | `/social`, `/communities`, `/pages` | SocialHubScreen | ProviderSocialScreen | community-service | — | Social tests | **Live** | No separate social-service |
| Impilo Live | Live events / CPD / education | Person, Provider | Experience | live-service | `services/live-service` | `contracts/openapi/impilo-live.openapi.yaml` | `/internal/v1/live/*` | `/live/*` (13 routes) | LiveDiscoverScreen, LiveEventScreen | ProviderLiveHubScreen | live-service, rtc-gateway-service | experience compose | live-service tests, useLive | **Live** | Media via rtc-gateway; mobile host partial |
| Nompilo assistant | GIVE_FEEDBACK, guidance | All | Experience | guidance-service, llm-orchestration | BFF guidance/llm | experience-bff.openapi | `/internal/v1/guidance/*`, `/internal/v1/llm/*`, nompilo on core-tx | `/ask`, command bar | NompiloAssistantScreen | NompiloAssistantScreen | guidance-service, llm-orchestration | Partial | Partial | Partial; BFF stubs on core-tx | Nompilo doctrine |
| Fundo LMS | Provider readiness | Provider | Enterprise | learning-service | `services/learning-service` | learning v11 OpenAPI | `/internal/v1/learning/v11/*` | `/learning/*` | shallow | learning tool | learning-service | Partial | Partial | Partial | Fundo competency support |
| UBOMI CRVS | Identity/civil events | Platform | Registry | ubomi-service | `services/ubomi-service` | ubomi.openapi | minimal | `/ubomi` placeholder | — | — | ubomi-service | registry-e2e | Partial | **Not Wired** | CRVS context |
| Zibo terminology | SERVICE_SELECTED (codes) | Platform | Registry | zibo-service | `services/zibo-service` | zibo.openapi | limited | ui/zibo-web | n/a | — | zibo-service | registry-e2e | Partial | Partial | Sovereign terminology console |
| Indawo site registry | Public health context | Platform | Registry / Data | indawo-service | `services/indawo-service` | indawo.openapi | public-health BFF | `/public-health/*` | public_health tab | field tasks | indawo-service | Partial | Partial | Partial | Ndila map integration |
| Surveillance / PH intel | Reporting/analytics | Platform | Data | surveillance-service, campaigns | `services/surveillance-service` | surveillance.openapi | public-health BFF | `/public-health/*`, intelligence | public_health | ph_field_tasks | surveillance-service | sovereign overlay | Partial; V003 deleted | Partial | LLM orchestration PH flows |
| Integration Hub | Platform ops | Platform | Integration | integration-hub | `services/integration-hub` | integration-hub.openapi | `/internal/v1/integration-hub/*` | `/admin/integration-status` | — | developer_hub | integration-hub | experience compose | Partial | Partial | Adapter doctrine |
| Document management | Orders/records | Provider | Integration | document-service, landela-adapter | `services/document-service` | document OpenAPI | BFF documents | clinical/EHR docs | records | — | document-service | Partial | Partial | Partial | DOCUMENT_MANAGEMENT_PIPELINE |
| Offline / edge | PENDING_SYNC branch | Provider | Integration | offline-sync-service, tshepo-offline | `services/offline-sync-service` | offline contracts | BFF offline | settings | offline patterns | OfflineTabs | offline-sync-service | Partial | Partial | Partial | Offline doctrine |
| Workflow definitions | MANAGE_STATE_MACHINE | Platform | Experience | workflow-service | `services/workflow-service` | workflow.openapi | `/internal/v1/workflows/*` | `/operations/workflows` | — | workflow_dispatch | workflow-service | core-tx e2e | Partial | Partial; **no core-tx emit** | Workflow SoR |
| Wellness | Need/trigger (wellness) | Person | Experience | wellness-service | `services/wellness-service` | wellness OpenAPI | BFF wellness | `/wellness/*` | wellness section | — | wellness-service | experience compose | Partial | Partial | Consumer-grade wellness pillar |
| General ledger / HR | FINANCIAL_PROCESSING | Platform | Enterprise | general-ledger-service, hr-payroll-service | respective services | finance/HR OpenAPI | BFF enterprise | `/finance/*`, `/enterprise/*` | finance | finance tool | gl, hr-payroll | Partial | Partial | Partial | ERP audits note gaps |
| RTC gateway | IN_SERVICE (virtual) | Person, Provider | Integration | rtc-gateway-service | `services/rtc-gateway-service` | — | — | telemedicine (blocked) | telehealth | telemedicine | rtc-gateway-service | **Not in registry** | None | **Blocked** | WebRTC intentionally off |
| AI diagnostic assist (UI) | DELIVER_CARE assist | Provider | Clinical | clinical-knowledge-platform | CKP service | — | BFF CDS routes | clinical tools / EHR | — | CDS tool | clinical-knowledge-platform-service | Partial | Partial tests | **Regression risk** (MOCK fallback) | Nompilo must not override judgement |
| Production command centre | Reporting/analytics | Platform | Experience | experience-bff + registry maturity | BFF + `registry-maturity.json` | — | various | `/production-command-centre` | — | ops_reports | experience-bff | — | Partial | Partial; fake metrics risk R8 | Maturity honesty |
| Citizen personal workspace | Multiple stages | Person | Experience | BFF compose | multiple | multiple | `/citizen/*`, personal sections | PersonalScreen sections | — | mobile only depth | apps/mobile/citizen-app | — | mobile tests | Partial | Person-first doctrine |
| Provider clinical tools | DELIVER_CARE → ORDER_ACTIONS | Provider | Experience | BFF + clinical services | multiple | multiple | `/clinical-tools/*` | ClinicalToolsScreen | soap, triage, orders, etc. | experience-bff | — | mobile tests | Partial | Provider journey |
| Web route registry | COMPOSE_EXPERIENCE_VIEW | Platform | Experience | one-ui-shell | `ui/one-ui-shell/src/lib/routes.ts` | — | — | 417 routes | — | — | ui/one-ui-shell | docker build | `test:routes` | **Preserved** (417) | Route count invariant |
| Event bus core transactions | EMIT_EVENTS_AND_AUDIT | Platform | Integration | Kafka + domain services | outbox publishers | `contracts/asyncapi/core-transaction-events.asyncapi.yaml` | — | — | — | — | 6 dual-emit services | kafka in compose | doctrine compliance job | **Partial** (envelope gap) | Event doctrine |

---

## Plane rollup

| Plane | Features tracked | Live | Partial | Regression / Not Wired |
|-------|------------------|------|---------|------------------------|
| Trust | 4 | 0 | 4 | 0 |
| Registry | 7 | 0 | 5 | 1 (UBOMI) |
| Clinical | 8 | 1 (SHR) | 7 | 0 |
| Data | 3 | 0 | 3 | 0 |
| Integration | 6 | 0 | 5 | 1 (rtc blocked) |
| Experience | 8 | 1 (Social) | 6 | 1 (core-tx hook) |
| Enterprise | 10 | 0 | 10 | 0 |

---

## Build and deployment quick reference

| Tier | Services | Compose |
|------|----------|---------|
| **Always in experience base compose** | wellness, pct, integration-hub, inpatient, experience-bff, one-ui-shell | `compose/experience/docker-compose.yml` |
| **Sovereign overlay only** | pharmacy, mushex, dispatch, costing, surveillance, ndila, nhume | `compose/experience/docker-compose.sovereign.yml` |
| **Trust/registry E2E jars** | tshepo-*, vito, varapi, tuso, zibo, msika, ubomi, experience-bff | trust + registry compose files |
| **CI explicit unit test** | experience-bff, tshepo-authz, shared-core, mvumo, tshepo-consent, tshepo | `.github/workflows/ci.yml` |
| **Not in any compose** | ~65 remaining Maven modules | Local `spring-boot:run` only |

---

## Agent update protocol

When implementing a feature:

1. Add or update row in this file.
2. Update `docs/registry/services-registry.yaml` via `scripts/registry/seed-registry.mjs` if service ownership changes.
3. Regenerate `docs/frontend/BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md`.
4. Map Core Transaction stage in PR description using `docs/templates/CORE_TRANSACTION_FEATURE_ALIGNMENT_CHECKLIST.md` (if present) or doctrine docs.
5. Set maturity in `registry-maturity.json` / `app-registry.ts`.
6. Never mark **Live** without hook → BFF → service → contract → test proof.

---

## Related documents

- Full audit: [`VNEXT_HISTORICAL_FUNCTIONALITY_AND_DOCTRINE_REGRESSION_AUDIT.md`](../VNEXT_HISTORICAL_FUNCTIONALITY_AND_DOCTRINE_REGRESSION_AUDIT.md)
- Surfacing matrix: [`docs/frontend/BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md`](frontend/BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md)
- Service registry: [`docs/registry/services-registry.yaml`](registry/services-registry.yaml)
- Route registry: [`ui/one-ui-shell/src/lib/routes.ts`](../ui/one-ui-shell/src/lib/routes.ts)
